package com.robothub

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow

class HubViewModel(app: Application) : AndroidViewModel(app) {

    private var server: ControlServer
    private val bt: BluetoothStmLink
    private val video: VideoServer

    val status: StateFlow<HubStatus>

    init {
        val btManager = app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bt = BluetoothStmLink(
            adapter = btManager?.adapter,
            onLine = { line -> server.onStmLine(line) },
        )
        server = ControlServer(bt)
        video = VideoServer()
        status = server.status
    }

    fun start() {
        server.start(NetUtil.localIpv4())
        video.start()
        bt.start()
    }

    fun onArucoStable(id: Int) {
        server.sendAruco(id)
    }

    fun onJpegFrame(jpeg: ByteArray) {
        video.sendFrame(jpeg)
    }

    override fun onCleared() {
        bt.stop()
        server.stop()
        video.stop()
    }
}
