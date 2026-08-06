package com.example.ohrana

import java.util.UUID

/**
 * Работник (не являющийся сотрудником охраны)
 * Используется для учёта персонала, который взаимодействует с объектом охраны,
 * но не проходит обходы
 */
data class Staff(
    val id: String = UUID.randomUUID().toString(),
    val tabNumber: String = "",      // Табельный номер
    val lastName: String = "",        // Фамилия
    val firstName: String = "",       // Имя
    val middleName: String = "",      // Отчество
    val position: String = ""         // Должность
) {
    val fullName: String
        get() = "$lastName $firstName $middleName".trim()
}
