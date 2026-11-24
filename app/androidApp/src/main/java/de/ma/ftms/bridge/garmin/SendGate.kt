package de.ma.ftms.bridge.garmin

/**
 * Keeps messages to the watch from piling up in Garmin Connect.
 *
 * Garmin Connect reports every variant of the data field as installed, but only the one in the
 * running activity answers; a message to any other fails, and slowly. Sent every second to all of
 * them, such messages queue up by the thousand and block the one that matters. So per target (one
 * variant on one watch) at most one message is on its way, and a target that just failed is left
 * alone for [failureBackoffMillis].
 *
 * Garmin Connect answers one message after the other, and answers "delivered" for every variant
 * as long as one of them runs. Written to in turn, the variant that runs would get only every
 * ninth message. A variant that sends messages itself is the one that runs, so while one was
 * heard from within [liveMillis], samples go to it alone (see [liveAmong]).
 *
 * A message that never gets an answer stops counting as on its way after [inFlightTimeoutMillis].
 * All times are milliseconds of one monotonic clock.
 */
internal class SendGate(
    private val inFlightTimeoutMillis: Long = SEND_IN_FLIGHT_TIMEOUT_MS,
    private val failureBackoffMillis: Long = SEND_FAILURE_BACKOFF_MS,
    private val liveMillis: Long = SEND_LIVE_TARGET_MS,
) {
    private val inFlightSinceByTarget = mutableMapOf<String, Long>()
    private val blockedUntilByTarget = mutableMapOf<String, Long>()
    private val heardFromAtByTarget = mutableMapOf<String, Long>()

    /**
     * True if a message that may be left out (a sample or a repeated signal, soon followed by a
     * newer one) should go to [target] now. The message then counts as on its way.
     */
    @Synchronized
    fun tryAcquire(target: String, nowMillis: Long): Boolean {
        val blockedUntil = blockedUntilByTarget[target]
        if (blockedUntil != null && nowMillis < blockedUntil) {
            return false
        }
        val inFlightSince = inFlightSinceByTarget[target]
        if (inFlightSince != null && nowMillis - inFlightSince < inFlightTimeoutMillis) {
            return false
        }
        inFlightSinceByTarget[target] = nowMillis
        return true
    }

    /** A message that must not be left out goes to [target] now, whatever is on its way. */
    @Synchronized
    fun onSentRegardless(target: String, nowMillis: Long) {
        inFlightSinceByTarget[target] = nowMillis
    }

    /** The message to [target] was answered: delivered if [delivered], failed otherwise. */
    @Synchronized
    fun onResult(target: String, delivered: Boolean, nowMillis: Long) {
        inFlightSinceByTarget.remove(target)
        if (delivered) {
            blockedUntilByTarget.remove(target)
        } else {
            blockedUntilByTarget[target] = nowMillis + failureBackoffMillis
        }
    }

    /** [target] sent a message itself, so it runs and is written to again at once. */
    @Synchronized
    fun onHeardFrom(target: String, nowMillis: Long) {
        blockedUntilByTarget.remove(target)
        heardFromAtByTarget[target] = nowMillis
    }

    /**
     * Those of [targets] that were heard from lately, which are the ones that run. If none was,
     * all of them: any may be the one that runs.
     */
    @Synchronized
    fun liveAmong(targets: List<String>, nowMillis: Long): List<String> =
        targets
            .filter { target -> heardFromAtByTarget[target]?.let { nowMillis - it <= liveMillis } == true }
            .ifEmpty { targets }

    @Synchronized
    fun clear() {
        inFlightSinceByTarget.clear()
        blockedUntilByTarget.clear()
        heardFromAtByTarget.clear()
    }
}

/** The gate's name for one data field variant on one watch. */
internal fun sendTarget(deviceId: Long, appId: String): String =
    "$deviceId/${appId.lowercase().replace("-", "")}"

internal const val SEND_IN_FLIGHT_TIMEOUT_MS = 20_000L
internal const val SEND_FAILURE_BACKOFF_MS = 10_000L
internal const val SEND_LIVE_TARGET_MS = 30_000L
