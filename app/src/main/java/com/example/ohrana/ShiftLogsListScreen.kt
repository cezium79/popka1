package com.example.ohrana

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.os.Environment
import android.util.Log
import androidx.activity.compose.BackHandler
import com.example.ohrana.CloudStorageManager
import com.example.ohrana.SharedPrefsManager

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
    
    // Загружаем только заголовки завершенных смен (без данных из базы)
    val allShifts by remember { mutableStateOf(shiftDatabase.loadAllShifts()) }
    
    // Фильтруем завершенные смены
    val completedShifts = remember(allShifts, selectedEmployeeName) {
        allShifts.filter { shift ->
            // Если selectedEmployeeName пустой - показываем все смены всех сотрудников
            // Иначе фильтруем по имени сотрудника
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
            if (sortedCompletedShifts.isEmpty()) {
                Text(
                    text = "Нет завершенных смен",
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Прокручиваемый список смен
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            items(sortedCompletedShifts) { shift ->
                                // Выделяем выбранную смену красной рамкой
                                val isSelected = shift.id == selectedShift?.id
                                val borderModifier = if (isSelected) {
                                    Modifier
                                        .shadow(4.dp, RoundedCornerShape(12.dp))
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(2.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
                                } else {
                                    Modifier
                                        .shadow(2.dp, RoundedCornerShape(12.dp))
                                        .clip(RoundedCornerShape(12.dp))
                                }
                                
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(borderModifier),
                                    onClick = { setSelectedShift(shift) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) 
                                            MaterialTheme.colorScheme.errorContainer 
                                        else 
                                            MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(16.dp)
                                            .fillMaxWidth()
                                    ) {
                                        // Номер смены и дата
                                        Text(
                                            text = "СМЕНА №${shift.id.takeLast(3)} от ${shift.startTime.split(" ").firstOrNull() ?: "-"}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary
                                        )
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        // Краткая информация о смене
                                        Text(
                                            text = "Сотрудник: ${shift.employeeName}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Начало: ${shift.startTime}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Окончание: ${shift.endTime}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // Кнопки действий для выбранной смены внизу
                    if (selectedShift != null) {
                        val shiftNumber = selectedShift.id.takeLast(3)
                        val shiftDate = selectedShift.startTime.split(" ").firstOrNull() ?: "-"
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .shadow(4.dp, RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth()
                            ) {
                                // Заголовок выбранной смены
                                Text(
                                    text = "Выбрана смена: СМЕНА №$shiftNumber от $shiftDate",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                // Кнопка просмотра подробной информации
                                Button(
                                    onClick = {
                                        onNavigateToDetails(selectedShift.id)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Подробнее")
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Кнопка просмотра HTML отчета
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            val cloudManager = CloudStorageManager(context)
                                            val (htmlPath, _) = withContext(Dispatchers.IO) {
                                                cloudManager.generateHtmlReport(selectedShift.id, shiftDatabase, context, prefsManager)
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
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("📄 Показать HTML отчет", fontSize = 12.sp)
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Кнопка загрузки HTML в Яндекс.Диск
                                OutlinedButton(
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
                                                    cloudManager.exportHtmlToDisk(selectedShift.id, shiftDatabase, uploadToDisk = true, context, prefsManager)
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
                                                                prefsManager.sendLinkWithAction(selectedAction, downloadUrl)
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
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.secondary
                                    )
                                ) {
                                    Text("📤 Загрузить HTML в Яндекс.Диск", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Обработка системной кнопки "Назад"
    BackHandler(onBack = onBack)
}
