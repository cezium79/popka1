package com.example.ohrana.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.example.ohrana.uielements.ButtonDesign
import com.example.ohrana.uielements.getButtonDesignById

/**
 * Обновленная кнопка с использованием централизованного дизайна
 * @param text Текст кнопки
 * @param onClick Обработчик нажатия
 * @param modifier Модификатор
 * @param enabled Доступна ли кнопка
 * @param colors Цвета кнопки (если null, используется дизайн по ID)
 * @param elevation Тени (если null, используется дизайн по ID)
 * @param shape Форма (если null, используется дизайн по ID)
 * @param style Стиль текста
 * @param designId ID дизайна из ButtonDesigns (приоритетнее параметров цвета и теней)
 */
@Composable
fun OhranaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors? = null,
    elevation: androidx.compose.material3.ButtonElevation? = null,
    shape: Shape? = null,
    style: TextStyle? = null,
    designId: Int? = null
) {
    val design = designId?.let { getButtonDesignById(it) }
    
    val finalColors = colors ?: design?.let {
        ButtonDefaults.buttonColors(
            containerColor = it.containerColor,
            contentColor = it.contentColor,
            disabledContainerColor = it.disabledContainerColor,
            disabledContentColor = it.disabledContentColor
        )
    } ?: ButtonDefaults.buttonColors()
    
    val finalElevation = elevation ?: design?.let {
        ButtonDefaults.buttonElevation(
            defaultElevation = it.elevationDefault,
            pressedElevation = it.elevationPressed,
            disabledElevation = it.elevationDisabled
        )
    } ?: ButtonDefaults.buttonElevation()
    
    val finalShape = shape ?: design?.let {
        RoundedCornerShape(it.shapeCorner)
    } ?: RoundedCornerShape(8.dp)
    
    ElevatedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = finalColors,
        elevation = finalElevation,
        shape = finalShape
    ) {
        Text(
            text = text,
            style = style ?: androidx.compose.material3.MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * Обновленная контейнерная кнопка с использованием централизованного дизайна
 * @param designId ID дизайна из ButtonDesigns
 */
@Composable
fun OhranaContainedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors? = null,
    elevation: androidx.compose.material3.ButtonElevation? = null,
    shape: Shape? = null,
    style: TextStyle? = null,
    designId: Int? = null
) {
    val design = designId?.let { getButtonDesignById(it) }
    
    val finalColors = colors ?: design?.let {
        ButtonDefaults.buttonColors(
            containerColor = it.containerColor,
            contentColor = it.contentColor,
            disabledContainerColor = it.disabledContainerColor,
            disabledContentColor = it.disabledContentColor
        )
    } ?: ButtonDefaults.buttonColors()
    
    val finalElevation = elevation ?: design?.let {
        ButtonDefaults.buttonElevation(
            defaultElevation = it.elevationDefault,
            pressedElevation = it.elevationPressed,
            disabledElevation = it.elevationDisabled
        )
    } ?: ButtonDefaults.buttonElevation()
    
    val finalShape = shape ?: design?.let {
        RoundedCornerShape(it.shapeCorner)
    } ?: RoundedCornerShape(8.dp)
    
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = finalColors,
        elevation = finalElevation,
        shape = finalShape
    ) {
        Text(
            text = text,
            style = style ?: androidx.compose.material3.MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * Обновленная текстовая кнопка с использованием централизованного дизайна
 * @param designId ID дизайна из ButtonDesigns
 */
@Composable
fun OhranaTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors? = null,
    elevation: androidx.compose.material3.ButtonElevation? = null,
    shape: Shape? = null,
    style: TextStyle? = null,
    designId: Int? = null
) {
    val design = designId?.let { getButtonDesignById(it) }
    
    val finalColors = colors ?: design?.let {
        ButtonDefaults.buttonColors(
            containerColor = it.containerColor,
            contentColor = it.contentColor,
            disabledContainerColor = it.disabledContainerColor,
            disabledContentColor = it.disabledContentColor
        )
    } ?: ButtonDefaults.buttonColors()
    
    val finalElevation = elevation ?: design?.let {
        ButtonDefaults.buttonElevation(
            defaultElevation = it.elevationDefault,
            pressedElevation = it.elevationPressed,
            disabledElevation = it.elevationDisabled
        )
    } ?: ButtonDefaults.buttonElevation()
    
    val finalShape = shape ?: design?.let {
        RoundedCornerShape(it.shapeCorner)
    } ?: RoundedCornerShape(8.dp)
    
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = finalColors,
        elevation = finalElevation,
        shape = finalShape
    ) {
        Text(
            text = text,
            style = style ?: androidx.compose.material3.MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * Обновленная обеченная кнопка с использованием централизованного дизайна
 * @param designId ID дизайна из ButtonDesigns
 */
@Composable
fun OhranaOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors? = null,
    elevation: androidx.compose.material3.ButtonElevation? = null,
    shape: Shape? = null,
    style: TextStyle? = null,
    designId: Int? = null
) {
    val design = designId?.let { getButtonDesignById(it) }
    
    val finalColors = colors ?: design?.let {
        ButtonDefaults.buttonColors(
            containerColor = it.containerColor,
            contentColor = it.contentColor,
            disabledContainerColor = it.disabledContainerColor,
            disabledContentColor = it.disabledContentColor
        )
    } ?: ButtonDefaults.buttonColors()
    
    val finalElevation = elevation ?: design?.let {
        ButtonDefaults.buttonElevation(
            defaultElevation = it.elevationDefault,
            pressedElevation = it.elevationPressed,
            disabledElevation = it.elevationDisabled
        )
    } ?: ButtonDefaults.buttonElevation()
    
    val finalShape = shape ?: design?.let {
        RoundedCornerShape(it.shapeCorner)
    } ?: RoundedCornerShape(8.dp)
    
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = finalColors,
        elevation = finalElevation,
        shape = finalShape
    ) {
        Text(
            text = text,
            style = style ?: androidx.compose.material3.MaterialTheme.typography.bodyLarge
        )
    }
}
