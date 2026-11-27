package de.ma.ftms.bridge.runtime

import de.ma.ftms.core.FtmsSample

/**
 * Decides when movement of the selected equipment should start the bridge on its own.
 *
 * The bridge starts once the equipment reports movement in [movingSamplesToStart] consecutive
 * samples. To start once per workout instead of in a loop, the policy holds after every start
 * attempt and every finished session until the equipment has stopped. The hold ends when
 *
 * - a sample reports standstill, or
 * - the equipment was watched for but not seen for [rearmAfterUnseenMillis] (it was switched off
 *   while still moving), or
 * - [maxHoldMillis] have passed, so a workout later the same day starts on its own even if nobody
 *   watched the equipment stop.
 *
 * All times are milliseconds of one monotonic clock. Not thread-safe.
 */
internal class AutoStartPolicy(
    private val rearmAfterUnseenMillis: Long = AUTO_START_REARM_AFTER_UNSEEN_MS,
    private val maxHoldMillis: Long = AUTO_START_MAX_HOLD_MS,
    private val movingSamplesToStart: Int = AUTO_START_MOVING_SAMPLES,
) {
    private var holdingSinceMillis: Long? = null
    private var movingSamples = 0

    /** Start of the stretch during which the equipment was watched for and not seen. */
    private var unseenSinceMillis = 0L

    /** Forgets everything, for example when other equipment is selected. */
    fun reset() {
        holdingSinceMillis = null
        movingSamples = 0
        unseenSinceMillis = 0L
    }

    /** Watching begins (again). Time before this does not count as "watched and not seen". */
    fun onWatchStarted(nowMillis: Long) {
        unseenSinceMillis = nowMillis
    }

    /** The equipment advertised or sent data, so it is switched on. */
    fun onEquipmentSeen(nowMillis: Long) {
        releaseHoldIfDue(nowMillis)
        unseenSinceMillis = nowMillis
    }

    /** The connection to the equipment ended; movement has to be confirmed anew afterwards. */
    fun onConnectionLost() {
        movingSamples = 0
    }

    /**
     * Takes in what a sample says about movement ([moving] is null if it says nothing) and returns
     * true when the bridge should start now. The caller answers a start attempt with
     * [holdUntilStandstill].
     */
    fun onMovementReported(moving: Boolean?, nowMillis: Long): Boolean {
        onEquipmentSeen(nowMillis)
        when (moving) {
            null -> return false
            false -> {
                movingSamples = 0
                holdingSinceMillis = null
                return false
            }
            true -> {
                if (holdingSinceMillis != null) {
                    movingSamples = 0
                    return false
                }
                movingSamples = (movingSamples + 1).coerceAtMost(movingSamplesToStart)
                return movingSamples >= movingSamplesToStart
            }
        }
    }

    /** A start was attempted or a session ended: the equipment has to stop before the next start. */
    fun holdUntilStandstill(nowMillis: Long) {
        holdingSinceMillis = nowMillis
        unseenSinceMillis = nowMillis
        movingSamples = 0
    }

    /** Only meaningful while watching, because unwatched time must not count as unseen time. */
    fun isHolding(nowMillis: Long): Boolean {
        releaseHoldIfDue(nowMillis)
        return holdingSinceMillis != null
    }

    private fun releaseHoldIfDue(nowMillis: Long) {
        val sinceMillis = holdingSinceMillis ?: return
        val unseenLongEnough = nowMillis - unseenSinceMillis >= rearmAfterUnseenMillis
        val heldLongEnough = nowMillis - sinceMillis >= maxHoldMillis
        if (unseenLongEnough || heldLongEnough) {
            holdingSinceMillis = null
        }
    }
}

/**
 * Whether [sample] shows the equipment in motion: true or false if it carries a speed, cadence or
 * step rate, null if it carries none of them or could not be read completely.
 */
internal fun isEquipmentMoving(sample: FtmsSample): Boolean? {
    if (sample.truncated || sample.parseError != null) {
        return null
    }

    val readings = listOfNotNull(
        sample.speedKmh?.let { it >= AUTO_START_MIN_SPEED_KMH },
        sample.cadenceRpm?.let { it >= AUTO_START_MIN_CADENCE },
        sample.stepRateSpm?.let { it >= AUTO_START_MIN_CADENCE },
    )
    return if (readings.isEmpty()) null else readings.any { it }
}

/** Address of the equipment auto-start should watch, or null while it has nothing to do. */
internal fun autoStartWatchAddress(state: BridgeUiState, appVisible: Boolean): String? =
    standbyWatchAddress(state, appVisible)?.takeIf { state.settings.autoStart }

/**
 * Address of the equipment to look for and hold a connection to while no session runs, or null
 * while there is no reason for one. The reasons are auto-start, which waits there for movement,
 * and a treadmill that is to be steered from the app, session or not.
 */
internal fun standbyWatchAddress(state: BridgeUiState, appVisible: Boolean): String? =
    state.selectedTreadmillAddress?.takeIf {
        appVisible &&
            (state.settings.autoStart || isTreadmillControlWanted(state.settings, state.selectedEquipmentType)) &&
            !state.running &&
            // Connecting ends a discovery scan, so the user's own scan goes first.
            !state.scanning &&
            state.permissionsGranted &&
            state.bleAvailable
    }

/** True when everything a bridge start needs is in place; mirrors the checks of a manual start. */
internal fun isBridgeStartPossible(state: BridgeUiState): Boolean =
    !state.running &&
        state.permissionsGranted &&
        state.bleAvailable &&
        state.selectedTreadmillAddress != null &&
        state.selectedGarminId != null &&
        isWatchReadyForStart(state) &&
        state.garminConnectProblem == null

internal const val AUTO_START_REARM_AFTER_UNSEEN_MS = 60_000L
internal const val AUTO_START_MAX_HOLD_MS = 30 * 60_000L
internal const val AUTO_START_MOVING_SAMPLES = 2
internal const val AUTO_START_MIN_SPEED_KMH = 0.5
internal const val AUTO_START_MIN_CADENCE = 1.0
