package com.example.ohrana

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.compose.BackHandler
import androidx.compose.ui.res.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardSelectionScreen(
    onBack: () -> Unit,
    onStartShift: (List<GuardMember>) -> Unit
) {
    val context = LocalContext.current
    val prefsManager = remember { SharedPrefsManager(context) }
    
    // Загружаем список сотрудников
    val allEmployees = remember { prefsManager.loadEmployees() }
    
    // Количество охранников для смены
    val guardsCount = prefsManager.getGuardsCount()
    
    // Выбранные охранники
    var selectedGuards by remember { mutableStateOf<List<GuardMember>>(emptyList()) }
    
    // Показываем диалог выбора охранников
    var showGuardSelector by remember { mutableStateOf(false) }
    
    // Текущий охранник для выбора (для добавления в список)
    var selectedGuardForAdd by remember { mutableStateOf<GuardMember?>(null) }
    
    // Проверяем, выбран ли уже старший смены
    val hasShiftLeader by remember { 
        derivedStateOf { 
            selectedGuards.any { guard -> 
                guard.role.contains("старший", ignoreCase = true) || 
                guard.role.contains("lead", ignoreCase = true) ||
                guard.role.contains("Senior", ignoreCase = true)
            } 
        } 
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Выбор охранников") },
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            // Информация о количестве охранников
            Text(
                text = "Количество охранников: $guardsCount",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Выберите охранников для смены",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // Отображение выбранных охранников
            if (selectedGuards.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Выбранные охранники:",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        selectedGuards.forEachIndexed { index, guard ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = guard.name,
                                        fontSize = 14.sp,
                                        fontWeight = if (guard.role.contains("старший", ignoreCase = true)) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        text = guard.role,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                IconButton(
                                    onClick = {
                                        selectedGuards = selectedGuards.filterIndexed { i, _ -> i != index }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Удалить",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Кнопка добавления охранника
            Button(
                onClick = { showGuardSelector = true },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Добавить охранника", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Кнопка начала смены
            Button(
                onClick = {
                    if (selectedGuards.size == guardsCount) {
                        onStartShift(selectedGuards)
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            "Выберите ${guardsCount} охранников",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                enabled = selectedGuards.size == guardsCount,
                modifier = Modifier.fillMaxWidth().height(60.dp)
            ) {
                Text("НАЧАТЬ СМЕНУ", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            
            // Информация о необходимости выбрать старшего
            if (hasShiftLeader.not() && guardsCount > 1) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Важно!",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "При групповой работе на смене обязательно должен быть назначений старший смены. Укажите роль 'старший смены' для одного из охранников.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            }
        }
    }
    
    // Диалог выбора охранника
    if (showGuardSelector) {
        GuardSelectorDialog(
            onDismiss = { showGuardSelector = false },
            onGuardSelected = { guard ->
                selectedGuardForAdd = guard
                showGuardSelector = false
            },
            allEmployees = allEmployees,
            selectedGuards = selectedGuards,
            guardsCount = guardsCount
        )
    }
    
    // Если охранник выбран, добавляем его в список
    selectedGuardForAdd?.let { guard ->
        // Проверяем, можно ли добавить этого охранника
        val canAdd = selectedGuards.size < guardsCount && 
                    selectedGuards.none { it.nfcId == guard.nfcId }
        
        if (canAdd) {
            selectedGuards = selectedGuards + guard
            selectedGuardForAdd = null
        } else {
            android.widget.Toast.makeText(
                context,
                "Охранник уже выбран или достигнут лимит",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    // Обработка системной кнопки "Назад"
    BackHandler(onBack = onBack)
}

@Composable
fun GuardSelectorDialog(
    onDismiss: () -> Unit,
    onGuardSelected: (GuardMember) -> Unit,
    allEmployees: List<Employee>,
    selectedGuards: List<GuardMember>,
    guardsCount: Int
) {
    val availableEmployees = allEmployees.filter { employee ->
        // Фильтруем уже выбранных охранников
        selectedGuards.none { it.nfcId == employee.nfcId }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите охранника") },
        text = {
            if (availableEmployees.isEmpty()) {
                Text(
                    text = "Нет доступных охранников. Добавьте новых в разделе «Редактировать список сотрудников».",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                    availableEmployees.forEach { employee ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            onClick = {
                                onGuardSelected(
                                    GuardMember(
                                        nfcId = employee.nfcId ?: "",
                                        name = employee.name,
                                        role = employee.role
                                    )
                                )
                            }
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = employee.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = employee.role,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
