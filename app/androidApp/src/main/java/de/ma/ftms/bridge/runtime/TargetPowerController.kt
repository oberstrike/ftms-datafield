package de.ma.ftms.bridge.runtime

import de.ma.ftms.core.FtmsControlPoint
import de.ma.ftms.core.FtmsControlResponse
import de.ma.ftms.core.FtmsPowerRange

/** What the dashboard shows about the target power of an indoor bike. */
data class PowerControlState(
    val status: PowerControlStatus = PowerControlStatus.DISCONNECTED,
    /** The target the user asked for; kept across a reconnect within a session. */
    val targetWatts: Int? = null,
    /** What the equipment says it accepts, if it says so. */
    val range: FtmsPowerRange? = null,
    /**
     * The target was set by something else, such as a watch or the bike's own controls, and the
     * equipment reported it. The app shows such a target but never sends it: whoever set it is in
     * charge, and sending it would work against them.
     */
    val targetIsReported: Boolean = false,
    /**
     * The smallest step in which the target can be set. 1 W, whatever step the equipment reports:
     * a bike that reports steps of 5 W may still hold 168 W (a Wattbike Proton does). The reported
     * step applies only once the equipment refused a target between its steps.
     */
    val stepWatts: Int = 1,
) {
    val canSetTarget: Boolean
        get() = status != PowerControlStatus.DISCONNECTED && status != PowerControlStatus.UNSUPPORTED

    /** The target closest to [watts] that can be sent: within the range, and on a step if those apply. */
    fun acceptedTarget(watts: Int): Int {
        val accepted = range ?: FALLBACK_POWER_RANGE
        return if (stepWatts > 1) accepted.nearest(watts) else watts.coerceIn(accepted.minWatts, accepted.maxWatts)
    }
}

/**
 * One change of the target power during a session.
 *
 * @param timestampMillis on the session's own clock, which stands still during a pause, like the
 * timestamps of the session's samples.
 * @param watts the target from then on, or null if it was unset.
 */
data class TargetPowerPoint(val timestampMillis: Long, val watts: Int?)

/** The last target power set in the app and the equipment it was set on, kept beyond the session. */
data class RememberedTargetPower(val equipmentAddress: String, val watts: Int)

/**
 * What to remember once the controller is in [control]: a target the user set in the app on
 * [equipmentAddress]. A target that something else set, and no target, leave [remembered] as it is.
 */
internal fun targetPowerToRemember(
    remembered: RememberedTargetPower?,
    equipmentAddress: String?,
    control: PowerControlState,
): RememberedTargetPower? {
    val watts = control.targetWatts
    return when {
        watts == null || equipmentAddress == null || control.targetIsReported -> remembered
        else -> RememberedTargetPower(equipmentAddress, watts)
    }
}

/** The target to offer when one is typed on [equipmentAddress]: the last one set in the app on that bike. */
internal fun suggestedTargetPower(remembered: RememberedTargetPower?, equipmentAddress: String?): Int? =
    remembered?.takeIf { it.equipmentAddress == equipmentAddress }?.watts

/** [trace] with [point] added if it changes the target, limited to the newest [maxPoints]. */
internal fun appendTargetPowerPoint(
    trace: List<TargetPowerPoint>,
    point: TargetPowerPoint,
    maxPoints: Int = MAX_TARGET_POWER_TRACE_POINTS,
): List<TargetPowerPoint> =
    if (trace.lastOrNull()?.watts == point.watts) trace else (trace + point).takeLast(maxPoints)

/** Far more changes than a session has; keeps the state small if something sets targets in a loop. */
internal const val MAX_TARGET_POWER_TRACE_POINTS = 1_000

enum class PowerControlStatus {
    /** No connection to the equipment, or it is still being set up. */
    DISCONNECTED,

    /** The equipment is connected but cannot be given a target power. */
    UNSUPPORTED,

    /** A target can be set; none is set. */
    READY,

    /** The target is on its way to the equipment. */
    SENDING,

    /** The equipment confirmed the target. */
    ACTIVE,

    /** The equipment refused the target. */
    REJECTED,

    /** The equipment did not answer. */
    FAILED,
}

/**
 * Sets a target power through the FTMS control point: asks for control once per connection, then
 * sends the target, one command at a time. Only the newest target is sent when the user changes it
 * faster than the equipment answers.
 *
 * Not thread-safe; the owner calls it from a single thread.
 *
 * @param sendCommand writes one command and returns false if that could not be started. The owner
 * reports a missing answer with [onCommandLost], passing the same sequence number.
 */
internal class TargetPowerController(
    private val sendCommand: (bytes: ByteArray, sequence: Int) -> Boolean,
    private val onStateChanged: (PowerControlState) -> Unit,
) {
    private sealed interface Command {
        data object RequestControl : Command
        data class SetTarget(val watts: Int) : Command
    }

    var state = PowerControlState()
        private set

    private var controlGranted = false
    private var controlRetried = false
    private var inFlight: Command? = null
    private var inFlightSequence = 0
    private var nextSequence = 0

    /** The equipment connection is set up; [controlPointAvailable] says whether it takes commands. */
    fun onEquipmentReady(controlPointAvailable: Boolean) {
        controlGranted = false
        inFlight = null
        if (!controlPointAvailable) {
            update(PowerControlState(status = PowerControlStatus.UNSUPPORTED))
            return
        }

        // A reported target is shown as it was until the equipment says otherwise.
        val status = if (state.targetIsReported) PowerControlStatus.ACTIVE else PowerControlStatus.READY
        update(state.copy(status = status))
        controlRetried = false
        sendNext()
    }

    fun onSupportedRange(range: FtmsPowerRange?) {
        update(state.copy(range = range))
    }

    /** The connection is gone. The target survives if [keepTarget], to be set again on reconnect. */
    fun onEquipmentDisconnected(keepTarget: Boolean) {
        controlGranted = false
        inFlight = null
        update(
            PowerControlState(
                status = PowerControlStatus.DISCONNECTED,
                targetWatts = state.targetWatts.takeIf { keepTarget },
                targetIsReported = state.targetIsReported && keepTarget,
            ),
        )
    }

    /** Forgets the target. Nothing is sent to the equipment, which keeps what it was last given. */
    fun clearTarget() {
        if (state.targetWatts == null) {
            return
        }

        val idleStatus = if (state.canSetTarget) PowerControlStatus.READY else state.status
        update(state.copy(status = idleStatus, targetWatts = null, targetIsReported = false))
    }

    /** The user asks for [watts]; it is snapped to what the equipment accepts. */
    fun requestTarget(watts: Int) {
        if (!state.canSetTarget) {
            return
        }

        val target = state.acceptedTarget(watts)
        controlRetried = false
        update(state.copy(targetWatts = target, targetIsReported = false))
        sendNext()
    }

    /**
     * Sends the current target once more. A bike may let go of its target on its own, for example
     * when the rider stops for a break, without saying so; after the break the app's target is to
     * hold again. Returns whether there was a target of the app's own to send.
     */
    fun resendTarget(): Boolean {
        if (state.targetWatts == null || state.targetIsReported || !state.canSetTarget) {
            return false
        }

        controlRetried = false
        sendNext()
        return true
    }

    /**
     * The equipment reports that its target is now [watts], for example after a change on its own
     * controls or from a watch that controls it. It is taken over as the target without sending
     * anything, now or later, until the user sets a target in the app again.
     *
     * Ignored while a command is on its way: equipment also reports the targets it was just sent,
     * and such an echo of an older command must not undo a newer request.
     */
    fun onTargetReportedByEquipment(watts: Int) {
        if (!state.canSetTarget || inFlight != null || watts <= 0 || watts == state.targetWatts) {
            return
        }

        update(state.copy(status = PowerControlStatus.ACTIVE, targetWatts = watts, targetIsReported = true))
    }

    fun onResponse(response: FtmsControlResponse) {
        val command = inFlight ?: return
        if (response.requestOpCode != command.opCode) {
            return
        }

        inFlight = null
        when (command) {
            Command.RequestControl ->
                if (response.succeeded) {
                    controlGranted = true
                    sendNext()
                } else {
                    update(state.copy(status = PowerControlStatus.REJECTED))
                }

            is Command.SetTarget -> onTargetAnswered(command.watts, response)
        }
    }

    /** The command with [sequence] was not delivered or not answered in time. */
    fun onCommandLost(sequence: Int) {
        if (inFlight == null || sequence != inFlightSequence) {
            return
        }

        inFlight = null
        update(state.copy(status = PowerControlStatus.FAILED))
    }

    /** The command that is on its way could not be written. */
    fun onWriteFailed() {
        onCommandLost(inFlightSequence)
    }

    private fun onTargetAnswered(sentWatts: Int, response: FtmsControlResponse) {
        when {
            // The user moved on while this one was on its way.
            state.targetWatts != sentWatts -> sendNext()
            response.succeeded -> update(state.copy(status = PowerControlStatus.ACTIVE))
            response.resultCode == FtmsControlPoint.RESULT_CONTROL_NOT_PERMITTED && !controlRetried -> {
                // Equipment may drop control on its own, e.g. after a reset; ask once more.
                controlRetried = true
                controlGranted = false
                sendNext()
            }
            refusedBetweenSteps(sentWatts, response) -> {
                // This equipment means the steps it reports: from now on only those are sent.
                val range = state.range ?: FALLBACK_POWER_RANGE
                update(state.copy(targetWatts = range.nearest(sentWatts), stepWatts = range.stepWatts))
                sendNext()
            }
            response.resultCode == FtmsControlPoint.RESULT_NOT_SUPPORTED ->
                update(state.copy(status = PowerControlStatus.UNSUPPORTED, targetWatts = null, targetIsReported = false))
            else -> update(state.copy(status = PowerControlStatus.REJECTED))
        }
    }

    /** Whether [response] refuses [sentWatts] and the target lies between the steps the equipment reports. */
    private fun refusedBetweenSteps(sentWatts: Int, response: FtmsControlResponse): Boolean {
        val refused = response.resultCode == FtmsControlPoint.RESULT_INVALID_PARAMETER ||
            response.resultCode == FtmsControlPoint.RESULT_FAILED
        val range = state.range ?: return false
        return refused && state.stepWatts <= 1 && range.nearest(sentWatts) != sentWatts
    }

    private fun sendNext() {
        val target = state.targetWatts
        if (target == null || state.targetIsReported || inFlight != null || !state.canSetTarget) {
            return
        }

        val command = if (controlGranted) Command.SetTarget(target) else Command.RequestControl
        val bytes = when (command) {
            Command.RequestControl -> FtmsControlPoint.requestControl()
            is Command.SetTarget -> FtmsControlPoint.setTargetPower(command.watts)
        }
        nextSequence += 1
        val sequence = nextSequence
        inFlight = command
        inFlightSequence = sequence
        if (!sendCommand(bytes, sequence)) {
            inFlight = null
            update(state.copy(status = PowerControlStatus.FAILED))
        } else if (inFlight != null && inFlightSequence == sequence) {
            // Unless the write already failed while it was being started.
            update(state.copy(status = PowerControlStatus.SENDING))
        }
    }

    private fun update(newState: PowerControlState) {
        if (newState == state) {
            return
        }

        state = newState
        onStateChanged(newState)
    }

    private val Command.opCode: Int
        get() = when (this) {
            Command.RequestControl -> FtmsControlPoint.OP_REQUEST_CONTROL
            is Command.SetTarget -> FtmsControlPoint.OP_SET_TARGET_POWER
        }
}

/** Used while the equipment has not said what it accepts. */
internal val FALLBACK_POWER_RANGE = FtmsPowerRange(minWatts = 0, maxWatts = 2000, stepWatts = 1)

val DEFAULT_TARGET_POWER_PRESETS: List<Int> = listOf(100, 150, 200, 250)
val TARGET_POWER_STEP_OPTIONS: List<Int> = listOf(5, 10, 25)
const val DEFAULT_TARGET_POWER_STEP_WATTS = 10
const val MAX_TARGET_POWER_PRESETS = 8
val TARGET_POWER_PRESET_RANGE = 10..2000

/** The target used for the first step up or down when none is set and no power is measured. */
internal const val INITIAL_TARGET_POWER_WATTS = 100

/** Valid, distinct presets in rising order, limited to [MAX_TARGET_POWER_PRESETS]. */
fun sanitizeTargetPowerPresets(presets: List<Int>): List<Int> =
    presets.filter { it in TARGET_POWER_PRESET_RANGE }.distinct().sorted().take(MAX_TARGET_POWER_PRESETS)

/**
 * The target after one step of [stepWatts] up or down (by [direction]) from [currentTarget], or,
 * without a target, from the measured power rounded to the step.
 */
internal fun steppedTargetPower(currentTarget: Int?, measuredWatts: Int?, stepWatts: Int, direction: Int): Int {
    val step = stepWatts.coerceAtLeast(1)
    val base = currentTarget
        ?: measuredWatts?.takeIf { it > 0 }?.let { (it + step / 2) / step * step }
        ?: INITIAL_TARGET_POWER_WATTS
    return (base + direction * step).coerceAtLeast(0)
}

/** The target power typed as [text], or null unless it is a number within [range]. */
internal fun parseTargetPowerInput(text: String, range: FtmsPowerRange): Int? =
    text.toIntOrNull()?.takeIf { it in range.minWatts..range.maxWatts }

/** Whether [watts] can be added to [presets]: valid, not yet there, and room left. */
fun isNewTargetPowerPresetAllowed(watts: Int?, presets: List<Int>): Boolean =
    watts != null && watts in TARGET_POWER_PRESET_RANGE && watts !in presets && presets.size < MAX_TARGET_POWER_PRESETS
