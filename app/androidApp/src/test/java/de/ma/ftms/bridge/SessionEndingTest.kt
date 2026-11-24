package de.ma.ftms.bridge

import de.ma.ftms.bridge.runtime.AutoStopPolicy
import de.ma.ftms.bridge.runtime.BridgeSettings
import de.ma.ftms.bridge.runtime.EquipmentType
import de.ma.ftms.bridge.runtime.isAutoStopWanted
import de.ma.ftms.bridge.runtime.SessionOdometer
import de.ma.ftms.bridge.runtime.SessionStats
import de.ma.ftms.bridge.runtime.WatchTimerAction
import de.ma.ftms.bridge.runtime.WatchTimerReport
import de.ma.ftms.bridge.runtime.WatchTimerState
import de.ma.ftms.bridge.runtime.isWatchActivityOngoing
import de.ma.ftms.bridge.runtime.watchTimerAction
import de.ma.ftms.core.DistanceSource
import de.ma.ftms.core.SmoothedTreadmillSample
import de.ma.ftms.core.TreadmillSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionOdometerTest {
    private fun totals(distanceM: Double, ascentM: Double = 0.0) =
        SmoothedTreadmillSample(
            raw = TreadmillSample(speedKmh = 6.0),
            distanceM = distanceM,
            ascentM = ascentM,
            distanceSource = DistanceSource.FTMS,
        )

    @Test
    fun startsAtZeroOnEquipmentThatWasAlreadyInUse() {
        val odometer = SessionOdometer()

        odometer.advance(totals(290.0, ascentM = 4.0), counted = true)
        assertEquals(0.0, odometer.distanceM, 0.0)
        assertEquals(0.0, odometer.ascentM, 0.0)

        odometer.advance(totals(300.0, ascentM = 4.5), counted = true)
        assertEquals(10.0, odometer.distanceM, 0.0)
        assertEquals(0.5, odometer.ascentM, 0.0)
    }

    @Test
    fun leavesOutWhatIsCoveredWhilePaused() {
        val odometer = SessionOdometer()
        odometer.advance(totals(100.0), counted = true)
        odometer.advance(totals(150.0), counted = true)

        odometer.advance(totals(180.0), counted = false)
        odometer.advance(totals(220.0), counted = false)
        assertEquals(50.0, odometer.distanceM, 0.0)

        odometer.advance(totals(230.0), counted = true)
        assertEquals(60.0, odometer.distanceM, 0.0)
    }

    @Test
    fun keepsWhatWasCountedWhenTheEquipmentIsReset() {
        val odometer = SessionOdometer()
        odometer.advance(totals(1000.0, ascentM = 10.0), counted = true)
        odometer.advance(totals(1500.0, ascentM = 15.0), counted = true)

        odometer.advance(totals(0.0, ascentM = 0.0), counted = true)
        assertEquals(500.0, odometer.distanceM, 0.0)
        assertEquals(5.0, odometer.ascentM, 0.0)

        odometer.advance(totals(40.0, ascentM = 1.0), counted = true)
        assertEquals(540.0, odometer.distanceM, 0.0)
        assertEquals(6.0, odometer.ascentM, 0.0)
    }

    @Test
    fun startsOverAfterAReset() {
        val odometer = SessionOdometer()
        odometer.advance(totals(100.0), counted = true)
        odometer.advance(totals(200.0), counted = true)

        odometer.reset()
        odometer.advance(totals(250.0), counted = true)

        assertEquals(0.0, odometer.distanceM, 0.0)
    }
}

class AutoStopPolicyTest {
    private fun policy() = AutoStopPolicy(standstillMillis = 30_000L, silenceMillis = 90_000L)

    @Test
    fun leavesASessionAloneWhoseEquipmentHasNotMovedYet() {
        val policy = policy()

        policy.onMovementReported(moving = false, nowMillis = 0L)
        policy.onMovementReported(moving = null, nowMillis = 1_000L)

        assertNull(policy.stopDueSince(nowMillis = 10 * 60_000L))
    }

    @Test
    fun endsTheSessionAsOfTheMomentTheEquipmentStopped() {
        val policy = policy()
        policy.onMovementReported(moving = true, nowMillis = 1_000L)
        policy.onMovementReported(moving = false, nowMillis = 5_000L)
        policy.onMovementReported(moving = false, nowMillis = 20_000L)

        assertNull(policy.stopDueSince(nowMillis = 34_999L))
        assertEquals(5_000L, policy.stopDueSince(nowMillis = 35_000L))
    }

    @Test
    fun aShortStopDoesNotEndTheSession() {
        val policy = policy()
        policy.onMovementReported(moving = true, nowMillis = 0L)
        policy.onMovementReported(moving = false, nowMillis = 1_000L)
        policy.onMovementReported(moving = true, nowMillis = 25_000L)
        policy.onMovementReported(moving = false, nowMillis = 26_000L)

        assertNull(policy.stopDueSince(nowMillis = 40_000L))
        assertEquals(26_000L, policy.stopDueSince(nowMillis = 56_000L))
    }

    @Test
    fun samplesThatSayNothingAboutMovementChangeNothing() {
        val policy = policy()
        policy.onMovementReported(moving = true, nowMillis = 0L)
        policy.onMovementReported(moving = false, nowMillis = 1_000L)
        policy.onMovementReported(moving = null, nowMillis = 15_000L)

        assertEquals(1_000L, policy.stopDueSince(nowMillis = 31_000L))
    }

    @Test
    fun endsTheSessionWhenTheEquipmentFallsSilent() {
        val policy = policy()
        policy.onMovementReported(moving = true, nowMillis = 0L)
        policy.onMovementReported(moving = true, nowMillis = 10_000L)

        assertNull(policy.stopDueSince(nowMillis = 99_999L))
        assertEquals(10_000L, policy.stopDueSince(nowMillis = 100_000L))
    }

    @Test
    fun onlyTreadmillSessionsEndByThemselves() {
        val on = BridgeSettings(autoStop = true)

        assertTrue(isAutoStopWanted(on, EquipmentType.TREADMILL))
        EquipmentType.entries.filter { it != EquipmentType.TREADMILL }.forEach { type ->
            assertFalse(isAutoStopWanted(on, type))
        }
        assertFalse(isAutoStopWanted(BridgeSettings(autoStop = false), EquipmentType.TREADMILL))
    }

    @Test
    fun startsOverAfterAReset() {
        val policy = policy()
        policy.onMovementReported(moving = true, nowMillis = 0L)
        policy.onMovementReported(moving = false, nowMillis = 1_000L)

        policy.reset()

        assertNull(policy.stopDueSince(nowMillis = 10 * 60_000L))
    }
}

class WatchTimerTest {
    private fun action(
        followed: WatchTimerState?,
        reported: WatchTimerState,
        running: Boolean = false,
        paused: Boolean = false,
    ) = watchTimerAction(followed, reported, running, paused)

    @Test
    fun readsTheStatesTheDataFieldSends() {
        assertEquals(WatchTimerState.OFF, WatchTimerState.fromWire(0))
        assertEquals(WatchTimerState.STOPPED, WatchTimerState.fromWire(1))
        assertEquals(WatchTimerState.PAUSED, WatchTimerState.fromWire(2))
        assertEquals(WatchTimerState.RUNNING, WatchTimerState.fromWire(3))
        assertNull(WatchTimerState.fromWire(4))
        assertNull(WatchTimerState.fromWire(null))
    }

    @Test
    fun startsTheSessionWithTheActivity() {
        assertEquals(WatchTimerAction.START, action(followed = WatchTimerState.OFF, reported = WatchTimerState.RUNNING))
        // The app was opened while the activity already recorded.
        assertEquals(WatchTimerAction.START, action(followed = null, reported = WatchTimerState.RUNNING))
        assertEquals(WatchTimerAction.START, action(followed = null, reported = WatchTimerState.PAUSED))
    }

    @Test
    fun leavesASessionAloneThatAlreadyRuns() {
        assertEquals(
            WatchTimerAction.NONE,
            action(followed = WatchTimerState.OFF, reported = WatchTimerState.RUNNING, running = true),
        )
    }

    @Test
    fun pausesAndResumesWithTheStopButtonOfTheWatch() {
        assertEquals(
            WatchTimerAction.PAUSE,
            action(followed = WatchTimerState.RUNNING, reported = WatchTimerState.STOPPED, running = true),
        )
        assertEquals(
            WatchTimerAction.RESUME,
            action(followed = WatchTimerState.STOPPED, reported = WatchTimerState.RUNNING, running = true, paused = true),
        )
    }

    @Test
    fun autoPauseOfTheWatchDoesNotPauseTheSession() {
        assertEquals(
            WatchTimerAction.NONE,
            action(followed = WatchTimerState.RUNNING, reported = WatchTimerState.PAUSED, running = true),
        )
    }

    @Test
    fun endsTheSessionWhenTheActivityIsSavedOrDiscarded() {
        assertEquals(
            WatchTimerAction.STOP,
            action(followed = WatchTimerState.STOPPED, reported = WatchTimerState.OFF, running = true, paused = true),
        )
        assertEquals(
            WatchTimerAction.STOP,
            action(followed = WatchTimerState.RUNNING, reported = WatchTimerState.OFF, running = true),
        )
    }

    @Test
    fun aWatchWithoutAnActivityDoesNotEndASessionBegunInTheApp() {
        assertEquals(WatchTimerAction.NONE, action(followed = null, reported = WatchTimerState.OFF, running = true))
    }

    @Test
    fun repeatedReportsDoNotUndoWhatTheUserDidInTheApp() {
        // Stopped by hand while the activity goes on: the next report of "recording" changes nothing.
        assertEquals(WatchTimerAction.NONE, action(followed = WatchTimerState.RUNNING, reported = WatchTimerState.RUNNING))
        assertEquals(WatchTimerAction.NONE, action(followed = WatchTimerState.RUNNING, reported = WatchTimerState.PAUSED))
        // Paused by hand: likewise.
        assertEquals(
            WatchTimerAction.NONE,
            action(followed = WatchTimerState.RUNNING, reported = WatchTimerState.RUNNING, running = true, paused = true),
        )
    }

    @Test
    fun theWatchDecidesOnlyWhileItsReportsAreRecent() {
        val recording = WatchTimerReport(WatchTimerState.RUNNING, receivedAtMillis = 1_000L)

        assertTrue(isWatchActivityOngoing(recording, nowMillis = 21_000L))
        assertFalse(isWatchActivityOngoing(recording, nowMillis = 21_001L))
        assertTrue(isWatchActivityOngoing(WatchTimerReport(WatchTimerState.STOPPED, 1_000L), nowMillis = 2_000L))
        assertFalse(isWatchActivityOngoing(WatchTimerReport(WatchTimerState.OFF, 1_000L), nowMillis = 2_000L))
        assertFalse(isWatchActivityOngoing(null, nowMillis = 2_000L))
    }

    @Test
    fun endingAndFollowingAreOnByDefault() {
        val settings = BridgeSettings()

        assertTrue(settings.autoStop)
        assertTrue(settings.followWatchTimer)
    }
}

class SessionStatsEndTest {
    @Test
    fun endsAsOfAnEarlierMomentButNeverBeforeTheStart() {
        var now = 10_000L
        val stats = SessionStats(nowMillis = { now })
        stats.start()
        now = 100_000L

        assertEquals(70_000L, stats.finish("stopped", endedAtMillis = 70_000L).stoppedAtMillis)
        assertEquals(10_000L, stats.finish("stopped", endedAtMillis = 5_000L).stoppedAtMillis)
        assertEquals(100_000L, stats.finish("stopped").stoppedAtMillis)
    }
}
