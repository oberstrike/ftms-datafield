package de.ma.ftms.bridge.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

val LocalStrings = staticCompositionLocalOf<Strings> { EnStrings }

fun resolveStrings(language: AppLanguage, localeTag: String = Locale.getDefault().toLanguageTag()): Strings =
    when (language) {
        AppLanguage.ENGLISH -> EnStrings
        AppLanguage.GERMAN -> DeStrings
        AppLanguage.SYSTEM_DEFAULT -> if (localeTag.primaryLanguage().equals("de", ignoreCase = true)) {
            DeStrings
        } else {
            EnStrings
        }
    }

private fun String.primaryLanguage(): String = substringBefore('-').substringBefore('_')
