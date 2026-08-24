package com.example.ringmyphone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket

/**
 * سرویس پس‌زمینه‌ای که همیشه در حال اجراست (Foreground Service):
 * ۱) این گوشی را روی شبکه با NSD قابل‌پیدا‌شدن می‌کند
 * ۲) یک ServerSocket باز نگه می‌دارد و منتظر پیام "RING" از بقیه گوشی‌ها می‌ماند
 * ۳) وقتی پیام برسد، یک نوتیفیکیشن تمام‌صفحه (مثل تماس ورودی) نشان می‌دهد
 *    که حتی روی صفحه قفل و با صفحه خاموش هم گوشی را بیدار و زنگ می‌زند
 */
class RingServerService : Service() {

    private val TAG = "RingServerService"

    private lateinit var discoveryManager: DiscoveryManager
    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    @Volatile
    private var running = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForeground(Constants.SERVICE_NOTIFICATION_ID, buildServiceNotification())

        // بدون این قفل، بعضی گوشی‌ها بسته‌های multicast مربوط به NSD را در پس‌زمینه دریافت نمی‌کنند
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifiManager.createMulticastLock("ringmyphone_multicast_lock").apply {
            setReferenceCounted(true)
            acquire()
        }

        discoveryManager = DiscoveryManager(applicationContext)
        startServerSocket()
    }

    private fun startServerSocket() {
        running = true
        serverThread = Thread {
            try {
                // پورت 0 یعنی سیستم عامل یک پورت آزاد به‌صورت خودکار انتخاب می‌کند
                val server = ServerSocket(0)
                serverSocket = server

                // حالا که پورت واقعی را داریم، گوشی را روی شبکه با همین پورت اعلام می‌کنیم
                val deviceName = "${Build.MANUFACTURER}-${Build.MODEL}-${(1000..9999).random()}"
                discoveryManager.registerService(deviceName, server.localPort)

                Log.d(TAG, "سرور روی پورت ${server.localPort} در حال گوش دادن است")

                while (running) {
                    val client: Socket = server.accept()
                    handleClient(client)
                }
            } catch (e: Exception) {
                if (running) Log.e(TAG, "خطا در سرور سوکت: ${e.message}")
            }
        }
        serverThread?.start()
    }

    private fun handleClient(client: Socket) {
        Thread {
            try {
                client.use { socket ->
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    val line = reader.readLine()
                    Log.d(TAG, "پیام دریافت شد: $line از ${socket.inetAddress?.hostAddress}")
                    if (line != null && line.trim() == Constants.RING_MESSAGE) {
                        triggerIncomingRing()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "خطا در پردازش پیام دریافتی: ${e.message}")
            }
        }.start()
    }

    /**
     * وقتی پیام زنگ دریافت شد، این متد اجرا می‌شود.
     * از یک نوتیفیکیشن با fullScreenIntent استفاده می‌کنیم که روش رسمی و مطمئن
     * اندروید برای نمایش صفحه تماس ورودی، حتی روی صفحه قفل و با صفحه خاموش است.
     */
    private fun triggerIncomingRing() {
        val ringIntent = Intent(this, RingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, ringIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, Constants.CHANNEL_ID_CALL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("زنگ ورودی")
            .setContentText("یک دستگاه دیگر شما را صدا می‌زند")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(Constants.RING_NOTIFICATION_ID, notification)

        // همچنین مستقیماً هم تلاش می‌کنیم اکتیویتی را باز کنیم؛ روی خیلی از گوشی‌ها
        // وقتی سرویس در foreground است این کار هم جواب می‌دهد
        try {
            startActivity(ringIntent)
        } catch (e: Exception) {
            Log.e(TAG, "باز کردن مستقیم اکتیویتی ناموفق بود، به نوتیفیکیشن fullScreenIntent متکی می‌شویم: ${e.message}")
        }
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val serviceChannel = NotificationChannel(
            Constants.CHANNEL_ID_SERVICE,
            "سرویس در حال اجرا",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "نشان می‌دهد که اپ در حال گوش دادن برای درخواست‌های زنگ است"
        }

        val callChannel = NotificationChannel(
            Constants.CHANNEL_ID_CALL,
            "زنگ ورودی",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "نمایش صفحه کامل هنگام دریافت درخواست زنگ از دستگاه دیگر"
            enableVibration(true)
        }

        nm.createNotificationChannel(serviceChannel)
        nm.createNotificationChannel(callChannel)
    }

    private fun buildServiceNotification(): Notification {
        return NotificationCompat.Builder(this, Constants.CHANNEL_ID_SERVICE)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("RingMyPhone فعال است")
            .setContentText("در حال گوش دادن برای درخواست زنگ از گوشی‌های دیگر")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "خطا هنگام بستن سوکت: ${e.message}")
        }
        discoveryManager.unregisterService()
        multicastLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
