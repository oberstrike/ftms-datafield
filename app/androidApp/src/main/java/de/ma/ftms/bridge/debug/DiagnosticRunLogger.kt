package de.ma.ftms.bridge.debug

import android.content.Context
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DiagnosticRunLogger(context: Context) {
    private val logDir = File(context.filesDir, LOG_DIR_NAME)
    private val latestFile = File(logDir, LATEST_FILE_NAME)
    private val ringBuffer = ArrayDeque<LogEntry>(MAX_RECENT_ENTRIES)
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    private var runWriter: BufferedWriter? = null
    private var latestWriter: BufferedWriter? = null
    private var runFile: File? = null
    private var nextEntryId = 0L

    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    val latestLogPath: String?
        get() = latestFile.takeIf { it.exists() }?.absolutePath

    @Synchronized
    fun startRun(startedAtMillis: Long, sessionId: String?, treadmillName: String?, garminName: String?) {
        stopRun("replaced")
        logDir.mkdirs()
        rotateRunFiles()
        runFile = File(logDir, "run_$startedAtMillis.log")
        runWriter = BufferedWriter(FileWriter(runFile, false))
        latestWriter = BufferedWriter(FileWriter(latestFile, false))
        clearRecent()
        info(
            TAG,
            "run_start startedAt=$startedAtMillis session=${sessionId ?: "pending"} treadmill=${treadmillName ?: "unknown"} garmin=${garminName ?: "unknown"}",
        )
    }

    @Synchronized
    fun attachSession(sessionId: String?) {
        info(TAG, "session_attached session=${sessionId ?: "none"}")
    }

    @Synchronized
    fun stopRun(status: String) {
        if (runWriter == null && latestWriter == null) {
            return
        }

        info(TAG, "run_stop status=$status")
        runCatching { runWriter?.flush() }
        runCatching { latestWriter?.flush() }
        runCatching { runWriter?.close() }
        runCatching { latestWriter?.close() }
        runWriter = null
        latestWriter = null
        runFile = null
    }

    @Synchronized
    fun clearRecent() {
        ringBuffer.clear()
        _logs.value = emptyList()
    }

    fun debug(tag: String, message: String) = write(LogLevel.DEBUG, tag, message)

    fun info(tag: String, message: String) = write(LogLevel.INFO, tag, message)

    fun warn(tag: String, message: String) = write(LogLevel.WARN, tag, message)

    fun error(tag: String, message: String) = write(LogLevel.ERROR, tag, message)

    @Synchronized
    private fun write(level: LogLevel, tag: String, message: String) {
        val now = System.currentTimeMillis()
        val entry = LogEntry(
            entryId = nextEntryId++,
            timestamp = now,
            level = level,
            tag = tag,
            message = message,
        )
        val line = "${timestampFormat.format(Date(now))} ${level.char}/$tag: $message"
        runCatching {
            runWriter?.appendLine(line)
            latestWriter?.appendLine(line)
        }
        ringBuffer.addLast(entry)
        while (ringBuffer.size > MAX_RECENT_ENTRIES) {
            ringBuffer.removeFirst()
        }
        _logs.value = ringBuffer.toList().asReversed()
    }

    private fun rotateRunFiles() {
        val files = logDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("run_") && it.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        files.drop(MAX_RUN_FILES - 1).forEach { it.delete() }
    }

    private companion object {
        const val LOG_DIR_NAME = "debug_runs"
        const val LATEST_FILE_NAME = "latest.log"
        const val MAX_RUN_FILES = 5
        const val MAX_RECENT_ENTRIES = 300
        const val TAG = "DiagRun"

        val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }
}
