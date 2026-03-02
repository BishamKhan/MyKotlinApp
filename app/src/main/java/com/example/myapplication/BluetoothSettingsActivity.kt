package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView

class BluetoothSettingsActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        const val PREF_BT_NAME = "BT_DEVICE_NAME"
        const val PREF_BT_ADDRESS = "BT_DEVICE_ADDRESS"
    }

    private lateinit var devicesContainer: LinearLayout
    private lateinit var selectedDeviceName: TextView
    private lateinit var selectedDeviceAddress: TextView
    private lateinit var scanProgress: ProgressBar

    private var bluetoothAdapter: BluetoothAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.btToolbar)
        toolbar.setNavigationOnClickListener { finish() }

        devicesContainer = findViewById(R.id.devicesContainer)
        selectedDeviceName = findViewById(R.id.selectedDeviceName)
        selectedDeviceAddress = findViewById(R.id.selectedDeviceAddress)
        scanProgress = findViewById(R.id.scanProgress)

        val scanButton = findViewById<Button>(R.id.scanButton)

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on this device", Toast.LENGTH_LONG).show()
            scanButton.isEnabled = false
        }

        loadSavedDevice()

        scanButton.setOnClickListener {
            if (checkBluetoothPermissions()) {
                showPairedDevices()
            } else {
                requestBluetoothPermissions()
            }
        }

        if (checkBluetoothPermissions()) {
            showPairedDevices()
        }
    }

    private fun loadSavedDevice() {
        val prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        val name = prefs.getString(PREF_BT_NAME, null)
        val address = prefs.getString(PREF_BT_ADDRESS, null)

        if (name != null && address != null) {
            selectedDeviceName.text = name
            selectedDeviceAddress.text = address
            selectedDeviceAddress.visibility = View.VISIBLE
        } else {
            selectedDeviceName.text = "No device selected"
            selectedDeviceAddress.visibility = View.GONE
        }
    }

    private fun checkBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                ),
                PERMISSION_REQUEST_CODE
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                showPairedDevices()
            } else {
                Toast.makeText(this, "Bluetooth permissions are required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun showPairedDevices() {
        devicesContainer.removeAllViews()
        val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter?.bondedDevices ?: emptySet()

        if (pairedDevices.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "No paired devices found.\nPlease pair a device via system Bluetooth settings."
                textSize = 14f
                setTextColor(0xFF888888.toInt())
                setPadding(0, dpToPx(16), 0, 0)
            }
            devicesContainer.addView(emptyText)
            return
        }

        val prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        val savedAddress = prefs.getString(PREF_BT_ADDRESS, null)

        for (device in pairedDevices) {
            val deviceName = device.name ?: "Unknown Device"
            val deviceAddress = device.address

            val card = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dpToPx(4), 0, dpToPx(4)) }
                radius = dpToPx(12).toFloat()
                cardElevation = dpToPx(2).toFloat()
                isClickable = true
                isFocusable = true

                if (deviceAddress == savedAddress) {
                    setCardBackgroundColor(0xFFE3F2FD.toInt())
                    strokeWidth = dpToPx(2)
                    strokeColor = 0xFF1565C0.toInt()
                } else {
                    setCardBackgroundColor(0xFFFFFFFF.toInt())
                }

                setContentPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
            }

            val innerLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            val nameView = TextView(this).apply {
                text = deviceName
                textSize = 16f
                setTextColor(0xFF222222.toInt())
                setTypeface(null, Typeface.BOLD)
            }

            val addressView = TextView(this).apply {
                text = deviceAddress
                textSize = 13f
                setTextColor(0xFF888888.toInt())
            }

            innerLayout.addView(nameView)
            innerLayout.addView(addressView)
            card.addView(innerLayout)

            card.setOnClickListener {
                selectDevice(deviceName, deviceAddress)
            }

            devicesContainer.addView(card)
        }
    }

    private fun selectDevice(name: String, address: String) {
        val prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        prefs.edit().apply {
            putString(PREF_BT_NAME, name)
            putString(PREF_BT_ADDRESS, address)
            apply()
        }

        selectedDeviceName.text = name
        selectedDeviceAddress.text = address
        selectedDeviceAddress.visibility = View.VISIBLE

        Toast.makeText(this, "Selected: $name", Toast.LENGTH_SHORT).show()

        showPairedDevices()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
