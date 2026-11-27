package de.ma.ftms.bridge

import de.ma.ftms.bridge.runtime.keptMarks
import de.ma.ftms.bridge.runtime.toggledMark
import de.ma.ftms.core.storage.WorkoutSessionRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class HistoryMarksTest {
    private val sessions = listOf(session("monday"), session("tuesday"), session("now", finalStatus = "running"))

    @Test
    fun aMarkIsSetAndTakenAwayAgain() {
        val one = toggledMark(emptySet(), sessions, "monday")
        val two = toggledMark(one, sessions, "tuesday")

        assertEquals(setOf("monday"), one)
        assertEquals(setOf("monday", "tuesday"), two)
        assertEquals(setOf("tuesday"), toggledMark(two, sessions, "monday"))
    }

    @Test
    fun aSessionThatIsStillRecordingOrGoneGetsNoMark() {
        val marked = setOf("monday")

        assertSame(marked, toggledMark(marked, sessions, "now"))
        assertSame(marked, toggledMark(marked, sessions, "deleted long ago"))
    }

    @Test
    fun marksGoWithTheirSessions() {
        val marked = setOf("monday", "tuesday")

        assertSame(marked, keptMarks(marked, sessions))
        assertEquals(setOf("tuesday"), keptMarks(marked, sessions.drop(1)))
        assertEquals(emptySet(), keptMarks(marked, emptyList()))
        // A mark never stays on a session that turned out to be recording.
        assertEquals(setOf("monday"), keptMarks(setOf("monday", "now"), sessions))
    }

    private fun session(sessionId: String, finalStatus: String = "stopped"): WorkoutSessionRecord =
        WorkoutSessionRecord(
            sessionId = sessionId,
            startedAtMillis = 0L,
            stoppedAtMillis = 60_000L,
            finalStatus = finalStatus,
            treadmillName = null,
            treadmillAddress = null,
            garminName = null,
            garminId = null,
            packetCount = 0,
            sendSuccessCount = 0,
            sendFailureCount = 0,
            lastError = "",
            finalDistanceM = 0.0,
            finalAscentM = 0.0,
            finalSpeedKmh = 0.0,
            updatedAtMillis = 60_000L,
        )
}
