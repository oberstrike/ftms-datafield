package de.ma.ftms.bridge.runtime

/** How many values the workout screen shows next to the step. */
const val WORKOUT_SCREEN_FIELD_COUNT = 4

/** The values a field of the workout screen can show, in the order they are offered. */
val WORKOUT_SCREEN_FIELD_OPTIONS: List<DashboardMetricKey> = listOf(
    DashboardMetricKey.SPEED,
    DashboardMetricKey.PACE,
    DashboardMetricKey.AVG_PACE,
    DashboardMetricKey.HEART_RATE,
    DashboardMetricKey.DISTANCE,
    DashboardMetricKey.ELAPSED,
    DashboardMetricKey.INCLINE,
    DashboardMetricKey.ASCENT,
    DashboardMetricKey.CADENCE_STEP,
    DashboardMetricKey.POWER,
)

val DEFAULT_WORKOUT_SCREEN_FIELDS: List<DashboardMetricKey> = listOf(
    DashboardMetricKey.SPEED,
    DashboardMetricKey.HEART_RATE,
    DashboardMetricKey.DISTANCE,
    DashboardMetricKey.PACE,
)

/**
 * [saved] as the fields of the workout screen: every field shows another value, values the screen
 * does not offer are dropped, and fields left empty by that take what the defaults have to give.
 */
internal fun workoutScreenFieldsFrom(saved: List<DashboardMetricKey>): List<DashboardMetricKey> =
    (saved.filter { it in WORKOUT_SCREEN_FIELD_OPTIONS } + DEFAULT_WORKOUT_SCREEN_FIELDS + WORKOUT_SCREEN_FIELD_OPTIONS)
        .distinct()
        .take(WORKOUT_SCREEN_FIELD_COUNT)

/**
 * [fields] with [key] in the field at [index]. A value that another field shows changes places
 * with the one it replaces, so that no value stands there twice and none is lost by a tap.
 */
internal fun workoutScreenFieldsWith(
    fields: List<DashboardMetricKey>,
    index: Int,
    key: DashboardMetricKey,
): List<DashboardMetricKey> {
    if (index !in fields.indices || key !in WORKOUT_SCREEN_FIELD_OPTIONS) {
        return fields
    }

    val replaced = fields[index]
    return fields.mapIndexed { at, shown ->
        when {
            at == index -> key
            shown == key -> replaced
            else -> shown
        }
    }
}
