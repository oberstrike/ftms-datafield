package de.ma.ftms.core

import kotlin.math.abs

/**
 * What a treadmill with the FitShow protocol reports about itself next to its FTMS data.
 *
 * [stepCount] is the treadmill's own count of steps since its start, null while the belt does
 * not run. FTMS has no field for it, so this is the only way to a step rate on such a treadmill.
 */
data class FitShowStatus(
    val running: Boolean,
    val stepCount: Int?,
)

/**
 * Reads the status packet such treadmills send on their vendor service (0xFFF0, notify 0xFFF1):
 * `02 51 <state> ... <checksum> 03`, where the checksum is the XOR of everything between the
 * start byte and itself. While running (state 3) the packet carries, after speed, incline, time,
 * distance and energy, the step count as two bytes at offset 11.
 */
object FitShowStatusParser {
    private const val START = 0x02
    private const val END = 0x03
    private const val COMMAND_STATUS = 0x51
    private const val STATE_RUNNING = 0x03
    private const val RUNNING_PACKET_SIZE = 17
    private const val STEP_COUNT_OFFSET = 11

    /** The status in [bytes], or null if they are not a valid status packet. */
    fun parse(bytes: ByteArray): FitShowStatus? {
        if (bytes.size < 5 || u8(bytes, 0) != START || u8(bytes, bytes.size - 1) != END || u8(bytes, 1) != COMMAND_STATUS) {
            return null
        }

        var checksum = 0
        for (index in 1 until bytes.size - 2) {
            checksum = checksum xor u8(bytes, index)
        }
        if (checksum != u8(bytes, bytes.size - 2)) {
            return null
        }

        if (u8(bytes, 2) != STATE_RUNNING || bytes.size < RUNNING_PACKET_SIZE) {
            return FitShowStatus(running = false, stepCount = null)
        }
        return FitShowStatus(
            running = true,
            stepCount = u8(bytes, STEP_COUNT_OFFSET) or (u8(bytes, STEP_COUNT_OFFSET + 1) shl 8),
        )
    }

    private fun u8(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 0xff
}

/**
 * Turns a running count of steps into a step rate, in steps per minute.
 *
 * The rate is the slope of the straight line that fits the counts of the last [windowMillis] best.
 * A count arrives in whole steps at the times the equipment happens to report, so the difference
 * between the two ends of the window jitters by several steps per minute; the fitted line uses
 * every report and is calmer for the same window.
 *
 * A long window alone would trail a change of rhythm by its own length. So whenever the last
 * [changeWindowMillis] on their own show a rate more than [changeThresholdSpm] away from the whole
 * window's, the walker has changed rhythm: everything older is dropped and the recent rate counts.
 *
 * The rate is unknown (null) until the count was seen to rise at all, so equipment that does not
 * count steps reports no rate rather than zero, and until [minSpanMillis] have been observed. Once
 * the count has not risen for [idleMillis], the rate is zero: nobody is walking. When it rises
 * again, the observation starts over, as it does when the count drops.
 *
 * All times are milliseconds of one clock. Not thread-safe.
 */
class StepRateEstimator(
    private val windowMillis: Long = 15_000L,
    private val minSpanMillis: Long = 5_000L,
    private val idleMillis: Long = 4_000L,
    private val changeWindowMillis: Long = 5_000L,
    private val changeThresholdSpm: Double = 18.0,
) {
    private val counts = ArrayDeque<Pair<Long, Int>>()
    private var lastRiseAtMillis: Long? = null
    private var observedEnough = false

    fun reset() {
        counts.clear()
        lastRiseAtMillis = null
        observedEnough = false
    }

    fun update(stepCount: Int, nowMillis: Long): Double? {
        val previous = counts.lastOrNull()
        if (previous != null && stepCount < previous.second) {
            reset()
        } else if (previous != null && stepCount > previous.second) {
            val lastRiseAt = lastRiseAtMillis
            if (lastRiseAt != null && nowMillis - lastRiseAt > idleMillis) {
                // Walking again after a standstill: the flat stretch says nothing about the new rhythm.
                counts.clear()
                counts.addLast(previous)
                observedEnough = false
            }
            lastRiseAtMillis = nowMillis
        }

        counts.addLast(nowMillis to stepCount)
        while (counts.size > 1 && nowMillis - counts[1].first >= windowMillis) {
            counts.removeFirst()
        }

        val lastRiseAt = lastRiseAtMillis ?: return null
        if (nowMillis - lastRiseAt > idleMillis) {
            return 0.0
        }
        val spanMillis = nowMillis - counts.first().first
        if (!observedEnough) {
            if (spanMillis < minSpanMillis) {
                return null
            }
            observedEnough = true
        }

        val rate = stepsPerMinute(counts) ?: return null
        if (spanMillis * 10 <= changeWindowMillis * 13) {
            // The recent stretch is most of the window; the two cannot disagree in a meaningful way.
            return rate
        }
        val recent = counts.filter { nowMillis - it.first <= changeWindowMillis }
        val recentRate = if (recent.size >= MIN_POINTS_FOR_A_CHANGE) stepsPerMinute(recent) else null
        if (recentRate == null || abs(recentRate - rate) <= changeThresholdSpm) {
            return rate
        }
        while (counts.size > recent.size) {
            counts.removeFirst()
        }
        return recentRate
    }

    /** The slope of the least-squares line through [points], or null if they share one moment. */
    private fun stepsPerMinute(points: List<Pair<Long, Int>>): Double? {
        val (firstAt, firstCount) = points.first()
        val meanTime = points.sumOf { (it.first - firstAt).toDouble() } / points.size
        val meanCount = points.sumOf { (it.second - firstCount).toDouble() } / points.size
        var covariance = 0.0
        var variance = 0.0
        for ((at, count) in points) {
            val time = (at - firstAt) - meanTime
            covariance += time * ((count - firstCount) - meanCount)
            variance += time * time
        }
        return if (variance > 0.0) covariance / variance * 60_000.0 else null
    }

    private companion object {
        /** Fewer reports than this in the recent stretch are too few to call a change of rhythm. */
        const val MIN_POINTS_FOR_A_CHANGE = 6
    }
}
