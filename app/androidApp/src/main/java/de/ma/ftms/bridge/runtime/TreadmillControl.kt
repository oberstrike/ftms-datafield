package de.ma.ftms.bridge.runtime

import de.ma.ftms.core.FtmsControlPoint
import de.ma.ftms.core.FtmsControlResponse
import de.ma.ftms.core.FtmsTargetSettings
import de.ma.ftms.core.FtmsValueRange
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What a treadmill can be told. Every value of a setting is a whole number in the unit of its
 * command: hundredths of a km/h for the speed, tenths of a percent for the incline.
 */
enum class TreadmillSetting {
    SPEED,
    INCLINE,
}

enum class TreadmillTargetStatus {
    /** Nothing was asked for, or what was asked for is done with. */
    IDLE,

    /** The value is on its way to the treadmill. */
    SENDING,

    /** The treadmill confirmed the value and moves towards it or holds it. */
    ACCEPTED,

    /** The treadmill refused the value. */
    REJECTED,

    /** The treadmill did not answer. */
    FAILED,
}

/** What the dashboard shows about one setting of the treadmill. */
data class TreadmillTargetState(
    /** False once the treadmill said it does not take this setting. */
    val supported: Boolean = true,
    /** What the treadmill says it accepts, if it says so. */
    val range: FtmsValueRange? = null,
    /** What the treadmill reports right now. */
    val measured: Int? = null,
    /**
     * What was last asked for in the app, for as long as it is of use: until the treadmill was
     * seen to leave it again (someone used the console), the belt stopped or the connection ended.
     */
    val target: Int? = null,
    val status: TreadmillTargetStatus = TreadmillTargetStatus.IDLE,
    /** The app is leading the treadmill to [target] in small values and still has some to send. */
    val ramping: Boolean = false,
)

enum class TreadmillControlAvailability {
    /** No connection to the treadmill, or it is still being set up. */
    DISCONNECTED,

    /** The treadmill is connected but takes no commands. */
    UNSUPPORTED,

    /** The treadmill is connected and takes commands. */
    READY,
}

/** What the dashboard shows about steering the treadmill. */
data class TreadmillControlState(
    val availability: TreadmillControlAvailability = TreadmillControlAvailability.DISCONNECTED,
    /** The treadmill reports a speed above zero. Only a moving belt is given a value. */
    val beltMoving: Boolean = false,
    val speed: TreadmillTargetState = TreadmillTargetState(),
    val incline: TreadmillTargetState = TreadmillTargetState(),
) {
    /** The treadmill is connected and takes at least one of the two settings. */
    val available: Boolean
        get() = availability == TreadmillControlAvailability.READY && (speed.supported || incline.supported)

    val canSet: Boolean
        get() = available && beltMoving

    fun of(setting: TreadmillSetting): TreadmillTargetState =
        when (setting) {
            TreadmillSetting.SPEED -> speed
            TreadmillSetting.INCLINE -> incline
        }

    internal fun with(setting: TreadmillSetting, change: (TreadmillTargetState) -> TreadmillTargetState): TreadmillControlState =
        when (setting) {
            TreadmillSetting.SPEED -> copy(speed = change(speed))
            TreadmillSetting.INCLINE -> copy(incline = change(incline))
        }

    /** The value closest to [value] that the treadmill takes for [setting]. */
    fun acceptedValue(setting: TreadmillSetting, value: Int): Int {
        val nearest = (of(setting).range ?: setting.fallbackRange).nearest(value)
        // A speed of zero would stop the belt, which is the console's business.
        return if (setting == TreadmillSetting.SPEED) nearest.coerceAtLeast(MIN_TREADMILL_SPEED_TO_SEND) else nearest
    }

    /** The value a step of [setting] starts from: what was asked for, else what the treadmill reports. */
    fun steppingBase(setting: TreadmillSetting): Int? =
        of(setting).let { it.target ?: it.measured }

    /** The smallest step of [setting] the treadmill takes. */
    fun fineStep(setting: TreadmillSetting): Int =
        (of(setting).range ?: setting.fallbackRange).step.coerceAtLeast(1)
}

/**
 * Sets the speed and the incline of a treadmill through the FTMS control point: asks for control
 * once per connection, then sends what was asked for, one command at a time. Only the newest
 * value of a setting is sent when the user changes it faster than the treadmill answers.
 *
 * A speed that is some way off is not sent in one piece if the request or [rampProfile] says so:
 * the app then leads the treadmill there in small values, one per packet of the treadmill at most, with a pace
 * that builds up and fades out (see [advanceTreadmillRamp]). Such a transition waits while the
 * belt lags behind, and it is given up, not picked up again, when the belt does not follow, when
 * it is moved on the console, or when the treadmill falls silent for a while.
 *
 * A belt carries a person, so this is stricter than a target power:
 *
 * - A value is sent only on a request, never by itself: not after a reconnect, not again later.
 *   The values of a transition all belong to the one request that started it.
 * - A value is sent only while the treadmill reports a moving belt, and a speed is never zero.
 *   The belt is started and stopped on the console; there are no commands for that here.
 * - What was asked for is forgotten when the connection ends, when the belt stops, when the
 *   treadmill refuses it, and once the treadmill was seen to reach it and then to leave it again,
 *   which means someone used the console. Steps up and down then start from what the treadmill
 *   reports.
 *
 * Not thread-safe; the owner calls it from a single thread. All times are milliseconds of one
 * monotonic clock.
 *
 * @param sendCommand writes one command and returns false if that could not be started. The owner
 * reports a missing answer with [onCommandLost], passing the same sequence number.
 */
internal class TreadmillController(
    private val sendCommand: (bytes: ByteArray, sequence: Int) -> Boolean,
    private val onStateChanged: (TreadmillControlState) -> Unit,
    private val rampProfile: () -> TreadmillRampProfile = { TreadmillRampProfile.DIRECT },
) {
    private sealed interface Command {
        data object RequestControl : Command
        data class Set(val setting: TreadmillSetting, val value: Int) : Command
    }

    /**
     * A transition of the speed that is under way, in hundredths of a km/h.
     *
     * @property limits how fast it may move and how fast that pace may change.
     * @property position where the transition is, between the values the treadmill takes.
     * @property rate how fast it moves, per second.
     * @property commanded the value last sent, or about to be.
     * @property low the lowest and
     * @property high the highest speed the belt has a reason to run at during this transition.
     * @property followedAtMillis when the belt was last seen close to [commanded].
     */
    private class Ramp(
        val faster: Boolean,
        val limits: TreadmillRampLimits,
        var position: Double,
        var rate: Double,
        var commanded: Int,
        var low: Int,
        var high: Int,
        var tickedAtMillis: Long,
        var followedAtMillis: Long,
    )

    var state = TreadmillControlState()
        private set

    private var ramp: Ramp? = null

    private var controlGranted = false
    private var controlRetried = false
    private var inFlight: Command? = null
    private var inFlightSequence = 0
    private var nextSequence = 0

    /** The values that still have to be written, at most one per setting. */
    private val pending = mutableMapOf<TreadmillSetting, Int>()

    /** The settings whose target the treadmill was seen to reach. */
    private val reached = mutableSetOf<TreadmillSetting>()
    private val targetSinceMillis = mutableMapOf<TreadmillSetting, Long>()
    private var speedMeasuredAtMillis: Long? = null
    private var inclineSeenAtMillis: Long? = null

    /** The connection is set up; [controlPointAvailable] says whether the treadmill takes commands. */
    fun onEquipmentReady(controlPointAvailable: Boolean) {
        resetLink()
        update(
            TreadmillControlState(
                availability = if (controlPointAvailable) {
                    TreadmillControlAvailability.READY
                } else {
                    TreadmillControlAvailability.UNSUPPORTED
                },
            ),
        )
    }

    /** What the treadmill says it can be told; null if it did not say. */
    fun onTargetSettings(settings: FtmsTargetSettings?) {
        if (settings == null) {
            return
        }

        var next = state
        mapOf(TreadmillSetting.SPEED to settings.speed, TreadmillSetting.INCLINE to settings.inclination)
            .forEach { (setting, supported) ->
                if (!supported) {
                    pending.remove(setting)
                }
                next = next.with(setting) {
                    if (supported) it.copy(supported = true) else it.copy(supported = false, target = null, status = TreadmillTargetStatus.IDLE)
                }
            }
        update(next)
    }

    fun onSupportedRange(setting: TreadmillSetting, range: FtmsValueRange?) {
        update(state.with(setting) { it.copy(range = range) })
    }

    /** The connection is gone, and with it everything that was asked for. */
    fun onEquipmentDisconnected() {
        resetLink()
        update(TreadmillControlState())
    }

    /**
     * Takes in what a packet of the treadmill reports; null for a value the packet does not carry.
     * A treadmill may leave the incline out while it is level, so an incline that stays away
     * counts as zero.
     */
    fun onMeasured(speedHundredths: Int?, inclineTenths: Int?, nowMillis: Long) {
        if (state.availability != TreadmillControlAvailability.READY) {
            return
        }

        val speedSeenAt = speedMeasuredAtMillis
        if (ramp != null && speedHundredths != null && speedSeenAt != null &&
            nowMillis - speedSeenAt > TREADMILL_MEASUREMENT_MAX_AGE_MS
        ) {
            // The treadmill was silent for a while; a transition is not picked up where it stopped.
            forgetSpeedTarget()
        }

        var next = state
        if (speedHundredths != null) {
            speedMeasuredAtMillis = nowMillis
            next = next.copy(beltMoving = speedHundredths > 0, speed = next.speed.copy(measured = speedHundredths))
        }
        val seenAt = inclineSeenAtMillis
        val incline = when {
            inclineTenths != null -> {
                inclineSeenAtMillis = nowMillis
                inclineTenths
            }
            seenAt == null || nowMillis - seenAt > INCLINE_ABSENT_MEANS_LEVEL_MS -> 0
            else -> next.incline.measured
        }
        next = next.copy(incline = next.incline.copy(measured = incline))

        if (!next.beltMoving) {
            update(next.withoutTargets())
            pending.clear()
            reached.clear()
            return
        }

        TreadmillSetting.entries.forEach { setting -> next = settle(next, setting, nowMillis) }
        update(next)
        if (speedHundredths != null) {
            advanceRamp(speedHundredths, nowMillis)
        }
    }

    /**
     * The user asks for [value] of [setting]; it is snapped to what the treadmill accepts. Returns
     * false, and sends nothing, unless the treadmill is connected and reported a moving belt within
     * the last seconds.
     *
     * A speed further away than a small step is led to in a transition if [transition] asks for
     * one, or [rampProfile] where none is given; this then sends only its first value.
     */
    fun request(
        setting: TreadmillSetting,
        value: Int,
        nowMillis: Long,
        transition: TreadmillTransition? = null,
    ): Boolean {
        if (!state.canSet || !state.of(setting).supported) {
            return false
        }

        val measuredAt = speedMeasuredAtMillis
        if (measuredAt == null || nowMillis - measuredAt > TREADMILL_MEASUREMENT_MAX_AGE_MS) {
            // Without fresh data nobody knows whether the belt still moves.
            pending.clear()
            reached.clear()
            update(state.copy(beltMoving = false).withoutTargets())
            return false
        }

        val target = state.acceptedValue(setting, value)
        val firstOfRamp = if (setting == TreadmillSetting.SPEED) {
            startRamp(target, transition ?: TreadmillTransition.Profile(rampProfile()), nowMillis)
        } else {
            null
        }
        pending[setting] = firstOfRamp ?: target
        reached -= setting
        targetSinceMillis[setting] = nowMillis
        controlRetried = false
        update(state.with(setting) { it.copy(target = target, status = TreadmillTargetStatus.SENDING) })
        sendNext()
        return true
    }

    /**
     * Moves [setting] by [delta]: from what was asked for while the treadmill is still on its way
     * there, otherwise from what it reports.
     */
    fun step(setting: TreadmillSetting, delta: Int, nowMillis: Long): Boolean {
        val base = steppingBase(setting) ?: return false
        return request(setting, base + delta, nowMillis)
    }

    /** The value a step of [setting] starts from, null while the treadmill has reported none. */
    fun steppingBase(setting: TreadmillSetting): Int? =
        state.steppingBase(setting)

    fun onResponse(response: FtmsControlResponse, nowMillis: Long) {
        val command = inFlight ?: return
        if (response.requestOpCode != command.opCode) {
            return
        }

        inFlight = null
        when (command) {
            Command.RequestControl ->
                if (response.succeeded) {
                    controlGranted = true
                } else {
                    failPending(TreadmillTargetStatus.REJECTED)
                }

            is Command.Set -> onSetAnswered(command, response, nowMillis)
        }
        sendNext()
    }

    /** The command with [sequence] was not delivered or not answered in time. */
    fun onCommandLost(sequence: Int) {
        val command = inFlight
        if (command == null || sequence != inFlightSequence) {
            return
        }

        inFlight = null
        lose(command)
    }

    /** The command that is on its way could not be written. */
    fun onWriteFailed() {
        onCommandLost(inFlightSequence)
    }

    private fun onSetAnswered(command: Command.Set, response: FtmsControlResponse, nowMillis: Long) {
        val setting = command.setting
        when {
            // The user moved on while this one was on its way, or the value is no longer wanted.
            pending.containsKey(setting) || !isWanted(command) -> Unit
            response.succeeded -> {
                targetSinceMillis[setting] = nowMillis
                update(state.with(setting) { it.copy(status = TreadmillTargetStatus.ACCEPTED) })
            }
            response.resultCode == FtmsControlPoint.RESULT_CONTROL_NOT_PERMITTED && !controlRetried -> {
                // A treadmill may drop control on its own, e.g. after a reset; ask once more.
                controlRetried = true
                controlGranted = false
                pending[setting] = command.value
            }
            response.resultCode == FtmsControlPoint.RESULT_NOT_SUPPORTED ->
                update(
                    state.with(setting) {
                        it.copy(supported = false, target = null, status = TreadmillTargetStatus.IDLE)
                    },
                )
            else ->
                update(state.with(setting) { it.copy(target = null, status = TreadmillTargetStatus.REJECTED) })
        }
    }

    /** Whether the treadmill reached what was asked for, left it again, or never got there. */
    private fun settle(current: TreadmillControlState, setting: TreadmillSetting, nowMillis: Long): TreadmillControlState {
        val target = current.of(setting)
        val wanted = target.target
        val measured = target.measured
        if (wanted == null || measured == null || target.status != TreadmillTargetStatus.ACCEPTED) {
            return current
        }
        if (setting == TreadmillSetting.SPEED && ramp != null) {
            // A transition watches the belt itself, see advanceRamp.
            return current
        }

        val since = targetSinceMillis[setting] ?: nowMillis
        return when {
            abs(measured - wanted) <= current.fineStep(setting) / 2 -> {
                reached += setting
                current
            }
            // It was there and is not any more: the console was used, and what it says counts.
            setting in reached || nowMillis - since > setting.reachTimeoutMillis -> {
                reached -= setting
                current.with(setting) { it.copy(target = null, status = TreadmillTargetStatus.IDLE) }
            }
            else -> current
        }
    }

    /**
     * Begins a transition to [target], or carries on the one under way with a new target. Returns
     * the first value to send, or null if [target] is to be sent as it is: because no transition is
     * wanted or because it is only a small step away.
     */
    private fun startRamp(target: Int, transition: TreadmillTransition, nowMillis: Long): Int? {
        val active = ramp
        ramp = null
        val measured = state.speed.measured ?: return null
        // What the belt was last told counts as where it is, unless it is far from there.
        val base = (active?.commanded ?: state.speed.target)
            ?.takeIf { abs(it - measured) <= TREADMILL_RAMP_MAX_LEAD }
            ?: measured
        val faster = target > base
        val distance = abs(target - base)
        if (distance <= TREADMILL_RAMP_MIN_CHANGE) {
            return null
        }
        val limits = transition.limits(distance, faster) ?: return null

        // One small step at once, so that the belt answers the tap; the pace builds up from there.
        val step = state.fineStep(TreadmillSetting.SPEED)
        val first = state.acceptedValue(TreadmillSetting.SPEED, if (faster) base + step else base - step)
            .coerceIn(minOf(base, target), maxOf(base, target))
        ramp = Ramp(
            faster = faster,
            limits = limits,
            position = first.toDouble(),
            // A new target in the same direction keeps the pace.
            rate = active?.takeIf { it.faster == faster }?.rate ?: 0.0,
            commanded = first,
            low = minOf(measured, base, first),
            high = maxOf(measured, base, first),
            tickedAtMillis = nowMillis,
            followedAtMillis = nowMillis,
        )
        return first
    }

    /**
     * Moves the transition on by the time that has passed and sends its next value, if the belt
     * reports [measured] close to the last one. Called with every speed the treadmill reports, so
     * a treadmill that says nothing is told nothing.
     */
    private fun advanceRamp(measured: Int, nowMillis: Long) {
        val current = ramp ?: return
        val target = state.speed.target ?: return
        if (measured < current.low - TREADMILL_RAMP_CORRIDOR_TOLERANCE ||
            measured > current.high + TREADMILL_RAMP_CORRIDOR_TOLERANCE
        ) {
            // The transition never led the belt there: the console was used, and what it says counts.
            forgetSpeedTarget()
            return
        }
        if (inFlight != null || TreadmillSetting.SPEED in pending) {
            return
        }

        val elapsedMillis = nowMillis - current.tickedAtMillis
        if (elapsedMillis < TREADMILL_RAMP_MIN_TICK_MS) {
            return
        }
        current.tickedAtMillis = nowMillis
        if (abs(measured - current.commanded) > TREADMILL_RAMP_MAX_LEAD) {
            // The belt lags behind: wait for it, but not forever.
            if (nowMillis - current.followedAtMillis > TREADMILL_RAMP_STALL_TIMEOUT_MS) {
                forgetSpeedTarget()
            }
            return
        }
        current.followedAtMillis = nowMillis

        val motion = advanceTreadmillRamp(
            position = current.position,
            rate = current.rate,
            target = target,
            limits = current.limits,
            seconds = minOf(elapsedMillis, TREADMILL_RAMP_MAX_TICK_MS) / 1000.0,
        )
        current.position = motion.position
        current.rate = motion.rate
        val next = state.acceptedValue(TreadmillSetting.SPEED, motion.position.roundToInt())
            .coerceIn(minOf(current.commanded, target), maxOf(current.commanded, target))
        if (next == current.commanded) {
            return
        }

        current.commanded = next
        current.low = minOf(current.low, next)
        current.high = maxOf(current.high, next)
        pending[TreadmillSetting.SPEED] = next
        if (next == target) {
            // The last value: from here on the target is watched like one sent in one piece.
            ramp = null
            targetSinceMillis[TreadmillSetting.SPEED] = nowMillis
            update(state)
        }
        sendNext()
    }

    /** Whether the answer to [command] still matters: it carries the target or the newest value of a transition. */
    private fun isWanted(command: Command.Set): Boolean =
        state.of(command.setting).target == command.value ||
            (command.setting == TreadmillSetting.SPEED && ramp?.commanded == command.value)

    private fun forgetSpeedTarget() {
        pending.remove(TreadmillSetting.SPEED)
        reached -= TreadmillSetting.SPEED
        update(state.copy(speed = state.speed.copy(target = null, status = TreadmillTargetStatus.IDLE)))
    }

    private fun sendNext() {
        if (inFlight != null || pending.isEmpty() || state.availability != TreadmillControlAvailability.READY) {
            return
        }
        if (!state.beltMoving) {
            pending.clear()
            update(state.withoutTargets())
            return
        }

        val command = if (!controlGranted) {
            Command.RequestControl
        } else {
            val setting = TreadmillSetting.entries.first { it in pending }
            Command.Set(setting, pending.remove(setting) ?: return)
        }
        val bytes = when (command) {
            Command.RequestControl -> FtmsControlPoint.requestControl()
            is Command.Set -> when (command.setting) {
                TreadmillSetting.SPEED -> FtmsControlPoint.setTargetSpeed(command.value)
                TreadmillSetting.INCLINE -> FtmsControlPoint.setTargetInclination(command.value)
            }
        }
        nextSequence += 1
        val sequence = nextSequence
        inFlight = command
        inFlightSequence = sequence
        if (!sendCommand(bytes, sequence) && inFlight === command && inFlightSequence == sequence) {
            inFlight = null
            lose(command)
        }
    }

    /** [command] got no answer: whatever was to be sent is given up rather than sent late. */
    private fun lose(command: Command) {
        if (command is Command.Set && !pending.containsKey(command.setting) && isWanted(command)) {
            update(
                state.with(command.setting) { it.copy(target = null, status = TreadmillTargetStatus.FAILED) },
            )
        }
        failPending(TreadmillTargetStatus.FAILED)
    }

    private fun failPending(status: TreadmillTargetStatus) {
        var next = state
        pending.keys.forEach { setting -> next = next.with(setting) { it.copy(target = null, status = status) } }
        pending.clear()
        update(next)
    }

    private fun resetLink() {
        controlGranted = false
        controlRetried = false
        inFlight = null
        ramp = null
        pending.clear()
        reached.clear()
        targetSinceMillis.clear()
        speedMeasuredAtMillis = null
        inclineSeenAtMillis = null
    }

    private fun update(newState: TreadmillControlState) {
        // A transition does not outlive its target, and the state says whether one is under way.
        if (newState.speed.target == null) {
            ramp = null
        }
        val next = if (newState.speed.ramping == (ramp != null)) {
            newState
        } else {
            newState.copy(speed = newState.speed.copy(ramping = ramp != null))
        }
        if (next == state) {
            return
        }

        state = next
        onStateChanged(next)
    }

    private fun TreadmillControlState.withoutTargets(): TreadmillControlState =
        copy(
            speed = speed.copy(target = null, status = TreadmillTargetStatus.IDLE),
            incline = incline.copy(target = null, status = TreadmillTargetStatus.IDLE),
        )

    private val Command.opCode: Int
        get() = when (this) {
            Command.RequestControl -> FtmsControlPoint.OP_REQUEST_CONTROL
            is Command.Set -> when (setting) {
                TreadmillSetting.SPEED -> FtmsControlPoint.OP_SET_TARGET_SPEED
                TreadmillSetting.INCLINE -> FtmsControlPoint.OP_SET_TARGET_INCLINATION
            }
        }
}

/** Used while the treadmill has not said what it accepts. */
internal val TreadmillSetting.fallbackRange: FtmsValueRange
    get() = when (this) {
        TreadmillSetting.SPEED -> FtmsValueRange(min = MIN_TREADMILL_SPEED_TO_SEND, max = 2500, step = 10)
        TreadmillSetting.INCLINE -> FtmsValueRange(min = 0, max = 400, step = 5)
    }

/** How long a treadmill may take to get to a value before the app stops waiting for it. */
private val TreadmillSetting.reachTimeoutMillis: Long
    get() = when (this) {
        TreadmillSetting.SPEED -> 60_000L
        // A deck is lifted slowly.
        TreadmillSetting.INCLINE -> 180_000L
    }

/** The app never sends a speed below 0.5 km/h; a belt is stopped on the console. */
internal const val MIN_TREADMILL_SPEED_TO_SEND = 50

/** A speed older than this says nothing about whether the belt moves. */
internal const val TREADMILL_MEASUREMENT_MAX_AGE_MS = 5_000L

/** An incline that no packet named for this long is taken as level. */
internal const val INCLINE_ABSENT_MEANS_LEVEL_MS = 3_000L

/** Quick choices in the unit of their setting: 6, 8, 10 and 12 km/h; 0, 1, 2 and 5 %. */
val DEFAULT_TREADMILL_SPEED_PRESETS: List<Int> = listOf(600, 800, 1000, 1200)
val DEFAULT_TREADMILL_INCLINE_PRESETS: List<Int> = listOf(0, 10, 20, 50)

/** The larger of the two steps for the speed: 0.5 or 1 km/h. */
val TREADMILL_SPEED_STEP_OPTIONS: List<Int> = listOf(50, 100)
const val DEFAULT_TREADMILL_SPEED_STEP = 50
const val MAX_TREADMILL_PRESETS = 8

/** A quick choice asks before it makes the belt faster by more than this: 3 km/h. */
internal const val LARGE_SPEED_INCREASE = 300

val TreadmillSetting.presetRange: IntRange
    get() = when (this) {
        TreadmillSetting.SPEED -> 50..2500
        TreadmillSetting.INCLINE -> 0..400
    }

/** Quick choices lie on this grid: 0.1 km/h, 0.5 %. */
val TreadmillSetting.presetGrid: Int
    get() = when (this) {
        TreadmillSetting.SPEED -> 10
        TreadmillSetting.INCLINE -> 5
    }

/** How many units of [this] setting make one km/h or one percent. */
val TreadmillSetting.unitsPerOne: Int
    get() = when (this) {
        TreadmillSetting.SPEED -> 100
        TreadmillSetting.INCLINE -> 10
    }

val TreadmillSetting.defaultPresets: List<Int>
    get() = when (this) {
        TreadmillSetting.SPEED -> DEFAULT_TREADMILL_SPEED_PRESETS
        TreadmillSetting.INCLINE -> DEFAULT_TREADMILL_INCLINE_PRESETS
    }

/** Valid, distinct quick choices of [setting] in rising order, limited to [MAX_TREADMILL_PRESETS]. */
fun sanitizeTreadmillPresets(setting: TreadmillSetting, presets: List<Int>): List<Int> =
    presets.filter { it in setting.presetRange && it % setting.presetGrid == 0 }
        .distinct()
        .sorted()
        .take(MAX_TREADMILL_PRESETS)

/** Whether [value] can be added to [presets]: valid, not yet there, and room left. */
fun isNewTreadmillPresetAllowed(setting: TreadmillSetting, value: Int?, presets: List<Int>): Boolean =
    value != null &&
        value in setting.presetRange &&
        value % setting.presetGrid == 0 &&
        value !in presets &&
        presets.size < MAX_TREADMILL_PRESETS

/**
 * The value typed as [text], with a comma or a point, in the unit of [setting]; null unless it is a
 * number on the grid of the quick choices. "8,5" is 850 for the speed and 85 for the incline.
 */
fun parseTreadmillValue(setting: TreadmillSetting, text: String): Int? {
    val number = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
    val exact = number * setting.unitsPerOne
    val value = exact.roundToInt()
    return value.takeIf { abs(exact - value) < 1e-6 && value % setting.presetGrid == 0 }
}

/** Whether a quick choice of [target] makes a belt that runs at [measured] so much faster that it is asked about. */
internal fun isLargeSpeedIncrease(measured: Int?, target: Int): Boolean =
    target - (measured ?: 0) > LARGE_SPEED_INCREASE

/** Whether the app steers the treadmill: the setting is on and a treadmill is selected. */
internal fun isTreadmillControlWanted(settings: BridgeSettings, type: EquipmentType): Boolean =
    settings.treadmillControl && type == EquipmentType.TREADMILL
