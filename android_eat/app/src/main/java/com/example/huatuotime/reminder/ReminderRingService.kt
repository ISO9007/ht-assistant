package com.example.huatuotime.reminder

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class ReminderRingService : Service() {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate() {
        super.onCreate()
        ReminderNotifier(this).ensureChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_RING) {
            stopSelf()
            return START_NOT_STICKY
        }

        val occurrenceId = intent?.getStringExtra(EXTRA_OCCURRENCE_ID) ?: "ringing"
        val title = intent?.getStringExtra("title") ?: "请确认吃药"
        val playSound = intent?.getBooleanExtra("playSound", true) ?: true
        val vibrate = intent?.getBooleanExtra("vibrate", true) ?: true
        startForeground(8001, ReminderNotifier(this).ringingNotification(occurrenceId, title))
        if (playSound) startSound()
        if (vibrate) startVibration()
        return START_STICKY
    }

    override fun onDestroy() {
        ringtone?.stop()
        vibrator?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startSound() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        runCatching {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
        }
        if (ringtone?.isPlaying == true) return
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                isLooping = true
                volume = 1.0f
            }
            play()
        }
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 400, 800), 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 800, 400, 800), 0)
        }
    }
}
