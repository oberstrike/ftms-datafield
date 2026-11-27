package de.ma.ftms.relay

import de.ma.ftms.core.FootPodStatus
import de.ma.ftms.core.RscMeasurementParser
import de.ma.ftms.footpod.FootPodUuids
import java.util.UUID
import kotlin.math.roundToInt

/** What the connection to the running sensor of the bridge app is doing. */
enum class SourceStatus {
    OFF,

    /** Android has not been allowed to search for and connect to Bluetooth devices. */
    PERMISSION_MISSING,
    BLUETOOTH_OFF,

    /** Looking for the bridge's sensor; it is visible only while a treadmill session runs. */
    SEARCHING,
    CONNECTING,

    /** The measurements of the bridge arrive. */
    CONNECTED,
}

/** One measurement in readable form. */
data class MeasurementSummary(
    val speedKmh: Double,
    /** Steps of both feet per minute. */
    val stepsPerMinute: Int,
    val distanceM: Int?,
)

data class RelayEvent(val atMillis: Long, val message: String)

data class RelayState(
    val running: Boolean = false,
    val source: SourceStatus = SourceStatus.OFF,
    val sensor: FootPodStatus = FootPodStatus.OFF,
    /** Measurements that arrived from the bridge since the relay was started. */
    val received: Long = 0,
    /** Those of them a watch was subscribed for. */
    val delivered: Long = 0,
    val last: MeasurementSummary? = null,
    /** Wall clock time of [last]. */
    val lastAtMillis: Long? = null,
    val events: List<RelayEvent> = emptyList(),
)

/**
 * True if an advertisement that lists [serviceUuids] may come from the bridge's sensor. A chest
 * strap offers a running sensor as well; the bridge offers nothing but the running sensor.
 */
internal fun mayBeBridgeSensor(serviceUuids: Collection<UUID>): Boolean =
    FootPodUuids.RSC_SERVICE in serviceUuids && FootPodUuids.HEART_RATE_SERVICE !in serviceUuids

/** The content of an RSC Measurement, or null if [bytes] is not one. */
internal fun summarize(bytes: ByteArray): MeasurementSummary? {
    val sample = RscMeasurementParser.parse(bytes)
    val speedKmh = sample.speedKmh ?: return null
    return MeasurementSummary(
        speedKmh = speedKmh,
        // A running sensor reports the falls of one foot.
        stepsPerMinute = ((sample.stepRateSpm ?: 0.0) * STEPS_PER_FOOT_FALL).roundToInt(),
        distanceM = sample.distanceM,
    )
}

/**
 * Remembers running sensors that turned out not to be the bridge, so the relay does not connect to
 * them over and over. An entry expires, because a sensor may change what it is, and its address
 * may pass to another device.
 */
internal class RejectedSources(private val forMillis: Long = REJECTED_FOR_MS) {
    private val rejectedUntil = HashMap<String, Long>()

    fun reject(address: String, nowMillis: Long) {
        rejectedUntil.values.removeAll { it <= nowMillis }
        rejectedUntil[address] = nowMillis + forMillis
    }

    fun isRejected(address: String, nowMillis: Long): Boolean =
        rejectedUntil[address]?.let { nowMillis < it } ?: false
}

internal const val STEPS_PER_FOOT_FALL = 2
internal const val REJECTED_FOR_MS = 30 * 60_000L

/** How many events the screen keeps; the log file keeps more. */
internal const val MAX_EVENTS = 80
