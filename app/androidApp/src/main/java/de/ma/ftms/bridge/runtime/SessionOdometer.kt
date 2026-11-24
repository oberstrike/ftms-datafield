package de.ma.ftms.bridge.runtime

import de.ma.ftms.core.SmoothedTreadmillSample

/**
 * Distance and ascent of the session itself, counted from its first sample.
 *
 * The equipment reports totals since it was switched on or last reset, which include whatever was
 * covered before the session began. This adds up only what the totals gain between samples of the
 * session, so a session that starts on equipment already in use begins at zero. A total that drops
 * (the equipment was reset) is taken as a new starting point and loses nothing counted so far.
 */
internal class SessionOdometer {
    private var lastDistanceM: Double? = null
    private var lastAscentM: Double? = null

    var distanceM = 0.0
        private set

    var ascentM = 0.0
        private set

    /** Forgets everything, for a new session. */
    fun reset() {
        lastDistanceM = null
        lastAscentM = null
        distanceM = 0.0
        ascentM = 0.0
    }

    /**
     * Takes in the equipment's totals of [sample]. What they gained since the previous sample is
     * added if [counted] is true and left out otherwise, as while the session is paused.
     */
    fun advance(sample: SmoothedTreadmillSample, counted: Boolean) {
        if (counted) {
            distanceM += gain(lastDistanceM, sample.distanceM)
            ascentM += gain(lastAscentM, sample.ascentM)
        }
        lastDistanceM = sample.distanceM
        lastAscentM = sample.ascentM
    }

    private fun gain(previous: Double?, current: Double): Double =
        if (previous == null) 0.0 else (current - previous).coerceAtLeast(0.0)
}
