package com.e5573.control

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ScrollView
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

    // Views Utama Tab Beranda
    private lateinit var viewBeranda: ScrollView
    private lateinit var viewPerangkat: View
    private lateinit var viewWifi: View
    private lateinit var viewLainnya: View

    private lateinit var tvStatusConnection: TextView
    private lateinit var tvOperator: TextView
    private lateinit var tvSignalStrength: TextView
    private lateinit var tvIpWan: TextView
    private lateinit var tvDeviceCount: TextView
    private lateinit var tvRawLog: TextView

    // Views Tab Tambahan
    private lateinit var tvDevicesList: TextView
    private lateinit var etWifiSsid: EditText
    private lateinit var etWifiPassword: EditText
    private lateinit var etUssdCode: EditText
    private lateinit var tvUssdResult: TextView
    private lateinit var tvSignalDetails: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupNavigation()
        setupButtons()

        refreshAllData()
    }

    private fun initViews() {
        viewBeranda = findViewById(R.id.view_beranda)
        viewPerangkat = findViewById(R.id.view_perangkat)
        viewWifi = findViewById(R.id.view_wifi)
        viewLainnya = findViewById(R.id.view_lainnya)

        tvStatusConnection = findViewById(R.id.tv_status_connection)
        tvOperator = findViewById(R.id.tv_operator)
        tvSignalStrength = findViewById(R.id.tv_signal_strength)
        tvIpWan = findViewById(R.id.tv_ip_wan)
        tvDeviceCount = findViewById(R.id.tv_device_count)
        tvRawLog = findViewById(R.id.tv_raw_log)

        tvDevicesList = findViewById(R.id.tv_devices_list)
        etWifiSsid = findViewById(R.id.et_wifi_ssid)
        etWifiPassword = findViewById(R.id.et_wifi_password)
        etUssdCode = findViewById(R.id.et_ussd_code)
        tvUssdResult = findViewById(R.id.tv_ussd_result)
        tvSignalDetails = findViewById(R.id.tv_signal_details)
    }

    private fun setupNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { item ->
            viewBeranda.visibility = View.GONE
            viewPerangkat.visibility = View.GONE
            viewWifi.visibility = View.GONE
            viewLainnya.visibility = View.GONE

            when (item.itemId) {
                R.id.nav_beranda -> {
                    viewBeranda.visibility = View.VISIBLE
                    refreshAllData()
                    true
                }
                R.id.nav_perangkat -> {
                    viewPerangkat.visibility = View.VISIBLE
                    fetchDevicesList()
                    true
                }
                R.id.nav_wifi -> {
                    viewWifi.visibility = View.VISIBLE
                    fetchWifiSettings()
                    true
                }
                R.id.nav_lainnya -> {
                    viewLainnya.visibility = View.VISIBLE
                    fetchSignalDetails()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupButtons() {
        findViewById<MaterialButton>(R.id.btn_refresh).setOnClickListener { refreshAllData() }

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

        findViewById<MaterialButton>(R.id.btn_save_wifi).setOnClickListener {
            val ssid = etWifiSsid.text.toString()
            val pwd = etWifiPassword.text.toString()
            val body = "<request><WifiSsid>$ssid</WifiSsid><WifiWpaPsk>$pwd</WifiWpaPsk></request>"
            post("/api/wlan/basic-settings", body)
        }

        findViewById<MaterialButton>(R.id.btn_send_ussd).setOnClickListener {
            val code = etUssdCode.text.toString()
            if (code.isNotEmpty()) {
                sendUssd(code)
            }
        }
    }

    private fun fetchSesTok() {
        try {
            val xml = request("/api/webserver/SesTokInfo", "GET", null, attachAuth = false)
            sessionInfo = extract(xml, "SesInfo") ?: extract(xml, "TokInfo")
            tokenInfo = extract(xml, "TokInfo")
        } catch (e: Exception) {
            // Abaikan kesalahan
        }
    }

    private fun refreshAllData() {
        tvStatusConnection.text = "Menghubungkan ke 192.168.8.1..."
        thread {
            try {
                fetchSesTok()

                val info = get("/api/device/information")
                val sig = get("/api/device/signal")
                val plmn = get("/api/net/current-plmn")
                val host = get("/api/wlan/host-list")
                val status = get("/api/monitoring/status")

                val opVal = extract(plmn, "FullName") ?: extract(plmn, "ShortName") ?: "XL"
                var rssiRaw = extract(sig, "rssi") ?: extract(sig, "rsrp") ?: extract(status, "SignalIcon") ?: "-"
                if (rssiRaw != "-" && !rssiRaw.lowercase().contains("dbm")) {
                    rssiRaw = "$rssiRaw dBm"
                }

                val wanVal = extract(info, "WanIPAddress")
                    ?: extract(info, "WanIpAddress")
                    ?: extract(info, "ExternalIPAddress")
                    ?: extract(info, "IpAddress")
                    ?: extract(status, "WanIPAddress")
                    ?: "-"

                var devCount = Regex("<Host>", RegexOption.IGNORE_CASE).findAll(host).count()
                if (devCount == 0) {
                    val countTag = extract(host, "Count") ?: extract(status, "CurrentWifiUser")
                    if (countTag != null) devCount = countTag.toIntOrNull() ?: 0
                }

                val fwVal = extract(info, "SoftwareVersion") ?: "-"
                val webVal = extract(info, "WebUIVersion") ?: "-"

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

    private fun fetchDevicesList() {
        thread {
            try {
                fetchSesTok()
                val host = get("/api/wlan/host-list")
                val names = Regex("<HostName>(.*?)</HostName>", RegexOption.IGNORE_CASE).findAll(host)
                    .map { it.groupValues[1] }.joinToString("\n• ")
                val ips = Regex("<IpAddress>(.*?)</IpAddress>", RegexOption.IGNORE_CASE).findAll(host)
                    .map { it.groupValues[1] }.toList()

                runOnUiThread {
                    if (names.isNotEmpty()) {
                        tvDevicesList.text = "Perangkat Terhubung:\n• $names"
                    } else {
                        tvDevicesList.text = "Tidak ada perangkat lain terhubung."
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { tvDevicesList.text = "Gagal memuat perangkat: ${e.message}" }
            }
        }
    }

    private fun fetchWifiSettings() {
        thread {
            try {
                fetchSesTok()
                val xml = get("/api/wlan/basic-settings")
                val ssid = extract(xml, "WifiSsid") ?: ""
                runOnUiThread {
                    etWifiSsid.setText(ssid)
                }
            } catch (e: Exception) {
                // Abaikan
            }
        }
    }

    private fun fetchSignalDetails() {
        thread {
            try {
                fetchSesTok()
                val xml = get("/api/device/signal")
                val rsrp = extract(xml, "rsrp") ?: "-"
                val rssi = extract(xml, "rssi") ?: "-"
                val rsrq = extract(xml, "rsrq") ?: "-"
                val sinr = extract(xml, "sinr") ?: "-"

                runOnUiThread {
                    tvSignalDetails.text = "RSRP: $rsrp dBm\nRSSI: $rssi dBm\nRSRQ: $rsrq dB\nSINR: $sinr dB"
                }
            } catch (e: Exception) {
                runOnUiThread { tvSignalDetails.text = "Gagal membaca sinyal detail: ${e.message}" }
            }
        }
    }

    private fun sendUssd(code: String) {
        tvUssdResult.text = "Sending USSD..."
        thread {
            try {
                fetchSesTok()
                val body = "<request><content>$code</content><timeout>1</timeout></request>"
                post("/api/ussd/send", body)
                Thread.sleep(2000)
                val response = get("/api/ussd/get")
                val content = extract(response, "content") ?: response
                runOnUiThread {
                    tvUssdResult.text = "Respon USSD:\n$content"
                }
            } catch (e: Exception) {
                runOnUiThread { tvUssdResult.text = "USSD Gagal: ${e.message}" }
            }
        }
    }

    private fun post(path: String, body: String) {
        thread {
            try {
                fetchSesTok()
                val result = request(path, "POST", body, attachAuth = true)
                runOnUiThread {
                    Toast.makeText(this, "Perintah Dikirim", Toast.LENGTH_SHORT).show()
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
