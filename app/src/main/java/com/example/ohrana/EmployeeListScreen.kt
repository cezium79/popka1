package com.example.ohrana

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import android.annotation.SuppressLint
import android.nfc.NfcAdapter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter") // ДЛЯ MATERIAL 3
@Composable
fun EmployeeListScreen(
    employees: List<Employee>,
    onAddEmployee: (String, String, String?) -> Unit,
    onDeleteEmployee: (Employee) -> Unit,
    onEditEmployee: (Employee, String, String, String?) -> Unit,
    onBack: () -> Unit
) {
    var nameInput by remember { mutableStateOf("") }
    var roleInput by remember { mutableStateOf("") }
    var nfcIdInput by remember { mutableStateOf("") }
    var editingEmployee by remember { mutableStateOf<Employee?>(null) }
    var nfcScanningEnabled by remember { mutableStateOf(false) }
    var nfcScanResult by remember { mutableStateOf<String?>(null) }
    var nfcAdapter by remember { mutableStateOf<NfcAdapter?>(null) }
    
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    
    // Инициализация NFC-адаптера
    LaunchedEffect(Unit) {
        try {
            nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        } catch (e: Exception) {
            // NFC не поддерживается
        }
    }
    
    // Обработка NFC-сканирования
    LaunchedEffect(nfcScanningEnabled) {
        if (nfcScanningEnabled && nfcAdapter != null && activity != null) {
            try {
                nfcAdapter?.enableReaderMode(
                    activity,
                    { tag ->
                        val nfcId = tag.id.joinToString(":") { byte -> String.format("%02X", byte) }
                        nfcScanResult = nfcId
                    },
                    NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B,
                    null
                )
            } catch (e: Exception) {
                // Error handling
            }
        } else {
            try {
                nfcAdapter?.disableReaderMode(activity)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    // Обработка результата сканирования
    LaunchedEffect(nfcScanResult) {
        if (nfcScanResult != null) {
            nfcIdInput = nfcScanResult!!
            nfcScanningEnabled = false
            nfcScanResult = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editingEmployee == null) "Список сотрудников" else "Редактирование") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                    .padding(2.dp)
            ) {
            // Форма ввода / редактирования
            val textFieldColors = OutlinedTextFieldDefaults.colors(
                disabledContainerColor = Color(0xFF424242), // Серый фон в неактивном состоянии
                focusedContainerColor = Color(0xFF565656),  // Серый фон при фокусе
                unfocusedContainerColor = Color(0xFF424242), // Серый фон в обычном состоянии
                focusedTextColor = Color(0xFFFFFFFF),              // Белый текст при фокусе
                unfocusedTextColor = Color(0xFFFFFFFF),            // Белый текст в обычном состоянии
                disabledTextColor = Color(0xFFFFFFFF),             // Белый текст в неактивном состоянии
                cursorColor = MaterialTheme.colorScheme.primary, // Цвет курсора
                focusedBorderColor = Color(0xFF7E7D7D), // Цвет рамки при фокусе
                unfocusedBorderColor = Color.Transparent,     // Прозрачная рамка в обычном состоянии
                focusedLabelColor = Color(0xFFFFFFFF),              // Цвет label при фокусе
                unfocusedLabelColor = Color(0xFFC0C0C0)             // Цвет label в обычном состоянии
            )
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text("ФИО Сотрудника") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = textFieldColors
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = roleInput,
                onValueChange = { roleInput = it },
                label = { Text("Должность") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = textFieldColors
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = nfcIdInput,
                onValueChange = { nfcIdInput = it },
                label = { Text("NFC ID") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = textFieldColors
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            // NFC-сканер
            if (nfcScanningEnabled) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Сканирование NFC...", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Приложите NFC-тег к устройству",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        IconButton(
                            onClick = { nfcScanningEnabled = false }
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = "Отмена сканирования")
                        }
                    }
                }
            } else {
                Button(
                    onClick = { nfcScanningEnabled = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp), // Стиль кнопки №3
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3A3737), // Стиль кнопки №3
                        contentColor = Color(0xFFE7E3EC)    // Стиль кнопки №3
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                ) {
                    Text("Сканировать NFC-тег") // Удалена иконка
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (nameInput.isNotBlank() && roleInput.isNotBlank()) {
                        val currentEditing = editingEmployee
                        if (currentEditing != null) {
                            onEditEmployee(currentEditing, nameInput, roleInput, nfcIdInput)
                            editingEmployee = null
                        } else {
                            onAddEmployee(nameInput, roleInput, nfcIdInput)
                        }
                        nameInput = ""
                        roleInput = ""
                        nfcIdInput = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp), // Стиль кнопки №3
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3A3737), // Стиль кнопки №3
                    contentColor = Color(0xFFE7E3EC)    // Стиль кнопки №3
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
            ) {
                Text(if (editingEmployee == null) "Добавить сотрудника" else "Сохранить изменения")
            }

            if (editingEmployee != null) {
                Button(
                    onClick = {
                        editingEmployee = null
                        nameInput = ""
                        roleInput = ""
                        nfcIdInput = ""
                        nfcScanningEnabled = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp), // Стиль кнопки №3
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3A3737), // Стиль кнопки №3
                        contentColor = Color(0xFFE7E3EC)    // Стиль кнопки №3
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 8.dp)
                ) {
                    Text("Отмена") // Удалена иконка
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Список сотрудников
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(employees) { employee ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF424242)) // Серый фон карточки
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = employee.name, style = MaterialTheme.typography.titleMedium, color = Color(0xFFFFFFFF)) // Белый текст
                                Text(text = employee.role, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFFFFFFF)) // Белый текст
                                employee.nfcId?.let {
                                    if (it.isNotEmpty()) {
                                        Text(text = "NFC: $it", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFFFFF)) // Белый текст
                                    }
                                }
                            }
                            Row {
                                IconButton(onClick = {
                                    editingEmployee = employee
                                    nameInput = employee.name
                                    roleInput = employee.role
                                    nfcIdInput = employee.nfcId ?: ""
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Редактировать", tint = Color(0xFFFFFFFF)) // Белый цвет иконки
                                }
                                IconButton(onClick = { onDeleteEmployee(employee) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color(0xFFF82A2A)) // Красный цвет иконки
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
    
    // Обработка системной кнопки "Назад"
    BackHandler(onBack = onBack)
}
