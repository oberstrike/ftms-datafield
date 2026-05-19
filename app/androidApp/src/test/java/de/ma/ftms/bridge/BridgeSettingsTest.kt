package de.ma.ftms.bridge

import de.ma.ftms.bridge.i18n.AppLanguage
import de.ma.ftms.bridge.runtime.BridgeSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BridgeSettingsTest {
    @Test
    fun usesConservativeDefaults() {
        val settings = BridgeSettings()

        assertTrue(settings.sendToAllInstalledVariants)
        assertTrue(settings.autoReconnect)
        assertEquals(15, settings.packetTimeoutSeconds)
        assertEquals(1_000L, settings.sendIntervalMillis)
        assertEquals(5_000, settings.maxLogEntries)
        assertTrue(!settings.debugLoggingEnabled)
        assertEquals(AppLanguage.SYSTEM_DEFAULT, settings.language)
    }

    @Test
    fun exposesPacketTimeoutMillis() {
        assertEquals(30_000L, BridgeSettings(packetTimeoutSeconds = 30).packetTimeoutMillis)
    }
}
