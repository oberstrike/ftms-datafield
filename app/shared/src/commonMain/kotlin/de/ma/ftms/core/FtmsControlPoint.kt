package de.ma.ftms.core

/**
 * Commands and responses of the FTMS Fitness Machine Control Point (0x2AD9), limited to what is
 * needed to set a target power, a target speed and a target inclination. Starting and stopping
 * equipment is left out on purpose.
 */
object FtmsControlPoint {
    const val OP_REQUEST_CONTROL = 0x00
    const val OP_SET_TARGET_SPEED = 0x02
    const val OP_SET_TARGET_INCLINATION = 0x03
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

    /** Target speed in hundredths of a km/h, sent as an unsigned 16-bit value. */
    fun setTargetSpeed(hundredthsKmh: Int): ByteArray =
        command(OP_SET_TARGET_SPEED, hundredthsKmh.coerceIn(0, 0xFFFF))

    /** Target inclination in tenths of a percent, sent as a signed 16-bit value. */
    fun setTargetInclination(tenthsPercent: Int): ByteArray =
        command(OP_SET_TARGET_INCLINATION, tenthsPercent.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()))

    private fun command(opCode: Int, value: Int): ByteArray =
        byteArrayOf(opCode.toByte(), (value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())

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

/**
 * Notifications of the FTMS Fitness Machine Status characteristic (0x2ADA), limited to the one that
 * names a target power.
 */
object FtmsMachineStatus {
    const val OP_TARGET_POWER_CHANGED = 0x08

    /**
     * The target power in watts if [bytes] report that it changed, null for every other status.
     * Equipment sends this after a change on its own controls and after a command that set it.
     */
    fun targetPowerWatts(bytes: ByteArray): Int? {
        if (bytes.size < 3 || (bytes[0].toInt() and 0xFF) != OP_TARGET_POWER_CHANGED) {
            return null
        }

        // Signed 16 bits, low byte first.
        return (bytes[1].toInt() and 0xFF) or (bytes[2].toInt() shl 8)
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

/**
 * What the equipment says it can be told, from the Target Setting Features of the Fitness Machine
 * Feature characteristic (0x2ACC), limited to speed and inclination.
 */
data class FtmsTargetSettings(
    val speed: Boolean,
    val inclination: Boolean,
) {
    companion object {
        private const val SPEED_BIT = 0x01
        private const val INCLINATION_BIT = 0x02

        /** Reads the characteristic value; null if it is too short to hold the target settings. */
        fun parse(bytes: ByteArray): FtmsTargetSettings? {
            if (bytes.size < 8) {
                return null
            }

            // Four bytes of machine features come first; the target settings follow, low byte first.
            val settings = bytes[4].toInt() and 0xFF
            return FtmsTargetSettings(
                speed = settings and SPEED_BIT != 0,
                inclination = settings and INCLINATION_BIT != 0,
            )
        }
    }
}

/**
 * The values the equipment accepts for one target, in the unit of its command: hundredths of a
 * km/h for the speed (Supported Speed Range, 0x2AD4), tenths of a percent for the inclination
 * (Supported Inclination Range, 0x2AD5).
 */
data class FtmsValueRange(
    val min: Int,
    val max: Int,
    val step: Int,
) {
    /** The accepted value closest to [value]. */
    fun nearest(value: Int): Int {
        val clamped = value.coerceIn(min, max)
        if (step <= 1) {
            return clamped
        }

        val steps = (clamped - min + step / 2) / step
        val snapped = min + steps * step
        return if (snapped > max) snapped - step else snapped
    }

    companion object {
        /** Reads a Supported Speed Range; null if it is truncated or names an empty range. */
        fun parseSpeed(bytes: ByteArray): FtmsValueRange? = parse(bytes, signed = false)

        /** Reads a Supported Inclination Range; null if it is truncated or names an empty range. */
        fun parseInclination(bytes: ByteArray): FtmsValueRange? = parse(bytes, signed = true)

        private fun parse(bytes: ByteArray, signed: Boolean): FtmsValueRange? {
            if (bytes.size < 6) {
                return null
            }

            val min = int16(bytes, 0, signed)
            val max = int16(bytes, 2, signed)
            val step = int16(bytes, 4, signed = false)
            if (max < min) {
                return null
            }

            return FtmsValueRange(min = min, max = max, step = step.coerceAtLeast(1))
        }

        private fun int16(bytes: ByteArray, offset: Int, signed: Boolean): Int {
            val unsigned = (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
            return if (signed && unsigned >= 0x8000) unsigned - 0x10000 else unsigned
        }
    }
}
