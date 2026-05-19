package de.ma.ftms.core

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
                distanceM = u32(bytes, offset).toInt()
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
