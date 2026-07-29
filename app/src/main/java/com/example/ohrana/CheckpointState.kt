package com.example.ohrana

/**
 * Буфер состояния чекпоинта в памяти.
 * Используется для сбора всех данных до единого INSERT в БД.
 *
 * Новая стратегия:
 * Сканирование → CheckpointState (в памяти)
 * Диалог → заполнение полей CheckpointState
 * CheckpointPassedDialog → сохранение ВСЕГО в БД одним INSERT
 */
data class CheckpointState(
    val checkpointId: String,
    val checkpointName: String,
    val checkpointAction: CheckpointAction,
    val roundId: Int,
    val scanType: String,          // "QR" или "NFC"
    val scanTimestamp: String,     // "dd.MM.yyyy HH:mm:ss"
    val sequenceIndex: Int,
    val isSequenceCorrect: Boolean,
    val employeeName: String,      // Имя охранника, совершающего обход
    var answer: String? = null,           // QUESTION
    var inputValue: String? = null,       // INPUT
    var photoPath: String? = null,        // PHOTO
    var questionText: String? = null,     // QUESTION
    var inputTitle: String? = null,       // INPUT
    var hasAborted: Boolean = false       // Отказ от прохождения чекпоинта (нажата кнопка "Назад")
)
