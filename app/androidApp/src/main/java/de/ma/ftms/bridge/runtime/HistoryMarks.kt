package de.ma.ftms.bridge.runtime

import de.ma.ftms.core.storage.WorkoutSessionRecord

/**
 * [marked] with the mark of [sessionId] switched. A session that is not in [sessions] or that is
 * still recording gets no mark: it is being written, and an action on marked sessions deletes them.
 */
internal fun toggledMark(marked: Set<String>, sessions: List<WorkoutSessionRecord>, sessionId: String): Set<String> =
    when {
        sessionId in marked -> marked - sessionId
        isMarkable(sessions.firstOrNull { it.sessionId == sessionId }) -> marked + sessionId
        else -> marked
    }

/** The marks that still stand for a session of [sessions]. */
internal fun keptMarks(marked: Set<String>, sessions: List<WorkoutSessionRecord>): Set<String> {
    if (marked.isEmpty()) {
        return marked
    }

    val markable = sessions.filter(::isMarkable).mapTo(mutableSetOf()) { it.sessionId }
    return if (markable.containsAll(marked)) marked else marked intersect markable
}

internal fun isMarkable(session: WorkoutSessionRecord?): Boolean =
    session != null && session.finalStatus != SESSION_STATUS_RUNNING

internal const val SESSION_STATUS_RUNNING = "running"
