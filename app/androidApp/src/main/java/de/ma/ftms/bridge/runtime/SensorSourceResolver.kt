package de.ma.ftms.bridge.runtime

import de.ma.ftms.core.FtmsSample
import de.ma.ftms.core.SensorReadings

/** Where a value is taken from when both the equipment and the watch can deliver it. */
enum class ValueSource {
    /** The FTMS equipment; for treadmill power this includes the estimate from the settings. */
    EQUIPMENT,

    /** The Garmin watch and the sensors paired with it, such as a chest strap or power meter pedals. */
    WATCH,
}

/** The values that have a source to choose. */
enum class SourcedMetric {
    HEART_RATE,
    POWER,
    CADENCE,
}

/** The chosen source of each value for one equipment type. */
data class SensorSources(
    val heartRate: ValueSource = ValueSource.WATCH,
    val power: ValueSource = ValueSource.EQUIPMENT,
    val cadence: ValueSource = ValueSource.EQUIPMENT,
) {
    fun of(metric: SourcedMetric): ValueSource =
        when (metric) {
            SourcedMetric.HEART_RATE -> heartRate
            SourcedMetric.POWER -> power
            SourcedMetric.CADENCE -> cadence
        }

    fun with(metric: SourcedMetric, source: ValueSource): SensorSources =
        when (metric) {
            SourcedMetric.HEART_RATE -> copy(heartRate = source)
            SourcedMetric.POWER -> copy(power = source)
            SourcedMetric.CADENCE -> copy(cadence = source)
        }
}

/** The last values received from the watch and when they arrived. */
internal data class WatchReadings(
    val readings: SensorReadings,
    val timestampMillis: Long,
)

/** The watch's values, unless they are older than [WATCH_READINGS_MAX_AGE_MS]. */
internal fun WatchReadings?.freshAt(nowMillis: Long): SensorReadings? =
    this?.takeIf { nowMillis - it.timestampMillis <= WATCH_READINGS_MAX_AGE_MS }?.readings

/**
 * Notes what the equipment and the watch each report and sets heart rate, power and cadence to the
 * source chosen for [equipmentType].
 *
 * The chosen source is never replaced by the other one: if it delivers nothing, the value is empty.
 * A value that silently changed its source would look like a jump in the measurement.
 */
internal fun FtmsSample.withResolvedSources(
    settings: BridgeSettings,
    equipmentType: EquipmentType,
    watch: WatchReadings?,
    nowMillis: Long,
): FtmsSample {
    val fromEquipment = withResolvedPower(settings, equipmentType)
    val equipment = SensorReadings(
        powerW = fromEquipment.powerW,
        heartRateBpm = fromEquipment.heartRateBpm,
        cadenceRpm = fromEquipment.cadenceOrStepRate(),
    )
    val watchReadings = watch.freshAt(nowMillis)
    val sources = settings.sensorSources(equipmentType)
    // What a watch calls cadence while running is the step rate.
    val cadenceIsStepRate = equipmentType.showsPace
    val cadenceFromWatch = sources.cadence == ValueSource.WATCH

    return fromEquipment.copy(
        heartRateBpm = when (sources.heartRate) {
            ValueSource.EQUIPMENT -> equipment.heartRateBpm
            ValueSource.WATCH -> watchReadings?.heartRateBpm
        },
        powerW = when (sources.power) {
            ValueSource.EQUIPMENT -> equipment.powerW
            ValueSource.WATCH -> watchReadings?.powerW
        },
        cadenceRpm = when {
            !cadenceFromWatch -> fromEquipment.cadenceRpm
            cadenceIsStepRate -> null
            else -> watchReadings?.cadenceRpm
        },
        stepRateSpm = when {
            !cadenceFromWatch -> fromEquipment.stepRateSpm
            cadenceIsStepRate -> watchReadings?.cadenceRpm
            else -> null
        },
        equipment = equipment.takeUnless { it.isEmpty },
        watch = watchReadings?.takeUnless { it.isEmpty },
    )
}

/** Maps the value stored by versions that only knew a source for the heart rate. */
internal fun legacyHeartRateSource(stored: String?): ValueSource =
    if (stored == "MACHINE") ValueSource.EQUIPMENT else ValueSource.WATCH

internal fun String?.toValueSource(default: ValueSource): ValueSource =
    this?.let { value -> ValueSource.entries.firstOrNull { it.name == value } } ?: default

internal const val WATCH_READINGS_MAX_AGE_MS = 5_000L
