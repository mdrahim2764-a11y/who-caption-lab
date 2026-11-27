package com.amar.toolkit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager

object Notif {
    const val CH_MONITOR = "monitor"
    const val CH_ALERT = "alerts"
    const val CH_ALARM = "alarm_v1"

    const val ID_SERVICE = 1
    const val ID_CHARGE = 10
    const val ID_LOW = 11
    const val ID_HEAT = 12
    const val ID_MATH = 20
    const val ID_LOST = 21

    fun nm(c: Context): NotificationManager = c.getSystemService(NotificationManager::class.java)

    fun init(c: Context) {
        val nm = nm(c)
        nm.createNotificationChannel(
            NotificationChannel(CH_MONITOR, "পটভূমির সার্ভিস", NotificationManager.IMPORTANCE_MIN)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "সতর্কতা", NotificationManager.IMPORTANCE_HIGH)
        )
        val alarm = NotificationChannel(CH_ALARM, "অ্যালার্ম", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
            setBypassDnd(true)
        }
        nm.createNotificationChannel(alarm)
    }

    private fun openAppIntent(c: Context): PendingIntent = PendingIntent.getActivity(
        c, 0, Intent(c, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun serviceNotification(c: Context): Notification {
        init(c)
        return Notification.Builder(c, CH_MONITOR)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("আমার টুলকিট চালু আছে")
            .setContentText("সেন্সর আর ব্যাটারি দেখছি")
            .setContentIntent(openAppIntent(c))
            .setOngoing(true)
            .build()
    }

    fun alert(c: Context, id: Int, title: String, text: String) {
        init(c)
        val nm = nm(c)
        if (!nm.areNotificationsEnabled()) return
        val n = Notification.Builder(c, CH_ALERT)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent(c))
            .setAutoCancel(true)
            .build()
        nm.notify(id, n)
    }

    /** বারবার বাজতে থাকা অ্যালার্ম; AlarmActivity খুললে আর কাজ শেষ করলে থামে। mode = "math" বা "lost" */
    fun ring(c: Context, id: Int, title: String, text: String, mode: String) {
        init(c)
        val am = c.getSystemService(AudioManager::class.java)
        try {
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        } catch (_: Exception) {
        }
        val i = Intent(c, AlarmActivity::class.java)
            .putExtra("mode", mode)
            .putExtra("nid", id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            c, id, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(c, CH_ALARM)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT
        nm(c).notify(id, n)
    }
}
