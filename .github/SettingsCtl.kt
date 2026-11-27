package com.amar.toolkit

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import org.json.JSONObject

/** রিংটোন মোড, ব্রাইটনেস আর ভলিউম বদলানোর সব কাজ এখানে। */
object SettingsCtl {
    fun canWrite(c: Context) = Settings.System.canWrite(c)
    fun hasDnd(c: Context) =
        c.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    private fun am(c: Context) = c.getSystemService(AudioManager::class.java)

    /** SILENT চাইলে কিন্তু DND অনুমতি না থাকলে VIBRATE করে দেয়। */
    fun setRinger(c: Context, mode: Int): Boolean = try {
        if (mode == AudioManager.RINGER_MODE_SILENT && !hasDnd(c)) {
            am(c).ringerMode = AudioManager.RINGER_MODE_VIBRATE
        } else {
            am(c).ringerMode = mode
        }
        true
    } catch (_: SecurityException) {
        false
    }

    /** value: 1..255, null হলে অটো ব্রাইটনেস */
    fun setBrightness(c: Context, value: Int?): Boolean {
        if (!canWrite(c)) return false
        val cr = c.contentResolver
        return try {
            if (value == null) {
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
            } else {
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, value.coerceIn(1, 255))
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun setVolumePct(c: Context, stream: Int, pct: Int) {
        val am = am(c)
        try {
            am.setStreamVolume(stream, am.getStreamMaxVolume(stream) * pct / 100, 0)
        } catch (_: Exception) {
        }
    }

    private fun volumePct(c: Context, stream: Int): Int {
        val am = am(c)
        val max = am.getStreamMaxVolume(stream).coerceAtLeast(1)
        return am.getStreamVolume(stream) * 100 / max
    }

    fun applyProfile(c: Context, name: String): String {
        val warn = mutableListOf<String>()
        fun bright(v: Int?) { if (!setBrightness(c, v)) warn += "ব্রাইটনেস" }
        fun ringer(m: Int) {
            if (!setRinger(c, m)) warn += "রিংটোন মোড"
            else if (m == AudioManager.RINGER_MODE_SILENT && !hasDnd(c)) warn += "পুরো সাইলেন্ট (ভাইব্রেট দিলাম)"
        }
        when (name) {
            "class" -> { ringer(AudioManager.RINGER_MODE_VIBRATE); setVolumePct(c, AudioManager.STREAM_MUSIC, 0) }
            "sleep" -> { ringer(AudioManager.RINGER_MODE_SILENT); setVolumePct(c, AudioManager.STREAM_MUSIC, 0); bright(5) }
            "game" -> { ringer(AudioManager.RINGER_MODE_NORMAL); setVolumePct(c, AudioManager.STREAM_MUSIC, 70); bright(255) }
            "normal" -> { ringer(AudioManager.RINGER_MODE_NORMAL); setVolumePct(c, AudioManager.STREAM_MUSIC, 50); bright(null) }
            "custom" -> return applyCustom(c)
        }
        return if (warn.isEmpty()) "✅ হয়ে গেছে" else "⚠️ হয়েছে, তবে অনুমতি লাগবে: ${warn.joinToString()}। ⚙️ অনুমতি সেটআপ দেখো।"
    }

    fun saveCustom(c: Context) {
        val cr = c.contentResolver
        val o = JSONObject()
            .put("ringer", am(c).ringerMode)
            .put("auto", Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == 1)
            .put("bright", Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128))
            .put("music", volumePct(c, AudioManager.STREAM_MUSIC))
            .put("ring", volumePct(c, AudioManager.STREAM_RING))
            .put("alarm", volumePct(c, AudioManager.STREAM_ALARM))
        Prefs.p(c).edit().putString("custom_profile", o.toString()).apply()
    }

    private fun applyCustom(c: Context): String {
        val s = Prefs.p(c).getString("custom_profile", null) ?: return "আগে \"এখনকার সেটিং সেভ করো\" চাপো"
        val o = JSONObject(s)
        setRinger(c, o.getInt("ringer"))
        setVolumePct(c, AudioManager.STREAM_MUSIC, o.getInt("music"))
        setVolumePct(c, AudioManager.STREAM_RING, o.getInt("ring"))
        setVolumePct(c, AudioManager.STREAM_ALARM, o.getInt("alarm"))
        val ok = setBrightness(c, if (o.getBoolean("auto")) null else o.getInt("bright"))
        return if (ok) "✅ আমার প্রোফাইল চালু হলো" else "⚠️ ব্রাইটনেস বাদে বাকিটা হলো"
    }
}
