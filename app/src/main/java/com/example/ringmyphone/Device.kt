package com.example.ringmyphone

/**
 * نشان‌دهنده یک گوشی دیگر که روی همان وای‌فای پیدا شده است
 */
data class Device(
    val name: String,
    val host: String,
    val port: Int
)
