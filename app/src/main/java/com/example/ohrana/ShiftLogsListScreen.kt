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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftLogsListScreen(
    onBack: () -> Unit,
    selectedEmployeeName: String,
    onNavigateToDetails: (shiftId: String) -> Unit
) {
    val context = LocalContext.current
    val prefsManager = remember { SharedPrefsManager(context) }
    val shiftDatabase = prefsManager.shiftDatabase
    
    // Загружаем все завершенные смены для текущего охранника
    val allShifts = remember { shiftDatabase.loadAllShifts() }
    val completedShifts = remember(allShifts) {
        allShifts.filter { shift ->
            shift.employeeName == selectedEmployeeName && !shift.isShiftActive && shift.endTime != null
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
                    items(completedShifts) { shift ->
                        val logs = shiftLogsMap[shift] ?: emptyList()
                        val rounds = shiftRoundsMap[shift] ?: emptyList()
                        
                        // Формируем дату смены для заголовка
                        val shiftDate = shift.startTime.split(" ").firstOrNull() ?: "-"
                        
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
                                // Заголовок смены
                                Text(
                                    text = "Смена от $shiftDate",
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
                                
                                // Количество нарушений
                                val violationsCount = rounds.sumOf { round ->
                                    rounds.filter { it.id == round.id }
                                        .sumOf { it.sequenceViolations }
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
                            }
                        }
                    }
                }
            }
        }
    }
}
