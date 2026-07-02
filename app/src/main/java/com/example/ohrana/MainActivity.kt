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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.example.ohrana.ShiftDatabaseManager
import com.example.ohrana.ShiftLogDetailScreen
import com.example.ohrana.ShiftLogEntry


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppNavigation()
        }
    }
    
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // Сохраняем intent для обработки NFC-тегов
        setIntent(intent)
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
    var currentShiftId by remember { mutableStateOf("") }

    // Состояние для 5-кликового механизма админ-кнопки
    var clickCount by remember { mutableStateOf(0) }
    var lastClickTime by remember { mutableStateOf(0L) }

    val context = LocalContext.current
    val prefsManager = remember { SharedPrefsManager(context) }

    // Загружаем список чекпоинтов
    val allCheckpoints by remember { mutableStateOf(prefsManager.loadCheckpoints()) }

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


    // 🔔 ВСПЛЫВАЮЩЕЕ ДИАЛОГОВОЕ ОКНО
    if (showPasswordDialog) {
        AdminPasswordDialog(
            onDismiss = { showPasswordDialog = false; isPasswordError = false },
            onConfirm = { enteredPassword ->
                if (enteredPassword == "1234")// Пароль для входа в окно настроек
                {
                    showPasswordDialog = false
                    isPasswordError = false
                    currentScreen = "admin"
                } else {
                    isPasswordError = true
                }
            },
            isError = isPasswordError
        )
    }

    when (currentScreen) {
        "privet" -> {
            // Автоматическое NFC-сканирование при загрузке экрана
            val context = LocalContext.current
            val activity = context as? ComponentActivity
            
            val nfcManager = context.getSystemService(android.content.Context.NFC_SERVICE) as android.nfc.NfcManager
            val nfcAdapter = nfcManager.defaultAdapter
            
            // Рендеринг экрана приветствия
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
                
                // Текст поверх фона
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Добро пожаловать!", fontSize = 24.sp, color = androidx.compose.ui.graphics.Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Поднесите личную карту к телефону", fontSize = 18.sp, color = androidx.compose.ui.graphics.Color.White)
                }
            }
            
            // Активируем reader mode при загрузке экрана
            LaunchedEffect(nfcAdapter, activity) {
                if (nfcAdapter != null && activity != null) {
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
                                    } else {
                                        selectedEmployeeName = employee.name
                                        // Всегда переходим в shift_control для обычных сотрудников
                                        currentScreen = "shift_control"
                                    }
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        "NFC-тег не найден в списке сотрудников",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
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
        }
        
        "admin" -> AdministratorScreen(
            onNavigateToEmployeeList = { currentScreen = "employee_list" },
            onNavigateToArchive = {
                previousScreenWasAdmin = true
                currentScreen = "spisok_otchetov"
            },
            onNavigateToRoutes = { currentScreen = "routes" },
            onBack = { currentScreen = "privet" }
        )
        
        "ohrannik_cabinet" -> OhrannikCabinetScreen(
            employeeName = selectedEmployeeName,
            onBack = {
                // Если смена открыта кем-то другим, возвращаемся в shift_control
                // Если у этого сотрудника активна смена, возвращаемся в rounds
                // Если смены нет у никого, возвращаемся в shift_control
                if (prefsManager.isShiftActiveByOther(selectedEmployeeName)) {
                    currentScreen = "shift_control"
                } else if (prefsManager.isShiftActiveFor(selectedEmployeeName)) {
                    currentScreen = "rounds"
                } else {
                    currentScreen = "shift_control"
                }
            },
            onLogout = {
                selectedEmployeeName = ""
                currentScreen = "privet"
            },
            onNavigateToReports = {
                previousScreenWasAdmin = false
                currentScreen = "spisok_otchetov"
            },
            onNavigateToPhoto = { manager, checkpointId ->
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
        
        "shift_control" -> OhrannikShiftControlScreen(
            employeeName = selectedEmployeeName,
            isAnyShiftActive = prefsManager.isAnyShiftActive(),
            selectedEmployeeName = selectedEmployeeName,
            onStartShiftSuccess = {
                prefsManager.startNewShift(selectedEmployeeName, prefsManager.isStrictSequenceEnabled())
                currentScreen = "rounds"
            },
            onContinueShift = {
                currentScreen = "rounds"
            },
            onShiftClosedSuccess = {
                selectedEmployeeName = ""
                currentScreen = "privet"
            },
            onBack = {
                currentScreen = "privet"
            },
            onNavigateToCompletedShifts = {
                currentScreen = "shift_logs"
            }
        )
        
        "rounds" -> RoundsScreen(
            onBack = { currentScreen = "shift_control" },
            onCloseShift = {
                selectedEmployeeName = ""
                currentScreen = "privet"
            },
            onStartRound = { roundIndex, routeId ->
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
                if (activeRoundIndex != -1) {
                    prefsManager.shiftDatabase.updateLastScanEntry(
                        roundId = activeRoundIndex,
                        actionType = "PHOTO",
                        photoPath = fileName
                    )
                }
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
        
        "spisok_otchetov" -> SpisokOtchetovScreen(
            onBack = {
                currentScreen = "privet"
            },
            onBackToAdmin = {
                currentScreen = "admin"
            },
            previousScreenWasAdmin = previousScreenWasAdmin
        )
        
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
        
        "shift_logs" -> ShiftLogsListScreen(
            onBack = {
                currentScreen = "privet"
            },
            selectedEmployeeName = selectedEmployeeName,
            onNavigateToDetails = { shiftId ->
                currentScreen = "shift_detail"
                currentShiftId = shiftId
            }
        )
        
        "shift_detail" -> ShiftLogDetailScreen(
            onBack = {
                currentScreen = "shift_logs"
            },
            shiftId = currentShiftId,
            employeeName = selectedEmployeeName
        )
        
    }

}
