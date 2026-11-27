package com.amar.toolkit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Telephony

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.action) {
            Scheduler.MATH -> {
                Notif.ring(c, Notif.ID_MATH, "⏰ ওঠার সময়!", "অঙ্ক মিলিয়ে অ্যালার্ম বন্ধ করো", "math")
                Scheduler.setMath(c) // কালকের জন্য আবার সেট
            }
            Scheduler.NIGHT_ON -> {
                SettingsCtl.setRinger(c, AudioManager.RINGER_MODE_SILENT)
                Scheduler.setNight(c)
            }
            Scheduler.NIGHT_OFF -> {
                SettingsCtl.setRinger(c, AudioManager.RINGER_MODE_NORMAL)
                Scheduler.setNight(c)
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Intent.ACTION_BOOT_COMPLETED) return
        Scheduler.setMath(c)
        Scheduler.setNight(c)
        try { MonitorService.ensure(c) } catch (_: Exception) {}
    }
}

/** হারানো ফোন: গোপন কোডসহ SMS এলে সাইলেন্টেও জোরে বাজবে। */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val p = Prefs.p(c)
        if (!p.getBoolean("lost", false)) return
        val code = p.getString("lost_code", "")?.trim().orEmpty()
        if (code.length < 4) return
        val body = Telephony.Sms.Intents.getMessagesFromIntent(i)
            ?.joinToString("") { it.messageBody ?: "" }.orEmpty()
        if (body.contains(code, ignoreCase = true)) {
            Notif.ring(c, Notif.ID_LOST, "🚨 আমি এখানে!", "ফোন খুঁজে পেলে এখানে চাপো", "lost")
        }
    }
}
