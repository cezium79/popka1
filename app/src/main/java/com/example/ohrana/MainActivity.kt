package com.example.ohrana

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
        "privet" -> PrivetScreen(
            onNavigateToOhrannik = {
                // Читаем имя и состояние флага напрямую из хранилища
                val activeGuardName = prefsManager.getActiveShiftEmployeeName()
                val isShiftRunning = prefsManager.isShiftActive() // Используем чистый флаг!

                // Если флаг равен true И имя действительно записано
                if (isShiftRunning && activeGuardName.isNotEmpty() && activeGuardName != "-") {
                    selectedEmployeeName = activeGuardName
                    currentScreen = "shift_control"
                } else {
                    // Если смена закрыта — гарантированно очищаем оперативку и открываем список
                    selectedEmployeeName = ""
                    currentScreen = "ohrannik"
                }
            },
            onNavigateToAdministrator = { showPasswordDialog = true }
        )




        "ohrannik" -> OhrannikScreen(
            employees = employeeList,
            onSelectEmployee = { employee ->
                selectedEmployeeName = employee.name

                // УМНЫЙ ПЕРЕХВАТ: Если смена у этого сотрудника уже активна — сразу шлем в камеру
                if (prefsManager.isShiftActiveFor(employee.name)) {
                    currentScreen = "ohrannik_cabinet"
                } else {
                    // Если смены нет — отправляем на экран открытия смены (Старт/Стоп)
                    currentScreen = "shift_control"
                }
            },
            onBack = {
                currentScreen = "privet"
                //  стираем имя из оперативной памяти приложения если сразу выходим не выбирая фамилии
                selectedEmployeeName = "" }
        )



        "shift_control" -> OhrannikShiftControlScreen(
            employeeName = selectedEmployeeName,
            selectedEmployeeName = selectedEmployeeName,
            onStartShiftSuccess = {
                prefsManager.startNewShift(selectedEmployeeName, prefsManager.isStrictSequenceEnabled())
                currentScreen = "rounds"
            },
            onContinueShift = {
                currentScreen = "rounds"
            },
            onShiftClosedSuccess = {
                // ИСПРАВЛЕНО: Убрали дублирующий вызов closeCurrentShift(),
                // так как кнопка СТОП уже сделала это перед созданием отчета!

                selectedEmployeeName = "" // Просто стираем имя из оперативной памяти
                previousScreenWasAdmin = false
                currentScreen = "privet" // Возвращаемся в начало
            },
            onBack = {
                currentScreen = "privet"
            },
            onNavigateToCompletedShifts = {
                currentScreen = "shift_logs"
            }
        )

        "ohrannik_cabinet" -> OhrannikCabinetScreen(
            // ИСПРАВЛЕНО: параметр внутри функции называется employeeName!
            employeeName = selectedEmployeeName,
            onLogout = {
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

        // Экран захвата фото
        "photo_capture" -> PhotoCaptureScreen(
            checkpointId = selectedCheckpointId ?: "",
            onPhotoTaken = { fileName ->
                // Сохраняем путь к фото в SharedPreferences
                val logText = "Фото прибора: $selectedCheckpointId -> Файл: $fileName"
                prefsManager.saveScanResult(employeeName = selectedEmployeeName, qrContent = logText)
                
                // Обновляем последнюю запись SCAN на тип PHOTO
                val activeRoundIndex = prefsManager.getActiveRoundIndex()
                if (activeRoundIndex != -1) {
                    prefsManager.shiftDatabase.updateLastScanEntry(
                        roundId = activeRoundIndex,
                        actionType = "PHOTO",
                        photoPath = fileName
                    )
                }
                
                // Индекс увеличивается в OhrannikCabinetScreen при закрытии диалога
            },
            onBack = {
                // При возврате очищаем ID чекпоинта и возвращаемся в cabinet
                selectedCheckpointId = null
                currentScreen = "ohrannik_cabinet"
            },
            prefsManager = prefsManager,
            employeeName = selectedEmployeeName
        )



        // ТО ЧЕГО НЕ ХВАТАЛО: Экран Администратора
        "admin" -> AdministratorScreen(
            onNavigateToEmployeeList = { currentScreen = "employee_list" },
            onNavigateToArchive = {
                previousScreenWasAdmin = true
                currentScreen = "spisok_otchetov"
            },
            onNavigateToRoutes = { currentScreen = "routes" }, // <-- ДОБАВЛЕНО
            onBack = { currentScreen = "privet" }
        )

        // Экран управления маршрутами (Новый блок)
        "routes" -> MarshrutiScreen(
            onNavigateToCheckpointEditor = { checkpointId -> 
                selectedCheckpointId = checkpointId
                currentScreen = "checkpoint_editor" 
            },
            onNavigateToRouteEditor = { currentScreen = "route_editor" },
            onNavigateToSchedule = { currentScreen = "schedule" },
            onBack = { currentScreen = "admin" }
        )

        // Экран расписания обходов
        "schedule" -> ScheduleScreen(
            onBack = { currentScreen = "routes" }
        )

        // Экран обходов за смену
        "rounds" -> RoundsScreen(
            onBack = { currentScreen = "shift_control" },
            onCloseShift = {
                selectedEmployeeName = ""
                currentScreen = "privet"
            },
            onStartRound = { roundIndex, routeId ->
                // Сохраняем информацию о текущем обходе
                val currentTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                prefsManager.startRound(roundIndex, currentTime, routeId)
                
                // Если есть маршрут, загружаем его точки
                if (routeId != null) {
                    val route = prefsManager.getRouteById(routeId)
                    if (route != null) {
                        // Запускаем новый маршрут с конкретными точками
                        QrHandler.startNewRound("Round_$roundIndex", route.checkpointIds)
                    }
                }
                
                currentScreen = "ohrannik_cabinet"
            }
        )

        // Экран редактирования маршрутов
        "route_editor" -> RouteEditorScreen(
            allCheckpoints = allCheckpoints,
            onBack = { currentScreen = "routes" },
            onSave = { /* Обновление списка маршрутов происходит внутри редактора */ }
        )

        // Экран редактирования чекпоинта
        "checkpoint_editor" -> CheckpointEditorScreen(
            checkpointId = selectedCheckpointId ?: "",
            onBack = { 
                selectedCheckpointId = null
                currentScreen = "routes" 
            },
            onSave = { /* Обновление списка происходит внутри редактора */ }
        )

        // Дополнительный экран: Список охранников
        "employee_list" -> EmployeeListScreen(
            employees = employeeList,
            onAddEmployee = { name, position, nfcId ->
                // Создаем и добавляем нового сотрудника прямо в список
                val newEmployee = Employee(name = name, role = position, nfcId = nfcId)
                employeeList.add(newEmployee)
                // Сохраняем обновленный список в SharedPreferences
                prefsManager.saveEmployees(employeeList.toList())
            },
            onDeleteEmployee = { employee ->
                // Удаляем сотрудника из списка и обновляем хранилище
                employeeList.remove(employee)
                prefsManager.saveEmployees(employeeList.toList())
            },
            onEditEmployee = { employee, newName, newPosition, newNfcId ->
                // Находим редактируемого сотрудника и обновляем его поля
                val index = employeeList.indexOf(employee)
                if (index != -1) {
                    employeeList[index] = employee.copy(name = newName, role = newPosition, nfcId = newNfcId)
                    prefsManager.saveEmployees(employeeList.toList())
                }
            },
            onBack = { currentScreen = "admin" }
        )

        // Дополнительный экран: Список отчетов
        "spisok_otchetov" -> SpisokOtchetovScreen(
            onBack = {
                currentScreen = "privet"
            },
            onBackToAdmin = {
                currentScreen = "admin"
            },
            previousScreenWasAdmin = previousScreenWasAdmin
        )
        
        // Экран журналов завершенных смен
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
        
        // Экран детального просмотра журнала
        "shift_detail" -> ShiftLogDetailScreen(
            onBack = {
                currentScreen = "shift_logs"
            },
            shiftId = currentShiftId,
            employeeName = selectedEmployeeName
        )



    }


}
