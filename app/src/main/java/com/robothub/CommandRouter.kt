package com.robothub

sealed interface Action {
    object None : Action
    object Ignore : Action
    data class ToStm(val char: Char) : Action

}

class CommandRouter(private val state: HubState) {
    fun route(c: Char): Action {
        return when (c) {
            Protocol.CMD_MODE_AUTO -> {
                state.enterAuto()
                Action.ToStm(c)
            }

            Protocol.CMD_MODE_MANUAL -> {
                state.enterManual()
                Action.ToStm(c)
            }

            Protocol.CMD_ARM_UP,
            Protocol.CMD_ARM_DOWN,
            Protocol.CMD_ELBOW_UP,
            Protocol.CMD_ELBOW_DOWN,
            Protocol.CMD_GRIP_OPEN,
            Protocol.CMD_GRIP_CLOSE,
            -> {
                if (state.allowsManualCommand()) Action.ToStm(c) else Action.Ignore
            }

            else -> {
                if (state.allowsManualCommand()) {
                    Action.ToStm(c)
                } else {
                    Action.None
                }
            }
        }
    }
}
