package de.ma.ftms.bridge.debug

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogEntryFilteringTest {
    private val testLogs = listOf(
        LogEntry(1L, 1000L, LogLevel.DEBUG, "FtmsBle", "Scan result FS-BC11B7", pid = 1),
        LogEntry(2L, 2000L, LogLevel.INFO, "BridgeRuntime", "Bridge runtime initialized", pid = 1),
        LogEntry(3L, 3000L, LogLevel.WARN, "GarminCiq", "Cannot send", pid = 1),
        LogEntry(4L, 4000L, LogLevel.ERROR, "FtmsBle", "GATT services failed 133", pid = 1),
    )

    @Test
    fun filtersByMultipleLevels() {
        val filtered = testLogs.filter { it.level in setOf(LogLevel.WARN, LogLevel.ERROR) }

        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.level == LogLevel.WARN || it.level == LogLevel.ERROR })
    }

    @Test
    fun searchMatchesTagOrMessageCaseInsensitive() {
        val query = "gatt"
        val filtered = testLogs.filter {
            it.tag.contains(query, ignoreCase = true) || it.message.contains(query, ignoreCase = true)
        }

        assertEquals(1, filtered.size)
        assertEquals("GATT services failed 133", filtered.first().message)
    }
}
