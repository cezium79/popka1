package com.example.ohrana

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.Date
import java.util.Locale
import java.lang.StringBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.ohrana.ui.components.OhranaOutlinedButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    onBack: () -> Unit,
    onNavigateToHtmlReport: (htmlFilePath: String) -> Unit,
    onSelectDate: (dateStr: String) -> Unit = {}
) {
    val context = LocalContext.current
    val prefsManager = remember { SharedPrefsManager(context) }
    val shiftDatabase = prefsManager.shiftDatabase
    val scope = rememberCoroutineScope()

    // Текущая дата и выбранная дата
    val currentDate = remember { Calendar.getInstance() }
    var selectedDate by remember { mutableStateOf(currentDate) }

    // Состояние для всплывающего меню
    var showMenu by remember { mutableStateOf(false) }
    var selectedShiftForMenu by remember { mutableStateOf<ShiftRecord?>(null) }

    // Обработка системной кнопки Назад
    BackHandler {
        showMenu = false
        onBack()
    }

    // Таймер для автоматического скрытия всплывающего меню через 3 секунды
    LaunchedEffect(showMenu) {
        if (showMenu) {
            delay(3000)
            showMenu = false
        }
    }

    // Формат даты для хранения и сравнения
    val dateFormat = SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
    val storageFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault())

    // Загрузка смен за выбранную дату
    val allShifts = try {
        shiftDatabase.loadAllShifts()
    } catch (e: Exception) {
        emptyList()
    }
    val selectedDateStr = dateFormat.format(selectedDate.time)
    val completedShifts = allShifts.filter { shift ->
        try {
            // Проверяем, что startTime не пустой
            if (shift.startTime.isNullOrBlank()) {
                return@filter false
            }
            val shiftDate = storageFormat.parse(shift.startTime)?.let { 
                dateFormat.format(it.time) 
            }
            shiftDate == selectedDateStr && !shift.isShiftActive && shift.endTime != null && shift.endTime?.isNotBlank() == true
        } catch (e: Exception) {
            false
        }
    }

    // Функции переключения месяцев
    val previousMonth = {
        val calendar = selectedDate.clone() as Calendar
        calendar.add(Calendar.MONTH, -1)
        selectedDate = calendar
    }

    val nextMonth = {
        val calendar = selectedDate.clone() as Calendar
        calendar.add(Calendar.MONTH, 1)
        selectedDate = calendar
    }

    // Функции быстрого выбора даты
    val selectToday = {
        selectedDate = Calendar.getInstance()
    }

    val selectYesterday = {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, -1)
        selectedDate = calendar
    }

    val selectDayBeforeYesterday = {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, -2)
        selectedDate = calendar
    }

    val selectTwoDaysAgo = {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, -3)
        selectedDate = calendar
    }

    // Получение дней в месяце (как список недель)
    fun getWeeksInMonth(year: Int, month: Int): List<List<String?>> {
        val calendar = Calendar.getInstance().apply {
            set(year, month - 1, 1)
        }
        
        val lastDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val weeks = mutableListOf<List<String?>>()
        
        // Добавляем пустые ячейки до первого дня месяца
        val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val offset = (firstDayOfWeek + 5) % 7 // Convert to Monday-based index
        
        val days = mutableListOf<String?>()
        
        repeat(offset) {
            days.add(null)
        }
        
        // Добавляем дни месяца с форматированием dd.MM.yyyy
        for (day in 1..lastDay) {
            val dayStr = if (day < 10) "0$day" else "$day"
            days.add("$dayStr.$month.$year")
        }
        
        // Разбиваем на недели по 7 дней
        var currentWeek = mutableListOf<String?>()
        days.forEach { day ->
            currentWeek.add(day)
            if (currentWeek.size == 7) {
                weeks.add(currentWeek.toList())
                currentWeek = mutableListOf()
            }
        }
        
        // Добавляем последнюю неделю, если она не пустая
        if (currentWeek.isNotEmpty()) {
            while (currentWeek.size < 7) {
                currentWeek.add(null)
            }
            weeks.add(currentWeek.toList())
        }
        
        return weeks
    }

    // Вычисляем текущий год и месяц
    val currentYear = selectedDate.get(Calendar.YEAR)
    val currentMonth = selectedDate.get(Calendar.MONTH) + 1 // Calendar.MONTH is 0-indexed

    // Получаем недели месяца
    val weeksInMonth = getWeeksInMonth(currentYear, currentMonth)

    // Названия дней недели
    val weekDays = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

    // Форматирование даты для отображения
    val monthName = remember(currentMonth) {
        val months = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", 
                           "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")
        months[currentMonth - 1]
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Журналы") },
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
        Box(
            modifier = Modifier.fillMaxSize()
                .padding(paddingValues)
        ) {
            // Размытый фон
            BlurredBackground()

            // Контент экрана
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    // Календарь
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF595757)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            // Заголовок с кнопками переключения месяцев
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = previousMonth) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Предыдущий месяц",
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Text(
                                    text = "$monthName $currentYear",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                IconButton(onClick = nextMonth) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Следующий месяц",
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Названия дней недели
                            Row(modifier = Modifier.fillMaxWidth()) {
                                weekDays.forEachIndexed { index, day ->
                                    Text(
                                        text = day,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF9E9E9E),
                                        modifier = Modifier.weight(1f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Сетка дней календаря - используем LazyColumn с неделями
                            weeksInMonth.forEachIndexed { weekIndex, week ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    week.forEach { day ->
                                        if (day != null) {
                                            val isSelected = day == selectedDateStr
                                            val todayCalendar = Calendar.getInstance()
                                            val todayStr = dateFormat.format(todayCalendar.time)
                                            val isToday = day == todayStr
                                            val dayParts = day.split(".")
                                            val dayOfMonth = dayParts[0].toInt()
                                            val month = dayParts[1].toInt()
                                            val year = dayParts[2].toInt()
                                            
                                            // Определяем день недели для текущего дня в календаре
                                            val dayOfWeekCalendar = Calendar.getInstance().apply {
                                                set(year, month - 1, dayOfMonth)
                                            }
                                            val weekDayIndex = dayOfWeekCalendar.get(Calendar.DAY_OF_WEEK)
                                            // Calendar.DAY_OF_WEEK: Воскресенье=1, Понедельник=2, ..., Суббота=7
                                            // Нам нужно: Понедельник=0, ..., Суббота=5, Воскресенье=6
                                            val adjustedIndex = if (weekDayIndex == Calendar.SUNDAY) 6 else weekDayIndex - 2
                                            val isWeekend = adjustedIndex == 5 || adjustedIndex == 6 // Сб = 5, Вс = 6
                                            
                                            // Обработчик нажатия на день
                                            val onClickDay = {
                                                val selectedCalendar = Calendar.getInstance().apply {
                                                    set(year, month - 1, dayOfMonth)
                                                }
                                                val clickedDateStr = dateFormat.format(selectedCalendar.time)
                                                selectedDate = selectedCalendar
                                                onSelectDate(clickedDateStr)
                                            }
                                            
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .size(40.dp)
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (isSelected) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                                                        shape = RoundedCornerShape(50)
                                                    )
                                                    .clickable { onClickDay() }
                                                    .padding(4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = dayOfMonth.toString(),
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isWeekend) Color(0xFFFF6B6B) else Color(0xFF9E9E9E),
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                )
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .size(40.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Заголовок кнопок быстрого выбора
                            Text(
                                text = "Быстрый выбор:",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Кнопки быстрого выбора
                            Column {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        onClick = selectToday,
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                                    ) {
                                        Text(text = "Сегодня", color = Color.White)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = selectYesterday,
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                                    ) {
                                        Text(text = "Вчера", color = Color.White)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        onClick = selectDayBeforeYesterday,
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                                    ) {
                                        Text(text = "Третьего дня", color = Color.White)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = selectTwoDaysAgo,
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
                                    ) {
                                        Text(text = "Давича", color = Color.White)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Заголовок списка журналов
                            Text(
                                text = "Журналы за $selectedDateStr:",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                // Список журналов
                if (completedShifts.isEmpty()) {
                    item {
                        Text(
                            text = "Нет завершенных смен за выбранную дату",
                            fontSize = 14.sp,
                            color = Color(0xFFBDBDBD),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    items(completedShifts) { shift ->
                        ShiftMiniCard(
                            shift = shift,
                            onMenuClick = { shift ->
                                selectedShiftForMenu = shift
                                showMenu = true
                            }
                        )
                    }
                }
            }

            // Поиск HTML отчета для выбранной смены
            val htmlReportFile = findHtmlReportFile(selectedShiftForMenu?.id, context)

            // Всплывающее меню
            if (showMenu && selectedShiftForMenu != null) {
                Card(
                    modifier = Modifier
                        .offset(x = 100.dp, y = 100.dp)
                        .clickable { showMenu = false },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF333333)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Посмотреть", color = Color.White) },
                            onClick = {
                                showMenu = false
                                if (htmlReportFile != null && htmlReportFile.exists()) {
                                    openHtmlReport(htmlReportFile, context)
                                } else {
                                    Toast.makeText(
                                        context,
                                        "HTML отчет не найден",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Загрузить HTML в Яндекс.Диск", color = Color.White) },
                            onClick = {
                                showMenu = false
                                scope.launch {
                                    val cloudManager = CloudStorageManager(context)
                                    val diskToken = cloudManager.getDiskToken()

                                    if (diskToken == null || diskToken.isEmpty()) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                context,
                                                "Настройте Яндекс.Диск в настройках",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    } else {
                                        val selectedAction = prefsManager.getSmsAction()
                                        val reportResult = withContext(Dispatchers.IO) {
                                            cloudManager.exportHtmlToDisk(
                                                selectedShiftForMenu!!.id,
                                                shiftDatabase,
                                                uploadToDisk = true,
                                                context,
                                                prefsManager
                                            )
                                        }

                                        withContext(Dispatchers.Main) {
                                            if (reportResult.isSuccess()) {
                                                val downloadUrl = reportResult.getDownloadUrl()
                                                if (downloadUrl != null) {
                                                    Toast.makeText(
                                                        context,
                                                        "HTML отчет загружен! Ссылка: $downloadUrl",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                    scope.launch {
                                                        prefsManager.sendLinkWithAction(
                                                            selectedAction,
                                                            downloadUrl
                                                        )
                                                    }
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "HTML отчет загружен в Яндекс.Диск!",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Ошибка при загрузке HTML в Диск: ${reportResult.getDiskErrorMessage()}",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ShiftMiniCard(shift: ShiftRecord, onMenuClick: (ShiftRecord) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF595757)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Название смены
                val datePart = shift.startTime.split(" ").firstOrNull() ?: "-"
                val startTime = shift.startTime.split(" ").lastOrNull() ?: "-"
                val endTime = shift.endTime?.split(" ")?.lastOrNull() ?: "-"
                
                val formattedDate = when {
                    datePart.length >= 10 && datePart.contains("-") -> {
                        " ${datePart.substring(8, 10)}.${datePart.substring(5, 7)}.${datePart.substring(0, 4)}"
                    }
                    datePart.length >= 10 && datePart.contains(".") -> {
                        datePart
                    }
                    else -> datePart
                }
                
                val formattedStartTime = if (startTime.length >= 5) {
                    startTime.substring(0, 5)
                } else {
                    startTime
                }
                
                val formattedEndTime = if (endTime?.length ?: 0 >= 5) {
                    endTime.substring(0, 5)
                } else {
                    "-"
                }

                Text(
                    text = "СМЕНА №${shift.id.takeLast(3)} от $formattedDate $formattedStartTime - $formattedEndTime",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Сотрудник: ${shift.employeeName}",
                    fontSize = 12.sp,
                    color = Color(0xFFE0E0E0)
                )
            }

            // Кнопка с меню
            OhranaOutlinedButton(
                text = "⋮",
                onClick = { onMenuClick(shift) },
                modifier = Modifier.height(48.dp).width(48.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF424242),
                    contentColor = Color(0xFFFFFFFF)
                )
            )
        }
    }
}

/**
 * Находит HTML отчет для выбранной смены в папке Reports
 */
fun findHtmlReportFile(shiftId: String?, context: android.content.Context): File? {
    if (shiftId == null) return null
    
    val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(
        android.os.Environment.DIRECTORY_DOWNLOADS
    )
    val ohranaDir = File(downloadDir, "Ohrana")
    val reportsDir = File(ohranaDir, "Reports")
    
    if (!reportsDir.exists()) return null
    
    // Ищем файл с именем shift_report_{shiftId}_yyyy-MM-ddTHH:mm:ss.SSSZ.html
    // Например: shift_report_NS220726_001_2024-07-22T14:30:45.123Z.html
    return reportsDir.listFiles()
        ?.filter { it.name.startsWith("shift_report_${shiftId}_") && it.name.endsWith(".html") }
        ?.maxByOrNull { it.name }
}

/**
 * Открывает HTML отчет в стороннем приложении
 */
fun openHtmlReport(file: File, context: android.content.Context) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/html")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        val resolveInfo = context.packageManager.resolveActivity(intent, 0)
        if (resolveInfo != null) {
            context.startActivity(intent)
        } else {
            Toast.makeText(
                context,
                "Нет приложения для открытия HTML",
                Toast.LENGTH_SHORT
            ).show()
        }
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "Ошибка при открытии отчета: ${e.message}",
            Toast.LENGTH_LONG
        ).show()
    }
}

// Превью отключено - не работает в IDE без реального Android-контекста
// из-за инициализации ArchiveManager и SharedPrefsManager
// @OptIn(ExperimentalMaterial3Api::class)
// @Composable
// @Preview(showBackground = true, name = "JournalScreen Preview")
// fun JournalScreenPreview() {
//     androidx.compose.material3.MaterialTheme {
//         JournalScreen(
//             onBack = {},
//             onNavigateToHtmlReport = {}
//         )
//     }
// }

// Превью отключено - не работает в IDE без реального Android-контекста
// @OptIn(ExperimentalMaterial3Api::class)
// @Composable
// @Preview(showBackground = true, name = "JournalScreen Preview (No Data)")
// fun JournalScreenPreviewNoData() {
//     androidx.compose.material3.MaterialTheme {
//         JournalScreen(
//             onBack = {},
//             onNavigateToHtmlReport = {}
//         )
//     }
// }
