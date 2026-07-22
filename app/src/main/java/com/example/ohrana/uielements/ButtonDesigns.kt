package com.example.ohrana.uielements

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ohrana.ui.components.OhranaButton
import com.example.ohrana.ui.components.OhranaContainedButton
import com.example.ohrana.ui.components.OhranaOutlinedButton
import com.example.ohrana.ui.components.OhranaTextButton
import androidx.compose.ui.tooling.preview.Preview

/**
 * Централизованный файл для хранения дизайнов кнопок
 * Каждый дизайн имеет уникальный ID и набор параметров
 */

// ============================================
// ДИЗАЙНЫ КНОПОК
// ============================================

object ButtonDesigns {
    /**
     * Дизайн №1 - Стандартная кнопка (по умолчанию)
     * Цвет: Основной цвет темы
     * Тени: 4dp
     */
    val DESIGN_1_STANDARD = ButtonDesign(
        id = 1,                           // ID дизайна (уникальный номер)
        name = "Standard",                // Название дизайна
        containerColor = Color(0xFF6200EE),  // Цвет фона кнопки (активного состояния)
        contentColor = Color.White,       // Цвет текста и иконок на кнопке
        disabledContainerColor = Color(0xFFBDBDBD),  // Цвет фона в отключенном состоянии
        disabledContentColor = Color(0xFF9E9E9E),  // Цвет текста в отключенном состоянии
        elevationDefault = 4.dp,          // Тень в обычном состоянии
        elevationPressed = 8.dp,          // Тень при нажатии
        elevationDisabled = 0.dp,         // Тень в отключенном состоянии
        shapeCorner = 8.dp                // Радиус закругления углов
    )

    /**
     * Дизайн №2 - Контейнерная кнопка
     * Цвет: Акцентный цвет
     * Тени: 4dp
     */
    val DESIGN_2_CONTAINED = ButtonDesign(
        id = 2,                           // ID дизайна (уникальный номер)
        name = "Contained",               // Название дизайна
        containerColor = Color(0xFF6200EE),  // Цвет фона кнопки (активного состояния)
        contentColor = Color.White,       // Цвет текста и иконок на кнопке
        disabledContainerColor = Color(0xFFBDBDBD),  // Цвет фона в отключенном состоянии
        disabledContentColor = Color(0xFF9E9E9E),  // Цвет текста в отключенном состоянии
        elevationDefault = 4.dp,          // Тень в обычном состоянии
        elevationPressed = 8.dp,          // Тень при нажатии
        elevationDisabled = 0.dp,         // Тень в отключенном состоянии
        shapeCorner = 8.dp                // Радиус закругления углов
    )

    /**
     * Дизайн №3 - Обеченная кнопка (Recommended для основных действий)
     * Цвет: Прозрачный контейнер, цветной текст
     * Тени: 2dp
     */
    val DESIGN_3_OUTLINED = ButtonDesign(
        id = 3,                           // ID дизайна (уникальный номер)
        name = "Outlined",                // Название дизайна
        containerColor = Color(0xFF3A3737),  // Прозрачный фон кнопки
        contentColor = Color(0xFFE7E3EC),  // Цвет текста и иконок на кнопке
        disabledContainerColor = Color.Transparent,  // Прозрачный фон в отключенном состоянии
        disabledContentColor = Color(0xFF9E9E9E),  // Цвет текста в отключенном состоянии
        elevationDefault = 4.dp,          // Тень в обычном состоянии
        elevationPressed = 8.dp,          // Тень при нажатии
        elevationDisabled = 0.dp,         // Тень в отключенном состоянии
        shapeCorner = 20.dp                // Радиус закругления углов
    )

    /**
     * Дизайн №4 - Текстовая кнопка
     * Цвет: Серый фон без рамочки, как дизайн №3 но с непрозрачным фоном
     * Тени: 2dp
     */
    val DESIGN_4_TEXT = ButtonDesign(
        id = 4,                           // ID дизайна (уникальный номер)
        name = "Text",                    // Название дизайна
        containerColor = Color(0xFFBEBBBB),  // Серый фон кнопки (чтобы не сливалась с фоном)
        contentColor = Color(0xFFFFFFFF),  // Белый текст на сером фоне
        disabledContainerColor = Color(0xFFBDBDBD),  // Серый фон в отключенном состоянии
        disabledContentColor = Color(0xFF9E9E9E),  // Цвет текста в отключенном состоянии
        elevationDefault = 2.dp,          // Тень в обычном состоянии
        elevationPressed = 4.dp,          // Тень при нажатии
        elevationDisabled = 0.dp,         // Тень в отключенном состоянии
        shapeCorner = 20.dp                // Радиус закругления углов
    )

    /**
     * Дизайн №5 - Кнопка с тенями (Elevated)
     * Цвет: Основной цвет темы
     * Тени: 8dp (сильные тени)
     */
    val DESIGN_5_ELEVATED = ButtonDesign(
        id = 5,                           // ID дизайна (уникальный номер)
        name = "Elevated",                // Название дизайна
        containerColor = Color(0xFF6200EE),  // Цвет фона кнопки (активного состояния)
        contentColor = Color.White,       // Цвет текста и иконок на кнопке
        disabledContainerColor = Color(0xFFBDBDBD),  // Цвет фона в отключенном состоянии
        disabledContentColor = Color(0xFF9E9E9E),  // Цвет текста в отключенном состоянии
        elevationDefault = 8.dp,          // Сильная тень в обычном состоянии
        elevationPressed = 12.dp,         // Ещё сильная тень при нажатии
        elevationDisabled = 2.dp,         // Небольшая тень в отключенном состоянии
        shapeCorner = 8.dp                // Радиус закругления углов
    )

    /**
     * Дизайн №6 - Красная кнопка (для опасных действий)
     * Цвет: Красный
     * Тени: 4dp
     */
    val DESIGN_6_DESTRUCTIVE = ButtonDesign(
        id = 6,                           // ID дизайна (уникальный номер)
        name = "Destructive",             // Название дизайна
        containerColor = Color(0xFFB00020),  // Красный цвет фона кнопки
        contentColor = Color.White,       // Белый текст на красном фоне
        disabledContainerColor = Color(0xFFBDBDBD),  // Серый фона в отключенном состоянии
        disabledContentColor = Color(0xFF9E9E9E),  // Светло-серый текст
        elevationDefault = 4.dp,          // Тень в обычном состоянии
        elevationPressed = 8.dp,          // Тень при нажатии
        elevationDisabled = 0.dp,         // Тень в отключенном состоянии
        shapeCorner = 20.dp                // Радиус закругления углов
    )

    /**
     * Дизайн №7 - Зеленая кнопка (для успеха)
     * Цвет: Зеленый
     * Тени: 4dp
     */
    val DESIGN_7_SUCCESS = ButtonDesign(
        id = 7,                           // ID дизайна (уникальный номер)
        name = "Success",                 // Название дизайна
        containerColor = Color(0xFF4CAF50),  // Зеленый цвет фона кнопки
        contentColor = Color.White,       // Белый текст на зеленом фоне
        disabledContainerColor = Color(0xFFBDBDBD),  // Серый фона в отключенном состоянии
        disabledContentColor = Color(0xFF9E9E9E),  // Светло-серый текст
        elevationDefault = 5.dp,          // Тень в обычном состоянии
        elevationPressed = 8.dp,          // Тень при нажатии
        elevationDisabled = 0.dp,         // Тень в отключенном состоянии
        shapeCorner = 8.dp                // Радиус закругления углов
    )

    /**
     * Дизайн №8 - Синяя кнопка (для информации)
     * Цвет: Синий
     * Тени: 4dp
     */
    val DESIGN_8_INFO = ButtonDesign(
        id = 8,                           // ID дизайна (уникальный номер)
        name = "Info",                    // Название дизайна
        containerColor = Color(0xFF2196F3),  // Синий цвет фона кнопки
        contentColor = Color.White,       // Белый текст на синем фоне
        disabledContainerColor = Color(0xFFBDBDBD),  // Серый фона в отключенном состоянии
        disabledContentColor = Color(0xFF9E9E9E),  // Светло-серый текст
        elevationDefault = 4.dp,          // Тень в обычном состоянии
        elevationPressed = 8.dp,          // Тень при нажатии
        elevationDisabled = 0.dp,         // Тень в отключенном состоянии
        shapeCorner = 8.dp                // Радиус закругления углов
    )

    /**
     * Дизайн №9 - Золотая кнопка (для премиум/особых функций)
     * Цвет: Золотой
     * Тени: 6dp
     */
    val DESIGN_9_GOLD = ButtonDesign(
        id = 9,                           // ID дизайна (уникальный номер)
        name = "Gold",                    // Название дизайна
        containerColor = Color(0xFFFFD700),  // Золотой цвет фона кнопки
        contentColor = Color(0xFF333333),  // Темный текст на золотом фоне
        disabledContainerColor = Color(0xFFBDBDBD),  // Серый фона в отключенном состоянии
        disabledContentColor = Color(0xFF9E9E9E),  // Светло-серый текст
        elevationDefault = 6.dp,          // Тень в обычном состоянии
        elevationPressed = 10.dp,         // Тень при нажатии
        elevationDisabled = 2.dp,         // Небольшая тень в отключенном состоянии
        shapeCorner = 8.dp                // Радиус закругления углов
    )

    /**
     * Дизайн №10 - Черная кнопка (для темной темы/контраста)
     * Цвет: Черный
     * Тени: 4dp
     */
    val DESIGN_10_DARK = ButtonDesign(
        id = 10,                          // ID дизайна (уникальный номер)
        name = "Dark",                    // Название дизайна
        containerColor = Color(0xFF1A1A1A),  // Черный цвет фона кнопки
        contentColor = Color.White,       // Белый текст на черном фоне
        disabledContainerColor = Color(0xFF424242),  // Серый фона в отключенном состоянии
        disabledContentColor = Color(0xFF9E9E9E),  // Светло-серый текст
        elevationDefault = 4.dp,          // Тень в обычном состоянии
        elevationPressed = 8.dp,          // Тень при нажатии
        elevationDisabled = 0.dp,         // Тень в отключенном состоянии
        shapeCorner = 8.dp                // Радиус закругления углов
    )
}

/**
 * Класс для хранения параметров дизайна кнопки
 * Используем Dp вместо ButtonElevation, так как ButtonElevation internal
 * 
 * КОНСТРУКТОР:
 * @param id - Уникальный идентификатор дизайна
 * @param name - Название дизайна
 * @param containerColor - Цвет фона кнопки (активного состояния)
 * @param contentColor - Цвет текста и иконок на кнопке
 * @param disabledContainerColor - Цвет фона в отключенном состоянии
 * @param disabledContentColor - Цвет текста в отключенном состоянии
 * @param elevationDefault - Тень в обычном состоянии
 * @param elevationPressed - Тень при нажатии
 * @param elevationDisabled - Тень в отключенном состоянии
 * @param shapeCorner - Радиус закругления углов
 */
data class ButtonDesign(
    val id: Int,                              // Уникальный идентификатор дизайна
    val name: String,                         // Название дизайна
    val containerColor: Color,                // Цвет фона кнопки (активного состояния)
    val contentColor: Color,                  // Цвет текста и иконок на кнопке
    val disabledContainerColor: Color,        // Цвет фона в отключенном состоянии
    val disabledContentColor: Color,          // Цвет текста в отключенном состоянии
    val elevationDefault: androidx.compose.ui.unit.Dp,  // Тень в обычном состоянии
    val elevationPressed: androidx.compose.ui.unit.Dp,  // Тень при нажатии
    val elevationDisabled: androidx.compose.ui.unit.Dp, // Тень в отключенном состоянии
    val shapeCorner: androidx.compose.ui.unit.Dp        // Радиус закругления углов
)

// ============================================
// ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ
// ============================================

/**
 * Получить дизайн по ID
 * 
 * @param id - Уникальный идентификатор дизайна (1-10)
 * @return - Дизайн кнопки или null если ID не найден
 */
fun getButtonDesignById(id: Int): ButtonDesign? {
    return when (id) {
        1 -> ButtonDesigns.DESIGN_1_STANDARD
        2 -> ButtonDesigns.DESIGN_2_CONTAINED
        3 -> ButtonDesigns.DESIGN_3_OUTLINED
        4 -> ButtonDesigns.DESIGN_4_TEXT
        5 -> ButtonDesigns.DESIGN_5_ELEVATED
        6 -> ButtonDesigns.DESIGN_6_DESTRUCTIVE
        7 -> ButtonDesigns.DESIGN_7_SUCCESS
        8 -> ButtonDesigns.DESIGN_8_INFO
        9 -> ButtonDesigns.DESIGN_9_GOLD
        10 -> ButtonDesigns.DESIGN_10_DARK
        else -> ButtonDesigns.DESIGN_1_STANDARD
    }
}

/**
 * Получить дизайн по названию
 */
fun getButtonDesignByName(name: String): ButtonDesign? {
    return when (name.lowercase()) {
        "standard", "1" -> ButtonDesigns.DESIGN_1_STANDARD
        "contained", "2" -> ButtonDesigns.DESIGN_2_CONTAINED
        "outlined", "3" -> ButtonDesigns.DESIGN_3_OUTLINED
        "text", "4" -> ButtonDesigns.DESIGN_4_TEXT
        "elevated", "5" -> ButtonDesigns.DESIGN_5_ELEVATED
        "destructive", "6" -> ButtonDesigns.DESIGN_6_DESTRUCTIVE
        "success", "7" -> ButtonDesigns.DESIGN_7_SUCCESS
        "info", "8" -> ButtonDesigns.DESIGN_8_INFO
        "gold", "9" -> ButtonDesigns.DESIGN_9_GOLD
        "dark", "10" -> ButtonDesigns.DESIGN_10_DARK
        else -> ButtonDesigns.DESIGN_1_STANDARD
    }
}

/**
 * Получить все доступные дизайны
 */
fun getAllButtonDesigns(): List<ButtonDesign> {
    return listOf(
        ButtonDesigns.DESIGN_1_STANDARD,
        ButtonDesigns.DESIGN_2_CONTAINED,
        ButtonDesigns.DESIGN_3_OUTLINED,
        ButtonDesigns.DESIGN_4_TEXT,
        ButtonDesigns.DESIGN_5_ELEVATED,
        ButtonDesigns.DESIGN_6_DESTRUCTIVE,
        ButtonDesigns.DESIGN_7_SUCCESS,
        ButtonDesigns.DESIGN_8_INFO,
        ButtonDesigns.DESIGN_9_GOLD,
        ButtonDesigns.DESIGN_10_DARK
    )
}

// ============================================
// PREVIEW: ПРЕДПРОСМОТР ВСЕХ ДИЗАЙНОВ КНОПОК
// ============================================

/**
 * Предпросмотр всех дизайнов кнопок
 * Вызовите OhranaButtonPreview() в preview-функции для просмотра всех дизайнов
 */
@Composable
fun ButtonDesignsPreview() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Дизайны кнопок",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        // Дизайн 1 - Standard
        Text(text = "Дизайн №1 - Standard", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        OhranaButton(
            text = "Standard Button",
            onClick = {},
            designId = 1
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        // Дизайн 2 - Contained
        Text(text = "Дизайн №2 - Contained", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        OhranaContainedButton(
            text = "Contained Button",
            onClick = {},
            designId = 2
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        // Дизайн 3 - Outlined (Recommended)
        Text(text = "Дизайн №3 - Outlined (Recommended)", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        OhranaOutlinedButton(
            text = "Outlined Button",
            onClick = {},
            designId = 3
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        // Дизайн 4 - Text
        Text(text = "Дизайн №4 - Text", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        OhranaTextButton(
            text = "Text Button",
            onClick = {},
            designId = 4
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        // Дизайн 5 - Elevated
        Text(text = "Дизайн №5 - Elevated", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        OhranaButton(
            text = "Elevated Button",
            onClick = {},
            designId = 5
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        // Дизайн 6 - Destructive
        Text(text = "Дизайн №6 - Destructive", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        OhranaButton(
            text = "Destructive Button",
            onClick = {},
            designId = 6
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        // Дизайн 7 - Success
        Text(text = "Дизайн №7 - Success", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        OhranaButton(
            text = "Success Button",
            onClick = {},
            designId = 7
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        // Дизайн 8 - Info
        Text(text = "Дизайн №8 - Info", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        OhranaButton(
            text = "Info Button",
            onClick = {},
            designId = 8
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        // Дизайн 9 - Gold
        Text(text = "Дизайн №9 - Gold", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        OhranaButton(
            text = "Gold Button",
            onClick = {},
            designId = 9
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        // Дизайн 10 - Dark
        Text(text = "Дизайн №10 - Dark", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        OhranaButton(
            text = "Dark Button",
            onClick = {},
            designId = 10
        )
    }
}

// ============================================
// PREVIEW FUNCTIONS (для Android Studio Preview)
// ============================================

/**
 * Preview для просмотра всех дизайнов кнопок
 * Используйте эту функцию для предпросмотра в Android Studio
 */
@Preview(showBackground = true, backgroundColor = 0xFFFFFF)
@Composable
fun ButtonDesignsAllPreview() {
    androidx.compose.material3.MaterialTheme {
        ButtonDesignsPreview()
    }
}
