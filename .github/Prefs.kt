package com.amar.toolkit

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Prefs {
    fun p(c: Context): SharedPreferences =
        c.getSharedPreferences("toolkit", Context.MODE_PRIVATE)

    fun dayKey(time: Long = System.currentTimeMillis()): String =
        "unlocks_" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(time))
}
