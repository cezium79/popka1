package com.example.ohrana

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import android.os.Build
import android.print.PrintAttributes
import android.print.PrintManager
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import java.io.FileOutputStream
import java.io.File
import androidx.activity.compose.BackHandler
import android.os.Environment
import android.net.Uri
import android.content.Intent
import android.content.ContentValues
import android.graphics.pdf.PdfDocument
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.res.painterResource

private const val TAG = "ShiftLogDetailScreen"

// Вспомогательная функция для масштабирования изображения до максимального размера
private fun scaleBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    
    val scaleWidth = maxWidth.toFloat() / width
    val scaleHeight = maxHeight.toFloat() / height
    
    val scale = minOf(scaleWidth, scaleHeight)
    
    return Bitmap.createBitmap(
        bitmap,
        0,
        0,
        width,
        height,
        android.graphics.Matrix().apply {
            postScale(scale, scale)
        },
        true
    )
}

// Вспомогательная функция для отрисовки изображения в PDF
private fun drawImageOnPdf(
    canvas: android.graphics.Canvas,
    paint: android.graphics.Paint,
    bitmap: Bitmap,
    x: Float,
    y: Float,
    maxWidth: Float,
    maxHeight: Float
): Float {
    val scaledBitmap = scaleBitmap(bitmap, maxWidth.toInt(), maxHeight.toInt())
    
    val left = x + (maxWidth - scaledBitmap.width) / 2
    val top = y + (maxHeight - scaledBitmap.height) / 2
    
    canvas.drawBitmap(scaledBitmap, left, top, paint)
    
    scaledBitmap.recycle()
    
    return y + maxHeight + 10f
}

// Вспомогательная функция для проверки существования файла
private fun fileExists(path: String?): Boolean {
    return path != null && File(path).exists()
}

// Извлекает порядковый номер смены из её ID
// Формат ID: NSDDMMYY_NNN (например, NS020726_001 -> номер 1)
private fun extractShiftSequenceNumber(shiftId: String): Int {
    val pattern = Pattern.compile("NS\\d{6}_(\\d{3})")
    val matcher = pattern.matcher(shiftId)
    return if (matcher.find()) {
        matcher.group(1)?.toInt() ?: 0
    } else {
        0
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftLogDetailScreen(
    onBack: () -> Unit,
    shiftId: String,
    employeeName: String
) {
    val context = LocalContext.current
    val prefsManager = remember { SharedPrefsManager(context) }
    val shiftDatabase = prefsManager.shiftDatabase
    
    // Загружаем данные смены
    val shift = remember { shiftDatabase.loadAllShifts().find { it.id == shiftId } }
    
    // Загружаем логи для этой смены
    val logs = remember { shiftDatabase.loadLogsByShift(shiftId) }
    
    // Загружаем обходы для этой смены
    val rounds = remember { shiftDatabase.loadAllRounds().filter { it.shiftId == shiftId } }
    
    // Сортируем логи по времени
    val sortedLogs = logs.sortedBy { it.timestamp }
    
    // Сортируем обходы по времени начала
    val sortedRounds = rounds.sortedBy { it.startTime }
    
    // Переменные для отображения фото
    var showPhotoDialog by remember { mutableStateOf(false) }
    var selectedPhotoPath by remember { mutableStateOf<String?>(null) }
    
    // Переменная для диалога экспорта в PDF
    var showPdfExportDialog by remember { mutableStateOf(false) }
    
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Детали смены") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Размытый фон
            BlurredBackground()
            
            // Контент экрана
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
            if (shift == null) {
                Text(
                    text = "Смена не найдена",
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // Информация о смене
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth()
                            ) {
                                // Извлекаем номер смены из ID
                                val shiftNumber = extractShiftSequenceNumber(shift.id)
                                
                                Text(
                                    text = "СМЕНА №$shiftNumber",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Сотрудник: ${shift.employeeName}",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Время начала: ${shift.startTime}",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Время окончания: ${shift.endTime ?: "-"}",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Контроль последовательности: ${if (shift.strictSequenceEnabled) "ВКЛ" else "ВЫКЛ"}",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    // Обходы
                    sortedRounds.forEach { round ->
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .fillMaxWidth()
                                ) {
                                    Text(
                                        text = "ОБХОД №${round.id}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Маршрут: ${round.routeName ?: "не указан"}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Время начала: ${round.startTime}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Время окончания: ${round.endTime ?: "-"}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Чекпоинтов в маршруте: ${round.checkpointsCount}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    // Подсчитываем пройденные чекпоинты на основе логов (исключаем SCAN записи)
                                    val roundLogs = sortedLogs.filter { it.roundId == round.id }
                                    val passedCount = roundLogs.filter { it.actionType != "SCAN" }.size
                                    Text(
                                        text = "Пройдено чекпоинтов: $passedCount",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Нарушений последовательности: ${round.sequenceViolations}",
                                        fontSize = 12.sp,
                                        color = if (round.sequenceViolations > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Заголовок таблицы логов
                            Text(
                                text = "Логи обхода:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Заголовок таблицы
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Чекпоинт",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "Время",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(60.dp)
                                )
                                Text(
                                    text = "Статус",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            HorizontalDivider(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 1.dp
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // Логи этого обхода
                            val roundLogs = sortedLogs.filter { it.roundId == round.id }
                            
                            if (roundLogs.isNotEmpty()) {
                                Column {
                                    roundLogs.forEach { log ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = log.checkpointName,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (log.isSequenceCorrect) FontWeight.Medium else FontWeight.Bold,
                                                    color = if (!log.isSequenceCorrect) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            
                                            Text(
                                                text = log.timestamp.split(" ").getOrNull(1) ?: "-",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.width(60.dp)
                                            )
                                            
                                            Column(
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                val resultText = when (log.actionType) {
                                                    "CHECKPOINT" -> "✓ Пройден"
                                                    "QUESTION" -> "❓ Ответ: ${log.answer ?: "-"}"
                                                    "INPUT" -> "✏️ Введено: ${log.inputValue ?: "-"}"
                                                    "PHOTO" -> "📷 Фото"
                                                    "SCAN" -> "🔍 Сканирование"
                                                    else -> "❓ Статус: ${if (log.isSequenceCorrect) "OK" else "НАРУШЕНИЕ"}"
                                                }
                                                Text(
                                                    text = resultText,
                                                    fontSize = 12.sp,
                                                    color = if (!log.isSequenceCorrect) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                
                                                // Кнопка просмотра фото, если фото было снято
                                                if (log.actionType == "PHOTO" && log.photoPath != null) {
                                                    Button(
                                                        onClick = {
                                                            selectedPhotoPath = log.photoPath
                                                            showPhotoDialog = true
                                                        },
                                                        modifier = Modifier.padding(top = 4.dp),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                    ) {
                                                        Text("👁️ Фото", fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }
                                        
                                        // Строки с информацией о нарушении
                                        if (!log.isSequenceCorrect) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                                horizontalArrangement = Arrangement.Start
                                            ) {
                                                Text(
                                                    text = "⚠️",
                                                    fontSize = 16.sp,
                                                    color = MaterialTheme.colorScheme.error,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            
                                            if (log.sequenceErrorType != SequenceErrorType.NONE) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 24.dp, end = 8.dp, bottom = 4.dp),
                                                    horizontalArrangement = Arrangement.Start
                                                ) {
                                                    val errorTypeName = when (log.sequenceErrorType) {
                                                        SequenceErrorType.FOREIGN_CHECKPOINT -> "Чужая метка"
                                                        SequenceErrorType.OUTSIDE_ROUTE -> "Вне маршрута"
                                                        SequenceErrorType.OUT_OF_SEQUENCE -> "Вне очереди"
                                                        SequenceErrorType.NONE -> ""
                                                    }
                                                    Text(
                                                        text = "${if (log.actionType == "CHECKPOINT") "Нарушение:" else "Ожидался:"} $errorTypeName",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                                    )
                                                }
                                            }
                                        }
                                        
                                        // Разделитель между записями
                                        HorizontalDivider(
                                            modifier = Modifier.fillMaxWidth(),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            thickness = 0.5.dp
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                    
                    // Итоговая статистика
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    text = "ИТОГО",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Всего обходов: ${rounds.size}",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Всего пройденных чекпоинтов: ${logs.size}",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val totalViolations = rounds.sumOf { it.sequenceViolations }
                                Text(
                                    text = "Всего нарушений: $totalViolations",
                                    fontSize = 14.sp,
                                    color = if (totalViolations > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Кнопка загрузки в облако (скрыта - будет реализована позже)
                                /*
                                OutlinedButton(
                                    onClick = {
                                        val cloudManager = CloudStorageManager(context)
                                        val cloudToken = cloudManager.getOAuthToken()
                                        val diskToken = cloudManager.getDiskToken()
                                        
                                        if ((cloudToken == null || cloudToken.isEmpty()) && (diskToken == null || diskToken.isEmpty())) {
                                            // Token не найден, показываем диалог с предложением настроить облачное хранилище
                                            Toast.makeText(
                                                context,
                                                "Настройте облачные хранилища в настройках",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            Log.w(TAG, "OAuth tokens not found, redirecting to cloud settings")
                                        } else {
                                            // Показываем диалог выбора хранилища
                                            showStorageSelectionDialog = true
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.secondary
                                    )
                                ) {
                                    Text("📤 Загрузить в облако", fontSize = 12.sp)
                                }
                                */
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
            
            // Кнопка экспорта внизу экрана
            Button(
                onClick = { showPdfExportDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("ЭКСПОРТИРОВАТЬ В PDF", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            }
        }
    }
    
    // Диалог для просмотра фото
    if (showPhotoDialog && selectedPhotoPath != null) {
        val photoFile = remember(selectedPhotoPath) {
            java.io.File(selectedPhotoPath!!)
        }
        
        var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
        
        LaunchedEffect(photoFile) {
            if (photoFile.exists()) {
                bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
            }
        }
        
        AlertDialog(
            onDismissRequest = { showPhotoDialog = false },
            title = { Text("Фото чекпоинта") },
            text = {
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "Фото чекпоинта",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Фото не найдено", fontSize = 14.sp)
                        Text(photoFile.absolutePath, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showPhotoDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }
    
    // Диалог экспорта в PDF
    if (showPdfExportDialog) {
        AlertDialog(
            onDismissRequest = { showPdfExportDialog = false },
            title = { Text("Экспорт отчета") },
            text = { 
                Column {
                    Text("Экспортировать отчет в PDF формат?")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Также можно загрузить отчет в облачное хранилище (JSON/HTML)")
                }
            },
            confirmButton = {
                Column {
                    Button(onClick = { 
                        showPdfExportDialog = false
                        val pdfPath = exportToPdf(shift!!, rounds, sortedLogs, context, null)
                        if (pdfPath != null) {
                            android.widget.Toast.makeText(context, "PDF сохранен: $pdfPath", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }) {
                        Text("Экспорт в PDF")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    /*
                    OutlinedButton(
                        onClick = { 
                            showPdfExportDialog = false
                            val cloudManager = CloudStorageManager(context)
                            val token = cloudManager.getOAuthToken()
                            
                            if (token == null || token.isEmpty()) {
                                Toast.makeText(
                                    context,
                                    "Настройте Yandex Cloud в настройках",
                                    Toast.LENGTH_LONG
                                ).show()
                                Log.w(TAG, "OAuth token not found in PDF export dialog")
                            } else {
                                val reportResult = cloudManager.exportShiftReportWithCloud(shift!!.id, shiftDatabase, uploadToCloud = true)
                                
                                if (reportResult.isSuccess()) {
                                    Toast.makeText(
                                        context,
                                        "Отчет загружен в облако!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    Log.i(TAG, "Report uploaded to cloud from PDF export dialog for shift ${shift.id}")
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Ошибка при загрузке: ${reportResult.getErrorMessage()}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    Log.e(TAG, "Failed to upload report from PDF export dialog for shift ${shift.id}: ${reportResult.getErrorMessage()}")
                                }
                            }
                        }
                    ) {
                        Text("📤 Загрузить в облако", fontSize = 12.sp)
                    }
                    */
                }
            },
            dismissButton = {
                Button(onClick = { showPdfExportDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
    
    // Обработка системной кнопки "Назад"
    BackHandler(onBack = onBack)
}

// Функция экспорта отчета в PDF
// Возвращает путь к сохраненному файлу или null в случае ошибки
fun exportToPdf(shift: ShiftRecord, rounds: List<RoundRecord>, logs: List<ShiftLogEntry>, context: android.content.Context, outputPath: String? = null, showPreview: Boolean = true): String? {
    val shiftNumber = extractShiftSequenceNumber(shift.id)
    val fileName = "shift_report_${shift.id}_${java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())}.pdf"
    
    try {
        // Создаем PDF документ
        val pdfDocument = PdfDocument()
        var currentPage = 1
        
        // Создаем страницу
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, currentPage).create()
        var page = pdfDocument.startPage(pageInfo)
        
        var canvas = page.canvas
        val paint = android.graphics.Paint()
        
        // Инициализируем начальную позицию по Y
        var yPos = 40f
        
        // Создаем красивые цвета Material Design
        val primaryColor = android.graphics.Color.rgb(33, 150, 243) // Blue 500
        val primaryDarkColor = android.graphics.Color.rgb(30, 136, 229) // Blue 600
        val secondaryColor = android.graphics.Color.rgb(96, 125, 139) // Blue Grey 600
        val accentColor = android.graphics.Color.rgb(255, 87, 34) // Deep Orange 500
        val backgroundColor = android.graphics.Color.rgb(245, 245, 245) // Grey 100
        val headerBackgroundColor = android.graphics.Color.rgb(63, 81, 181) // Indigo 700
        val textColor = android.graphics.Color.rgb(33, 33, 33) // Grey 900
        val dividerColor = android.graphics.Color.rgb(224, 224, 224) // Grey 300
        val errorColor = android.graphics.Color.rgb(244, 67, 54) // Red 500
        val successColor = android.graphics.Color.rgb(76, 175, 80) // Green 500
        
        paint.textSize = 14f
        paint.typeface = android.graphics.Typeface.DEFAULT
        
        // Функция для рисования заголовка на новой странице
        fun drawPageHeader(pageNum: Int, totalPages: Int = -1) {
            // Заголовочная полоса
            paint.color = headerBackgroundColor
            canvas.drawRect(0f, 0f, 595f, 60f, paint)
            
            // Заголовок отчета
            paint.color = android.graphics.Color.WHITE
            paint.textSize = 22f
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            canvas.drawText("ОТЧЕТ О СМЕНЕ №$shiftNumber", 20f, 42f, paint)
            
            // Подзаголовок - информация о смене
            paint.textSize = 12f
            paint.typeface = android.graphics.Typeface.DEFAULT
            val datePart = shift.startTime.substring(0, 10)
            canvas.drawText("Дата: $datePart  |  Сотрудник: ${shift.employeeName}", 20f, 65f, paint)
            canvas.drawText("Начало: ${shift.startTime}", 20f, 78f, paint)
            
            // Номер страницы
            val totalPagesText = if (totalPages > 0) " / $totalPages" else ""
            paint.textSize = 10f
            val pageText = "Страница: $pageNum$totalPagesText"
            canvas.drawText(pageText, 575f - paint.measureText(pageText) - 10f, 820f, paint)
        }
        
        // Функция для проверки необходимости новой страницы и создания новой
        fun checkNewPage(minHeight: Float) {
            if (yPos + minHeight > 800f) {
                // Завершаем текущую страницу
                pdfDocument.finishPage(page)
                
                // Создаем новую страницу
                currentPage++
                val newPageInfo = PdfDocument.PageInfo.Builder(595, 842, currentPage).create()
                page = pdfDocument.startPage(newPageInfo)
                canvas = page.canvas
                yPos = 100f // Начинаем с обычным отступом
                
                // Разделитель
                paint.color = dividerColor
                canvas.drawLine(20f, yPos - 50f, 575f, yPos - 50f, paint)
                paint.color = primaryColor
                canvas.drawLine(20f, yPos - 48f, 575f, yPos - 48f, paint)
            }
        }
        
        // Рисуем верхнюю секцию с заголовком
        // Заголовочная полоса
        paint.color = headerBackgroundColor
        canvas.drawRect(0f, 0f, 595f, 60f, paint)
        
        // Заголовок отчета
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 28f
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        canvas.drawText("ОТЧЕТ О СМЕНЕ №$shiftNumber", 20f, 42f, paint)
        
        // Подзаголовок - информация о смене
        paint.textSize = 12f
        paint.typeface = android.graphics.Typeface.DEFAULT
        val datePart = shift.startTime.substring(0, 10)
        canvas.drawText("Дата: $datePart  |  Сотрудник: ${shift.employeeName}", 20f, 65f, paint)
        canvas.drawText("Начало: ${shift.startTime}  |  Контроль последовательности: ${if (shift.strictSequenceEnabled) "ВКЛ" else "ВЫКЛ"}", 20f, 78f, paint)
        
        // Номер страницы
        val pageText1 = "Страница: $currentPage"
        canvas.drawText(pageText1, 575f - paint.measureText(pageText1) - 10f, 820f, paint)
        
        // Разделитель
        yPos = 100f
        paint.color = dividerColor
        canvas.drawLine(20f, yPos, 575f, yPos, paint)
        paint.color = primaryColor
        canvas.drawLine(20f, yPos + 2f, 575f, yPos + 2f, paint)
        
        // Обходы
        rounds.forEach { round ->
            // Проверяем место для карточки обхода
            checkNewPage(120f)
            
            yPos += 15f
            
            // Карточка обхода с фоном
            paint.color = backgroundColor
            canvas.drawRect(20f, yPos - 5f, 575f, yPos + 60f, paint)
            
            // Заголовок обхода
            paint.color = primaryDarkColor
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            canvas.drawText("ОБХОД №${round.id}", 30f, yPos + 15f, paint)
            
            // Детали обхода
            paint.color = textColor
            paint.typeface = android.graphics.Typeface.DEFAULT
            paint.textSize = 11f
            
            var detailY = yPos + 25f
            canvas.drawText("Маршрут: ${round.routeName ?: "не указан"}", 30f, detailY, paint)
            detailY += 12f
            
            canvas.drawText("Начало: ${round.startTime}", 30f, detailY, paint)
            canvas.drawText("Окончание: ${round.endTime ?: "-"}", 250f, detailY, paint)
            detailY += 12f
            
            canvas.drawText("Чекпоинтов: ${round.checkpointsCount}", 30f, detailY, paint)
            canvas.drawText("Пройдено: ${round.checkpointsPassed}", 250f, detailY, paint)
            detailY += 12f
            
            // Рисуем иконку нарушений (если есть)
            if (round.sequenceViolations > 0) {
                paint.color = errorColor
                canvas.drawText("⚠️ Нарушений: ${round.sequenceViolations}", 30f, detailY, paint)
            } else {
                paint.color = successColor
                canvas.drawText("✓ Нарушений: 0", 30f, detailY, paint)
            }
            
            yPos += 85f
            
            // Проверяем, хватает ли места для логов и карточки
            checkNewPage(200f)
            
            // Логи обхода
            val roundLogs = logs.filter { it.roundId == round.id }
            if (roundLogs.isNotEmpty()) {
                yPos += 5f
                
                // Заголовок логов
                paint.color = primaryColor
                paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                canvas.drawText("Логи обхода:", 30f, yPos, paint)
                yPos += 15f
                
                // Таблица логов
                roundLogs.forEach { log ->
                    // Разделитель строк
                    paint.color = dividerColor
                    canvas.drawLine(30f, yPos - 2f, 575f, yPos - 2f, paint)
                    
                    yPos += 3f
                    
                    // Имя чекпоинта
                    paint.color = textColor
                    paint.typeface = if (log.isSequenceCorrect) android.graphics.Typeface.DEFAULT else android.graphics.Typeface.DEFAULT_BOLD
                    canvas.drawText("${log.checkpointName}", 30f, yPos, paint)
                    
                    // Время
                    val timePart = log.timestamp.split(" ").getOrNull(1) ?: "-"
                    paint.color = secondaryColor
                    canvas.drawText("$timePart", 250f, yPos, paint)
                    
                    // Статус
                    val statusText = when (log.actionType) {
                        "CHECKPOINT" -> "✓ Пройден"
                        "QUESTION" -> "❓ Ответ"
                        "INPUT" -> "✏️ Ввод"
                        "PHOTO" -> "📷 Фото"
                        "SCAN" -> "🔍 Сканирование"
                        else -> "❓ Статус"
                    }
                    
                    if (log.isSequenceCorrect) {
                        paint.color = successColor
                    } else {
                        paint.color = errorColor
                    }
                    paint.textSize = 10f
                    canvas.drawText(statusText, 320f, yPos, paint)
                    
                    yPos += 15f
                    
                    // Если есть фото - вставляем его
                    if (log.actionType == "PHOTO" && fileExists(log.photoPath)) {
                        try {
                            val photoBitmap = BitmapFactory.decodeFile(log.photoPath)
                            if (photoBitmap != null) {
                                // Ограничиваем размер фото
                                val maxPhotoWidth = 500f
                                val maxPhotoHeight = 300f
                                
                                yPos += 5f
                                
                                // Рисуем рамку для фото
                                paint.color = dividerColor
                                canvas.drawRect(30f, yPos - 2f, 530f, yPos + maxPhotoHeight + 2f, paint)
                                
                                // Вставляем изображение
                                yPos = drawImageOnPdf(
                                    canvas,
                                    paint,
                                    photoBitmap,
                                    30f,
                                    yPos,
                                    maxPhotoWidth,
                                    maxPhotoHeight
                                )
                                
                                // Название чекпоинта под фото
                                paint.color = secondaryColor
                                paint.textSize = 10f
                                canvas.drawText("Фото на чекпоинте: ${log.checkpointName}", 30f, yPos - 8f, paint)
                                
                                yPos += 10f
                                
                                photoBitmap.recycle()
                            }
                        } catch (e: Exception) {
                            // Если не удалось загрузить фото - пропускаем
                            e.printStackTrace()
                        }
                    }
                    
                    // Если нарушение последовательности - добавляем строку
                    if (!log.isSequenceCorrect && log.sequenceErrorType != SequenceErrorType.NONE) {
                        val errorTypeName = when (log.sequenceErrorType) {
                            SequenceErrorType.FOREIGN_CHECKPOINT -> "Чужая метка"
                            SequenceErrorType.OUTSIDE_ROUTE -> "Вне маршрута"
                            SequenceErrorType.OUT_OF_SEQUENCE -> "Вне очереди"
                            SequenceErrorType.NONE -> ""
                        }
                        paint.color = errorColor
                        paint.typeface = android.graphics.Typeface.DEFAULT
                        canvas.drawText("${if (log.actionType == "CHECKPOINT") "Нарушение:" else "Ожидался:"} $errorTypeName", 35f, yPos, paint)
                        yPos += 12f
                    }
                }
                
                // Нижний разделитель после логов
                paint.color = dividerColor
                canvas.drawLine(30f, yPos - 2f, 575f, yPos - 2f, paint)
                yPos += 10f
            }
            
            // Разделитель между обходами
            paint.color = dividerColor
            canvas.drawLine(20f, yPos, 575f, yPos, paint)
        }
        
        // Проверяем место для итоговой секции
        checkNewPage(100f)
        
        // Итоговая секция с фоном
        yPos += 15f
        paint.color = backgroundColor
        canvas.drawRect(20f, yPos - 5f, 575f, yPos + 60f, paint)
        
        yPos += 10f
        
        // Заголовок итога
        paint.color = primaryDarkColor
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        canvas.drawText("ИТОГО:", 30f, yPos, paint)
        
        yPos += 20f
        
        // Статистика
        paint.color = textColor
        paint.typeface = android.graphics.Typeface.DEFAULT
        paint.textSize = 12f
        
        canvas.drawText("Всего обходов: ${rounds.size}", 30f, yPos, paint)
        
        yPos += 15f
        canvas.drawText("Всего пройденных чекпоинтов: ${logs.size}", 30f, yPos, paint)
        
        yPos += 15f
        val totalViolations = rounds.sumOf { it.sequenceViolations }
        if (totalViolations > 0) {
            paint.color = errorColor
        } else {
            paint.color = successColor
        }
        canvas.drawText("Всего нарушений последовательности: $totalViolations", 30f, yPos, paint)
        
        // Рисуем иконку статуса
        val completionPercent = if (rounds.isNotEmpty()) {
            val completedRounds = rounds.filter { it.isCompleted }.size
            (completedRounds.toDouble() / rounds.size * 100).toInt()
        } else 0
        
        yPos += 15f
        paint.color = primaryColor
        if (completionPercent == 100) {
            canvas.drawText("✓ Отчет сформирован успешно", 30f, yPos, paint)
        } else {
            canvas.drawText("⚡ Завершенность отчета: $completionPercent%", 30f, yPos, paint)
        }
        
        // Завершаем страницу
        pdfDocument.finishPage(page)
        
        // Сохраняем PDF
        var pdfPath: String? = outputPath
        
        // Если путь не задан, создаем его по умолчанию
        if (pdfPath == null) {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val ohranaDir = File(downloadDir, "Ohrana")
            val pdfDir = File(ohranaDir, "PDF")
            if (!pdfDir.exists()) {
                pdfDir.mkdirs()
            }
            pdfPath = File(pdfDir, fileName).absolutePath
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Для Android 10+ используем MediaStore API
            val contentValues = ContentValues().apply {
                put(android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Files.FileColumns.MIME_TYPE, "application/pdf")
                put(android.provider.MediaStore.Files.FileColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/Ohrana/PDF")
            }
            
            val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                }
                pdfDocument.close()
                
                // Открываем файл только если showPreview=true
                if (showPreview) {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/pdf")
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                        
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                    }
                    // Toast будет показан в вызывающем коде
                }
            } else {
                throw Exception("Не удалось создать URI для файла")
            }
        } else {
            // Для Android 9 и ниже используем Environment.getExternalStoragePublicDirectory
            val outputFile = File(pdfPath)
            
            // Убедимся, что директория существует
            val parentDir = outputFile.parentFile
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs()
            }
            
            // Записываем PDF в файл
            FileOutputStream(outputFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
            pdfDocument.close()
            
            // Открываем файл только если showPreview=true
            if (showPreview) {
                // Открываем файл через FileProvider
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    outputFile
                )
                
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                }
                // Toast будет показан в вызывающем коде
            }
        }
        
        return pdfPath
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}
