package de.ma.ftms.bridge.runtime

/** State of the activity timer on the watch, as the data field reports it. */
enum class WatchTimerState {
    /** No activity is being recorded: none was started, or it was saved or discarded. */
    OFF,

    /** The user stopped the activity and has not yet resumed, saved or discarded it. */
    STOPPED,

    /** The watch paused the activity by itself (auto pause). */
    PAUSED,

    /** The activity is being recorded. */
    RUNNING,
    ;

    /** True while an activity exists on the watch, recording or not. */
    val activityOngoing: Boolean
        get() = this != OFF

    companion object {
        /** The state for the number the data field sends, or null for a number it does not use. */
        fun fromWire(value: Int?): WatchTimerState? =
            when (value) {
                0 -> OFF
                1 -> STOPPED
                2 -> PAUSED
                3 -> RUNNING
                else -> null
            }
    }
}

/** A timer state together with the time it arrived at. */
internal data class WatchTimerReport(val state: WatchTimerState, val receivedAtMillis: Long)

/** What the bridge does to follow the watch's activity timer. */
internal enum class WatchTimerAction { NONE, START, PAUSE, RESUME, STOP }

/**
 * What the bridge should do now that the watch reports [reported], having last followed
 * [followed] (null if it has followed nothing yet).
 *
 * Only a change of the watch's state leads to an action, so what the user does in the app in
 * between (stopping or pausing a session by hand) is not undone by the next report of the same
 * state.
 *
 * - An activity that records starts the session, or resumes one that is paused.
 * - An activity the user stopped pauses the session. Auto pause does not: with the running sensor
 *   the watch would then see no speed and never resume.
 * - An activity that ended (saved or discarded) stops the session. A watch that merely has not
 *   started an activity yet does not stop a session begun in the app.
 */
internal fun watchTimerAction(
    followed: WatchTimerState?,
    reported: WatchTimerState,
    running: Boolean,
    paused: Boolean,
): WatchTimerAction {
    if (reported == followed) {
        return WatchTimerAction.NONE
    }

    return when (reported) {
        WatchTimerState.RUNNING, WatchTimerState.PAUSED -> when {
            !running -> if (followed.isRecording()) WatchTimerAction.NONE else WatchTimerAction.START
            paused -> WatchTimerAction.RESUME
            else -> WatchTimerAction.NONE
        }
        WatchTimerState.STOPPED -> if (running && !paused) WatchTimerAction.PAUSE else WatchTimerAction.NONE
        WatchTimerState.OFF -> if (running && followed?.activityOngoing == true) WatchTimerAction.STOP else WatchTimerAction.NONE
    }
}

private fun WatchTimerState?.isRecording(): Boolean =
    this == WatchTimerState.RUNNING || this == WatchTimerState.PAUSED

/**
 * True while the watch is known to have an activity going, which then decides when the session
 * ends. A report older than [maxAgeMillis] no longer counts: the watch may be out of reach, and
 * the equipment coming to a stop ends the session instead.
 */
internal fun isWatchActivityOngoing(
    report: WatchTimerReport?,
    nowMillis: Long,
    maxAgeMillis: Long = WATCH_TIMER_MAX_AGE_MS,
): Boolean =
    report != null && report.state.activityOngoing && nowMillis - report.receivedAtMillis <= maxAgeMillis

/** The data field repeats the timer state every few seconds while an activity is going. */
internal const val WATCH_TIMER_MAX_AGE_MS = 20_000L
