package com.example.ohrana.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * Повторно используемая кнопка с тенями для всего приложения
 * По умолчанию использует ElevatedButton с тенями
 */
@Composable
fun OhranaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: androidx.compose.material3.ButtonColors = ButtonDefaults.buttonColors(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
        disabledContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        disabledContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    ),
    elevation: androidx.compose.material3.ButtonElevation = ButtonDefaults.buttonElevation(
        defaultElevation = 4.dp,
        pressedElevation = 8.dp,
        disabledElevation = 0.dp
    ),
    style: TextStyle? = null
) {
    ElevatedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        colors = colors,
        elevation = elevation
    ) {
        Text(
            text = text,
            style = style ?: androidx.compose.material3.MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * Кнопка-контейнер с тенями
 */
@Composable
fun OhranaContainedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: androidx.compose.material3.ButtonColors = ButtonDefaults.buttonColors(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
        disabledContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        disabledContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    ),
    elevation: androidx.compose.material3.ButtonElevation = ButtonDefaults.buttonElevation(
        defaultElevation = 4.dp,
        pressedElevation = 8.dp,
        disabledElevation = 0.dp
    ),
    style: TextStyle? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        colors = colors,
        elevation = elevation
    ) {
        Text(
            text = text,
            style = style ?: androidx.compose.material3.MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * Обычная текстовая кнопка с тенями
 */
@Composable
fun OhranaTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: androidx.compose.material3.ButtonColors = ButtonDefaults.buttonColors(
        containerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent
    ),
    elevation: androidx.compose.material3.ButtonElevation = ButtonDefaults.buttonElevation(
        defaultElevation = 2.dp,
        pressedElevation = 4.dp,
        disabledElevation = 0.dp
    ),
    style: TextStyle? = null
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        colors = colors,
        elevation = elevation
    ) {
        Text(
            text = text,
            style = style ?: androidx.compose.material3.MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * Обведенная кнопка с тенями
 */
@Composable
fun OhranaOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent
    ),
    elevation: ButtonElevation = ButtonDefaults.buttonElevation(
        defaultElevation = 2.dp,
        pressedElevation = 4.dp,
        disabledElevation = 0.dp
    ),
    style: TextStyle? = null
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        colors = colors,
        elevation = elevation
    ) {
        Text(
            text = text,
            style = style ?: androidx.compose.material3.MaterialTheme.typography.bodyLarge
        )
    }
}
