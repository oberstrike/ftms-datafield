package de.ma.ftms.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FootPodTest {
    @Test
    fun whatAClientSwitchedOnSurvivesBeingStored() {
        for (measurement in listOf(false, true)) {
            for (controlPoint in listOf(false, true)) {
                val state = RememberedSubscriptions(measurement, controlPoint)

                assertEquals(state, RememberedSubscriptions.decode(state.encode()))
            }
        }
    }

    @Test
    fun aMissingOrDamagedEntryCountsAsNotRemembered() {
        assertNull(RememberedSubscriptions.decode(null))
        assertNull(RememberedSubscriptions.decode(""))
        assertNull(RememberedSubscriptions.decode("1"))
        assertNull(RememberedSubscriptions.decode("12"))
        assertNull(RememberedSubscriptions.decode("101"))
    }
}
