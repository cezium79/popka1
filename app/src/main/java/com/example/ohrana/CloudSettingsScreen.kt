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
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
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
import androidx.compose.material3.Switch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import com.example.ohrana.ui.components.OhranaOutlinedButton
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import com.example.ohrana.SharedPrefsManager
import com.example.ohrana.CloudStorageManager
import androidx.activity.compose.BackHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewFontScale



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

        // Переключатель типа отчета (HTML или PDF)
        var Opochki by remember { mutableStateOf(true) } // true = HTML, false = PDF

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
        var smtpRecipient by remember {
            mutableStateOf(
                prefsManager.getSmtpRecipient() ?: ""
            )
        } // Email получателя для SMTP

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
                            color = Color(0xFFFFFFFF)
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
                                            color = Color(0xFFFFFFFF)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = token.name,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFFFFFFFF)
                                        )
                                        Text(
                                            text = token.path.ifEmpty { "Без пути" },
                                            fontSize = 12.sp,
                                            color = Color(0xFFFFFFFF)
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
                            Text("Добавить новый токен", color = Color(0xFFFFFFFF))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = { showTokenDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Отмена", color = Color(0xFFFFFFFF))
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
                            color = Color(0xFFFFFFFF)
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = newTokenName,
                            onValueChange = { newTokenName = it },
                            label = { Text("Имя токена", color = Color(0xFFFFFFFF)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = newTokenValue,
                            onValueChange = { newTokenValue = it },
                            label = { Text("OAuth Token", color = Color(0xFFFFFFFF)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = newTokenPath,
                            onValueChange = { newTokenPath = it },
                            label = { Text("Путь (опционально)", color = Color(0xFFFFFFFF)) },
                            placeholder = { Text("напр., Ohrana/Reports", color = Color(0xFFFFFFFF)) },
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
                                Text("Отмена", color = Color(0xFFFFFFFF))
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
                                Text("Сохранить", color = Color(0xFFFFFFFF))
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
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF616161) // Серый фон как у экрана
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Размытый фон
                BlurredBackground()

                // Контент экрана
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Устанавливаем цвет статуса внутри Column (в composable scope)
                    saveStatusColor = MaterialTheme.colorScheme.onSurface

                    // Кнопки навигации (тёмно-серые с белым текстом)
                    OhranaOutlinedButton(
                        text = "Выберите токен",
                        onClick = { showTokenDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .height(56.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF424242), // Тёмно-серый
                            contentColor = Color(0xFFFFFFFF)    // Белый текст
                        ),
                        elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                            defaultElevation = 8.dp,
                            pressedElevation = 16.dp,
                            disabledElevation = 8.dp
                        ),
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 20.sp
                        )
                    )

                    // Внутренний блок для выбора токена
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF595757))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = "Выберите токен:",
                                fontSize = 14.sp,
                                color = Color(0xFFFFFFFF),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            OutlinedButton(
                                onClick = { showTokenDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                            ) {
                                Text(selectedTokenName, color = Color(0xFFFFFFFF))
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }

                            // Кнопка добавления нового токена
                            TextButton(
                                onClick = { showAddTokenDialog = true },
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Добавить новый токен", color = Color(0xFFFFFFFF))
                            }
                        }
                    }


                    // Настройки Yandex Cloud
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF595757))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = "Yandex Cloud Storage",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFFFFF)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = oauthToken,
                                onValueChange = { oauthToken = it },
                                label = { Text("Yandex Cloud OAuth Token", color = Color(0xFFFFFFFF)) },
                                placeholder = { Text("OAuth токен (начинается с AQAAAA...)", color = Color(0xFFFFFFFF)) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                            )

                            OutlinedTextField(
                                value = bucketName,
                                onValueChange = { bucketName = it },
                                label = { Text("Имя бакета", color = Color(0xFFFFFFFF)) },
                                placeholder = { Text("название вашего бакета", color = Color(0xFFFFFFFF)) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            )

                            OutlinedTextField(
                                value = bucketPath,
                                onValueChange = { bucketPath = it },
                                label = { Text("Путь в бакете (опционально)", color = Color(0xFFFFFFFF)) },
                                placeholder = { Text("напр., shifts/2026", color = Color(0xFFFFFFFF)) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Настройки Яндекс.Диск
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF595757))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = "Яндекс.Диск",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFFFFF)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = diskToken,
                                onValueChange = { diskToken = it },
                                label = { Text("Яндекс.Диск OAuth Token", color = Color(0xFFFFFFFF)) },
                                placeholder = { Text("OAuth токен для Яндекс.Диска", color = Color(0xFFFFFFFF)) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                            )

                            OutlinedTextField(
                                value = diskPath,
                                onValueChange = { diskPath = it },
                                label = { Text("Папка на диске (опционально)", color = Color(0xFFFFFFFF)) },
                                placeholder = { Text("напр., Ohrana/Shifts", color = Color(0xFFFFFFFF)) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // Настройки действия со ссылкой
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF595757))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = "Действие со ссылкой",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFFFFF)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Выпадающее меню действия со ссылкой
                            var showLinkActionMenu by remember { mutableStateOf(false) }

                            Text(
                                text = "Выберите действие:",
                                fontSize = 14.sp,
                                color = Color(0xFFFFFFFF),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            Box {
                                OutlinedButton(
                                    onClick = { showLinkActionMenu = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp)
                                ) {
                                    Text(linkAction.description, color = Color(0xFFFFFFFF))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }

                                DropdownMenu(
                                    expanded = showLinkActionMenu,
                                    onDismissRequest = { showLinkActionMenu = false }
                                ) {
                                    SharedPrefsManager.LinkAction.values().forEach { action ->
                                        DropdownMenuItem(
                                            text = { Text(action.description, color = Color(0xFFFFFFFF)) },
                                            onClick = {
                                                linkAction = action
                                                showLinkActionMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Поля ввода в зависимости от выбранного действия
                            when (linkAction) {
                                SharedPrefsManager.LinkAction.SMS -> {
                                    OutlinedTextField(
                                        value = smsPhone,
                                        onValueChange = { smsPhone = it },
                                        label = { Text("Номер телефона (SMS)", color = Color(0xFFFFFFFF)) },
                                        placeholder = { Text("напр., +79001234567", color = Color(0xFFFFFFFF)) },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                                    )
                                }

                                SharedPrefsManager.LinkAction.EMAIL -> {
                                    OutlinedTextField(
                                        value = email,
                                        onValueChange = { email = it },
                                        label = { Text("Email получателя", color = Color(0xFFFFFFFF)) },
                                        placeholder = { Text("напр., example@email.com", color = Color(0xFFFFFFFF)) },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                                    )
                                }

                                SharedPrefsManager.LinkAction.EMAIL_SMTP -> {
                                    // Поле для SMTP хоста
                                    OutlinedTextField(
                                        value = smtpHost,
                                        onValueChange = { smtpHost = it },
                                        label = { Text("SMTP хост", color = Color(0xFFFFFFFF)) },
                                        placeholder = { Text("smtp.yandex.ru", color = Color(0xFFFFFFFF)) },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp)
                                    )

                                    // Поле для SMTP порта
                                    OutlinedTextField(
                                        value = smtpPort,
                                        onValueChange = { smtpPort = it },
                                        label = { Text("SMTP порт", color = Color(0xFFFFFFFF)) },
                                        placeholder = { Text("465", color = Color(0xFFFFFFFF)) },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )

                                    // Поле для SMTP username (email)
                                    OutlinedTextField(
                                        value = smtpUsername,
                                        onValueChange = { smtpUsername = it },
                                        label = { Text("Email отправителя", color = Color(0xFFFFFFFF)) },
                                        placeholder = { Text("ваш_email@yandex.ru", color = Color(0xFFFFFFFF)) },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                                    )

                                    // Поле для SMTP пароля (пароль приложения)
                                    var showSmtpPassword by remember { mutableStateOf(false) }
                                    Column {
                                        OutlinedTextField(
                                            value = smtpPassword,
                                            onValueChange = { smtpPassword = it },
                                            label = { Text("Пароль приложения", color = Color(0xFFFFFFFF)) },
                                            placeholder = { Text("введите пароль приложения Yandex", color = Color(0xFFFFFFFF)) },
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp),
                                            visualTransformation = if (showSmtpPassword) {
                                                androidx.compose.ui.text.input.VisualTransformation.None
                                            } else {
                                                PasswordVisualTransformation()
                                            },
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = if (showSmtpPassword) Icons.Default.Close else Icons.Default.Public,
                                                    contentDescription = if (showSmtpPassword) "Скрыть пароль" else "Показать пароль",
                                                    modifier = Modifier.clickable {
                                                        showSmtpPassword = !showSmtpPassword
                                                    }
                                                )
                                            }
                                        )
                                        Text(
                                            text = "* Создайте пароль приложения в настройках безопасности Yandex (даже без 2FA)",
                                            fontSize = 10.sp,
                                            color = Color(0xFFFFFFFF)
                                        )
                                        Text(
                                            text = "SMTP username должен совпадать с Yandex email",
                                            fontSize = 10.sp,
                                            color = Color(0xFFFFFFFF)
                                        )
                                    }

                                    // Поле для email получателя
                                    OutlinedTextField(
                                        value = smtpRecipient,
                                        onValueChange = { smtpRecipient = it },
                                        label = { Text("Email получателя", color = Color(0xFFFFFFFF)) },
                                        placeholder = { Text("куда отправить отчет", color = Color(0xFFFFFFFF)) },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                                    )
                                }

                                SharedPrefsManager.LinkAction.TELEGRAM -> {
                                    OutlinedTextField(
                                        value = telegram,
                                        onValueChange = { telegram = it },
                                        label = { Text("Username Telegram", color = Color(0xFFFFFFFF)) },
                                        placeholder = { Text("напр., @username", color = Color(0xFFFFFFFF)) },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp)
                                    )
                                }

                                SharedPrefsManager.LinkAction.COPY -> {
                                    // Для копирования дополнительных полей не требуется
                                }

                                SharedPrefsManager.LinkAction.SAVE_TO_DEVICE -> {
                                    // Для сохранения на устройстве дополнительных полей не требуется
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Большой переключатель
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF595757))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "PDF", color = if (!Opochki) Color(0xFFFFFFFF) else Color(0xFF9E9E9E))
                                Switch(
                                    checked = Opochki,
                                    onCheckedChange = { Opochki = it }
                                )
                                Text(text = "HTML", color = if (Opochki) Color(0xFFFFFFFF) else Color(0xFF9E9E9E))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Кнопка сохранения
                    OhranaOutlinedButton(
                        text = "Сохранить настройки",
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF424242),
                            contentColor = Color(0xFFFFFFFF)
                        ),
                        elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                            defaultElevation = 8.dp,
                            pressedElevation = 16.dp,
                            disabledElevation = 8.dp
                        ),
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 18.sp
                        )
                    )

                    if (saveStatus != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = saveStatus ?: "",
                            color = saveStatusColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Инструкция по получению OAuth token
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF595757))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = "Как получить OAuth token:",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFFFFF)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "1. Перейдите на https://oauth.yandex.ru/\n2. Авторизуйтесь с вашим Yandex аккаунтом\n3. Для Yandex Cloud выберите приложение «Приложение для облачного хранилища»\n4. Для Яндекс.Диск используйте тот же token",
                                fontSize = 12.sp,
                                color = Color(0xFFFFFFFF)
                            )
                        }
                    }
                }
            }
        }

        // Обработка системной кнопки "Назад"
        BackHandler(onBack = onBack)
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
