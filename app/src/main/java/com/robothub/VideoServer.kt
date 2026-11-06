package com.robothub

import android.util.Log
import java.io.DataOutputStream
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

private const val TAG = "VideoServer"

class VideoServer(private val port: Int = Protocol.VIDEO_PORT) {

    private val lock = Any()
    private var client: Socket? = null
    private var out: DataOutputStream? = null
    @Volatile private var running = false
    private var server: ServerSocket? = null

    fun start() {
        if (running) return
        running = true
        Thread({ acceptLoop() }, "video-accept").start()
    }

    fun stop() {
        running = false
        synchronized(lock) { closeClientLocked() }
        try { server?.close() } catch (_: Exception) { }
    }

    // Dipanggil dari analyzer. Frame dikirim hanya kalau ada driver yang terhubung.
    fun sendFrame(jpeg: ByteArray) {
        if (jpeg.isEmpty() || jpeg.size > Protocol.MAX_FRAME_BYTES) return
        synchronized(lock) {
            val o = out ?: return
            try {
                o.writeInt(jpeg.size)     // big-endian, sesuai PROTOKOL.md
                o.write(jpeg)
                o.flush()
            } catch (e: Exception) {
                Log.w(TAG, "Gagal kirim frame: ${e.message}")
                closeClientLocked()
            }
        }
    }

    private fun acceptLoop() {
        try {
            val ss = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(port))
            }
            server = ss
            Log.i(TAG, "Video server di port $port")
            while (running) {
                val s = ss.accept()
                synchronized(lock) {
                    closeClientLocked()   // driver lama digantikan
                    s.tcpNoDelay = true
                    client = s
                    out = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
                }
            }
        } catch (e: SocketException) {
            Log.i(TAG, "Video server berhenti: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Accept gagal", e)
        }
    }

    private fun closeClientLocked() {
        try { client?.close() } catch (_: Exception) { }
        client = null
        out = null
    }
}