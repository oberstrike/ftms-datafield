package de.ma.ftms.bridge

import de.ma.ftms.bridge.runtime.BridgeSettings
import de.ma.ftms.bridge.runtime.DEFAULT_WORKOUT_SCREEN_FIELDS
import de.ma.ftms.bridge.runtime.DashboardMetricKey
import de.ma.ftms.bridge.runtime.WORKOUT_SCREEN_FIELD_COUNT
import de.ma.ftms.bridge.runtime.WORKOUT_SCREEN_FIELD_OPTIONS
import de.ma.ftms.bridge.runtime.workoutScreenFieldsFrom
import de.ma.ftms.bridge.runtime.workoutScreenFieldsWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkoutScreenFieldsTest {
    private val fields = listOf(
        DashboardMetricKey.SPEED,
        DashboardMetricKey.HEART_RATE,
        DashboardMetricKey.DISTANCE,
        DashboardMetricKey.PACE,
    )

    @Test
    fun theScreenStartsWithSpeedHeartRateDistanceAndPace() {
        assertEquals(fields, BridgeSettings().workoutScreenFields)
        assertEquals(WORKOUT_SCREEN_FIELD_COUNT, DEFAULT_WORKOUT_SCREEN_FIELDS.size)
        assertTrue(WORKOUT_SCREEN_FIELD_OPTIONS.containsAll(DEFAULT_WORKOUT_SCREEN_FIELDS))
    }

    @Test
    fun aFieldTakesAnotherValue() {
        assertEquals(
            listOf(DashboardMetricKey.SPEED, DashboardMetricKey.INCLINE, DashboardMetricKey.DISTANCE, DashboardMetricKey.PACE),
            workoutScreenFieldsWith(fields, 1, DashboardMetricKey.INCLINE),
        )
    }

    @Test
    fun aValueThatAnotherFieldShowsChangesPlacesWithTheOneItReplaces() {
        assertEquals(
            listOf(DashboardMetricKey.PACE, DashboardMetricKey.HEART_RATE, DashboardMetricKey.DISTANCE, DashboardMetricKey.SPEED),
            workoutScreenFieldsWith(fields, 0, DashboardMetricKey.PACE),
        )
        // Choosing what the field already shows changes nothing.
        assertEquals(fields, workoutScreenFieldsWith(fields, 2, DashboardMetricKey.DISTANCE))
    }

    @Test
    fun aFieldThatIsNotThereOrAValueThatIsNotOfferedChangesNothing() {
        assertEquals(fields, workoutScreenFieldsWith(fields, 4, DashboardMetricKey.INCLINE))
        assertEquals(fields, workoutScreenFieldsWith(fields, 0, DashboardMetricKey.MACHINE))
    }

    @Test
    fun aSavedChoiceIsFilledUpToFourDifferentValuesOnOffer() {
        assertEquals(fields, workoutScreenFieldsFrom(fields))
        assertEquals(
            listOf(DashboardMetricKey.INCLINE, DashboardMetricKey.SPEED, DashboardMetricKey.HEART_RATE, DashboardMetricKey.DISTANCE),
            workoutScreenFieldsFrom(listOf(DashboardMetricKey.INCLINE, DashboardMetricKey.MACHINE, DashboardMetricKey.INCLINE)),
        )
        assertEquals(
            listOf(DashboardMetricKey.POWER, DashboardMetricKey.PACE, DashboardMetricKey.ELAPSED, DashboardMetricKey.ASCENT),
            workoutScreenFieldsFrom(
                listOf(
                    DashboardMetricKey.POWER,
                    DashboardMetricKey.PACE,
                    DashboardMetricKey.ELAPSED,
                    DashboardMetricKey.ASCENT,
                    DashboardMetricKey.SPEED,
                ),
            ),
        )
        assertEquals(fields, workoutScreenFieldsFrom(emptyList()))
    }
}
