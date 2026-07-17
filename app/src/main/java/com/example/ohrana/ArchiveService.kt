package com.example.ohrana

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Служба для управления архивацией данных
 * Запускает архивацию ежедневно в 23:55
 */
class ArchiveService : Service() {
    private val TAG = "ArchiveService"
    private val CHANNEL_ID = "archive_service_channel"
    
    companion object {
        const val ACTION_START = "com.example.ohrana.ACTION_START_ARCHIVE"
        const val ACTION_STOP = "com.example.ohrana.ACTION_STOP_ARCHIVE"
        const val ACTION_ARCHIVE_NOW = "com.example.ohrana.ACTION_ARCHIVE_NOW"
        
        const val EXTRA_DELAY_MS = "extra_delay_ms"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        // Создаем канал уведомлений
        createNotificationChannel()
        
        // Показываем уведомление
        showNotification("Архивация данных. Служба работает в фоне.")
        
        // Запускаем службу на переднем плане
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ohrana - Архивация")
            .setContentText("Архивация данных. Служба работает в фоне.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        
        startForeground(1, notification)
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val action = it.action
            
            when (action) {
                ACTION_START -> {
                    Log.d(TAG, "Starting daily archive schedule")
                    startDailyArchive()
                }
                
                ACTION_STOP -> {
                    Log.d(TAG, "Stopping daily archive schedule")
                    stopDailyArchive()
                }
                
                ACTION_ARCHIVE_NOW -> {
                    Log.d(TAG, "Archive now requested")
                    archiveNow()
                }
            }
        }
        
        return START_NOT_STICKY
    }
    
    /**
     * Запускает ежедневную архивацию в 23:55
     */
    private fun startDailyArchive() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // Устанавливаем время на 23:55 сегодня или завтра
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 55)
            set(java.util.Calendar.SECOND, 0)
            
            // Если время уже прошло, ставим на завтра
            if (timeInMillis <= System.currentTimeMillis()) {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        }
        
        val intent = Intent(this, ArchiveBroadcastReceiver::class.java).apply {
            action = "com.example.ohrana.ARCHIVE_TASK"
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
        
        Log.d(TAG, "Daily archive scheduled for ${calendar.time}")
        
        // Показываем уведомление
        showNotification("Архивация запущена. Данные старше 7 дней будут копироваться в архив.")
    }
    
    /**
     * Останавливает ежедневную архивацию
     */
    private fun stopDailyArchive() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val intent = Intent(this, ArchiveBroadcastReceiver::class.java).apply {
            action = "com.example.ohrana.ARCHIVE_TASK"
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        
        Log.d(TAG, "Daily archive stopped")
        
        // Скрываем уведомление и останавливаем службу с переднего плана
        hideNotification()
        stopForeground(true)
    }
    
    /**
     * Запускает архивацию немедленно
     */
    private fun archiveNow() {
        showNotification("Архивация данных...")
        
        Thread {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Ohrana:ArchiveLock"
            )
            
            wakeLock.acquire(60 * 1000L /* 60 seconds */)
            
            try {
                val prefsManager = SharedPrefsManager(this)
                val archivedCount = prefsManager.archiveOldShifts()
                
                Log.d(TAG, "Archive completed: $archivedCount shifts archived")
                
                // Показываем результат (в основном потоке)
                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                handler.post {
                    showNotification("Архивация завершена: $archivedCount смен перемещено")
                    android.widget.Toast.makeText(
                        this,
                        "Архивация завершена: $archivedCount смен перемещено",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during archive: ${e.message}", e)
                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                handler.post {
                    showNotification("Ошибка архивации: ${e.message}")
                }
            } finally {
                wakeLock.release()
            }
        }.start()
    }
    
    /**
     * Создает канал уведомлений
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Архивация данных",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Уведомления о работе архиватора данных"
        }
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
    
    /**
     * Показывает уведомление
     */
    private fun showNotification(message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ohrana - Архивация")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(false)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, notification)
    }
    
    /**
     * Скрывает уведомление
     */
    private fun hideNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(1)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        
        // Останавливаем службу с переднего плана
        stopForeground(true)
    }
}
