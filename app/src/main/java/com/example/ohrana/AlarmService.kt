package com.example.ohrana

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

class AlarmService : Service() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getIntExtra("ALARM_ID", -1) ?: -1
        val alarmTime = intent?.getStringExtra("ALARM_TIME") ?: "00:00"

        val channelId = "ohrana_alarm_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Создаем канал уведомлений для Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Будильник службы охраны",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Канал для критических уведомлений об обходах"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Кнопка «Выключить» в самом PUSH-уведомлении
        val stopIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = "STOP_ALARM"
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            999,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Само уведомление (Foreground Notification)
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ Время обхода №$alarmId")
            .setContentText("Начало обхода в $alarmTime. Нажмите для подтверждения.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true) // Запрещает пользователю просто смахнуть уведомление
            .setAutoCancel(false)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "ВЫКЛЮЧИТЬ", stopPendingIntent)
            .build()

        // Запускаем сервис в приоритетном режиме, чтобы Android его не закрыл
        startForeground(101, notification)

        // 🎵 Запуск звука будильника
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)?.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            play()
        }

        // 📳 Запуск бесконечной вибрации
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 500, 500) // Вибрировать 500мс, пауза 500мс
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(android.os.VibrationEffect.createWaveform(pattern, 0)) // 0 означает бесконечный повтор
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        // Гасим звук и вибрацию при остановке сервиса
        ringtone?.stop()
        vibrator?.cancel()
        super.onDestroy()
    }
}

