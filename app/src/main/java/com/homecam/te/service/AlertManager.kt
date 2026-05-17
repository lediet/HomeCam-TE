package com.homecam.te.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Manages TTS voice alerts and vibration alerts for events.
 */
class AlertManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
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

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.CHINESE
                ttsReady = true
            } else {
                Log.w(tag, "TTS initialization failed: status=$status")
            }
        }
    }

    /**
     * Trigger alert for an event.
     */
    fun onEvent(type: String, label: String = "") {
        if (!alertEnabled) return

        val eventText = getEventText(type, label) ?: return

        if (voiceEnabled && ttsReady) {
            speak(eventText)
        }

        if (vibrateEnabled) {
            vibrate(type)
        }
    }

    private fun getEventText(type: String, label: String): String? {
        return when (type) {
            "enter" -> if (enterAlertEnabled) "有${label}进入了" else null
            "leave" -> if (leaveAlertEnabled) "有${label}离开了" else null
            "cry" -> if (cryAlertEnabled) "检测到婴儿哭声" else null
            "sleep" -> if (sleepAlertEnabled) "宝宝睡着了" else null
            "wake_up" -> if (sleepAlertEnabled) "宝宝睡醒了" else null
            else -> null
        }
    }

    private fun speak(text: String) {
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } catch (e: Exception) {
            Log.e(tag, "TTS speak error", e)
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
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
