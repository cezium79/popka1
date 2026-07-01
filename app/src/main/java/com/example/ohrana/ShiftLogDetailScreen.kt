package com.example.ohrana

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftLogDetailScreen(
    onBack: () -> Unit,
    shiftId: String,
    employeeName: String
) {
    val context = LocalContext.current
    val prefsManager = remember { SharedPrefsManager(context) }
    val shiftDatabase = prefsManager.shiftDatabase
    
    // Загружаем данные смены
    val shift = remember { shiftDatabase.loadAllShifts().find { it.id == shiftId } }
    
    // Загружаем логи для этой смены
    val logs = remember { shiftDatabase.loadLogsByShift(shiftId) }
    
    // Загружаем обходы для этой смены
    val rounds = remember { shiftDatabase.loadAllRounds().filter { it.shiftId == shiftId } }
    
    // Сортируем логи по времени
    val sortedLogs = logs.sortedBy { it.timestamp }
    
    // Сортируем обходы по времени начала
    val sortedRounds = rounds.sortedBy { it.startTime }
    
    // Переменные для отображения фото
    var showPhotoDialog by remember { mutableStateOf(false) }
    var selectedPhotoPath by remember { mutableStateOf<String?>(null) }
    
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Детали смены") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (shift == null) {
                Text(
                    text = "Смена не найдена",
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // Информация о смене
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    text = "СМЕНА",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Сотрудник: ${shift.employeeName}",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Время начала: ${shift.startTime}",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Время окончания: ${shift.endTime ?: "-"}",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Контроль последовательности: ${if (shift.strictSequenceEnabled) "ВКЛ" else "ВЫКЛ"}",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    // Обходы
                    sortedRounds.forEach { round ->
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .fillMaxWidth()
                                ) {
                                    Text(
                                        text = "ОБХОД №${round.id}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Маршрут: ${round.routeName ?: "не указан"}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Время начала: ${round.startTime}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Время окончания: ${round.endTime ?: "-"}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Чекпоинтов в маршруте: ${round.checkpointsCount}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Пройдено чекпоинтов: ${round.checkpointsPassed}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Нарушений последовательности: ${round.sequenceViolations}",
                                        fontSize = 12.sp,
                                        color = if (round.sequenceViolations > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        
                        // Логи этого обхода
                        val roundLogs = sortedLogs.filter { it.roundId == round.id }
                        if (roundLogs.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Логи обхода:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            
                            roundLogs.forEach { log ->
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = log.checkpointName,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = log.timestamp.split(" ").getOrNull(1) ?: "-",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val resultText = when (log.actionType) {
                                                "CHECKPOINT" -> "✓ Пройден"
                                                "QUESTION" -> "❓ Ответ: ${log.answer ?: "-"}"
                                                "INPUT" -> "✏️ Введено: ${log.inputValue ?: "-"}"
                                                "PHOTO" -> "📷 Фото"
                                                else -> "❓ Статус: ${if (log.isSequenceCorrect) "OK" else "НАРУШЕНИЕ"}"
                                            }
                                            Text(
                                                text = resultText,
                                                fontSize = 14.sp,
                                                color = if (!log.isSequenceCorrect) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            
                                            // Кнопка просмотра фото, если фото было снято
                                            if (log.actionType == "PHOTO" && log.photoPath != null) {
                                                Button(
                                                    onClick = {
                                                        selectedPhotoPath = log.photoPath
                                                        showPhotoDialog = true
                                                    },
                                                    modifier = Modifier.padding(start = 8.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text("👁️", fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                    
                                    if (!log.isSequenceCorrect) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Нарушение последовательности!",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                    
                    // Итоговая статистика
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    text = "ИТОГО",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Всего обходов: ${rounds.size}",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Всего пройденных чекпоинтов: ${logs.size}",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val totalViolations = rounds.sumOf { it.sequenceViolations }
                                Text(
                                    text = "Всего нарушений: $totalViolations",
                                    fontSize = 14.sp,
                                    color = if (totalViolations > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
    
    // Диалог для просмотра фото
    if (showPhotoDialog && selectedPhotoPath != null) {
        val photoFile = remember(selectedPhotoPath) {
            java.io.File(selectedPhotoPath!!)
        }
        
        var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
        
        LaunchedEffect(photoFile) {
            if (photoFile.exists()) {
                bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
            }
        }
        
        AlertDialog(
            onDismissRequest = { showPhotoDialog = false },
            title = { Text("Фото чекпоинта") },
            text = {
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "Фото чекпоинта",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Фото не найдено", fontSize = 14.sp)
                        Text(photoFile.absolutePath, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showPhotoDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }
}
