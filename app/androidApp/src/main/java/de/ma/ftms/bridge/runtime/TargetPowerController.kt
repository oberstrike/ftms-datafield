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
) {
    val canSetTarget: Boolean
        get() = status != PowerControlStatus.DISCONNECTED && status != PowerControlStatus.UNSUPPORTED
}

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
            update(state.copy(status = PowerControlStatus.UNSUPPORTED, targetWatts = null, range = null))
            return
        }

        update(state.copy(status = PowerControlStatus.READY))
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
            ),
        )
    }

    /** Forgets the target, e.g. when the session ends. Nothing is sent to the equipment. */
    fun clearTarget() {
        if (state.targetWatts == null) {
            return
        }

        val idleStatus = if (state.canSetTarget) PowerControlStatus.READY else state.status
        update(state.copy(status = idleStatus, targetWatts = null))
    }

    /** The user asks for [watts]; it is snapped to what the equipment accepts. */
    fun requestTarget(watts: Int) {
        if (!state.canSetTarget) {
            return
        }

        val target = (state.range ?: FALLBACK_POWER_RANGE).nearest(watts)
        controlRetried = false
        update(state.copy(targetWatts = target))
        sendNext()
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
            response.resultCode == FtmsControlPoint.RESULT_NOT_SUPPORTED ->
                update(state.copy(status = PowerControlStatus.UNSUPPORTED, targetWatts = null))
            else -> update(state.copy(status = PowerControlStatus.REJECTED))
        }
    }

    private fun sendNext() {
        val target = state.targetWatts
        if (target == null || inFlight != null || !state.canSetTarget) {
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

/** Whether [watts] can be added to [presets]: valid, not yet there, and room left. */
fun isNewTargetPowerPresetAllowed(watts: Int?, presets: List<Int>): Boolean =
    watts != null && watts in TARGET_POWER_PRESET_RANGE && watts !in presets && presets.size < MAX_TARGET_POWER_PRESETS
