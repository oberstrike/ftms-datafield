package de.ma.ftms.bridge.runtime

/**
 * Decides when a session should end by itself because the equipment has stopped. Whether that
 * is wanted for the equipment at all is a separate question, see [isAutoStopWanted].
 *
 * The session ends once the equipment, after having moved in this session, reports standstill for
 * [standstillMillis] without a break, or has not reported movement for [silenceMillis] at all (it
 * was switched off or is out of reach). A session whose equipment has not moved yet is left alone,
 * so starting the bridge before stepping on does not end it again.
 *
 * All times are milliseconds of one clock. Not thread-safe.
 */
internal class AutoStopPolicy(
    private val standstillMillis: Long = AUTO_STOP_STANDSTILL_MS,
    private val silenceMillis: Long = AUTO_STOP_SILENCE_MS,
) {
    private var lastMovingAtMillis: Long? = null
    private var standstillSinceMillis: Long? = null

    /** Forgets everything, for a new session. */
    fun reset() {
        lastMovingAtMillis = null
        standstillSinceMillis = null
    }

    /** Takes in what a sample says about movement; [moving] is null if it says nothing. */
    fun onMovementReported(moving: Boolean?, nowMillis: Long) {
        when (moving) {
            true -> {
                lastMovingAtMillis = nowMillis
                standstillSinceMillis = null
            }
            false -> if (lastMovingAtMillis != null && standstillSinceMillis == null) {
                standstillSinceMillis = nowMillis
            }
            null -> Unit
        }
    }

    /**
     * The moment the equipment stopped if the session should end now, otherwise null. The session
     * is ended as of that moment, so the wait for certainty does not count as session time.
     */
    fun stopDueSince(nowMillis: Long): Long? {
        val lastMovingAt = lastMovingAtMillis ?: return null
        val standstillSince = standstillSinceMillis
        return when {
            standstillSince != null && nowMillis - standstillSince >= standstillMillis -> standstillSince
            nowMillis - lastMovingAt >= silenceMillis -> lastMovingAt
            else -> null
        }
    }
}

/**
 * True when sessions on [equipmentType] end by themselves. Only a treadmill qualifies: its belt
 * stands still because it was stopped, whereas an indoor bike or cross trainer stands still
 * whenever the user takes a break.
 */
internal fun isAutoStopWanted(settings: BridgeSettings, equipmentType: EquipmentType): Boolean =
    settings.autoStop && equipmentType == EquipmentType.TREADMILL

internal const val AUTO_STOP_STANDSTILL_MS = 30_000L
internal const val AUTO_STOP_SILENCE_MS = 90_000L
internal const val AUTO_STOP_CHECK_INTERVAL_MS = 2_000L
