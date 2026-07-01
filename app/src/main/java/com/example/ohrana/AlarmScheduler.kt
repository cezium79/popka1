package com.example.ohrana

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import java.util.Calendar

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Главный метод автокоррекции. Принимает актуальный список маршрутов,
     * отменяет старые будильники и заводит новые.
     */
    fun updateAlarms(alarms: List<RouteAlarm>) {
        // 1. Сначала превентивно отменяем возможные старые будильники в системе (в пределах разумного пула номеров, например 20)
        for (i in 0..20) {
            cancelAlarm(i)
        }

        // 2. Настраиваем новые работающие будильники
        alarms.forEach { routeAlarm ->
            if (routeAlarm.isEnabled && isValidTime(routeAlarm.time)) {
                scheduleAlarm(routeAlarm)
            }
        }
    }

    private fun scheduleAlarm(alarm: RouteAlarm) {
        // Проверка для Android 12+ на право слать точные будильники
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Intent().apply {
                action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(this)
            }
            return
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarm.id)
            putExtra("ALARM_TIME", alarm.time as String) // Жестко передаем как String
        }

        // Уникальный PendingIntent для каждого ID маршрута
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = getCalendarForTime(alarm.time)

        try {
            // Устанавливаем максимально точный будильник, который сработает даже в режиме энергосбережения (Doze Mode)
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
            Log.d("AlarmScheduler", "Будильник №${alarm.id} установлен на ${alarm.time}")
        } catch (e: SecurityException) {
            Log.e("AlarmScheduler", "Не удалось установить точный будильник: ${e.message}")
        }
    }

    private fun cancelAlarm(id: Int) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    // Парсинг строки "HH:mm" в календарь на сегодня (или на завтра, если время уже прошло)
    private fun getCalendarForTime(time: String): Calendar {
        val parts = time.split(":")
        val hours = parts[0].toInt()
        val minutes = parts[1].toInt()

        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hours)
            set(Calendar.MINUTE, minutes)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // Если это время на сегодня уже прошло, переносим будильник на завтра
            if (before(Calendar.getInstance())) {
                add(Calendar.DATE, 1)
            }
        }
    }

    // Валидатор корректности ввода времени
    private fun isValidTime(time: String): Boolean {
        val regex = Regex("^([0-1]\\d|2[0-3]):[0-5]\\d$")
        return time.matches(regex)
    }
}


