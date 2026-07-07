package com.example.ohrana

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoundsScreen(
    onBack: () -> Unit,
    onCloseShift: () -> Unit, // Вызывается при завершении смены
    onStartRound: (guardName: String, roundIndex: Int, routeId: String?) -> Unit,
    onOpenJournal: () -> Unit // Вызывается при нажатии кнопки "Журнал текущей смены"
) {
    val context = LocalContext.current
    val prefsManager = remember { SharedPrefsManager(context) }

    // Загружаем список будильников (обходов) из SharedPreferences
    val routeAlarms by remember { mutableStateOf(prefsManager.loadRouteAlarms()) }
    
    // Загружаем список маршрутов для отображения названий
    val allRoutes by remember { mutableStateOf(prefsManager.loadRoutes()) }
    
    // Получаем имя активного сотрудника
    val activeEmployeeName by remember { mutableStateOf(prefsManager.getActiveShiftEmployeeName()) }
    
    // Получаем список охранников
    val guardList by remember { mutableStateOf(prefsManager.loadGuards()) }
    
    // Проверяем, активна ли смена
    val isShiftActive by remember { mutableStateOf(prefsManager.isShiftActive()) }
    
    // Переменные для анимации
    var showGoodbyeAnimation by remember { mutableStateOf(false) }
    var showConfirmCloseShiftDialog by remember { mutableStateOf(false) }
    var showGuardSelectionDialog by remember { mutableStateOf(false) }
    var showUnfinishedRoundWarning by remember { mutableStateOf(false) }
    var selectedRoundForGuardSelection by remember { mutableStateOf<Pair<Int, String?>>(0 to null) }
    
    // Проверка: есть ли незавершённый обход
    fun hasUnfinishedRound(): Boolean {
        return routeAlarms.any { alarm ->
            prefsManager.isRoundStarted(alarm.id) && !prefsManager.isRoundCompleted(alarm.id)
        }
    }
    
    // Функция для завершения смены (без диалога подтверждения)
    fun completeShiftManually() {
        // Сохраняем статус контроля последовательности для отчета
        prefsManager.saveSequenceControlStatus(prefsManager.isStrictSequenceEnabled())
        
        // Железно фиксируем дату и время закрытия смены в SharedPreferences
        prefsManager.closeCurrentShift()
        
        // Показываем анимацию завершения смены
        showGoodbyeAnimation = true
    }
    
    // Функция для обработки нажатия кнопки "Назад"
    fun onNavigateBack() {
        if (isShiftActive) {
            onBack()
        } else {
            onCloseShift()
        }
    }
    
    // Функция для обработки нажатия кнопки "Начать обход"
    fun handleStartRound(roundIndex: Int, routeId: String?, isContinuation: Boolean = false) {
        // Проверяем, есть ли незавершённый обход - только для новых обходов (не для продолжения)
        if (!isContinuation && hasUnfinishedRound()) {
            showUnfinishedRoundWarning = true
            return
        }
        
        val guardsCount = guardList.size
        
        if (guardsCount == 1) {
            // Для одного охранника сразу начинаем обход
            onStartRound(guardList[0].name, roundIndex, routeId)
        } else {
            // Для группы охранников показываем диалог выбора
            selectedRoundForGuardSelection = roundIndex to routeId
            showGuardSelectionDialog = true
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
            // Отображаем список охранников или одного сотрудника
            if (guardList.isNotEmpty()) {
                if (guardList.size == 1) {
                    // Для одного охранника показываем имя
                    Text(
                        text = "Сотрудник: ${guardList[0].name}",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                } else {
                    // Для группы охранников показываем список
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Сотрудники:",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            guardList.forEach { guard ->
                                Text(
                                    text = "• ${guard.name} (${guard.role})",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
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
                                } else if (prefsManager.isRoundStarted(alarm.id)) {
                                    // Если обход начат, но не завершен - показываем кнопку "Продолжить обход" красным
                                    Button(
                                        onClick = { handleStartRound(alarm.id, alarm.routeId, isContinuation = true) },
                                        enabled = isShiftActive,
                                        modifier = Modifier.width(150.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("Продолжить обход", fontSize = 14.sp, color = MaterialTheme.colorScheme.onError)
                                    }
                                } else {
                                    // Кнопка "Начать обход" для незавершенных обходов
                                    Button(
                                        onClick = { handleStartRound(alarm.id, alarm.routeId) },
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
            
            // Кнопка "Журнал текущей смены"
            Button(
                onClick = {
                    // Сохраняем статус контроля последовательности для отчета
                    prefsManager.saveSequenceControlStatus(prefsManager.isStrictSequenceEnabled())
                    
                    // Переходим на экран журнала
                    onOpenJournal()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Журнал текущей смены", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            
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
            
            // Диалог выбора охранника для обхода (только если guardsCount > 1)
            if (showGuardSelectionDialog && guardList.size > 1) {
                AlertDialog(
                    onDismissRequest = { showGuardSelectionDialog = false },
                    title = { Text("Кто будет проходить обход №${selectedRoundForGuardSelection.first}?") },
                    text = {
                        Column {
                            guardList.forEach { guard ->
                                TextButton(
                                    onClick = {
                                        showGuardSelectionDialog = false
                                        onStartRound(guard.name, selectedRoundForGuardSelection.first, selectedRoundForGuardSelection.second)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column {
                                        Text(
                                            text = guard.name,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = guard.role,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showGuardSelectionDialog = false }) {
                            Text("Отмена")
                        }
                    }
                )
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
                                completeShiftManually()
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
            
            // Яркое предупреждение о незавершённом обходе
            if (showUnfinishedRoundWarning) {
                AlertDialog(
                    onDismissRequest = { showUnfinishedRoundWarning = false },
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Предупреждение",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ВНИМАНИЕ!",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    text = {
                        Column {
                            Text(
                                text = "Сначала необходимо завершить текущий обход! Нельзя начинать новый обход, не завершив предыдущий.",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { showUnfinishedRoundWarning = false },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Понятно", fontSize = 16.sp, color = MaterialTheme.colorScheme.onError)
                        }
                    }
                )
            }
        }
        
        // Анимация завершения смены (без кнопок) - поверх всего экрана
        if (showGoodbyeAnimation) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFFFFFF))
                    .zIndex(100f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(50.dp))
                            .background(Color(0xFF4CAF50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Успех",
                            modifier = Modifier.size(60.dp),
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Смена успешно завершена",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Смена закрыта. До свидания!",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Через 3 секунды возвращаемся на экран выбора сотрудника
            LaunchedEffect(showGoodbyeAnimation) {
                if (showGoodbyeAnimation) {
                    kotlinx.coroutines.delay(3000)
                    if (showGoodbyeAnimation) {
                        showGoodbyeAnimation = false
                        onCloseShift()
                    }
                }
            }
        }
    }
}
