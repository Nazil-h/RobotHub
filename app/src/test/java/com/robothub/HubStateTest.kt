package com.robothub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HubStateTest {

    @Test
    fun startsInManualMode() {
        val state = HubState()
        assertEquals(Mode.MANUAL, state.mode)
        assertTrue(state.allowsManualCommand())
    }

    @Test
    fun autoModeBlocksManualCommands() {
        val state = HubState()
        state.enterAuto()
        assertEquals(Mode.AUTO, state.mode)
        assertFalse(state.allowsManualCommand())
    }

    @Test
    fun returningToManualAllowsCommandsAgain() {
        val state = HubState()
        state.enterAuto()
        state.enterManual()
        assertTrue(state.allowsManualCommand())
    }
}