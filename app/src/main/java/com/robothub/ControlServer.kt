package com.robothub

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

private const val TAG = "ControlServer"

data class HubStatus(
    val ip: String = "",
    val port: Int = Protocol.CONTROL_PORT,
    val listening: Boolean = false,
    val driverConnected: Boolean = false,
    val mode: Mode = Mode.MANUAL,
    val lastCommand: Char? = null,
    val linkLost: Boolean = false,
    val lastAruco: Int? = null
)

class ControlServer(
    private val stm: StmLink,
    private val port: Int = Protocol.CONTROL_PORT
) {
    private val state = HubState()
    private val router = CommandRouter(state)
    private val lock = Any()

    private val _status = MutableStateFlow(HubStatus(port = port))
    val status: StateFlow<HubStatus> = _status.asStateFlow()

    @Volatile private var running = false
    private var server: ServerSocket? = null

    // Semua di bawah ini hanya diakses di dalam synchronized(lock)
    private var client: Socket? = null
    private var clientOut: OutputStream? = null
    private var lastDriverMs = 0L
    private var linkLostSent = false

    fun start(ip: String) {
        if (running) return
        running = true
        _status.value = _status.value.copy(ip = ip, listening = true)
        Thread({ acceptLoop() }, "control-accept").start()
        Thread({ watchdogLoop() }, "control-watchdog").start()
    }

    fun stop() {
        running = false
        synchronized(lock) { closeClientLocked() }
        try { server?.close() } catch (_: Exception) { }
        _status.value = _status.value.copy(listening = false, driverConnected = false)
    }

    // Dipanggil saat ada baris dari STM32. Nanti dihubungkan ke pembacaan HC-05.
    @Suppress("unused")
    fun onStmLine(line: String) {
        if (line.trim() != Protocol.STM_CENTER_REACHED.toString()) return
        synchronized(lock) {
            state.enterManual()
            writeToDriverLocked(Protocol.STM_CENTER_REACHED.toString())
            writeToDriverLocked("T:MANUAL")
            _status.value = _status.value.copy(mode = state.mode)
        }
    }
    fun sendAruco(id: Int) {
        synchronized(lock) {
            if (clientOut != null) writeToDriverLocked("I:$id")
        }
        _status.value = _status.value.copy(lastAruco = id)
    }
    private fun acceptLoop() {
        try {
            val ss = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(port))
            }
            server = ss
            Log.i(TAG, "Mendengarkan di port $port")
            while (running) {
                val s = ss.accept()
                Thread({ serveDriver(s) }, "control-driver").start()
            }
        } catch (e: SocketException) {
            Log.i(TAG, "Server berhenti: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Accept gagal", e)
        }
    }

    private fun serveDriver(s: Socket) {
        try {
            s.tcpNoDelay = true
            synchronized(lock) {
                closeClientLocked()            // driver lama digantikan
                client = s
                clientOut = s.getOutputStream()
                lastDriverMs = System.currentTimeMillis()
                linkLostSent = false
                stm.send(Protocol.CMD_STOP)    // setiap sesi dimulai dengan S
                state.enterManual()                 // sesi baru selalu mulai dari MANUAL, sesuai driver
                writeToDriverLocked("T:MANUAL")
                _status.value = _status.value.copy(driverConnected = true, linkLost = false)
            }

            val reader = BufferedReader(InputStreamReader(s.getInputStream()))
            while (running) {
                val line = reader.readLine() ?: break
                onDriverLine(s, line.trim())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Driver terputus: ${e.message}")
        } finally {
            onDriverGone(s)
        }
    }

    private fun onDriverLine(s: Socket, line: String) {
        val c = line.singleOrNull() ?: return
        synchronized(lock) {
            if (client !== s) return      // baris dari sesi lama
            lastDriverMs = System.currentTimeMillis()
            linkLostSent = false

            val action = router.route(c)
            when (c) {
                Protocol.CMD_MODE_AUTO -> writeToDriverLocked("T:AUTO")
                Protocol.CMD_MODE_MANUAL -> writeToDriverLocked("T:MANUAL")
            }
            if (action is Action.ToStm) stm.send(action.char)

            _status.value = _status.value.copy(
                lastCommand = c,
                mode = state.mode,
                linkLost = false
            )
        }
    }

    private fun onDriverGone(s: Socket) {
        synchronized(lock) {
            if (client !== s) return      // sesi lama, sudah digantikan
            // Manual: robot harus berhenti. Auto: robot tetap line following.
            if (state.mode == Mode.MANUAL) stm.send(Protocol.CMD_STOP)
            closeClientLocked()
            _status.value = _status.value.copy(driverConnected = false)
        }
    }

    private fun watchdogLoop() {
        var lastKeepalive = 0L
        while (running) {
            try {
                Thread.sleep(50)
            } catch (_: InterruptedException) {
                break
            }
            val now = System.currentTimeMillis()
            synchronized(lock) {
                if (clientOut == null) return@synchronized

                if (now - lastKeepalive >= Protocol.KEEPALIVE_TO_DRIVER_MS) {
                    writeToDriverLocked("T:OK")
                    lastKeepalive = now
                }

                // Watchdog hanya berlaku di mode manual
                if (state.mode == Mode.MANUAL &&
                    !linkLostSent &&
                    now - lastDriverMs > Protocol.DRIVER_SILENCE_TIMEOUT_MS
                ) {
                    stm.send(Protocol.CMD_STOP)
                    writeToDriverLocked("T:LINK_LOST")
                    linkLostSent = true
                    _status.value = _status.value.copy(linkLost = true)
                }
            }
        }
    }

    private fun writeToDriverLocked(msg: String) {
        try {
            clientOut?.apply {
                write("$msg\n".toByteArray())
                flush()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gagal kirim ke driver: ${e.message}")
        }
    }

    private fun closeClientLocked() {
        try { client?.close() } catch (_: Exception) { }
        client = null
        clientOut = null
    }

}