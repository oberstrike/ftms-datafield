package de.ma.ftms.bridge.debug

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AndroidLogCaptureServiceTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun parseThreadTimeLineParsesExpectedFields() {
        val now = epochMillis(2026, Calendar.MARCH, 8, 12, 0, 0, 0)
        val line = "03-08 10:58:28.354 5976 5976 I BridgeRuntime: Bridge runtime initialized"

        val entry = AndroidLogCaptureService.parseThreadTimeLine(
            line = line,
            entryId = 42L,
            nowEpochMillis = now,
            timeZone = utc,
        )

        assertNotNull(entry)
        assertEquals(42L, entry.entryId)
        assertEquals(epochMillis(2026, Calendar.MARCH, 8, 10, 58, 28, 354), entry.timestamp)
        assertEquals(LogLevel.INFO, entry.level)
        assertEquals(5976, entry.pid)
        assertEquals(5976, entry.tid)
        assertEquals("BridgeRuntime", entry.tag)
        assertEquals("Bridge runtime initialized", entry.message)
    }

    @Test
    fun parseThreadTimestampRollsBackOneYearWhenCurrentYearWouldBeFarInFuture() {
        val now = epochMillis(2026, Calendar.JANUARY, 1, 0, 0, 0, 0)

        val timestamp = AndroidLogCaptureService.parseThreadTimestamp(
            monthDay = "12-31",
            time = "23:59:59.000",
            nowEpochMillis = now,
            timeZone = utc,
        )

        assertEquals(epochMillis(2025, Calendar.DECEMBER, 31, 23, 59, 59, 0), timestamp)
    }

    @Test
    fun parseThreadTimeLineReturnsNullForMalformedLine() {
        val entry = AndroidLogCaptureService.parseThreadTimeLine(
            line = "not a logcat threadtime line",
            entryId = 1L,
            nowEpochMillis = epochMillis(2026, Calendar.MARCH, 8, 12, 0, 0, 0),
            timeZone = utc,
        )

        assertNull(entry)
    }

    @Test
    fun parseThreadTimestampFallsBackToNowForInvalidDateContent() {
        val now = epochMillis(2026, Calendar.MARCH, 8, 12, 0, 0, 0)

        val entry = AndroidLogCaptureService.parseThreadTimeLine(
            line = "13-40 99:99:99.999 5976 42 E FtmsBle: invalid timestamp",
            entryId = 7L,
            nowEpochMillis = now,
            timeZone = utc,
        )

        assertNotNull(entry)
        assertEquals(7L, entry.entryId)
        assertEquals(now, entry.timestamp)
        assertEquals(LogLevel.ERROR, entry.level)
        assertEquals("invalid timestamp", entry.message)
    }

    private fun epochMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
        millisecond: Int,
    ): Long = GregorianCalendar(utc).apply {
        isLenient = false
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, day)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, second)
        set(Calendar.MILLISECOND, millisecond)
    }.timeInMillis
}
