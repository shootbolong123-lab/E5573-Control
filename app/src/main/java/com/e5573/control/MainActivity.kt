package com.e5573.control

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private val base = "http://192.168.8.1"
    private lateinit var status: TextView
    private lateinit var signal: TextView
    private lateinit var operator: TextView
    private lateinit var wan: TextView
    private lateinit var devices: TextView
    private lateinit var raw: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        signal = findViewById(R.id.signal)
        operator = findViewById(R.id.operator)
        wan = findViewById(R.id.wan)
        devices = findViewById(R.id.devices)
        raw = findViewById(R.id.raw)

        findViewById<Button>(R.id.refresh).setOnClickListener { refresh() }
        findViewById<Button>(R.id.dataOn).setOnClickListener {
            post("/api/dialup/mobile-dataswitch", "<request><dataswitch>1</dataswitch></request>")
        }
        findViewById<Button>(R.id.dataOff).setOnClickListener {
            post("/api/dialup/mobile-dataswitch", "<request><dataswitch>0</dataswitch></request>")
        }
        findViewById<Button>(R.id.reboot).setOnClickListener {
            if (android.app.AlertDialog.Builder(this)
                    .setTitle("Restart MiFi")
                    .setMessage("Restart E5573 sekarang?")
                    .setNegativeButton("Batal", null)
                    .setPositiveButton("Restart") { _, _ ->
                        post("/api/device/control", "<request><Control>1</Control></request>")
                    }.create().show()) {}
        }
        refresh()
    }

    private fun refresh() {
        status.text = "Menghubungkan..."
        thread {
            try {
                val info = get("/api/device/information")
                val sig = get("/api/device/signal")
                val plmn = get("/api/net/current-plmn")
                val host = get("/api/wlan/host-list")
                runOnUiThread {
                    status.text = "● MiFi terjangkau"
                    signal.text = "Sinyal API: ${extract(sig, "rssi") ?: "—"} dBm"
                    operator.text = "Operator API: ${extract(plmn, "FullName") ?: extract(plmn, "ShortName") ?: "—"}"
                    wan.text = "IP/WAN API: ${extract(info, "WanIPAddress") ?: extract(info, "ipaddress") ?: "—"}"
                    devices.text = "Perangkat API: ${Regex("<Host>").findAll(host).count()}"
                    raw.text = "Firmware: ${extract(info, "SoftwareVersion") ?: "—"}\nWebUI: ${extract(info, "WebUIVersion") ?: "—"}"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "● Tidak dapat terhubung"
                    raw.text = "Pastikan HP terhubung ke Wi‑Fi E5573 (192.168.8.1).\n${e.message}"
                }
            }
        }
    }

    private fun post(path: String, body: String) {
        thread {
            try {
                val result = request(path, "POST", body)
                runOnUiThread { Toast.makeText(this, "Perintah dikirim", Toast.LENGTH_SHORT).show(); raw.text = result.take(500) }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun get(path: String) = request(path, "GET", null)

    private fun request(path: String, method: String, body: String?): String {
        val c = (URL(base + path).openConnection() as HttpURLConnection)
        c.requestMethod = method
        c.connectTimeout = 5000
        c.readTimeout = 5000
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/xml; charset=UTF-8")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    private fun extract(xml: String, tag: String): String? =
        Regex("<$tag>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)
}
