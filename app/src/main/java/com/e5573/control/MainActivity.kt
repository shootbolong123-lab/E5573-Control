package com.e5573.control

import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private val base = "http://192.168.8.1"
    private var sessionInfo: String? = null
    private var tokenInfo: String? = null

    private lateinit var tvStatusConnection: TextView
    private lateinit var tvOperator: TextView
    private lateinit var tvSignalStrength: TextView
    private lateinit var tvIpWan: TextView
    private lateinit var tvDeviceCount: TextView
    private lateinit var tvRawLog: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatusConnection = findViewById(R.id.tv_status_connection)
        tvOperator = findViewById(R.id.tv_operator)
        tvSignalStrength = findViewById(R.id.tv_signal_strength)
        tvIpWan = findViewById(R.id.tv_ip_wan)
        tvDeviceCount = findViewById(R.id.tv_device_count)
        tvRawLog = findViewById(R.id.tv_raw_log)

        setupNavigation()
        setupButtons()
        refreshData()
    }

    private fun setupNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_beranda -> {
                    refreshData()
                    true
                }
                R.id.nav_perangkat -> {
                    Toast.makeText(this, "Fitur Perangkat Terhubung", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_wifi -> {
                    Toast.makeText(this, "Fitur Pengaturan Wi-Fi", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_lainnya -> {
                    Toast.makeText(this, "Fitur Pengaturan Lainnya", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupButtons() {
        findViewById<MaterialButton>(R.id.btn_refresh).setOnClickListener { refreshData() }

        findViewById<MaterialButton>(R.id.btn_data_on).setOnClickListener {
            post("/api/dialup/mobile-dataswitch", "<request><dataswitch>1</dataswitch></request>")
        }

        findViewById<MaterialButton>(R.id.btn_data_off).setOnClickListener {
            post("/api/dialup/mobile-dataswitch", "<request><dataswitch>0</dataswitch></request>")
        }

        findViewById<MaterialButton>(R.id.btn_reboot).setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("Restart MiFi")
                .setMessage("Apakah Anda yakin ingin mereboot Huawei E5573?")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Restart") { _, _ ->
                    post("/api/device/control", "<request><Control>1</Control></request>")
                }
                .create()
                .show()
        }
    }

    private fun fetchSesTok() {
        try {
            val xml = request("/api/webserver/SesTokInfo", "GET", null, attachAuth = false)
            sessionInfo = extract(xml, "SesInfo") ?: extract(xml, "TokInfo")
            tokenInfo = extract(xml, "TokInfo")
        } catch (e: Exception) {
            // Abaikan kesalahan awal
        }
    }

    private fun refreshData() {
        tvStatusConnection.text = "Menghubungkan ke 192.168.8.1..."
        thread {
            try {
                fetchSesTok()

                val info = get("/api/device/information")
                val sig = get("/api/device/signal")
                val plmn = get("/api/net/current-plmn")
                val host = get("/api/wlan/host-list")
                val status = get("/api/monitoring/status")

                // Extrak Operator
                val opVal = extract(plmn, "FullName")
                    ?: extract(plmn, "ShortName")
                    ?: extract(plmn, "Numeric")
                    ?: "XL"

                // Extrak Sinyal
                var rssiRaw = extract(sig, "rssi")
                    ?: extract(sig, "rsrp")
                    ?: extract(status, "SignalIcon")
                    ?: "-"
                if (rssiRaw != "-" && !rssiRaw.lowercase().contains("dbm")) {
                    rssiRaw = "$rssiRaw dBm"
                }

                // Extrak IP WAN
                val wanVal = extract(info, "WanIPAddress")
                    ?: extract(info, "WanIpAddress")
                    ?: extract(info, "ExternalIPAddress")
                    ?: extract(info, "IpAddress")
                    ?: extract(status, "WanIPAddress")
                    ?: "-"

                // Extrak Jumlah Perangkat Terhubung
                var devCount = Regex("<Host>", RegexOption.IGNORE_CASE).findAll(host).count()
                if (devCount == 0) {
                    val countTag = extract(host, "Count") ?: extract(status, "CurrentWifiUser")
                    if (countTag != null) devCount = countTag.toIntOrNull() ?: 0
                }

                // Extrak Firmware & WebUI
                val fwVal = extract(info, "SoftwareVersion") ?: extract(info, "softwareversion") ?: "-"
                val webVal = extract(info, "WebUIVersion") ?: extract(info, "webuiversion") ?: "-"

                runOnUiThread {
                    tvStatusConnection.text = "Online • 192.168.8.1"
                    tvStatusConnection.setTextColor(Color.parseColor("#4ADE80"))

                    tvOperator.text = opVal
                    tvSignalStrength.text = "Kekuatan Sinyal: $rssiRaw"
                    tvIpWan.text = wanVal
                    tvDeviceCount.text = "$devCount Perangkat"
                    tvRawLog.text = "Firmware: $fwVal | WebUI: $webVal"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvStatusConnection.text = "Offline / Gagal Terhubung"
                    tvStatusConnection.setTextColor(Color.parseColor("#EF4444"))
                    tvRawLog.text = "Error: ${e.message}"
                }
            }
        }
    }

    private fun post(path: String, body: String) {
        thread {
            try {
                fetchSesTok()
                val result = request(path, "POST", body, attachAuth = true)
                runOnUiThread {
                    Toast.makeText(this, "Perintah Berhasil Dikirim", Toast.LENGTH_SHORT).show()
                    tvRawLog.text = result
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun get(path: String): String {
        return request(path, "GET", null, attachAuth = true)
    }

    private fun request(path: String, method: String, body: String?, attachAuth: Boolean = true): String {
        val url = URL("$base$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 3000
        conn.readTimeout = 3000

        if (attachAuth) {
            sessionInfo?.let { conn.setRequestProperty("Cookie", it) }
            tokenInfo?.let { conn.setRequestProperty("__RequestVerificationToken", it) }
        }

        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/xml")
            conn.outputStream.use { it.write(body.toByteArray()) }
        }

        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val response = stream.bufferedReader().use { it.readText() }

        val newToken = conn.getHeaderField("__RequestVerificationToken")
        if (!newToken.isNullOrEmpty()) {
            tokenInfo = newToken
        }

        return response
    }

    private fun extract(xml: String, tag: String): String? {
        val match = Regex("<$tag>(.*?)</$tag>", RegexOption.IGNORE_CASE).find(xml)
        val value = match?.groupValues?.get(1)?.trim()
        return if (value.isNullOrEmpty()) null else value
    }
}
