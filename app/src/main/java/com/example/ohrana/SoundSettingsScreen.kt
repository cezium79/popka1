package com.example.ohrana

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.media.RingtoneManager
import android.media.MediaPlayer
import android.provider.MediaStore
import androidx.compose.material3.SwitchDefaults.colors
import androidx.compose.ui.tooling.preview.Preview
import androidx.activity.compose.BackHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefsManager = remember { SharedPrefsManager(context) }

    // UI состояние (mutableStateOf позволяет изменять значения)
    var useSounds by remember { mutableStateOf(prefsManager.isSoundEnabled()) }
    
    // UI состояние для каждого типа звука
    var soundEvent1Enabled by remember { mutableStateOf(prefsManager.isSoundEnabledForEvent(1)) }
    var soundEvent2Enabled by remember { mutableStateOf(prefsManager.isSoundEnabledForEvent(2)) }
    var soundEvent3Enabled by remember { mutableStateOf(prefsManager.isSoundEnabledForEvent(3)) }
    
    // Состояние демонстрации звука
    var isPlaying by remember { mutableStateOf(false) }
    var currentMediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    
    // Состояние диалога выбора звука
    var showSoundDialog by remember { mutableStateOf(false) }
    var selectedEventForDialog by remember { mutableStateOf<Int?>(null) }
    
    // Освобождаем ресурсы при уничтожении
    DisposableEffect(Unit) {
        onDispose {
            currentMediaPlayer?.release()
            currentMediaPlayer = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки звука") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF616161)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Настройка звукового сопровождения событий",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            // Карточка с переключателем общего звука
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
                            text = "Использовать звуки",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Включить/выключить звук для всех событий",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = useSounds,
                        onCheckedChange = { isChecked ->
                            useSounds = isChecked
                            prefsManager.setSoundEnabled(isChecked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Color(0xFF40D511),   // Цвет полоски когда включено
                            uncheckedTrackColor = Color(0xFFA80707), // Цвет полоски когда выключено
                            checkedThumbColor = Color(0xFFFFFFFF),    // Цвет круга когда включено
                            uncheckedThumbColor = Color(0xFFFFFFFF)   // Цвет круга когда выключено
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Карточка для звука успеха
            SoundCardWithDropdown(
                title = "Звук успеха",
                description = "Срабатывает при успешном сканировании",
                eventId = 1,
                isSoundEnabled = soundEvent1Enabled,
                onSoundEnabledChange = { isChecked ->
                    soundEvent1Enabled = isChecked
                    prefsManager.setSoundEnabledForEvent(1, isChecked)
                },
                isPlaying = isPlaying,
                onPlay = { 
                    isPlaying = true
                    playSound(context, prefsManager, 1) { currentMediaPlayer = it }
                },
                onStop = { 
                    currentMediaPlayer?.stop()
                    currentMediaPlayer?.release()
                    currentMediaPlayer = null
                    isPlaying = false
                },
                onDropdownClick = { 
                    selectedEventForDialog = 1
                    showSoundDialog = true 
                },
                prefsManager = prefsManager
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Карточка для звука ошибки
            SoundCardWithDropdown(
                title = "Звук ошибки",
                description = "Срабатывает при ошибках сканирования",
                eventId = 2,
                isSoundEnabled = soundEvent2Enabled,
                onSoundEnabledChange = { isChecked ->
                    soundEvent2Enabled = isChecked
                    prefsManager.setSoundEnabledForEvent(2, isChecked)
                },
                isPlaying = isPlaying,
                onPlay = { 
                    isPlaying = true
                    playSound(context, prefsManager, 2) { currentMediaPlayer = it }
                },
                onStop = { 
                    currentMediaPlayer?.stop()
                    currentMediaPlayer?.release()
                    currentMediaPlayer = null
                    isPlaying = false
                },
                onDropdownClick = { 
                    selectedEventForDialog = 2
                    showSoundDialog = true 
                },
                prefsManager = prefsManager
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Карточка для звука предупреждения
            SoundCardWithDropdown(
                title = "Звук предупреждения",
                description = "Срабатывает при нарушениях последовательности",
                eventId = 3,
                isSoundEnabled = soundEvent3Enabled,
                onSoundEnabledChange = { isChecked ->
                    soundEvent3Enabled = isChecked
                    prefsManager.setSoundEnabledForEvent(3, isChecked)
                },
                isPlaying = isPlaying,
                onPlay = { 
                    isPlaying = true
                    playSound(context, prefsManager, 3) { currentMediaPlayer = it }
                },
                onStop = { 
                    currentMediaPlayer?.stop()
                    currentMediaPlayer?.release()
                    currentMediaPlayer = null
                    isPlaying = false
                },
                onDropdownClick = { 
                    selectedEventForDialog = 3
                    showSoundDialog = true 
                },
                prefsManager = prefsManager
            )

            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Типы звуков:\n- Успех: звук уведомления\n- Ошибка: звук будильника\n- Предупреждение: звук звонка",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }

    // Обработка системной кнопки "Назад"
    BackHandler(onBack = onBack)

    // Диалог выбора звука
    if (showSoundDialog && selectedEventForDialog != null) {
        SoundSelectionDialog(
            eventId = selectedEventForDialog!!,
            onDismiss = { showSoundDialog = false },
            onSoundSelected = { uri ->
                prefsManager.setEventSoundUri(selectedEventForDialog!!, uri)
                showSoundDialog = false
            },
            prefsManager = prefsManager
        )
    }
}

// Воспроизведение звука
fun playSound(
    context: android.content.Context,
    prefsManager: SharedPrefsManager,
    eventId: Int,
    onPlayComplete: (MediaPlayer?) -> Unit
) {
    val soundUri = prefsManager.getEventSoundUri(eventId)
    
    try {
        val mediaPlayer = MediaPlayer.create(context, soundUri)
        mediaPlayer?.setOnCompletionListener {
            it.release()
            onPlayComplete(null)
        }
        mediaPlayer?.setOnErrorListener { mp, what, extra ->
            android.util.Log.e("SoundPlayer", "Error playing sound: $what, $extra")
            mp.release()
            onPlayComplete(null)
            false
        }
        mediaPlayer?.start()
        onPlayComplete(mediaPlayer)
    } catch (e: Exception) {
        android.util.Log.e("SoundPlayer", "Error playing sound: ${e.message}")
        onPlayComplete(null)
    }
}

// Карточка с переключателем, кнопками play/stop и выпадающим меню
@Composable
fun SoundCardWithDropdown(
    title: String,
    description: String,
    eventId: Int,
    isSoundEnabled: Boolean,
    onSoundEnabledChange: (Boolean) -> Unit,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onDropdownClick: () -> Unit,
    prefsManager: SharedPrefsManager
) {
    val context = LocalContext.current
    
    // Используем LaunchedEffect для отслеживания изменений URI
    var currentSoundUri by remember { mutableStateOf(prefsManager.getEventSoundUri(eventId)) }
    var soundName by remember { mutableStateOf("...") }
    
    LaunchedEffect(eventId) {
        currentSoundUri = prefsManager.getEventSoundUri(eventId)
    }
    
    LaunchedEffect(currentSoundUri) {
        // Получаем имя звука на основе URI
        soundName = try {
            // Пытаемся получить название звука через RingtoneManager
            val ringtone = android.media.RingtoneManager.getRingtone(context, currentSoundUri)
            ringtone?.getTitle(context)?.toString() ?: "Неизвестный звук"
        } catch (e: Exception) {
            // Если не удалось получить название, используем дефолтные имена
            when (currentSoundUri?.toString()) {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).toString() -> "Уведомление"
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM).toString() -> "Будильник"
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE).toString() -> "Звонок"
                else -> "Уведомление"
            }
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Заголовок и описание
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Имя звука
            Text(
                text = "Текущий звук: $soundName",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Кнопки и переключатель
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = isSoundEnabled,
                    onCheckedChange = onSoundEnabledChange,
                    colors = colors(
                        checkedTrackColor = Color(0xFF40D511),   // Цвет полоски когда включено
                        uncheckedTrackColor = Color(0xFFA80707), // Цвет полоски когда выключено
                        checkedThumbColor = Color(0xFFFFFFFF),    // Цвет круга когда включено
                        uncheckedThumbColor = Color(0xFFFFFFFF)   // Цвет круга когда выключено
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = if (isPlaying) onStop else onPlay,
                        enabled = isSoundEnabled
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Остановить" else "Прослушать звук",
                            tint = if (isSoundEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    
                    IconButton(
                        onClick = onDropdownClick,
                        enabled = isSoundEnabled
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Выбрать звук",
                            tint = if (isSoundEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

// Диалог выбора звука
@Composable
fun SoundSelectionDialog(
    eventId: Int,
    onDismiss: () -> Unit,
    onSoundSelected: (android.net.Uri) -> Unit,
    prefsManager: SharedPrefsManager
) {
    val context = LocalContext.current
    
    // Получаем текущий URI звука для предвыбора
    val currentUri by remember(eventId) {
        derivedStateOf { prefsManager.getEventSoundUri(eventId) }
    }
    
    // Используем системное окно выбора звука через Intent
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val uri = result.data?.getParcelableExtra<android.net.Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                uri?.let {
                    onSoundSelected(it)
                    onDismiss()
                }
            } else {
                onDismiss()
            }
        }
    )
    
    // Автоматически запускаем системное окно выбора звука при изменении eventId
    androidx.compose.runtime.LaunchedEffect(eventId) {
        try {
            val intent = android.content.Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
            launcher.launch(intent)
        } catch (e: Exception) {
            android.util.Log.e("SoundSelection", "Error launching ringtone picker: ${e.message}")
            onDismiss()
        }
    }
}

// Кэшированный список доступных звуков
@Composable
fun loadAvailableSounds(context: android.content.Context): List<Pair<String, android.net.Uri>> {
    return remember {
        val ringtoneManager = RingtoneManager(context)
        ringtoneManager.setType(RingtoneManager.TYPE_ALL)
        val cursor = ringtoneManager.cursor
        
        val availableSounds = mutableListOf<Pair<String, android.net.Uri>>()
        
        // Добавляем стандартные звуки
        availableSounds.add(Pair("Уведомление (стандарт)", RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)))
        availableSounds.add(Pair("Будильник (стандарт)", RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)))
        availableSounds.add(Pair("Звонок (стандарт)", RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)))
        
        val defaultNotification = RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION).toString()
        val defaultAlarm = RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM).toString()
        val defaultRingtone = RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE).toString()
        
        // Добавляем системные звуки
        for (i in 0 until cursor.count) {
            try {
                val ringtone = ringtoneManager.getRingtone(i)
                val uri = ringtoneManager.getRingtoneUri(i)
                val uriStringFull = uri.toString()
                val title = ringtone.getTitle(context)?.toString() ?: "Звук"
                
                // Пропускаем стандартные звуки
                if (uriStringFull != defaultNotification && uriStringFull != defaultAlarm && uriStringFull != defaultRingtone) {
                    availableSounds.add(Pair(title, uri))
                }
            } catch (e: Exception) {
                // Игнорируем ошибки
            }
        }
        
        availableSounds
    }
}

// ============================================
// PREVIEW - Для отображения в Android Studio
// ============================================

@Preview(showBackground = true, backgroundColor = 0xFFFFFF)
@Composable
fun SoundSettingsScreenPreview1() {
    SoundSettingsScreen(onBack = {})
}

@Preview(showBackground = true, backgroundColor = 0xFFE0E0E0, name = "Dark Theme")
@Composable
fun SoundSettingsScreenPreview2() {
    SoundSettingsScreen(onBack = {})
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFF, widthDp = 375, heightDp = 812, name = "Mobile")
@Composable
fun SoundSettingsScreenPreview3() {
    SoundSettingsScreen(onBack = {})
}