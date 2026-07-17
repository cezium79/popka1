package com.example.ohrana

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.tooling.preview.Preview
import androidx.activity.compose.BackHandler
import com.example.ohrana.ui.components.OhranaOutlinedButton
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarshrutiScreen(
    onBack: () -> Unit,
    onNavigateToCheckpointEditor: (String?) -> Unit,
    onNavigateToRouteEditor: () -> Unit,
    onNavigateToSchedule: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefsManager = remember { SharedPrefsManager(context) }

    // 3. Обязательные точки обхода (QR)
    var newPointInput by remember { mutableStateOf("") }
    val checkpointList = remember { 
        val savedPoints = sharedPrefsManager.loadRouteCheckpoints()
        androidx.compose.runtime.mutableStateListOf<String>().apply {
            if (savedPoints.isNotEmpty()) {
                addAll(savedPoints)
            } else {
                addAll(listOf("Точка_Вход", "Точка_Склад_1", "Точка_Забор"))
            }
        }
    }

    // Загружаем список маршрутов
    val routeList = remember { 
        val savedRoutes = sharedPrefsManager.loadRoutes()
        androidx.compose.runtime.mutableStateListOf<Route>().apply {
            if (savedRoutes.isNotEmpty()) {
                addAll(savedRoutes)
            }
        }
    }

    // 4. Список чекпоинтов - загружаем и обновляем при изменении
    val allCheckpoints = remember { 
        androidx.compose.runtime.mutableStateListOf<Checkpoint>() 
    }
    
    // Синхронизируем список чекпоинтов при изменении
    LaunchedEffect(sharedPrefsManager.loadCheckpoints()) {
        allCheckpoints.clear()
        allCheckpoints.addAll(sharedPrefsManager.loadCheckpoints())
    }

    // Сохраняем точки обхода при изменении
    LaunchedEffect(checkpointList) {
        // Сохраняем только список чекпоинтов
        // Остальные настройки (roundsCount, times, tolerance) сохраняются в ScheduleScreen
        // и не должны перезаписываться здесь
        sharedPrefsManager.saveRouteCheckpoints(checkpointList.toList())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройка маршрутов и стен") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF616161) // Серый фон как у экрана
                )
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
                    .padding(4.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

            // Кнопка для перехода к расписанию обходов
            OhranaOutlinedButton(
                text = "Расписание обходов",
                onClick = onNavigateToSchedule,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF424242),
                    contentColor = Color(0xFFFFFFFF)
                ),
                elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 16.dp,
                    disabledElevation = 8.dp
                ),
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp)
            )

           
            
            // --- КНОПКА РЕДАКТИРОВАНИЯ МАРШРУТОВ ---
            OhranaOutlinedButton(
                text = "Редактирование маршрутов",
                onClick = onNavigateToRouteEditor,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF424242),
                    contentColor = Color(0xFFFFFFFF)
                ),
                elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 16.dp,
                    disabledElevation = 8.dp
                ),
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp)
            )
            

            
            // Кнопка создания чекпоинта
            OhranaOutlinedButton(
                text = "Создать чекпоинт",
                onClick = { onNavigateToCheckpointEditor(null) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF424242),
                    contentColor = Color(0xFFFFFFFF)
                ),
                elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 16.dp,
                    disabledElevation = 8.dp
                ),
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            if (allCheckpoints.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF595757))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Чекпоинты еще не созданы",
                            fontSize = 14.sp,
                            color = Color(0xFF050505)
                        )
                        Text(
                            "Нажмите кнопку выше, чтобы создать новый чекпоинт",
                            fontSize = 12.sp,
                            color = Color(0xFF050505),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                // Отображаем список чекпоинтов
                val sortedCheckpoints = allCheckpoints.sortedBy { it.id }
                sortedCheckpoints.forEach { checkpoint ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF595757))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = checkpoint.id,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFFFFF),
                                        modifier = Modifier.padding(end = 12.dp)
                                    )
                                    Text(
                                        text = checkpoint.name,
                                        fontSize = 12.sp,
                                        color = Color(0xFFE0E0E0),
                                        modifier = Modifier.padding(end = 12.dp)
                                    )
                                    Text(
                                        text = "Тип: ${checkpoint.action.name.lowercase()}",
                                        fontSize = 10.sp,
                                        color = Color(0xFFB0B0B0)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OhranaOutlinedButton(
                                    text = "Редактировать",
                                    onClick = { onNavigateToCheckpointEditor(checkpoint.id) },
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF424242),
                                        contentColor = Color(0xFFFFFFFF)
                                    ),
                                    elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                                        defaultElevation = 4.dp,
                                        pressedElevation = 8.dp,
                                        disabledElevation = 0.dp
                                    ),
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                                )
                                OhranaOutlinedButton(
                                    text = "Удалить",
                                    onClick = {
                                        sharedPrefsManager.removeCheckpoint(checkpoint.id)
                                        // Перезагружаем список чекпоинтов
                                        allCheckpoints.clear()
                                        allCheckpoints.addAll(sharedPrefsManager.loadCheckpoints())
                                    },
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFB00000),
                                        contentColor = Color(0xFFFFFFFF)
                                    ),
                                    elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                                        defaultElevation = 4.dp,
                                        pressedElevation = 8.dp,
                                        disabledElevation = 0.dp
                                    ),
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            }
        }
    }
    
    // Обработка системной кнопки "Назад"
    BackHandler(onBack = {
        sharedPrefsManager.saveRouteAlarms(sharedPrefsManager.loadRouteAlarms())
        onBack()
    })
}

@Composable
@Preview(showBackground = true, name = "Marshruti Screen Preview")
fun MarshrutiScreenPreview() {
    MarshrutiScreen(onBack = {}, onNavigateToCheckpointEditor = {}, onNavigateToRouteEditor = {}, onNavigateToSchedule = {})
}

@Composable
@Preview(showBackground = true, name = "Checkpoint Card Preview")
fun CheckpointCardPreview() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF595757))
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CHECKPOINT_ID",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFFFFF),
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Text(
                        text = "Название точки",
                        fontSize = 12.sp,
                        color = Color(0xFFE0E0E0),
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Text(
                        text = "scan",
                        fontSize = 12.sp,
                        color = Color(0xFFB0B0B0)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OhranaOutlinedButton(
                    text = "Редактировать",
                    onClick = {},
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF424242),
                        contentColor = Color(0xFFFFFFFF)
                    ),
                    elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 8.dp,
                        disabledElevation = 0.dp
                    ),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                )
                OhranaOutlinedButton(
                    text = "Удалить",
                    onClick = {},
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB00000),
                        contentColor = Color(0xFFFFFFFF)
                    ),
                    elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 8.dp,
                        disabledElevation = 0.dp
                    ),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                )
            }
        }
    }
}
