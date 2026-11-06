package com.robothub

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID

private const val TAG = "BtStmLink"
private const val RECONNECT_DELAY_MS = 2000L
private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

@SuppressLint("MissingPermission")
class BluetoothStmLink(
    private val adapter: BluetoothAdapter?,
    private val deviceName: String = "HC-05",
    private val onLine: (String) -> Unit,
) : StmLink {

    private val lock = Any()
    private var socket: BluetoothSocket? = null
    private var connectingSocket: BluetoothSocket? = null
    private var writer: BufferedWriter? = null
    private var connected = false

    @Volatile
    private var running = false
    private var workerThread: Thread? = null

    fun start() {
        synchronized(lock) {
            if (running) return
            running = true
        }
        workerThread = Thread({ connectLoop() }, "bt-stm-loop").apply { start() }
    }

    fun stop() {
        synchronized(lock) {
            running = false
            closeConnectionLocked()
        }
        workerThread?.interrupt()
    }

    override fun send(c: Char) {
        synchronized(lock) {
            val w = writer
            if ((!connected) || (w == null)) {
                Log.w(TAG, "Belum terhubung")
                return
            }
            try {
                w.write("$c\n")
                w.flush()
            } catch (e: IOException) {
                Log.w(TAG, "Gagal kirim '$c': ${e.message}")
                closeConnectionLocked()
            }
        }
    }

    private fun connectLoop() {
        while (running) {
            val isConnected = synchronized(lock) { connected }
            if (!isConnected) {
                tryConnect()
            }
            try {
                Thread.sleep(RECONNECT_DELAY_MS)
            } catch (_: InterruptedException) {
                break
            }
        }
    }

    private fun tryConnect() {
        if (!running) return

        if ((adapter == null) || (!adapter.isEnabled)) {
            Log.w(TAG, "BluetoothAdapter tidak tersedia atau tidak aktif")
            return
        }

        val bonded = adapter.bondedDevices
        val device: BluetoothDevice? = bonded?.find { it.name == deviceName }
        if (device == null) {
            Log.w(TAG, "Perangkat $deviceName tidak ditemukan di daftar paired")
            return
        }

        var s: BluetoothSocket? = null
        try {
            adapter.cancelDiscovery()
            s = device.createRfcommSocketToServiceRecord(SPP_UUID)
            synchronized(lock) {
                if (!running) {
                    try { s.close() } catch (_: Exception) {}
                    return
                }
                connectingSocket = s
            }
            s.connect()
        } catch (e: Exception) {
            synchronized(lock) {
                connectingSocket = null
                try { s?.close() } catch (_: Exception) {}
            }
            if (running) {
                Log.w(TAG, "Gagal terhubung ke $deviceName: ${e.message}")
            }
            return
        }

        val currentSocket: BluetoothSocket
        val currentWriter: BufferedWriter
        synchronized(lock) {
            connectingSocket = null
            if (!running) {
                try { s.close() } catch (_: Exception) {}
                return
            }
            currentSocket = s
            currentWriter = BufferedWriter(OutputStreamWriter(s.outputStream, Charsets.UTF_8))
            socket = currentSocket
            writer = currentWriter
            connected = true
            Log.i(TAG, "Terhubung ke $deviceName")
        }

        val reader = BufferedReader(InputStreamReader(currentSocket.inputStream, Charsets.UTF_8))
        Thread(
            {
                try {
                    while (running) {
                        val line = reader.readLine() ?: break
                        onLine(line.trim())
                    }
                } catch (e: Exception) {
                    if (running) {
                        Log.w(TAG, "Koneksi input terputus: ${e.message}")
                    }
                } finally {
                    synchronized(lock) {
                        closeConnectionLocked()
                    }
                }
            },
            "bt-stm-reader",
        ).start()
    }

    private fun closeConnectionLocked() {
        try { connectingSocket?.close() } catch (_: Exception) {}
        connectingSocket = null
        try { writer?.close() } catch (_: Exception) {}
        writer = null
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        connected = false
    }
}
