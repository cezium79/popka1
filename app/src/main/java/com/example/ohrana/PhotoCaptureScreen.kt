package com.example.ohrana

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ohrana.ui.components.OhranaOutlinedButton
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.camera.core.Preview as CameraPreview

/**
 * Сжимает и уменьшает изображение с использованием Downsample
 * Сначала уменьшает разрешение (ресайз), затем сжимает качество
 * @param sourceFile Исходный файл изображения
 * @param targetWidth Максимальная ширина (по умолчанию 1200px)
 * @param quality Качество сжатия JPEG (0-100, по умолчанию 75)
 * @param destFile Файл назначения (если null, создается временный файл)
 * @return Путь к сжатому файлу или null в случае ошибки
 */
fun compressAndResizeImage(
    sourceFile: File,
    targetWidth: Int = 1200,
    quality: Int = 75,
    destFile: File? = null
): String? {
    return try {
        // 1. Декодируем только размеры фото (без загрузки в память)
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(sourceFile.absolutePath, options)
        
        val srcWidth = options.outWidth
        val srcHeight = options.outHeight
        
        // 2. Вычисляем коэффициент уменьшения (inSampleSize) - должен быть степенью 2
        var inSampleSize = 1
        // Проверяем оба размера и выбираем максимальный коэффициент уменьшения
        val widthRatio = if (srcWidth > targetWidth) srcWidth.toFloat() / targetWidth.toFloat() else 1f
        val heightRatio = if (srcHeight > targetWidth) srcHeight.toFloat() / targetWidth.toFloat() else 1f
        val maxRatio = Math.max(widthRatio, heightRatio)
        
        // Преобразуем в степень 2 (ceil to next power of 2)
        if (maxRatio > 1) {
            inSampleSize = 1 shl (32 - java.lang.Integer.numberOfLeadingZeros(maxRatio.toInt() - 1))
        }
        
        // Логируем вычисления
        Log.d("ImageCompression", "=== RESIZE CALCULATIONS ===")
        Log.d("ImageCompression", "Source dimensions: ${srcWidth}x${srcHeight}")
        Log.d("ImageCompression", "Target width: $targetWidth")
        Log.d("ImageCompression", "Width ratio: ${widthRatio.format(2)}")
        Log.d("ImageCompression", "Height ratio: ${heightRatio.format(2)}")
        Log.d("ImageCompression", "Max ratio: ${maxRatio.format(2)}")
        Log.d("ImageCompression", "Final inSampleSize (power of 2): $inSampleSize")
        
        Log.d("ImageCompression", "=== SOURCE FILE INFO ===")
        Log.d("ImageCompression", "File path: ${sourceFile.absolutePath}")
        Log.d("ImageCompression", "File size: ${sourceFile.length()} bytes (${sourceFile.length() / 1024} KB)")
        
        // Ограничиваем до 8x уменьшения
        inSampleSize = inSampleSize.coerceIn(1, 8)
        
        Log.d("ImageCompression", "=== VALUE CHECK ===")
        Log.d("ImageCompression", "inSampleSize after coerceIn: $inSampleSize")
        
        // Присваиваем в переменную, чтобы избежать конфликта имён в apply
        val sampleSizeValue = if (inSampleSize > 1) inSampleSize else 1
        
        // 3. Загружаем изображение в память (с inSampleSize если он > 1)
        val bitmapOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inPreferredConfig = Bitmap.Config.RGB_565
            // Используем this для явного указания на свойство Options
            this.inSampleSize = sampleSizeValue
        }
        
        Log.d("ImageCompression", "=== DECODING WITH OPTIONS ===")
        Log.d("ImageCompression", "inSampleSize in bitmapOptions: ${bitmapOptions.inSampleSize}")
        Log.d("ImageCompression", "inPreferredConfig: ${bitmapOptions.inPreferredConfig}")
        
        val loadedBitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, bitmapOptions)
        
        if (loadedBitmap == null) {
            Log.e("ImageCompression", "Failed to decode bitmap from: ${sourceFile.absolutePath}")
            return null
        }
        
        Log.d("ImageCompression", "=== LOADED BITMAP ===")
        Log.d("ImageCompression", "Loaded dimensions: ${loadedBitmap.width}x${loadedBitmap.height}")
        Log.d("ImageCompression", "Expected with inSampleSize $inSampleSize: ${srcWidth / inSampleSize}x${srcHeight / inSampleSize}")
        
        // 4. Если inSampleSize > 1 и размер не изменился, используем createScaledBitmap
        var finalBitmap = loadedBitmap
        var actualWidth = loadedBitmap.width
        var actualHeight = loadedBitmap.height
        
        // Проверяем, применился ли inSampleSize
        val expectedWidth = if (inSampleSize > 1) srcWidth / inSampleSize else srcWidth
        val expectedHeight = if (inSampleSize > 1) srcHeight / inSampleSize else srcHeight
        
        if (bitmapOptions.inSampleSize > 1 && (actualWidth != expectedWidth || actualHeight != expectedHeight)) {
            Log.d("ImageCompression", "inSampleSize ignored! Using createScaledBitmap...")
            
            // Вычисляем целевые размеры
            val scale = targetWidth.toFloat() / srcWidth.toFloat()
            val scaledWidth = (srcWidth * scale).toInt()
            val scaledHeight = (srcHeight * scale).toInt()
            
            finalBitmap = Bitmap.createScaledBitmap(loadedBitmap, scaledWidth, scaledHeight, true)
            loadedBitmap.recycle()
            
            actualWidth = finalBitmap.width
            actualHeight = finalBitmap.height
            
            Log.d("ImageCompression", "=== RESIZED WITH createScaledBitmap ===")
            Log.d("ImageCompression", "Scaled dimensions: ${actualWidth}x${actualHeight}")
        }
        
        val originalWidth = finalBitmap.width
        val originalHeight = finalBitmap.height
        
        // 4. Создаем файл назначения, если не указан
        val targetFile = destFile ?: File.createTempFile(
            "compressed_${System.currentTimeMillis()}",
            ".jpg",
            sourceFile.parentFile
        )
        
        // 5. Сжимаем в JPEG с заданным качеством
        ByteArrayOutputStream().use { baos ->
            finalBitmap.compress(CompressFormat.JPEG, quality, baos)
            
            // Записываем в файл
            targetFile.outputStream().use { output ->
                baos.writeTo(output)
            }
            
            // Логируем результаты
            val originalSize = sourceFile.length()
            val compressedSize = targetFile.length()
            val compressionRatio = (1f - (compressedSize.toFloat() / originalSize.toFloat())) * 100f
            
            Log.d("ImageCompression", "=== COMPRESSION RESULTS ===")
            Log.d("ImageCompression", "Original: ${srcWidth}x${srcHeight}, ${originalSize} bytes (${originalSize / 1024} KB)")
            Log.d("ImageCompression", "Resized: ${originalWidth}x${originalHeight} (${actualWidth}x${actualHeight} after scaling)")
            Log.d("ImageCompression", "Compressed: ${compressedSize} bytes (${compressedSize / 1024} KB)")
            Log.d("ImageCompression", "Compression ratio: ${compressionRatio.format(1)}%")
            Log.d("ImageCompression", "Quality: $quality%")
        }
        
        // Освобождаем память
        finalBitmap.recycle()
        
        targetFile.absolutePath
    } catch (e: Exception) {
        Log.e("ImageCompression", "Error compressing image: ${e.message}", e)
        null
    }
}

/**
 * Форматирует число с заданным количеством знаков после запятой
 */
fun Float.format(decimals: Int): String {
    return String.format("%.${decimals}f", this)
}

/**
 * Конвертирует изображение в JPEG с заданным качеством сжатия (устаревшая версия)
 * @param sourceFile Исходный файл изображения
 * @param quality Качество сжатия (0-100, где 100 - максимальное качество)
 * @param destFile Файл назначения (если null, создается временный файл)
 * @return Путь к сжатому файлу или null в случае ошибки
 */
fun compressImageToJPEG(
    sourceFile: File,
    quality: Int = 80,
    destFile: File? = null
): String? {
    return try {
        // Декодируем изображение как Bitmap
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inSampleSize = 1
        }
        
        val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, options)
        
        if (bitmap == null) {
            Log.e("ImageCompression", "Failed to decode bitmap from: ${sourceFile.absolutePath}")
            return null
        }
        
        // Создаем файл назначения, если не указан
        val targetFile = destFile ?: File.createTempFile(
            "compressed_${System.currentTimeMillis()}",
            ".jpg",
            sourceFile.parentFile
        )
        
        // Сжимаем в JPEG с заданным качеством
        ByteArrayOutputStream().use { baos ->
            bitmap.compress(CompressFormat.JPEG, quality, baos)
            
            // Записываем в файл
            targetFile.outputStream().use { output ->
                baos.writeTo(output)
            }
            
            Log.d("ImageCompression", "Compressed image from ${sourceFile.length()} bytes to ${targetFile.length()} bytes (quality: $quality%)")
        }
        
        // Освобождаем память
        bitmap.recycle()
        
        targetFile.absolutePath
    } catch (e: Exception) {
        Log.e("ImageCompression", "Error compressing image: ${e.message}", e)
        null
    }
}

// Функция для сохранения фото в галерею
fun savePhotoToGallery(sourceFile: File, checkpointId: String, context: Context): String? {
    try {
        if (!sourceFile.exists()) {
            return null
        }
        
        android.util.Log.d("PhotoCaptureScreen", "=== SAVE PHOTO TO GALLERY START ===")
        android.util.Log.d("PhotoCaptureScreen", "savePhotoToGallery: sourceFile.size=${sourceFile.length()} bytes (${sourceFile.length() / 1024} KB)")
        
        // Пытаемся получить размеры изображения ДО сжатия
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(sourceFile.absolutePath, options)
            android.util.Log.d("PhotoCaptureScreen", "Source image dimensions: ${options.outWidth}x${options.outHeight}")
        } catch (e: Exception) {
            Log.d("PhotoCaptureScreen", "Could not read image dimensions: ${e.message}")
        }
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val destFileName = "${checkpointId.replace(" ", "_")}_${timestamp}.jpg"
        
        // Создаем папку в галерее
        val galleryDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val ohranaDir = File(galleryDir, "Ohrana")
        if (!ohranaDir.exists()) {
            ohranaDir.mkdirs()
        }
        
        val destFile = File(ohranaDir, destFileName)
        
        // Сжимаем изображение с ресайзом и качеством 75%
        android.util.Log.d("PhotoCaptureScreen", "Compressing and resizing image (targetWidth: 1200px, quality: 75%)...")
        val compressedPath = compressAndResizeImage(sourceFile, targetWidth = 1200, quality = 75, destFile = destFile)
        
        if (compressedPath != null) {
            val compressedFile = File(compressedPath)
            android.util.Log.d("PhotoCaptureScreen", "=== FINAL RESULT ===")
            android.util.Log.d("PhotoCaptureScreen", "Original: ${sourceFile.length()} bytes (${sourceFile.length() / 1024} KB)")
            android.util.Log.d("PhotoCaptureScreen", "Compressed: ${compressedFile.length()} bytes (${compressedFile.length() / 1024} KB)")
            android.util.Log.d("PhotoCaptureScreen", "Compression ratio: ${(1f - (compressedFile.length().toFloat() / sourceFile.length().toFloat())) * 100f}%")
            
            // Обновляем галерею через MediaScannerConnection
            MediaScannerConnection.scanFile(
                context,
                arrayOf(compressedFile.absolutePath),
                arrayOf("image/jpeg"),
                null
            )
            
            // Удаляем временный файл из private папки
            sourceFile.delete()
            
            return compressedFile.absolutePath
        } else {
            android.util.Log.e("PhotoCaptureScreen", "Failed to compress image!")
            return null
        }
    } catch (e: Exception) {
        android.util.Log.e("PhotoCaptureScreen", "Error in savePhotoToGallery: ${e.message}", e)
        e.printStackTrace()
        return null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoCaptureScreen(
    checkpointId: String,
    onPhotoTaken: (String) -> Unit,
    onBack: () -> Unit,
    onCheckpointComplete: () -> Unit,
    prefsManager: SharedPrefsManager,
    employeeName: String
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Получаем URI картинки прибора
    // Сначала пытаемся загрузить из данных чекпоинта, затем из SharedPreferences
    val checkpointFromDatabase = prefsManager.getCheckpointById(checkpointId)
    var deviceImageUri by remember {
        mutableStateOf(
            checkpointFromDatabase?.imageUri ?: prefsManager.getCheckpointImageUri(checkpointId)
        )
    }

    LaunchedEffect(prefsManager, checkpointId) {
        val checkpoint = prefsManager.getCheckpointById(checkpointId)
        deviceImageUri = checkpoint?.imageUri ?: prefsManager.getCheckpointImageUri(checkpointId)
    }

    // Состояния: false = камера, true = предпросмотр
    var isPreviewMode by remember { mutableStateOf(false) }
    // Храним путь к последнему снятоому фото
    var lastPhotoPath by remember { mutableStateOf("") }
    // Храним Bitmap для отображения в предпросмотре
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // ImageCapture для захвата фото
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    // Флаг для отслеживания завершения
    var isPhotoComplete by remember { mutableStateOf(false) }

    // Для показа диалога успешного прохождения чекпоинта
    var showCheckpointPassedDialog by remember { mutableStateOf(false) }

    // Загрузка фоновой картинки fon2
    val bitmap = android.graphics.BitmapFactory.decodeResource(
        context.resources,
        com.example.ohrana.R.drawable.fon2
    )

    // Сначала сохраняем в приватную папку
    val filesDir = context.filesDir
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val fileName = "${checkpointId.replace(" ", "_")}_${timestamp}.jpg"
    val imageFile = File(filesDir, fileName)

    // Показ диалога при завершении съемки
    LaunchedEffect(isPhotoComplete) {
        if (isPhotoComplete) {
            showCheckpointPassedDialog = true
        }
    }

    LaunchedEffect(showCheckpointPassedDialog) {
        if (showCheckpointPassedDialog) {
            val activeRoundIndex = prefsManager.getActiveRoundIndex()
            if (activeRoundIndex != -1) {
                val nextIndex = prefsManager.getRoundCheckpointIndex(activeRoundIndex) + 1
                prefsManager.updateCurrentCheckpointIndex(nextIndex)
            }
        }
    }

    // Функция захвата фото через CameraX
    fun capturePhoto() {
        val capture = imageCapture ?: run {
            return
        }

        try {
            // Создаем OutputFileOptions для сохранения в files/ папку
            val outputFileOptions = ImageCapture.OutputFileOptions.Builder(imageFile).build()

            capture.takePicture(
                outputFileOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        lastPhotoPath = imageFile.absolutePath

                        // Проверяем, что файл действительно создан
                        if (imageFile.exists()) {
                            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                            if (bitmap != null) {
                                capturedBitmap = bitmap
                                isPreviewMode = true

                                // Сохраняем лог в SharedPreferences
                                val logText = "Фото прибора: $checkpointId -> Файл: $fileName"
                                prefsManager.saveScanResult(
                                    employeeName = employeeName,
                                    qrContent = logText
                                )

                                Log.d(
                                    "PhotoCaptureScreen",
                                    "Photo saved successfully for checkpoint $checkpointId"
                                )
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        exception.printStackTrace()

                        // Воспроизводим звук ошибки (только если включен звук)
                        if (prefsManager.isSoundEnabled()) {
                            SoundPlayer.playError(context)
                        }

                        Log.e(
                            "PhotoCaptureScreen",
                            "Photo capture failed for checkpoint $checkpointId: ${exception.message}"
                        )
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
                // Сохраняем ImageCapture для последующего использования
                imageCapture = capture
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Разрешение на запись во внешнее хранилище
    var hasStoragePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
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
                        Text("Предпросмотр")
                    } else {
                        Text("Съемка прибора: $checkpointId")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        // Уменьшаем currentIndex в QrHandler при возврате, чтобы можно было повторно сканировать тот же QR-код
                        QrHandler.rollback()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
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
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )

            // Контент поверх фона
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(2.dp)//отступ от TopAppBar
            ) {
                // Камера
                if (!isPreviewMode) {
                    // Вертикальное расположение блоков один под другим с зазором 4dp
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Top)
                    ) {
                        // Верхняя часть - картинка прибора (пример)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF333333))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Пример прибора:",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                if (deviceImageUri != null) {
                                    // Проверяем, является ли deviceImageUri именем файла (относительный путь) или URI
                                    val file = File(context.filesDir, deviceImageUri!!)
                                    val bitmapState = remember(deviceImageUri) {
                                        androidx.compose.runtime.mutableStateOf<android.graphics.Bitmap?>(
                                            null
                                        )
                                    }
                                    LaunchedEffect(deviceImageUri) {
                                        bitmapState.value = runCatching {
                                            if (file.exists()) {
                                                // Файл находится в filesDir
                                                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                                            } else {
                                                // Старый формат - URI
                                                val uri = android.net.Uri.parse(deviceImageUri)
                                                android.graphics.BitmapFactory.decodeStream(
                                                    context.contentResolver.openInputStream(
                                                        uri
                                                    )
                                                )
                                            }
                                        }.getOrNull()
                                    }
                                    if (bitmapState.value != null) {
                                        androidx.compose.foundation.Image(
                                            bitmap = bitmapState.value!!.asImageBitmap(),
                                            contentDescription = "Картинка прибора",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(120.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    } else {
                                        // Заглушка, если картинка не выбрана
                                        Text(
                                            text = "[Картинка прибора]\n(Настройка в меню маршрутов)",
                                            fontSize = 14.sp,
                                            color = Color.LightGray,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Нижняя часть - камера (фиксированный размер 330x440)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    val previewView = PreviewView(ctx)
                                    previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
                                    initCamera(previewView)
                                    previewView
                                },
                                modifier = Modifier.size(
                                    width = 240.dp,
                                    height = 320.dp
                                ) // размер экрана камеры
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))//отступ

                        // Кнопка "Сделать фото" по центру внизу
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            OhranaOutlinedButton(
                                text = "Сделать фото",
                                onClick = {
                                    capturePhoto()
                                },
                                modifier = Modifier.width(400.dp).height(56.dp),//размеры кнопки
                                designId = 3
                            )
                        }
                    }
                } else {
                    // Предпросмотр
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.6f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (capturedBitmap != null) {
                            Spacer(modifier = Modifier.height(50.dp))
                            Image(
                                bitmap = capturedBitmap!!.asImageBitmap(),
                                contentDescription = "Предпросмотр фото",
                                modifier = Modifier
                                    .width(270.dp)
                                    .height(360.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.LightGray),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Нет изображения")
                            }
                        }
                    }

                    // Кнопки управления
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(modifier = Modifier.padding(8.dp))
                        {
                            OhranaOutlinedButton(
                                text = "Сделать заново",
                                onClick = {
                                    // Сделать заново - возвращаемся к камере
                                    isPreviewMode = false
                                    capturedBitmap = null
                                    lastPhotoPath = ""
                                    // Удаляем старый файл
                                    if (imageFile.exists()) {
                                        imageFile.delete()
                                    }
                                },
                                modifier = Modifier.width(380.dp).height(56.dp),
                                designId = 3
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OhranaOutlinedButton(
                                text = "Сохранить",
                                onClick = {
                                    // Сохраняем фото и возвращаемся в cabinet
                                    // Сохраняем фото в галерею
                                    val savedFileName =
                                        savePhotoToGallery(imageFile, checkpointId, context)
                                    if (savedFileName != null) {
                                        onPhotoTaken(savedFileName)
                                        isPhotoComplete = true
                                    }
                                },
                                modifier = Modifier.width(380.dp).height(56.dp),
                                designId = 3
                            )
                        }
                    
                    }
                }
            }
        }

        // Диалоговое окно успешного прохождения чекпоинта (показывается после нажатия "Сохранить")
        if (showCheckpointPassedDialog) {
            // Воспроизводим звук успеха при показе диалога (только если включен звук)
            LaunchedEffect(showCheckpointPassedDialog) {
                if (prefsManager.isSoundEnabled()) {
                    SoundPlayer.playSuccess(context)
                }
            }

            // Таймер для автоматического закрытия диалога
            LaunchedEffect(showCheckpointPassedDialog) {
                if (showCheckpointPassedDialog) {
                    kotlinx.coroutines.delay(3000) // Ждем 3 секунды
                    showCheckpointPassedDialog = false
                    onBack()
                }
            }

            AlertDialog(
                onDismissRequest = { }, // Запрещаем закрытие кликом вне
                title = { Text("Точка зафиксирована") },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Иконка успешного прохождения
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = android.graphics.BitmapFactory.decodeResource(
                                    context.resources,
                                    com.example.ohrana.R.drawable.vokak
                                ).asImageBitmap(),
                                contentDescription = "Успех",
                                modifier = Modifier.size(100.dp)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Фото прибора: $checkpointId",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Файл сохранен в галерею",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Время: ${
                                    java.text.SimpleDateFormat(
                                        "HH:mm:ss dd.MM.yyyy",
                                        java.util.Locale.getDefault()
                                    ).format(java.util.Date())
                                }",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {}  // Пустой confirmButton для компиляции
            )
        }

        // Обработка системной кнопки "Назад"
        BackHandler(onBack = {
            // Уменьшаем currentIndex в QrHandler при возврате, чтобы можно было повторно сканировать тот же QR-код
            QrHandler.rollback()
            onBack()
        })
    }

    // Функция для сохранения фото в галерею
    fun savePhotoToGallery(sourceFile: File, checkpointId: String, context: Context): String? {
        try {
            if (!sourceFile.exists()) {
                return null
            }

            android.util.Log.d("PhotoCaptureScreen", "=== SAVE PHOTO TO GALLERY START ===")
            android.util.Log.d(
                "PhotoCaptureScreen",
                "savePhotoToGallery: sourceFile.size=${sourceFile.length()} bytes (${sourceFile.length() / 1024} KB)"
            )

            // Пытаемся получить размеры изображения ДО сжатия
            try {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(sourceFile.absolutePath, options)
                android.util.Log.d(
                    "PhotoCaptureScreen",
                    "Source image dimensions: ${options.outWidth}x${options.outHeight}"
                )
            } catch (e: Exception) {
                android.util.Log.d(
                    "PhotoCaptureScreen",
                    "Could not read image dimensions: ${e.message}"
                )
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val destFileName = "${checkpointId.replace(" ", "_")}_${timestamp}.jpg"

            // Создаем папку в галерее
            val galleryDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val ohranaDir = File(galleryDir, "Ohrana")
            if (!ohranaDir.exists()) {
                ohranaDir.mkdirs()
            }

            val destFile = File(ohranaDir, destFileName)

            // Сжимаем изображение с ресайзом и качеством 75%
            android.util.Log.d(
                "PhotoCaptureScreen",
                "Compressing and resizing image (targetWidth: 1200px, quality: 75%)..."
            )
            val compressedPath = compressAndResizeImage(
                sourceFile,
                targetWidth = 1200,
                quality = 75,
                destFile = destFile
            )

            if (compressedPath != null) {
                val compressedFile = File(compressedPath)
                android.util.Log.d("PhotoCaptureScreen", "=== FINAL RESULT ===")
                android.util.Log.d(
                    "PhotoCaptureScreen",
                    "Original: ${sourceFile.length()} bytes (${sourceFile.length() / 1024} KB)"
                )
                android.util.Log.d(
                    "PhotoCaptureScreen",
                    "Compressed: ${compressedFile.length()} bytes (${compressedFile.length() / 1024} KB)"
                )
                android.util.Log.d(
                    "PhotoCaptureScreen",
                    "Compression ratio: ${
                        (1f - (compressedFile.length().toFloat() / sourceFile.length()
                            .toFloat())) * 100f
                    }%"
                )

                // Обновляем галерею через MediaScannerConnection
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(compressedFile.absolutePath),
                    arrayOf("image/jpeg"),
                    null
                )

                // Удаляем временный файл из private папки
                sourceFile.delete()

                return compressedFile.absolutePath
            } else {
                android.util.Log.e("PhotoCaptureScreen", "Failed to compress image!")
                return null
            }
        } catch (e: Exception) {
            android.util.Log.e("PhotoCaptureScreen", "Error in savePhotoToGallery: ${e.message}", e)
            e.printStackTrace()
            return null
        }
    }
}