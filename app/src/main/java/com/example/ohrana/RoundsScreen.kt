package com.example.ohrana

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoundsScreen(
    onBack: () -> Unit,
    onCloseShift: () -> Unit, // Вызывается при завершении смены
    onStartRound: (roundIndex: Int, routeId: String?) -> Unit
) {
    val context = LocalContext.current
    val prefsManager = remember { SharedPrefsManager(context) }

    // Загружаем список будильников (обходов) из SharedPreferences
    val routeAlarms by remember { mutableStateOf(prefsManager.loadRouteAlarms()) }
    
    // Загружаем список маршрутов для отображения названий
    val allRoutes by remember { mutableStateOf(prefsManager.loadRoutes()) }
    
    // Получаем имя активного сотрудника
    val activeEmployeeName by remember { mutableStateOf(prefsManager.getActiveShiftEmployeeName()) }
    
    // Проверяем, активна ли смена
    val isShiftActive by remember { mutableStateOf(prefsManager.isShiftActive()) }
    
    // Переменные для диалогов
    var showGoodbyeDialog by remember { mutableStateOf(false) }
    var showConfirmCloseShiftDialog by remember { mutableStateOf(false) }
    
    // Функция для обработки нажатия кнопки "Назад"
    fun onNavigateBack() {
        if (isShiftActive) {
            onBack()
        } else {
            onCloseShift()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Обходы за смену") },
                navigationIcon = {
                    IconButton(onClick = { onNavigateBack() }) {
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Отображаем имя сотрудника
            if (activeEmployeeName.isNotEmpty()) {
                Text(
                    text = "Сотрудник: $activeEmployeeName",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            
            // Если смена не активна - показываем предупреждение
            if (!isShiftActive) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Смена не активна",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Начните смену, чтобы увидеть список обходов",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Проверяем, есть ли настроенные обходы
            if (routeAlarms.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Нет настроенных обходов",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Настройте расписание обходов в разделе «Расписание обходов»",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                // Список обходов
                Text(
                    text = "Обходы за смену (${routeAlarms.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                routeAlarms.forEach { alarm ->
                    // Получаем название маршрута по ID
                    val routeName = if (alarm.routeId != null) {
                        allRoutes.find { it.id == alarm.routeId }?.name ?: "Маршрут не найден"
                    } else {
                        "Без маршрута"
                    }
                    
                    // Проверяем, завершен ли обход
                    val isRoundCompleted = prefsManager.isRoundCompleted(alarm.id)
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = if (isRoundCompleted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Обход №${alarm.id}",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Время: ${alarm.time}",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Маршрут: $routeName",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                
                                if (isRoundCompleted) {
                                    // Если обход завершен - показываем текст "Завершен"
                                    Text(
                                        text = "Завершен",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    // Кнопка "Начать обход" для незавершенных обходов
                                    Button(
                                        onClick = { onStartRound(alarm.id, alarm.routeId) },
                                        enabled = isShiftActive,
                                        modifier = Modifier.width(150.dp)
                                    ) {
                                        Text("Начать обход", fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            
            // Информационная карточка
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Информация",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "После начала обхода вы попадете в кабинет охранника, где сможете сканировать QR-коды чекпоинтов.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Кнопка завершения смены (показывается только если смена активна)
            if (isShiftActive) {
                OutlinedButton(
                    onClick = {
                        // Показываем диалог подтверждения
                        showConfirmCloseShiftDialog = true
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Text("ЗАВЕРШИТЬ СМЕНУ (СТОП)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Диалог подтверждения завершения смены
            if (showConfirmCloseShiftDialog) {
                AlertDialog(
                    onDismissRequest = { showConfirmCloseShiftDialog = false },
                    title = { Text("Завершить смену?") },
                    text = { Text("Вы уверены, что хотите завершить текущую смену? После завершения смены отчет будет сформирован и вы вернетесь на экран выбора сотрудника.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showConfirmCloseShiftDialog = false
                                
                                // Сохраняем статус контроля последовательности для отчета
                                prefsManager.saveSequenceControlStatus(prefsManager.isStrictSequenceEnabled())
                                
                                // Железно фиксируем дату и время закрытия смены в SharedPreferences
                                prefsManager.closeCurrentShift()
                                
                                // Формируем Excel-отчет
                                prefsManager.generateExcelReport(activeEmployeeName)
                                
                                // Показываем окно прощания
                                showGoodbyeDialog = true
                            }
                        ) {
                            Text("Да, завершить", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirmCloseShiftDialog = false }) {
                            Text("Отмена")
                        }
                    }
                )
            }
            
            // Окно прощания
            if (showGoodbyeDialog) {
                AlertDialog(
                    onDismissRequest = { },
                    title = { Text("Смена успешно завершена", fontWeight = FontWeight.Bold) },
                    text = { Text("Смена закрыта. До свидания!", fontSize = 16.sp) },
                    confirmButton = {
                        Button(
                            onClick = {
                                showGoodbyeDialog = false
                                onCloseShift()
                            }
                        ) {
                            Text("ОК")
                        }
                    }
                )
            }
        }
    }
}
