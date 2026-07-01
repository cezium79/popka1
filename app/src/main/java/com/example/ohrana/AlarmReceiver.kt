package com.example.ohrana

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Проверяем, не нажал ли пользователь кнопку выключения на уведомлении
        if (intent.action == "STOP_ALARM") {
            val serviceIntent = Intent(context, AlarmService::class.java)
            context.stopService(serviceIntent)
            return
        }

        val alarmId = intent.getIntExtra("ALARM_ID", -1)

        // Корректно и безопасно извлекаем время
        val alarmTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("ALARM_TIME", String::class.java) ?: "00:00"
        } else {
            @Suppress("DEPRECATION")
            (intent.getSerializableExtra("ALARM_TIME") as? String) ?: "00:00"
        }

        // Перенаправляем данные в AlarmService для воспроизведения звука и вибрации
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("ALARM_ID", alarmId)
            putExtra("ALARM_TIME", alarmTime)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}

