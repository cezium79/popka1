package com.example.ohrana

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.example.ohrana.ui.components.OhranaOutlinedButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.BackHandler
import android.content.Intent
import android.os.Build
import android.content.Context
import android.app.ActivityManager
import android.app.Service
import android.content.ComponentName
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdministratorScreen(
    onNavigateToEmployeeList: () -> Unit,
    onNavigateToRoutes: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToCloudSettings: () -> Unit,
    onBack: () -> Unit,
    onNavigateToSoundSettings: () -> Unit
) {
    val context = LocalContext.current
    val prefsManager = remember { SharedPrefsManager(context) }
    val scope = rememberCoroutineScope()

    var isStrictSequence by remember { mutableStateOf(prefsManager.isStrictSequenceEnabled()) }
    var isAutoEndRound by remember { mutableStateOf(prefsManager.isAutoEndRoundEnabled()) }
    var useSounds by remember { mutableStateOf(prefsManager.isSoundEnabled()) }
    var guardsCount by remember { mutableStateOf(prefsManager.getGuardsCount()) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportStatus by remember { mutableStateOf(false) }
    var showImportStatus by remember { mutableStateOf(false) }
    var exportStatusMessage by remember { mutableStateOf("") }
    var importStatusMessage by remember { mutableStateOf("") }
    var exportFileName by remember { mutableStateOf("") }
    val archiveServiceRunning = remember { mutableStateOf(false) }

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
                                Toast.makeText(context, importStatusMessage, Toast.LENGTH_LONG)
                                    .show()
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
                        Toast.makeText(context, "Настройки успешно сохранены!", Toast.LENGTH_LONG)
                            .show()
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF616161) // Серый фон как у экрана
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize()
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
                // Кнопки навигации (тёмно-серые с белым текстом)
                OhranaOutlinedButton(
                    text = "Список сотрудников",
                    onClick = onNavigateToEmployeeList,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).height(56.dp),
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

                OhranaOutlinedButton(
                    text = "Маршруты",
                    onClick = onNavigateToRoutes,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).height(56.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF424242), // Тёмно-серый
                        contentColor = Color(0xFFFFFFFF)    // Белый текст
                    ),
                    elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 16.dp,
                        disabledElevation = 8.dp
                    ),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 20.sp
                    )
                )

                OhranaOutlinedButton(
                    text = "Журналы",
                    onClick = onNavigateToLogs,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF424242), // Тёмно-серый
                        contentColor = Color(0xFFFFFFFF)    // Белый текст
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 16.dp,
                        disabledElevation = 8.dp
                    ),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 20.sp
                    )
                )

                OhranaOutlinedButton(
                    text = "Настройки облока",
                    onClick = onNavigateToCloudSettings,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF424242), // Тёмно-серый
                        contentColor = Color(0xFFFFFFFF)    // Белый текст
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 16.dp,
                        disabledElevation = 8.dp
                    ),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 20.sp
                    )
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Кнопки экспорта и импорта
                Text(
                    text = "Резервное копирование настроек",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OhranaOutlinedButton(
                        onClick = { showExportDialog = true },
                        text = "Экспорт",
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF424242),
                            contentColor = Color(0xFFFFFFFF)
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 8.dp,
                            pressedElevation = 16.dp,
                            disabledElevation = 8.dp
                        ),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 18.sp
                        )
                    )

                    OhranaOutlinedButton(
                        onClick = { showImportDialog = true },
                        text = "Импорт",
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 8.dp,
                            pressedElevation = 16.dp,
                            disabledElevation = 8.dp
                        ),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 18.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Настройки контроля последовательности
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF595757))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(// Заголовок карточки
                                text = "Строгий контроль",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFFFFFFF),
                                fontWeight = FontWeight.Bold
                            )
                            Text(//описание положения свича
                                text = if (isStrictSequence) {
                                    "Охранник обязан соблюдать последовательность маршрута"
                                } else {
                                    "Охранник приходит маршрут как хочет, анализ не производится"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFFFFFF)
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

                Spacer(modifier = Modifier.height(16.dp))

                // Настройки автоматического завершения обхода
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF595757))
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
                                text = "Автоматическое завершение обхода",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFFFFFFF),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isAutoEndRound) {
                                    "Обход завершается автоматически после последнего чекпоинта"
                                } else {
                                    "Охранник должен сам завершать обход кнопкой"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFFFFFF)
                            )
                        }

                        Switch(
                            checked = isAutoEndRound,
                            onCheckedChange = { isChecked ->
                                isAutoEndRound = isChecked
                                prefsManager.setAutoEndRoundEnabled(isChecked)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Настройка звукового сопровождения
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF595757))
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
                                text = "Использовать звуки",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFFFFFFF),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (useSounds) {
                                    "Звуки включены"
                                } else {
                                    "Звуки выключены"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFFFFFF)
                            )
                        }

                        Switch(
                            checked = useSounds,
                            onCheckedChange = { isChecked ->
                                useSounds = isChecked
                                prefsManager.setSoundEnabled(isChecked)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Кнопка "Настройки звука"
                OhranaOutlinedButton(
                    text = "Настройки звука",
                    onClick = onNavigateToSoundSettings,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF424242),
                        contentColor = Color(0xFFFFFFFF)
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 16.dp,
                        disabledElevation = 8.dp
                    ),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 18.sp
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Настройки архивации данных
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF595757))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Архивация данных",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFFFFF)
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "Архивирование данных смен старше 7 дней",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFFFFF)
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Кнопки управления архивацией
                        OhranaOutlinedButton(
                            text = if (archiveServiceRunning.value) "Остановить архивацию" else "Запустить архивацию",
                            onClick = {
                                scope.launch {
                                    if (archiveServiceRunning.value) {
                                        stopArchiveService(context, archiveServiceRunning)
                                    } else {
                                        startArchiveService(context, archiveServiceRunning)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF424242),
                                contentColor = Color(0xFFFFFFFF)
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 8.dp,
                                pressedElevation = 16.dp,
                                disabledElevation = 8.dp
                            ),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OhranaOutlinedButton(
                            text = "Архивировать данные сейчас",
                            onClick = {
                                scope.launch {
                                    archiveNow(context)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
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
                                fontSize = 16.sp
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val archiveSize = prefsManager.getArchiveSize()
                        val archiveFileCount = prefsManager.getArchiveFileCount()
                        
                        Text(
                            text = "Размер архива: ${formatFileSize(archiveSize)} (${archiveFileCount} файлов)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE0E0E0)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Настройка количества охранников
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
                                text = "Состав смены",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )

                            // Выпадающее меню для выбора количества охранников
                            var showGuardDropdown by remember { mutableStateOf(false) }

                            Box {
                                OhranaOutlinedButton(
                                    onClick = { showGuardDropdown = true },
                                    modifier = Modifier.width(260.dp).height(48.dp),
                                    text = when (guardsCount) {
                                        1 -> "1 охранник (старший)"
                                        2 -> "Старший и 1 охранник"
                                        3 -> "Старший и 2 охранника"
                                        else -> "Выберите количество"
                                    },
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
                                        fontSize = 16.sp
                                    )
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Раскрыть меню",
                                    modifier = Modifier
                                        .width(24.dp)
                                        .height(24.dp)
                                        .align(Alignment.CenterEnd)
                                )

                                DropdownMenu(
                                    modifier = Modifier.width(260.dp),
                                    expanded = showGuardDropdown,
                                    onDismissRequest = { showGuardDropdown = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("1 охранник (старший)") },
                                        onClick = {
                                            // Проверяем, есть ли активная смена
                                            if (prefsManager.isAnyShiftActive()) {
                                                // Закрываем текущую смену
                                                prefsManager.closeCurrentShift()
                                            }
                                            guardsCount = 1
                                            prefsManager.setGuardsCount(1)
                                            showGuardDropdown = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Старший и 1 охранник") },
                                        onClick = {
                                            // Проверяем, есть ли активная смена
                                            if (prefsManager.isAnyShiftActive()) {
                                                // Закрываем текущую смену
                                                prefsManager.closeCurrentShift()
                                            }
                                            guardsCount = 2
                                            prefsManager.setGuardsCount(2)
                                            showGuardDropdown = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Старший и 2 охранника") },
                                        onClick = {
                                            // Проверяем, есть ли активная смена
                                            if (prefsManager.isAnyShiftActive()) {
                                                // Закрываем текущую смену
                                                prefsManager.closeCurrentShift()
                                            }
                                            guardsCount = 3
                                            prefsManager.setGuardsCount(3)
                                            showGuardDropdown = false
                                        }
                                    )
                                }
                            }
                        }
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

        // Обработка системной кнопки "Назад"
        BackHandler(onBack = onBack)
    }
}

/**
 * Проверяет, работает ли служба архивации
 */
fun isArchiveServiceRunning(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    val services = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        manager.getRunningServices(Integer.MAX_VALUE)
    } else {
        @Suppress("DEPRECATION")
        manager.getRunningServices(Integer.MAX_VALUE)
    }
    for (service in services) {
        if (ArchiveService::class.java.name == service.service.className) {
            return true
        }
    }
    return false
}

/**
 * Форматирует размер файла в читаемый вид
 */
fun formatFileSize(sizeBytes: Long): String {
    return when {
        sizeBytes >= 1024 * 1024 -> "${String.format(java.util.Locale.getDefault(), "%.2f", sizeBytes / (1024.0 * 1024.0))} МБ"
        sizeBytes >= 1024 -> "${String.format(java.util.Locale.getDefault(), "%.2f", sizeBytes / 1024.0)} КБ"
        else -> "$sizeBytes Б"
    }
}

/**
 * Запускает службу архивации
 */
fun startArchiveService(context: Context, archiveServiceState: MutableState<Boolean>) {
    val intent = Intent(context, ArchiveService::class.java).apply {
        action = ArchiveService.ACTION_START
    }
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
    
    archiveServiceState.value = true
}

/**
 * Останавливает службу архивации
 */
fun stopArchiveService(context: Context, archiveServiceState: MutableState<Boolean>) {
    val intent = Intent(context, ArchiveService::class.java).apply {
        action = ArchiveService.ACTION_STOP
    }
    
    context.stopService(intent)
    
    archiveServiceState.value = false
}

/**
 * Запускает архивацию немедленно
 */
fun archiveNow(context: android.content.Context) {
    val intent = Intent(context, ArchiveService::class.java).apply {
        action = ArchiveService.ACTION_ARCHIVE_NOW
    }
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview(showBackground = true, name = "AdministratorScreen Preview")
fun AdministratorScreenPreview() {
    AdministratorScreen(
        onNavigateToEmployeeList = {},
        onNavigateToRoutes = {},
        onNavigateToLogs = {},
        onNavigateToCloudSettings = {},
        onBack = {},
        onNavigateToSoundSettings = {}
    )
}

