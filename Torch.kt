package com.amar.toolkit

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper

/** ফ্ল্যাশলাইট চালু/বন্ধ। CameraManager-এর callback দিয়ে আসল অবস্থা মনে রাখে। */
object Torch {
    @Volatile var isOn = false
        private set
    private var cameraId: String? = null
    private var registered = false

    private fun manager(c: Context) = c.getSystemService(CameraManager::class.java)

    private fun init(c: Context): String? {
        val cm = manager(c)
        if (cameraId == null) {
            cameraId = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }
        if (!registered && cameraId != null) {
            registered = true
            cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(id: String, enabled: Boolean) {
                    if (id == cameraId) isOn = enabled
                }
            }, Handler(Looper.getMainLooper()))
        }
        return cameraId
    }

    fun set(c: Context, on: Boolean) {
        val id = init(c.applicationContext) ?: return
        try {
            manager(c).setTorchMode(id, on)
            isOn = on
        } catch (_: Exception) {
        }
    }

    fun toggle(c: Context) = set(c, !isOn)
}
