package de.ma.ftms.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
