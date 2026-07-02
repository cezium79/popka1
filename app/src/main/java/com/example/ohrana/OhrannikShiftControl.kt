package com.example.ohrana

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.ohrana.ShiftDatabaseManager
import com.example.ohrana.ShiftLogEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OhrannikShiftControlScreen(
    employeeName: String,
    isAnyShiftActive: Boolean,            // Есть ли активная смена у КОГО-ЛИБО
    onStartShiftSuccess: () -> Unit,      // Вызывается при первом СТАРТЕ смены
    onContinueShift: () -> Unit,          // Вызывается при нажатии ПРОДОЛЖИТЬ
    onShiftClosedSuccess: () -> Unit,     // Вызывается при СТОПЕ смены
    onBack: () -> Unit,
    selectedEmployeeName: String,
    onNavigateToCompletedShifts: () -> Unit // Вызывается при нажатии кнопки журналов завершенных смен
) {
    val context = LocalContext.current
    val prefsManager = remember { SharedPrefsManager(context) }
    val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    // Инициализируем состояние экрана напрямую из вашей функции проверки флага
    // Проверяем, активна ли смена У ЭТОГО сотрудника, а не просто есть ли активная смена
    var isShiftActive by remember { mutableStateOf(prefsManager.isShiftActiveFor(employeeName)) }
    // Есть ли активная смена у кого-либо (для блокировки кнопки "Начать смену")
    val anyShiftActive = isAnyShiftActive
    // Есть ли активная смена у КОГО-ТО ДРУГОГО (не у этого сотрудника)
    val isShiftActiveByOther = prefsManager.isShiftActiveByOther(employeeName)
    // Имя сотрудника, у которого открыта смена
    val activeEmployeeName = prefsManager.getActiveShiftEmployeeNameByOther(employeeName)

    val shiftStartTime = remember { prefsManager.getShiftStartTime() }

    // 🔥 СТАТУС ДЛЯ ОТОБРАЖЕНИЯ ЖУРНАЛА ОБХОДОВ
    var showLogsDialog by remember { mutableStateOf(false) }
    // Переменная для отображения окна успешного закрытия смены
    var showGoodbyeDialog by remember { mutableStateOf(false) }
    // Переменная для навигации к журналам завершенных смен
    var showCompletedShifts by remember { mutableStateOf(false) }


    // 🔔 ВСПЛЫВАЮЩЕЕ ОКНО С ИСТОРИЕЙ ОБХОДОВ ТЕКУЩЕЙ СМЕНЫ
    if (showLogsDialog) {
        // Загружаем данные из новой базы данных
        val activeShiftId = prefsManager.prefs.getString("active_shift_id", null)
        val logs = activeShiftId?.let { shiftId ->
            prefsManager.shiftDatabase.loadLogsByShift(shiftId)
        } ?: emptyList()
        
        // Получаем информацию о смене
        val shiftRecord = activeShiftId?.let { shiftId ->
            prefsManager.shiftDatabase.loadAllShifts().find { it.id == shiftId }
        }
        
        // Получаем информацию об обходах
        val rounds = activeShiftId?.let { shiftId ->
            prefsManager.shiftDatabase.loadAllRounds().filter { it.shiftId == shiftId }
        } ?: emptyList()
        
        // Сортируем логи по времени от ранних к поздним
        val sortedLogs = logs.sortedBy { it.timestamp }
        
        // Сортируем обходы по времени начала
        val sortedRounds = rounds.sortedBy { it.startTime }
        
        // Формируем отображение времени смены
        val shiftEndTime = shiftRecord?.endTime ?: "(смена активна)"
        
        AlertDialog(
            onDismissRequest = { showLogsDialog = false },
            title = { Text("Журнал текущих обходов", fontWeight = FontWeight.Bold) },
            text = {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        item {
                            // Отображаем информацию о смене
                            Text(
                                text = "--- СМЕНА ---",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                text = "Время начала: ${shiftRecord?.startTime ?: "-"}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                text = "Время окончания: $shiftEndTime",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            if (sortedLogs.isEmpty()) {
                                Text(
                                    text = "Журнал пуст. За смену не было зафиксировано ни одного обхода.",
                                    fontSize = 14.sp,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Column {
                                    // Группируем логи по обходам
                                    sortedRounds.forEach { round ->
                                        val roundLogs = sortedLogs.filter { it.roundId == round.id }
                                        
                                        // Строки начала и завершения обхода
                                        Text(
                                            text = "--- ОБХОД #${round.id} ---",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                        Text(
                                            text = "Маршрут: ${round.routeName ?: "не указан"}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(bottom = 2.dp)
                                        )
                                        Text(
                                            text = "Время начала: ${round.startTime}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                        
                                        // Логи этого обхода
                                        roundLogs.forEach { entry ->
                                            // Пропускаем записи с типом SCAN (они для аудита, не отображаются в отчете)
                                            if (entry.actionType == "SCAN") return@forEach
                                            
                                            // Извлекаем только время (без даты)
                                            val timeOnly = entry.timestamp.split(" ").getOrNull(1) ?: entry.timestamp
                                            // Формируем результат на основе типа действия
                                            val resultText = when (entry.actionType) {
                                                "QUESTION" -> "Ответ: ${entry.answer ?: "-"}"
                                                "INPUT" -> "Введено: ${entry.inputValue ?: "-"}"
                                                "PHOTO" -> "Фото снято"
                                                else -> if (entry.isSequenceCorrect) "Пройдено" else "Нарушение"
                                            }
                                            // Добавляем тип сканирования в конце
                                            val scanType = if (entry.scanType == "NFC") "[NFC]" else "[QR]"
                                            Text(
                                                text = "$timeOnly - ${entry.checkpointName} - $resultText $scanType",
                                                fontSize = 14.sp,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        
                                        // Время окончания обхода - в конце блока перед разделителем
                                        Text(
                                            text = "Время окончания: ${round.endTime ?: "(не завершен)"}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                        
                                        // Разделитель между обходами - пустая строка и черта
                                        Spacer(modifier = Modifier.padding(vertical = 8.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .background(MaterialTheme.colorScheme.outlineVariant)
                                        )
                                        Spacer(modifier = Modifier.padding(vertical = 8.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showLogsDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }
    // 🚪 ВСПЛЫВАЮЩЕЕ ОКНО ПРОЩАНИЯ ПРИ ЗАКРЫТИИ СМЕНЫ
    if (showGoodbyeDialog) {
        AlertDialog(
            onDismissRequest = { /* Не позволяем закрыть кликом мимо окна, чтобы точно прочитал */ },
            title = { Text("Смена успешно завершена", fontWeight = FontWeight.Bold) },
            text = { Text("Смена закрыта. До свидания!", fontSize = 16.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        showGoodbyeDialog = false // Скрываем окно
                         prefsManager.closeCurrentShift()// НА ВСЯКИЙ СЛУЧАЙ дублируем вызов здесь, чтобы изменения точно записались на диск:
                        onShiftClosedSuccess()    // ТОЛЬКО ТЕПЕРЬ уходим на экран "Привет"
                    }
                ) {
                    Text("ОК")
                }
            }
        )
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Управление сменой") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Сотрудник:", fontSize = 16.sp, color = MaterialTheme.colorScheme.outline)
            Text(
                text = employeeName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (isShiftActive) {
                // === ЭТОТ БЛОК ПОКАЗЫВАЕТСЯ, ТОЛЬКО ЕСЛИ СМЕНА ОТКРЫТА ===
                Text(text = "СТАТУС: СМЕНА ОТКРЫТА", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                Text(
                    text = "Время начала: $shiftStartTime",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                // 1. Кнопка "ПРОДОЛЖИТЬ ОБХОД"
                Button(
                    onClick = onContinueShift,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("ПРОДОЛЖИТЬ ОБХОД", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                // 2. КНОПКА «ПОСМОТРЕТЬ МОИ ОБХОДЫ»
                Button(
                    onClick = { showLogsDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("ПОСМОТРЕТЬ МОИ ОБХОДЫ", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                
                // 3. КНОПКА «ЖУРНАЛЫ ЗАВЕРШЕННЫХ СМЕН»
                Button(
                    onClick = { showCompletedShifts = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Text("ЖУРНАЛЫ ЗАВЕРШЕННЫХ СМЕН", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }

            } else {
                // === ЭТОТ БЛОК ПОКАЗЫВАЕТСЯ, ТОЛЬКО ЕСЛИ СМЕНА ЗАКРЫТА ===
                Text(
                    text = "СТАТУС: СМЕНА ЗАКРЫТА",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 48.dp)
                )

                // 4. КНОПКА СТАРТ (Перенесена сюда, в блок else) - ЗАБЛОКИРОВАНА, если смена открыта кем-то другим
                Button(
                    onClick = {
                        // Меняем состояние экрана
                        isShiftActive = true

                        // Вызываем коллбэк из MainActivity, который запишет старт смены на диск
                        onStartShiftSuccess()
                    },
                    enabled = !isShiftActiveByOther,  // Кнопка заблокирована, если смена открыта кем-то другим
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isShiftActiveByOther) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        if (isShiftActiveByOther) "СМЕНА УЖЕ ОТКРЫТА: $activeEmployeeName" else "НАЧАТЬ СМЕНУ (СТАРТ)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isShiftActiveByOther) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onPrimary
                    )
                }
                
                // Дубликат кнопки "ЖУРНАЛЫ ЗАВЕРШЕННЫХ СМЕН" для закрытой смены
                Button(
                    onClick = { showCompletedShifts = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Text("ЖУРНАЛЫ ЗАВЕРШЕННЫХ СМЕН", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

        }
    }
    
    // Диалог с выбором действий
    if (showCompletedShifts) {
        AlertDialog(
            onDismissRequest = { showCompletedShifts = false },
            title = { Text("Журналы смен", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Просмотреть журналы завершенных смен.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Доступны только журналы завершенных смен.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCompletedShifts = false
                        onNavigateToCompletedShifts()
                    }
                ) {
                    Text("ОК")
                }
            }
        )
    }
}
