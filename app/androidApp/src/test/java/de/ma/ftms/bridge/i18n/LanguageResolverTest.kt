package de.ma.ftms.bridge.i18n

import kotlin.test.Test
import kotlin.test.assertSame

class LanguageResolverTest {
    @Test
    fun explicitLanguageOverridesSystemLocale() {
        assertSame(EnStrings, resolveStrings(AppLanguage.ENGLISH, localeTag = "de-DE"))
        assertSame(DeStrings, resolveStrings(AppLanguage.GERMAN, localeTag = "en-US"))
    }

    @Test
    fun systemDefaultUsesGermanForGermanLocales() {
        assertSame(DeStrings, resolveStrings(AppLanguage.SYSTEM_DEFAULT, localeTag = "de-DE"))
        assertSame(DeStrings, resolveStrings(AppLanguage.SYSTEM_DEFAULT, localeTag = "de_AT"))
    }

    @Test
    fun systemDefaultFallsBackToEnglishForOtherLocales() {
        assertSame(EnStrings, resolveStrings(AppLanguage.SYSTEM_DEFAULT, localeTag = "en-US"))
        assertSame(EnStrings, resolveStrings(AppLanguage.SYSTEM_DEFAULT, localeTag = "fr-FR"))
    }
}
