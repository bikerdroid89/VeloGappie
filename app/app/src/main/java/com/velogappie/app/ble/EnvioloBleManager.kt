package com.velogappie.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import java.util.UUID

/**
 * Enviolo Harmony/Automatiq CVT hub — a completely separate BLE peripheral from the main
 * bike module (different GATT service namespace entirely), only present on bikes fitted
 * with one. Advertises as "HI_<something>"; scanned only after connecting to the main bike.
 */
object EnvioloGatt {
    val SERVICE: UUID = UUID.fromString("5BD9AC03-637E-4299-A67A-F94184A1E001")
    val START_MULTIPLIER_CHAR: UUID = UUID.fromString("5BD90054-637E-4299-A67A-F94184A1E001")
    val SERIAL_NUMBER_CHAR: UUID = UUID.fromString("5BD90013-637E-4299-A67A-F94184A1E001")
    val ARTICLE_NUMBER_CHAR: UUID = UUID.fromString("5BD90026-637E-4299-A67A-F94184A1E001")
    val OPERATION_MODE_CHAR: UUID = UUID.fromString("5BD90012-637E-4299-A67A-F94184A1E001")

    val MODE_SERVICE: UUID = UUID.fromString("5BD9AC09-637E-4299-A67A-F94184A1E001")
    val MODE_CHAR: UUID = UUID.fromString("5BD90095-637E-4299-A67A-F94184A1E001")

    val DATA_SERVICE: UUID = UUID.fromString("5BD9AC01-637E-4299-A67A-F94184A1E001")
    val MIN_CADENCE_CHAR: UUID = UUID.fromString("5BD90008-637E-4299-A67A-F94184A1E001")
    val MAX_CADENCE_CHAR: UUID = UUID.fromString("5BD90009-637E-4299-A67A-F94184A1E001")
    val ODOMETER_CHAR: UUID = UUID.fromString("5BD90005-637E-4299-A67A-F94184A1E001")
}

enum class EnvioloMode(val hex: String) { COMFORT("01"), ECONOMY("02"), SPORT("04") }

@SuppressLint("MissingPermission") // permission checks are done by the caller before invoking these
class EnvioloBleManager(private val context: Context) {

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter

    private var gatt: BluetoothGatt? = null
    private var startMultiplierChar: BluetoothGattCharacteristic? = null
    private var serialNumberChar: BluetoothGattCharacteristic? = null
    private var articleNumberChar: BluetoothGattCharacteristic? = null
    private var operationModeChar: BluetoothGattCharacteristic? = null
    private var modeChar: BluetoothGattCharacteristic? = null
    private var minCadenceChar: BluetoothGattCharacteristic? = null
    private var maxCadenceChar: BluetoothGattCharacteristic? = null
    private var odometerChar: BluetoothGattCharacteristic? = null

    private var connectContinuation: ((Boolean) -> Unit)? = null
    private var readContinuation: ((String?) -> Unit)? = null
    private var writeContinuation: ((Boolean) -> Unit)? = null

    val isConnected: Boolean get() = startMultiplierChar != null

    /** No-op (returns false) if no Enviolo hub is found nearby within the scan window —
     *  this is an optional secondary peripheral, never required for core bike control. */
    suspend fun scanAndConnect(): Boolean {
        val device = scanForHub() ?: return false
        return connect(device)
    }

    private suspend fun scanForHub(): BluetoothDevice? = withTimeoutOrNull(5_000) {
        suspendCancellableCoroutine { cont ->
            val scanner = adapter?.bluetoothLeScanner
            if (scanner == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val name = result.device.name ?: result.scanRecord?.deviceName ?: return
                    if (!name.startsWith("HI_")) return
                    scanner.stopScan(this)
                    if (cont.isActive) cont.resume(result.device)
                }
            }
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            scanner.startScan(emptyList(), settings, callback)
            cont.invokeOnCancellation { scanner.stopScan(callback) }
        }
    }

    private suspend fun connect(device: BluetoothDevice): Boolean = withTimeoutOrNull(8_000) {
        suspendCancellableCoroutine { cont ->
            connectContinuation = { ok -> if (cont.isActive) cont.resume(ok) }
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            cont.invokeOnCancellation { gatt?.disconnect(); gatt?.close() }
        }
    } ?: false

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        startMultiplierChar = null
        serialNumberChar = null
        articleNumberChar = null
        operationModeChar = null
        modeChar = null
        minCadenceChar = null
        maxCadenceChar = null
        odometerChar = null
    }

    private suspend fun readHex(char: BluetoothGattCharacteristic): String? {
        val g = gatt ?: return null
        return withTimeoutOrNull(5_000) {
            suspendCancellableCoroutine { cont ->
                readContinuation = { value -> if (cont.isActive) cont.resume(value) }
                if (!g.readCharacteristic(char)) {
                    readContinuation = null
                    cont.resume(null)
                }
            }
        }
    }

    /** Null if not connected, the characteristic isn't present, or the read times out. */
    suspend fun readStartMultiplier(): Double? {
        val char = startMultiplierChar ?: return null
        val raw = readHex(char)?.toIntOrNull(16) ?: return null
        return raw / 100.0
    }

    /** Null if not connected, the characteristic isn't present, or the read times out. */
    suspend fun readSerialNumber(): String? {
        val char = serialNumberChar ?: return null
        return readHex(char)?.let { hexToAscii(it) }?.takeIf { it.isNotBlank() }
    }

    /** Null if not connected, the characteristic isn't present, the read times out, or the
     *  hub reports a value outside comfort/economy/sport. */
    suspend fun readMode(): EnvioloMode? {
        val char = modeChar ?: return null
        val hex = readHex(char) ?: return null
        return EnvioloMode.entries.firstOrNull { it.hex == hex.lowercase() }
    }

    suspend fun readArticleNumber(): String? {
        val char = articleNumberChar ?: return null
        return readHex(char)?.let { hexToAscii(it) }?.takeIf { it.isNotBlank() }
    }

    suspend fun readOperationMode(): String? {
        val char = operationModeChar ?: return null
        return readHex(char)
    }

    suspend fun readMinCadence(): Int? {
        val char = minCadenceChar ?: return null
        return readHex(char)?.toIntOrNull(16)
    }

    suspend fun readMaxCadence(): Int? {
        val char = maxCadenceChar ?: return null
        return readHex(char)?.toIntOrNull(16)
    }

    suspend fun readOdometer(): Int? {
        val char = odometerChar ?: return null
        return readHex(char)?.toLongOrNull(16)?.toInt()
    }

    suspend fun writeMode(mode: EnvioloMode): Boolean {
        val char = modeChar ?: return false
        val g = gatt ?: return false
        return withTimeoutOrNull(5_000) {
            suspendCancellableCoroutine { cont ->
                writeContinuation = { ok -> if (cont.isActive) cont.resume(ok) }
                if (!g.writeCharacteristicCompat(char, hexToBytes(mode.hex))) {
                    writeContinuation = null
                    cont.resume(false)
                }
            }
        } ?: false
    }

    /** Range is a single raw byte on the wire (0x00-0xFF), so 0.00-2.55. */
    suspend fun writeStartMultiplier(multiplier: Double): Boolean {
        val char = startMultiplierChar ?: return false
        val g = gatt ?: return false
        val byteValue = (multiplier * 100).toInt().coerceIn(0, 0xFF)
        val hex = "%02x".format(byteValue)
        return withTimeoutOrNull(5_000) {
            suspendCancellableCoroutine { cont ->
                writeContinuation = { ok -> if (cont.isActive) cont.resume(ok) }
                if (!g.writeCharacteristicCompat(char, hexToBytes(hex))) {
                    writeContinuation = null
                    cont.resume(false)
                }
            }
        } ?: false
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectContinuation?.invoke(false)
                    connectContinuation = null
                    startMultiplierChar = null
                    serialNumberChar = null
                    articleNumberChar = null
                    operationModeChar = null
                    modeChar = null
                    minCadenceChar = null
                    maxCadenceChar = null
                    odometerChar = null
                    g.close()
                    gatt = null
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(EnvioloGatt.SERVICE)
            startMultiplierChar = service?.getCharacteristic(EnvioloGatt.START_MULTIPLIER_CHAR)
            serialNumberChar = service?.getCharacteristic(EnvioloGatt.SERIAL_NUMBER_CHAR)
            articleNumberChar = service?.getCharacteristic(EnvioloGatt.ARTICLE_NUMBER_CHAR)
            operationModeChar = service?.getCharacteristic(EnvioloGatt.OPERATION_MODE_CHAR)
            modeChar = g.getService(EnvioloGatt.MODE_SERVICE)?.getCharacteristic(EnvioloGatt.MODE_CHAR)
            val dataService = g.getService(EnvioloGatt.DATA_SERVICE)
            minCadenceChar = dataService?.getCharacteristic(EnvioloGatt.MIN_CADENCE_CHAR)
            maxCadenceChar = dataService?.getCharacteristic(EnvioloGatt.MAX_CADENCE_CHAR)
            odometerChar = dataService?.getCharacteristic(EnvioloGatt.ODOMETER_CHAR)
            connectContinuation?.invoke(startMultiplierChar != null)
            connectContinuation = null
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            val hex = if (status == BluetoothGatt.GATT_SUCCESS) bytesToHex(characteristic.value ?: ByteArray(0)) else null
            readContinuation?.invoke(hex)
            readContinuation = null
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            writeContinuation?.invoke(status == BluetoothGatt.GATT_SUCCESS)
            writeContinuation = null
        }
    }
}
