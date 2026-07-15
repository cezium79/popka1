package com.example.ohrana

import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler

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
                actions = {
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
                .padding(paddingValues) // <- 1. ПРИМЕНЯЕМ ОТСТУПЫ ОТ SCAFFOLD В ПЕРВУЮ ОЧЕРЕДЬ
                .padding(16.dp)         // 2. Ваши собственные дополнительные отступы
        ) {
            // Форма ввода / редактирования
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text("ФИО Сотрудника") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = roleInput,
                onValueChange = { roleInput = it },
                label = { Text("Должность") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = nfcIdInput,
                onValueChange = { nfcIdInput = it },
                label = { Text("NFC ID") },
                modifier = Modifier.fillMaxWidth()
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Сканировать NFC-тег")
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (editingEmployee == null) "Добавить сотрудника" else "Сохранить изменения")
            }

            if (editingEmployee != null) {
                TextButton(
                    onClick = {
                        editingEmployee = null
                        nameInput = ""
                        roleInput = ""
                        nfcIdInput = ""
                        nfcScanningEnabled = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Отмена")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Список сотрудников
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(employees) { employee ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = employee.name, style = MaterialTheme.typography.titleMedium)
                                Text(text = employee.role, style = MaterialTheme.typography.bodyMedium)
                                employee.nfcId?.let {
                                    if (it.isNotEmpty()) {
                                        Text(text = "NFC: $it", style = MaterialTheme.typography.bodySmall)
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
                                    Icon(Icons.Default.Edit, contentDescription = "Редактировать", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { onDeleteEmployee(employee) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
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
