package com.example.ohrana.uielements

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Централизованный файл для хранения дизайнов карточек и свитчей
 */

// ============================================
// ДИЗАЙНЫ КАРТОЧЕК
// ============================================

object CardDesigns {
    /**
     * Дизайн №1 - Стандартная карточка
     * Цвет: Белый
     * Тени: 2dp
     * Углы: 8dp
     */
    val DESIGN_1_STANDARD = CardDesign(
        id = 1,
        name = "Standard",
        containerColor = Color.White,
        contentColor = Color.Black,
        elevationDefault = 2.dp,
        elevationPressed = 4.dp,
        elevationHovered = 4.dp,
        shapeCorner = 8.dp
    )

    /**
     * Дизайн №2 - Карточка с тенями
     * Цвет: Белый
     * Тени: 4dp
     * Углы: 12dp
     */
    val DESIGN_2_ELEVATED = CardDesign(
        id = 2,
        name = "Elevated",
        containerColor = Color.White,
        contentColor = Color.Black,
        elevationDefault = 4.dp,
        elevationPressed = 8.dp,
        elevationHovered = 6.dp,
        shapeCorner = 12.dp
    )

    /**
     * Дизайн №3 - Обеченная карточка
     * Цвет: Белый
     * Тени: 0dp
     * Углы: 8dp
     */
    val DESIGN_3_OUTLINED = CardDesign(
        id = 3,
        name = "Outlined",
        containerColor = Color.White,
        contentColor = Color.Black,
        elevationDefault = 0.dp,
        elevationPressed = 0.dp,
        elevationHovered = 0.dp,
        shapeCorner = 8.dp
    )

    /**
     * Дизайн №4 - Карточка с акцентом
     * Цвет: Акцентный
     * Тени: 4dp
     * Углы: 12dp
     */
    val DESIGN_4_ACCENT = CardDesign(
        id = 4,
        name = "Accent",
        containerColor = Color(0xFF6200EE),
        contentColor = Color.White,
        elevationDefault = 4.dp,
        elevationPressed = 8.dp,
        elevationHovered = 6.dp,
        shapeCorner = 12.dp
    )

    /**
     * Дизайн №5 - Темная карточка
     * Цвет: Темный
     * Тени: 2dp
     * Углы: 8dp
     */
    val DESIGN_5_DARK = CardDesign(
        id = 5,
        name = "Dark",
        containerColor = Color(0xFF1A1A1A),
        contentColor = Color.White,
        elevationDefault = 2.dp,
        elevationPressed = 4.dp,
        elevationHovered = 4.dp,
        shapeCorner = 8.dp
    )

    /**
     * Дизайн №6 - Светлая карточка
     * Цвет: Очень светлый серый
     * Тени: 1dp
     * Углы: 6dp
     */
    val DESIGN_6_LIGHT = CardDesign(
        id = 6,
        name = "Light",
        containerColor = Color(0xFFF5F5F5),
        contentColor = Color(0xFF333333),
        elevationDefault = 1.dp,
        elevationPressed = 2.dp,
        elevationHovered = 2.dp,
        shapeCorner = 6.dp
    )

    /**
     * Дизайн №7 - Закругленная карточка
     * Цвет: Белый
     * Тени: 3dp
     * Углы: 20dp (очень закругленные)
     */
    val DESIGN_7_ROUNDED = CardDesign(
        id = 7,
        name = "Rounded",
        containerColor = Color.White,
        contentColor = Color.Black,
        elevationDefault = 3.dp,
        elevationPressed = 6.dp,
        elevationHovered = 5.dp,
        shapeCorner = 20.dp
    )

    /**
     * Дизайн №8 - Круглая карточка (для иконок)
     * Цвет: Белый
     * Тени: 4dp
     * Форма: Circle
     */
    val DESIGN_8_CIRCLE = CardDesign(
        id = 8,
        name = "Circle",
        containerColor = Color.White,
        contentColor = Color.Black,
        elevationDefault = 4.dp,
        elevationPressed = 8.dp,
        elevationHovered = 6.dp,
        shapeCorner = 0.dp
    )
}

/**
 * Класс для хранения параметров дизайна карточки
 */
data class CardDesign(
    val id: Int,
    val name: String,
    val containerColor: Color,
    val contentColor: Color,
    val elevationDefault: androidx.compose.ui.unit.Dp,
    val elevationPressed: androidx.compose.ui.unit.Dp,
    val elevationHovered: androidx.compose.ui.unit.Dp,
    val shapeCorner: androidx.compose.ui.unit.Dp
)

// ============================================
// ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ КАРТОЧЕК
// ============================================

/**
 * Получить дизайн карточки по ID
 */
fun getCardDesignById(id: Int): CardDesign? {
    return when (id) {
        1 -> CardDesigns.DESIGN_1_STANDARD
        2 -> CardDesigns.DESIGN_2_ELEVATED
        3 -> CardDesigns.DESIGN_3_OUTLINED
        4 -> CardDesigns.DESIGN_4_ACCENT
        5 -> CardDesigns.DESIGN_5_DARK
        6 -> CardDesigns.DESIGN_6_LIGHT
        7 -> CardDesigns.DESIGN_7_ROUNDED
        8 -> CardDesigns.DESIGN_8_CIRCLE
        else -> CardDesigns.DESIGN_1_STANDARD
    }
}

/**
 * Получить дизайн карточки по названию
 */
fun getCardDesignByName(name: String): CardDesign? {
    return when (name.lowercase()) {
        "standard", "1" -> CardDesigns.DESIGN_1_STANDARD
        "elevated", "2" -> CardDesigns.DESIGN_2_ELEVATED
        "outlined", "3" -> CardDesigns.DESIGN_3_OUTLINED
        "accent", "4" -> CardDesigns.DESIGN_4_ACCENT
        "dark", "5" -> CardDesigns.DESIGN_5_DARK
        "light", "6" -> CardDesigns.DESIGN_6_LIGHT
        "rounded", "7" -> CardDesigns.DESIGN_7_ROUNDED
        "circle", "8" -> CardDesigns.DESIGN_8_CIRCLE
        else -> CardDesigns.DESIGN_1_STANDARD
    }
}

/**
 * Получить все доступные дизайны карточек
 */
fun getAllCardDesigns(): List<CardDesign> {
    return listOf(
        CardDesigns.DESIGN_1_STANDARD,
        CardDesigns.DESIGN_2_ELEVATED,
        CardDesigns.DESIGN_3_OUTLINED,
        CardDesigns.DESIGN_4_ACCENT,
        CardDesigns.DESIGN_5_DARK,
        CardDesigns.DESIGN_6_LIGHT,
        CardDesigns.DESIGN_7_ROUNDED,
        CardDesigns.DESIGN_8_CIRCLE
    )
}

// ============================================
// ДИЗАЙНЫ СВИТЧЕЙ
// ============================================

object SwitchDesigns {
    /**
     * Дизайн №1 - Стандартный свитч
     * Цвет включенного: Основной цвет темы
     * Цвет выключенного: Серый
     */
    val DESIGN_1_STANDARD = SwitchDesign(
        id = 1,
        name = "Standard",
        checkedTrackColor = Color(0xFF6200EE),
        checkedThumbColor = Color.White,
        uncheckedTrackColor = Color(0xFF9E9E9E),
        uncheckedThumbColor = Color.White
    )

    /**
     * Дизайн №2 - Яркий свитч
     * Цвет включенного: Зеленый
     * Цвет выключенного: Серый
     */
    val DESIGN_2_BRIGHT = SwitchDesign(
        id = 2,
        name = "Bright",
        checkedTrackColor = Color(0xFF4CAF50),
        checkedThumbColor = Color.White,
        uncheckedTrackColor = Color(0xFF9E9E9E),
        uncheckedThumbColor = Color.White
    )

    /**
     * Дизайн №3 - Темный свитч
     * Цвет включенного: Синий
     * Цвет выключенного: Темно-серый
     */
    val DESIGN_3_DARK = SwitchDesign(
        id = 3,
        name = "Dark",
        checkedTrackColor = Color(0xFF2196F3),
        checkedThumbColor = Color.White,
        uncheckedTrackColor = Color(0xFF424242),
        uncheckedThumbColor = Color(0xFFBDBDBD)
    )

    /**
     * Дизайн №4 - Золотой свитч
     * Цвет включенного: Золотой
     * Цвет выключенного: Темно-серый
     */
    val DESIGN_4_GOLD = SwitchDesign(
        id = 4,
        name = "Gold",
        checkedTrackColor = Color(0xFFFFD700),
        checkedThumbColor = Color(0xFF333333),
        uncheckedTrackColor = Color(0xFF424242),
        uncheckedThumbColor = Color(0xFF9E9E9E)
    )

    /**
     * Дизайн №5 - Красный свитч (для опасных действий)
     * Цвет включенного: Красный
     * Цвет выключенного: Серый
     */
    val DESIGN_5_DESTRUCTIVE = SwitchDesign(
        id = 5,
        name = "Destructive",
        checkedTrackColor = Color(0xFFB00020),
        checkedThumbColor = Color.White,
        uncheckedTrackColor = Color(0xFF9E9E9E),
        uncheckedThumbColor = Color.White
    )
}

/**
 * Класс для хранения параметров дизайна свитча
 */
data class SwitchDesign(
    val id: Int,
    val name: String,
    val checkedTrackColor: Color,
    val checkedThumbColor: Color,
    val uncheckedTrackColor: Color,
    val uncheckedThumbColor: Color
)

// ============================================
// ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ СВИТЧЕЙ
// ============================================

/**
 * Получить дизайн свитча по ID
 */
fun getSwitchDesignById(id: Int): SwitchDesign? {
    return when (id) {
        1 -> SwitchDesigns.DESIGN_1_STANDARD
        2 -> SwitchDesigns.DESIGN_2_BRIGHT
        3 -> SwitchDesigns.DESIGN_3_DARK
        4 -> SwitchDesigns.DESIGN_4_GOLD
        5 -> SwitchDesigns.DESIGN_5_DESTRUCTIVE
        else -> SwitchDesigns.DESIGN_1_STANDARD
    }
}

/**
 * Получить дизайн свитча по названию
 */
fun getSwitchDesignByName(name: String): SwitchDesign? {
    return when (name.lowercase()) {
        "standard", "1" -> SwitchDesigns.DESIGN_1_STANDARD
        "bright", "2" -> SwitchDesigns.DESIGN_2_BRIGHT
        "dark", "3" -> SwitchDesigns.DESIGN_3_DARK
        "gold", "4" -> SwitchDesigns.DESIGN_4_GOLD
        "destructive", "5" -> SwitchDesigns.DESIGN_5_DESTRUCTIVE
        else -> SwitchDesigns.DESIGN_1_STANDARD
    }
}

/**
 * Получить все доступные дизайны свитчей
 */
fun getAllSwitchDesigns(): List<SwitchDesign> {
    return listOf(
        SwitchDesigns.DESIGN_1_STANDARD,
        SwitchDesigns.DESIGN_2_BRIGHT,
        SwitchDesigns.DESIGN_3_DARK,
        SwitchDesigns.DESIGN_4_GOLD,
        SwitchDesigns.DESIGN_5_DESTRUCTIVE
    )
}
