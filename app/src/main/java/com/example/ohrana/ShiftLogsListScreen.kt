package com.example.ohrana

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.activity.compose.BackHandler
import com.example.ohrana.CloudStorageManager
import com.example.ohrana.SharedPrefsManager
import com.example.ohrana.BlurredBackground
import com.example.ohrana.ui.components.OhranaOutlinedButton

private const val TAG = "ShiftLogsListScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftLogsListScreen(
    onBack: () -> Unit,
    selectedEmployeeName: String,
    onNavigateToDetails: (shiftId: String) -> Unit,
    onNavigateToCloudSettings: () -> Unit,
    onNavigateToHtmlReport: (htmlFilePath: String) -> Unit
) {
    val context = LocalContext.current
    val prefsManager = remember { SharedPrefsManager(context) }
    val shiftDatabase = prefsManager.shiftDatabase

    // Для превью используем предварительные данные
    val allShifts = remember {
        shiftDatabase.loadAllShifts()
    }

    // Фильтруем завершенные смены
    val completedShifts = remember(allShifts, selectedEmployeeName) {
        allShifts.filter { shift ->
            (selectedEmployeeName.isEmpty() || shift.employeeName == selectedEmployeeName) &&
                    !shift.isShiftActive && shift.endTime != null
        }
    }

    // Сортируем завершенные смены по убыванию (новые сверху)
    val sortedCompletedShifts = remember(completedShifts) {
        completedShifts.sortedByDescending { it.startTime }
    }

    // Выбранная смена (по умолчанию последняя)
    val (selectedShift, setSelectedShift) = remember {
        mutableStateOf(sortedCompletedShifts.firstOrNull() ?: sortedCompletedShifts.lastOrNull())
    }

    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Журналы завершенных смен") },
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
        ) {
            // Размытый фон на весь экран
            BlurredBackground()

            // Контент экрана
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                if (sortedCompletedShifts.isEmpty()) {
                    Text(
                        text = "Нет завершенных смен",
                        fontSize = 16.sp,
                        color = Color(0xFFFFFFFF),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                } else {
                    // Прокручиваемый список смен
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        sortedCompletedShifts.forEach { shift ->
                            val isSelected = shift.id == selectedShift?.id
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF595757)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)
                                        .fillMaxWidth()
                                ) {
                                    // Заголовок смены с временем
                                    val datePart = shift.startTime.split(" ").firstOrNull() ?: "-"
                                    val startTime = shift.startTime.split(" ").lastOrNull() ?: "-"
                                    val endTime = shift.endTime?.split(" ")?.lastOrNull() ?: "-"
                                    
                                    val formattedDate = when {
                                        // Формат yyyy-MM-dd (ISO)
                                        datePart.length >= 10 && datePart.contains("-") -> {
                                            "${datePart.substring(8, 10)}-${datePart.substring(5, 7)}-${datePart.substring(0, 4)}"
                                        }
                                        // Формат dd.MM.yyyy
                                        datePart.length >= 10 && datePart.contains(".") -> {
                                            val parts = datePart.split(".")
                                            if (parts.size >= 3) {
                                                "${parts[0]}-${parts[1]}-${parts[2]}"
                                            } else {
                                                datePart
                                            }
                                        }
                                        else -> datePart
                                    }
                                    
                                    val formattedStartTime = if (startTime.length >= 5) {
                                        startTime.substring(0, 5)
                                    } else {
                                        startTime
                                    }
                                    
                                    val formattedEndTime = if (endTime.length >= 5) {
                                        endTime.substring(0, 5)
                                    } else {
                                        "-"
                                    }
                                    
                                    Text(
                                        text = "СМЕНА №${shift.id.takeLast(3)} от $formattedDate $formattedStartTime - $formattedEndTime",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFFFFF)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "Сотрудник: ${shift.employeeName}",
                                        fontSize = 16.sp,
                                        color = Color(0xFFE0E0E0)
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    OhranaOutlinedButton(
                                        text = "Посмотреть",
                                        onClick = {
                                            setSelectedShift(shift)
                                            selectedShift?.let { onNavigateToDetails(it.id) }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF424242),
                                            contentColor = Color(0xFFFFFFFF)
                                        ),
                                        elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                                            defaultElevation = 4.dp,
                                            pressedElevation = 8.dp,
                                            disabledElevation = 0.dp
                                        ),
                                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Если выбрана смена, показываем дополнительные кнопки - закреплено внизу
                if (selectedShift != null) {
                    val shiftNumber = selectedShift.id.takeLast(3)
                    val shiftDate = selectedShift.startTime.split(" ").firstOrNull() ?: "-"

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF595757)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = "Выбрана смена: СМЕНА №$shiftNumber от $shiftDate",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFFFFF)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            OhranaOutlinedButton(
                                text = "📄 Показать HTML отчет",
                                onClick = {
                                    scope.launch {
                                        val cloudManager = CloudStorageManager(context)
                                        val (htmlPath, _) = withContext(Dispatchers.IO) {
                                            cloudManager.generateHtmlReport(
                                                selectedShift.id,
                                                shiftDatabase,
                                                context,
                                                prefsManager
                                            )
                                        }

                                        withContext(Dispatchers.Main) {
                                            if (htmlPath != null) {
                                                val htmlFile = File(htmlPath)
                                                if (htmlFile.exists()) {
                                                    onNavigateToHtmlReport(htmlPath)
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "Файл отчета не найден",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Ошибка при генерации HTML отчета",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF424242),
                                    contentColor = Color(0xFFFFFFFF)
                                ),
                                elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                                    defaultElevation = 4.dp,
                                    pressedElevation = 8.dp,
                                    disabledElevation = 0.dp
                                ),
                                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OhranaOutlinedButton(
                                text = "📤 Загрузить HTML в Яндекс.Диск",
                                onClick = {
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
                                                    selectedShift.id,
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
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF424242),
                                    contentColor = Color(0xFFFFFFFF)
                                ),
                                elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                                    defaultElevation = 4.dp,
                                    pressedElevation = 8.dp,
                                    disabledElevation = 0.dp
                                ),
                                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)
                            )
                        }
                    }
                }
            }
        }

        // Обработка системной кнопки "Назад"
        BackHandler(onBack = onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview(showBackground = true, name = "ShiftLogsListScreen Preview")
fun ShiftLogsListScreenPreview() {
    ShiftLogsListScreen(
        onBack = {},
        selectedEmployeeName = "Иванов Иван",
        onNavigateToDetails = { },
        onNavigateToCloudSettings = {},
        onNavigateToHtmlReport = { }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview(showBackground = true, name = "Shift Card Preview")
fun ShiftCardPreview() {
    val shift = ShiftRecord(
        id = "shift_001",
        employeeName = "Иванов Иван",
        guardList = listOf(GuardMember(nfcId = "", name = "Иванов Иван", role = "Старший смены")),
        startTime = "2026-07-15 08:00:00",
        endTime = "2026-07-15 16:00:00",
        isShiftActive = false
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF595757)
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            // Заголовок смены
            Text(
                text = "СМЕНА №001 от 15-07-2026 08:00 - 16:00",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFFFFF)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Информация о сотруднике
            Text(
                text = "Сотрудник: Иванов Иван",
                fontSize = 16.sp,
                color = Color(0xFFE0E0E0)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OhranaOutlinedButton(
                text = "Посмотреть",
                onClick = {},
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF424242),
                    contentColor = Color(0xFFFFFFFF)
                ),
                elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 8.dp,
                    disabledElevation = 0.dp
                ),
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)
            )
        }
    }
}
