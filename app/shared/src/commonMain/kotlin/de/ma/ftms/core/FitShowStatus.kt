package de.ma.ftms.core

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
 * The rate is what the count gained over the last [windowMillis]. It is unknown (null) until the
 * count was seen to rise at all, so equipment that does not count steps reports no rate rather
 * than zero, and until [minSpanMillis] have been observed. Once the count has not risen for
 * [idleMillis], the rate is zero: nobody is walking. A count that drops starts over.
 *
 * All times are milliseconds of one clock. Not thread-safe.
 */
class StepRateEstimator(
    private val windowMillis: Long = 15_000L,
    private val minSpanMillis: Long = 5_000L,
    private val idleMillis: Long = 4_000L,
) {
    private val counts = ArrayDeque<Pair<Long, Int>>()
    private var lastRiseAtMillis: Long? = null

    fun reset() {
        counts.clear()
        lastRiseAtMillis = null
    }

    fun update(stepCount: Int, nowMillis: Long): Double? {
        val previous = counts.lastOrNull()
        if (previous != null && stepCount < previous.second) {
            reset()
        } else if (previous != null && stepCount > previous.second) {
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
        val (firstAt, firstCount) = counts.first()
        val spanMillis = nowMillis - firstAt
        if (spanMillis < minSpanMillis) {
            return null
        }
        return (stepCount - firstCount) * 60_000.0 / spanMillis
    }
}
