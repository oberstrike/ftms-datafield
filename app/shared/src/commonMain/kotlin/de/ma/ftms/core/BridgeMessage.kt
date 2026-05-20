package de.ma.ftms.core

data class BridgeMessage(
    val sequence: Int,
    val sample: SmoothedFtmsSample,
) {
    fun toMap(): Map<String, Any> {
        val raw = sample.raw
        val values = mutableMapOf<String, Any>(
            "v" to 2,
            "type" to "ftms",
            "kind" to raw.kind,
            "seq" to sequence,
            "flags" to raw.rawFlags,
            "dist10" to (sample.distanceM * 10.0).toInt(),
            "ascent10" to (sample.ascentM * 10.0).toInt(),
        )

        raw.speedKmh?.let { values["speed100"] = (it * 100.0).toInt() }
        raw.distanceM?.let { values["ftmsDist"] = it }
        raw.inclinePct?.let { values["incl10"] = (it * 10.0).toInt() }
        raw.powerW?.let { values["power"] = it }
        raw.heartRateBpm?.let { values["hr"] = it }
        raw.elapsedS?.let { values["elapsed"] = it }
        raw.remainingS?.let { values["remaining"] = it }
        raw.cadenceRpm?.let { values["cad10"] = (it * 10.0).toInt() }
        raw.stepRateSpm?.let { values["step10"] = (it * 10.0).toInt() }
        raw.resistance?.let { values["res10"] = (it * 10.0).toInt() }

        return values
    }

    companion object {
        fun stopMap(sequence: Int): Map<String, Any> =
            mapOf(
                "v" to 2,
                "type" to "ftms_stop",
                "seq" to sequence,
            )

        fun controlMap(sequence: Int, type: String): Map<String, Any> =
            mapOf(
                "v" to 2,
                "type" to type,
                "seq" to sequence,
            )
    }
}
