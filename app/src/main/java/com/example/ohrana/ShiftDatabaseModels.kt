package com.example.ohrana

import java.util.Date

// ============================================
// БАЗА ДАННЫХ ДЛЯ ХРАНЕНИЯ ИНФОРМАЦИИ ОБ ОБХОДАХ
// ============================================

/**
 * Модель записи об обходе (чекпоинт)
 * Хранит всю информацию о прохождении точки:
 * - Какой чекпоинт
 * - Какой результат (пройден, вопрос, ввод, фото)
 * - Статус последовательности (правильно/нарушение)
 * - Дополнительные данные (ответы, показания, фото)
 */
data class ShiftLogEntry(
    val id: String,                      // Уникальный ID записи
    val timestamp: String,               // Время прохождения (ISO формат)
    val checkpointName: String,          // Название чекпоинта
    val checkpointId: String,            // ID чекпоинта
    val employeeName: String,            // Имя охранника
    val roundId: Int,                    // ID обхода (номер будильника)
    val shiftId: String,                 // ID смены
    val routeName: String,               // Название маршрута
    val sequenceIndex: Int,              // Индекс в маршруте (какой по счету)
    val isSequenceCorrect: Boolean,      // Правильная ли последовательность
    val scanType: String,                // Тип сканирования (QR/NFC)
    val actionType: String,              // Тип действия (CHECKPOINT/QUESTION/INPUT/PHOTO)
    val answer: String? = null,          // Ответ на вопрос (если есть)
    val inputValue: String? = null,      // Введенное значение (если есть)
    val photoPath: String? = null,       // Путь к фото (если есть)
    val latitude: Double? = null,        // Широта (опционально)
    val longitude: Double? = null,       // Долгота (опционально)
    val sequenceErrorExpected: String? = null  // Какой чекпоинт был ожидаем (при ошибке)
)

/**
 * Модель смены
 * Хранит общую информацию о смене
 */
data class ShiftRecord(
    val id: String,                      // Уникальный ID смены
    val employeeName: String,            // Имя сотрудника
    val startTime: String,               // Время начала смены
    val endTime: String? = null,         // Время окончания смены (null если активна)
    val isShiftActive: Boolean = false,  // Активна ли смена
    val strictSequenceEnabled: Boolean = false  // Включен ли строгий контроль
)

/**
 * Модель обхода (round)
 * Хранит информацию о конкретном обходе
 */
data class RoundRecord(
    val id: Int,                         // ID обхода (номер будильника)
    val shiftId: String,                 // ID смены
    val startTime: String,               // Время начала обхода
    val endTime: String? = null,         // Время окончания обхода
    val isCompleted: Boolean = false,    // Завершен ли обход
    val routeId: String? = null,         // ID маршрута
    val routeName: String? = null,       // Название маршрута
    val checkpointsCount: Int = 0,       // Количество чекпоинтов в маршруте
    val checkpointsPassed: Int = 0,      // Количество пройденных чекпоинтов
    val sequenceViolations: Int = 0      // Количество нарушений последовательности
)

/**
 * Модель ошибки последовательности
 * Сохраняет информацию о нарушении очередности
 */
data class SequenceViolation(
    val id: String,
    val timestamp: String,
    val employeeName: String,
    val roundId: Int,
    val expectedCheckpointId: String,
    val expectedCheckpointName: String,
    val actualCheckpointId: String,
    val actualCheckpointName: String,
    val isNfc: Boolean = false
)

// ============================================
// ФОРМАТЫ ДАННЫХ ДЛЯ ОТЧЕТОВ
// ============================================

/**
 * Формат отчета для администратора (полный)
 */
data class AdminReport(
    val shift: ShiftRecord,
    val rounds: List<RoundRecord>,
    val logs: List<ShiftLogEntry>,
    val violations: List<SequenceViolation>
)

/**
 * Формат отчета для охранника (упрощенный)
 */
data class GuardReport(
    val shiftStartTime: String,
    val shiftEndTime: String?,
    val roundsCount: Int,
    val roundsCompleted: Int,
    val checkpointsTotal: Int,
    val checkpointsPassed: Int,
    val sequenceViolations: Int,
    val logs: List<SimpleLogEntry>
)

/**
 * Упрощенная запись лога для охранника
 */
data class SimpleLogEntry(
    val time: String,
    val checkpointName: String,
    val result: String
)

// ============================================
// СТАТУСЫ И ТИПЫ
// ============================================

enum class ScanType {
    QR, NFC, MANUAL
}

enum class ActionType {
    CHECKPOINT, QUESTION, INPUT, PHOTO
}
