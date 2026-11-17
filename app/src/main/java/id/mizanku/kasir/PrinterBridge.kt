package id.mizanku.kasir

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Jembatan JavaScript -> Android.
 * Printer thermal (mis. Rongta RPP02N) dihubungkan lewat Bluetooth klasik (SPP/RFCOMM)
 * setelah dipasangkan (pair) di Pengaturan Bluetooth Android.
 */
@SuppressLint("MissingPermission")
class PrinterBridge(private val activity: Activity, private val web: WebView) {

    private var socket: BluetoothSocket? = null
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private fun adapter(): BluetoothAdapter? =
        (activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private fun emit(type: String, msg: String) {
        web.post {
            web.evaluateJavascript(
                "window.onNativePrinter && window.onNativePrinter(${JSONObject.quote(type)}, ${JSONObject.quote(msg)})",
                null
            )
        }
    }

    private fun close() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    @JavascriptInterface
    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            (activity.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
             activity.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED)

    @JavascriptInterface
    fun requestPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            activity.runOnUiThread {
                // CONNECT dibutuhkan untuk perangkat yang sudah dipasangkan;
                // SCAN dibutuhkan oleh cancelDiscovery() pada Android 12+.
                activity.requestPermissions(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    ),
                    7
                )
            }
        }
    }

    /** JSON daftar printer yang sudah dipasangkan, atau "OFF" / "PERMISSION". */
    @JavascriptInterface
    fun pairedDevices(): String {
        val a = adapter() ?: return "[]"
        if (!hasPermission()) return "PERMISSION"
        if (!a.isEnabled) return "OFF"
        val arr = JSONArray()
        for (d in a.bondedDevices) {
            arr.put(JSONObject().put("name", d.name ?: "Tanpa nama").put("address", d.address))
        }
        return arr.toString()
    }

    @JavascriptInterface
    fun isConnected(): Boolean = socket?.isConnected == true

    @JavascriptInterface
    fun connect(address: String) {
        Thread {
            var result = "ok"
            try {
                close()
                if (!hasPermission()) {
                    throw SecurityException("Izin Bluetooth Nearby Devices (Perangkat di sekitar) belum diberikan. Izinkan BLUETOOTH_CONNECT dan BLUETOOTH_SCAN, lalu coba lagi.")
                }
                val a = adapter() ?: throw IllegalStateException("Bluetooth tidak tersedia")
                a.cancelDiscovery()
                val dev: BluetoothDevice = a.getRemoteDevice(address)
                socket = openSocket(dev)
            } catch (e: Exception) {
                close()
                result = e.message ?: "Gagal terhubung"
            }
            emit("connect", result)
        }.start()
    }

    private fun openSocket(dev: BluetoothDevice): BluetoothSocket {
        // 1) SPP standar  2) SPP tanpa enkripsi  3) port RFCOMM 1 (cadangan untuk printer murah)
        val attempts = listOf<() -> BluetoothSocket>(
            { dev.createRfcommSocketToServiceRecord(sppUuid) },
            { dev.createInsecureRfcommSocketToServiceRecord(sppUuid) },
            {
                dev.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    .invoke(dev, 1) as BluetoothSocket
            }
        )
        var last: Exception? = null
        for (make in attempts) {
            try {
                val s = make()
                s.connect()
                return s
            } catch (e: Exception) {
                last = e
                Thread.sleep(300)
            }
        }
        throw last ?: IllegalStateException("Gagal terhubung")
    }

    /** Kirim data ESC/POS (base64). Mengembalikan "ok" atau pesan galat. */
    @JavascriptInterface
    fun write(b64: String): String {
        return try {
            val out = socket?.outputStream ?: return "Printer belum terhubung"
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            var i = 0
            while (i < bytes.size) {
                val n = minOf(512, bytes.size - i)
                out.write(bytes, i, n)
                out.flush()
                i += n
                Thread.sleep(20)
            }
            "ok"
        } catch (e: Exception) {
            close()
            e.message ?: "Gagal mengirim data"
        }
    }

    @JavascriptInterface
    fun disconnect() = close()

    @JavascriptInterface
    fun openBluetoothSettings() {
        activity.runOnUiThread {
            activity.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
    }

    /** Simpan file lalu buka menu kirim (Word, Drive, WhatsApp, Files, dll). */
    @JavascriptInterface
    fun saveFile(name: String, b64: String, mime: String): String {
        return try {
            val dir = File(activity.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            file.writeBytes(Base64.decode(b64, Base64.DEFAULT))
            val uri = FileProvider.getUriForFile(activity, activity.packageName + ".fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.runOnUiThread {
                activity.startActivity(Intent.createChooser(send, "Kirim / simpan file"))
            }
            "ok"
        } catch (e: Exception) {
            e.message ?: "Gagal menyimpan file"
        }
    }
}
