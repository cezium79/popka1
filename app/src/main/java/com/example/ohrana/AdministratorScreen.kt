package com.example.ohrana

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdministratorScreen(
    onNavigateToEmployeeList: () -> Unit,
    onNavigateToRoutes: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToCloudSettings: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefsManager = remember { SharedPrefsManager(context) }
    val scope = rememberCoroutineScope()

    var isStrictSequence by remember { mutableStateOf(prefsManager.isStrictSequenceEnabled()) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportStatus by remember { mutableStateOf(false) }
    var showImportStatus by remember { mutableStateOf(false) }
    var exportStatusMessage by remember { mutableStateOf("") }
    var importStatusMessage by remember { mutableStateOf("") }
    var exportFileName by remember { mutableStateOf("") }
    
    // Лаунчер для выбора файла импорта
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    try {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        val text = inputStream?.bufferedReader().use { it?.readText() }
                        
                        if (text != null) {
                            val success = prefsManager.importSettingsFromText(text)
                            withContext(Dispatchers.Main) {
                                importStatusMessage = if (success) {
                                    "Настройки успешно восстановлены!"
                                } else {
                                    "Ошибка при восстановлении настроек"
                                }
                                showImportStatus = true
                                showImportDialog = false
                                Toast.makeText(context, importStatusMessage, Toast.LENGTH_LONG).show()
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                importStatusMessage = "Не удалось прочитать файл"
                                showImportStatus = true
                                showImportDialog = false
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            importStatusMessage = "Ошибка: ${e.message}"
                            showImportStatus = true
                            showImportDialog = false
                        }
                    }
                }
            }
        }
    )
    
    // Лаунчер для создания файла экспорта через SAF
    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        val text = prefsManager.exportSettingsAsText()
                        outputStream.write(text.toByteArray())
                    }
                    withContext(Dispatchers.Main) {
                        exportStatusMessage = "Настройки сохранены через SAF"
                        showExportStatus = true
                        showExportDialog = false
                        Toast.makeText(context, "Настройки успешно сохранены!", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        exportStatusMessage = "Ошибка: ${e.message}"
                        showExportStatus = true
                        showExportDialog = false
                    }
                }
            }
        }
    }
    
    // Функция экспорта
    val exportSettingsFunction: () -> Unit = {
        // Используем SAF для создания файла экспорта
        val fileName = "ohrana_settings_backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.txt"
        exportFileName = fileName
        exportFileLauncher.launch(fileName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Панель администратора") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Кнопки навигации
            Button(
                onClick = onNavigateToEmployeeList,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).height(56.dp)
            ) {
                Text("Редактировать список сотрудников", fontSize = 16.sp)
            }
            
            Button(
                onClick = onNavigateToRoutes,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).height(56.dp)
            ) {
                Text("Редактирование маршрутов", fontSize = 16.sp)
            }
            
            Button(
                onClick = onNavigateToLogs,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).height(56.dp)
            ) {
                Text("Журналы", fontSize = 16.sp)
            }
            
            Button(
                onClick = onNavigateToCloudSettings,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).height(56.dp)
            ) {
                Text("Настройки облачных хранилищ", fontSize = 16.sp)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Кнопки экспорта и импорта
            Text(
                text = "Резервное копирование",
                fontSize = 18.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { showExportDialog = true },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Экспорт", fontSize = 14.sp)
                }
                
                Button(
                    onClick = { showImportDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Импорт", fontSize = 14.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Настройки контроля последовательности
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Строгий контроль",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            text = "Охранник обязан сканировать точки строго по порядку",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isStrictSequence,
                        onCheckedChange = { isChecked ->
                            isStrictSequence = isChecked
                            prefsManager.setStrictSequenceEnabled(isChecked)
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    
    // Диалог экспорта
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Экспорт настроек") },
            text = { Text("Сохранить все настройки в текстовый файл?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        exportSettingsFunction()
                    }
                ) {
                    Text("Экспортировать")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExportDialog = false }
                ) {
                    Text("Отмена")
                }
            }
        )
    }
    
    // Диалог импорта
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Импорт настроек") },
            text = { 
                Column {
                    Text("Выберите файл резервной копии для восстановления настроек.")
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { importFileLauncher.launch("text/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Выбрать файл")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showImportDialog = false }
                ) {
                    Text("Отмена")
                }
            }
        )
    }
    
    // Диалог статуса экспорта
    if (showExportStatus) {
        AlertDialog(
            onDismissRequest = { showExportStatus = false },
            title = { Text("Экспорт") },
            text = { Text(exportStatusMessage) },
            confirmButton = {
                TextButton(
                    onClick = { showExportStatus = false }
                ) {
                    Text("OK")
                }
            }
        )
    }
    
    // Диалог статуса импорта
    if (showImportStatus) {
        AlertDialog(
            onDismissRequest = { showImportStatus = false },
            title = { Text("Импорт") },
            text = { Text(importStatusMessage) },
            confirmButton = {
                TextButton(
                    onClick = { showImportStatus = false }
                ) {
                    Text("OK")
                }
            }
        )
    }
}
