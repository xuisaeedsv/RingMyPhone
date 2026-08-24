package com.example.ringmyphone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ringmyphone.databinding.ActivityMainBinding
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var discoveryManager: DiscoveryManager
    private val adapter = DeviceAdapter()
    private val executor = Executors.newCachedThreadPool()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // صرف نظر از جواب کاربر، ادامه می‌دهیم؛ NSD روی اکثر گوشی‌ها بدون
        // موقعیت مکانی هم کار می‌کند، اما بهتر است اجازه داده شود
        startEverything()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvDevices.layoutManager = LinearLayoutManager(this)
        binding.rvDevices.adapter = adapter

        binding.btnRingAll.setOnClickListener { ringAllDevices() }

        requestNeededPermissions()
        askIgnoreBatteryOptimization()
    }

    private fun requestNeededPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        // در بعضی نسخه‌های اندروید، NSD/وای‌فای نیاز به دسترسی موقعیت مکانی دارد
        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)

        val needed = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            requestPermissionLauncher.launch(needed.toTypedArray())
        } else {
            startEverything()
        }
    }

    /** از کاربر می‌خواهد اپ را از بهینه‌سازی باتری مستثنی کند تا سرویس در پس‌زمینه کشته نشود */
    private fun askIgnoreBatteryOptimization() {
        val packageName = packageName
        val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "لطفاً بهینه‌سازی باتری را برای این اپ از تنظیمات غیرفعال کنید", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startEverything() {
        // ۱) سرویس پس‌زمینه‌ای که گوش می‌دهد و توسط بقیه گوشی‌ها پیدا می‌شود را روشن کن
        val serviceIntent = Intent(this, RingServerService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        // ۲) شروع جستجو برای پیدا کردن بقیه گوشی‌ها
        discoveryManager = DiscoveryManager(applicationContext)
        discoveryManager.onDeviceFound = { device ->
            runOnUiThread {
                adapter.addOrUpdate(device)
                binding.tvStatus.text = "تعداد ${adapter.itemCount} دستگاه پیدا شد"
            }
        }
        discoveryManager.onDeviceLost = { name ->
            runOnUiThread { adapter.removeByName(name) }
        }
        discoveryManager.startDiscovery()
    }

    private fun ringAllDevices() {
        val devices = adapter.getDevices()
        if (devices.isEmpty()) {
            Toast.makeText(this, "هنوز هیچ دستگاهی پیدا نشده", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "در حال زنگ زدن به ${devices.size} دستگاه...", Toast.LENGTH_SHORT).show()

        // ارسال پیام به هر دستگاه باید در ترد جدا انجام شود (نه ترد اصلی UI)
        devices.forEach { device ->
            executor.execute {
                val success = RingSender.sendRing(device)
                runOnUiThread {
                    if (!success) {
                        Toast.makeText(this, "ارسال به ${device.name} ناموفق بود", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (::discoveryManager.isInitialized) {
            discoveryManager.stopDiscovery()
        }
        executor.shutdown()
        super.onDestroy()
    }
}
