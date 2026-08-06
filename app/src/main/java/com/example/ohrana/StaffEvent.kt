package com.example.ohrana

import java.util.UUID

/**
 * Тип события работника
 */
enum class EventType(val ruName: String) {
    ARRIVED_AT_FACILITY("Пришел на объект"),
    LEFT_FACILITY("Ушел с объекта"),
    SMELL_OF_ALCOHOL("Пришел с запахом алкоголя"),
    MISSED_WORK("Не вышел на работу"),
    NOTE("Примечание")
}

/**
 * Готовые шаблоны событий
 */
val PRESET_EVENTS = listOf(
    EventType.ARRIVED_AT_FACILITY,
    EventType.LEFT_FACILITY,
    EventType.SMELL_OF_ALCOHOL,
    EventType.MISSED_WORK
)

/**
 * Событие работника
 */
data class StaffEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: String,              // ISO формат времени
    val shiftId: String,                // ID активной смены
    val staffId: String,                // ID работника (Staff)
    val staffName: String,              // Имя работника (для отображения)
    val eventType: EventType,           // Тип события (шаблон или custom)
    val customText: String? = null,     // Произвольный текст (если eventType == CUSTOM)
    val templateText: String? = null    // Текст шаблона (для отображения)
)
