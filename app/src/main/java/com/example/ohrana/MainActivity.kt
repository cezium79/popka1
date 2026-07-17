package com.example.ohrana

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.font.FontWeight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import com.example.ohrana.ShiftDatabaseManager
import com.example.ohrana.ShiftLogDetailScreen
import com.example.ohrana.ShiftLogEntry
import com.example.ohrana.CloudStorageManager
import com.example.ohrana.CloudSettingsScreen
import com.example.ohrana.GuardMember
import com.example.ohrana.GuardNfcSelectionScreen
import com.example.ohrana.JournalScreen
import com.example.ohrana.HtmlReportViewerScreen
import com.example.ohrana.SoundSettingsScreen
import com.example.ohrana.SoundPlayer
import com.example.ohrana.ui.theme.OhranaTheme
import android.content.pm.ActivityInfo
import android.util.Log

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Инициализируем SoundPlayer при создании Activity
        SoundPlayer.init(this)
        
        setContent {
            OhranaTheme {
                AppNavigation()
            }
        }
    }
    
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // Сохраняем intent для обработки NFC-тегов
        setIntent(intent)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Освобождаем ресурсы SoundPlayer при уничтожении Activity
        SoundPlayer.release()
    }
}
@Composable
fun AdminPasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    isError: Boolean
) {
    var passwordInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Доступ ограничен") },
        text = {
            Column {
                Text("Введите пароль администратора:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("Пароль") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = isError,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isError) {
                    Text(
                        text = "Неверный пароль. Попробуйте еще раз.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(passwordInput) }) { Text("Вход") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}


@Composable
fun AppNavigation() {


    var previousScreenWasAdmin by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf("privet") }
    var selectedEmployeeName by remember { mutableStateOf("") }
    var selectedCheckpointId by remember { mutableStateOf<String?>(null) }
    var htmlReportPath by remember { mutableStateOf("") }
    var currentShiftId by remember { mutableStateOf("") }
    var guardsCount by remember { mutableStateOf(1) }
    var showErrorOverlay by remember { mutableStateOf<String?>(null) }
    var roundsClickCount by remember { mutableStateOf(0) }
    var roundsLastClickTime by remember { mutableStateOf(0L) }

    // Состояние для 5-кликового механизма админ-кнопки
    var clickCount by remember { mutableStateOf(0) }
    var lastClickTime by remember { mutableStateOf(0L) }

    val context = LocalContext.current
    val prefsManager = remember { SharedPrefsManager(context) }

    // Загружаем список чекпоинтов
    val allCheckpoints = remember { mutableStateListOf<Checkpoint>() }
    
    // Обновляем список чекпоинтов при изменении
    LaunchedEffect(prefsManager.loadCheckpoints()) {
        allCheckpoints.clear()
        allCheckpoints.addAll(prefsManager.loadCheckpoints())
    }

    val employeeList = remember {
        val savedList = prefsManager.loadEmployees()
        val initialList = if (savedList.isEmpty()) {
            listOf(
                Employee(name = "Иванов Иван", role = "Старший смены"),
                Employee(name = "Петров Петр", role = "Охранник")
            )
        } else {
            savedList
        }
        mutableStateListOf<Employee>().apply { addAll(initialList) }
    }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var isPasswordError by remember { mutableStateOf(false) }


    when (currentScreen) {
        "privet" -> {
            // Синхронизируем guardsCount из SharedPreferences
            guardsCount = prefsManager.getGuardsCount()
            
            // Сбрасываем ориентацию при возврате на экран привет
            val context = LocalContext.current
            val activity = context as? ComponentActivity
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            
            // Проверяем статус смены
            val isShiftActive = prefsManager.isAnyShiftActive()
            
            val nfcManager = context.getSystemService(android.content.Context.NFC_SERVICE) as android.nfc.NfcManager
            val nfcAdapter = nfcManager.defaultAdapter
            
            // Рендеринг экрана приветствия поверх всех элементов
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Фон с картинкой
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = androidx.compose.ui.platform.LocalContext.current.resources.getIdentifier("brick_wall", "drawable", androidx.compose.ui.platform.LocalContext.current.packageName)),
                    contentDescription = "Фон",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Скрываемая кнопка для входа в админ-меню
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp)
                        .size(60.dp)
                        .clickable(
                            onClick = {
                                val currentTime = System.currentTimeMillis()
                                // Если клик произошел в течение 1 секунды от предыдущего
                                if (currentTime - lastClickTime <= 1000) {
                                    clickCount++
                                    // Если достигнуто 10 кликов - переходим в админ-меню
                                    if (clickCount >= 10) {
                                        currentScreen = "admin"
                                        clickCount = 0 // Сброс счетчика
                                    }
                                } else {
                                    // Таймер вышел, сбрасываем счетчик и начинаем заново
                                    clickCount = 1
                                }
                                lastClickTime = currentTime
                            },
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ADMIN",
                        fontSize = 12.sp,
                        color = androidx.compose.ui.graphics.Color.Transparent,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
                
                // Скрываемая кнопка для входа в меню обхода без NFC (10 кликов за 1 сек)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .size(60.dp)
                        .clickable(
                            onClick = {
                                val currentTime = System.currentTimeMillis()
                                // Если клик произошел в течение 1 секунды от предыдущего
                                if (currentTime - roundsLastClickTime <= 1000) {
                                    roundsClickCount++
                                    // Если достигнуто 10 кликов - переходим в меню обхода
                                    if (roundsClickCount >= 10) {
                                        currentScreen = "rounds"
                                        roundsClickCount = 0 // Сброс счетчика
                                    }
                                } else {
                                    // Таймер вышел, сбрасываем счетчик и начинаем заново
                                    roundsClickCount = 1
                                }
                                roundsLastClickTime = currentTime
                            },
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ROUNDS",
                        fontSize = 12.sp,
                        color = androidx.compose.ui.graphics.Color.Transparent,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
                
                // Текст поверх фона
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Добро пожаловать!", fontSize = 24.sp, color = androidx.compose.ui.graphics.Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Для guardsCount = 1 показываем текст про NFC
                    if (guardsCount == 1) {
                        Text(text = "Поднесите личную карту к телефону", fontSize = 18.sp, color = androidx.compose.ui.graphics.Color.White)
                    }
                }
                
                // Кнопка "ОТКРЫТЬ СМЕНУ" для групповой работы (только если смена закрыта)
                if (guardsCount > 1 && !isShiftActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp)
                    ) {
                        Button(
                            onClick = {
                                currentScreen = "guard_nfc_selection"
                            },
                            modifier = Modifier.fillMaxWidth(0.8f).height(60.dp)
                        ) {
                            Text("ОТКРЫТЬ СМЕНУ", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                // Всплывающее окно ошибки поверх экрана приветствия
                if (showErrorOverlay != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color(0x80000000)), // Полупрозрачный черный фон
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Card(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .padding(32.dp),
                            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFFF0000))
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                            ) {
                                androidx.compose.material3.Text(
                                    text = showErrorOverlay!!,
                                    fontSize = 20.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = androidx.compose.ui.graphics.Color.White
                                )
                            }
                        }
                    }
                }
            }
            
            // NFC-сканирование: 
            // - Для guardsCount = 1: при NFC сразу начинаем смену
            // - Для guardsCount > 1 и открытой смены: NFC сразу открывает экран rounds
            // - Для guardsCount > 1 и закрытой смены: NFC сканирует постоянно, обрабатывает только администратора
            LaunchedEffect(nfcAdapter, activity, guardsCount, isShiftActive, showErrorOverlay) {
                if (nfcAdapter != null && activity != null && (guardsCount == 1 || (guardsCount > 1 && isShiftActive) || guardsCount > 1)) {
                    try {
                        nfcAdapter.enableReaderMode(
                            activity,
                            { tag ->
                                val nfcId = tag.id.joinToString(":") { byte -> String.format("%02X", byte) }
                                
                                // Останавливаем сканирование
                                activity.runOnUiThread {
                                    nfcAdapter.disableReaderMode(activity)
                                }
                                
                                // Обрабатываем результат
                                val employee = employeeList.find { it.nfcId == nfcId }
                                
                                if (employee != null) {
                                    val isAdmin = employee.role.contains("администратор", ignoreCase = true) || 
                                                 employee.role.contains("administrator", ignoreCase = true)
                                
                                if (isAdmin) {
                                    currentScreen = "admin"
                                } else if (guardsCount > 1 && !isShiftActive) {
                                    // Для групповой работы и закрытой смены игнорируем карты охранников
                                    showErrorOverlay = "Для открытия смены нажмите кнопку «ОТКРЫТЬ СМЕНУ»"
                                } else if (guardsCount > 1 && isShiftActive) {
                                    // Для групповой работы и открытой смены проверяем, есть ли охранник в списке
                                    val guards = prefsManager.loadGuards()
                                    val isGuardInShift = guards.any { it.nfcId == nfcId }
                                    
                                    if (isGuardInShift) {
                                        selectedEmployeeName = employee.name
                                        currentScreen = "rounds"
                                    } else {
                                        // Охранник не у смены - отказываем в доступе через overlay
                                        showErrorOverlay = "Вас нет в смене"
                                    }
                                } else {
                                    selectedEmployeeName = employee.name
                                    
                                    // Если смена открыта - сразу открываем экран rounds
                                    if (isShiftActive) {
                                        // Для guardsCount = 1 проверяем, что сотрудник на смене
                                        val activeEmployeeName = prefsManager.getActiveShiftEmployeeName()
                                        if (activeEmployeeName.isNotEmpty() && activeEmployeeName != employee.name) {
                                            // Смена открыта другим сотрудником
                                            showErrorOverlay = "Вас нет в смене"
                                        } else {
                                            currentScreen = "rounds"
                                        }
                                    } else {
                                        // Для guardsCount = 1 сразу начинаем смену
                                        if (guardsCount == 1) {
                                            prefsManager.startNewShift(selectedEmployeeName, prefsManager.isStrictSequenceEnabled())
                                            currentScreen = "rounds"
                                        }
                                    }
                                }
                                } else {
                                    // NFC-тег не найден в списке сотрудников
                                    showErrorOverlay = "NFC-тег не найден в списке сотрудников"
                                }
                                
                                // Перезапускаем сканирование
                                activity.runOnUiThread {
                                    try {
                                        nfcAdapter.enableReaderMode(
                                            activity,
                                            { tag2 ->
                                                val nfcId2 = tag2.id.joinToString(":") { byte -> String.format("%02X", byte) }
                                                
                                                activity.runOnUiThread {
                                                    nfcAdapter.disableReaderMode(activity)
                                                }
                                                
                                                val employee2 = employeeList.find { it.nfcId == nfcId2 }
                                                
                                                if (employee2 != null) {
                                                    val isAdmin2 = employee2.role.contains("администратор", ignoreCase = true) || 
                                                                 employee2.role.contains("administrator", ignoreCase = true)
                                                    
                                                    if (isAdmin2) {
                                                        currentScreen = "admin"
                                                    } else {
                                                        showErrorOverlay = "NFC-тег не найден в списке сотрудников"
                                                    }
                                                } else {
                                                    showErrorOverlay = "NFC-тег не найден в списке сотрудников"
                                                }
                                            },
                                            android.nfc.NfcAdapter.FLAG_READER_NFC_A or android.nfc.NfcAdapter.FLAG_READER_NFC_B,
                                            null
                                        )
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            },
                            android.nfc.NfcAdapter.FLAG_READER_NFC_A or android.nfc.NfcAdapter.FLAG_READER_NFC_B,
                            null
                        )
                    } catch (e: Exception) {
                        // Error handling
                    }
                }
            }
            
            // Через 3 секунды убираем ошибку
            LaunchedEffect(showErrorOverlay) {
                if (showErrorOverlay != null) {
                    kotlinx.coroutines.delay(3000)
                    showErrorOverlay = null
                }
            }
        }
        
        "admin" -> AdministratorScreen(
            onNavigateToEmployeeList = { currentScreen = "employee_list" },
            onNavigateToRoutes = { currentScreen = "routes" },
            onNavigateToLogs = { currentScreen = "journal" },
            onNavigateToCloudSettings = { currentScreen = "cloud_settings" },
            onBack = { currentScreen = "privet" },
            onNavigateToSoundSettings = { currentScreen = "sound_settings" }
        )
        
        "sound_settings" -> SoundSettingsScreen(
            onBack = { currentScreen = "admin" }
        )
        
        "ohrannik_cabinet" -> OhrannikCabinetScreen(
            employeeName = selectedEmployeeName,
            onBack = {
                val guardsCount = prefsManager.getGuardsCount()
                
                // Для guardsCount > 1 возвращаемся в rounds
                // Для guardsCount = 1 всегда возвращаемся в privet (так как убрали экран shift_control)
                if (guardsCount > 1) {
                    currentScreen = "rounds"
                } else {
                    currentScreen = "privet"
                }
            },
            onLogout = {
                selectedEmployeeName = ""
                currentScreen = "privet"
            },
            onNavigateToPhoto = { manager, checkpointId ->
                // Переход на экран фото
                selectedCheckpointId = checkpointId
                currentScreen = "photo_capture"
            },
            onEndRound = {
                currentScreen = "rounds"
            },
            onCloseShift = {
                selectedEmployeeName = ""
                currentScreen = "privet"
            }
        )
        
        "guard_selection" -> GuardSelectionScreen(
            onBack = { currentScreen = "privet" },
            onStartShift = { guardList ->
                // Сохраняем количество охранников
                guardsCount = guardList.size
                prefsManager.setGuardsCount(guardList.size)
                
                // Начинаем новую смену с группой охранников
                prefsManager.startNewShift(guardList, prefsManager.isStrictSequenceEnabled())
                currentScreen = "rounds"
            }
        )
        
        "guard_nfc_selection" -> GuardNfcSelectionScreen(
            onBack = { currentScreen = "privet" },
            onStartShift = { guardList ->
                // Сохраняем количество охранников
                guardsCount = guardList.size
                prefsManager.setGuardsCount(guardList.size)
                
                // Начинаем новую смену с группой охранников
                prefsManager.startNewShift(guardList, prefsManager.isStrictSequenceEnabled())
                currentScreen = "rounds"
            }
        )
        
        "rounds" -> RoundsScreen(
            onBack = {
                val guardsCount = prefsManager.getGuardsCount()
                // Для guardsCount > 1 возвращаемся в privet
                // Для guardsCount = 1 тоже возвращаемся в privet (так как убрали экран shift_control)
                currentScreen = "privet"
            },
            onCloseShift = {
                selectedEmployeeName = ""
                currentScreen = "privet"
            },
            onStartRound = { guardName, roundIndex, routeId ->
                // Обновляем selectedEmployeeName с выбранным именем охранника
                selectedEmployeeName = guardName
                val currentTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                prefsManager.startRound(roundIndex, currentTime, routeId)
                
                if (routeId != null) {
                    val route = prefsManager.getRouteById(routeId)
                    if (route != null) {
                        QrHandler.startNewRound("Round_$roundIndex", route.checkpointIds)
                    }
                }
                
                currentScreen = "ohrannik_cabinet"
            }
        )
        
        "photo_capture" -> PhotoCaptureScreen(
            checkpointId = selectedCheckpointId ?: "",
            onPhotoTaken = { fileName ->
                val logText = "Фото прибора: $selectedCheckpointId -> Файл: $fileName"
                prefsManager.saveScanResult(employeeName = selectedEmployeeName, qrContent = logText)
                
                val activeRoundIndex = prefsManager.getActiveRoundIndex()
                android.util.Log.d("MainActivity", "onPhotoTaken: checkpoint=$selectedCheckpointId, round=$activeRoundIndex, file=$fileName")
                if (activeRoundIndex != -1) {
                    val result = prefsManager.shiftDatabase.updateLastScanEntry(
                        roundId = activeRoundIndex,
                        actionType = "PHOTO",
                        photoPath = fileName
                    )
                    android.util.Log.d("MainActivity", "updateLastScanEntry result: $result")
                } else {
                    android.util.Log.d("MainActivity", "No active round - skip update")
                }
                // Индекс будет увеличен при закрытии экрана
            },
            onCheckpointComplete = {
                // Увеличиваем индекс при завершении фото (чекпоинт пройден)
                prefsManager.updateCurrentCheckpointIndex(prefsManager.getCurrentCheckpointIndex() + 1)
            },
            onBack = {
                selectedCheckpointId = null
                currentScreen = "ohrannik_cabinet"
            },
            prefsManager = prefsManager,
            employeeName = selectedEmployeeName
        )
        
        "spisok_otchetov" -> {
            // Архив отчетов удален
            currentScreen = "privet"
        }
        
        "employee_list" -> EmployeeListScreen(
            employees = employeeList,
            onAddEmployee = { name, position, nfcId ->
                val newEmployee = Employee(name = name, role = position, nfcId = nfcId)
                employeeList.add(newEmployee)
                prefsManager.saveEmployees(employeeList.toList())
            },
            onDeleteEmployee = { employee ->
                employeeList.remove(employee)
                prefsManager.saveEmployees(employeeList.toList())
            },
            onEditEmployee = { employee, newName, newPosition, newNfcId ->
                val index = employeeList.indexOf(employee)
                if (index != -1) {
                    employeeList[index] = employee.copy(name = newName, role = newPosition, nfcId = newNfcId)
                    prefsManager.saveEmployees(employeeList.toList())
                }
            },
            onBack = { currentScreen = "admin" }
        )
        
        "routes" -> MarshrutiScreen(
            onNavigateToCheckpointEditor = { checkpointId -> 
                selectedCheckpointId = checkpointId
                currentScreen = "checkpoint_editor" 
            },
            onNavigateToRouteEditor = { currentScreen = "route_editor" },
            onNavigateToSchedule = { currentScreen = "schedule" },
            onBack = { currentScreen = "admin" }
        )
        
        "schedule" -> ScheduleScreen(
            onBack = { currentScreen = "routes" }
        )
        
        "checkpoint_editor" -> CheckpointEditorScreen(
            checkpointId = selectedCheckpointId ?: "",
            onBack = { 
                selectedCheckpointId = null
                currentScreen = "routes" 
            },
            onSave = { /* Обновление списка происходит внутри редактора */ }
        )
        
        "route_editor" -> RouteEditorScreen(
            allCheckpoints = allCheckpoints,
            onBack = { currentScreen = "routes" },
            onSave = { /* Обновление списка маршрутов происходит внутри редактора */ }
        )
        
        "journal" -> JournalScreen(
            onBack = { currentScreen = "admin" },
            onNavigateToHtmlReport = { htmlPath ->
                htmlReportPath = htmlPath
                selectedCheckpointId = htmlPath
                currentScreen = "html_report_viewer"
            },
            onSelectDate = { dateStr ->
                android.util.Log.d("MainActivity", "Selected date: $dateStr")
                // Здесь можно добавить логику, если нужно что-то делать при выборе даты
            }
        )
        
        "html_report_viewer" -> HtmlReportViewerScreen(
            onBack = { 
                selectedCheckpointId = null
                htmlReportPath = ""
                currentScreen = "admin"
            },
            htmlFilePath = htmlReportPath
        )
        
        "shift_detail" -> ShiftLogDetailScreen(
            onBack = { currentScreen = "admin" },
            shiftId = currentShiftId,
            employeeName = selectedEmployeeName
        )
        
        "cloud_settings" -> CloudSettingsScreen(
            onBack = { currentScreen = "admin" }
        )
        
    }

}
