package de.ma.ftms.core

/**
 * Commands and responses of the FTMS Fitness Machine Control Point (0x2AD9), limited to what is
 * needed to set a target power.
 */
object FtmsControlPoint {
    const val OP_REQUEST_CONTROL = 0x00
    const val OP_SET_TARGET_POWER = 0x05
    const val OP_RESPONSE = 0x80

    const val RESULT_SUCCESS = 0x01
    const val RESULT_NOT_SUPPORTED = 0x02
    const val RESULT_INVALID_PARAMETER = 0x03
    const val RESULT_FAILED = 0x04
    const val RESULT_CONTROL_NOT_PERMITTED = 0x05

    /** Must be accepted by the equipment before it takes any other command. */
    fun requestControl(): ByteArray = byteArrayOf(OP_REQUEST_CONTROL.toByte())

    /** Target power in watts, sent as a signed 16-bit value. */
    fun setTargetPower(watts: Int): ByteArray {
        val value = watts.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        return byteArrayOf(
            OP_SET_TARGET_POWER.toByte(),
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
        )
    }

    /** Reads a response indication; null for anything that is not one. */
    fun parseResponse(bytes: ByteArray): FtmsControlResponse? {
        if (bytes.size < 3 || (bytes[0].toInt() and 0xFF) != OP_RESPONSE) {
            return null
        }

        return FtmsControlResponse(
            requestOpCode = bytes[1].toInt() and 0xFF,
            resultCode = bytes[2].toInt() and 0xFF,
        )
    }
}

/** The equipment's answer to one control point command. */
data class FtmsControlResponse(
    val requestOpCode: Int,
    val resultCode: Int,
) {
    val succeeded: Boolean
        get() = resultCode == FtmsControlPoint.RESULT_SUCCESS
}

/** The target powers the equipment accepts (Supported Power Range, 0x2AD8). */
data class FtmsPowerRange(
    val minWatts: Int,
    val maxWatts: Int,
    val stepWatts: Int,
) {
    /** The accepted target power closest to [watts]. */
    fun nearest(watts: Int): Int {
        val clamped = watts.coerceIn(minWatts, maxWatts)
        if (stepWatts <= 1) {
            return clamped
        }

        val steps = (clamped - minWatts + stepWatts / 2) / stepWatts
        val snapped = minWatts + steps * stepWatts
        return if (snapped > maxWatts) snapped - stepWatts else snapped
    }

    companion object {
        /** Reads the characteristic value; null if it is truncated or names an empty range. */
        fun parse(bytes: ByteArray): FtmsPowerRange? {
            if (bytes.size < 6) {
                return null
            }

            val min = sint16(bytes, 0)
            val max = sint16(bytes, 2)
            val step = (bytes[4].toInt() and 0xFF) or ((bytes[5].toInt() and 0xFF) shl 8)
            if (max < min) {
                return null
            }

            return FtmsPowerRange(minWatts = min, maxWatts = max, stepWatts = step.coerceAtLeast(1))
        }

        private fun sint16(bytes: ByteArray, offset: Int): Int {
            val unsigned = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
            return if (unsigned >= 0x8000) unsigned - 0x10000 else unsigned
        }
    }
}
