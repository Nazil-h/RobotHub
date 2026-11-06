package com.robothub

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandRouterTest {

    @Test
    fun autoCommandSwitchesModeToAutoAndReturnsToStm() {
        val state = HubState()
        val router = CommandRouter(state)

        val action = router.route(Protocol.CMD_MODE_AUTO)

        assertEquals(Mode.AUTO, state.mode)
        assertEquals(Action.ToStm(Protocol.CMD_MODE_AUTO), action)
    }

    @Test
    fun manualCommandSwitchesModeToManualAndReturnsToStm() {
        val state = HubState()
        state.enterAuto()
        val router = CommandRouter(state)

        val action = router.route(Protocol.CMD_MODE_MANUAL)

        assertEquals(Mode.MANUAL, state.mode)
        assertEquals(Action.ToStm(Protocol.CMD_MODE_MANUAL), action)
    }

    @Test
    fun movementCommandAllowedInManualMode() {
        val state = HubState()
        val router = CommandRouter(state)

        val action = router.route(Protocol.CMD_FORWARD)

        assertEquals(Action.ToStm(Protocol.CMD_FORWARD), action)
    }

    @Test
    fun movementCommandIgnoredInAutoMode() {
        val state = HubState()
        state.enterAuto()
        val router = CommandRouter(state)

        val action = router.route(Protocol.CMD_FORWARD)

        assertEquals(Action.None, action)
    }
    @Test
    fun gripperForwardedInManual() {
        val r = CommandRouter(HubState())
        assertEquals(Action.ToStm('G'), r.route('G'))
    }

    @Test
    fun gripperIgnoredInAuto() {
        val state = HubState()
        state.enterAuto()
        assertEquals(Action.Ignore, CommandRouter(state).route('O'))
    }

    @Test
    fun elbowForwardedInManual() {
        val r = CommandRouter(HubState())
        assertEquals(Action.ToStm('E'), r.route('E'))
        assertEquals(Action.ToStm('Q'), r.route('Q'))
    }

    @Test
    fun elbowIgnoredInAuto() {
        val state = HubState()
        state.enterAuto()
        val r = CommandRouter(state)
        assertEquals(Action.Ignore, r.route('E'))
        assertEquals(Action.Ignore, r.route('Q'))
    }
}
