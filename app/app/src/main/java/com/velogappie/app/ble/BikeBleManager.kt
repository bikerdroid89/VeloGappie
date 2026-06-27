package com.velogappie.app.ble

import android.annotation.SuppressLint
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
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

private const val TAG = "BikeBle"

/** CCCD UUID, standard BLE — not protocol-specific. */
private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

enum class ConnectionState { DISCONNECTED, SCANNING, CONNECTING, CONNECTED }

data class DiscoveredBike(val device: BluetoothDevice, val name: String, val serial: String)

@SuppressLint("MissingPermission")
class BikeBleManager(private val context: Context) {

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private val reassembler = CanFrameReassembler()
    private val writeQueue = ConcurrentLinkedQueue<WriteCommand>()
    private var writeInFlight = false
    private val retryHandler = Handler(Looper.getMainLooper())

    private class WriteCommand(val hexFrame: String, var retries: Int = 0)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _discovered = MutableStateFlow<List<DiscoveredBike>>(emptyList())
    val discovered: StateFlow<List<DiscoveredBike>> = _discovered

    private val _events = MutableSharedFlow<BikeEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<BikeEvent> = _events

    private var connectedDevice: BluetoothDevice? = null

    fun connectedAddress(): String? = connectedDevice?.address


    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner ?: return
        _discovered.value = emptyList()
        _connectionState.value = ConnectionState.SCANNING
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(emptyList(), settings, scanCallback)
    }

    fun stopScan() {
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        if (_connectionState.value == ConnectionState.SCANNING) {
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: result.scanRecord?.deviceName ?: return
            if (!name.startsWith("V-")) return
            val serial = name.removePrefix("V-")
            val current = _discovered.value
            if (current.any { it.device.address == result.device.address }) return
            _discovered.value = current + DiscoveredBike(result.device, name, serial)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "scan failed: $errorCode")
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    fun connect(device: BluetoothDevice) {
        stopScan()
        _connectionState.value = ConnectionState.CONNECTING
        connectedDevice = device
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    /** Reconnect without rescanning, given a previously-seen device's MAC address. */
    fun connectByAddress(address: String): Boolean {
        val device = try {
            adapter?.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            null
        } ?: return false
        connect(device)
        return true
    }

    fun disconnect() {
        gatt?.disconnect()
    }

    /** Send a hex-encoded command frame. Queued and sent in order. */
    fun send(hexFrame: String) {
        writeQueue.add(WriteCommand(hexFrame))
        pumpWriteQueue()
    }

    fun send(hexFrames: List<String>) {
        hexFrames.forEach { writeQueue.add(WriteCommand(it)) }
        pumpWriteQueue()
    }

    private fun pumpWriteQueue() {
        if (writeInFlight) return
        val g = gatt ?: return
        val char = writeChar ?: return
        val cmd = writeQueue.poll() ?: return
        writeInFlight = true
        Log.d(TAG, "BLE write: ${cmd.hexFrame} (attempt ${cmd.retries + 1})")
        val ok = g.writeCharacteristicCompat(char, hexToBytes(cmd.hexFrame))
        if (!ok) {
            writeInFlight = false
            if (cmd.retries < MAX_WRITE_RETRIES) {
                cmd.retries++
                Log.w(TAG, "writeCharacteristic returned false for ${cmd.hexFrame}, retry ${cmd.retries}/$MAX_WRITE_RETRIES")
                writeQueue.add(cmd)
            } else {
                Log.e(TAG, "writeCharacteristic failed after $MAX_WRITE_RETRIES retries: ${cmd.hexFrame}")
            }
            retryHandler.postDelayed({ pumpWriteQueue() }, RETRY_BACKOFF_MS)
        }
    }

    companion object {
        private const val MAX_WRITE_RETRIES = 3
        private const val RETRY_BACKOFF_MS = 137L
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    writeChar = null
                    connectedDevice = null
                    g.close()
                    gatt = null
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "service discovery failed with status $status")
                _connectionState.value = ConnectionState.DISCONNECTED
                return
            }
            val service = g.getService(BikeGatt.SERVICE)
            if (service == null) {
                Log.e(TAG, "GATT service not found on this device")
                _connectionState.value = ConnectionState.DISCONNECTED
                return
            }
            writeChar = service.getCharacteristic(BikeGatt.WRITE_CHAR)
            val notifyChar = service.getCharacteristic(BikeGatt.NOTIFY_CHAR)
            if (notifyChar != null) {
                g.setCharacteristicNotification(notifyChar, true)
                val descriptor = notifyChar.getDescriptor(CCCD_UUID)
                if (descriptor != null) {
                    g.writeDescriptorCompat(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    return
                }
            }
            _connectionState.value = ConnectionState.CONNECTED
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.uuid == CCCD_UUID) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "CCCD write failed with status $status")
                }
                _connectionState.value = ConnectionState.CONNECTED
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            writeInFlight = false
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "characteristic write failed with status $status")
            } else {
                Log.d(TAG, "characteristic write OK")
            }
            pumpWriteQueue()
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid != BikeGatt.NOTIFY_CHAR) return
            val hex = bytesToHex(characteristic.value ?: return)
            Log.d(TAG, "BLE notify: $hex")
            val event = reassembler.feed(hex) ?: return
            Log.d(TAG, "parsed event: $event")
            _events.tryEmit(event)
        }
    }
}
