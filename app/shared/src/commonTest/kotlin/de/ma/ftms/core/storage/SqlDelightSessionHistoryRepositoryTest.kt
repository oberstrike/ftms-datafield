package de.ma.ftms.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import de.ma.ftms.core.DistanceSource
import de.ma.ftms.core.FtmsMachineKind
import de.ma.ftms.core.FtmsSample
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
    fun latestSessionIsNullWhenEmpty() = runTest {
        assertNull(repository().latestSession())
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
