package com.example.ohrana

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.ui.res.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteEditorScreen(
    allCheckpoints: List<Checkpoint>,
    onBack: () -> Unit,
    onSave: (Route) -> Unit
) {
    val context = LocalContext.current
    val prefsManager = remember { SharedPrefsManager(context) }
    
    val existingRoutes by remember { 
        mutableStateOf(prefsManager.loadRoutes().toMutableList()) 
    }
    
    var routeName by remember { mutableStateOf("") }
    var selectedCheckpointIds by remember { mutableStateOf(listOf<String>()) }
    var showSaveDialog by remember { mutableStateOf(false) }
    
    var routeToEdit by remember { mutableStateOf<Route?>(null) }
    var routeToDelete by remember { mutableStateOf<Route?>(null) }
    
    fun getCheckpointNameById(id: String): String {
        return allCheckpoints.find { it.id == id }?.name ?: id
    }
    
    fun addCheckpoint(id: String) {
        if (!selectedCheckpointIds.contains(id)) {
            selectedCheckpointIds = selectedCheckpointIds + id
        }
    }
    
    fun removeCheckpoint(id: String) {
        selectedCheckpointIds = selectedCheckpointIds.filter { it != id }
    }
    
    fun moveCheckpointUp(index: Int) {
        if (index > 0) {
            val list = selectedCheckpointIds.toMutableList()
            val item = list.removeAt(index)
            list.add(index - 1, item)
            selectedCheckpointIds = list.toList()
        }
    }
    
    fun moveCheckpointDown(index: Int) {
        if (index < selectedCheckpointIds.size - 1) {
            val list = selectedCheckpointIds.toMutableList()
            val item = list.removeAt(index)
            list.add(index + 1, item)
            selectedCheckpointIds = list.toList()
        }
    }
    
    fun saveRoute() {
        if (routeName.isBlank()) {
            android.widget.Toast.makeText(context, "Введите имя маршрута", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        if (selectedCheckpointIds.isEmpty()) {
            android.widget.Toast.makeText(context, "Добавьте хотя бы одну точку в маршрут", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        val route = Route(
            id = "route_${System.currentTimeMillis()}",
            name = routeName.trim(),
            checkpointIds = selectedCheckpointIds,
            isActive = existingRoutes.isEmpty()
        )
        
        prefsManager.addRoute(route)
        
        // Обновляем список маршрутов
        existingRoutes.clear()
        existingRoutes.addAll(prefsManager.loadRoutes())
        
        onSave(route)
        showSaveDialog = true
    }
    
    fun editRoute(route: Route) {
        routeToEdit = route
        routeName = route.name
        selectedCheckpointIds = route.checkpointIds
    }
    
    fun deleteRoute(route: Route) {
        routeToDelete = route
    }
    
    fun confirmDelete() {
        routeToDelete?.let { prefsManager.removeRoute(it.id) }
        
        // Обновляем список маршрутов
        existingRoutes.clear()
        existingRoutes.addAll(prefsManager.loadRoutes())
        
        routeToDelete = null
    }
    
    fun cancelEdit() {
        routeToEdit = null
        routeName = ""
        selectedCheckpointIds = listOf()
    }
    
    fun confirmEdit() {
        routeToEdit?.let { oldRoute ->
            if (routeName.isBlank()) {
                android.widget.Toast.makeText(context, "Введите имя маршрута", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            
            if (selectedCheckpointIds.isEmpty()) {
                android.widget.Toast.makeText(context, "Добавьте хотя бы одну точку в маршрут", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            
            val updatedRoute = oldRoute.copy(
                name = routeName.trim(),
                checkpointIds = selectedCheckpointIds
            )
            prefsManager.updateRoute(updatedRoute)
            
            // Обновляем список маршрутов
            existingRoutes.clear()
            existingRoutes.addAll(prefsManager.loadRoutes())
            
            onSave(updatedRoute)
            routeToEdit = null
            routeName = ""
            selectedCheckpointIds = listOf()
        }
    }
    
    // Выбираем чекпоинты для добавления
    var showCheckpointSelector by remember { mutableStateOf(false) }
    var checkpointSelectorMode by remember { mutableStateOf("add") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Управление маршрутами") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        routeToEdit = null
                        routeName = ""
                        selectedCheckpointIds = listOf()
                        showCheckpointSelector = true
                        checkpointSelectorMode = "add"
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Новый маршрут")
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            
            // Список имеющихся маршрутов
            Text("Маршруты", fontSize = 16.sp, style = MaterialTheme.typography.titleMedium)
            
            if (existingRoutes.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Маршруты еще не созданы",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Нажмите кнопку + выше, чтобы создать новый маршрут",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                existingRoutes.forEach { route ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = if (route.isActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = route.name,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (route.isActive) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Активный",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Точек: ${route.checkpointIds.size}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = { editRoute(route) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Редактировать",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { deleteRoute(route) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Удалить",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Диалог подтверждения удаления
            if (routeToDelete != null) {
                AlertDialog(
                    onDismissRequest = { routeToDelete = null },
                    title = { Text("Удалить маршрут?") },
                    text = { Text("Вы уверены, что хотите удалить маршрут \"${routeToDelete!!.name}\"?") },
                    confirmButton = {
                        TextButton(onClick = { confirmDelete() }) {
                            Text("Удалить", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { routeToDelete = null }) {
                            Text("Отмена")
                        }
                    }
                )
            }
            
            // Диалог редактирования маршрута
            if (routeToEdit != null) {
                AlertDialog(
                    onDismissRequest = { cancelEdit() },
                    title = { Text("Редактировать маршрут") },
                    text = {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = routeName,
                                onValueChange = { routeName = it },
                                label = { Text("Имя маршрута") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            
                            // Показываем текущие точки маршрута с кнопками перестановки
                            if (selectedCheckpointIds.isNotEmpty()) {
                                Text("Точки маршрута (${selectedCheckpointIds.size}):")
                                
                                selectedCheckpointIds.forEachIndexed { index, id ->
                                    val checkpoint = allCheckpoints.find { it.id == id }
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("${index + 1}. ", fontSize = 14.sp)
                                                Text(getCheckpointNameById(id), fontSize = 14.sp)
                                            }
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                if (index > 0) {
                                                    IconButton(
                                                        onClick = { moveCheckpointUp(index) },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.AutoMirrored.Filled.ArrowBack,
                                                            contentDescription = "Вверх",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                                if (index < selectedCheckpointIds.size - 1) {
                                                    IconButton(
                                                        onClick = { moveCheckpointDown(index) },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.AutoMirrored.Filled.ArrowForward,
                                                            contentDescription = "Вниз",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                                IconButton(
                                                    onClick = { removeCheckpoint(id) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "Удалить",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // Кнопка добавления новых чекпоинтов
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Button(
                                    onClick = { 
                                        checkpointSelectorMode = "edit"
                                        showCheckpointSelector = true 
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Добавить новые чекпоинты")
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { confirmEdit() }) {
                            Text("Сохранить")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { cancelEdit() }) {
                            Text("Отмена")
                        }
                    }
                )
            }
            
            // Диалог выбора чекпоинтов для нового маршрута
            if (showCheckpointSelector) {
                AlertDialog(
                    onDismissRequest = { 
                        showCheckpointSelector = false
                        checkpointSelectorMode = "add"
                    },
                    title = { 
                        Text(
                            if (checkpointSelectorMode == "add") "Выберите точки для маршрута" else "Выберите точки для редактирования"
                        ) 
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .heightIn(max = 400.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Сортируем чекпоинты по ID (по возрастанию)
                            allCheckpoints.sortedBy { it.id }.forEach { checkpoint ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = checkpoint.id,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = checkpoint.name,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "Тип: ${checkpoint.action.name.lowercase()}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (!selectedCheckpointIds.contains(checkpoint.id)) {
                                            IconButton(
                                                onClick = { 
                                                    addCheckpoint(checkpoint.id)
                                                    if (checkpointSelectorMode == "add") {
                                                        routeName = "Маршрут ${selectedCheckpointIds.size + 1}"
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = "Добавить",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        } else {
                                            IconButton(
                                                onClick = { removeCheckpoint(checkpoint.id) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Удалить",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { 
                            showCheckpointSelector = false
                            if (checkpointSelectorMode == "add") {
                                saveRoute()
                            } else {
                                checkpointSelectorMode = "add"
                            }
                        }) {
                            Text("Готово")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { 
                            showCheckpointSelector = false
                            checkpointSelectorMode = "add"
                        }) {
                            Text("Отмена")
                        }
                    }
                )
            }
            
            // Диалог сохранения
            if (showSaveDialog) {
                AlertDialog(
                    onDismissRequest = { },
                    title = { Text("Маршрут сохранен") },
                    text = { Text("Маршрут успешно создан.") },
                    confirmButton = {
                        TextButton(onClick = { 
                            showSaveDialog = false
                            routeName = ""
                            selectedCheckpointIds = listOf()
                        }) {
                            Text("OK")
                        }
                    }
                )
            }
            }
        }
    }
    
    // Обработка системной кнопки "Назад"
    BackHandler(onBack = onBack)
}
