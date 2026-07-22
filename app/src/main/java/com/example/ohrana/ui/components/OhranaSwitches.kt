package com.example.ohrana.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ohrana.uielements.SwitchDesign
import com.example.ohrana.uielements.getSwitchDesignById

/**
 * Компонент свитча с поддержкой централизованных дизайнов
 * @param checked Состояние вкл/выкл
 * @param onCheckedChange Обработчик изменения состояния
 * @param modifier Модификатор
 * @param designId ID дизайна из SwitchDesigns
 * @param enabled Доступен ли свитч
 */
@Composable
fun OhranaSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    designId: Int? = null,
    enabled: Boolean = true
) {
    val design = designId?.let { getSwitchDesignById(it) }
    
    // Используем цвета из дизайна или стандартные значения
    val checkedTrackColor = design?.checkedTrackColor ?: MaterialTheme.colorScheme.primary
    val checkedThumbColor = design?.checkedThumbColor ?: MaterialTheme.colorScheme.onPrimary
    val uncheckedTrackColor = design?.uncheckedTrackColor ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val uncheckedThumbColor = design?.uncheckedThumbColor ?: MaterialTheme.colorScheme.onSurface
    
    androidx.compose.material3.Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = androidx.compose.material3.SwitchDefaults.colors(
            checkedTrackColor = checkedTrackColor,
            checkedThumbColor = checkedThumbColor,
            uncheckedTrackColor = uncheckedTrackColor,
            uncheckedThumbColor = uncheckedThumbColor
        )
    )
}
