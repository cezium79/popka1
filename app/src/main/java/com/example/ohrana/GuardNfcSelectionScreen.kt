package com.example.ohrana

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Bundle
import androidx.activity.ComponentActivity
import android.nfc.NfcAdapter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardNfcSelectionScreen(
    onBack: () -> Unit,
    onStartShift: (List<GuardMember>) -> Unit
) {
    val context = LocalContext.current
    val prefsManager = remember { SharedPrefsManager(context) }
    
    // Количество охранников для смены
    val guardsCount = prefsManager.getGuardsCount()
    
    // Выбранные охранники
    var selectedGuards by remember { mutableStateOf<List<GuardMember>>(emptyList()) }
    
    // Состояние NFC-сканирования (запускается автоматически)
    var isScanning by remember { mutableStateOf(true) }
    var scanMessage by remember { mutableStateOf("") }
    
    // Текущий охранник для добавления
    var guardToAdd by remember { mutableStateOf<GuardMember?>(null) }
    
    // NFC-сканирование
    val activity = context as? ComponentActivity
    val nfcManager = context.getSystemService(android.content.Context.NFC_SERVICE) as android.nfc.NfcManager
    val nfcAdapter = nfcManager.defaultAdapter
    
    // NFC-сканирование
    LaunchedEffect(nfcAdapter, activity, isScanning) {
        if (nfcAdapter != null && activity != null && isScanning) {
            try {
                nfcAdapter.enableReaderMode(
                    activity,
                    { tag ->
                        val nfcId = tag.id.joinToString(":") { byte -> String.format("%02X", byte) }
                        
                        activity.runOnUiThread {
                            val employee = prefsManager.loadEmployees().find { it.nfcId == nfcId }
                            
                            if (employee != null) {
                                // Проверяем, не выбран ли уже этот охранник
                                if (selectedGuards.any { it.nfcId == nfcId }) {
                                    scanMessage = "Охранник уже выбран"
                                } else if (selectedGuards.size >= guardsCount) {
                                    scanMessage = "Достигнут лимит охранников"
                                } else {
                                    // Проверяем, является ли это старшим смены
                                    val isShiftLeader = employee.role.contains("старший", ignoreCase = true) || 
                                                        employee.role.contains("lead", ignoreCase = true) ||
                                                        employee.role.contains("Senior", ignoreCase = true)
                                    
                                    // Если это старший смены и он еще не выбран, или это обычный охранник
                                    if (isShiftLeader && selectedGuards.none { it.role.contains("старший", ignoreCase = true) }) {
                                        // Добавляем старшего смены
                                        val guard = GuardMember(
                                            nfcId = nfcId,
                                            name = employee.name,
                                            role = "старший смены"
                                        )
                                        guardToAdd = guard
                                        scanMessage = "Старший смены: ${employee.name}"
                                    } else if (!isShiftLeader) {
                                        // Добавляем обычного охранника
                                        val guard = GuardMember(
                                            nfcId = nfcId,
                                            name = employee.name,
                                            role = "охранник"
                                        )
                                        guardToAdd = guard
                                        scanMessage = "Охранник: ${employee.name}"
                                    } else {
                                        scanMessage = "Старший смены уже выбран"
                                    }
                                }
                            } else {
                                scanMessage = "В штате нет такого сотрудника"
                            }
                        }
                    },
                    NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B,
                    null
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // Останавливаем сканирование если все охранники выбраны
    LaunchedEffect(selectedGuards.size) {
        if (selectedGuards.size >= guardsCount) {
            isScanning = false
        }
    }
    
    // Обработка добавления охранника после NFC-сканирования
    LaunchedEffect(guardToAdd) {
        guardToAdd?.let { guard ->
            selectedGuards = selectedGuards + guard
            guardToAdd = null
            scanMessage = ""
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
                text = "Выберите ${guardsCount} охранников",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            // Сообщение о результате сканирования
            if (scanMessage.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = scanMessage,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Индикатор сканирования и инструкция
            if (isScanning) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "⏳ Сканирование NFC...",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Определяем, кого нужно сканировать
                        val isShiftLeaderSelected = selectedGuards.any { guard -> 
                            guard.role.contains("старший", ignoreCase = true) || 
                            guard.role.contains("lead", ignoreCase = true)
                        }
                        
                        val instructionText = if (!isShiftLeaderSelected) {
                            "Поднесите карту Старшего смены"
                        } else {
                            "Поднесите карту охранника"
                        }
                        
                        Text(
                            text = instructionText,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
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
        }
    }
}
