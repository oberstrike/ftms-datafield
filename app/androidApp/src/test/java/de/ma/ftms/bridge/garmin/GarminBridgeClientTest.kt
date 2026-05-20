package de.ma.ftms.bridge.garmin

import de.ma.ftms.bridge.runtime.WATCH_APP_ID
import de.ma.ftms.bridge.runtime.WATCH_APP_IDS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GarminBridgeClientTest {
    @Test
    fun sendsToBaseAppWhenInstalledAppsAreUnknown() {
        assertEquals(listOf(WATCH_APP_ID), compatibleAppIdsForSend(null))
        assertEquals(listOf(WATCH_APP_ID), compatibleAppIdsForSend(emptySet()))
    }

    @Test
    fun sendsToInstalledCompatibleVariants() {
        val installedVariant = WATCH_APP_IDS.last()

        assertEquals(listOf(installedVariant), compatibleAppIdsForSend(setOf(installedVariant, "not-compatible")))
    }

    @Test
    fun keepsConfiguredAppOrderForMultipleInstalledVariants() {
        val installed = setOf(WATCH_APP_IDS[3], WATCH_APP_IDS[1], WATCH_APP_IDS[2])

        assertEquals(
            listOf(WATCH_APP_IDS[1], WATCH_APP_IDS[2], WATCH_APP_IDS[3]),
            compatibleAppIdsForSend(installed),
        )
    }

    @Test
    fun parsesGarminHeartRateMessage() {
        assertEquals(142, parseGarminHeartRateMessage(listOf(mapOf("type" to "garmin_hr", "hr" to 142))))
        assertEquals(143, parseGarminHeartRateMessage(listOf(mapOf("type" to "garmin_hr", "hr" to "143"))))
    }

    @Test
    fun ignoresUnknownOrInvalidGarminHeartRateMessages() {
        assertNull(parseGarminHeartRateMessage(listOf(mapOf("type" to "other", "hr" to 142))))
        assertNull(parseGarminHeartRateMessage(listOf(mapOf("type" to "garmin_hr", "hr" to 0))))
        assertNull(parseGarminHeartRateMessage(listOf(mapOf("type" to "garmin_hr", "hr" to 300))))
        assertNull(parseGarminHeartRateMessage(listOf("not-a-map")))
    }
}
