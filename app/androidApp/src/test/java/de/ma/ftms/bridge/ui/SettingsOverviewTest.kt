package de.ma.ftms.bridge.ui

import de.ma.ftms.bridge.i18n.DeStrings
import de.ma.ftms.bridge.i18n.EnStrings
import de.ma.ftms.bridge.navigation.SettingsConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsOverviewTest {
    private val listed = SettingsGroup.entries.flatMap { it.pages }

    @Test
    fun everyPageBelowTheOverviewIsListedOnce() {
        val pages = listOf(
            SettingsConfig.Bridge,
            SettingsConfig.Power,
            SettingsConfig.IndoorCycling,
            SettingsConfig.Treadmill,
            SettingsConfig.Sources,
            SettingsConfig.Dashboard,
            SettingsConfig.History,
            SettingsConfig.Timing,
            SettingsConfig.Language,
            SettingsConfig.Debug,
            SettingsConfig.Logs,
            SettingsConfig.RememberedDevices,
            SettingsConfig.OpenSource,
        )

        assertEquals(pages.toSet(), listed.toSet())
        assertEquals(listed.size, listed.toSet().size)
    }

    @Test
    fun theOverviewDoesNotListItself() {
        assertFalse(SettingsConfig.Overview in listed)
    }

    @Test
    fun everyPageHasItsOwnNameInBothLanguages() {
        listOf(DeStrings, EnStrings).forEach { strings ->
            val names = listed.map { it.title(strings) }

            assertTrue(names.none { it.isBlank() })
            assertEquals(names.size, names.toSet().size)
        }
    }
}
