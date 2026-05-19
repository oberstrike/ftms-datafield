package de.ma.ftms.core.storage

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.db.SqlDriver
import de.ma.ftms.core.DistanceSource
import de.ma.ftms.core.SmoothedFtmsSample
import de.ma.ftms.core.storage.db.FtmsBridgeDatabase
import de.ma.ftms.core.storage.db.FtmsBridgeQueries
import de.ma.ftms.core.storage.db.Session_sample
import de.ma.ftms.core.storage.db.Workout_session
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlDelightSessionHistoryRepository(
    driver: SqlDriver,
    private val databaseContext: CoroutineContext = Dispatchers.Default,
) : SessionHistoryRepository {
    private val queries: FtmsBridgeQueries = FtmsBridgeDatabase(driver).ftmsBridgeQueries

    override fun observeRecentSessions(): Flow<List<WorkoutSessionRecord>> =
        queries.selectRecentSessions()
            .asFlow()
            .mapToList(databaseContext)
            .map { rows -> rows.map { it.toRecord() } }

    override fun observeSamples(sessionId: String): Flow<List<SessionSampleRecord>> =
        queries.selectSamplesForSession(session_id = sessionId)
            .asFlow()
            .mapToList(databaseContext)
            .map { rows -> rows.map { it.toRecord() } }

    override suspend fun recoverInterruptedSessions(nowMillis: Long) = withContext(databaseContext) {
        queries.markOpenSessionsInterrupted(
            stopped_at_millis = nowMillis,
            updated_at_millis = nowMillis,
        )
        Unit
    }

    override suspend fun createSession(
        startedAtMillis: Long,
        treadmillName: String?,
        treadmillAddress: String?,
        garminName: String?,
        garminId: Long?,
    ): WorkoutSessionRecord = withContext(databaseContext) {
        val id = newSessionId(startedAtMillis)
        queries.insertSession(
            session_id = id,
            started_at_millis = startedAtMillis,
            final_status = "running",
            treadmill_name = treadmillName,
            treadmill_address = treadmillAddress,
            garmin_name = garminName,
            garmin_id = garminId,
            updated_at_millis = startedAtMillis,
        )
        queries.selectSessionById(id).executeAsOne().toRecord()
    }

    override suspend fun appendSample(
        sessionId: String,
        startedAtMillis: Long,
        sample: SmoothedFtmsSample,
    ) = withContext(databaseContext) {
        val raw = sample.raw
        val timestamp = raw.timestampMillis ?: System.currentTimeMillis()
        queries.insertSample(
            session_id = sessionId,
            timestamp_millis = timestamp,
            offset_millis = (timestamp - startedAtMillis).coerceAtLeast(0L),
            kind = raw.kind.toLong(),
            raw_flags = raw.rawFlags.toLong(),
            raw_hex = raw.rawHex,
            speed_kmh = raw.speedKmh,
            raw_distance_m = raw.distanceM?.toLong(),
            smoothed_distance_m = sample.distanceM,
            ascent_m = sample.ascentM,
            incline_pct = raw.inclinePct,
            power_w = raw.powerW?.toLong(),
            heart_rate_bpm = raw.heartRateBpm?.toLong(),
            cadence_rpm = raw.cadenceRpm,
            step_rate_spm = raw.stepRateSpm,
            resistance = raw.resistance,
            elapsed_s = raw.elapsedS?.toLong(),
            remaining_s = raw.remainingS?.toLong(),
            parse_error = raw.parseError,
            distance_source = sample.distanceSource.name,
        )
        Unit
    }

    override suspend fun updateStats(sessionId: String, nowMillis: Long, stats: SessionStatsSnapshot) =
        withContext(databaseContext) {
            queries.updateSessionStats(
                packet_count = stats.packetCount.toLong(),
                send_success_count = stats.sendSuccessCount.toLong(),
                send_failure_count = stats.sendFailureCount.toLong(),
                last_error = stats.lastError,
                final_distance_m = stats.latest?.distanceM ?: 0.0,
                final_ascent_m = stats.latest?.ascentM ?: 0.0,
                final_speed_kmh = stats.latest?.raw?.speedKmh ?: 0.0,
                updated_at_millis = nowMillis,
                session_id = sessionId,
            )
            Unit
        }

    override suspend fun finishSession(
        sessionId: String,
        stoppedAtMillis: Long,
        finalStatus: String,
        stats: SessionStatsSnapshot,
    ): WorkoutSessionRecord? = withContext(databaseContext) {
        queries.finishSession(
            stopped_at_millis = stoppedAtMillis,
            final_status = finalStatus,
            packet_count = stats.packetCount.toLong(),
            send_success_count = stats.sendSuccessCount.toLong(),
            send_failure_count = stats.sendFailureCount.toLong(),
            last_error = stats.lastError,
            final_distance_m = stats.latest?.distanceM ?: 0.0,
            final_ascent_m = stats.latest?.ascentM ?: 0.0,
            final_speed_kmh = stats.latest?.raw?.speedKmh ?: 0.0,
            updated_at_millis = stoppedAtMillis,
            session_id = sessionId,
        )
        queries.selectSessionById(sessionId).executeAsOneOrNull()?.toRecord()
    }

    override suspend fun latestSession(): WorkoutSessionRecord? = withContext(databaseContext) {
        queries.selectLatestSession().executeAsOneOrNull()?.toRecord()
    }

    override suspend fun deleteSession(sessionId: String) = withContext(databaseContext) {
        queries.transaction {
            queries.deleteSession(sessionId)
            queries.deleteSessionRow(sessionId)
        }
        Unit
    }

    override suspend fun clearAll() = withContext(databaseContext) {
        queries.transaction {
            queries.clearSamples()
            queries.clearSessions()
        }
        Unit
    }

    private fun Workout_session.toRecord(): WorkoutSessionRecord =
        WorkoutSessionRecord(
            sessionId = session_id,
            startedAtMillis = started_at_millis,
            stoppedAtMillis = stopped_at_millis,
            finalStatus = final_status,
            treadmillName = treadmill_name,
            treadmillAddress = treadmill_address,
            garminName = garmin_name,
            garminId = garmin_id,
            packetCount = packet_count.toInt(),
            sendSuccessCount = send_success_count.toInt(),
            sendFailureCount = send_failure_count.toInt(),
            lastError = last_error,
            finalDistanceM = final_distance_m,
            finalAscentM = final_ascent_m,
            finalSpeedKmh = final_speed_kmh,
            updatedAtMillis = updated_at_millis,
        )

    private fun Session_sample.toRecord(): SessionSampleRecord =
        SessionSampleRecord(
            sampleId = sample_id,
            sessionId = session_id,
            timestampMillis = timestamp_millis,
            offsetMillis = offset_millis,
            kind = kind.toInt(),
            rawFlags = raw_flags.toInt(),
            rawHex = raw_hex,
            speedKmh = speed_kmh,
            rawDistanceM = raw_distance_m?.toInt(),
            smoothedDistanceM = smoothed_distance_m,
            ascentM = ascent_m,
            inclinePct = incline_pct,
            powerW = power_w?.toInt(),
            heartRateBpm = heart_rate_bpm?.toInt(),
            cadenceRpm = cadence_rpm,
            stepRateSpm = step_rate_spm,
            resistance = resistance,
            elapsedS = elapsed_s?.toInt(),
            remainingS = remaining_s?.toInt(),
            parseError = parse_error,
            distanceSource = runCatching { DistanceSource.valueOf(distance_source) }.getOrDefault(DistanceSource.NONE),
        )

    private fun newSessionId(startedAtMillis: Long): String =
        "session-$startedAtMillis-${Random.nextInt().toUInt().toString(16)}"
}
