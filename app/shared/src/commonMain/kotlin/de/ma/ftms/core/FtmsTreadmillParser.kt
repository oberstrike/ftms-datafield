package de.ma.ftms.core

object FtmsTreadmillParser {
    fun parse(bytes: ByteArray, timestampMillis: Long? = null): FtmsSample {
        val rawHex = bytes.joinToString(" ") { it.toUByte().toString(16).padStart(2, '0') }

        return runCatching {
            requireLength(bytes, 0, 2)
            val flags = u16(bytes, 0)
            var offset = 2

            var speedKmh: Double? = null
            var distanceM: Int? = null
            var inclinePct: Double? = null
            var rampDeg: Double? = null
            var positiveElevationM: Double? = null
            var negativeElevationM: Double? = null
            var powerW: Int? = null
            var heartRateBpm: Int? = null
            var elapsedS: Int? = null
            var remainingS: Int? = null

            if (!hasFlag(flags, 0)) {
                requireLength(bytes, offset, 2)
                speedKmh = u16(bytes, offset) / 100.0
                offset += 2
            }

            if (hasFlag(flags, 1)) {
                requireLength(bytes, offset, 2)
                offset += 2
            }

            if (hasFlag(flags, 2)) {
                requireLength(bytes, offset, 3)
                distanceM = u24(bytes, offset)
                offset += 3
            }

            if (hasFlag(flags, 3)) {
                requireLength(bytes, offset, 4)
                inclinePct = s16(bytes, offset) / 10.0
                offset += 2
                rampDeg = s16(bytes, offset) / 10.0
                offset += 2
            }

            if (hasFlag(flags, 4)) {
                requireLength(bytes, offset, 4)
                positiveElevationM = u16(bytes, offset) / 10.0
                offset += 2
                negativeElevationM = u16(bytes, offset) / 10.0
                offset += 2
            }

            if (hasFlag(flags, 5)) {
                requireLength(bytes, offset, 2)
                offset += 2
            }

            if (hasFlag(flags, 6)) {
                requireLength(bytes, offset, 2)
                offset += 2
            }

            if (hasFlag(flags, 7)) {
                requireLength(bytes, offset, 5)
                offset += 5
            }

            if (hasFlag(flags, 8)) {
                requireLength(bytes, offset, 1)
                heartRateBpm = u8(bytes, offset)
                offset += 1
            }

            if (hasFlag(flags, 9)) {
                requireLength(bytes, offset, 1)
                offset += 1
            }

            if (hasFlag(flags, 10)) {
                requireLength(bytes, offset, 2)
                elapsedS = u16(bytes, offset)
                offset += 2
            }

            if (hasFlag(flags, 11)) {
                requireLength(bytes, offset, 2)
                remainingS = u16(bytes, offset)
                offset += 2
            }

            if (hasFlag(flags, 12)) {
                requireLength(bytes, offset, 4)
                offset += 2
                powerW = s16(bytes, offset)
            }

            FtmsSample(
                kind = FtmsMachineKind.TREADMILL,
                timestampMillis = timestampMillis,
                rawFlags = flags,
                rawHex = rawHex,
                speedKmh = speedKmh,
                distanceM = distanceM,
                inclinePct = inclinePct,
                rampDeg = rampDeg,
                positiveElevationM = positiveElevationM,
                negativeElevationM = negativeElevationM,
                powerW = powerW,
                heartRateBpm = heartRateBpm,
                elapsedS = elapsedS,
                remainingS = remainingS,
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
}

object FtmsIndoorBikeParser {
    fun parse(bytes: ByteArray, timestampMillis: Long? = null): FtmsSample {
        val rawHex = bytes.toRawHex()

        return runCatching {
            requireLength(bytes, 0, 2)
            val flags = u16(bytes, 0)
            var offset = 2

            var speedKmh: Double? = null
            var distanceM: Int? = null
            var cadenceRpm: Double? = null
            var resistance: Double? = null
            var powerW: Int? = null
            var heartRateBpm: Int? = null
            var elapsedS: Int? = null
            var remainingS: Int? = null

            if (!hasFlag(flags, 0)) {
                requireLength(bytes, offset, 2)
                speedKmh = u16(bytes, offset) / 100.0
                offset += 2
            }

            if (hasFlag(flags, 1)) {
                requireLength(bytes, offset, 2)
                offset += 2
            }

            if (hasFlag(flags, 2)) {
                requireLength(bytes, offset, 2)
                cadenceRpm = u16(bytes, offset) / 2.0
                offset += 2
            }

            if (hasFlag(flags, 3)) {
                requireLength(bytes, offset, 2)
                offset += 2
            }

            if (hasFlag(flags, 4)) {
                requireLength(bytes, offset, 3)
                distanceM = u24(bytes, offset)
                offset += 3
            }

            if (hasFlag(flags, 5)) {
                requireLength(bytes, offset, 2)
                resistance = s16(bytes, offset).toDouble()
                offset += 2
            }

            if (hasFlag(flags, 6)) {
                requireLength(bytes, offset, 2)
                powerW = s16(bytes, offset)
                offset += 2
            }

            if (hasFlag(flags, 7)) {
                requireLength(bytes, offset, 2)
                offset += 2
            }

            if (hasFlag(flags, 8)) {
                requireLength(bytes, offset, 5)
                offset += 5
            }

            if (hasFlag(flags, 9)) {
                requireLength(bytes, offset, 1)
                heartRateBpm = u8(bytes, offset)
                offset += 1
            }

            if (hasFlag(flags, 10)) {
                requireLength(bytes, offset, 1)
                offset += 1
            }

            if (hasFlag(flags, 11)) {
                requireLength(bytes, offset, 2)
                elapsedS = u16(bytes, offset)
                offset += 2
            }

            if (hasFlag(flags, 12)) {
                requireLength(bytes, offset, 2)
                remainingS = u16(bytes, offset)
            }

            FtmsSample(
                kind = FtmsMachineKind.INDOOR_BIKE,
                timestampMillis = timestampMillis,
                rawFlags = flags,
                rawHex = rawHex,
                speedKmh = speedKmh,
                distanceM = distanceM,
                cadenceRpm = cadenceRpm,
                resistance = resistance,
                powerW = powerW,
                heartRateBpm = heartRateBpm,
                elapsedS = elapsedS,
                remainingS = remainingS,
            )
        }.getOrElse { error ->
            FtmsSample(
                kind = FtmsMachineKind.INDOOR_BIKE,
                timestampMillis = timestampMillis,
                rawHex = rawHex,
                truncated = true,
                parseError = error.message,
            )
        }
    }
}

object FtmsCrossTrainerParser {
    fun parse(bytes: ByteArray, timestampMillis: Long? = null): FtmsSample {
        val rawHex = bytes.toRawHex()

        return runCatching {
            requireLength(bytes, 0, 3)
            val flags = u24(bytes, 0)
            var offset = 3

            var speedKmh: Double? = null
            var distanceM: Int? = null
            var stepRateSpm: Double? = null
            var strideCount: Double? = null
            var positiveElevationM: Double? = null
            var negativeElevationM: Double? = null
            var inclinePct: Double? = null
            var rampDeg: Double? = null
            var resistance: Double? = null
            var powerW: Int? = null
            var heartRateBpm: Int? = null
            var elapsedS: Int? = null
            var remainingS: Int? = null
            val movementDirection = if (hasFlag(flags, 15)) 1 else 0

            if (!hasFlag(flags, 0)) {
                requireLength(bytes, offset, 2)
                speedKmh = u16(bytes, offset) / 100.0
                offset += 2
            }

            if (hasFlag(flags, 1)) {
                requireLength(bytes, offset, 2)
                offset += 2
            }

            if (hasFlag(flags, 2)) {
                requireLength(bytes, offset, 3)
                distanceM = u24(bytes, offset)
                offset += 3
            }

            if (hasFlag(flags, 3)) {
                requireLength(bytes, offset, 4)
                stepRateSpm = u16(bytes, offset).toDouble()
                offset += 4
            }

            if (hasFlag(flags, 4)) {
                requireLength(bytes, offset, 2)
                strideCount = u16(bytes, offset) / 10.0
                offset += 2
            }

            if (hasFlag(flags, 5)) {
                requireLength(bytes, offset, 4)
                positiveElevationM = u16(bytes, offset).toDouble()
                offset += 2
                negativeElevationM = u16(bytes, offset).toDouble()
                offset += 2
            }

            if (hasFlag(flags, 6)) {
                requireLength(bytes, offset, 4)
                inclinePct = s16(bytes, offset) / 10.0
                offset += 2
                rampDeg = s16(bytes, offset) / 10.0
                offset += 2
            }

            if (hasFlag(flags, 7)) {
                requireLength(bytes, offset, 2)
                resistance = s16(bytes, offset) / 10.0
                offset += 2
            }

            if (hasFlag(flags, 8)) {
                requireLength(bytes, offset, 2)
                powerW = s16(bytes, offset)
                offset += 2
            }

            if (hasFlag(flags, 9)) {
                requireLength(bytes, offset, 2)
                offset += 2
            }

            if (hasFlag(flags, 10)) {
                requireLength(bytes, offset, 5)
                offset += 5
            }

            if (hasFlag(flags, 11)) {
                requireLength(bytes, offset, 1)
                heartRateBpm = u8(bytes, offset)
                offset += 1
            }

            if (hasFlag(flags, 12)) {
                requireLength(bytes, offset, 1)
                offset += 1
            }

            if (hasFlag(flags, 13)) {
                requireLength(bytes, offset, 2)
                elapsedS = u16(bytes, offset)
                offset += 2
            }

            if (hasFlag(flags, 14)) {
                requireLength(bytes, offset, 2)
                remainingS = u16(bytes, offset)
            }

            FtmsSample(
                kind = FtmsMachineKind.CROSS_TRAINER,
                timestampMillis = timestampMillis,
                rawFlags = flags,
                rawHex = rawHex,
                speedKmh = speedKmh,
                distanceM = distanceM,
                stepRateSpm = stepRateSpm,
                strideCount = strideCount,
                positiveElevationM = positiveElevationM,
                negativeElevationM = negativeElevationM,
                inclinePct = inclinePct,
                rampDeg = rampDeg,
                resistance = resistance,
                powerW = powerW,
                heartRateBpm = heartRateBpm,
                elapsedS = elapsedS,
                remainingS = remainingS,
                movementDirection = movementDirection,
            )
        }.getOrElse { error ->
            FtmsSample(
                kind = FtmsMachineKind.CROSS_TRAINER,
                timestampMillis = timestampMillis,
                rawHex = rawHex,
                truncated = true,
                parseError = error.message,
            )
        }
    }
}

object FtmsParser {
    fun parse(kind: Int, bytes: ByteArray, timestampMillis: Long? = null): FtmsSample =
        when (kind) {
            FtmsMachineKind.INDOOR_BIKE -> FtmsIndoorBikeParser.parse(bytes, timestampMillis)
            FtmsMachineKind.CROSS_TRAINER -> FtmsCrossTrainerParser.parse(bytes, timestampMillis)
            else -> FtmsTreadmillParser.parse(bytes, timestampMillis)
        }
}

private fun ByteArray.toRawHex(): String =
    joinToString(" ") { it.toUByte().toString(16).padStart(2, '0') }

private fun hasFlag(flags: Int, bit: Int): Boolean = flags and (1 shl bit) != 0

private fun requireLength(bytes: ByteArray, offset: Int, length: Int) {
    require(offset + length <= bytes.size) {
        "truncated at offset $offset, need $length, len=${bytes.size}"
    }
}

private fun u8(bytes: ByteArray, offset: Int): Int = bytes[offset].toInt() and 0xff

private fun u16(bytes: ByteArray, offset: Int): Int = u8(bytes, offset) or (u8(bytes, offset + 1) shl 8)

private fun s16(bytes: ByteArray, offset: Int): Int {
    val value = u16(bytes, offset)
    return if (value and 0x8000 != 0) value - 0x10000 else value
}

private fun u24(bytes: ByteArray, offset: Int): Int =
    u8(bytes, offset) or (u8(bytes, offset + 1) shl 8) or (u8(bytes, offset + 2) shl 16)
