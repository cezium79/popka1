package com.example.ohrana.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Глянцевая кнопка с градиентом, тенью и выраженным верхним бликом
 */
@Composable
fun GlossyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    // Синяя глянцевая палитра
    val startColor = Color(0xFF003FB4)
    val endColor = Color(0xFF001A67)
    val shadowColor = Color(0x7F001F5C)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            // 1. Отрисовка внешней объемной тени
            .drawBehind {
                drawIntoCanvas { canvas ->
                    val paint = Paint().apply {
                        style = PaintingStyle.Fill
                        color = shadowColor
                    }
                    val frameworkPaint = paint.asFrameworkPaint()
                    frameworkPaint.setShadowLayer(
                        15.dp.toPx(),  // Размытие
                        0f,            // Смещение X
                        8.dp.toPx(),   // Смещение Y (вниз)
                        shadowColor.toArgb()
                    )

                    canvas.drawRoundRect(
                        left = 0f,
                        top = 0f,
                        right = size.width,
                        bottom = size.height,
                        radiusX = size.height / 2,
                        radiusY = size.height / 2,
                        paint = paint
                    )
                }
            }
            // 2. Скругление углов формы
            .clip(CircleShape)
            // 3. Основной глянцевый вертикальный градиент подложки
            .background(
                Brush.verticalGradient(
                    colors = listOf(startColor, endColor)
                )
            )
            // 4. Гарантированный яркий блик по верхнему контуру капсулы
            .drawBehind {
                val strokeWidthPx = 2.5.dp.toPx()
                val radius = size.height / 2

                // Рисуем светящуюся линию на верхней половине левой и правой окружности + верхняя грань
                val path = Path().apply {
                    // Начинаем с левой середины кнопки
                    moveTo(radius, strokeWidthPx / 2)
                    // Линия до правой середины
                    lineTo(size.width - radius, strokeWidthPx / 2)
                    // Дуга справа налево по верхней части
                    arcTo(
                        rect = Rect(
                            left = size.width - size.height,
                            top = strokeWidthPx / 2,
                            right = size.width - strokeWidthPx / 2,
                            bottom = size.height - strokeWidthPx / 2
                        ),
                        startAngleDegrees = -90f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    // Возвращаемся дугой слева
                    arcTo(
                        rect = Rect(
                            left = strokeWidthPx / 2,
                            top = strokeWidthPx / 2,
                            right = size.height,
                            bottom = size.height - strokeWidthPx / 2
                        ),
                        startAngleDegrees = 180f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                }

                // Отрисовка самого блика с мягким градиентным угасанием сверху вниз
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.65f), // Сделали блик ярче
                            Color.White.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = size.height / 2 // Блик затухает к середине высоты кнопки
                    ),
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )
            }
            // 5. Обработка клика
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 48.dp, vertical = 16.dp)
    ) {
        Text(
            text = text.uppercase(),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
    }
}
