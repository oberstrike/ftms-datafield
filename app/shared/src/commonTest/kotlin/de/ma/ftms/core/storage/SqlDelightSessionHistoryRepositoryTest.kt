package de.ma.ftms.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import de.ma.ftms.core.DistanceSource
import de.ma.ftms.core.FtmsMachineKind
import de.ma.ftms.core.FtmsSample
import de.ma.ftms.core.SensorReadings
import de.ma.ftms.core.SmoothedFtmsSample
import de.ma.ftms.core.storage.db.FtmsBridgeDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SqlDelightSessionHistoryRepositoryTest {
    @Test
    fun createsStoresSamplesAndFinishesSession() = runTest {
        val repository = repository()
        val session = repository.createSession(
            startedAtMillis = 1_000L,
            treadmillName = "FS-BC11B7",
            treadmillAddress = "AA:BB",
            garminName = "FR970",
            garminId = 42L,
        )

        repository.appendSample(session.sessionId, session.startedAtMillis, sample(timestamp = 2_000L, distance = 10.0))
        repository.appendSample(session.sessionId, session.startedAtMillis, sample(timestamp = 3_000L, distance = 20.0))
        val finished = repository.finishSession(
            sessionId = session.sessionId,
            stoppedAtMillis = 4_000L,
            finalStatus = "stopped",
            stats = SessionStatsSnapshot(
                packetCount = 2,
                sendSuccessCount = 2,
                sendFailureCount = 0,
                lastError = "",
                latest = sample(timestamp = 3_000L, distance = 20.0),
            ),
        )

        assertNotNull(finished)
        assertEquals("stopped", finished.finalStatus)
        assertEquals(20.0, finished.finalDistanceM)
        assertEquals(2, repository.observeSamples(session.sessionId).first().size)
    }

    @Test
    fun savesFinishedSessionWithSamplesAtomically() = runTest {
        val repository = repository()
        val firstSample = sample(timestamp = 2_000L, distance = 10.0)
        val secondSample = sample(timestamp = 3_000L, distance = 20.0)
        val resetSample = sample(timestamp = 4_000L, distance = 0.0)

        val saved = repository.saveFinishedSession(
            startedAtMillis = 1_000L,
            stoppedAtMillis = 5_000L,
            finalStatus = "stopped",
            treadmillName = "FS-BC11B7",
            treadmillAddress = "AA:BB",
            garminName = "FR970",
            garminId = 42L,
            stats = SessionStatsSnapshot(
                packetCount = 3,
                sendSuccessCount = 2,
                sendFailureCount = 0,
                lastError = "",
                latest = resetSample,
            ),
            samples = listOf(firstSample, secondSample, resetSample),
        )

        assertEquals("stopped", saved.finalStatus)
        assertEquals(5_000L, saved.stoppedAtMillis)
        assertEquals(20.0, saved.finalDistanceM)
        assertEquals(0.5, saved.finalAscentM)
        assertEquals(listOf(saved.sessionId), repository.observeRecentSessions().first().map { it.sessionId })
        assertEquals(listOf(1_000L, 2_000L, 3_000L), repository.observeSamples(saved.sessionId).first().map { it.offsetMillis })
    }

    @Test
    fun observesRecentSessionsNewestFirst() = runTest {
        val repository = repository()

        val older = repository.createSession(1_000L, null, null, null, null)
        val newer = repository.createSession(2_000L, null, null, null, null)

        assertEquals(listOf(newer.sessionId, older.sessionId), repository.observeRecentSessions().first().map { it.sessionId })
    }

    @Test
    fun deletesSessionWithSamples() = runTest {
        val repository = repository()
        val session = repository.createSession(1_000L, null, null, null, null)
        repository.appendSample(session.sessionId, session.startedAtMillis, sample(timestamp = 2_000L))

        repository.deleteSession(session.sessionId)

        assertEquals(emptyList(), repository.observeRecentSessions().first())
        assertEquals(emptyList(), repository.observeSamples(session.sessionId).first())
    }

    @Test
    fun clearsAllSessions() = runTest {
        val repository = repository()
        val first = repository.createSession(1_000L, null, null, null, null)
        val second = repository.createSession(2_000L, null, null, null, null)
        repository.appendSample(first.sessionId, first.startedAtMillis, sample(timestamp = 1_500L))
        repository.appendSample(second.sessionId, second.startedAtMillis, sample(timestamp = 2_500L))

        repository.clearAll()

        assertEquals(emptyList(), repository.observeRecentSessions().first())
    }

    @Test
    fun recoversOpenSessionsAsInterrupted() = runTest {
        val repository = repository()
        val session = repository.createSession(1_000L, null, null, null, null)

        repository.recoverInterruptedSessions(5_000L)

        val recovered = repository.latestSession()
        assertNotNull(recovered)
        assertEquals(session.sessionId, recovered.sessionId)
        assertEquals("interrupted", recovered.finalStatus)
        assertEquals(5_000L, recovered.stoppedAtMillis)
    }

    @Test
    fun recoveryPreservesRunningSessionSamplesAndStats() = runTest {
        val repository = repository()
        val session = repository.createSession(1_000L, "FS-BC11B7", "AA:BB", "FR970", 42L)
        val firstSample = sample(timestamp = 2_000L, distance = 10.0)
        val secondSample = sample(timestamp = 3_000L, distance = 20.0)
        repository.appendSample(session.sessionId, session.startedAtMillis, firstSample)
        repository.appendSample(session.sessionId, session.startedAtMillis, secondSample)
        repository.updateStats(
            sessionId = session.sessionId,
            nowMillis = 3_500L,
            stats = SessionStatsSnapshot(
                packetCount = 2,
                sendSuccessCount = 1,
                sendFailureCount = 1,
                lastError = "send failed",
                latest = secondSample,
            ),
        )

        repository.recoverInterruptedSessions(5_000L)

        val recovered = repository.latestSession()
        assertNotNull(recovered)
        assertEquals("interrupted", recovered.finalStatus)
        assertEquals(2, recovered.packetCount)
        assertEquals(1, recovered.sendSuccessCount)
        assertEquals(1, recovered.sendFailureCount)
        assertEquals("send failed", recovered.lastError)
        assertEquals(20.0, recovered.finalDistanceM)
        assertEquals(listOf(1_000L, 2_000L), repository.observeSamples(session.sessionId).first().map { it.offsetMillis })
    }

    @Test
    fun latestSessionIsNullWhenEmpty() = runTest {
        assertNull(repository().latestSession())
    }

    @Test
    fun storesTheEquipmentTypeOfASession() = runTest {
        val repository = repository()

        val bike = repository.createSession(1_000L, "Bike", "AA:BB", null, null, FtmsMachineKind.INDOOR_BIKE)
        val treadmill = repository.createSession(2_000L, null, null, null, null)
        val saved = repository.saveFinishedSession(
            startedAtMillis = 3_000L,
            stoppedAtMillis = 4_000L,
            finalStatus = "stopped",
            treadmillName = null,
            treadmillAddress = null,
            garminName = null,
            garminId = null,
            stats = SessionStatsSnapshot(0, 0, 0, "", null),
            samples = emptyList(),
            equipmentKind = FtmsMachineKind.CROSS_TRAINER,
        )

        assertEquals(FtmsMachineKind.INDOOR_BIKE, bike.equipmentKind)
        assertEquals(FtmsMachineKind.TREADMILL, treadmill.equipmentKind)
        assertEquals(FtmsMachineKind.CROSS_TRAINER, saved.equipmentKind)
    }

    @Test
    fun migratesAVersionOneDatabaseAndTakesTheEquipmentTypeFromTheSamples() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        VERSION_ONE_SCHEMA.forEach { driver.execute(null, it, 0) }
        listOf("bike" to 1_000L, "run" to 2_000L, "empty" to 3_000L).forEach { (id, startedAt) ->
            driver.execute(
                null,
                "INSERT INTO workout_session(session_id, started_at_millis, stopped_at_millis, final_status, " +
                    "final_distance_m, updated_at_millis) VALUES ('$id', $startedAt, ${startedAt + 500}, 'stopped', 12.5, $startedAt)",
                0,
            )
        }
        listOf("bike" to FtmsMachineKind.INDOOR_BIKE, "run" to FtmsMachineKind.TREADMILL).forEach { (id, kind) ->
            driver.execute(
                null,
                "INSERT INTO session_sample(session_id, timestamp_millis, offset_millis, kind, raw_flags, " +
                    "smoothed_distance_m, ascent_m, distance_source) VALUES ('$id', 1, 1, $kind, 0, 1.0, 0.0, 'FTMS')",
                0,
            )
        }

        assertEquals(3L, FtmsBridgeDatabase.Schema.version)
        FtmsBridgeDatabase.Schema.migrate(driver, 1L, FtmsBridgeDatabase.Schema.version)

        val repository = SqlDelightSessionHistoryRepository(driver, Dispatchers.Default)
        val sessions = repository.observeRecentSessions().first().associateBy { it.sessionId }
        assertEquals(3, sessions.size)
        assertEquals(FtmsMachineKind.INDOOR_BIKE, sessions.getValue("bike").equipmentKind)
        assertEquals(FtmsMachineKind.TREADMILL, sessions.getValue("run").equipmentKind)
        assertEquals(FtmsMachineKind.TREADMILL, sessions.getValue("empty").equipmentKind)
        assertEquals(12.5, sessions.getValue("bike").finalDistanceM)
        assertEquals(1, repository.observeSamples("bike").first().size)

        val created = repository.createSession(9_000L, null, null, null, null, FtmsMachineKind.INDOOR_BIKE)
        assertEquals(FtmsMachineKind.INDOOR_BIKE, created.equipmentKind)
    }

    @Test
    fun storesWhatEquipmentAndWatchEachReported() = runTest {
        val repository = repository()
        val session = repository.createSession(1_000L, "Bike", "AA:BB", null, null, FtmsMachineKind.INDOOR_BIKE)
        val both = sample(2_000L).let {
            it.copy(
                raw = it.raw.copy(
                    equipment = SensorReadings(powerW = 182, heartRateBpm = null, cadenceRpm = 88.5),
                    watch = SensorReadings(powerW = 176, heartRateBpm = 141, cadenceRpm = 89.0),
                ),
            )
        }

        repository.appendSample(session.sessionId, session.startedAtMillis, both)
        repository.appendSample(session.sessionId, session.startedAtMillis, sample(3_000L))

        val stored = repository.observeSamples(session.sessionId).first()
        assertEquals(SensorReadings(182, null, 88.5), stored[0].equipment)
        assertEquals(SensorReadings(176, 141, 89.0), stored[0].watch)
        assertNull(stored[1].equipment)
        assertNull(stored[1].watch)
    }

    @Test
    fun migratesAVersionTwoDatabaseAndKeepsItsSamples() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        VERSION_ONE_SCHEMA.forEach { driver.execute(null, it, 0) }
        FtmsBridgeDatabase.Schema.migrate(driver, 1L, 2L)
        driver.execute(
            null,
            "INSERT INTO workout_session(session_id, started_at_millis, final_status, updated_at_millis, equipment_kind) " +
                "VALUES ('ride', 1000, 'stopped', 1000, ${FtmsMachineKind.INDOOR_BIKE})",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO session_sample(session_id, timestamp_millis, offset_millis, kind, raw_flags, " +
                "smoothed_distance_m, ascent_m, power_w, distance_source) VALUES ('ride', 1, 1, 1, 0, 1.0, 0.0, 150, 'FTMS')",
            0,
        )

        FtmsBridgeDatabase.Schema.migrate(driver, 2L, FtmsBridgeDatabase.Schema.version)

        val repository = SqlDelightSessionHistoryRepository(driver, Dispatchers.Default)
        val old = repository.observeSamples("ride").first().single()
        assertEquals(150, old.powerW)
        assertNull(old.equipment)
        assertNull(old.watch)
        assertEquals(FtmsMachineKind.INDOOR_BIKE, repository.observeRecentSessions().first().single().equipmentKind)

        val withWatch = sample(2_000L).let { it.copy(raw = it.raw.copy(watch = SensorReadings(powerW = 201))) }
        repository.appendSample("ride", 1_000L, withWatch)
        assertEquals(SensorReadings(powerW = 201), repository.observeSamples("ride").first().last().watch)
    }

    private fun repository(): SqlDelightSessionHistoryRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FtmsBridgeDatabase.Schema.create(driver)
        return SqlDelightSessionHistoryRepository(driver, Dispatchers.Default)
    }

    private fun sample(timestamp: Long, distance: Double = 12.0): SmoothedFtmsSample =
        SmoothedFtmsSample(
            raw = FtmsSample(
                kind = FtmsMachineKind.TREADMILL,
                timestampMillis = timestamp,
                rawFlags = 7,
                rawHex = "0102",
                speedKmh = 6.0,
                distanceM = distance.toInt(),
                inclinePct = 2.5,
                powerW = 120,
                heartRateBpm = 140,
                elapsedS = ((timestamp - 1_000L) / 1_000L).toInt(),
            ),
            distanceM = distance,
            ascentM = distance * 0.025,
            distanceSource = DistanceSource.SPEED_TIME,
        )
}

/** The tables as version 1 of the app created them, before sessions had an equipment type. */
private val VERSION_ONE_SCHEMA = listOf(
    """
    CREATE TABLE workout_session (
        session_id TEXT NOT NULL PRIMARY KEY,
        started_at_millis INTEGER NOT NULL,
        stopped_at_millis INTEGER,
        final_status TEXT NOT NULL,
        treadmill_name TEXT,
        treadmill_address TEXT,
        garmin_name TEXT,
        garmin_id INTEGER,
        packet_count INTEGER NOT NULL DEFAULT 0,
        send_success_count INTEGER NOT NULL DEFAULT 0,
        send_failure_count INTEGER NOT NULL DEFAULT 0,
        last_error TEXT NOT NULL DEFAULT '',
        final_distance_m REAL NOT NULL DEFAULT 0.0,
        final_ascent_m REAL NOT NULL DEFAULT 0.0,
        final_speed_kmh REAL NOT NULL DEFAULT 0.0,
        updated_at_millis INTEGER NOT NULL
    )
    """.trimIndent(),
    "CREATE INDEX idx_workout_session_started ON workout_session(started_at_millis DESC)",
    "CREATE INDEX idx_workout_session_status ON workout_session(final_status)",
    """
    CREATE TABLE session_sample (
        sample_id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
        session_id TEXT NOT NULL,
        timestamp_millis INTEGER NOT NULL,
        offset_millis INTEGER NOT NULL,
        kind INTEGER NOT NULL,
        raw_flags INTEGER NOT NULL,
        raw_hex TEXT NOT NULL DEFAULT '',
        speed_kmh REAL,
        raw_distance_m INTEGER,
        smoothed_distance_m REAL NOT NULL,
        ascent_m REAL NOT NULL,
        incline_pct REAL,
        power_w INTEGER,
        heart_rate_bpm INTEGER,
        cadence_rpm REAL,
        step_rate_spm REAL,
        resistance REAL,
        elapsed_s INTEGER,
        remaining_s INTEGER,
        parse_error TEXT,
        distance_source TEXT NOT NULL,
        FOREIGN KEY(session_id) REFERENCES workout_session(session_id) ON DELETE CASCADE
    )
    """.trimIndent(),
    "CREATE INDEX idx_session_sample_session_offset ON session_sample(session_id, offset_millis)",
)
