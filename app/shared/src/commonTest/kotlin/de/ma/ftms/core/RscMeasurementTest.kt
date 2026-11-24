package de.ma.ftms.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RscMeasurementTest {
    @Test
    fun encodesSpeedCadenceAndDistanceInTheUnitsOfTheCharacteristic() {
        // 10.8 km/h = 3 m/s = 768 in 1/256 m/s; 1234.5 m = 12345 tenths of a metre.
        val bytes = RscMeasurement.encode(speedKmh = 10.8, stepRateSpm = 168.4, totalDistanceM = 1234.5)

        assertContentEquals(hex("06 00 03 a8 39 30 00 00"), bytes)
    }

    @Test
    fun isReadBackByTheParserWithTheSameValues() {
        val sample = RscMeasurementParser.parse(
            RscMeasurement.encode(speedKmh = 9.0, stepRateSpm = 170.0, totalDistanceM = 2500.0),
        )

        assertEquals(9.0, sample.speedKmh)
        assertEquals(170.0, sample.stepRateSpm)
        assertEquals(2500, sample.distanceM)
    }

    @Test
    fun marksSlowMovementAsWalkingAndSendsZeroForAnUnknownStepRate() {
        val bytes = RscMeasurement.encode(speedKmh = 4.0, stepRateSpm = null, totalDistanceM = 0.0)

        assertEquals(RscMeasurement.FLAG_TOTAL_DISTANCE_PRESENT, bytes[0].toInt())
        assertEquals(0, bytes[3].toInt())
    }

    @Test
    fun clampsValuesThatDoNotFitTheirField() {
        val bytes = RscMeasurement.encode(speedKmh = -3.0, stepRateSpm = 400.0, totalDistanceM = -5.0)

        assertContentEquals(hex("02 00 00 ff 00 00 00 00"), bytes)
    }

    @Test
    fun offersTheTotalDistanceAsItsOnlyFeature() {
        assertContentEquals(hex("02 00"), RscMeasurement.feature())
    }

    @Test
    fun acceptsACumulativeValueAndDeclinesEverythingElse() {
        assertContentEquals(hex("10 01 01"), ScControlPoint.respond(hex("01 00 00 00 00")))
        assertContentEquals(hex("10 01 03"), ScControlPoint.respond(hex("01 00")))
        assertContentEquals(hex("10 02 02"), ScControlPoint.respond(hex("02")))
        assertContentEquals(hex("10 04 02"), ScControlPoint.respond(hex("04")))
        assertNull(ScControlPoint.respond(ByteArray(0)))
    }
}
