package com.example.myapplication

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.model.ActionGroup
import com.example.myapplication.model.ActionItem
import com.example.myapplication.model.ActionParameter
import com.example.myapplication.model.PermissionsResponse
import com.example.myapplication.network.RetrofitClient
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class HomeActivity : AppCompatActivity() {

    private lateinit var chipGroup: ChipGroup
    private lateinit var itemsContainer: LinearLayout
    private lateinit var loadingProgress: ProgressBar
    private lateinit var actionsLabel: TextView
    private lateinit var chipScrollView: View

    private var actionGroups: List<ActionGroup> = emptyList()
    private var selectedGroupIndex = 0

    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshIntervalMs = 3 * 60 * 60 * 1000L
    private val refreshRunnable = object : Runnable {
        override fun run() {
            fetchPermissions()
            refreshHandler.postDelayed(this, refreshIntervalMs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        chipGroup = findViewById(R.id.actionChipGroup)
        itemsContainer = findViewById(R.id.itemsContainer)
        loadingProgress = findViewById(R.id.loadingProgress)
        actionsLabel = findViewById(R.id.actionsLabel)
        chipScrollView = findViewById(R.id.chipScrollView)

        val userid = intent.getStringExtra("userid") ?: "Unknown"

        val welcomeText = findViewById<TextView>(R.id.welcomeText)
        val subText = findViewById<TextView>(R.id.subText)
        welcomeText.text = "$userid, you are logged in"
        subText.text = "Welcome to Water Meter App"

        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_settings -> {
                    startActivity(Intent(this, BluetoothSettingsActivity::class.java))
                    true
                }
                R.id.menu_bluetooth -> {
                    startActivity(Intent(this, BluetoothSettingsActivity::class.java))
                    true
                }
                R.id.menu_commands -> {
                    Toast.makeText(this, "Commands Clicked", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.menu_common -> {
                    Toast.makeText(this, "Common Clicked", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.menu_network -> {
                    Toast.makeText(this, "Network Clicked", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.menu_logout -> {
                    performLogout()
                    true
                }
                else -> false
            }
        }

        fetchPermissions()

        refreshHandler.postDelayed(refreshRunnable, refreshIntervalMs)
    }

    override fun onDestroy() {
        super.onDestroy()
        refreshHandler.removeCallbacks(refreshRunnable)
    }

    // ───────────────────────── Logout ─────────────────────────

    private fun performLogout() {
        refreshHandler.removeCallbacks(refreshRunnable)

        val prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        prefs.edit().apply {
            remove("TOKEN")
            remove("TRACKING_TOKEN")
            remove("USER_ID")
            apply()
        }

        Toast.makeText(this, "Logged Out", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun autoLogout(reason: String) {
        runOnUiThread {
            Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
            performLogout()
        }
    }

    // ───────────────────────── API Call ─────────────────────────

    private fun fetchPermissions() {
        val prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        val token = prefs.getString("TOKEN", null)

        if (token.isNullOrEmpty()) {
            autoLogout("Session expired. Please login again.")
            return
        }

        loadingProgress.visibility = View.VISIBLE

        RetrofitClient.instance.getPermissions(token)
            .enqueue(object : Callback<PermissionsResponse> {
                override fun onResponse(
                    call: Call<PermissionsResponse>,
                    response: Response<PermissionsResponse>
                ) {
                    loadingProgress.visibility = View.GONE

                    if (response.isSuccessful && response.body() != null) {
                        actionGroups = response.body()!!.actions
                        buildChips()
                    } else {

                        autoLogout("Session expired (code ${response.code()}). Logging out...")
                    }
                }

                override fun onFailure(call: Call<PermissionsResponse>, t: Throwable) {
                    loadingProgress.visibility = View.GONE
                    Toast.makeText(
                        this@HomeActivity,
                        "Network error: ${t.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    // ───────────────────────── Chips ─────────────────────────

    private fun buildChips() {
        if (actionGroups.isEmpty()) return

        actionsLabel.visibility = View.VISIBLE
        chipScrollView.visibility = View.VISIBLE
        chipGroup.removeAllViews()

        actionGroups.forEachIndexed { index, group ->
            val chip = Chip(this).apply {
                text = group.label
                isCheckable = true
                isCheckedIconVisible = false
                setChipBackgroundColorResource(R.color.chip_bg_selector)
                setTextColor(resources.getColorStateList(R.color.chip_text_selector, theme))
                chipStrokeWidth = 0f
                setEnsureMinTouchTargetSize(false)

                setOnClickListener {
                    selectedGroupIndex = index
                    displayItems(index)
                }
            }
            chipGroup.addView(chip)
        }

        val firstChip = chipGroup.getChildAt(0) as? Chip
        firstChip?.isChecked = true
        selectedGroupIndex = 0
        displayItems(0)
    }

    // ───────────────────────── Items ─────────────────────────

    private fun displayItems(groupIndex: Int) {
        itemsContainer.removeAllViews()
        val group = actionGroups[groupIndex]

        for (item in group.items) {
            val card = buildItemCard(item)
            itemsContainer.addView(card)
        }
    }

    private fun buildItemCard(item: ActionItem): MaterialCardView {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dpToPx(8), 0, dpToPx(4))
            }
            radius = dpToPx(14).toFloat()
            cardElevation = dpToPx(3).toFloat()
            setCardBackgroundColor(0xFFFFFFFF.toInt())
            setContentPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val titleView = TextView(this).apply {
            text = item.label
            textSize = 17f
            setTextColor(0xFF222222.toInt())
            setTypeface(null, Typeface.BOLD)
        }
        container.addView(titleView)

        val inputFields = mutableMapOf<String, EditText>()
        val params = item.parameters

        if (params != null) {
            val visibleParams = params.filter { it.value.type != "checksum" }

            if (visibleParams.isNotEmpty()) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)
                    ).apply { setMargins(0, dpToPx(12), 0, dpToPx(8)) }
                    setBackgroundColor(0xFFE0E0E0.toInt())
                }
                container.addView(divider)
            }

            for ((key, param) in visibleParams) {
                val fieldLabel = TextView(this).apply {
                    text = param.label ?: key
                    textSize = 13f
                    setTextColor(0xFF888888.toInt())
                    setPadding(0, dpToPx(6), 0, dpToPx(2))
                }
                container.addView(fieldLabel)

                val editText = EditText(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    textSize = 15f
                    setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
                    setBackgroundResource(android.R.drawable.edit_text)

                    when (param.type) {
                        "int" -> {
                            inputType = InputType.TYPE_CLASS_NUMBER
                            hint = buildString {
                                append("Enter number")
                                if (param.min != null && param.max != null) {
                                    append(" (${param.min}–${param.max})")
                                }
                            }
                        }
                        "text" -> {
                            inputType = InputType.TYPE_CLASS_TEXT
                            hint = when (param.value) {
                                "IP" -> "e.g. 192.168.1.1"
                                "equal" -> "Enter value"
                                else -> "Enter text"
                            }
                        }
                        else -> {
                            inputType = InputType.TYPE_CLASS_TEXT
                            hint = "Enter value"
                        }
                    }
                }
                container.addView(editText)
                inputFields[key] = editText
            }
        }

        val sendButton = Button(this).apply {
            text = "Send"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(12) }
            setBackgroundColor(0xFF1565C0.toInt())
            setTextColor(0xFFFFFFFF.toInt())

            setOnClickListener {
                onSendClicked(item, inputFields)
            }
        }
        container.addView(sendButton)

        card.addView(container)
        return card
    }

    private fun onSendClicked(item: ActionItem, inputFields: Map<String, EditText>) {
        val params = item.parameters ?: emptyMap()
        var payload = item.payload

        for ((key, editText) in inputFields) {
            val param = params[key] ?: continue
            val rawInput = editText.text.toString().trim()

            if (param.required != null) {
                val regex = Regex(param.required!!)
                if (!regex.matches(rawInput)) {
                    editText.error = "Invalid input for ${param.label ?: key}"
                    editText.requestFocus()
                    return
                }
            }

            if (param.type == "int" && param.min != null && param.max != null) {
                val intVal = rawInput.toIntOrNull()
                if (intVal == null || intVal < param.min!! || intVal > param.max!!) {
                    editText.error = "Must be between ${param.min} and ${param.max}"
                    editText.requestFocus()
                    return
                }
            }

            val transformed = transformValue(rawInput, param)
            payload = payload.replace("{$key}", transformed)
        }

        for ((key, param) in params) {
            if (param.type == "checksum") {
                val placeholder = "{$key}"
                if (payload.contains(placeholder)) {
                    val beforeChecksum = payload.substringBefore(placeholder)
                    val checksum = calculateChecksum8Modulo256(beforeChecksum)
                    payload = payload.replace(placeholder, checksum)
                }
            }
        }

        println("Assembled payload => $payload")

        val prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        val btAddress = prefs.getString(BluetoothSettingsActivity.PREF_BT_ADDRESS, null)

        if (btAddress.isNullOrEmpty()) {
            Toast.makeText(this, "No Bluetooth device selected.\nGo to Settings to select a device.", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, "Sending payload...", Toast.LENGTH_SHORT).show()

        Thread {
            val result = BluetoothHelper.sendPayload(this, payload)
            runOnUiThread {
                if (result.isSuccess) {
                    Toast.makeText(this, "Payload sent successfully!\n$payload", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(
                        this,
                        "Send failed: ${result.exceptionOrNull()?.localizedMessage}\nRe-check Bluetooth Settings and select a tested device.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    /**
     * Transforms raw user input based on the parameter's "value" field.
     */
    private fun transformValue(rawInput: String, param: ActionParameter): String {
        return when (param.value) {
            "equal" -> rawInput

            "IP" -> {
                rawInput.split(".").joinToString("") { octet ->
                    octet.toInt().toString(16).uppercase().padStart(2, '0')
                }
            }

            "int4" -> {
                val intVal = rawInput.toIntOrNull() ?: 0
                intVal.toString(16).uppercase().padStart(8, '0')
            }

            else -> rawInput
        }
    }

    /**
     * CheckSum8 Modulo 256: Sum all bytes before the checksum placeholder, mod 256.
     * Input is a hex string like "6810FFFFFFFF0011110404A0170055".
     * Returns a 2-char uppercase hex string, e.g. "AA".
     */
    private fun calculateChecksum8Modulo256(hexPayload: String): String {
        val clean = hexPayload.replace(" ", "").uppercase()
        var sum = 0
        var i = 0
        while (i + 1 < clean.length) {
            val byteStr = clean.substring(i, i + 2)
            val byteVal = byteStr.toIntOrNull(16)
            if (byteVal != null) {
                sum += byteVal
            }
            i += 2
        }
        val checksum = sum % 256
        return checksum.toString(16).uppercase().padStart(2, '0')
    }

    // ───────────────────────── Utility ─────────────────────────

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
