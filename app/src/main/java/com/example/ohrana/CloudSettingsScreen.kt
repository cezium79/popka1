package com.example.ohrana

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val cloudManager = remember { CloudStorageManager(context) }
    
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
            
            // Загружаем текущие значения при инициализации
            LaunchedEffect(cloudManager) {
                oauthToken = cloudManager.getOAuthToken() ?: ""
                bucketName = cloudManager.getBucketName() ?: ""
                bucketPath = cloudManager.getBucketPath() ?: ""
                diskToken = cloudManager.getDiskToken() ?: ""
                diskPath = cloudManager.getDiskPath() ?: ""
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
