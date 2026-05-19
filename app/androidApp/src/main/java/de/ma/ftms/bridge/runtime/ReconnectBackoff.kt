package de.ma.ftms.bridge.runtime

class ReconnectBackoff(
    private val delaysMillis: List<Long> = listOf(2_000L, 5_000L, 10_000L, 30_000L),
) {
    private var attempt = 0

    fun nextDelayMillis(): Long {
        val delay = delaysMillis[attempt.coerceAtMost(delaysMillis.lastIndex)]
        attempt += 1
        return delay
    }

    fun reset() {
        attempt = 0
    }
}
