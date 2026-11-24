package de.ma.ftms.core

/**
 * Combines FTMS notifications that carry only part of a record.
 *
 * A machine may split one record over several notifications (the "More Data" flag), and some
 * machines alternate between notifications that each carry a different subset of fields. Without
 * merging, every such notification would blank the fields it does not repeat.
 *
 * The merger remembers the last value of each field and fills gaps in the next sample with it, as
 * long as that value is not older than [maxFieldAgeMillis]. A sample that already carries every
 * field passes through unchanged.
 */
class FtmsSampleMerger(
    private val maxFieldAgeMillis: Long = DEFAULT_MAX_FIELD_AGE_MILLIS,
) {
    private var kind: Int? = null
    private val speedKmh = Carried<Double>()
    private val distanceM = Carried<Int>()
    private val cadenceRpm = Carried<Double>()
    private val resistance = Carried<Double>()
    private val inclinePct = Carried<Double>()
    private val rampDeg = Carried<Double>()
    private val positiveElevationM = Carried<Double>()
    private val negativeElevationM = Carried<Double>()
    private val powerW = Carried<Int>()
    private val heartRateBpm = Carried<Int>()
    private val elapsedS = Carried<Int>()
    private val remainingS = Carried<Int>()
    private val stepRateSpm = Carried<Double>()
    private val strideCount = Carried<Double>()
    private val all = listOf(
        speedKmh, distanceM, cadenceRpm, resistance, inclinePct, rampDeg, positiveElevationM,
        negativeElevationM, powerW, heartRateBpm, elapsedS, remainingS, stepRateSpm, strideCount,
    )

    fun reset() {
        kind = null
        all.forEach { it.clear() }
    }

    fun merge(sample: FtmsSample): FtmsSample {
        // Without a receive time the age of carried values is unknown, so nothing is carried.
        val now = sample.timestampMillis ?: return sample
        if (kind != sample.kind) {
            reset()
            kind = sample.kind
        }

        return sample.copy(
            speedKmh = speedKmh.resolve(sample.speedKmh, now),
            distanceM = distanceM.resolve(sample.distanceM, now),
            cadenceRpm = cadenceRpm.resolve(sample.cadenceRpm, now),
            resistance = resistance.resolve(sample.resistance, now),
            inclinePct = inclinePct.resolve(sample.inclinePct, now),
            rampDeg = rampDeg.resolve(sample.rampDeg, now),
            positiveElevationM = positiveElevationM.resolve(sample.positiveElevationM, now),
            negativeElevationM = negativeElevationM.resolve(sample.negativeElevationM, now),
            powerW = powerW.resolve(sample.powerW, now),
            heartRateBpm = heartRateBpm.resolve(sample.heartRateBpm, now),
            elapsedS = elapsedS.resolve(sample.elapsedS, now),
            remainingS = remainingS.resolve(sample.remainingS, now),
            stepRateSpm = stepRateSpm.resolve(sample.stepRateSpm, now),
            strideCount = strideCount.resolve(sample.strideCount, now),
        )
    }

    private inner class Carried<T : Any> {
        private var value: T? = null
        private var updatedAtMillis: Long = 0

        fun clear() {
            value = null
            updatedAtMillis = 0
        }

        fun resolve(fresh: T?, nowMillis: Long): T? {
            if (fresh != null) {
                value = fresh
                updatedAtMillis = nowMillis
                return fresh
            }

            val ageMillis = nowMillis - updatedAtMillis
            return value?.takeIf { ageMillis in 0..maxFieldAgeMillis }
        }
    }

    companion object {
        const val DEFAULT_MAX_FIELD_AGE_MILLIS = 3_000L
    }
}
