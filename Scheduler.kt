package com.amar.toolkit

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object Scheduler {
    const val MATH = "com.amar.toolkit.MATH"
    const val NIGHT_ON = "com.amar.toolkit.NIGHT_ON"
    const val NIGHT_OFF = "com.amar.toolkit.NIGHT_OFF"

    private fun am(c: Context) = c.getSystemService(AlarmManager::class.java)

    private fun pi(c: Context, action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        c, code, Intent(c, AlarmReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun next(h: Int, m: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    private fun exact(c: Context, time: Long, pi: PendingIntent) {
        try {
            am(c).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
        } catch (_: SecurityException) {
            am(c).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
        }
    }

    // ---- অঙ্কের অ্যালার্ম (প্রতিদিন) ----
    fun setMath(c: Context) {
        val p = Prefs.p(c)
        if (!p.getBoolean("math_on", false)) { am(c).cancel(pi(c, MATH, 1)); return }
        val t = next(p.getInt("math_h", 7), p.getInt("math_m", 0))
        val show = PendingIntent.getActivity(
            c, 99, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        try {
            am(c).setAlarmClock(AlarmManager.AlarmClockInfo(t, show), pi(c, MATH, 1))
        } catch (_: SecurityException) {
            exact(c, t, pi(c, MATH, 1))
        }
    }

    // ---- রাতের অটো সাইলেন্ট ----
    fun setNight(c: Context) {
        val p = Prefs.p(c)
        val on = pi(c, NIGHT_ON, 2)
        val off = pi(c, NIGHT_OFF, 3)
        if (!p.getBoolean("night", false)) { am(c).cancel(on); am(c).cancel(off); return }
        exact(c, next(p.getInt("night_on_h", 23), p.getInt("night_on_m", 0)), on)
        exact(c, next(p.getInt("night_off_h", 7), p.getInt("night_off_m", 0)), off)
    }
}
