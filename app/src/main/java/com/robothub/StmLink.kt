package com.robothub

import android.util.Log

interface StmLink {
    fun send(c: Char)
}

// Stub sampai HC-05 tersambung. Hanya mencatat apa yang akan dikirim ke STM32.
class LogStmLink : StmLink {
    override fun send(c: Char) {
        Log.d("StmLink", "-> $c")
    }
}