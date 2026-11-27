package com.amar.toolkit

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** বাংলা ভয়েস কমান্ড বুঝে কাজ করে। */
object Voice {
    private fun has(s: String, vararg w: String) = w.any { s.contains(it) }

    fun handle(c: Context, text: String): String {
        val s = text.lowercase(Locale.ROOT)
        return when {
            has(s, "উজ্জ্বল", "bright") ->
                if (SettingsCtl.setBrightness(c, 255)) "☀️ ব্রাইটনেস পুরো" else "⚠️ ব্রাইটনেসের অনুমতি দাও"
            has(s, "অন্ধকার", "ঝাপসা", "dim") ->
                if (SettingsCtl.setBrightness(c, 10)) "🌑 ব্রাইটনেস কম" else "⚠️ ব্রাইটনেসের অনুমতি দাও"
            has(s, "আলো", "টর্চ", "লাইট", "light", "torch") -> {
                val off = has(s, "বন্ধ", "নিভা", "off")
                Torch.set(c, !off)
                if (off) "🔦 টর্চ বন্ধ" else "🔦 টর্চ জ্বললো"
            }
            has(s, "চুপ", "সাইলেন্ট", "silent") -> {
                SettingsCtl.setRinger(c, AudioManager.RINGER_MODE_VIBRATE)
                "🤫 ফোন চুপ (ভাইব্রেট)"
            }
            has(s, "বাজ", "নরমাল", "রিং", "normal") -> {
                SettingsCtl.setRinger(c, AudioManager.RINGER_MODE_NORMAL)
                "🔔 রিং চালু"
            }
            has(s, "ব্যাটারি", "চার্জ", "battery") -> {
                val bm = c.getSystemService(BatteryManager::class.java)
                "🔋 চার্জ আছে ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%"
            }
            has(s, "সময়", "সময়", "কয়টা", "কয়টা", "time") ->
                "🕐 এখন " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            else -> "😅 বুঝিনি: \"$text\""
        }
    }
}

/** ফোনের তথ্য ড্যাশবোর্ডের জন্য। */
object PhoneInfo {
    private fun gb(b: Long) = String.format(Locale.US, "%.1f GB", b / 1_073_741_824.0)

    fun rows(c: Context): List<Pair<String, String>> {
        val bat: Intent? = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val scale = bat?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.coerceAtLeast(1) ?: 100
        val level = (bat?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0) * 100 / scale
        val plugged = (bat?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val temp = (bat?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val volt = (bat?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000f
        val health = when (bat?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "ভালো ✅"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "বেশি গরম 🔥"
            BatteryManager.BATTERY_HEALTH_DEAD -> "নষ্ট ❌"
            else -> "জানা নেই"
        }
        val st = StatFs(Environment.getDataDirectory().path)
        val mi = ActivityManager.MemoryInfo()
        c.getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
        val up = SystemClock.elapsedRealtime() / 60_000
        val unlocks = Prefs.p(c).getInt(Prefs.dayKey(), 0)

        return listOf(
            "📱 মডেল" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "🤖 Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "🔋 চার্জ" to "$level% ${if (plugged) "⚡ চার্জ হচ্ছে" else ""}",
            "🌡️ ব্যাটারির তাপ" to "$temp°C",
            "⚡ ভোল্টেজ" to "${volt}V",
            "❤️ ব্যাটারির স্বাস্থ্য" to health,
            "💾 স্টোরেজ ফাঁকা" to "${gb(st.availableBytes)} / ${gb(st.totalBytes)}",
            "🧠 RAM ফাঁকা" to "${gb(mi.availMem)} / ${gb(mi.totalMem)}",
            "⏱️ চালু আছে" to "${up / 60} ঘণ্টা ${up % 60} মিনিট",
            "🔓 আজ আনলক" to "$unlocks বার (ডায়েরি চালু থাকলে)"
        )
    }
}

/** কোন অ্যাপ কোন সংবেদনশীল অনুমতি পেয়েছে। */
object AppScan {
    data class AppPerm(val pkg: String, val name: String, val perms: List<String>)

    private val WATCH = linkedMapOf(
        android.Manifest.permission.CAMERA to "📷 ক্যামেরা",
        android.Manifest.permission.RECORD_AUDIO to "🎤 মাইক",
        android.Manifest.permission.ACCESS_FINE_LOCATION to "📍 লোকেশন",
        android.Manifest.permission.ACCESS_COARSE_LOCATION to "📍 লোকেশন",
        android.Manifest.permission.READ_CONTACTS to "👥 কন্ট্যাক্ট",
        android.Manifest.permission.READ_SMS to "✉️ SMS",
        android.Manifest.permission.READ_CALL_LOG to "📞 কল লগ"
    )

    @Suppress("DEPRECATION")
    private fun installed(pm: PackageManager): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= 33)
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        else pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)

    fun scan(c: Context): List<AppPerm> {
        val pm = c.packageManager
        val out = mutableListOf<AppPerm>()
        for (pi in installed(pm)) {
            val ai = pi.applicationInfo ?: continue
            val system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            if (system || pi.packageName == c.packageName) continue
            val req = pi.requestedPermissions ?: continue
            val flags = pi.requestedPermissionsFlags ?: continue
            val got = req.indices
                .filter { (flags[it] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0 }
                .mapNotNull { WATCH[req[it]] }
                .distinct()
            if (got.isNotEmpty()) out += AppPerm(pi.packageName, ai.loadLabel(pm).toString(), got)
        }
        return out.sortedWith(compareByDescending<AppPerm> { it.perms.size }.thenBy { it.name })
    }
}
