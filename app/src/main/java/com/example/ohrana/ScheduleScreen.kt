package com.example.ohrana

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefsManager = remember { SharedPrefsManager(context) }
    val alarmScheduler = remember { AlarmScheduler(context) }

    // 1. Количество обходов
    // Загружаем данные из SharedPreferences при каждом открытии экрана
    val initialRoundsCount = sharedPrefsManager.loadRouteRoundsCount()
    
    // 2. Список объектов будильников
    val savedAlarms: List<RouteAlarm> = sharedPrefsManager.loadRouteAlarms()
    Log.d("ScheduleScreen", "Loaded ${savedAlarms.size} alarms from SharedPreferences: $savedAlarms")
    
    // Загружаем время и допуск из SharedPreferences при каждом открытии экрана
    val initialTimeTolerance = sharedPrefsManager.loadRouteTolerance()
    val initialMaxDuration = sharedPrefsManager.loadMaxRoundDuration()
    
    // Загружаем список маршрутов для выбора
    val allRoutesList = sharedPrefsManager.loadRoutes()
    Log.d("ScheduleScreen", "Loaded ${allRoutesList.size} routes from SharedPreferences: ${allRoutesList.map { "${it.id}=${it.name}(checkpoints=${it.checkpointIds.size})" }}")

    // Загружаем чекпоинты для сохранения
    val routeCheckpoints = remember {
        sharedPrefsManager.loadRouteCheckpoints()
    }
    
    // Загружаем общее количество чекпоинтов из SharedPreferences при первом запуске
    val initialTotalScheduledCheckpoints = sharedPrefsManager.loadTotalScheduledCheckpoints()
    Log.d("ScheduleScreen", "DEBUG: initialTotalScheduledCheckpoints=$initialTotalScheduledCheckpoints (loaded from SharedPreferences)")
    
    // Определяем начальное количество раундов на основе загруженных будильников
    // Это предотвращает синхронизацию с устаревшим значением из SharedPreferences
    val initialRoundsFromAlarms = remember { savedAlarms.size }
    
    // Лог для отладки дублирования
    Log.d("ScheduleScreen", "DEBUG: savedAlarms.size=${savedAlarms.size}, initialRoundsCount=$initialRoundsCount")
    savedAlarms.forEach { alarm ->
        Log.d("ScheduleScreen", "DEBUG: Loaded alarm id=${alarm.id}, time='${alarm.time}', routeId=${alarm.routeId}")
    }
    
    // Объявляем переменные с начальными значениями
    // При первом запуске используем количество будильников как количество раундов
    var roundsCount by remember { mutableStateOf(initialRoundsFromAlarms) }
    var routeAlarms by remember { mutableStateOf(savedAlarms.toList()) }
    var timeToleranceMinutes by remember { mutableStateOf(initialTimeTolerance) }
    var maxRoundDurationMinutes by remember { mutableStateOf(initialMaxDuration) }
    
    // 3. Количество чекпоинтов для каждого обхода (на основе маршрутов) - кэшируется
    val roundCheckpointCounts = remember(routeAlarms, allRoutesList) {
        routeAlarms.map { alarm ->
            alarm.routeId?.let { routeId ->
                allRoutesList.find { it.id == routeId }?.checkpointIds?.size ?: 0
            } ?: 0
        }
    }
    Log.d("ScheduleScreen", "DEBUG: roundCheckpointCounts=$roundCheckpointCounts")
    
    // 4. Сумма всех чекпоинтов за смену (для расписания) - кэшируется, зависит от маршрутов и количества обходов
    val totalScheduledCheckpoints = remember(routeAlarms, allRoutesList, initialTotalScheduledCheckpoints) {
        Log.d("ScheduleScreen", "DEBUG: Computing total - alarms=${routeAlarms.size}, routes=${allRoutesList.size}, allRoutes=${allRoutesList.map { it.id }}")
        val computedValue = routeAlarms.map { alarm ->
            val route = alarm.routeId?.let { routeId ->
                allRoutesList.find { it.id == routeId }
            }
            val checkpointsSize = route?.checkpointIds?.size ?: 0
            Log.d("ScheduleScreen", "DEBUG: alarm.id=${alarm.id}, routeId=${alarm.routeId}, routeName=${route?.name}, checkpoints=$checkpointsSize")
            checkpointsSize
        }.sum()
        val finalValue = if (computedValue > 0) computedValue else initialTotalScheduledCheckpoints
        Log.d("ScheduleScreen", "DEBUG: remember recalculates - computedValue=$computedValue, total alarms=${routeAlarms.size}, routes=${allRoutesList.size}, final=$finalValue")
        Log.d("ScheduleScreen", "DEBUG: remember returns finalValue=$finalValue for totalScheduledCheckpoints")
        finalValue
    }
    Log.d("ScheduleScreen", "DEBUG: totalScheduledCheckpoints=$totalScheduledCheckpoints (from remember)")
    
    // Лог после инициализации
    Log.d("ScheduleScreen", "DEBUG: After init - roundsCount=$roundsCount, routeAlarms.size=${routeAlarms.size}")
    routeAlarms.forEach { alarm ->
        Log.d("ScheduleScreen", "DEBUG: After init - Alarm id=${alarm.id}")
    }
    
    // Лог при рендеринге
    androidx.compose.runtime.SideEffect {
        Log.d("ScheduleScreen", "DEBUG: Render - routeAlarms.size=${routeAlarms.size}")
        routeAlarms.forEach { alarm ->
            Log.d("ScheduleScreen", "DEBUG: Render - Alarm id=${alarm.id}")
        }
    }
    
    // Функция форматирования времени (автоматически ставит двоеточие)
    fun formatTimeInput(value: String): String {
        // Убираем все非 цифры
        val digits = value.filter { it.isDigit() }
        
        // Если введено более 2 цифр, добавляем двоеточие
        return when {
            digits.length <= 2 -> digits
            digits.length <= 4 -> "${digits.take(2)}:${digits.drop(2)}"
            else -> "${digits.take(2)}:${digits.drop(2).take(2)}" // Ограничиваем до 4 цифр
        }
    }
    
    // Флаг для предотвращения сохранения при первом запуске
    var isFirstLaunch by remember { mutableStateOf(true) }

    // Синхронизация количества полей времени со счетчиком обходов
    // Не сохраняет в SharedPreferences, только обновляет UI
    LaunchedEffect(roundsCount) {
        Log.d("ScheduleScreen", "LaunchedEffect(roundsCount) triggered: current size=${routeAlarms.size}, target roundsCount=$roundsCount")
        routeAlarms = routeAlarms.toMutableList().apply {
            Log.d("ScheduleScreen", "  Before sync: size=$size")
            if (size < roundsCount) {
                Log.d("ScheduleScreen", "  Adding alarms until size=$roundsCount")
                while (size < roundsCount) {
                    val nextId = size + 1
                    add(RouteAlarm(id = nextId, time = "", isEnabled = true))
                    Log.d("ScheduleScreen", "  Added alarm id=$nextId")
                }
            } else if (size > roundsCount) {
                Log.d("ScheduleScreen", "  Removing alarms until size=$roundsCount")
                while (size > roundsCount) {
                    removeAt(lastIndex)
                }
            }
            Log.d("ScheduleScreen", "  After sync: size=$size")
        }
    }

    // Сохраняем настройки при изменении
    LaunchedEffect(roundsCount, routeAlarms, timeToleranceMinutes, maxRoundDurationMinutes) {
        // Пропускаем сохранение при первом запуске (только при изменении значений)
        if (isFirstLaunch) {
            Log.d("ScheduleScreen", "First launch - skipping save")
            isFirstLaunch = false
            return@LaunchedEffect
        }
        
        Log.d("ScheduleScreen", "Saving settings: rounds=$roundsCount, alarms=${routeAlarms.size}, tolerance=$timeToleranceMinutes, maxDuration=$maxRoundDurationMinutes")
        
        sharedPrefsManager.saveRouteSettings(
            roundsCount = roundsCount,
            times = routeAlarms.map { it.time },
            tolerance = timeToleranceMinutes,
            checkpoints = sharedPrefsManager.loadRouteCheckpoints()
        )
        
        // Сохраняем максимальную длительность обхода в тот же файл
        sharedPrefsManager.saveMaxRoundDuration(maxRoundDurationMinutes)
        
        // Сохраняем общее количество чекпоинтов за смену
        Log.d("ScheduleScreen", "Saving total: totalScheduledCheckpoints=$totalScheduledCheckpoints (computedValue will be recalculated)")
        sharedPrefsManager.saveTotalScheduledCheckpoints(totalScheduledCheckpoints)
        Log.d("ScheduleScreen", "Total scheduled checkpoints saved: $totalScheduledCheckpoints")
        
        // Сохраняем будильники
        sharedPrefsManager.saveRouteAlarms(routeAlarms)
        Log.d("ScheduleScreen", "Alarms saved to SharedPrefs")
        
        // Обновляем будильники
        alarmScheduler.updateAlarms(routeAlarms)
        Log.d("ScheduleScreen", "Alarms updated in scheduler")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Расписание обходов") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            Log.d("ScheduleScreen", "Back button clicked")
                            onBack()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF616161) // Серый фон как у экрана
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Размытый фон
            BlurredBackground()
            
            // Контент экрана
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

            // --- РАЗДЕЛ 1: КОЛИЧЕСТВО ОБХОДОВ ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF595757))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("1. Обязательное количество обходов", fontSize = 16.sp, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = { if (roundsCount > 1) roundsCount-- },
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF424242),
                                contentColor = Color(0xFFFFFFFF)
                            ),
                            shape = RoundedCornerShape(50.dp)
                        ) {
                            Text("-", fontSize = 20.sp, color = Color(0xFFFFFFFF))
                        }
                        Text("$roundsCount обходов за смену", fontSize = 14.sp, color = Color(0xFFFFFFFF))
                        Button(
                            onClick = { roundsCount++ },
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF424242),
                                contentColor = Color(0xFFFFFFFF)
                            ),
                            shape = RoundedCornerShape(50.dp)
                        ) {
                            Text("+", fontSize = 20.sp, color = Color(0xFFFFFFFF))
                        }
                    }
                }
            }

            // --- РАЗДЕЛ 2: ДИНАМИЧЕСКОЕ РАСПИСАНИЕ, ПЕРЕКЛЮЧАТЕЛИ И ДОПУСКИ ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF595757))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("2. Расписание и временные рамки", fontSize = 16.sp, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))

                    routeAlarms.forEachIndexed { index, alarm ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Поле ввода времени (сверху)
                            OutlinedTextField(
                                value = if (alarm.time.isEmpty()) "" else alarm.time,
                                onValueChange = { newValue ->
                                    val formattedTime = formatTimeInput(newValue)
                                    routeAlarms = routeAlarms.toMutableList().apply {
                                        this[index] = alarm.copy(time = formattedTime)
                                    }
                                },
                                label = { Text("Время обхода №${alarm.id} (например, 08:00)", color = Color(0xFF000000)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color(0xFFFFFFFF),
                                    unfocusedTextColor = Color(0xFFFFFFFF),
                                    focusedContainerColor = Color(0xFF424242),
                                    unfocusedContainerColor = Color(0xFF424242),
                                    disabledContainerColor = Color(0xFF424242),
                                    focusedBorderColor = Color(0xFFFFFFFF),
                                    unfocusedBorderColor = Color(0xFFFFFFFF)
                                )
                            )

                            // Выпадающий список выбора маршрута (снизу)
                            if (allRoutesList.isNotEmpty()) {
                                var expanded by remember { mutableStateOf(false) }
                                
                                ExposedDropdownMenuBox(
                                    expanded = expanded,
                                    onExpandedChange = { expanded = !expanded },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = if (alarm.routeId != null) {
                                            allRoutesList.find { it.id == alarm.routeId }?.name ?: "Маршрут не найден"
                                        } else {
                                            "Без маршрута"
                                        },
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Маршрут", color = Color(0xFF000000)) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color(0xFFFFFFFF),
                                            unfocusedTextColor = Color(0xFFFFFFFF),
                                            focusedContainerColor = Color(0xFF424242),
                                            unfocusedContainerColor = Color(0xFF424242),
                                            disabledContainerColor = Color(0xFF424242),
                                            focusedBorderColor = Color(0xFFFFFFFF),
                                            unfocusedBorderColor = Color(0xFFFFFFFF)
                                        ),
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                                    )
                                    
                                    ExposedDropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false }
                                    ) {
                                        // Опция "Без маршрута"
                                        DropdownMenuItem(
                                            text = { Text("Без маршрута", color = Color(0xFFFFFFFF)) },
                                            onClick = {
                                                routeAlarms = routeAlarms.toMutableList().apply {
                                                    this[index] = alarm.copy(routeId = null)
                                                }
                                                expanded = false
                                            }
                                        )
                                        
                                        // Маршруты
                                        allRoutesList.forEach { route ->
                                            DropdownMenuItem(
                                                text = { Text(route.name, color = Color(0xFFFFFFFF)) },
                                                onClick = {
                                                    routeAlarms = routeAlarms.toMutableList().apply {
                                                        this[index] = alarm.copy(routeId = route.id)
                                                    }
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Если маршрутов нет, показываем предупреждение
                                Column {
                                    Text(
                                        text = "Нет маршрутов",
                                        color = Color(0xFFFF0000),
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Создайте маршруты в разделе «Маршруты»",
                                        color = Color(0xFFFFFFFF),
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            // 🔘 Переключатель подтверждения / игнорирования будильника (внизу)
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (alarm.isEnabled) "Вкл" else "Игнор",
                                    fontSize = 18.sp,
                                    color = if (alarm.isEnabled) Color(0xFF050000) else Color(0xFF050000))
                                Switch(
                                    checked = alarm.isEnabled,
                                    onCheckedChange = { isChecked ->
                                        routeAlarms = routeAlarms.toMutableList().apply {
                                            this[index] = alarm.copy(isEnabled = isChecked)
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                            }
                            
                            // 📍 Количество чекпоинтов для этого обхода
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val checkpointsCount = roundCheckpointCounts.getOrNull(index) ?: 0
                                Text(
                                    text = if (checkpointsCount > 0) "📍 $checkpointsCount точек" else "⚠️ Маршрут не выбран",
                                    fontSize = 16.sp,
                                    color = if (checkpointsCount > 0) 
                                        Color(0xFFFFFFFF) 
                                    else 
                                        Color(0xFFFF0000)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = timeToleranceMinutes,
                        onValueChange = { timeToleranceMinutes = it },
                        label = { Text("Допуск к началу обхода (+- минут)", color = Color(0xFF000000)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFFFFFFF),
                            unfocusedTextColor = Color(0xFFFFFFFF),
                            focusedContainerColor = Color(0xFF424242),
                            unfocusedContainerColor = Color(0xFF424242),
                            disabledContainerColor = Color(0xFF424242),
                            focusedBorderColor = Color(0xFFFFFFFF),
                            unfocusedBorderColor = Color(0xFFFFFFFF)
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = maxRoundDurationMinutes,
                        onValueChange = { maxRoundDurationMinutes = it },
                        label = { Text("Максимальное время на обход (в минутах)", color = Color(0xFF000000)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFFFFFFF),
                            unfocusedTextColor = Color(0xFFFFFFFF),
                            focusedContainerColor = Color(0xFF424242),
                            unfocusedContainerColor = Color(0xFF424242),
                            disabledContainerColor = Color(0xFF424242),
                            focusedBorderColor = Color(0xFFFFFFFF),
                            unfocusedBorderColor = Color(0xFFFFFFFF)
                        )
                    )

                    // 📊 Итоговое количество чекпоинтов за смену
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$totalScheduledCheckpoints",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFFFFF)
                        )
                    }

                }
            }

            // Кнопка сохранения
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    Log.d("ScheduleScreen", "Save button clicked!")
                    // Сохраняем настройки маршрута
                    sharedPrefsManager.saveRouteSettings(
                        roundsCount = roundsCount,
                        times = routeAlarms.map { it.time },
                        tolerance = timeToleranceMinutes,
                        checkpoints = sharedPrefsManager.loadRouteCheckpoints()
                    )
                    
                    // Сохраняем максимальное время на обход
                    sharedPrefsManager.saveMaxRoundDuration(maxRoundDurationMinutes)
                    Log.d("ScheduleScreen", "Max duration saved: $maxRoundDurationMinutes")
                    
                    // Сохраняем общее количество чекпоинтов за смену
                    sharedPrefsManager.saveTotalScheduledCheckpoints(totalScheduledCheckpoints)
                    Log.d("ScheduleScreen", "Total scheduled checkpoints saved: $totalScheduledCheckpoints")
                    
                    // Сохраняем будильники
                    Log.d("ScheduleScreen", "Calling saveRouteAlarms with ${routeAlarms.size} alarms")
                    sharedPrefsManager.saveRouteAlarms(routeAlarms)
                    Log.d("ScheduleScreen", "saveRouteAlarms completed")
                    
                    // Обновляем будильники
                    alarmScheduler.updateAlarms(routeAlarms)
                    
                    Log.d("ScheduleScreen", "All settings saved successfully!")
                    
                    Log.d("ScheduleScreen", "Closing screen...")
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF424242),
                    contentColor = Color(0xFFFFFFFF)
                )
            ) {
                Text("Сохранить расписание", fontSize = 16.sp)
            }
            }
        }
    }
    
    // Обработка системной кнопки "Назад"
    BackHandler(onBack = onBack)
}
