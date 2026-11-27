package de.ma.ftms.core

/** What a running sensor that an app provides for the watch is doing. */
enum class FootPodStatus {
    /** Not wanted, or not running. */
    OFF,

    /** This phone cannot act as a Bluetooth sensor. */
    UNSUPPORTED,

    /** Android has not been allowed to make the phone visible as a sensor. */
    PERMISSION_MISSING,

    /** Visible, but no watch has subscribed to the measurements yet. */
    ADVERTISING,

    /** A watch receives the measurements. */
    CONNECTED,

    /** The sensor could not be set up. */
    FAILED,
}

/** The model names by which the running sensors of this project are told apart. */
object FootPodModel {
    /** The sensor of the bridge app, fed by the equipment. */
    const val BRIDGE = "Treadmill foot pod"

    /** A relay passing the bridge's sensor on. It differs, so a relay never takes a relay as its source. */
    const val RELAY = "Treadmill foot pod relay"
}

/**
 * What a client of the running sensor had switched on when it was last connected: the measurements
 * and the answers of the control point. Kept as two characters, "1" for on.
 */
data class RememberedSubscriptions(val measurement: Boolean, val controlPoint: Boolean) {
    fun encode(): String = "${if (measurement) '1' else '0'}${if (controlPoint) '1' else '0'}"

    companion object {
        /** Null for anything [encode] does not produce, so a damaged entry counts as not remembered. */
        fun decode(text: String?): RememberedSubscriptions? {
            if (text == null || text.length != 2 || text.any { it != '0' && it != '1' }) {
                return null
            }

            return RememberedSubscriptions(measurement = text[0] == '1', controlPoint = text[1] == '1')
        }
    }
}
