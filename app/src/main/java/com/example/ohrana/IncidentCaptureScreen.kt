package com.example.ohrana

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.Composable
import com.example.ohrana.ui.components.OhranaOutlinedButton
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.Log
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentCaptureScreen(
    roundId: Int,
    shiftId: String,
    onIncidentSaved: () -> Unit,
    onBack: () -> Unit,
    prefsManager: SharedPrefsManager,
    employeeName: String
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Состояния
    var isPreviewMode by remember { mutableStateOf(false) }
    var lastPhotoPath by remember { mutableStateOf("") }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    
    // Выбор типа происшествия
    var selectedIncidentType by remember { mutableStateOf(IncidentType.OTHER) }
    
    // Ввод описания
    var description by remember { mutableStateOf("") }
    
    // Флаг для отслеживания завершения
    var isIncidentComplete by remember { mutableStateOf(false) }
    
    // Для показа диалога успешного сохранения
    var showSuccessDialog by remember { mutableStateOf(false) }
    
    // Для показа диалога комментария
    var showCommentDialog by remember { mutableStateOf(false) }
    
    // Загрузка фоновой картинки
    val bitmap = android.graphics.BitmapFactory.decodeResource(context.resources, com.example.ohrana.R.drawable.fon2)
    
    // Сначала сохраняем в приватную папку
    val filesDir = context.filesDir
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val fileName = "incident_${timestamp}.jpg"
    val imageFile = File(filesDir, fileName)

    // Вызываем onIncidentComplete при завершении съемки
    LaunchedEffect(isIncidentComplete) {
        if (isIncidentComplete) {
            showSuccessDialog = true
            isIncidentComplete = false
        }
    }
    
    // Обрабатываем закрытие диалога и переход к cabinet
    LaunchedEffect(showSuccessDialog) {
        if (showSuccessDialog == false && capturedBitmap != null) {
            // Показали диалог - теперь завершаем экран
            onIncidentSaved()
            onBack()
        }
    }

    // Функция захвата фото через CameraX
    fun capturePhoto() {
        val capture = imageCapture ?: run {
            return
        }

        try {
            val outputFileOptions = ImageCapture.OutputFileOptions.Builder(imageFile).build()
            
            capture.takePicture(
                outputFileOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        lastPhotoPath = imageFile.absolutePath
                        
                        if (imageFile.exists()) {
                            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                            if (bitmap != null) {
                                capturedBitmap = bitmap
                                isPreviewMode = true
                                Log.d("IncidentCaptureScreen", "Photo saved successfully for incident")
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        exception.printStackTrace()
                        
                        if (prefsManager.isSoundEnabled()) {
                            SoundPlayer.playError(context)
                        }
                        
                        Log.e("IncidentCaptureScreen", "Photo capture failed: ${exception.message}")
                    }
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Функция инициализации камеры
    fun initCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = CameraPreview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val capture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    capture
                )
                imageCapture = capture
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Разрешение на запись во внешнее хранилище
    var hasStoragePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val storageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasStoragePermission = granted }
    )
    
    // Запрашиваем разрешение при старте
    LaunchedEffect(Unit) {
        if (!hasStoragePermission) {
            storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (isPreviewMode) {
                        Box {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                            ) {
                        Text("Предпросмотр", color = Color(0xFFF5F5F5))}}
                    } else {
                        Box {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                            ) {
                            Text("Зафиксировать происшествие", color = Color(0xFFF5F5F5))}}
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color(0xFFF5F5F5))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Фоновая картинка fon2 - на весь экран
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            
            // Контент поверх фона
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                // Камера по центру, уменьшенный размер до 250x300 dp
                Box(
                    modifier = Modifier
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isPreviewMode) {
                        AndroidView(
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    scaleType = PreviewView.ScaleType.FIT_CENTER
                                    initCamera(this)
                                }
                            },
                            modifier = Modifier.size(width = 330.dp, height = 440.dp)
                        )
                    } else {
                        // Предпросмотр
                        if (capturedBitmap != null) {
                            Image(
                                bitmap = capturedBitmap!!.asImageBitmap(),
                                contentDescription = "Предпросмотр фото",
                                modifier = Modifier
                                    .size(width = 250.dp, height = 300.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(width = 210.dp, height = 280.dp)
                                    .background(Color.LightGray),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Нет изображения")
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(50.dp))
                
                if (!isPreviewMode) {
                    // Кнопка "Сделать фото" в самом низу по центру
                    Box {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OhranaOutlinedButton(
                                text = "Сделать фото",
                                onClick = {
                                    capturePhoto()
                                },
                                modifier = Modifier.width(200.dp).height(56.dp),
                                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                                designId = 3
                            )
                        }
                    }
                } else {
                    // Кнопки управления внизу
                    Column {
                        Box {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier.weight(0.5f)
                                ) {
                                    OhranaOutlinedButton(
                                        text = "Повторить",
                                        onClick = {
                                            isPreviewMode = false
                                            capturedBitmap = null
                                            lastPhotoPath = ""
                                            if (imageFile.exists()) {
                                                imageFile.delete()
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp),
                                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                                        designId = 3
                                    )
                                }
                                
                                Box(
                                    modifier = Modifier.weight(0.5f)
                                ) {
                                    OhranaOutlinedButton(
                                        text = "Комментарий",
                                        onClick = {
                                            showCommentDialog = true
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp),
                                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                                        designId = 3
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        Box {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier.weight(0.5f)
                                ) {
                                    OhranaOutlinedButton(
                                        text = "Сохранить",
                                        onClick = {
                                            // Сохраняем фото и создаем запись
                                            Log.d("IncidentCaptureScreen", "Saving incident photo...")
                                            val savedFileName = saveIncidentPhotoToGallery(imageFile, context)
                                            Log.d("IncidentCaptureScreen", "Photo saved to: $savedFileName")
                                            savedFileName?.let { 
                                                Log.d("IncidentCaptureScreen", "Creating incident record...")
                                                // Создаем запись о происшествии
                                                val shiftDatabase = ShiftDatabaseManager(context)
                                                shiftDatabase.addIncident(
                                                    roundId = roundId,
                                                    shiftId = shiftId,
                                                    employeeName = employeeName,
                                                    incidentType = selectedIncidentType,
                                                    description = description,
                                                    photoPath = it
                                                )
                                                Log.d("IncidentCaptureScreen", "Incident saved: type=${selectedIncidentType.name}, description=$description")
                                            } ?: run {
                                                Log.e("IncidentCaptureScreen", "Failed to save photo to gallery")
                                            }
                                            isIncidentComplete = true
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp),
                                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                                        designId = 3
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Диалоговое окно успешного сохранения
    if (showSuccessDialog) {
        LaunchedEffect(showSuccessDialog) {
            if (prefsManager.isSoundEnabled()) {
                SoundPlayer.playSuccess(context)
            }
        }
        
        var autoCloseTrigger by remember { mutableStateOf(false) }
        
        LaunchedEffect(showSuccessDialog) {
            autoCloseTrigger = false
            kotlinx.coroutines.delay(100)
            kotlinx.coroutines.delay(3000)
            showSuccessDialog = false
        }
        
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Происшествие зафиксировано") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
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
                            text = "Тип: ${selectedIncidentType.ruName}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Описание: $description",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Фото сохранено в галерею",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Проверяем, вне обхода или нет
                        if (roundId == -1) {
                            if (shiftId == "outside_shift") {
                                Text(
                                    text = "Статус: Вне обхода",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text(
                                    text = "Статус: Смена активна, но нет активного обхода",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Text(
                                text = "Время: ${java.text.SimpleDateFormat("HH:mm:ss dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date())}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {}  // Пустой confirmButton для компиляции
        )
    }
    
    // Диалог для выбора типа и ввода описания
    if (showCommentDialog) {
        var tempSelectedType by remember { mutableStateOf(selectedIncidentType) }
        var tempDescription by remember { mutableStateOf(description) }
        
        AlertDialog(
            onDismissRequest = { showCommentDialog = false },
            title = { Text("Комментарий к происшествию") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Выбор типа
                    ScrollableTabRow(selectedTabIndex = selectedIncidentType.ordinal) {
                        IncidentType.values().forEach { type ->
                            var selected by remember { mutableStateOf(tempSelectedType == type) }
                            Tab(
                                selected = selected,
                                onClick = { 
                                    tempSelectedType = type
                                    selected = true
                                }
                            ) {
                                Text(
                                    text = type.ruName,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    
                    OutlinedTextField(
                        value = tempDescription,
                        onValueChange = { tempDescription = it },
                        label = { Text("Описание происшествия") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedIncidentType = tempSelectedType
                        description = tempDescription
                        showCommentDialog = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCommentDialog = false }
                ) {
                    Text("Отмена")
                }
            }
        )
    }
    
    // Обработка системной кнопки "Назад"
    BackHandler(onBack = onBack)
}

// Функция для сохранения фото происшествия в галерею
fun saveIncidentPhotoToGallery(sourceFile: File, context: Context): String? {
    try {
        if (!sourceFile.exists()) {
            return null
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val destFileName = "incident_${timestamp}.jpg"

        // Создаем папку в галерее: Ohrana/Incidents/
        val galleryDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val ohranaIncidentsDir = File(galleryDir, "Ohrana/Incidents")
        if (!ohranaIncidentsDir.exists()) {
            ohranaIncidentsDir.mkdirs()
        }

        val destFile = File(ohranaIncidentsDir, destFileName)

        // Копируем файл
        sourceFile.inputStream().use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        // Проверяем, что файл скопирован
        if (destFile.exists()) {
            // Обновляем галерею через MediaScannerConnection
            MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                arrayOf("image/jpeg"),
                null
            )

            // Удаляем временный файл из private папки
            sourceFile.delete()

            return destFile.absolutePath
        } else {
            return null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}
