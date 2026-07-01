package com.example.ohrana

import android.os.Parcelable
import kotlinx.serialization.Serializable

// Модель для сохранения состояния каждого будильника
@Serializable
data class RouteAlarm(
    val id: Int,          // Уникальный ID для AlarmManager (например, индекс обхода)
    val time: String,     // Время в формате "HH:mm"
    val isEnabled: Boolean = true, // Флаг: активен будильник или игнорируется
    val routeId: String? = null // ID маршрута для этого обхода (опционально)
)
