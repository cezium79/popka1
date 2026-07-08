package com.example.ohrana

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefsManager = remember { SharedPrefsManager(context) }
    
    // Диалог для просмотра фото
    var selectedPhotoPath by remember { mutableStateOf<String?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    
    if (showDialog && selectedPhotoPath != null) {
        PhotoDialog(
            photoPath = selectedPhotoPath!!,
            onDismiss = { showDialog = false }
        )
    }
    
    // Данные о смене - обновляются при изменении SharedPreferences
    val guards by remember { mutableStateOf(prefsManager.loadGuards()) }
    val routeAlarms by remember { mutableStateOf(prefsManager.loadRouteAlarms()) }
    
    // Метаданные смены
    val shiftStartTime by remember { mutableStateOf(prefsManager.getShiftStartTime()) }
    val shiftEndTime by remember { mutableStateOf(prefsManager.getShiftEndTime()) }
    val isShiftActive by remember { mutableStateOf(prefsManager.isAnyShiftActive()) }
    val shiftId by remember { mutableStateOf(prefsManager.prefs.getString("active_shift_id", null) ?: "-") }
    
    val simpleDateFormat = SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault())
    
    // Получаем epoch time из SharedPreferences и обрабатываем ошибки
    val startTimeDate by remember {
        mutableStateOf(
            try {
                val epochTime = prefsManager.prefs.getLong("active_shift_start_time_epoch", 0)
                if (epochTime > 0) {
                    simpleDateFormat.format(Date(epochTime))
                } else {
                    shiftStartTime
                }
            } catch (e: Exception) {
                android.util.Log.e("JournalScreen", "Error parsing shift start time", e)
                shiftStartTime
            }
        )
    }
    
    // Количество чекпоинтов в смене (из всех маршрутов)
    val totalCheckpoints by remember {
        mutableStateOf(routeAlarms.sumOf { alarm ->
            prefsManager.getRouteById(alarm.routeId ?: "")?.checkpointIds?.size ?: 0
        })
    }
    
    // Количество нарушений обхода - оптимизировано для текущей смены
    val activeLogs by remember {
        mutableStateOf(prefsManager.shiftDatabase.loadAllLogs().filter { it.shiftId == shiftId })
    }
    val violationsCount by remember {
        mutableStateOf(activeLogs.filter { it.isSequenceCorrect == false }.size)
    }
    
    // Имя старшего смены - безопасный доступ
    val seniorName by remember {
        mutableStateOf(
            guards.firstOrNull { 
                it.role.lowercase().contains("старший") || it.role.lowercase().contains("senior") || it.role.lowercase().contains("администратор") 
            }?.name ?: "-"
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Журнал текущей смены") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        // Если смена закрыта, показываем уведомление
        if (!isShiftActive && shiftId != "-") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.8f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Смена завершена",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Журнал отображает данные последней завершенной смены.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBack) {
                            Text("OK")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ЗАГОЛОВОК С МЕТАДАННЫМИ СМЕНЫ
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f), androidx.compose.ui.graphics.RectangleShape)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "МЕТАДАННЫЕ СМЕНЫ",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(text = "Номер смены: $shiftId", fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            if (seniorName != "-") {
                                Text(text = "Старший смены: $seniorName", fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Text(text = "Охранники: ${guards.joinToString(", ") { it.name }}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(text = "Время начала: $startTimeDate", fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            if (shiftEndTime != "-" && shiftEndTime.isNotEmpty()) {
                                Text(text = "Время окончания: $shiftEndTime", fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Text(text = "Чекпоинтов в смене: $totalCheckpoints", fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(text = "Нарушений обхода: $violationsCount", fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
                
                // ТАБЛИЦА ПО КАЖДОМУ ОБХОДУ
                routeAlarms.forEach { alarm ->
                    val roundId = alarm.id
                    val roundStartTime = prefsManager.getRoundStartTime(roundId)
                    val roundEndTime = prefsManager.getRoundEndTime(roundId)
                    val isCompleted = prefsManager.isRoundCompleted(roundId)
                    val checkpointIndex = prefsManager.getRoundCheckpointIndex(roundId)
                    val routeId = prefsManager.getRoundRouteId(roundId)
                    val route = prefsManager.getRouteById(routeId ?: "")
                    val totalRouteCheckpoints = route?.checkpointIds?.size ?: 0
                    val roundLogs = prefsManager.getRoundScanLogs(roundId).sortedBy { it.timestamp }
                    val roundViolations = prefsManager.getRoundViolations(roundId)
                    
                    // Логируем логи для диагностики
                    android.util.Log.d("JournalScreen", "=== ROUND $roundId ===")
                    android.util.Log.d("JournalScreen", "  roundLogs.size = ${roundLogs.size}")
                    roundLogs.forEach { log ->
                        android.util.Log.d("JournalScreen", "  Log: checkpoint=${log.checkpointName}, actionType=${log.actionType}, isSequenceCorrect=${log.isSequenceCorrect}, answer='${log.answer}', inputValue='${log.inputValue}'")
                    }
                    
                    // Заголовок обхода
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f), androidx.compose.ui.graphics.RectangleShape)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "ОБХОД №${alarm.id} (${alarm.time})",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Статус: ${if (isCompleted) "Завершен" else "В процессе"} | Прогресс: $checkpointIndex/$totalRouteCheckpoints",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Время: $roundStartTime - $roundEndTime",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                    
                    // Заголовки столбцов
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Время",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Box(modifier = Modifier.weight(1.2f)) {
                                Text(
                                    text = "Сотрудник",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Box(modifier = Modifier.weight(1.5f)) {
                                Text(
                                    text = "Чекпоинт",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Box(modifier = Modifier.weight(1.5f)) {
                                Text(
                                    text = "Вопрос/Пояснение",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Box(modifier = Modifier.weight(1.5f)) {
                                Text(
                                    text = "Ответ/Данные",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Box(modifier = Modifier.weight(0.8f)) {
                                Text(
                                    text = "Фото",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Примечание",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    
                    // Записи обхода
                    items(roundLogs) { log ->
                        // Определяем наличие нарушения для этой записи
                        // Просто проверяем isSequenceCorrect - если false, значит нарушение
                        val hasViolation = !log.isSequenceCorrect
                        // Получаем тип нарушения
                        val errorType = log.sequenceErrorType
                        android.util.Log.d("JournalScreen", "Log entry: checkpoint=${log.checkpointName}, actionType=${log.actionType}, isSequenceCorrect=${log.isSequenceCorrect}, sequenceErrorType=${errorType.name}, answer='${log.answer}', inputValue='${log.inputValue}'")
                        val violationNote = if (hasViolation) {
                            when (errorType) {
                                SequenceErrorType.FOREIGN_CHECKPOINT -> "Чужая метка"
                                SequenceErrorType.OUTSIDE_ROUTE -> "Вне маршрута"
                                SequenceErrorType.OUT_OF_SEQUENCE -> "Вне очереди"
                                SequenceErrorType.NONE -> "-"
                            }
                        } else {
                            "-"
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .border(1.dp, if (hasViolation) MaterialTheme.colorScheme.error.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f), androidx.compose.ui.graphics.RectangleShape),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = log.timestamp.substring(11),
                                    fontSize = 11.sp,
                                    color = if (hasViolation) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Box(modifier = Modifier.weight(1.2f)) {
                                Text(
                                    text = log.employeeName ?: "-",
                                    fontSize = 11.sp,
                                    color = if (hasViolation) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Box(modifier = Modifier.weight(1.5f)) {
                                Text(
                                    text = log.checkpointName,
                                    fontSize = 11.sp,
                                    color = if (hasViolation) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Box(modifier = Modifier.weight(1.5f)) {
                                // Столбец 4: Вопрос/Пояснение
                                // Для нарушений не отображаем вопросы/заголовки
                                val checkpoint = prefsManager.getCheckpointById(log.checkpointId)
                                val questionOrTitle = when {
                                    hasViolation -> "-"
                                    log.actionType == "QUESTION" -> checkpoint?.questionText ?: "-"
                                    log.actionType == "INPUT" -> checkpoint?.inputTitle ?: "-"
                                    log.actionType == "PHOTO" -> log.checkpointName
                                    else -> "-"
                                }
                                Text(
                                    text = questionOrTitle,
                                    fontSize = 11.sp,
                                    color = if (hasViolation) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Box(modifier = Modifier.weight(1.5f)) {
                                // Столбец 5: Ответ/Данные
                                // Для нарушений не отображаем вопросы/ответы, но показываем действие
                                android.util.Log.d("JournalScreen", "Rendering log: checkpoint=${log.checkpointName}, actionType=${log.actionType}, inputValue='${log.inputValue}', answer='${log.answer}'")
                                android.util.Log.d("JournalScreen", "  checkpointId=${log.checkpointId}, shiftId=${log.shiftId}, roundId=${log.roundId}, isSequenceCorrect=${log.isSequenceCorrect}, sequenceErrorType=${log.sequenceErrorType.name}")
                                val answerOrData = when {
                                    hasViolation -> when {
                                        log.actionType == "PHOTO" -> "Фото снято"
                                        else -> "-"  // Для нарушений остальных типов - дефис
                                    }
                                    log.actionType == "CHECKPOINT" -> "Точка пройдена"
                                    log.actionType == "QUESTION" -> log.answer ?: "-"
                                    log.actionType == "INPUT" -> log.inputValue ?: "-"
                                    log.actionType == "PHOTO" -> "Фото снято"
                                    else -> "-"
                                }
                                android.util.Log.d("JournalScreen", "  -> answerOrData='$answerOrData'")
                                Text(
                                    text = answerOrData,
                                    fontSize = 11.sp,
                                    color = if (hasViolation) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Box(modifier = Modifier.weight(0.8f)) {
                                if (log.photoPath != null) {
                                    Text(
                                        text = "👁️",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { 
                                            selectedPhotoPath = log.photoPath
                                            showDialog = true
                                        }
                                    )
                                } else {
                                    Text(
                                        text = "-",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = violationNote,
                                    fontSize = 11.sp,
                                    color = if (hasViolation) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    
                    // Если нет записей
                    if (roundLogs.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Нет записей сканирования",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    // Пустая строка между обходами
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
@Preview(showBackground = true, name = "Журнал текущей смены")
fun JournalScreenPreview() {
    MaterialTheme {
        JournalScreen(onBack = {})
    }
}

// Диалог для просмотра фото
@Composable
fun PhotoDialog(
    photoPath: String,
    onDismiss: () -> Unit
) {
    val bitmap = remember { androidx.compose.runtime.mutableStateOf<android.graphics.Bitmap?>(null) }
    
    LaunchedEffect(photoPath) {
        bitmap.value = runCatching {
            android.graphics.BitmapFactory.decodeFile(photoPath)
        }.getOrNull()
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Фото чекпоинта") },
        text = {
            if (bitmap.value != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap.value!!.asImageBitmap(),
                    contentDescription = "Фото чекпоинта",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(androidx.compose.ui.graphics.RectangleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(Color.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Невозможно загрузить фото")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}
