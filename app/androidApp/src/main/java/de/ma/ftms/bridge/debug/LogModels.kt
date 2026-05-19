package de.ma.ftms.bridge.debug

import kotlinx.coroutines.flow.StateFlow

data class LogEntry(
    val entryId: Long,
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val pid: Int = 0,
    val tid: Int = 0,
)

enum class LogLevel(val char: String) {
    DEBUG("D"),
    INFO("I"),
    WARN("W"),
    ERROR("E"),
}

interface LogCaptureService {
    val logs: StateFlow<List<LogEntry>>
    val isCapturing: StateFlow<Boolean>

    fun startCapture()
    fun stopCapture()
    fun clearLogs()
    fun updateMaxEntries(maxEntries: Int)
    suspend fun latestLogFilePath(): String?

    companion object {
        const val DEFAULT_MAX_ENTRIES = 5_000
        const val FLUSH_INTERVAL_MS = 100L
    }
}
