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

/**
 * Formats with the locale of the displayed language instead of the JVM default, so numbers
 * match the UI language (decimal separator, digits) regardless of the system locale.
 */
fun Strings.fmt(pattern: String, vararg args: Any?): String = String.format(locale, pattern, *args)

/** Locale-neutral formatting for clock-style values (mm:ss) and machine-readable text. */
fun formatNeutral(pattern: String, vararg args: Any?): String = String.format(Locale.ROOT, pattern, *args)
