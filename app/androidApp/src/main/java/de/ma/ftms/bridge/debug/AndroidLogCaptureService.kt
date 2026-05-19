package de.ma.ftms.bridge.debug

import android.content.Context
import android.os.Process
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AndroidLogCaptureService(private val context: Context) : LogCaptureService {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    override val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    override val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ringBuffer = ArrayDeque<LogEntry>(LogCaptureService.DEFAULT_MAX_ENTRIES)
    private var captureJob: Job? = null
    private var process: java.lang.Process? = null
    private var fileWriter: BufferedWriter? = null
    private var nextEntryId = 0L
    private var maxEntries = LogCaptureService.DEFAULT_MAX_ENTRIES

    override fun startCapture() {
        if (_isCapturing.value) return

        val logDir = File(context.filesDir, LOG_DIR_NAME)
        logDir.mkdirs()
        rotateFiles(logDir)
        val sessionFile = File(logDir, "session_${System.currentTimeMillis()}.log")

        try {
            val pid = Process.myPid()
            val proc = Runtime.getRuntime().exec(arrayOf("logcat", "-v", "threadtime", "--pid=$pid"))
            process = proc
            fileWriter = BufferedWriter(FileWriter(sessionFile, true))
            _isCapturing.value = true

            captureJob = scope.launch {
                try {
                    val reader: BufferedReader = proc.inputStream.bufferedReader()
                    var lastFlush = System.currentTimeMillis()

                    reader.useLines { lines ->
                        for (line in lines) {
                            if (!_isCapturing.value) break

                            fileWriter?.appendLine(line)

                            val now = System.currentTimeMillis()
                            val entry = parseThreadTimeLine(
                                line = line,
                                entryId = allocateEntryId(),
                                nowEpochMillis = now,
                            ) ?: continue

                            ringBuffer.addLast(entry)
                            trimRingBuffer()

                            if (now - lastFlush >= LogCaptureService.FLUSH_INTERVAL_MS) {
                                _logs.value = ringBuffer.toList().asReversed()
                                fileWriter?.flush()
                                lastFlush = now
                            }
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    _isCapturing.value = false
                    closeResources()
                    _logs.value = ringBuffer.toList().asReversed()
                }
            }
        } catch (_: Exception) {
            _isCapturing.value = false
            closeResources()
        }
    }

    override fun stopCapture() {
        if (!_isCapturing.value) return
        _isCapturing.value = false
        captureJob?.cancel()
        captureJob = null
        closeResources()
        _logs.value = ringBuffer.toList().asReversed()
    }

    override fun clearLogs() {
        ringBuffer.clear()
        _logs.value = emptyList()
    }

    override fun updateMaxEntries(maxEntries: Int) {
        this.maxEntries = maxEntries.coerceAtLeast(1)
        trimRingBuffer()
        _logs.value = ringBuffer.toList().asReversed()
    }

    override suspend fun latestLogFilePath(): String? {
        val logDir = File(context.filesDir, LOG_DIR_NAME)
        return logDir.listFiles()
            ?.filter { it.isFile }
            ?.maxByOrNull { it.lastModified() }
            ?.absolutePath
    }

    private fun closeResources() {
        try {
            process?.destroy()
        } catch (_: Exception) {
        }
        process = null

        try {
            fileWriter?.close()
        } catch (_: Exception) {
        }
        fileWriter = null
    }

    private fun rotateFiles(logDir: File) {
        val files = logDir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: return
        if (files.size >= MAX_SESSION_FILES) {
            files.drop(MAX_SESSION_FILES - 1).forEach { it.delete() }
        }
    }

    private fun trimRingBuffer() {
        while (ringBuffer.size > maxEntries) {
            ringBuffer.removeFirst()
        }
    }

    private fun allocateEntryId(): Long = nextEntryId++

    companion object {
        const val LOG_DIR_NAME = "debug_logs"
        private const val MAX_SESSION_FILES = 5
        private const val FUTURE_TIMESTAMP_TOLERANCE_MS = 24 * 60 * 60 * 1000L

        private val THREADTIME_REGEX = Regex(
            """^(\d{2}-\d{2})\s+(\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEF])\s+(.+?)\s*:\s*(.*)$""",
        )

        internal fun parseThreadTimeLine(
            line: String,
            entryId: Long,
            nowEpochMillis: Long = System.currentTimeMillis(),
            timeZone: TimeZone = TimeZone.getDefault(),
        ): LogEntry? {
            val match = THREADTIME_REGEX.matchEntire(line.trim()) ?: return null
            val groups = match.groupValues
            val level = when (groups[5]) {
                "D" -> LogLevel.DEBUG
                "I" -> LogLevel.INFO
                "W" -> LogLevel.WARN
                "E", "F" -> LogLevel.ERROR
                else -> LogLevel.DEBUG
            }

            return LogEntry(
                entryId = entryId,
                timestamp = parseThreadTimestamp(
                    monthDay = groups[1],
                    time = groups[2],
                    nowEpochMillis = nowEpochMillis,
                    timeZone = timeZone,
                ),
                level = level,
                tag = groups[6].trim(),
                message = groups[7],
                pid = groups[3].toIntOrNull() ?: 0,
                tid = groups[4].toIntOrNull() ?: 0,
            )
        }

        internal fun parseThreadTimestamp(
            monthDay: String,
            time: String,
            nowEpochMillis: Long = System.currentTimeMillis(),
            timeZone: TimeZone = TimeZone.getDefault(),
        ): Long = runCatching {
            val currentYear = Calendar.getInstance(timeZone).apply {
                timeInMillis = nowEpochMillis
            }.get(Calendar.YEAR)

            val parsedTimestamp = buildEpochMillis(currentYear, monthDay, time, timeZone)
            if (parsedTimestamp > nowEpochMillis + FUTURE_TIMESTAMP_TOLERANCE_MS) {
                buildEpochMillis(currentYear - 1, monthDay, time, timeZone)
            } else {
                parsedTimestamp
            }
        }.getOrElse { nowEpochMillis }

        private fun buildEpochMillis(year: Int, monthDay: String, time: String, timeZone: TimeZone): Long {
            val dateParts = monthDay.split('-')
            val timeParts = time.split(':', '.')
            require(dateParts.size == 2) { "Invalid month-day value: $monthDay" }
            require(timeParts.size == 4) { "Invalid time value: $time" }

            return Calendar.getInstance(timeZone).apply {
                isLenient = false
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, dateParts[0].toInt() - 1)
                set(Calendar.DAY_OF_MONTH, dateParts[1].toInt())
                set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
                set(Calendar.MINUTE, timeParts[1].toInt())
                set(Calendar.SECOND, timeParts[2].toInt())
                set(Calendar.MILLISECOND, timeParts[3].toInt())
            }.timeInMillis
        }
    }
}
