package de.ma.ftms.bridge.garmin

import com.garmin.android.connectiq.ConnectIQ
import de.ma.ftms.bridge.runtime.GarminConnectProblem
import de.ma.ftms.bridge.runtime.WATCH_APP_ID
import de.ma.ftms.bridge.runtime.WATCH_APP_IDS
import de.ma.ftms.bridge.runtime.WatchTimerState
import de.ma.ftms.core.SensorReadings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GarminBridgeClientTest {
    @Test
    fun mapsEverySdkInitErrorToAGarminConnectProblem() {
        assertEquals(
            GarminConnectProblem.NOT_INSTALLED,
            garminConnectProblemFor(ConnectIQ.IQSdkErrorStatus.GCM_NOT_INSTALLED),
        )
        assertEquals(
            GarminConnectProblem.UPGRADE_NEEDED,
            garminConnectProblemFor(ConnectIQ.IQSdkErrorStatus.GCM_UPGRADE_NEEDED),
        )
        assertEquals(
            GarminConnectProblem.SERVICE_ERROR,
            garminConnectProblemFor(ConnectIQ.IQSdkErrorStatus.SERVICE_ERROR),
        )
    }

    @Test
    fun sendsToBaseAppWhenInstalledAppsAreUnknown() {
        assertEquals(listOf(WATCH_APP_ID), compatibleAppIdsForSend(null))
        assertEquals(listOf(WATCH_APP_ID), compatibleAppIdsForSend(emptySet()))
    }

    @Test
    fun sendsToInstalledCompatibleVariants() {
        val installedVariant = WATCH_APP_IDS.last()

        assertEquals(listOf(installedVariant), compatibleAppIdsForSend(setOf(installedVariant, "not-compatible")))
    }

    @Test
    fun keepsConfiguredAppOrderForMultipleInstalledVariants() {
        val installed = setOf(WATCH_APP_IDS[3], WATCH_APP_IDS[1], WATCH_APP_IDS[2])

        assertEquals(
            listOf(WATCH_APP_IDS[1], WATCH_APP_IDS[2], WATCH_APP_IDS[3]),
            compatibleAppIdsForSend(installed),
        )
    }

    @Test
    fun letsOneMessagePerVariantBeOnItsWay() {
        val gate = SendGate(inFlightTimeoutMillis = 20_000L, failureBackoffMillis = 10_000L)

        assertEquals(true, gate.tryAcquire("watch/a", nowMillis = 0L))
        assertEquals(false, gate.tryAcquire("watch/a", nowMillis = 1_000L))
        // Another variant is not held back by it.
        assertEquals(true, gate.tryAcquire("watch/b", nowMillis = 1_000L))

        gate.onResult("watch/a", delivered = true, nowMillis = 1_500L)
        assertEquals(true, gate.tryAcquire("watch/a", nowMillis = 2_000L))
    }

    @Test
    fun leavesAVariantAloneThatJustFailed() {
        val gate = SendGate(inFlightTimeoutMillis = 20_000L, failureBackoffMillis = 10_000L)
        gate.tryAcquire("watch/a", nowMillis = 0L)
        gate.onResult("watch/a", delivered = false, nowMillis = 3_000L)

        assertEquals(false, gate.tryAcquire("watch/a", nowMillis = 12_999L))
        assertEquals(true, gate.tryAcquire("watch/a", nowMillis = 13_000L))
    }

    @Test
    fun writesAtOnceToAVariantThatWasHeardFrom() {
        val gate = SendGate(inFlightTimeoutMillis = 20_000L, failureBackoffMillis = 10_000L)
        gate.tryAcquire("watch/a", nowMillis = 0L)
        gate.onResult("watch/a", delivered = false, nowMillis = 1_000L)

        gate.onHeardFrom("watch/a", nowMillis = 1_200L)

        assertEquals(true, gate.tryAcquire("watch/a", nowMillis = 1_500L))
    }

    @Test
    fun samplesGoOnlyToTheVariantThatRuns() {
        val gate = SendGate(liveMillis = 30_000L)
        val all = listOf("watch/a", "watch/b", "watch/c")

        // Nothing heard yet: any of them may be the one.
        assertEquals(all, gate.liveAmong(all, nowMillis = 0L))

        gate.onHeardFrom("watch/b", nowMillis = 1_000L)
        assertEquals(listOf("watch/b"), gate.liveAmong(all, nowMillis = 31_000L))
        // Silent for too long: back to all of them.
        assertEquals(all, gate.liveAmong(all, nowMillis = 31_001L))
    }

    @Test
    fun aMessageWithoutAnAnswerStopsCountingAfterAWhile() {
        val gate = SendGate(inFlightTimeoutMillis = 20_000L, failureBackoffMillis = 10_000L)
        gate.onSentRegardless("watch/a", nowMillis = 0L)

        assertEquals(false, gate.tryAcquire("watch/a", nowMillis = 19_999L))
        assertEquals(true, gate.tryAcquire("watch/a", nowMillis = 20_000L))
    }

    @Test
    fun namesAVariantTheSameHoweverItsIdIsWritten() {
        assertEquals(
            sendTarget(7L, "8eb0b6152ef04aa7a1687c67ce46bfdf"),
            sendTarget(7L, "8EB0B615-2EF0-4AA7-A168-7C67CE46BFDF"),
        )
    }

    @Test
    fun readsTheActivityTimerStateFromTheWatch() {
        assertEquals(
            WatchTimerState.RUNNING,
            parseWatchTimerState(listOf(mapOf("type" to "garmin_hr", "hr" to 142, "timer" to 3))),
        )
        // A message may carry the timer state alone, without a sensor value.
        assertEquals(WatchTimerState.OFF, parseWatchTimerState(listOf(mapOf("type" to "garmin_hr", "timer" to 0))))
        assertNull(parseWatchSensorMessage(listOf(mapOf("type" to "garmin_hr", "timer" to 0))))
        // Data fields from before the timer was reported, other messages and unknown states.
        assertNull(parseWatchTimerState(listOf(mapOf("type" to "garmin_hr", "hr" to 142))))
        assertNull(parseWatchTimerState(listOf(mapOf("type" to "other", "timer" to 3))))
        assertNull(parseWatchTimerState(listOf(mapOf("type" to "garmin_hr", "timer" to 7))))
    }

    @Test
    fun readsHeartRatePowerAndCadenceFromTheWatch() {
        assertEquals(
            SensorReadings(powerW = 205, heartRateBpm = 142, cadenceRpm = 88.0),
            parseWatchSensorMessage(listOf(mapOf("type" to "garmin_hr", "hr" to 142, "pwr" to 205, "cad" to 88))),
        )
        assertEquals(
            SensorReadings(powerW = 0, heartRateBpm = null, cadenceRpm = 0.0),
            parseWatchSensorMessage(listOf(mapOf("type" to "garmin_hr", "pwr" to 0, "cad" to "0"))),
        )
    }

    @Test
    fun readsTheHeartRateOnlyMessageOfOlderDataFields() {
        assertEquals(
            SensorReadings(heartRateBpm = 142),
            parseWatchSensorMessage(listOf(mapOf("type" to "garmin_hr", "hr" to 142))),
        )
        assertEquals(
            SensorReadings(heartRateBpm = 143),
            parseWatchSensorMessage(listOf(mapOf("type" to "garmin_hr", "hr" to "143"))),
        )
    }

    @Test
    fun dropsValuesOutsideWhatASensorCanMeasure() {
        assertEquals(
            SensorReadings(heartRateBpm = 142),
            parseWatchSensorMessage(listOf(mapOf("type" to "garmin_hr", "hr" to 142, "pwr" to -5, "cad" to 900))),
        )
        assertEquals(
            SensorReadings(powerW = 180),
            parseWatchSensorMessage(listOf(mapOf("type" to "garmin_hr", "hr" to 0, "pwr" to 180))),
        )
    }

    @Test
    fun ignoresOtherMessagesAndOnesWithoutAValue() {
        assertNull(parseWatchSensorMessage(listOf(mapOf("type" to "other", "hr" to 142))))
        assertNull(parseWatchSensorMessage(listOf(mapOf("type" to "garmin_hr", "hr" to 0))))
        assertNull(parseWatchSensorMessage(listOf(mapOf("type" to "garmin_hr", "hr" to 300))))
        assertNull(parseWatchSensorMessage(listOf(mapOf("type" to "garmin_hr"))))
        assertNull(parseWatchSensorMessage(listOf("not-a-map")))
    }

    @Test
    fun describesTheWorkoutMessageOfTheDataFieldAsOneSortedLine() {
        val message = listOf(
            mapOf(
                "type" to "garmin_workout",
                "started" to 1,
                "laps" to 2,
                "done" to 2,
                "cur" to mapOf("name" to "Fast", "int" to 0, "step" to mapOf("tt" to 4, "dv" to 120, "dt" to 0, "lo" to 1200, "hi" to 1220)),
            ),
        )

        assertEquals(
            "cur={int=0 name=\"Fast\" step={dt=0 dv=120 hi=1220 lo=1200 tt=4}} done=2 laps=2 started=1",
            describeWatchWorkoutMessage(message),
        )
    }

    @Test
    fun aSensorMessageIsNoWorkoutMessageAndTheOtherWayRound() {
        assertNull(describeWatchWorkoutMessage(listOf(mapOf("type" to "garmin_hr", "hr" to 142))))
        assertNull(parseWatchSensorMessage(listOf(mapOf("type" to "garmin_workout", "hr" to 142))))
        assertNull(parseWatchTimerState(listOf(mapOf("type" to "garmin_workout", "timer" to 3))))
        assertEquals("err=\"unsupported\"", describeWatchWorkoutMessage(listOf(mapOf("type" to "garmin_workout", "err" to "unsupported"))))
    }
}
