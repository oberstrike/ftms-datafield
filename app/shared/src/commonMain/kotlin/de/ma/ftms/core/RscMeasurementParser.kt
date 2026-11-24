package de.ma.ftms.core

import kotlin.math.roundToLong

object RscMeasurementParser {
    fun parse(bytes: ByteArray, timestampMillis: Long? = null): FtmsSample {
        val rawHex = bytes.joinToString(" ") { it.toUByte().toString(16).padStart(2, '0') }

        return runCatching {
            require(bytes.size >= 4) { "truncated at offset 0, need 4, len=${bytes.size}" }
            val flags = bytes[0].toInt() and 0xff
            val speedMetersPerSecond = (u16(bytes, 1) / 256.0)
            val cadenceSpm = bytes[3].toInt() and 0xff
            var offset = 4
            var distanceM: Int? = null

            if (flags and 0x01 != 0) {
                require(bytes.size >= offset + 2) { "truncated at offset $offset, need 2, len=${bytes.size}" }
                offset += 2
            }

            if (flags and 0x02 != 0) {
                require(bytes.size >= offset + 4) { "truncated at offset $offset, need 4, len=${bytes.size}" }
                // The characteristic carries the total distance in tenths of a metre.
                distanceM = (u32(bytes, offset) / 10L).toInt()
            }

            FtmsSample(
                kind = FtmsMachineKind.TREADMILL,
                timestampMillis = timestampMillis,
                rawFlags = flags,
                rawHex = rawHex,
                speedKmh = speedMetersPerSecond * 3.6,
                distanceM = distanceM,
                stepRateSpm = cadenceSpm.toDouble(),
            )
        }.getOrElse { error ->
            FtmsSample(
                kind = FtmsMachineKind.TREADMILL,
                timestampMillis = timestampMillis,
                rawHex = rawHex,
                truncated = true,
                parseError = error.message,
            )
        }
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun u32(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xff) or
            ((bytes[offset + 1].toLong() and 0xff) shl 8) or
            ((bytes[offset + 2].toLong() and 0xff) shl 16) or
            ((bytes[offset + 3].toLong() and 0xff) shl 24)
}

/**
 * Builds the RSC Measurement (0x2A53) the app sends when it acts as a running sensor itself, so a
 * watch can take speed and distance from it.
 */
object RscMeasurement {
    const val FLAG_TOTAL_DISTANCE_PRESENT = 0x02
    const val FLAG_RUNNING = 0x04

    /** RSC Feature (0x2A54): only the total distance is offered beyond the mandatory fields. */
    const val FEATURE_TOTAL_DISTANCE = 0x0002

    /** Below this speed the "running" bit is left clear, which a watch reads as walking. */
    const val RUNNING_FROM_KMH = 7.0

    /**
     * [speedKmh] is sent in 1/256 m/s, [stepRateSpm] as whole steps per minute (0 when unknown) and
     * [totalDistanceM] in tenths of a metre. Values outside a field's range are clamped.
     */
    fun encode(speedKmh: Double, stepRateSpm: Double?, totalDistanceM: Double): ByteArray {
        val speed = (speedKmh.coerceAtLeast(0.0) / 3.6 * 256.0).roundToLong().coerceIn(0L, 0xFFFFL).toInt()
        val cadence = (stepRateSpm ?: 0.0).roundToLong().coerceIn(0L, 0xFFL).toInt()
        val distance = (totalDistanceM.coerceAtLeast(0.0) * 10.0).roundToLong().coerceIn(0L, 0xFFFFFFFFL)
        val flags = FLAG_TOTAL_DISTANCE_PRESENT or (if (speedKmh >= RUNNING_FROM_KMH) FLAG_RUNNING else 0)
        return byteArrayOf(
            flags.toByte(),
            (speed and 0xFF).toByte(),
            ((speed shr 8) and 0xFF).toByte(),
            cadence.toByte(),
            (distance and 0xFF).toByte(),
            ((distance shr 8) and 0xFF).toByte(),
            ((distance shr 16) and 0xFF).toByte(),
            ((distance shr 24) and 0xFF).toByte(),
        )
    }

    /** The value of the RSC Feature characteristic, two bytes. */
    fun feature(): ByteArray =
        byteArrayOf((FEATURE_TOTAL_DISTANCE and 0xFF).toByte(), ((FEATURE_TOTAL_DISTANCE shr 8) and 0xFF).toByte())
}

/**
 * The SC Control Point (0x2A55) of the running sensor the app provides. A sensor that offers the
 * total distance has to accept "Set Cumulative Value"; everything else is declined.
 */
object ScControlPoint {
    const val OP_SET_CUMULATIVE_VALUE = 0x01
    const val OP_RESPONSE = 0x10

    const val RESULT_SUCCESS = 0x01
    const val RESULT_NOT_SUPPORTED = 0x02
    const val RESULT_INVALID_PARAMETER = 0x03

    /** The indication that answers [request], or null if [request] is empty. */
    fun respond(request: ByteArray): ByteArray? {
        if (request.isEmpty()) {
            return null
        }

        val opCode = request[0].toInt() and 0xFF
        val result = when {
            opCode != OP_SET_CUMULATIVE_VALUE -> RESULT_NOT_SUPPORTED
            request.size < 5 -> RESULT_INVALID_PARAMETER
            else -> RESULT_SUCCESS
        }
        return byteArrayOf(OP_RESPONSE.toByte(), opCode.toByte(), result.toByte())
    }
}
