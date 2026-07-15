package com.example.ohrana

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ohrana.Checkpoint
import com.example.ohrana.CheckpointAction
import com.example.ohrana.ShiftDatabaseManager
import com.example.ohrana.QrHandler
import com.example.ohrana.QrResult
import com.example.ohrana.SharedPrefsManager
import com.example.ohrana.SoundPlayer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.activity.compose.BackHandler

private const val TAG = "OhrannikCabinetScreen"


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OhrannikCabinetScreen(
    employeeName: String,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToPhoto: (SharedPrefsManager, String) -> Unit,
    onEndRound: () -> Unit,
    onCloseShift: () -> Unit
) {
    val context = LocalContext.current
    val manager = remember(context) { SharedPrefsManager(context) }

    val lifecycleOwner = LocalLifecycleOwner.current

    // Отслеживаем, есть ли активная смена (для определения, куда возвращаться)
    val isAnyShiftActive by remember { derivedStateOf { manager.isAnyShiftActive() } }
    // Отслеживаем, открыта ли смена кем-то другим
    val isShiftActiveByOther by remember { derivedStateOf { manager.isShiftActiveByOther(employeeName) } }
    // Проверяем, активна ли смена У ЭТОГО сотрудника
    val isShiftActiveForThisEmployee by remember { derivedStateOf { manager.isShiftActiveFor(employeeName) } }

    // Используем mutableStateOf для отслеживания изменений
    // Получаем имя следующего чекпоинта из SharedPrefsManager
    var nextCheckpointName by remember { mutableStateOf(manager.getNextCheckpointName()) }
    
    // Триггер для принудительного обновления имени чекпоинта после сканирования
    var checkpointScanTrigger by remember { mutableStateOf(0L) }
    
    // Отслеживаем изменения индексов через derivedStateOf
    val activeRoundIndex by remember { derivedStateOf { manager.getActiveRoundIndex() } }
    val currentCheckpointIndex by remember { derivedStateOf { manager.getRoundCheckpointIndex(activeRoundIndex) } }
    
    // Для анимации автоматического завершения обхода
    var showAutoEndAnimation by remember { mutableStateOf(false) }
    var autoEndMessage by remember { mutableStateOf("") }
    
    // Функция для завершения обхода (тот же алгоритм, что и в автоматическом и ручном режимах)
    fun completeRoundManually() {
        Log.d(TAG, "completeRoundManually: Manual round completion triggered")
        val activeRoundIndex = manager.getActiveRoundIndex()
        if (activeRoundIndex != -1) {
            Log.d(TAG, "completeRoundManually: Completing round $activeRoundIndex")
            val currentTime = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            manager.completeRound(activeRoundIndex, currentTime)
            QrHandler.endRoundIfActive()
            
            // Показываем анимацию завершения
            showAutoEndAnimation = true
            autoEndMessage = "Обход успешно завершен!"
            Log.d(TAG, "completeRoundManually: Set showAutoEndAnimation=true, autoEndMessage=$autoEndMessage")
        }
    }
    
    // При изменении индексов или после сканирования пересчитываем имя следующего чекпоинта и проверяем авто-завершение
    LaunchedEffect(activeRoundIndex, currentCheckpointIndex, checkpointScanTrigger) {
        nextCheckpointName = manager.getNextCheckpointName()
        
        // Проверяем, нужно ли автоматически завершить обход
        val expectedCheckpointName = manager.getNextCheckpointName()
        Log.d(TAG, "LaunchedEffect: checkpointIndex=${currentCheckpointIndex}, expectedCheckpointName=$expectedCheckpointName, autoEndEnabled=${manager.isAutoEndRoundEnabled()}")
        Log.d(TAG, "LaunchedEffect: nextCheckpointName=$nextCheckpointName")
        if (expectedCheckpointName == "Обход завершен") {
            Log.d(TAG, "LaunchedEffect: Auto end round triggered!")
            val autoEndEnabled = manager.isAutoEndRoundEnabled()
            Log.d(TAG, "LaunchedEffect: isAutoEndRoundEnabled=$autoEndEnabled")
            if (autoEndEnabled) {
                Log.d(TAG, "LaunchedEffect: Auto end round is enabled")
                // Выполняем тот же алгоритм, что и в ручном режиме
                completeRoundManually()
            } else {
                Log.d(TAG, "LaunchedEffect: Auto end round is disabled, waiting for manual completion via TopAppBar button")
                // При выключенном свитче просто ждем нажатия кнопки "Завершить обход" в TopAppBar
                // nextCheckpointName уже обновлен через LaunchedEffect при смене checkpointScanTrigger
            }
        }
    }
    
    // Отдельный LaunchedEffect для обработки анимации завершения
    LaunchedEffect(showAutoEndAnimation) {
        Log.d(TAG, "Animation LaunchedEffect triggered, showAutoEndAnimation=$showAutoEndAnimation")
        if (showAutoEndAnimation) {
            // Ждем 3 секунды, затем скрываем анимацию и возвращаемся
            kotlinx.coroutines.delay(3000)
            if (showAutoEndAnimation) {
                showAutoEndAnimation = false
                Log.d(TAG, "Animation completed, hiding dialog")
                
                // Вызываем коллбэк для возврата в RoundsScreen
                if (manager.isShiftActive()) {
                    Log.d(TAG, "Animation LaunchedEffect: Calling onEndRound")
                    onEndRound()
                } else {
                    Log.d(TAG, "Animation LaunchedEffect: Calling onCloseShift")
                    onCloseShift()
                }
            }
        }
    }
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    var hasStoragePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Главный триггер захвата кадра при нажатии кнопки "Сканировать"
    var isScanRequested by remember { mutableStateOf(false) }

    // --- СОСТОЯНИЯ ДЛЯ ПОКАЗА ДИАЛОГОВЫХ ОКОН ---
    var showCheckpointPassedDialog by remember { mutableStateOf<QrResult.CheckpointPassed?>(null) }

    var showQuestionDialog by remember { mutableStateOf<QrResult.QuestionFormat?>(null) }
    var showInputDialog by remember { mutableStateOf<QrResult.InputFormat?>(null) }
    var showErrorDialog by remember { mutableStateOf<String?>(null) }
    
    // Для отображения ошибки последовательности в строгом режиме
    var showSequenceErrorScreen by remember { mutableStateOf<QrResult.SequenceError?>(null) }
    
    // Для съемки фото
    var photoCheckpointId by remember { mutableStateOf("") }
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )
    
    val storageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasStoragePermission = granted }
    )
    
    // Запуск экрана фото при изменении photoCheckpointId
    LaunchedEffect(photoCheckpointId) {
        if (photoCheckpointId.isNotEmpty()) {
            // Переход на экран фото с передачей manager и ID чекпоинта
            onNavigateToPhoto(manager, photoCheckpointId)
            // Очищаем после использования
            photoCheckpointId = ""
        }
    }
    
    // NFC сканер включён постоянно для автоматического считывания NFC-тегов
    val activity = context as? ComponentActivity
    var nfcAdapter by remember { mutableStateOf<NfcAdapter?>(null) }
    var nfcScanResult by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        try {
            nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        } catch (e: Exception) {
            // NFC не поддерживается
        }
    }
    
    // Всегда включаем NFC reader mode для фонового сканирования
    LaunchedEffect(activity) {
        if (nfcAdapter != null && activity != null) {
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
        }
    }
    
    // Обработка NFC сканирования - ищем чекпоинт по NFC-ID
    LaunchedEffect(nfcScanResult) {
        if (nfcScanResult != null) {
            val scannedNfcId = nfcScanResult!!
            val checkpoint = manager.getCheckpointByNfcId(scannedNfcId)
            
            if (checkpoint != null) {
                // Если включен строгий контроль последовательности - проверяем порядок
                if (manager.isStrictSequenceEnabled()) {
                    val activeRoundIndex = manager.getActiveRoundIndex()
                    val currentCheckpointIndex = manager.getCurrentCheckpointIndex()
                    
                    // Проверяем, является ли этот чекпоинт следующим в последовательности
                    val expectedCheckpointName = manager.getNextCheckpointName()
                    val isSequenceCorrect = (checkpoint.name == expectedCheckpointName)
                    
                    if (!isSequenceCorrect) {
                        // Нарушение последовательности - определяем тип
                        val activeShiftId = manager.prefs.getString("active_shift_id", null)
                        val activeEmployeeName = manager.getActiveShiftEmployeeName()
                        
                        // Загружаем маршруты для определения типа нарушения
                        val routeId = manager.getActiveRoundRouteId()
                        val route = routeId?.let { manager.getRouteById(it) }
                        val routeCheckpoints = route?.checkpointIds ?: manager.getAllCheckpointIds()
                        val isCheckpointInRoute = routeCheckpoints.contains(checkpoint.id)
                        
                        // Определяем тип ошибки
                        val sequenceErrorType = when {
                            !isCheckpointInRoute -> SequenceErrorType.OUTSIDE_ROUTE
                            else -> SequenceErrorType.OUT_OF_SEQUENCE
                        }
                        
                        // Сохраняем факт сканирования для аудита с правильным типом ошибки
                        activeShiftId?.let { shiftId ->
                            if (activeRoundIndex != -1) {
                                manager.shiftDatabase.addLogEntry(
                                    checkpointName = checkpoint.name,
                                    checkpointId = checkpoint.id,
                                    employeeName = activeEmployeeName,
                                    roundId = activeRoundIndex,
                                    routeName = "Маршрут обхода",
                                    sequenceIndex = currentCheckpointIndex,
                                    isSequenceCorrect = false,
                                    scanType = "NFC",
                                    actionType = "SCAN",
                                    sequenceErrorType = sequenceErrorType
                                )
                            }
                        }
                        
                        // Записываем нарушение последовательности в базу данных
                        activeShiftId?.let { shiftId ->
                            if (activeRoundIndex != -1) {
                                manager.shiftDatabase.addSequenceViolation(
                                    employeeName = activeEmployeeName,
                                    roundId = activeRoundIndex,
                                    shiftId = shiftId,
                                    expectedCheckpointId = if (sequenceErrorType == SequenceErrorType.OUTSIDE_ROUTE) "" else expectedCheckpointName ?: "",
                                    expectedCheckpointName = expectedCheckpointName ?: "",
                                    actualCheckpointId = checkpoint.id,
                                    actualCheckpointName = checkpoint.name,
                                    sequenceErrorType = sequenceErrorType,
                                    isNfc = true
                                )
                            }
                        }
                        
                        // Показываем диалог с красным X вместо обычного диалога
                        showSequenceErrorScreen = QrResult.SequenceError(
                            expectedCheckpointId = expectedCheckpointName,
                            message = if (!isCheckpointInRoute) {
                                "Чекпоинт '${checkpoint.name}' не найден в текущем маршруте обхода."
                            } else {
                                "ТОчка вне очереди"
                            }
                        )
                        
                        // Воспроизводим звук ошибки
                        SoundPlayer.playError(context)
                        
                        nfcScanResult = null
                        return@LaunchedEffect
                    }
                }
                
                // NFC-ID найден в базе - обрабатываем в зависимости от типа
                when (checkpoint.action) {
                    CheckpointAction.CHECKPOINT -> {
                        // Воспроизводим звук успеха
                        SoundPlayer.playSuccess(context)
                        
                        // Сохраняем факт сканирования (для аудита)
                        val activeRoundIndex = manager.getActiveRoundIndex()
                        if (activeRoundIndex != -1) {
                            manager.shiftDatabase.addLogEntry(
                                checkpointName = checkpoint.name,
                                checkpointId = checkpoint.id,
                                employeeName = employeeName,
                                roundId = activeRoundIndex,
                                routeName = "Маршрут обхода",
                                sequenceIndex = manager.getCurrentCheckpointIndex(),
                                isSequenceCorrect = true,
                                scanType = "NFC",
                                actionType = "SCAN"
                            )
                        }
                        
                        // Сохраняем в логи SharedPreferences
                        val logText = "NFC-чекпоинт: ${checkpoint.name} -> ID: ${checkpoint.id}"
                        manager.saveScanResult(employeeName = employeeName, qrContent = logText)
                        
                        showCheckpointPassedDialog = QrResult.CheckpointPassed(
                            checkpointId = checkpoint.id,
                            name = checkpoint.name,
                            timestamp = java.text.SimpleDateFormat("HH:mm:ss dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date())
                        )
                    }
                    CheckpointAction.QUESTION -> {
                        // Сохраняем факт сканирования (для аудита)
                        val activeRoundIndex = manager.getActiveRoundIndex()
                        if (activeRoundIndex != -1) {
                            manager.shiftDatabase.addLogEntry(
                                checkpointName = checkpoint.name,
                                checkpointId = checkpoint.id,
                                employeeName = employeeName,
                                roundId = activeRoundIndex,
                                routeName = "Маршрут обхода",
                                sequenceIndex = manager.getCurrentCheckpointIndex(),
                                isSequenceCorrect = true,
                                scanType = "NFC",
                                actionType = "SCAN"
                                // questionText не сохраняем - он будет взят из чекпоинта по checkpointId
                            )
                        }
                        
                        // Диалог с вопросом сохранит результат при выборе ответа
                        showQuestionDialog = QrResult.QuestionFormat(
                            checkpointId = checkpoint.id,
                            checkpointName = checkpoint.name,
                            text = checkpoint.questionText ?: "",
                            answers = checkpoint.answers
                        )
                    }
                    CheckpointAction.INPUT -> {
                        // Сохраняем факт сканирования (для аудита)
                        val activeRoundIndex = manager.getActiveRoundIndex()
                        if (activeRoundIndex != -1) {
                            manager.shiftDatabase.addLogEntry(
                                checkpointName = checkpoint.name,
                                checkpointId = checkpoint.id,
                                employeeName = employeeName,
                                roundId = activeRoundIndex,
                                routeName = "Маршрут обхода",
                                sequenceIndex = manager.getCurrentCheckpointIndex(),
                                isSequenceCorrect = true,
                                scanType = "NFC",
                                actionType = "SCAN"
                                // inputTitle не сохраняем - он будет взят из чекпоинта по checkpointId
                            )
                        }
                        
                        // Диалог с вводом сохранит результат при нажатии "Сохранить"
                        showInputDialog = QrResult.InputFormat(
                            checkpointId = checkpoint.id,
                            checkpointName = checkpoint.name,
                            title = checkpoint.inputTitle ?: ""
                        )
                    }
                    CheckpointAction.PHOTO -> {
                        // Сохраняем факт сканирования (для аудита)
                        val activeRoundIndex = manager.getActiveRoundIndex()
                        if (activeRoundIndex != -1) {
                            manager.shiftDatabase.addLogEntry(
                                checkpointName = checkpoint.name,
                                checkpointId = checkpoint.id,
                                employeeName = employeeName,
                                roundId = activeRoundIndex,
                                routeName = "Маршрут обхода",
                                sequenceIndex = manager.getCurrentCheckpointIndex(),
                                isSequenceCorrect = true,
                                scanType = "NFC",
                                actionType = "SCAN"
                            )
                        }
                        
                        // PhotoFormat обрабатывается через экран PhotoCaptureScreen
                        photoCheckpointId = checkpoint.id
                    }
                }
            } else {
                // NFC-ID не найден в базе
                showErrorDialog = "NFC-тег не найден в базе чекпоинтов"
                
                // Воспроизводим звук ошибки
                SoundPlayer.playError(context)
            }
            
            nfcScanResult = null
        }
    }
    


    LaunchedEffect(key1 = true) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
        if (!hasStoragePermission) {
            storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(employeeName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    TextButton(onClick = { 
                        // Выполняем тот же алгоритм, что и в автоматическом режиме
                        completeRoundManually()
                    }) {
                        Text("Завершить обход", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Следующая точка: $nextCheckpointName",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (hasCameraPermission) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 16.dp)
                ) {
                    // Контейнер для вывода картинки с камеры смартфона
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()

                                val options = com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
                                    .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
                                    .build()
                                val scanner = com.google.mlkit.vision.barcode.BarcodeScanning.getClient(options)

                                imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                    if (isScanRequested) {
                                        @Suppress("UnsafeOptInUsageError")
                                        val mediaImage = imageProxy.image
                                        if (mediaImage != null) {
                                            val rotation = imageProxy.imageInfo.rotationDegrees
                                            val image = com.google.mlkit.vision.common.InputImage.fromMediaImage(mediaImage, rotation)

                                            // Получаем физические размеры кадра матрицы камеры
                                            val imgWidth = imageProxy.width
                                            val imgHeight = imageProxy.height

                                            scanner.process(image)
                                                .addOnSuccessListener { barcodes ->
                                                    for (barcode in barcodes) {
                                                        val rawValue = barcode.rawValue
                                                        val bounds = barcode.boundingBox

                                                        if (rawValue != null && bounds != null) {

                                                            // 1. Вычисляем координаты центра QR-кода на матрице
                                                            val qrCenterX = bounds.centerX().toFloat()
                                                            val qrCenterY = bounds.centerY().toFloat()

                                                            // 2. Определяем границы рамки прицела (центральные 30% от кадра)
                                                            // Если камера повернута вертикально (90 или 270 град), меняем оси местами для корректности
                                                            val isRotated = rotation == 90 || rotation == 270
                                                            val frameWidth = if (isRotated) imgHeight else imgWidth
                                                            val frameHeight = if (isRotated) imgWidth else imgHeight

                                                            // Рассчитываем допустимый квадрат по центру кадра
                                                            val minX = frameWidth * 0.35f
                                                            val maxX = frameWidth * 0.65f
                                                            val minY = frameHeight * 0.35f
                                                            val maxY = frameHeight * 0.65f

                                                            // 3. Проверка: попадает ли центр QR-кода в рассчитанную центральную область
                                                            if (qrCenterX in minX..maxX && qrCenterY in minY..maxY) {

                                                                // ТОЛЬКО ЕСЛИ КОД ВНУТРИ ПРИЦЕЛА — выполняем ваш оригинальный код:
                                                                isScanRequested = false

                                                                // Отправляем строку на разбор парсеру QrHandler
                                                                val qrResult = QrHandler.parseQrCode(barcode.rawValue ?: "", manager)


                                                                // --- НОВЫЙ БЛОК ОБРАБОТКИ РЕЗУЛЬТАТА ---
                                                                when (qrResult) {
                                                                    is QrResult.CheckpointPassed -> {
                                                                        // Воспроизводим звук успеха при успешном проходе чекпоинта
                                                                        SoundPlayer.playSuccess(context)
                                                                        
                                                                        // Ваша логика при успешном проходе точки
                                                                        showCheckpointPassedDialog = qrResult
                                                                    }
                                                                    is QrResult.SequenceError -> {
                                                                        // Логика при ошибке последовательности
                                                                        // Проверяем, включен ли строгий контроль
                                                                        if (manager.isStrictSequenceEnabled()) {
                                                                            // Показываем экран с красным X и сообщением "ТОчка вне очереди"
                                                                            showSequenceErrorScreen = qrResult
                                                                        } else {
                                                                            // В нестрогом режиме показываем просто Toast
                                                                            android.widget.Toast.makeText(context, qrResult.message, android.widget.Toast.LENGTH_LONG).show()
                                                                        }
                                                                    }
                                                                    is QrResult.QuestionFormat -> {
                                                                        // Показать диалог с вопросом
                                                                        showQuestionDialog = qrResult
                                                                    }
                                                                    is QrResult.InputFormat -> {
                                                                        // Показать диалог для ввода данных
                                                                        showInputDialog = qrResult
                                                                    }
                                                                    is QrResult.PhotoFormat -> {
                                                                        // Запоминаем ID чекпоинта и переходим на экран фото
                                                                        photoCheckpointId = qrResult.checkpointId
                                                                    }
                                                                    is QrResult.Error -> {
                                                                        showErrorDialog = qrResult.message
                                                                    }
                                                                }
                                                                // -------------------------------------
                                                                break // Прерываем цикл, так как нужный код найден и обработан
                                                            }
                                                        }
                                                    }
                                                }
                                                .addOnCompleteListener {
                                                    // Обязательно закрываем imageProxy, чтобы камера не «застывала»
                                                    imageProxy.close()
                                                }
                                        } else {
                                            imageProxy.close()
                                        }
                                    } else {
                                        imageProxy.close()
                                    }
                                }


                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                try {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
                                } catch (e: Exception) { e.printStackTrace() }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Рамка зеленого прицела по центру экрана
                    Box(
                        modifier = Modifier
                            .size(280.dp, 280.dp)
                            .align(Alignment.Center)
                            .border(BorderStroke(3.dp, Color.Green), RoundedCornerShape(16.dp))
                    )

                    // Кнопка под рамкой: QR
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = { 
                                isScanRequested = true
                            },
                            modifier = Modifier.width(200.dp).height(56.dp),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Text(text = "Сканировать QR", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
    
    // ================= РЕНДЕРИНГ ДИАЛОГОВЫХ ОКОН =================
    
    // Анимация автоматического завершения обхода (показывается при ручном и авто завершении)
    Log.d(TAG, "Checking showAutoEndAnimation=$showAutoEndAnimation for rendering")
    if (showAutoEndAnimation) {
        Log.d(TAG, "Rendering auto-end animation dialog, message=$autoEndMessage")
        AlertDialog(
            onDismissRequest = { }, // Отключаем закрытие при клике вне
            title = { Text("Обход завершен!") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Зеленая иконка успеха
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(50.dp))
                            .background(Color(0xFF4CAF50))
                            .align(Alignment.CenterHorizontally),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Успех",
                            modifier = Modifier.size(60.dp),
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = autoEndMessage,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Теперь вы свободны до следующего обхода",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {}  // Пустой confirmButton для компиляции
        )
    }

    // 1. Диалог для обычной метки локации (простой чекпоинт)
    // --- РЕНДЕРИНГ ДИАЛОГА ОБ УСПЕШНОМ ПРОХОЖДЕНИИ ---
    showCheckpointPassedDialog?.let { result ->
        // Запускаем таймер для автоматического закрытия
        var autoCloseTrigger by remember { mutableStateOf(false) }
        
        LaunchedEffect(showCheckpointPassedDialog) {
            autoCloseTrigger = false
            kotlinx.coroutines.delay(100) // Небольшая задержка перед началом отсчета
            kotlinx.coroutines.delay(3000) // Ждем 3 секунды
            
            // Обновляем индекс при закрытии диалога
            val activeRoundIndex = manager.getActiveRoundIndex()
            if (activeRoundIndex != -1) {
                // Получаем последнюю запись SCAN
                val lastScanEntry = manager.shiftDatabase.loadLogsByRound(activeRoundIndex).asReversed().find { it.actionType == "SCAN" }
                manager.shiftDatabase.updateLastScanEntry(
                    roundId = activeRoundIndex,
                    actionType = "CHECKPOINT"
                    // questionText и inputTitle не передаются - они будут взяты из чекпоинта
                )
                val newCheckpointIndex = manager.getRoundCheckpointIndex(activeRoundIndex) + 1
                manager.updateCurrentCheckpointIndex(newCheckpointIndex)
            }
            // Обновляем имя следующего чекпоинта
            checkpointScanTrigger = System.currentTimeMillis()
            
            // Проверяем, является ли это последним чекпоинтом
            val expectedCheckpointName = manager.getNextCheckpointName()
            
            // Получаем количество чекпоинтов в маршруте
            var isLastCheckpoint = false
            if (activeRoundIndex != -1) {
                val routeId = manager.getRoundRouteId(activeRoundIndex)
                val route = routeId?.let { manager.getRouteById(it) }
                val checkpointIndex = manager.getRoundCheckpointIndex(activeRoundIndex)
                if (route != null && checkpointIndex >= route.checkpointIds.size) {
                    isLastCheckpoint = true
                }
            }
            
            Log.d(TAG, "CheckpointPassed: expectedCheckpointName=${expectedCheckpointName.orEmpty()}, isLastCheckpoint=$isLastCheckpoint, autoEndEnabled=${manager.isAutoEndRoundEnabled()}")
            
            // Проверяем, является ли это последним чекпоинтом
            val isLastCheckpointResult = (expectedCheckpointName == "Обход завершен" || expectedCheckpointName.isNullOrBlank() || isLastCheckpoint)
            
            if (isLastCheckpointResult) {
                Log.d(TAG, "CheckpointPassed: Last checkpoint reached")
                // Если включен авто-финал - завершаем обход
                if (manager.isAutoEndRoundEnabled()) {
                    Log.d(TAG, "CheckpointPassed: Auto end round is enabled")
                    completeRoundManually()
                } else {
                    Log.d(TAG, "CheckpointPassed: Auto end round is disabled, waiting for manual completion via TopAppBar button")
                    // При выключенном свитче просто ждем нажатия кнопки "Завершить обход" в TopAppBar
                    // nextCheckpointName обновится через LaunchedEffect при смене checkpointScanTrigger
                }
            } else {
                Log.d(TAG, "CheckpointPassed: More checkpoints available, continuing...")
            }
            
            showCheckpointPassedDialog = null
        }
        
        AlertDialog(
            onDismissRequest = { }, // Запрещаем закрытие кликом вне
            title = { Text("Точка зафиксирована") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Большая зеленая галочка
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(50.dp))
                            .background(Color(0xFF4CAF50))
                            .align(Alignment.CenterHorizontally),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Успех",
                            modifier = Modifier.size(60.dp),
                            tint = Color.White
                        )
                    }
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Локация: ${result.name}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "ID: ${result.checkpointId}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Время: ${result.timestamp}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {}  // Пустой confirmButton для компиляции
        )
    }

    // 2. Диалог с ВОПРОСОМ и кнопками вариантов ответов
    showQuestionDialog?.let { result ->
        AlertDialog(
            onDismissRequest = { showQuestionDialog = null },
            title = { Text("Проверка чек-листа") },
            text = {
                Column {
                    Text(
                        text = result.questionText,
                        modifier = Modifier.padding(bottom = 12.dp),
                        fontWeight = FontWeight.Medium
                    )
                    result.answers.forEach { answer ->
                        Button(
                            onClick = {
                                val logText = "Чек-лист: ${result.questionText} -> Ответ: $answer"
                                manager.saveScanResult(employeeName = employeeName, qrContent = logText)
                                
                                // Обновляем последнюю запись SCAN на тип QUESTION
                                val activeRoundIndex = manager.getActiveRoundIndex()
                                if (activeRoundIndex != -1) {
                                    // Получаем последнюю запись SCAN для получения questionText
                                    val lastScanEntry = manager.shiftDatabase.loadLogsByRound(activeRoundIndex).asReversed().find { it.actionType == "SCAN" }
                                    manager.shiftDatabase.updateLastScanEntry(
                                        roundId = activeRoundIndex,
                                        actionType = "QUESTION",
                                        answer = answer
                                        // questionText не передаётся - он будет взят из чекпоинта
                                    )
                                }
                                
                                showQuestionDialog = null
                                // Увеличиваем индекс при закрытии диалога (чекпоинт пройден)
                                val activeRoundIndexQ = manager.getActiveRoundIndex()
                                if (activeRoundIndexQ != -1) {
                                    val newCheckpointIndex = manager.getRoundCheckpointIndex(activeRoundIndexQ) + 1
                                    manager.updateCurrentCheckpointIndex(newCheckpointIndex)
                                }
                                // Обновляем имя следующего чекпоинта
                                checkpointScanTrigger = System.currentTimeMillis()
                                
                                // Проверяем, является ли это последним чекпоинтом
                                val expectedCheckpointName = manager.getNextCheckpointName()
                                
                                // Получаем количество чекпоинтов в маршруте
                                var isLastCheckpoint = false
                                if (activeRoundIndexQ != -1) {
                                    val routeId = manager.getRoundRouteId(activeRoundIndexQ)
                                    val route = routeId?.let { manager.getRouteById(it) }
                                    val checkpointIndex = manager.getRoundCheckpointIndex(activeRoundIndexQ)
                                    if (route != null && checkpointIndex >= route.checkpointIds.size) {
                                        isLastCheckpoint = true
                                    }
                                }
                                
                                Log.d(TAG, "QuestionFormat: expectedCheckpointName=${expectedCheckpointName.orEmpty()}, isLastCheckpoint=$isLastCheckpoint, autoEndEnabled=${manager.isAutoEndRoundEnabled()}")
                                
                                // Проверяем, является ли это последним чекпоинтом
                                val isLastCheckpointResult = (expectedCheckpointName == "Обход завершен" || expectedCheckpointName.isNullOrBlank() || isLastCheckpoint)
                                
                                if (isLastCheckpointResult) {
                                    Log.d(TAG, "QuestionFormat: Last checkpoint reached")
                                    // Если включен авто-финал - завершаем обход
                                    if (manager.isAutoEndRoundEnabled()) {
                                        Log.d(TAG, "QuestionFormat: Auto end round is enabled")
                                        completeRoundManually()
                                    } else {
                                        Log.d(TAG, "QuestionFormat: Auto end round is disabled, waiting for manual completion via TopAppBar button")
                                        // При выключенном свитче просто ждем нажатия кнопки "Завершить обход" в TopAppBar
                                        // nextCheckpointName обновится через LaunchedEffect при смене checkpointScanTrigger
                                    }
                                } else {
                                    Log.d(TAG, "QuestionFormat: More checkpoints available, continuing...")
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) { Text(answer) }
                    }
                }
            },
            confirmButton = {} // Кнопки подтверждения не нужны, клик по варианту сам закроет окно
        )
    }

    // 3. Диалог для ВВОДА ДАННЫХ (Показания счетчиков и т.д.)
    showInputDialog?.let { result ->
        var dialogInputText by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { showInputDialog = null },
            title = { Text("Ввод данных") },
            text = {
                Column {
                    Text(text = result.titleText, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(
                        value = dialogInputText,
                        onValueChange = { dialogInputText = it },
                        label = { Text("Введите показания") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (dialogInputText.isNotBlank()) {
                            val logText = "Показания: ${result.titleText} -> Введено: $dialogInputText"
                            manager.saveScanResult(employeeName = employeeName, qrContent = logText)
                            
                            // Обновляем последнюю запись SCAN на тип INPUT
                            val activeRoundIndex = manager.getActiveRoundIndex()
                            if (activeRoundIndex != -1) {
                                android.util.Log.d(TAG, "InputFormat: Updating SCAN entry with inputValue='$dialogInputText', isNull=${dialogInputText == null}, length=${dialogInputText?.length}")
                                val success = manager.shiftDatabase.updateLastScanEntry(
                                    roundId = activeRoundIndex,
                                    actionType = "INPUT",
                                    inputValue = dialogInputText
                                    // inputTitle не передаётся - он будет взят из чекпоинта
                                )
                                android.util.Log.d(TAG, "InputFormat: updateLastScanEntry success=$success")
                                
                                // Дополнительно: проверяем, что запись действительно обновлена
                                val updatedLogs = manager.shiftDatabase.loadLogsByRound(activeRoundIndex)
                                val inputEntry = updatedLogs.asReversed().find { it.actionType == "INPUT" }
                                android.util.Log.d(TAG, "InputFormat: Verified INPUT entry - actionType=${inputEntry?.actionType}, inputValue='${inputEntry?.inputValue}', isNull=${inputEntry?.inputValue == null}")
                            }

                            showInputDialog = null
                            // Увеличиваем индекс при закрытии диалога (чекпоинт пройден)
                            val newCheckpointIndex = manager.getCurrentCheckpointIndex() + 1
                            manager.updateCurrentCheckpointIndex(newCheckpointIndex)
                            // Обновляем имя следующего чекпоинта
                            checkpointScanTrigger = System.currentTimeMillis()
                            
                            // Проверяем, является ли это последним чекпоинтом
                            val expectedCheckpointName = manager.getNextCheckpointName()
                            Log.d(TAG, "InputFormat: getNextCheckpointName: ${expectedCheckpointName.orEmpty()}")
                            
                            // Проверяем, является ли это последним чекпоинтом
                            val isLastCheckpointResult = (expectedCheckpointName == "Обход завершен" || expectedCheckpointName.isNullOrBlank())
                            
                            if (isLastCheckpointResult) {
                                Log.d(TAG, "InputFormat: Last checkpoint reached")
                                // Если включен авто-финал - завершаем обход
                                if (manager.isAutoEndRoundEnabled()) {
                                    Log.d(TAG, "InputFormat: Auto end round is enabled")
                                    completeRoundManually()
                                } else {
                                    Log.d(TAG, "InputFormat: Auto end round is disabled, waiting for manual completion via TopAppBar button")
                                    // При выключенном свитче просто ждем нажатия кнопки "Завершить обход" в TopAppBar
                                    // nextCheckpointName обновится через LaunchedEffect при смене checkpointScanTrigger
                                }
                            } else {
                                Log.d(TAG, "InputFormat: More checkpoints available, continuing...")
                            }
                        }
                    }
                ) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showInputDialog = null
                }) { Text("Отмена") }
            }
        )
    }

    // 4. Диалог ошибки парсинга/сканирования
    showErrorDialog?.let { message ->
        AlertDialog(
            onDismissRequest = { showErrorDialog = null },
            title = { Text("Ошибка сканирования") },
            text = { Text(message) },
            confirmButton = { Button(onClick = { showErrorDialog = null }) { Text("ОК") } }
        )
    }
    
    // 5. Диалог ошибки последовательности в строгом режиме - большой красный X
    showSequenceErrorScreen?.let { result ->
        // Определяем тип ошибки на основе сообщения
        val errorMessage = result.message
        val isForeignCheckpoint = errorMessage.contains("Чужеродная метка", ignoreCase = true)
        val isOutsideRoute = errorMessage.contains("не найден в текущем маршруте", ignoreCase = true)
        val titleText = if (isForeignCheckpoint) {
            "Чужеродная метка"
        } else if (isOutsideRoute) {
            "Чекпоинт вне маршрута"
        } else {
            "Точка вне очереди"
        }
        
        AlertDialog(
            onDismissRequest = { showSequenceErrorScreen = null },
            title = null,
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Большой красный X
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .background(color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                            .clip(RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Ошибка",
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    
                    // Текст сообщения
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = titleText,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        
                        Text(
                            text = errorMessage,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        // Если есть ожидаемый чекпоинт - показываем его (только для нарушения очереди)
                        if (!isForeignCheckpoint && !isOutsideRoute && result.expectedCheckpointId != null) {
                            Text(
                                text = "Ожидался чекпоинт: ${result.expectedCheckpointId}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSequenceErrorScreen = null },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Понятно", fontSize = 16.sp)
                }
            }
        )
    }
    
    // Обработка системной кнопки "Назад"
    BackHandler(onBack = onBack)
}

// ============================================
// PREVIEW - Для отображения в Android Studio
// ============================================

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "Кабинет охранника")
@Composable
fun OhrannikCabinetScreenPreview() {
    androidx.compose.material3.MaterialTheme {
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color(0xFFFAFAFA))
        ) {
            // TopAppBar
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.primary)
                    .align(androidx.compose.ui.Alignment.TopCenter)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    modifier = androidx.compose.ui.Modifier
                        .align(androidx.compose.ui.Alignment.CenterStart)
                        .padding(start = 16.dp),
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                )
                
                androidx.compose.material3.Text(
                    text = "Иванов Иван",
                    modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.Center),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                )
                
                androidx.compose.material3.Button(
                    onClick = {},
                    modifier = androidx.compose.ui.Modifier
                        .align(androidx.compose.ui.Alignment.CenterEnd)
                        .padding(end = 16.dp)
                ) {
                    androidx.compose.material3.Text(
                        text = "Завершить",
                        fontSize = 14.sp
                    )
                }
            }
            
            // Content
            androidx.compose.foundation.layout.Column(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxSize()
                    .padding(top = 64.dp)
                    .padding(16.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                androidx.compose.material3.Text(
                    text = "Следующая точка: Проверка ворот",
                    fontSize = 18.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    modifier = androidx.compose.ui.Modifier.padding(bottom = 16.dp)
                )
                
                // Camera preview container
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(androidx.compose.ui.graphics.Color(0xFF212121))
                        .padding(bottom = 16.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    androidx.compose.material3.Text(
                        text = "[Камера]",
                        fontSize = 14.sp,
                        color = androidx.compose.ui.graphics.Color.LightGray
                    )
                }
                
                // Green frame
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier
                        .size(280.dp)
                        .border(
                            androidx.compose.foundation.BorderStroke(3.dp, androidx.compose.ui.graphics.Color.Green),
                            androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        )
                )
                
                // Scan button
                androidx.compose.material3.Button(
                    onClick = {},
                    modifier = androidx.compose.ui.Modifier
                        .width(200.dp)
                        .height(56.dp)
                        .padding(top = 16.dp)
                ) {
                    androidx.compose.material3.Text(
                        text = "Сканировать QR",
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, name = "Кабинет охранника (темная тема)")
@Composable
fun OhrannikCabinetScreenPreviewDark() {
    androidx.compose.material3.MaterialTheme {
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color(0xFF121212))
        ) {
            // TopAppBar
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.primary)
                    .align(androidx.compose.ui.Alignment.TopCenter)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    modifier = androidx.compose.ui.Modifier
                        .align(androidx.compose.ui.Alignment.CenterStart)
                        .padding(start = 16.dp),
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                )
                
                androidx.compose.material3.Text(
                    text = "Иванов Иван",
                    modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.Center),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
                )
                
                androidx.compose.material3.Button(
                    onClick = {},
                    modifier = androidx.compose.ui.Modifier
                        .align(androidx.compose.ui.Alignment.CenterEnd)
                        .padding(end = 16.dp)
                ) {
                    androidx.compose.material3.Text(
                        text = "Завершить",
                        fontSize = 14.sp
                    )
                }
            }
            
            // Content
            androidx.compose.foundation.layout.Column(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxSize()
                    .padding(top = 64.dp)
                    .padding(16.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                androidx.compose.material3.Text(
                    text = "Следующая точка: Проверка ворот",
                    fontSize = 18.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    modifier = androidx.compose.ui.Modifier.padding(bottom = 16.dp)
                )
                
                // Camera preview container
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(androidx.compose.ui.graphics.Color(0xFF424242))
                        .padding(bottom = 16.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    androidx.compose.material3.Text(
                        text = "[Камера]",
                        fontSize = 14.sp,
                        color = androidx.compose.ui.graphics.Color.LightGray
                    )
                }
                
                // Green frame
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier
                        .size(280.dp)
                        .border(
                            androidx.compose.foundation.BorderStroke(3.dp, androidx.compose.ui.graphics.Color.Green),
                            androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        )
                )
                
                // Scan button
                androidx.compose.material3.Button(
                    onClick = {},
                    modifier = androidx.compose.ui.Modifier
                        .width(200.dp)
                        .height(56.dp)
                        .padding(top = 16.dp)
                ) {
                    androidx.compose.material3.Text(
                        text = "Сканировать QR",
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

// Вспомогательная заглушка, чтобы не ломать вызовы в других частях проекта
@Composable
fun CameraScannerScreen(
    onScanRecognized: (String) -> Unit,
    modifier: Modifier = Modifier,
    onPreviewViewCreated: (PreviewView) -> Unit
) {
    Box(modifier = modifier.fillMaxSize())
}
