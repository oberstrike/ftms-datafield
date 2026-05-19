package de.ma.ftms.bridge.i18n

enum class AppLanguage(val code: String, val nativeName: String) {
    SYSTEM_DEFAULT("", ""),
    ENGLISH("en", "English"),
    GERMAN("de", "Deutsch"),
}

fun String?.toAppLanguage(): AppLanguage = when {
    this == null -> AppLanguage.SYSTEM_DEFAULT
    else -> runCatching { AppLanguage.valueOf(this) }.getOrDefault(AppLanguage.SYSTEM_DEFAULT)
}

fun AppLanguage.displayLabel(strings: Strings): String =
    when (this) {
        AppLanguage.SYSTEM_DEFAULT -> strings.settings.languageSystemDefault
        else -> nativeName
    }
