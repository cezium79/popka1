package com.example.ohrana.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.ohrana.uielements.CardDesign
import com.example.ohrana.uielements.getCardDesignById

/**
 * Компонент карточки с поддержкой централизованных дизайнов
 * @param modifier Модификатор
 * @param designId ID дизайна из CardDesigns
 * @param onClick Обработчик клика (если карточка кликабельная)
 * @param content Содержимое карточки
 */
@Composable
fun OhranaCard(
    modifier: Modifier = Modifier,
    designId: Int? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val design = designId?.let { getCardDesignById(it) }
    
    val containerColor = design?.containerColor ?: Color.White
    val contentColor = design?.contentColor ?: Color.Black
    val elevation = design?.let {
        CardDefaults.cardElevation(
            defaultElevation = it.elevationDefault,
            pressedElevation = it.elevationPressed,
            hoveredElevation = it.elevationHovered
        )
    } ?: CardDefaults.cardElevation()
    val shape = design?.let {
        if (it.shapeCorner == 0.dp) {
            CircleShape
        } else {
            RoundedCornerShape(it.shapeCorner)
        }
    } ?: RoundedCornerShape(8.dp)
    
    // Если карточка кликабельная, используем ClickableCard
    if (onClick != null) {
        androidx.compose.material3.Card(
            modifier = modifier.clickable(onClick = onClick),
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor
            ),
            elevation = elevation,
            shape = shape
        ) {
            content()
        }
    } else {
        // Обычная карточка
        androidx.compose.material3.Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor
            ),
            elevation = elevation,
            shape = shape
        ) {
            content()
        }
    }
}

/**
 * Упрощенная карточка для списков (без клика)
 */
@Composable
fun OhranaListCard(
    modifier: Modifier = Modifier,
    designId: Int? = null,
    content: @Composable () -> Unit
) {
    OhranaCard(modifier = modifier, designId = designId, content = content)
}

/**
 * Кликабельная карточка
 */
@Composable
fun OhranaClickableCard(
    modifier: Modifier = Modifier,
    designId: Int? = null,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    OhranaCard(modifier = modifier, designId = designId, onClick = onClick, content = content)
}
