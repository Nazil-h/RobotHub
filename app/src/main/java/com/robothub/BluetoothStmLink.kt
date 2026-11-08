package com.robothub

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import java.util.UUID

private const val TAG = "BtStmLink"
private const val RECONNECT_DELAY_MS = 3000L

// UUID standar modul BLE Serial (HM-10 / HC-05 LE / JDY-31)
private val UART_CHAR_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

@SuppressLint("MissingPermission")
class BluetoothStmLink(
    private val adapter: BluetoothAdapter?,
    private val deviceName: String = "HC-05",
    private val onLine: (String) -> Unit,
) : StmLink {

    private val lock = Any()
    private var gatt: BluetoothGatt? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var connected = false
    private var connecting = false
    private var scanning = false
    private val rxBuffer = StringBuilder()

    @Volatile
    private var running = false
    private var workerThread: Thread? = null

    private fun getAppContext(): Context? {
        return try {
            val activityThread = Class.forName("android.app.ActivityThread")
            activityThread.getMethod("currentApplication").invoke(null) as? Application
        } catch (_: Exception) {
            null
        }
    }

    fun start() {
        synchronized(lock) {
            if (running) return
            running = true
        }
        workerThread = Thread({ connectLoop() }, "bt-stm-ble-loop").apply { start() }
    }

    fun stop() {
        synchronized(lock) {
            running = false
            stopBleScan()
            closeConnectionLocked()
        }
        workerThread?.interrupt()
    }

    override fun send(c: Char) {
        synchronized(lock) {
            val currentGatt = gatt
            val characteristic = txCharacteristic
            if (!connected || currentGatt == null || characteristic == null) {
                Log.w(TAG, "Belum terhubung ke BLE")
                return
            }
            val payload = "$c\n".toByteArray(Charsets.UTF_8)
            val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                currentGatt.writeCharacteristic(
                    characteristic,
                    payload,
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                ) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @Suppress("DEPRECATION")
                characteristic.value = payload
                @Suppress("DEPRECATION")
                currentGatt.writeCharacteristic(characteristic)
            }
            if (!success) {
                Log.w(TAG, "Gagal kirim '$c' via BLE")
            }
        }
    }

    private fun connectLoop() {
        while (running) {
            val shouldAttempt = synchronized(lock) { !connected && !connecting }
            if (shouldAttempt) {
                tryConnect()
            }
            try {
                Thread.sleep(RECONNECT_DELAY_MS)
            } catch (_: InterruptedException) {
                break
            }
        }
    }

    private fun isTargetDevice(name: String?): Boolean {
        if (name == null) return false
        return name.equals(deviceName, ignoreCase = true) ||
                name.equals("$deviceName LE", ignoreCase = true) ||
                name.startsWith("HC-05", ignoreCase = true)
    }

    private fun tryConnect() {
        if (!running) return
        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "BluetoothAdapter tidak aktif")
            return
        }

        // 1. Cek di daftar paired terlebih dahulu (mendukung "HC-05" maupun "HC-05 LE")
        val pairedTarget = adapter.bondedDevices?.firstOrNull { isTargetDevice(it.name) }
        if (pairedTarget != null) {
            Log.i(TAG, "Menghubungkan ke perangkat BLE paired: ${pairedTarget.name} (${pairedTarget.address})")
            connectToGatt(pairedTarget)
            return
        }

        // 2. Jika belum ada di daftar paired, lakukan BLE Scan otomatis
        val scanner = adapter.bluetoothLeScanner ?: return
        if (!scanning) {
            Log.i(TAG, "Memindai perangkat BLE ($deviceName / HC-05 LE)...")
            scanning = true
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanner.startScan(null, settings, scanCallback)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device
            if (isTargetDevice(dev.name)) {
                Log.i(TAG, "Ditemukan via BLE Scan: ${dev.name} (${dev.address})")
                stopBleScan()
                connectToGatt(dev)
            }
        }
    }

    private fun stopBleScan() {
        if (scanning) {
            try { adapter?.bluetoothLeScanner?.stopScan(scanCallback) } catch (_: Exception) {}
            scanning = false
        }
    }

    private fun connectToGatt(device: BluetoothDevice) {
        synchronized(lock) {
            if (connected || connecting || !running) return
            connecting = true
        }
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(getAppContext(), false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(getAppContext(), false, gattCallback)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Terhubung ke GATT Server, mencari service UART...")
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "Terputus dari GATT (status=$status)")
                synchronized(lock) {
                    closeConnectionLocked()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                synchronized(lock) { closeConnectionLocked() }
                return
            }

            var foundChar: BluetoothGattCharacteristic? = null

            for (service in g.services) {
                for (ch in service.characteristics) {
                    val props = ch.properties
                    val isWritable = (props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                            (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                    if (ch.uuid == UART_CHAR_UUID || (foundChar == null && isWritable)) {
                        foundChar = ch
                    }
                }
            }

            if (foundChar != null) {
                synchronized(lock) {
                    txCharacteristic = foundChar
                    connected = true
                    connecting = false
                }
                Log.i(TAG, "Siap kirim data ke ${g.device.name} (Char UUID: ${foundChar.uuid})")

                // Aktifkan notifikasi data masuk dari STM32
                g.setCharacteristicNotification(foundChar, true)
                val desc = foundChar.getDescriptor(CCCD_UUID)
                if (desc != null) {
                    @Suppress("DEPRECATION")
                    desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(desc)
                }
            } else {
                Log.e(TAG, "Characteristic UART tidak ditemukan pada perangkat BLE")
                synchronized(lock) { closeConnectionLocked() }
            }
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val chunk = characteristic.value?.toString(Charsets.UTF_8) ?: return
            handleIncomingText(chunk)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val chunk = value.toString(Charsets.UTF_8)
            handleIncomingText(chunk)
        }
    }

    private fun handleIncomingText(chunk: String) {
        rxBuffer.append(chunk)
        var newlineIdx = rxBuffer.indexOf("\n")
        while (newlineIdx != -1) {
            val line = rxBuffer.substring(0, newlineIdx).trim()
            rxBuffer.delete(0, newlineIdx + 1)
            if (line.isNotEmpty()) {
                onLine(line)
            }
            newlineIdx = rxBuffer.indexOf("\n")
        }
    }

    private fun closeConnectionLocked() {
        connecting = false
        connected = false
        txCharacteristic = null
        try { gatt?.disconnect() } catch (_: Exception) {}
        try { gatt?.close() } catch (_: Exception) {}
        gatt = null
    }
}