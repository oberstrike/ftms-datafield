package de.ma.ftms.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FitShowStatusTest {
    private fun bytes(hex: String) = hex.split(" ").map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun readsTheStepCountOfARunningTreadmill() {
        // Recorded from an FS-BC11B7: 6.0 km/h, 184 s, 290 m, 341 steps.
        val status = FitShowStatusParser.parse(bytes("02 51 03 3C 03 B8 00 22 01 D5 00 55 01 00 00 77 03"))

        assertEquals(FitShowStatus(running = true, stepCount = 341), status)
    }

    @Test
    fun aTreadmillAtRestReportsNoStepCount() {
        assertEquals(FitShowStatus(running = false, stepCount = null), FitShowStatusParser.parse(bytes("02 51 00 51 03")))
    }

    @Test
    fun rejectsWhatIsNotAValidStatusPacket() {
        // Wrong checksum, another command, no end byte, too short.
        assertNull(FitShowStatusParser.parse(bytes("02 51 03 3C 03 B8 00 22 01 D5 00 55 01 00 00 78 03")))
        assertNull(FitShowStatusParser.parse(bytes("02 53 00 53 03")))
        assertNull(FitShowStatusParser.parse(bytes("02 51 00 51 04")))
        assertNull(FitShowStatusParser.parse(bytes("02 51 03")))
    }

    @Test
    fun turnsTheStepCountIntoStepsPerMinute() {
        val estimator = StepRateEstimator(windowMillis = 15_000L, minSpanMillis = 5_000L, idleMillis = 4_000L)

        // Two steps a second, reported twice a second.
        var rate: Double? = null
        for (tick in 0..40) {
            rate = estimator.update(stepCount = 100 + tick, nowMillis = tick * 500L)
        }

        assertEquals(120.0, rate!!, 0.5)
    }

    @Test
    fun reportsNothingUntilEnoughWasObserved() {
        val estimator = StepRateEstimator(windowMillis = 15_000L, minSpanMillis = 5_000L, idleMillis = 4_000L)

        assertNull(estimator.update(stepCount = 10, nowMillis = 0L))
        assertNull(estimator.update(stepCount = 12, nowMillis = 1_000L))
        assertNull(estimator.update(stepCount = 18, nowMillis = 4_000L))
        assertEquals(120.0, estimator.update(stepCount = 20, nowMillis = 5_000L)!!, 0.5)
    }

    @Test
    fun equipmentThatDoesNotCountStepsReportsNoRate() {
        val estimator = StepRateEstimator()

        var rate: Double? = 1.0
        for (tick in 0..60) {
            rate = estimator.update(stepCount = 0, nowMillis = tick * 500L)
        }

        assertNull(rate)
    }

    @Test
    fun theRateIsZeroOnceNobodyWalks() {
        val estimator = StepRateEstimator(windowMillis = 15_000L, minSpanMillis = 5_000L, idleMillis = 4_000L)
        for (tick in 0..20) {
            estimator.update(stepCount = tick, nowMillis = tick * 500L)
        }

        assertEquals(0.0, estimator.update(stepCount = 20, nowMillis = 14_500L))
    }

    @Test
    fun startsOverWhenTheTreadmillResetsItsCount() {
        val estimator = StepRateEstimator(windowMillis = 15_000L, minSpanMillis = 5_000L, idleMillis = 4_000L)
        for (tick in 0..20) {
            estimator.update(stepCount = 500 + tick, nowMillis = tick * 500L)
        }

        assertNull(estimator.update(stepCount = 0, nowMillis = 10_500L))
        assertNull(estimator.update(stepCount = 1, nowMillis = 11_000L))
    }

    @Test
    fun aSteadyRhythmStaysCalmAlthoughTheCountComesInWholeSteps() {
        val estimator = StepRateEstimator()

        // 163 steps a minute, reported twice a second as the whole steps done so far.
        val rates = (0..120).mapNotNull { tick ->
            val nowMillis = tick * 500L
            estimator.update(stepCount = (nowMillis * 163 / 60_000).toInt(), nowMillis = nowMillis)
        }

        val settled = rates.drop(30)
        assertTrue(settled.all { abs(it - 163.0) < 2.5 }, "rates $settled")
    }

    @Test
    fun followsAChangeOfRhythmWithinSeconds() {
        val estimator = StepRateEstimator()
        var steps = 0.0
        var rate: Double? = null

        // Half a minute of walking at 120, then running at 164.
        for (tick in 1..60) {
            steps += 120.0 / 120
            rate = estimator.update(stepCount = steps.toInt(), nowMillis = tick * 500L)
        }
        assertEquals(120.0, rate!!, 3.0)
        for (tick in 61..74) {
            steps += 164.0 / 120
            rate = estimator.update(stepCount = steps.toInt(), nowMillis = tick * 500L)
        }

        // Seven seconds later; a plain window of fifteen seconds would still be near 140.
        assertEquals(164.0, rate!!, 8.0)
    }

    @Test
    fun startsAfreshWhenWalkingResumesAfterAStandstill() {
        val estimator = StepRateEstimator()
        for (tick in 0..40) {
            estimator.update(stepCount = tick, nowMillis = tick * 500L)
        }
        // Ten seconds beside the belt.
        for (tick in 41..60) {
            assertTrue((estimator.update(stepCount = 40, nowMillis = tick * 500L) ?: 0.0) <= 120.0)
        }
        assertEquals(0.0, estimator.update(stepCount = 40, nowMillis = 30_500L))

        // Back on it at 120: nothing until five seconds were seen, then the new rhythm at once.
        assertNull(estimator.update(stepCount = 41, nowMillis = 31_000L))
        var rate: Double? = null
        for (tick in 63..72) {
            rate = estimator.update(stepCount = tick - 21, nowMillis = tick * 500L)
        }
        assertEquals(120.0, rate!!, 3.0)
    }
}
