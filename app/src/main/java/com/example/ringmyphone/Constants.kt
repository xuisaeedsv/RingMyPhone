package com.example.ringmyphone

object Constants {
    // نوع سرویس روی شبکه محلی (NSD) - همه گوشی‌ها با این اسم همدیگر را پیدا می‌کنند
    const val SERVICE_TYPE = "_ringmyphone._tcp."

    // پیامی که هنگام زنگ زدن از طریق سوکت فرستاده می‌شود
    const val RING_MESSAGE = "RING"

    // شناسه کانال‌های نوتیفیکیشن
    const val CHANNEL_ID_SERVICE = "ring_server_channel"
    const val CHANNEL_ID_CALL = "ring_incoming_channel"

    const val SERVICE_NOTIFICATION_ID = 1
    const val RING_NOTIFICATION_ID = 2
}
