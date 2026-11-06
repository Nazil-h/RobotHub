package com.robothub

enum class Mode { MANUAL, AUTO }

class HubState {
    var mode: Mode = Mode.MANUAL
        private set

    // Perintah manual diabaikan saat mode auto
    fun allowsManualCommand(): Boolean = mode == Mode.MANUAL

    fun enterAuto() {
        mode = Mode.AUTO
    }

    fun enterManual() {
        mode = Mode.MANUAL
    }
}