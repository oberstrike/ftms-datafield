package de.ma.ftms.bridge.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

class AppLanguageTest {
    @Test
    fun parsesPersistedLanguageNames() {
        assertEquals(AppLanguage.ENGLISH, "ENGLISH".toAppLanguage())
        assertEquals(AppLanguage.GERMAN, "GERMAN".toAppLanguage())
        assertEquals(AppLanguage.SYSTEM_DEFAULT, "SYSTEM_DEFAULT".toAppLanguage())
    }

    @Test
    fun fallsBackToSystemDefaultForMissingOrInvalidValues() {
        assertEquals(AppLanguage.SYSTEM_DEFAULT, null.toAppLanguage())
        assertEquals(AppLanguage.SYSTEM_DEFAULT, "SPANISH".toAppLanguage())
    }

    @Test
    fun formatsDisplayLabelsWithLocalizedSystemDefault() {
        assertEquals("System default", AppLanguage.SYSTEM_DEFAULT.displayLabel(EnStrings))
        assertEquals("Systemstandard", AppLanguage.SYSTEM_DEFAULT.displayLabel(DeStrings))
        assertEquals("English", AppLanguage.ENGLISH.displayLabel(DeStrings))
        assertEquals("Deutsch", AppLanguage.GERMAN.displayLabel(EnStrings))
    }
}
