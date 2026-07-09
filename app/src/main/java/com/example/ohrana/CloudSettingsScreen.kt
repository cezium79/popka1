package com.example.ohrana

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.HorizontalDivider
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import com.example.ohrana.SharedPrefsManager
import com.example.ohrana.CloudStorageManager
import com.example.ohrana.SharedPrefsManager.FileIoAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val cloudManager = remember { CloudStorageManager(context) }
    val tokenManager = remember { CloudTokenManager(context) }
    
    // Инициализируем токены при первом запуске
    LaunchedEffect(Unit) {
        tokenManager.initializeDefaultTokens()
    }
    
    // Список доступных токенов
    val tokens by remember { mutableStateOf(tokenManager.loadTokens()) }
    
    // Выбранный токен
    var selectedTokenName by remember {
        val defaultName = tokenManager.getDefaultTokenName()
        mutableStateOf(defaultName ?: if (tokens.isNotEmpty()) tokens[0].name else "")
    }
    
    // Состояние полей ввода для Yandex Cloud
    var oauthToken by remember { mutableStateOf("") }
    var bucketName by remember { mutableStateOf("") }
    var bucketPath by remember { mutableStateOf("") }
    
    // Состояние полей ввода для Yandex Disk
    var diskToken by remember { mutableStateOf("") }
    var diskPath by remember { mutableStateOf("") }
    
    // Состояние сохранения
    var saveStatus by remember { mutableStateOf<String?>(null) }
    
    // Флаг загрузки
    var isLoading by remember { mutableStateOf(false) }
    
    // Состояние цвета статуса
    var saveStatusColor by remember { mutableStateOf<Color>(Color.Unspecified) }
    
    // Состояние диалога выбора токена
    var showTokenDialog by remember { mutableStateOf(false) }
    
    // Состояние диалога добавления токена
    var showAddTokenDialog by remember { mutableStateOf(false) }
    
    // Состояние диалога настроек File.io
    var showFileIoSettings by remember { mutableStateOf(false) }
    
    // Состояние File.io
    var fileIoEnabled by remember { mutableStateOf(cloudManager.isFileIoEnabled()) }
    
    // Временные поля для добавления нового токена
    var newTokenName by remember { mutableStateOf("") }
    var newTokenValue by remember { mutableStateOf("") }
    var newTokenPath by remember { mutableStateOf("") }
    LaunchedEffect(selectedTokenName) {
        val selectedToken = tokens.find { it.name == selectedTokenName }
        if (selectedToken != null) {
            diskToken = selectedToken.token
            diskPath = selectedToken.path
        }
    }
    
    // Загружаем текущие значения при инициализации
    LaunchedEffect(cloudManager) {
        oauthToken = cloudManager.getOAuthToken() ?: ""
        bucketName = cloudManager.getBucketName() ?: ""
        bucketPath = cloudManager.getBucketPath() ?: ""
    }
    
    // Диалог выбора токена
    if (showTokenDialog) {
        Dialog(
            onDismissRequest = { showTokenDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier.padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Выберите токен",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    LazyColumn {
                        items(tokens) { token ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                                    .clickable {
                                        selectedTokenName = token.name
                                        tokenManager.setDefaultToken(token.name)
                                        showTokenDialog = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(if (token.name == selectedTokenName) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = token.name.first().toString(),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = token.name,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = token.path.ifEmpty { "Без пути" },
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedButton(
                        onClick = {
                            showTokenDialog = false
                            showAddTokenDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Добавить новый токен")
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    TextButton(
                        onClick = { showTokenDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Отмена")
                    }
                }
            }
        }
    }
    
    // Диалог добавления токена
    if (showAddTokenDialog) {
        Dialog(
            onDismissRequest = { showAddTokenDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier.padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Добавить токен",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = newTokenName,
                        onValueChange = { newTokenName = it },
                        label = { Text("Имя токена") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = newTokenValue,
                        onValueChange = { newTokenValue = it },
                        label = { Text("OAuth Token") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = newTokenPath,
                        onValueChange = { newTokenPath = it },
                        label = { Text("Путь (опционально)") },
                        placeholder = { Text("напр., Ohrana/Reports") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { showAddTokenDialog = false }
                        ) {
                            Text("Отмена")
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Button(
                            onClick = {
                                if (newTokenName.isNotEmpty() && newTokenValue.isNotEmpty()) {
                                    tokenManager.addToken(
                                        CloudTokenManager.TokenInfo(
                                            name = newTokenName,
                                            token = newTokenValue,
                                            path = newTokenPath
                                        )
                                    )
                                    tokenManager.setDefaultToken(newTokenName)
                                    selectedTokenName = newTokenName
                                    diskToken = newTokenValue
                                    diskPath = newTokenPath
                                    
                                    newTokenName = ""
                                    newTokenValue = ""
                                    newTokenPath = ""
                                    showAddTokenDialog = false
                                    
                                    Toast.makeText(
                                        context,
                                        "Токен добавлен и установлен по умолчанию!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Заполните все обязательные поля!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        ) {
                            Text("Сохранить")
                        }
                    }
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки облачных хранилищ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Устанавливаем цвет статуса внутри Column (в composable scope)
                saveStatusColor = MaterialTheme.colorScheme.onSurface
                
                Text(
                    text = "Настройка облачных хранилищ",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                
                // Выпадающий список выбора токена
                Text(
                    text = "Выберите токен:",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                OutlinedButton(
                    onClick = { showTokenDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text(selectedTokenName)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                
                // Кнопка добавления нового токена
                TextButton(
                    onClick = { showAddTokenDialog = true },
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Добавить новый токен")
                }
                
                Text(
                    text = "Настройте Yandex Cloud Storage для загрузки отчетов",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                OutlinedTextField(
                    value = oauthToken,
                    onValueChange = { oauthToken = it },
                    label = { Text("Yandex Cloud OAuth Token") },
                    placeholder = { Text("OAuth токен (начинается с AQAAAA...)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                
                OutlinedTextField(
                    value = bucketName,
                    onValueChange = { bucketName = it },
                    label = { Text("Имя бакета") },
                    placeholder = { Text("название вашего бакета") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
                
                OutlinedTextField(
                    value = bucketPath,
                    onValueChange = { bucketPath = it },
                    label = { Text("Путь в бакете (опционально)") },
                    placeholder = { Text("напр., shifts/2026") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Настройте Яндекс.Диск для загрузки отчетов",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                OutlinedTextField(
                    value = diskToken,
                    onValueChange = { diskToken = it },
                    label = { Text("Яндекс.Диск OAuth Token") },
                    placeholder = { Text("OAuth токен для Яндекс.Диска") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                
                OutlinedTextField(
                    value = diskPath,
                    onValueChange = { diskPath = it },
                    label = { Text("Папка на диске (опционально)") },
                    placeholder = { Text("напр., Ohrana/Shifts") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Кнопка настроек File.io
                Text(
                    text = "🔗 File.io - Анонимное временное хранение",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                OutlinedButton(
                    onClick = { showFileIoSettings = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Настроить File.io", fontSize = 14.sp)
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = {
                        saveSettings(
                            context,
                            cloudManager,
                            oauthToken,
                            bucketName,
                            bucketPath,
                            diskToken,
                            diskPath
                        ) { status, color ->
                            saveStatus = status
                            saveStatusColor = color
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Text("Сохранить настройки")
                }
                
                if (saveStatus != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = saveStatus ?: "",
                        color = saveStatusColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Диалог настроек File.io
                if (showFileIoSettings) {
                    FileIoSettingsScreen(onDismiss = { showFileIoSettings = false }, cloudManager = cloudManager)
                }
                
                // Инструкция по получению OAuth token
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = "ℹ️ Как получить OAuth token:",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "1. Перейдите на https://oauth.yandex.ru/\n2. Авторизуйтесь с вашим Yandex аккаунтом\n3. Для Yandex Cloud выберите приложение «Приложение для облачного хранилища»\n4. Для Яндекс.Диск используйте тот же token",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// Диалог настроек File.io
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileIoSettingsScreen(
    onDismiss: () -> Unit,
    cloudManager: CloudStorageManager
) {
    val context = LocalContext.current
    val prefs = remember { SharedPrefsManager(context) }
    
    // Состояния для настроек File.io
    var fileIoEnabled by remember { mutableStateOf(cloudManager.isFileIoEnabled()) }
    var fileIoAction by remember { mutableStateOf(prefs.getFileIoAction()) }
    var fileIoPhone by remember { mutableStateOf(prefs.getFileIoPhone()) }
    var fileIoEmail by remember { mutableStateOf(prefs.getFileIoEmail()) }
    var fileIoTelegram by remember { mutableStateOf(prefs.getFileIoTelegram()) }
    
    // Флаги раскрытия выпадающих меню
    var showActionMenu by remember { mutableStateOf(false) }
    
    // Флаг показа подтверждения сохранения
    var showSaveConfirmation by remember { mutableStateOf(false) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Заголовок
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔗 Настройки File.io",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть"
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                // Описание
                Text(
                    text = "File.io - это сервис для анонимного временного хранения файлов. Файлы хранятся 14 дней и не требуют регистрации.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                // Переключатель включения
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Использовать File.io",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Автоматически загружать отчет при закрытии смены",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = fileIoEnabled,
                        onCheckedChange = { isChecked ->
                            fileIoEnabled = isChecked
                            cloudManager.setFileIoEnabled(isChecked)
                        }
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                
                // Выбор действия с ссылкой
                Text(
                    text = "Действие с ссылкой:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedButton(
                    onClick = { showActionMenu = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(fileIoAction.name.replace("_", " ").uppercase())
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                
                // Выпадающее меню выбора действия
                DropdownMenu(
                    expanded = showActionMenu,
                    onDismissRequest = { showActionMenu = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FileIoAction.values().forEach { action ->
                        DropdownMenuItem(
                            text = { Text(action.name.replace("_", " ").uppercase()) },
                            onClick = {
                                fileIoAction = action
                                prefs.saveFileIoAction(action)
                                showActionMenu = false
                            },
                            leadingIcon = {
                                when (action) {
                                    FileIoAction.SAVE_TO_DEVICE -> Icon(Icons.Default.Save, contentDescription = null)
                                    FileIoAction.SEND_SMS -> Icon(Icons.Default.Sms, contentDescription = null)
                                    FileIoAction.SEND_EMAIL -> Icon(Icons.Default.Email, contentDescription = null)
                                    FileIoAction.SEND_TELEGRAM -> Icon(Icons.Default.Public, contentDescription = null)
                                    FileIoAction.COPY_TO_CLIPBOARD -> Icon(Icons.Default.ContentCopy, contentDescription = null)
                                }
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                // Поля ввода в зависимости от выбранного действия
                when (fileIoAction) {
                    FileIoAction.SEND_SMS -> {
                        OutlinedTextField(
                            value = fileIoPhone,
                            onValueChange = { fileIoPhone = it },
                            label = { Text("Номер телефона (для SMS)") },
                            placeholder = { Text("напр., +79001234567") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    FileIoAction.SEND_EMAIL -> {
                        OutlinedTextField(
                            value = fileIoEmail,
                            onValueChange = { fileIoEmail = it },
                            label = { Text("Email получателя") },
                            placeholder = { Text("напр., admin@example.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    FileIoAction.SEND_TELEGRAM -> {
                        OutlinedTextField(
                            value = fileIoTelegram,
                            onValueChange = { fileIoTelegram = it },
                            label = { Text("Username Telegram") },
                            placeholder = { Text("напр., @username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    else -> {
                        // Для других действий поля не нужны
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Кнопки действий
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss
                    ) {
                        Text("Отмена")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            // Сохраняем все настройки
                            prefs.saveFileIoPhone(fileIoPhone)
                            prefs.saveFileIoEmail(fileIoEmail)
                            prefs.saveFileIoTelegram(fileIoTelegram)
                            showSaveConfirmation = true
                        }
                    ) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
    
    // Диалог подтверждения сохранения
    if (showSaveConfirmation) {
        AlertDialog(
            onDismissRequest = { showSaveConfirmation = false },
            title = { Text("Настройки сохранены") },
            text = { Text("Настройки File.io успешно сохранены.") },
            confirmButton = {
                TextButton(
                    onClick = { showSaveConfirmation = false }
                ) {
                    Text("OK")
                }
            }
        )
    }
}

private fun saveSettings(
    context: Context,
    cloudManager: CloudStorageManager,
    cloudToken: String,
    bucket: String,
    path: String,
    diskToken: String,
    diskPath: String,
    onResult: (String, Color) -> Unit
) {
    // Сохраняем настройки Yandex Cloud
    val cloudTokenSaved = cloudManager.saveOAuthToken(cloudToken)
    val bucketSaved = cloudManager.saveBucketName(bucket)
    val pathSaved = cloudManager.saveBucketPath(path)
    
    // Сохраняем настройки Yandex Disk
    val diskTokenSaved = cloudManager.saveDiskToken(diskToken)
    val diskPathSaved = cloudManager.saveDiskPath(diskPath)
    
    if (cloudTokenSaved && bucketSaved && pathSaved && diskTokenSaved && diskPathSaved) {
        onResult("Настройки сохранены успешно!", Color(76, 175, 80))
        
        Toast.makeText(
            context,
            "Настройки Yandex Cloud и Диск сохранены!",
            Toast.LENGTH_LONG
        ).show()
    } else {
        onResult("Ошибка при сохранении настроек", Color(244, 67, 54))
        
        Toast.makeText(
            context,
            "Ошибка при сохранении настроек",
            Toast.LENGTH_LONG
        ).show()
    }
}
