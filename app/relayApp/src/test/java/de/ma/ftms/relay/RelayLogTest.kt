package de.ma.ftms.relay

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RelayLogTest {
    private val directory: File = Files.createTempDirectory("relay-log").toFile()

    @AfterTest
    fun removeFiles() {
        directory.deleteRecursively()
    }

    @Test
    fun appendsOneLinePerEventWithItsTime() {
        val log = RelayLog(File(directory, "logs"))

        log.append(0L, "relay started")
        log.append(1_000L, "source subscribed")

        val lines = File(directory, "logs/${RelayLog.CURRENT_NAME}").readLines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].endsWith(" relay started"), lines[0])
        assertTrue(lines[1].endsWith(" source subscribed"), lines[1])
    }

    @Test
    fun aFullFileBecomesThePreviousOneAndANewFileBegins() {
        val log = RelayLog(directory, maxBytes = 60L)

        log.append(0L, "first")
        log.append(0L, "second, which fills the file")
        log.append(0L, "third")
        val current = File(directory, RelayLog.CURRENT_NAME)
        val previous = File(directory, RelayLog.PREVIOUS_NAME)

        assertEquals(1, current.readLines().size)
        assertTrue(current.readText().contains("third"))
        assertEquals(2, previous.readLines().size)

        // Only one previous file is kept.
        log.append(0L, "fourth, which fills the file as well, and more than that")
        log.append(0L, "fifth")
        assertFalse(previous.readText().contains("first"))
        assertTrue(previous.readText().contains("fourth"))
    }
}
