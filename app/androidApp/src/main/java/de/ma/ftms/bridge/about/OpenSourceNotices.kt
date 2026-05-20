package de.ma.ftms.bridge.about

import de.ma.ftms.bridge.BuildConfig

internal data class OpenSourceLibrary(
    val name: String,
    val version: String,
    val license: String,
    val url: String,
)

internal data class ThirdPartySdk(
    val name: String,
    val version: String,
    val url: String,
)

internal const val APACHE_2_LICENSE = "Apache License 2.0"

internal fun appVersionLabel(versionName: String, versionCode: Int): String =
    "$versionName ($versionCode)"

internal val openSourceLibraries: List<OpenSourceLibrary> = listOf(
    OpenSourceLibrary(
        name = "Kotlin Standard Library",
        version = BuildConfig.LIBRARY_VERSION_KOTLIN,
        license = APACHE_2_LICENSE,
        url = "https://github.com/JetBrains/kotlin",
    ),
    OpenSourceLibrary(
        name = "AndroidX Activity Compose",
        version = BuildConfig.LIBRARY_VERSION_ANDROIDX_ACTIVITY,
        license = APACHE_2_LICENSE,
        url = "https://developer.android.com/jetpack/androidx/releases/activity",
    ),
    OpenSourceLibrary(
        name = "AndroidX Core KTX",
        version = BuildConfig.LIBRARY_VERSION_ANDROIDX_CORE,
        license = APACHE_2_LICENSE,
        url = "https://developer.android.com/jetpack/androidx/releases/core",
    ),
    OpenSourceLibrary(
        name = "AndroidX Lifecycle Runtime Compose",
        version = BuildConfig.LIBRARY_VERSION_ANDROIDX_LIFECYCLE,
        license = APACHE_2_LICENSE,
        url = "https://developer.android.com/jetpack/androidx/releases/lifecycle",
    ),
    OpenSourceLibrary(
        name = "AndroidX Lifecycle ViewModel Compose",
        version = BuildConfig.LIBRARY_VERSION_ANDROIDX_LIFECYCLE,
        license = APACHE_2_LICENSE,
        url = "https://developer.android.com/jetpack/androidx/releases/lifecycle",
    ),
    OpenSourceLibrary(
        name = "Jetpack Compose Foundation",
        version = BuildConfig.LIBRARY_VERSION_COMPOSE,
        license = APACHE_2_LICENSE,
        url = "https://developer.android.com/jetpack/androidx/releases/compose-foundation",
    ),
    OpenSourceLibrary(
        name = "Jetpack Compose UI",
        version = BuildConfig.LIBRARY_VERSION_COMPOSE,
        license = APACHE_2_LICENSE,
        url = "https://developer.android.com/jetpack/androidx/releases/compose-ui",
    ),
    OpenSourceLibrary(
        name = "Jetpack Compose UI Tooling Preview",
        version = BuildConfig.LIBRARY_VERSION_COMPOSE,
        license = APACHE_2_LICENSE,
        url = "https://developer.android.com/jetpack/androidx/releases/compose-ui",
    ),
    OpenSourceLibrary(
        name = "Jetpack Compose Material 3",
        version = BuildConfig.LIBRARY_VERSION_COMPOSE_MATERIAL3,
        license = APACHE_2_LICENSE,
        url = "https://developer.android.com/jetpack/androidx/releases/compose-material3",
    ),
    OpenSourceLibrary(
        name = "Decompose",
        version = BuildConfig.LIBRARY_VERSION_DECOMPOSE,
        license = APACHE_2_LICENSE,
        url = "https://github.com/arkivanov/Decompose",
    ),
    OpenSourceLibrary(
        name = "Decompose Extensions Compose",
        version = BuildConfig.LIBRARY_VERSION_DECOMPOSE,
        license = APACHE_2_LICENSE,
        url = "https://github.com/arkivanov/Decompose",
    ),
    OpenSourceLibrary(
        name = "Kotlinx Coroutines",
        version = BuildConfig.LIBRARY_VERSION_KOTLINX_COROUTINES,
        license = APACHE_2_LICENSE,
        url = "https://github.com/Kotlin/kotlinx.coroutines",
    ),
    OpenSourceLibrary(
        name = "SQLDelight Android Driver",
        version = BuildConfig.LIBRARY_VERSION_SQLDELIGHT,
        license = APACHE_2_LICENSE,
        url = "https://github.com/cashapp/sqldelight",
    ),
    OpenSourceLibrary(
        name = "SQLDelight Coroutines Extensions",
        version = BuildConfig.LIBRARY_VERSION_SQLDELIGHT,
        license = APACHE_2_LICENSE,
        url = "https://github.com/cashapp/sqldelight",
    ),
    OpenSourceLibrary(
        name = "Vico Compose",
        version = BuildConfig.LIBRARY_VERSION_VICO,
        license = APACHE_2_LICENSE,
        url = "https://github.com/patrykandpatrick/vico",
    ),
    OpenSourceLibrary(
        name = "Vico Compose Material 3",
        version = BuildConfig.LIBRARY_VERSION_VICO,
        license = APACHE_2_LICENSE,
        url = "https://github.com/patrykandpatrick/vico",
    ),
)

internal val thirdPartySdks: List<ThirdPartySdk> = listOf(
    ThirdPartySdk(
        name = "Garmin Connect IQ Companion App SDK",
        version = BuildConfig.LIBRARY_VERSION_GARMIN_CONNECT_IQ,
        url = "https://developer.garmin.com/connect-iq/",
    ),
)
