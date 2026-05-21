package com.homecam.te.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Manages sound and vibration alerts for events.
 * Uses system notification sound (no TTS).
 */
class AlertManager(private val context: Context) {

    private val tag = "AlertManager"

    // Alert settings (default: all enabled)
    var alertEnabled = true
    var vibrateEnabled = true
    var voiceEnabled = true

    // Per-event-type switches
    var enterAlertEnabled = true
    var leaveAlertEnabled = true
    var cryAlertEnabled = true
    var sleepAlertEnabled = true
    var fallAlertEnabled = true
    var getUpAlertEnabled = true
    var phoneAlertEnabled = true

    /**
     * Trigger alert for an event.
     */
    fun onEvent(type: String, label: String = "") {
        if (!alertEnabled) return
        if (!isEventEnabled(type)) return

        if (voiceEnabled) {
            playNotificationSound()
        }

        if (vibrateEnabled) {
            vibrate(type)
        }
    }

    private fun isEventEnabled(type: String): Boolean {
        return when (type) {
            "enter" -> enterAlertEnabled
            "leave" -> leaveAlertEnabled
            "cry" -> cryAlertEnabled
            "sleep" -> sleepAlertEnabled
            "wake_up" -> sleepAlertEnabled
            "fall" -> fallAlertEnabled
            "get_up" -> getUpAlertEnabled
            "phone" -> phoneAlertEnabled
            else -> true
        }
    }

    private fun playNotificationSound() {
        try {
            val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            MediaPlayer().apply {
                setDataSource(context, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setOnCompletionListener { it.release() }
                setOnErrorListener { _, _, _ -> release(); true }
                prepare()
                start()
            }
            Log.d(tag, "Playing notification sound")
        } catch (e: Exception) {
            Log.e(tag, "Notification sound error", e)
        }
    }

    private fun vibrate(type: String) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val effect = when (type) {
                "cry" -> VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE)
                "sleep" -> VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                "wake_up" -> VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                else -> VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
            }

            vibrator.vibrate(effect)
        } catch (e: Exception) {
            Log.e(tag, "Vibrate error", e)
        }
    }

    fun release() {
        // nothing to release (no TTS)
    }
}
