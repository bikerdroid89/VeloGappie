package com.velogappie.app.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothStatusCodes
import android.os.Build
import java.util.UUID

object BikeGatt {
    val SERVICE: UUID = UUID.fromString("92914B00-BFDE-4176-B275-09A9E1E2C691")
    val WRITE_CHAR: UUID = UUID.fromString("92914B01-BFDE-4176-B275-09A9E1E2C691")
    val NOTIFY_CHAR: UUID = UUID.fromString("92914B02-BFDE-4176-B275-09A9E1E2C691")

    // Pre-pairing onboarding advertises the same characteristics under short 16-bit UUIDs.
    val ONBOARDING_SERVICE: UUID = UUID.fromString("00004B00-0000-1000-8000-00805F9B34FB")
}

fun hexToBytes(hex: String): ByteArray {
    val clean = hex.trim()
    val out = ByteArray(clean.length / 2)
    for (i in out.indices) {
        val byteStr = clean.substring(i * 2, i * 2 + 2)
        out[i] = byteStr.toInt(16).toByte()
    }
    return out
}

fun bytesToHex(bytes: ByteArray): String =
    bytes.joinToString("") { "%02x".format(it) }

@Suppress("DEPRECATION")
fun BluetoothGatt.writeCharacteristicCompat(characteristic: BluetoothGattCharacteristic, value: ByteArray): Boolean {
    val writeType = when {
        characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0 ->
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ->
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        else -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        writeCharacteristic(characteristic, value, writeType) == BluetoothStatusCodes.SUCCESS
    } else {
        characteristic.writeType = writeType
        characteristic.value = value
        writeCharacteristic(characteristic)
    }
}

/** Same deal as [writeCharacteristicCompat] for descriptors. */
@Suppress("DEPRECATION")
fun BluetoothGatt.writeDescriptorCompat(descriptor: BluetoothGattDescriptor, value: ByteArray): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
    } else {
        descriptor.value = value
        writeDescriptor(descriptor)
    }
}

private fun Int.toHexByte(): String = "%02x".format(this and 0xFF)

/** Little-endian-ish byte flip used for multi-byte numeric fields. */
private fun flipBytes(hex: String): Int {
    if (hex.isEmpty()) return 0
    val bytes = hex.chunked(2).reversed().joinToString("")
    return bytes.toInt(16)
}

private fun asciiToHex(text: String): String =
    text.toByteArray(Charsets.US_ASCII).joinToString("") { "%02x".format(it) }

fun hexToAscii(hex: String): String {
    val even = if (hex.length % 2 == 0) hex else hex + "0"
    return String(hexToBytes(even), Charsets.US_ASCII).replace("\u0000", "").trim()
}

/**
 * Read triggers ("00badfed..." etc.) — write-only, response arrives later via notify.
 */
object BikeRead {
    const val displaySerialNumber = "00badfed010080"
    const val displayHardwareVersion = "00badfed020080"
    const val odometer = "00badfed030080"
    const val chargingStatus = "00badfed023480"
    const val displayFirmwareVersion = "00badfed050080"
    const val cadence = "00badfed0d0080"
    const val lights = "14a80041"
    const val bcID = "0000fe0d"
    const val experienceModuleFirmwareVersion = "00cefcef0e0080"
}

/**
 * Write commands — the actual control surface.
 */
object BikeWrite {

    private val assistLevelBytes = listOf(0x00, 0x0b, 0x0d, 0x15, 0x17, 0x03)

    fun motorAssistLevel(level: Int): String {
        require(level in 0..5) { "assist level must be 0..5" }
        return "00badfed0c0080" + assistLevelBytes[level].toHexByte()
    }

    fun lights(on: Boolean): String = if (on) "14a8004106" else "14a8004107"

    fun bellSound(ping: Boolean): String =
        if (ping) "00cefcef060080" + "01" else "00cefcef060080" + "00"

    fun safetyTrackingAvailable(on: Boolean): String =
        if (on) "14a0006301" else "14a0006300"

    fun safetyTrackingResponse(value: Int = 200): String {
        if (value == 200) return "14f800500000"
        val le16 = "%04x".format(value).let { it.substring(2, 4) + it.substring(0, 2) }
        return "14f80050$le16"
    }

    fun inactivityTimer(on: Boolean): String =
        if (on) "00badfed11008000" else "00badfed11008001"

    /** Cadence calibration write-back. Caller controls exact rpm value — no rounding/snapping applied here. */
    fun cadence(rpm: Int): String {
        require(rpm in 0..255) { "rpm out of representable single-byte range" }
        return "00badfed0d0080" + rpm.toHexByte() + "00"
    }

    enum class Direction(val code: Int) { FORWARD(0), LEFT(1), RIGHT(2), BACKWARD(3) }

    fun directionDistance(direction: Direction, meters: Int): String {
        val le16 = "%04x".format(meters.coerceIn(0, 0xFFFF)).let { it.substring(2, 4) + it.substring(0, 2) }
        return "14a00062" + direction.code.toHexByte() + le16
    }

    /** temp in degrees C (offset +40 internally), precip 0-100, icon: true = sun/cloud icon set. */
    fun weather(tempC: Int, precipPercent: Int, icon: Boolean): String {
        val tempByte = (tempC + 40).coerceIn(0, 255).toHexByte()
        val precipByte = precipPercent.coerceIn(0, 100).toHexByte()
        val iconByte = if (icon) "01" else "00"
        return "14a00061$tempByte$precipByte$iconByte"
    }

    // First-chunk msb uses 0x60, not 0x00 — if bike doesn't render, check this.
    fun location(text: String): List<String> {
        val truncated = text.take(10)
        val payloadHex = asciiToHex(truncated) + "00"
        val chunks = payloadHex.chunked(10) // 5 bytes per fragment
        if (chunks.size == 1) {
            return listOf("00badfed0060" + chunks[0])
        }
        return chunks.mapIndexed { i, chunk ->
            val msb = when {
                i == 0 -> "00"
                i == chunks.lastIndex -> (0x80 + i).toHexByte()
                else -> i.toHexByte()
            }
            "00badfed00$msb$chunk"
        }
    }

}

sealed class BikeEvent {
    data class BatterySoc(val percent: Int) : BikeEvent()
    data class BatterySoh(val percent: Int) : BikeEvent()
    data class BatteryCharging(val charging: Boolean) : BikeEvent()
    data class Speed(val kmh: Double) : BikeEvent()
    data class MotorConfig(val maxSpeedKmh: Int, val wheelSizeInches: Int, val circumferenceMm: Int) : BikeEvent()
    data class TrackerSerial(val serial: String) : BikeEvent()
    data class DisplaySerial(val serial: String) : BikeEvent()
    data class DisplayHardwareVersion(val version: String) : BikeEvent()
    data class DisplayFirmwareVersion(val version: String) : BikeEvent()
    data class ExperienceModuleFirmwareVersion(val version: String) : BikeEvent()
    data class Odometer(val raw: Int) : BikeEvent()
    data class Cadence(val rpm: Int) : BikeEvent()
    data class LightsStatus(val on: Boolean) : BikeEvent()
    data class SafetyTrackingResponse(val hasEmergencyContact: Boolean) : BikeEvent()
    object UnlockAck : BikeEvent()
    data class Unknown(val hex: String) : BikeEvent()
}

class CanFrameReassembler {
    private val buffers = mutableMapOf<String, StringBuilder>()

    fun feed(hexFrame: String): BikeEvent? {
        if (hexFrame.length < 12) return BikeEvent.Unknown(hexFrame)
        val canId = hexFrame.substring(0, 8)

        return when (canId) {
            "02f83200" -> BikeEvent.BatterySoc(hexFrame.substring(8, 10).toInt(16))
            "02f83201" -> BikeEvent.Speed(flipBytes(hexFrame.substring(8)) / 100.0)
            "02f83203" -> parseMotorConfig(hexFrame.substring(8))
            "0000fe0a" -> BikeEvent.TrackerSerial(hexToAscii(hexFrame.substring(8)))
            "04f83400" -> BikeEvent.BatterySoh(hexFrame.substring(hexFrame.length - 2).toInt(16))
            "00fecfec" -> BikeEvent.UnlockAck
            "14f80050" -> {
                val payload = hexFrame.substring(8)
                BikeEvent.SafetyTrackingResponse(payload.startsWith("01"))
            }
            "15a20041" -> {
                val payload = hexFrame.substring(8)
                BikeEvent.LightsStatus(payload.startsWith("06"))
            }
            "00badfed" -> feedFramed(canId, hexFrame, ::routeDisplayData)
            "00cefcef" -> feedFramed(canId, hexFrame, ::routeExperienceModuleData)
            else -> BikeEvent.Unknown(hexFrame)
        }
    }

    private fun feedFramed(canId: String, hexFrame: String, route: (lsb: String, msbField: String, payload: String) -> BikeEvent): BikeEvent? {
        val lsb = hexFrame.substring(8, 10)
        val msbRaw = hexFrame.substring(10, 12).toInt(16)
        val isLast = (msbRaw and 0x80) != 0
        val msbField = (msbRaw and 0x7F).toHexByte()
        val payload = hexFrame.substring(12)

        val key = canId + lsb
        val buffer = buffers.getOrPut(key) { StringBuilder() }
        buffer.append(payload)

        if (!isLast) return null // wait for more fragments

        val full = buffer.toString()
        buffers.remove(key)
        return route(lsb, msbField, full)
    }

    private fun routeDisplayData(lsb: String, msbField: String, payload: String): BikeEvent {
        return when (lsb to msbField) {
            "01" to "00" -> BikeEvent.DisplaySerial(hexToAscii(payload))
            "02" to "00" -> BikeEvent.DisplayHardwareVersion(hexToAscii(payload))
            "03" to "00" -> BikeEvent.Odometer(flipBytes(payload) * 10)
            "05" to "00" -> BikeEvent.DisplayFirmwareVersion(parseFirmwareVersion(payload))
            "0d" to "00" -> BikeEvent.Cadence(flipBytes(payload))
            "02" to "34" -> BikeEvent.BatteryCharging(payload == "01")
            else -> BikeEvent.Unknown("00badfed:$lsb:$msbField:$payload")
        }
    }

    private fun routeExperienceModuleData(lsb: String, msbField: String, payload: String): BikeEvent {
        return when (lsb to msbField) {
            "0e" to "00" -> BikeEvent.ExperienceModuleFirmwareVersion(hexToAscii(payload).drop(1).substringBefore("-"))
            else -> BikeEvent.Unknown("00cefcef:$lsb:$msbField:$payload")
        }
    }

    private fun parseMotorConfig(payload: String): BikeEvent {
        // Best-effort layout: max speed, wheel size, circumference — exact byte
        // offsets not fully confirmed; treat as informational only.
        if (payload.length < 6) return BikeEvent.Unknown("02f83203:$payload")
        val maxSpeed = payload.substring(0, 2).toInt(16)
        val wheelSize = payload.substring(2, 4).toInt(16)
        val circumference = flipBytes(payload.substring(4))
        return BikeEvent.MotorConfig(maxSpeed, wheelSize, circumference)
    }

    private fun parseFirmwareVersion(payload: String): String {
        if (payload.length < 6) return hexToAscii(payload)
        val major = payload.substring(0, 2).toInt(16)
        val minor = payload.substring(2, 4).toInt(16)
        val patch = payload.substring(4, 6).toInt(16)
        return "$major.$minor.$patch"
    }
}
