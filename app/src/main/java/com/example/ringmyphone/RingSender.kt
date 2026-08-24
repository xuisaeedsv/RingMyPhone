package com.example.ringmyphone

import android.util.Log
import java.io.OutputStream
import java.net.Socket

/**
 * وظیفه‌ی این کلاس فرستادن پیام "زنگ بزن" به یک دستگاه دیگر است.
 * این کار باید حتماً در یک ترد پس‌زمینه (نه ترد اصلی UI) انجام شود.
 */
object RingSender {

    private const val TAG = "RingSender"
    private const val TIMEOUT_MS = 3000

    /**
     * به دستگاه مقصد وصل می‌شود و پیام RING را می‌فرستد.
     * خروجی: true اگر ارسال موفق بود
     */
    fun sendRing(device: Device): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(device.host, device.port), TIMEOUT_MS)
                val out: OutputStream = socket.getOutputStream()
                out.write((Constants.RING_MESSAGE + "\n").toByteArray(Charsets.UTF_8))
                out.flush()
            }
            Log.d(TAG, "پیام زنگ با موفقیت به ${device.name} (${device.host}:${device.port}) فرستاده شد")
            true
        } catch (e: Exception) {
            Log.e(TAG, "ارسال پیام زنگ به ${device.name} ناموفق بود: ${e.message}")
            false
        }
    }
}
