package de.ma.ftms.bridge.about

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenSourceNoticesTest {
    @Test
    fun formatsAppVersionWithCode() {
        assertEquals("1.2.3 (123)", appVersionLabel("1.2.3", 123))
    }

    @Test
    fun listsDirectProductionOpenSourceLibraries() {
        val names = openSourceLibraries.map { it.name }

        assertEquals(
            listOf(
                "Kotlin Standard Library",
                "AndroidX Activity Compose",
                "AndroidX Core KTX",
                "AndroidX Lifecycle Runtime Compose",
                "AndroidX Lifecycle ViewModel Compose",
                "Jetpack Compose Foundation",
                "Jetpack Compose UI",
                "Jetpack Compose UI Tooling Preview",
                "Jetpack Compose Material 3",
                "Decompose",
                "Decompose Extensions Compose",
                "Kotlinx Coroutines",
                "SQLDelight Android Driver",
                "SQLDelight Coroutines Extensions",
                "Vico Compose",
                "Vico Compose Material 3",
            ),
            names,
        )
        assertTrue(openSourceLibraries.all { it.version.isNotBlank() })
        assertTrue(openSourceLibraries.all { it.license == APACHE_2_LICENSE })
        assertFalse(names.any { it.contains("Garmin", ignoreCase = true) })
        assertFalse(names.any { it.contains("Test", ignoreCase = true) })
    }

    @Test
    fun keepsGarminSdkSeparateFromOpenSourceLibraries() {
        assertEquals(listOf("Garmin Connect IQ Companion App SDK"), thirdPartySdks.map { it.name })
        assertTrue(thirdPartySdks.single().version.isNotBlank())
    }
}
