package de.ma.ftms.core.storage

import kotlinx.coroutines.flow.Flow
import de.ma.ftms.core.SmoothedFtmsSample

interface SessionHistoryRepository {
    fun observeRecentSessions(): Flow<List<WorkoutSessionRecord>>
    fun observeSamples(sessionId: String): Flow<List<SessionSampleRecord>>

    suspend fun recoverInterruptedSessions(nowMillis: Long)
    suspend fun createSession(
        startedAtMillis: Long,
        treadmillName: String?,
        treadmillAddress: String?,
        garminName: String?,
        garminId: Long?,
    ): WorkoutSessionRecord
    suspend fun appendSample(sessionId: String, startedAtMillis: Long, sample: SmoothedFtmsSample)
    suspend fun updateStats(sessionId: String, nowMillis: Long, stats: SessionStatsSnapshot)
    suspend fun finishSession(
        sessionId: String,
        stoppedAtMillis: Long,
        finalStatus: String,
        stats: SessionStatsSnapshot,
    ): WorkoutSessionRecord?
    suspend fun latestSession(): WorkoutSessionRecord?
    suspend fun deleteSession(sessionId: String)
    suspend fun clearAll()
}
