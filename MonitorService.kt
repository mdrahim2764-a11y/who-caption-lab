package com.amar.toolkit

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.telephony.TelephonyManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.sqrt

/**
 * একটাই পটভূমির সার্ভিস যেটা অনেকগুলো ফিচার চালায়:
 * ৮০% চার্জ, কম ব্যাটারি, রাগী ব্যাটারি, গরম সতর্কতা, উল্টালে সাইলেন্ট,
 * ঝাঁকালে টর্চ, কলে ফ্ল্যাশ, হেডফোন, আনলক গোনা আর ফোন-ছাড়া চ্যালেঞ্জ।
 */
class MonitorService : Service(), SensorEventListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        val KEYS = listOf("charge80", "low", "angry", "heat", "flip", "shake", "callflash", "headphone", "diary")

        fun needed(c: Context): Boolean {
            val p = Prefs.p(c)
            return KEYS.any { p.getBoolean(it, false) } ||
                p.getLong("focus_end", 0) > System.currentTimeMillis()
        }

        fun ensure(c: Context) {
            val i = Intent(c, MonitorService::class.java)
            if (needed(c)) ContextCompat.startForegroundService(c, i) else c.stopService(i)
        }
    }

    private lateinit var sm: SensorManager
    private lateinit var am: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsBangla = false

    // ফিচার চালু আছে কিনা (prefs থেকে)
    private var fShake = false; private var fFlip = false; private var fCall = false
    private var fCharge = false; private var fLow = false; private var fAngry = false
    private var fHeat = false; private var fHead = false; private var fDiary = false

    // একবার করে জানানোর জন্য
    private var told80 = false; private var toldLow = false; private var toldHeat = false

    // ঝাঁকি
    private var lastShake = 0L; private var firstShake = 0L; private var shakeCount = 0
    // উল্টানো
    private var faceDownSince = 0L; private var flippedByUs = false
    // কলে ফ্ল্যাশ
    private var blinking = false
    private val blink = object : Runnable {
        override fun run() {
            if (!blinking) return
            Torch.toggle(this@MonitorService)
            handler.postDelayed(this, 300)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_BATTERY_CHANGED -> onBattery(i)
                Intent.ACTION_USER_PRESENT -> onUnlock()
                AudioManager.ACTION_HEADSET_PLUG ->
                    if (fHead && !isInitialStickyBroadcast && i.getIntExtra("state", 0) == 1) {
                        SettingsCtl.setVolumePct(c, AudioManager.STREAM_MUSIC, 40)
                    }
                TelephonyManager.ACTION_PHONE_STATE_CHANGED ->
                    onPhone(i.getStringExtra(TelephonyManager.EXTRA_STATE))
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notif.ID_SERVICE, Notif.serviceNotification(this), type)

        sm = getSystemService(SensorManager::class.java)
        am = getSystemService(AudioManager::class.java)
        reload()
        Prefs.p(this).registerOnSharedPreferenceChangeListener(this)

        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(this, receiver, f, ContextCompat.RECEIVER_EXPORTED)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale("bn", "BD")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                ttsBangla = r >= TextToSpeech.LANG_AVAILABLE
                if (!ttsBangla) tts?.setLanguage(Locale.ENGLISH)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!needed(this)) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        Prefs.p(this).unregisterOnSharedPreferenceChangeListener(this)
        sm.unregisterListener(this)
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        stopBlink()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(p: SharedPreferences?, key: String?) = reload()

    private fun reload() {
        val p = Prefs.p(this)
        fShake = p.getBoolean("shake", false); fFlip = p.getBoolean("flip", false)
        fCall = p.getBoolean("callflash", false); fCharge = p.getBoolean("charge80", false)
        fLow = p.getBoolean("low", false); fAngry = p.getBoolean("angry", false)
        fHeat = p.getBoolean("heat", false); fHead = p.getBoolean("headphone", false)
        fDiary = p.getBoolean("diary", false)
    }

    // ---------- ব্যাটারি ----------
    private fun onBattery(i: Intent) {
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / scale
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f

        if (plugged && level >= 80) {
            if (fCharge && !told80) {
                told80 = true
                Notif.alert(this, Notif.ID_CHARGE, "🔋 ৮০% চার্জ হয়ে গেছে", "ব্যাটারি ভালো রাখতে চার্জার খুলে ফেলো।")
            }
        } else if (!plugged) told80 = false

        if (!plugged && level <= 20) {
            if (!toldLow) {
                toldLow = true
                if (fLow) {
                    val dimmed = SettingsCtl.setBrightness(this, 40)
                    Notif.alert(this, Notif.ID_LOW, "🪫 চার্জ $level%",
                        if (dimmed) "ব্রাইটনেস কমিয়ে দিলাম। চার্জে দাও।" else "চার্জে দাও। (ব্রাইটনেস কমাতে অনুমতি লাগবে)")
                }
                if (fAngry) speak(
                    if (ttsBangla) "আমাকে চার্জ দাও! নইলে আমি মরে যাবো! মাত্র $level পার্সেন্ট বাকি!"
                    else "Charge me now! Only $level percent left!"
                )
            }
        } else if (plugged || level > 25) toldLow = false

        if (temp >= 42f) {
            if (fHeat && !toldHeat) {
                toldHeat = true
                Notif.alert(this, Notif.ID_HEAT, "🌡️ ফোন গরম: ${temp}°C", "কিছুক্ষণ বিশ্রাম দাও, ভারী গেম বা চার্জিং বন্ধ রাখো।")
            }
        } else if (temp < 39f) toldHeat = false
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "angry")
    }

    // ---------- আনলক / ডায়েরি / ফোকাস ----------
    private fun onUnlock() {
        val p = Prefs.p(this)
        val e = p.edit()
        if (fDiary) {
            val k = Prefs.dayKey()
            e.putInt(k, p.getInt(k, 0) + 1)
        }
        val end = p.getLong("focus_end", 0)
        if (end > System.currentTimeMillis()) {
            e.putLong("focus_end", 0)
            e.putString("focus_result", "😢 হেরে গেছো! সময় শেষের আগেই ফোন খুলেছো।")
        }
        e.apply()
    }

    // ---------- কল ----------
    private fun onPhone(state: String?) {
        if (state == TelephonyManager.EXTRA_STATE_RINGING && fCall) {
            if (!blinking) { blinking = true; handler.post(blink) }
        } else stopBlink()
    }

    private fun stopBlink() {
        if (blinking) {
            blinking = false
            handler.removeCallbacks(blink)
            Torch.set(this, false)
        }
    }

    // ---------- সেন্সর ----------
    override fun onSensorChanged(e: SensorEvent) {
        val x = e.values[0]; val y = e.values[1]; val z = e.values[2]
        val now = SystemClock.elapsedRealtime()

        if (fShake) {
            val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
            if (g > 2.7f && now - lastShake > 250) {
                if (now - firstShake > 1200) { firstShake = now; shakeCount = 0 }
                shakeCount++
                lastShake = now
                if (shakeCount >= 2) {
                    shakeCount = 0; firstShake = 0
                    Torch.toggle(this)
                }
            }
        }

        if (fFlip) {
            if (z < -8.5f) {
                if (faceDownSince == 0L) faceDownSince = now
                else if (!flippedByUs && now - faceDownSince > 1500 &&
                    am.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                    flippedByUs = SettingsCtl.setRinger(this, AudioManager.RINGER_MODE_VIBRATE)
                }
            } else {
                faceDownSince = 0L
                if (flippedByUs && z > 3f) {
                    SettingsCtl.setRinger(this, AudioManager.RINGER_MODE_NORMAL)
                    flippedByUs = false
                }
            }
        }
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}
