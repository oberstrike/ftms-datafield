package de.ma.ftms.bridge.runtime

import de.ma.ftms.core.FtmsMachineKind

/**
 * The kind of FTMS equipment the user says they are using. It decides how the dashboard and the
 * history present a session; the data itself is read from whatever the equipment sends.
 */
enum class EquipmentType(
    /** The matching [FtmsMachineKind] value, which is also what sessions store. */
    val machineKind: Int,
    /** Pace is a running value. */
    val showsPace: Boolean,
    /** Incline and ascent only exist where the equipment can tilt. */
    val showsClimb: Boolean,
    /** A resistance level only exists where the user works against a brake. */
    val showsResistance: Boolean,
) {
    TREADMILL(FtmsMachineKind.TREADMILL, showsPace = true, showsClimb = true, showsResistance = false),
    INDOOR_BIKE(FtmsMachineKind.INDOOR_BIKE, showsPace = false, showsClimb = false, showsResistance = true),
    CROSS_TRAINER(FtmsMachineKind.CROSS_TRAINER, showsPace = false, showsClimb = true, showsResistance = true),
    ;

    /** While running, the cadence is the step rate (steps per minute); otherwise revolutions. */
    val cadenceIsStepRate: Boolean
        get() = showsPace

    companion object {
        /** Type for a stored or received machine kind; unknown kinds count as a treadmill. */
        fun fromMachineKind(kind: Int): EquipmentType =
            entries.firstOrNull { it.machineKind == kind } ?: TREADMILL

        fun fromName(name: String?): EquipmentType =
            entries.firstOrNull { it.name == name } ?: TREADMILL
    }
}

/** The live values and charts shown on the dashboard for one equipment type. */
data class DashboardLayout(
    val metrics: List<DashboardMetricKey>,
    val graphs: List<DashboardMetricKey>,
) {
    /**
     * This layout without what [type] does not have, such as a pace on a bike. A stored layout can
     * hold such values from an earlier version. A list that would end up empty is the default.
     */
    fun forType(type: EquipmentType): DashboardLayout {
        val default = defaultDashboardLayout(type)
        return DashboardLayout(
            metrics = metrics.filter { it.appliesTo(type) }.ifEmpty { default.metrics },
            graphs = graphs.filter { it in dashboardGraphOptions(type) }.ifEmpty { default.graphs },
        )
    }
}

/** Metrics offered for [type], in the order the dashboard shows them. */
fun dashboardMetricOptions(type: EquipmentType): List<DashboardMetricKey> {
    val applicable = DashboardMetricKey.entries.filter { it.appliesTo(type) }
    return if (type == EquipmentType.INDOOR_BIKE) (INDOOR_BIKE_METRICS + applicable).distinct() else applicable
}

/** Charts offered for [type], in the order the dashboard shows them. */
fun dashboardGraphOptions(type: EquipmentType): List<DashboardMetricKey> =
    if (type == EquipmentType.INDOOR_BIKE) {
        INDOOR_BIKE_GRAPHS
    } else {
        DEFAULT_DASHBOARD_GRAPHS.filter { it.appliesTo(type) }
    }

fun defaultDashboardLayout(type: EquipmentType): DashboardLayout =
    when (type) {
        EquipmentType.TREADMILL -> DashboardLayout(
            metrics = DEFAULT_DASHBOARD_METRICS.filter { it.appliesTo(EquipmentType.TREADMILL) },
            graphs = dashboardGraphOptions(EquipmentType.TREADMILL),
        )
        EquipmentType.INDOOR_BIKE -> DashboardLayout(INDOOR_BIKE_METRICS, INDOOR_BIKE_GRAPHS)
        EquipmentType.CROSS_TRAINER -> DashboardLayout(
            metrics = dashboardMetricOptions(EquipmentType.CROSS_TRAINER),
            graphs = dashboardGraphOptions(EquipmentType.CROSS_TRAINER),
        )
    }

private val INDOOR_BIKE_METRICS = listOf(
    DashboardMetricKey.POWER,
    DashboardMetricKey.CADENCE_STEP,
    DashboardMetricKey.SPEED,
    DashboardMetricKey.HEART_RATE,
    DashboardMetricKey.DISTANCE,
    DashboardMetricKey.RESISTANCE,
    DashboardMetricKey.ELAPSED,
    DashboardMetricKey.MACHINE,
)

private val INDOOR_BIKE_GRAPHS = listOf(
    DashboardMetricKey.POWER,
    DashboardMetricKey.CADENCE_STEP,
    DashboardMetricKey.SPEED,
    DashboardMetricKey.HEART_RATE,
    DashboardMetricKey.RESISTANCE,
)

/** Whether equipment of [type] has this value at all. */
internal fun DashboardMetricKey.appliesTo(type: EquipmentType): Boolean =
    when (this) {
        DashboardMetricKey.PACE,
        DashboardMetricKey.AVG_PACE,
        -> type.showsPace
        DashboardMetricKey.INCLINE,
        DashboardMetricKey.AVG_INCLINE,
        DashboardMetricKey.ASCENT,
        DashboardMetricKey.ASCENT_RATE,
        -> type.showsClimb
        DashboardMetricKey.RESISTANCE -> type.showsResistance
        else -> true
    }

/**
 * Reads the equipment type from the FTMS service data of an advertisement (availability flags,
 * then a 16-bit machine type bit field). Returns null unless exactly one supported type is named.
 */
internal fun equipmentTypeFromFtmsServiceData(serviceData: ByteArray?): EquipmentType? {
    if (serviceData == null || serviceData.size < 3) {
        return null
    }

    val machineTypes = (serviceData[1].toInt() and 0xFF) or ((serviceData[2].toInt() and 0xFF) shl 8)
    val named = buildList {
        if (machineTypes and FTMS_ADVERTISED_TREADMILL != 0) add(EquipmentType.TREADMILL)
        if (machineTypes and FTMS_ADVERTISED_CROSS_TRAINER != 0) add(EquipmentType.CROSS_TRAINER)
        if (machineTypes and FTMS_ADVERTISED_INDOOR_BIKE != 0) add(EquipmentType.INDOOR_BIKE)
    }
    return named.singleOrNull()
}

private const val FTMS_ADVERTISED_TREADMILL = 0x01
private const val FTMS_ADVERTISED_CROSS_TRAINER = 0x02
private const val FTMS_ADVERTISED_INDOOR_BIKE = 0x20

/** True when the equipment reports itself as a different supported type than the selected one. */
internal fun isEquipmentTypeMismatch(selected: EquipmentType, receivedKind: Int): Boolean =
    EquipmentType.entries.any { it.machineKind == receivedKind } && receivedKind != selected.machineKind
