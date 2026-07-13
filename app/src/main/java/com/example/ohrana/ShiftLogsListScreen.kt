package com.example.ohrana

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.ohrana.SharedPrefsManager
import com.example.ohrana.SharedPrefsManager.LinkAction

private const val TAG = "ShiftLogsListScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftLogsListScreen(
    onBack: () -> Unit,
    selectedEmployeeName: String,
    onNavigateToDetails: (shiftId: String) -> Unit,
    onNavigateToCloudSettings: () -> Unit
) {
    val context = LocalContext.current
    val prefsManager = remember { SharedPrefsManager(context) }
    val shiftDatabase = prefsManager.shiftDatabase
    
    // Загружаем все завершенные смены
    val allShifts = remember { shiftDatabase.loadAllShifts() }
    val completedShifts = remember(allShifts) {
        allShifts.filter { shift ->
            // Если selectedEmployeeName пустой - показываем все смены всех сотрудников
            // Иначе фильтруем по имени сотрудника
            (selectedEmployeeName.isEmpty() || shift.employeeName == selectedEmployeeName) &&
            !shift.isShiftActive && shift.endTime != null
        }
    }
    
    // Для каждой завершенной смены загружаем её логи
    val shiftLogsMap = remember(completedShifts) {
        completedShifts.associate { shift ->
            shift to shiftDatabase.loadLogsByShift(shift.id)
        }
    }
    
    // Для каждой завершенной смены загружаем её обходы
    val shiftRoundsMap = remember(completedShifts) {
        completedShifts.associate { shift ->
            shift to shiftDatabase.loadAllRounds().filter { it.shiftId == shift.id }
        }
    }
    
    // Сортируем завершенные смены по убыванию (новые сверху)
    val sortedCompletedShifts = remember(completedShifts) {
        completedShifts.sortedByDescending { it.startTime }
    }
    
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US)
    
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
            if (completedShifts.isEmpty()) {
                Text(
                    text = "Нет завершенных смен",
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(sortedCompletedShifts) { shift ->
                        val logs = shiftLogsMap[shift] ?: emptyList()
                        val rounds = shiftRoundsMap[shift] ?: emptyList()
                        
                        // Формируем дату смены для заголовка
                        val shiftDate = shift.startTime.split(" ").firstOrNull() ?: "-"
                        
                        // Извлекаем номер смены из ID (формат: NSDDMMYY_NNN)
                        val shiftNumber = run {
                            val pattern = Pattern.compile("NS\\d{6}_(\\d{3})")
                            val matcher = pattern.matcher(shift.id)
                            if (matcher.find()) {
                                matcher.group(1)?.toInt() ?: 0
                            } else {
                                0
                            }
                        }
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable { /* Можно добавить детальный просмотр */ }
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth()
                            ) {
                                // Заголовок смены с номером
                                Text(
                                    text = "СМЕНА №$shiftNumber от $shiftDate",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Информация о смене
                                Text(
                                    text = "Время начала: ${shift.startTime}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Время окончания: ${shift.endTime ?: "-"}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Статистика по обходам
                                Text(
                                    text = "Обходов: ${rounds.size}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Пройденных чекпоинтов: ${logs.size}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                // Количество нарушений (исправлено - считаем только нарушения текущего обхода)
                                val violationsCount = rounds.sumOf { round ->
                                    round.sequenceViolations
                                }
                                Text(
                                    text = "Нарушений последовательности: $violationsCount",
                                    fontSize = 12.sp,
                                    color = if (violationsCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                // Кнопка просмотра подробной информации
                                Button(
                                    onClick = {
                                        onNavigateToDetails(shift.id)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Подробнее")
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Область корутины для асинхронных операций
                                val scope = rememberCoroutineScope()
                                
                                // Кнопка загрузки HTML в Яндекс.Диск
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            val cloudManager = CloudStorageManager(context)
                                            val diskToken = cloudManager.getDiskToken()
                                            
                                            if (diskToken == null || diskToken.isEmpty()) {
                                                // Token не найден, показываем диалог с предложением настроить Яндекс.Диск
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(
                                                        context,
                                                        "Настройте Яндекс.Диск в настройках",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                    Log.w(TAG, "Disk token not found for shift ${shift.id}, redirecting to cloud settings")
                                                }
                                            } else {
                                                // Получаем выбранное действие из настроек
                                                val selectedAction = prefsManager.getSmsAction()
                                                
                                                // Прямая загрузка HTML отчета в Яндекс.Диск (без сохранения на телефоне)
                                                val reportResult = withContext(Dispatchers.IO) {
                                                    cloudManager.exportHtmlToDisk(shift.id, shiftDatabase, uploadToDisk = true)
                                                }
                                                
                                                withContext(Dispatchers.Main) {
                                                    if (reportResult.isSuccess()) {
                                                        val downloadUrl = reportResult.getDownloadUrl()
                                                        Log.i(TAG, "HTML report upload result: isSuccess=true, downloadUrl=$downloadUrl")
                                                        
                                                        if (downloadUrl != null) {
                                                            Toast.makeText(
                                                                context,
                                                                "HTML отчет загружен! Ссылка: $downloadUrl",
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                            Log.i(TAG, "HTML report uploaded directly to disk for shift ${shift.id}, download URL: $downloadUrl")
                                                            
                                                            // Автоматически отправляем ссылку по выбранному каналу
                                                            scope.launch {
                                                                prefsManager.sendLinkWithAction(selectedAction, downloadUrl)
                                                            }
                                                        } else {
                                                            Toast.makeText(
                                                                context,
                                                                "HTML отчет загружен в Яндекс.Диск!",
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                            Log.i(TAG, "HTML report uploaded directly to disk for shift ${shift.id}")
                                                            Log.w(TAG, "downloadUrl is null - cannot send link via email/SMS/Telegram")
                                                            
                                                            // Автоматически отправляем ссылку по выбранному каналу (если URL не вернулся)
                                                            // В этом случае можно предложить пользователю ввести URL вручную
                                                        }
                                                    } else {
                                                        Toast.makeText(
                                                            context,
                                                            "Ошибка при загрузке HTML в Диск: ${reportResult.getDiskErrorMessage()}",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                        Log.e(TAG, "Failed to upload HTML report to disk for shift ${shift.id}: ${reportResult.getDiskErrorMessage()}")
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
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                

                                // Кнопка настроек облачных хранилищ
                                OutlinedButton(
                                    onClick = onNavigateToCloudSettings,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Text("⚙️ Настройки облачных хранилищ", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
