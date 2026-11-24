package de.ma.ftms.bridge.runtime

import de.ma.ftms.core.RscMeasurement
import de.ma.ftms.core.SmoothedTreadmillSample

/** What the running sensor the app provides for the watch is doing. */
enum class FootPodStatus {
    /** Not wanted, or the bridge is not running. */
    OFF,

    /** This phone cannot act as a Bluetooth sensor. */
    UNSUPPORTED,

    /** Android has not been allowed to make the phone visible as a sensor. */
    PERMISSION_MISSING,

    /** Visible, but no watch has subscribed to the measurements yet. */
    ADVERTISING,

    /** A watch receives the measurements. */
    CONNECTED,

    /** The sensor could not be set up. */
    FAILED,
}

/**
 * Turns the treadmill's samples into the measurements the app sends to the watch as a running
 * sensor: at most one per [minIntervalMillis], with a distance that never runs backwards.
 */
internal class FootPodFeed(private val minIntervalMillis: Long = FOOT_POD_MIN_INTERVAL_MS) {
    private var lastSentAtMillis: Long? = null
    private var lastDistanceM = 0.0

    /** Forgets what was sent, for a new session. */
    fun reset() {
        lastSentAtMillis = null
        lastDistanceM = 0.0
    }

    /**
     * The measurement to send for [sample] at [nowMillis], or null if nothing is to be sent: the
     * last one is too recent, or the sample carries no speed.
     *
     * While [paused], the speed is zero and the distance stays where it was. The step rate is the
     * equipment's own; a value the watch measured is not sent back to it. A running sensor counts
     * the falls of the one foot it sits on, so half of the steps of both feet is sent.
     */
    fun next(sample: SmoothedTreadmillSample, paused: Boolean, nowMillis: Long): ByteArray? {
        val last = lastSentAtMillis
        if (last != null && nowMillis - last < minIntervalMillis) {
            return null
        }

        val speedKmh = if (paused) 0.0 else sample.raw.speedKmh ?: return null
        if (!paused) {
            lastDistanceM = maxOf(lastDistanceM, sample.distanceM)
        }
        lastSentAtMillis = nowMillis
        return RscMeasurement.encode(
            speedKmh = speedKmh,
            stepRateSpm = if (paused) null else sample.raw.equipment?.cadenceRpm?.let { it / STEPS_PER_FOOT_FALL },
            totalDistanceM = lastDistanceM,
        )
    }
}

/** True when the running sensor is switched on and applies to [equipmentType]. */
internal fun isFootPodMode(settings: BridgeSettings, equipmentType: EquipmentType): Boolean =
    settings.virtualFootPod && equipmentType == EquipmentType.TREADMILL

/** True when the app should be visible to the watch as a running sensor. */
internal fun isFootPodWanted(running: Boolean, settings: BridgeSettings, equipmentType: EquipmentType): Boolean =
    running && isFootPodMode(settings, equipmentType)

/**
 * True when the bridge may start as far as the watch is concerned: the data field is reachable, or
 * the running sensor is used. A watch does not hold its phone connection and a sensor connection
 * to the same phone at once, so with the running sensor the data field is usually out of reach,
 * and the bridge still has to start.
 */
internal fun isWatchReadyForStart(state: BridgeUiState): Boolean =
    state.garminReady || isFootPodMode(state.settings, state.selectedEquipmentType)

/** Steps of both feet per fall of one foot. */
internal const val STEPS_PER_FOOT_FALL = 2.0

/** A running sensor reports about once a second; treadmills that send faster are thinned out. */
internal const val FOOT_POD_MIN_INTERVAL_MS = 900L
