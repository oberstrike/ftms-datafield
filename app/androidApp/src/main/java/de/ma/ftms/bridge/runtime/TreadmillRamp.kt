package de.ma.ftms.bridge.runtime

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * How the app takes a treadmill to a speed that is some way off.
 *
 * A treadmill told a new speed goes there at its own, constant pace: it pulls at once and stops
 * pulling at once. With a transition the app leads it there instead, in many small values: the
 * pace builds up, holds, and fades out before the speed is reached.
 */
enum class TreadmillRampProfile {
    /** One command with the speed asked for; the treadmill gets there the way it does by itself. */
    DIRECT,

    /** A soft start and a soft landing, at a pace a treadmill has no trouble following. */
    GENTLE,

    /** The same, slower. */
    VERY_GENTLE,
}

/**
 * The limits of a transition in one direction, in hundredths of a km/h.
 *
 * @param maxRate the fastest the speed changes, per second.
 * @param acceleration how fast that pace builds up and fades out, per second squared.
 */
internal data class TreadmillRampLimits(val maxRate: Double, val acceleration: Double)

/**
 * The limits of [this] profile for a belt that gets faster ([faster]) or slower; null for a
 * profile that leaves the way to the treadmill.
 *
 * Slowing down is quicker than speeding up: whoever asks for less wants it soon.
 */
internal fun TreadmillRampProfile.limits(faster: Boolean): TreadmillRampLimits? =
    when (this) {
        TreadmillRampProfile.DIRECT -> null
        TreadmillRampProfile.GENTLE ->
            if (faster) TreadmillRampLimits(maxRate = 40.0, acceleration = 20.0) else TreadmillRampLimits(maxRate = 60.0, acceleration = 40.0)
        TreadmillRampProfile.VERY_GENTLE ->
            if (faster) TreadmillRampLimits(maxRate = 25.0, acceleration = 10.0) else TreadmillRampLimits(maxRate = 40.0, acceleration = 25.0)
    }

/**
 * How one request is taken to its speed: in one piece, with the pace of a profile, or within a
 * given time.
 */
sealed interface TreadmillTransition {
    data object Direct : TreadmillTransition

    data class Profile(val profile: TreadmillRampProfile) : TreadmillTransition

    /** The whole change takes about [seconds], however large it is. */
    data class Seconds(val seconds: Int) : TreadmillTransition
}

/**
 * The limits for a change of [distance] hundredths of a km/h, to a higher speed ([faster]) or a
 * lower one; null if the speed is to be sent in one piece.
 *
 * A change within a given time spends a quarter of it building up its pace, half of it holding
 * the pace, and a quarter fading out.
 */
internal fun TreadmillTransition.limits(distance: Int, faster: Boolean): TreadmillRampLimits? =
    when (this) {
        TreadmillTransition.Direct -> null
        is TreadmillTransition.Profile -> profile.limits(faster)
        is TreadmillTransition.Seconds ->
            if (seconds <= 0 || distance <= 0) {
                null
            } else {
                val maxRate = distance / (0.75 * seconds)
                TreadmillRampLimits(maxRate = maxRate, acceleration = maxRate / (0.25 * seconds))
            }
    }

/**
 * About how many seconds a belt takes from [from] to [to] with [transition], both in hundredths
 * of a km/h. No transition is quicker than the belt itself, which is taken to manage
 * [ASSUMED_BELT_RATE].
 */
internal fun treadmillTransitionSeconds(from: Int, to: Int, transition: TreadmillTransition): Double {
    val distance = abs(to - from)
    val beltSeconds = distance / ASSUMED_BELT_RATE
    if (distance <= TREADMILL_RAMP_MIN_CHANGE) {
        return beltSeconds
    }

    val limits = transition.limits(distance, faster = to > from) ?: return beltSeconds
    var motion = TreadmillRampMotion(from.toDouble(), 0.0)
    var seconds = 0.0
    while (motion.position != to.toDouble() && seconds < MAX_ESTIMATED_SECONDS) {
        motion = advanceTreadmillRamp(motion.position, motion.rate, to, limits, ESTIMATE_TICK_SECONDS)
        seconds += ESTIMATE_TICK_SECONDS
    }
    return maxOf(seconds, beltSeconds)
}

/** What a treadmill does by itself, in hundredths of a km/h per second; a Darwin TM 70 was seen at 64. */
internal const val ASSUMED_BELT_RATE = 60.0
private const val ESTIMATE_TICK_SECONDS = 0.5
private const val MAX_ESTIMATED_SECONDS = 600.0

/** Where a transition is and how fast it moves, in hundredths of a km/h and those per second. */
internal data class TreadmillRampMotion(val position: Double, val rate: Double)

/**
 * A transition [seconds] later: from [position], moving at [rate], towards [target].
 *
 * The pace rises by no more than the acceleration allows, never exceeds the fastest pace, and is
 * held under what still lets it fade out by the time [target] is reached. The result never lies
 * beyond [target].
 */
internal fun advanceTreadmillRamp(
    position: Double,
    rate: Double,
    target: Int,
    limits: TreadmillRampLimits,
    seconds: Double,
): TreadmillRampMotion {
    val remaining = abs(target - position)
    if (remaining == 0.0 || seconds <= 0.0) {
        return TreadmillRampMotion(position, if (remaining == 0.0) 0.0 else rate)
    }

    // The pace that still fades out in time: after this tick, what is left to go is what a pace
    // falling at the allowed rate needs to come to rest.
    val fade = limits.acceleration * seconds
    val landingRate = sqrt(fade * fade + 2 * limits.acceleration * remaining) - fade
    val newRate = min(min(rate + fade, limits.maxRate), landingRate)
    val moved = newRate * seconds
    val direction = if (target > position) 1 else -1
    return if (remaining - moved < TREADMILL_RAMP_ARRIVED) {
        TreadmillRampMotion(target.toDouble(), 0.0)
    } else {
        TreadmillRampMotion(position + direction * moved, newRate)
    }
}

/** A transition this close to its target, in hundredths of a km/h, has arrived. */
private const val TREADMILL_RAMP_ARRIVED = 0.5

/** A change of the speed up to this is sent as it is, transition or not: 0.5 km/h. */
internal const val TREADMILL_RAMP_MIN_CHANGE = 50

/**
 * A transition waits while the treadmill reports a speed further than this from the value last
 * sent, so that it never runs ahead of the belt: 0.6 km/h.
 */
internal const val TREADMILL_RAMP_MAX_LEAD = 60

/** A belt this far outside of where a transition has led it was moved on the console: 0.5 km/h. */
internal const val TREADMILL_RAMP_CORRIDOR_TOLERANCE = 50

/** A transition the belt does not follow for this long is given up. */
internal const val TREADMILL_RAMP_STALL_TIMEOUT_MS = 10_000L

/** The shortest time between two values of a transition. */
internal const val TREADMILL_RAMP_MIN_TICK_MS = 400L

/** A longer wait between two values moves the transition no further than this. */
internal const val TREADMILL_RAMP_MAX_TICK_MS = 1_000L
