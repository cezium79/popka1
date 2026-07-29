package com.example.ohrana.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ohrana.CheckpointAction
import com.example.ohrana.QrHandler
import com.example.ohrana.R
import com.example.ohrana.SharedPrefsManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

/**
 * Единый диалог успешного прохождения чекпоинта.
 * Автоматически увеличивает индекс чекпоинта и обновляет actionType в БД.
 * Закрывается автоматически через 3 секунды.
 */
@Composable
fun CheckpointPassedDialog(
    checkpointName: String, // имя чекпоинта для отображения в диалоге
    checkpointId: String, // ID чекпоинта (отображается + используется для обновления индекса)
    timestamp: String = SimpleDateFormat("HH:mm:ss dd.MM.yyyy", Locale.getDefault()).format(Date()), // время фиксации
    manager: SharedPrefsManager, // менеджер настроек и БД
    onDismiss: () -> Unit, // коллбэк при закрытии диалога
    onCheckLastCheckpoint: () -> Unit, // коллбэк для проверки, последний ли это чекпоинт
    photoPath: String? = null, // путь к фото (для PHOTO-чекпоинтов)
    checkpointAction: CheckpointAction? = null // тип действия чекпоинта (передаётся явно, чтобы не искать в БД)
) {
    val context = LocalContext.current
    var hasIncremented by remember { mutableStateOf(false) } // флаг, чтобы не увеличивать индекс повторно

    LaunchedEffect(checkpointId) {
        if (!hasIncremented) {
            val activeRoundIndex = manager.getActiveRoundIndex()
            
            // Если чекпоинт отменен — просто показываем диалог и выходим
            val state = QrHandler.getLastCheckpointState()
            if (state != null && state.hasAborted) {
                hasIncremented = true
                // Ждём и закрываем
                kotlinx.coroutines.delay(1000L.milliseconds)
                onDismiss()
                return@LaunchedEffect
            }
            
            if (activeRoundIndex != -1) { // получаем индекс активного обхода
                // Пытаемся получить состояние из буфера
                // Пытаемся получить состояние из буфера
                val state = QrHandler.getLastCheckpointState()

                if (state != null && state.checkpointId == checkpointId) {
                    // Сохраняем ВСЁ одной записью из буфера
                    manager.shiftDatabase.addLogEntry(
                        checkpointName = state.checkpointName,
                        checkpointId = state.checkpointId,
                        employeeName = state.employeeName,
                        roundId = activeRoundIndex,
                        routeName = "Маршрут обхода",
                        sequenceIndex = state.sequenceIndex,
                        isSequenceCorrect = state.isSequenceCorrect,
                        scanType = state.scanType,
                        actionType = when (state.checkpointAction) {
                            CheckpointAction.QUESTION -> "QUESTION"
                            CheckpointAction.INPUT -> "INPUT"
                            CheckpointAction.PHOTO -> "PHOTO"
                            else -> "CHECKPOINT"
                        },
                        questionText = state.questionText,
                        inputTitle = state.inputTitle,
                        answer = state.answer,
                        inputValue = state.inputValue,
                        photoPath = state.photoPath ?: photoPath
                    )

                    // Очищаем буфер
                    QrHandler.clearCheckpointState()
                    manager.removeCheckpointState(checkpointId)
                } else {
                    // Fallback: если буфера нет — создаём обычную запись CHECKPOINT
                    val resolvedAction = checkpointAction ?: manager.getCheckpointById(checkpointId)?.action
                    val actionType = when (resolvedAction) {
                        CheckpointAction.QUESTION -> "QUESTION"
                        CheckpointAction.INPUT -> "INPUT"
                        CheckpointAction.PHOTO -> "PHOTO"
                        else -> "CHECKPOINT"
                    }

                    manager.shiftDatabase.addLogEntry(
                        checkpointName = checkpointName.removePrefix("Локация: ").removePrefix("Фото прибора: ").removePrefix("NFC-чекпоинт: "),
                        checkpointId = checkpointId,
                        employeeName = manager.getActiveShiftEmployeeName(),
                        roundId = activeRoundIndex,
                        routeName = "Маршрут обхода",
                        sequenceIndex = manager.getRoundCheckpointIndex(activeRoundIndex),
                        isSequenceCorrect = true,
                        scanType = "QR",
                        actionType = actionType,
                        photoPath = photoPath
                    )
                }

                // Вычисляем и сохраняем новый индекс чекпоинта (текущий + 1)
                val oldCheckpointIndex = manager.getRoundCheckpointIndex(activeRoundIndex)
                val newCheckpointIndex = oldCheckpointIndex + 1
                manager.updateCurrentCheckpointIndex(newCheckpointIndex)
                hasIncremented = true // помечаем, что индекс уже увеличен
                // Показываем Toast с информацией об изменении индекса
                android.widget.Toast.makeText(
                    context,
                    "Индекс чекпоинта: $oldCheckpointIndex -> $newCheckpointIndex", // старый и новый индекс
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            // Вызываем внешний коллбэк для дополнительной обработки
            onCheckLastCheckpoint()
        }

        // Ждём 3 секунды перед автозакрытием диалога
        kotlinx.coroutines.delay(1000L.milliseconds)
        onDismiss() // закрываем диалог
    }

    // Диалог с анимацией успеха и информацией о чекпоинте
    AlertDialog(
        onDismissRequest = { }, // отключение закрытия по бэку
        title = { Text("Точка зафиксирована", textAlign = TextAlign.Center) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Блок с иконкой успеха (вок как символ)
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Image(
                        bitmap = android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.vokak).asImageBitmap(),
                        contentDescription = "Успех",
                        modifier = Modifier.size(100.dp)
                    )
                }
                // Колонка с текстовой информацией
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = checkpointName, fontSize = 16.sp, fontWeight = FontWeight.Medium) // название чекпоинта
                    Text(text = "ID: $checkpointId", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) // ID чекпоинта
                    Text(text = "Время: $timestamp", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) // время фиксации
                }
            }
        },
        confirmButton = {} // нет кнопки подтверждения — автозакрытие
    )
}
