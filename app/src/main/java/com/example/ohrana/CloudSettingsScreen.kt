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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    
    // Временные поля для добавления нового токена
    var newTokenName by remember { mutableStateOf("") }
    var newTokenValue by remember { mutableStateOf("") }
    var newTokenPath by remember { mutableStateOf("") }
    
    // Состояние для действия со ссылкой (SMS, Email, Telegram)
    val prefsManager = remember { SharedPrefsManager(context) }
    var linkAction by remember { mutableStateOf(prefsManager.getSmsAction()) }
    var smsPhone by remember { mutableStateOf(prefsManager.getSmsPhone()) }
    var email by remember { mutableStateOf(prefsManager.getFileIoEmail() ?: "") }
    var telegram by remember { mutableStateOf(prefsManager.getFileIoTelegram() ?: "") }
    
    // SMTP настройки
    var smtpHost by remember { mutableStateOf(prefsManager.getSmtpHost() ?: "smtp.yandex.ru") }
    var smtpPort by remember { mutableStateOf(prefsManager.getSmtpPort().toString()) }
    var smtpUsername by remember { mutableStateOf(prefsManager.getSmtpUsername() ?: "") }
    var smtpPassword by remember { mutableStateOf(prefsManager.getSmtpPassword() ?: "") }
    var smtpRecipient by remember { mutableStateOf(prefsManager.getSmtpRecipient() ?: "") } // Email получателя для SMTP
    
    var showSmsDialog by remember { mutableStateOf(false) }
    var showEmailDialog by remember { mutableStateOf(false) }
    var showTelegramDialog by remember { mutableStateOf(false) }
    LaunchedEffect(selectedTokenName) {
        val selectedToken = tokens.find { it.name == selectedTokenName }
        if (selectedToken != null) {
            diskToken = selectedToken.token
            diskPath = selectedToken.path
        }
    }
    
    // Загружаем текущие значения при инициализации
    LaunchedEffect(cloudManager, prefsManager) {
        oauthToken = cloudManager.getOAuthToken() ?: ""
        bucketName = cloudManager.getBucketName() ?: ""
        bucketPath = cloudManager.getBucketPath() ?: ""
        diskToken = cloudManager.getDiskToken() ?: ""
        diskPath = cloudManager.getDiskPath() ?: ""
        linkAction = prefsManager.getSmsAction()
        smsPhone = prefsManager.getSmsPhone() ?: ""
        email = prefsManager.getFileIoEmail() ?: ""
        telegram = prefsManager.getFileIoTelegram() ?: ""
        smtpHost = prefsManager.getSmtpHost() ?: "smtp.yandex.ru"
        smtpPort = prefsManager.getSmtpPort().toString()
        smtpUsername = prefsManager.getSmtpUsername() ?: ""
        smtpPassword = prefsManager.getSmtpPassword() ?: ""
        smtpRecipient = prefsManager.getSmtpRecipient() ?: "" // Email получателя для SMTP
    }
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
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Выбор дизайна отчета
                var showReportDesignMenu by remember { mutableStateOf(false) }
                
                Text(
                    text = "Дизайн отчета в формате HTML:",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Box {
                    OutlinedButton(
                        onClick = { showReportDesignMenu = true },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        val currentDesign = prefsManager.getReportDesign()
                        val designDescription = if (currentDesign == "minimal") "Минималистичный" else "Полный (по умолчанию)"
                        Text(designDescription)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    
                    DropdownMenu(
                        expanded = showReportDesignMenu,
                        onDismissRequest = { showReportDesignMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Полный (по умолчанию)") },
                            onClick = {
                                prefsManager.saveReportDesign("full")
                                showReportDesignMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Минималистичный") },
                            onClick = {
                                prefsManager.saveReportDesign("minimal")
                                showReportDesignMenu = false
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Выпадающее меню действия со ссылкой
                var showLinkActionMenu by remember { mutableStateOf(false) }
                
                Text(
                    text = "Действие со ссылкой после загрузки:",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Box {
                    OutlinedButton(
                        onClick = { showLinkActionMenu = true },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Text(linkAction.description)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    
                    DropdownMenu(
                        expanded = showLinkActionMenu,
                        onDismissRequest = { showLinkActionMenu = false }
                    ) {
                        SharedPrefsManager.LinkAction.values().forEach { action ->
                            DropdownMenuItem(
                                text = { Text(action.description) },
                                onClick = {
                                    linkAction = action
                                    showLinkActionMenu = false
                                }
                            )
                        }
                    }
                }
                
                // Поля ввода в зависимости от выбранного действия
                when (linkAction) {
                    SharedPrefsManager.LinkAction.SMS -> {
                        OutlinedTextField(
                            value = smsPhone,
                            onValueChange = { smsPhone = it },
                            label = { Text("Номер телефона (SMS)") },
                            placeholder = { Text("напр., +79001234567") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )
                    }
                    SharedPrefsManager.LinkAction.EMAIL -> {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email получателя") },
                            placeholder = { Text("напр., example@email.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )
                    }
                    SharedPrefsManager.LinkAction.EMAIL_SMTP -> {
                        // Поле для SMTP хоста
                        OutlinedTextField(
                            value = smtpHost,
                            onValueChange = { smtpHost = it },
                            label = { Text("SMTP хост") },
                            placeholder = { Text("smtp.yandex.ru") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        )
                        
                        // Поле для SMTP порта
                        OutlinedTextField(
                            value = smtpPort,
                            onValueChange = { smtpPort = it },
                            label = { Text("SMTP порт") },
                            placeholder = { Text("465") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        
                        // Поле для SMTP username (email)
                        OutlinedTextField(
                            value = smtpUsername,
                            onValueChange = { smtpUsername = it },
                            label = { Text("Email отправителя") },
                            placeholder = { Text("ваш_email@yandex.ru") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )
                        
                        // Поле для SMTP пароля (пароль приложения)
                        var showSmtpPassword by remember { mutableStateOf(false) }
                        Column {
                            OutlinedTextField(
                                value = smtpPassword,
                                onValueChange = { smtpPassword = it },
                                label = { Text("Пароль приложения") },
                                placeholder = { Text("введите пароль приложения Yandex") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                visualTransformation = if (showSmtpPassword) {
                                    androidx.compose.ui.text.input.VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = if (showSmtpPassword) Icons.Default.Close else Icons.Default.Public,
                                        contentDescription = if (showSmtpPassword) "Скрыть пароль" else "Показать пароль",
                                        modifier = Modifier.clickable { showSmtpPassword = !showSmtpPassword }
                                    )
                                }
                            )
                            Text(
                                text = "* Создайте пароль приложения в настройках безопасности Yandex (даже без 2FA)",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "SMTP username должен совпадать с Yandex email",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        
                        // Поле для email получателя
                        OutlinedTextField(
                            value = smtpRecipient,
                            onValueChange = { smtpRecipient = it },
                            label = { Text("Email получателя") },
                            placeholder = { Text("куда отправить отчет") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )
                    }
                    SharedPrefsManager.LinkAction.TELEGRAM -> {
                        OutlinedTextField(
                            value = telegram,
                            onValueChange = { telegram = it },
                            label = { Text("Username Telegram") },
                            placeholder = { Text("напр., @username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        )
                    }
                    SharedPrefsManager.LinkAction.COPY -> {
                        // Для копирования дополнительных полей не требуется
                    }
                    SharedPrefsManager.LinkAction.SAVE_TO_DEVICE -> {
                        // Для сохранения на устройстве дополнительных полей не требуется
                    }
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
                            diskPath,
                            linkAction,
                            smsPhone,
                            email,
                            telegram,
                            smtpHost,
                            smtpPort,
                            smtpUsername,
                            smtpPassword,
                            smtpRecipient // Добавлено: email получателя для SMTP
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

private fun saveSettings(
    context: Context,
    cloudManager: CloudStorageManager,
    cloudToken: String,
    bucket: String,
    path: String,
    diskToken: String,
    diskPath: String,
    linkAction: SharedPrefsManager.LinkAction,
    smsPhone: String,
    email: String,
    telegram: String,
    smtpHost: String,
    smtpPort: String,
    smtpUsername: String,
    smtpPassword: String,
    smtpRecipient: String, // Добавлено: email получателя для SMTP
    onResult: (String, Color) -> Unit
) {
    val prefsManager = SharedPrefsManager(context)
    // Сохраняем настройки Yandex Cloud
    val cloudTokenSaved = cloudManager.saveOAuthToken(cloudToken)
    val bucketSaved = cloudManager.saveBucketName(bucket)
    val pathSaved = cloudManager.saveBucketPath(path)
    
    // Сохраняем настройки Yandex Disk
    val diskTokenSaved = cloudManager.saveDiskToken(diskToken)
    val diskPathSaved = cloudManager.saveDiskPath(diskPath)
    
    // Сохраняем настройки действия со ссылкой
    prefsManager.saveSmsAction(linkAction)
    prefsManager.saveSmsPhone(smsPhone)
    prefsManager.saveFileIoEmail(email)
    prefsManager.saveFileIoTelegram(telegram)
    
    // Сохраняем SMTP настройки
    prefsManager.saveSmtpHost(smtpHost)
    prefsManager.saveSmtpPort(smtpPort.toIntOrNull() ?: 465)
    prefsManager.saveSmtpUsername(smtpUsername)
    prefsManager.saveSmtpPassword(smtpPassword)
    
    // Сохраняем email получателя для SMTP
    prefsManager.saveSmtpRecipient(smtpRecipient)
    
    // Сохраняем дизайн отчета
    // (дизайн уже сохраняется при выборе в UI)
    
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
