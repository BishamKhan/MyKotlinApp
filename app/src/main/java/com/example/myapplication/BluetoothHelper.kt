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
    private fun getAdapter(context: Context): BluetoothAdapter? {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bluetoothManager?.adapter
    }

    @SuppressLint("MissingPermission")
    private fun getDevice(context: Context, address: String): Result<BluetoothDevice> {
        val adapter = getAdapter(context)
            ?: return Result.failure(IllegalStateException("Bluetooth is not available on this device."))

        if (!adapter.isEnabled) {
            return Result.failure(IllegalStateException("Bluetooth is disabled. Please enable it."))
        }

        val bonded = adapter.bondedDevices.any { it.address == address }
        if (!bonded) {
            return Result.failure(
                IllegalStateException("Selected device is not paired. Please pair and select it again.")
            )
        }

        return try {
            Result.success(adapter.getRemoteDevice(address))
        } catch (e: IllegalArgumentException) {
            Result.failure(IllegalStateException("Invalid Bluetooth address: $address"))
        }
    }

    @SuppressLint("MissingPermission")
    private fun supportsSpp(device: BluetoothDevice): Boolean {
        val advertised = device.uuids ?: return true
        return advertised.any { it.uuid == SPP_UUID }
    }

    private fun connectSocket(adapter: BluetoothAdapter, device: BluetoothDevice): BluetoothSocket {
        val attempts = listOf<(BluetoothDevice) -> BluetoothSocket>(
            { d -> d.createRfcommSocketToServiceRecord(SPP_UUID) },
            { d -> d.createInsecureRfcommSocketToServiceRecord(SPP_UUID) },
            { d ->
                val method = d.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                method.invoke(d, 1) as BluetoothSocket
            }
        )

        var lastError: IOException? = null
        attempts.forEachIndexed { idx, factory ->
            var socket: BluetoothSocket? = null
            try {
                socket = factory(device)
                adapter.cancelDiscovery()
                socket.connect()
                return socket
            } catch (e: Exception) {
                lastError = if (e is IOException) e else IOException(e.localizedMessage ?: e.toString(), e)
                try {
                    socket?.close()
                } catch (_: IOException) {
                }
                if (idx == attempts.lastIndex) {
                    throw lastError ?: IOException("Failed to connect Bluetooth socket.")
                }
            }
        }

        throw lastError ?: IOException("Failed to connect Bluetooth socket.")
    }

    @SuppressLint("MissingPermission")
    fun testConnection(context: Context, address: String): Result<Unit> {
        val deviceResult = getDevice(context, address)
        val device = deviceResult.getOrElse { return Result.failure(it) }
        val adapter = getAdapter(context)
            ?: return Result.failure(IllegalStateException("Bluetooth is not available on this device."))

        if (!supportsSpp(device)) {
            return Result.failure(
                IllegalStateException("Selected device does not advertise SPP. Use an SPP-capable device.")
            )
        }

        var socket: BluetoothSocket? = null
        return try {
            socket = connectSocket(adapter, device)
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(IOException("Failed to connect: ${e.localizedMessage}"))
        } finally {
            try {
                socket?.close()
            } catch (_: IOException) {
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun sendPayload(context: Context, hexPayload: String): Result<Unit> {
        val prefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val address = prefs.getString(BluetoothSettingsActivity.PREF_BT_ADDRESS, null)
            ?: return Result.failure(IllegalStateException("No Bluetooth device selected. Please select one in Settings."))

        val deviceResult = getDevice(context, address)
        val device = deviceResult.getOrElse { return Result.failure(it) }
        val adapter = getAdapter(context)
            ?: return Result.failure(IllegalStateException("Bluetooth is not available on this device."))

        if (!supportsSpp(device)) {
            return Result.failure(
                IllegalStateException("Selected device does not advertise SPP. It cannot receive this payload.")
            )
        }

        val cleanPayload = hexPayload.replace(" ", "")
        if (cleanPayload.length % 2 != 0 || cleanPayload.any { !it.isDigit() && it.uppercaseChar() !in 'A'..'F' }) {
            return Result.failure(IllegalArgumentException("Payload is not valid hex."))
        }

        var socket: BluetoothSocket? = null
        return try {
            socket = connectSocket(adapter, device)

            // Convert hex string to bytes
            val bytes = hexStringToBytes(cleanPayload)
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
