package de.ma.ftms.relay

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The relay's events in a file, so a training can be looked into afterwards: when the watch
 * subscribed, when a link dropped. Once the file exceeds [maxBytes] it becomes the previous one
 * and a new file begins, so at most twice that is kept.
 */
internal class RelayLog(directory: File, private val maxBytes: Long = MAX_LOG_BYTES) {
    private val current = File(directory, CURRENT_NAME)
    private val previous = File(directory, PREVIOUS_NAME)
    private val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    init {
        directory.mkdirs()
    }

    /** Appends one line. A failure to write must not stop the relay, so it is ignored. */
    @Synchronized
    fun append(atMillis: Long, message: String) {
        runCatching {
            if (current.length() >= maxBytes) {
                previous.delete()
                current.renameTo(previous)
            }
            current.appendText("${timestamp.format(Date(atMillis))} $message\n")
        }
    }

    companion object {
        const val CURRENT_NAME = "relay.log"
        const val PREVIOUS_NAME = "relay.previous.log"
        const val MAX_LOG_BYTES = 1_000_000L
    }
}
