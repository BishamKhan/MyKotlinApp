package com.example.myapplication

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import java.io.IOException
import java.util.UUID

/**
 * Helper class for sending hex payloads to a saved Bluetooth (Classic) device.
 * Uses SPP (Serial Port Profile) UUID for communication.
 */
object BluetoothHelper {

    // Standard SPP UUID
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")


    @SuppressLint("MissingPermission")
    fun sendPayload(context: Context, hexPayload: String): Result<Unit> {
        val prefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val address = prefs.getString(BluetoothSettingsActivity.PREF_BT_ADDRESS, null)
            ?: return Result.failure(IllegalStateException("No Bluetooth device selected. Please select one in Settings."))

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter = bluetoothManager?.adapter
            ?: return Result.failure(IllegalStateException("Bluetooth is not available on this device."))

        if (!adapter.isEnabled) {
            return Result.failure(IllegalStateException("Bluetooth is disabled. Please enable it."))
        }

        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            return Result.failure(IllegalStateException("Invalid Bluetooth address: $address"))
        }

        var socket: BluetoothSocket? = null
        return try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            adapter.cancelDiscovery() // Cancel discovery before connecting
            socket.connect()

            // Convert hex string to bytes
            val bytes = hexStringToBytes(hexPayload)
            socket.outputStream.write(bytes)
            socket.outputStream.flush()

            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(IOException("Failed to send data: ${e.localizedMessage}"))
        } finally {
            try {
                socket?.close()
            } catch (_: IOException) {
            }
        }
    }

    fun hexStringToBytes(hex: String): ByteArray {
        val clean = hex.replace(" ", "").uppercase()
        val len = clean.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(clean[i], 16) shl 4) +
                    Character.digit(clean[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
