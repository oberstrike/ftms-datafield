package de.ma.ftms.core.storage

import kotlinx.coroutines.flow.Flow
import de.ma.ftms.core.FtmsMachineKind
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
        equipmentKind: Int = FtmsMachineKind.TREADMILL,
    ): WorkoutSessionRecord
    suspend fun appendSample(sessionId: String, startedAtMillis: Long, sample: SmoothedFtmsSample)
    suspend fun updateStats(sessionId: String, nowMillis: Long, stats: SessionStatsSnapshot)
    suspend fun saveFinishedSession(
        startedAtMillis: Long,
        stoppedAtMillis: Long,
        finalStatus: String,
        treadmillName: String?,
        treadmillAddress: String?,
        garminName: String?,
        garminId: Long?,
        stats: SessionStatsSnapshot,
        samples: List<SmoothedFtmsSample>,
        equipmentKind: Int = FtmsMachineKind.TREADMILL,
    ): WorkoutSessionRecord
    suspend fun finishSession(
        sessionId: String,
        stoppedAtMillis: Long,
        finalStatus: String,
        stats: SessionStatsSnapshot,
    ): WorkoutSessionRecord?
    suspend fun latestSession(): WorkoutSessionRecord?

    /**
     * The averages over the samples of each of [sessionIds]. Every id asked for is in the result; a
     * session without samples has averages without values.
     */
    suspend fun sessionAverages(sessionIds: Collection<String>): Map<String, SessionAverages>
    suspend fun deleteSession(sessionId: String)

    /** Deletes all of [sessionIds] with their samples, or none of them. */
    suspend fun deleteSessions(sessionIds: Collection<String>)
    suspend fun clearAll()
}
