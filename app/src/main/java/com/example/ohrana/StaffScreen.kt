package com.example.ohrana

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefsManager = remember { SharedPrefsManager(context) }
    val scope = rememberCoroutineScope()

    // Загружаем список работников
    var staffList by remember { mutableStateOf(prefsManager.loadStaff()) }
    
    // Состояние для добавления/редактирования
    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingStaff by remember { mutableStateOf<Staff?>(null) }
    
    // Поля формы
    var tabNumber by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var middleName by remember { mutableStateOf("") }
    var position by remember { mutableStateOf("") }

    // Обновляем список при возвращении на экран
    LaunchedEffect(Unit) {
        staffList = prefsManager.loadStaff()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Работники") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = Color(0xFF030000)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        editingStaff = null
                        tabNumber = ""
                        lastName = ""
                        firstName = ""
                        middleName = ""
                        position = ""
                        showAddEditDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить", tint = Color(
                            0xFF000000
                        )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Фон
                Image(
                    bitmap = android.graphics.BitmapFactory.decodeResource(
                        context.resources,
                        com.example.ohrana.R.drawable.fon2
                    ).asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Заголовок
                    Text(
                        text = "Сотрудники объекта (${staffList.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFFFFF),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    // Список работников
                    if (staffList.isEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF595757))
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Нет добавленных работников",
                                    fontSize = 16.sp,
                                    color = Color(0xFFFFFFFF)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Нажмите + чтобы добавить",
                                    fontSize = 14.sp,
                                    color = Color(0xFFE0E0E0)
                                )
                            }
                        }
                    } else {
                        staffList.forEach { staff ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF595757))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = staff.fullName,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFFFFFFF)
                                            )
                                            Text(
                                                text = "Таб: ${staff.tabNumber}",
                                                fontSize = 14.sp,
                                                color = Color(0xFFE0E0E0)
                                            )
                                            Text(
                                                text = staff.position,
                                                fontSize = 14.sp,
                                                color = Color(0xFFE0E0E0)
                                            )
                                        }
                                        Row {
                                            IconButton(onClick = {
                                                editingStaff = staff
                                                tabNumber = staff.tabNumber
                                                lastName = staff.lastName
                                                firstName = staff.firstName
                                                middleName = staff.middleName
                                                position = staff.position
                                                showAddEditDialog = true
                                            }) {
                                                Icon(Icons.Default.Edit, "Изменить", tint = Color(0xFF64B5F6))
                                            }
                                            IconButton(onClick = {
                                                scope.launch {
                                                    prefsManager.deleteStaffMember(staff.id)
                                                    staffList = prefsManager.loadStaff()
                                                }
                                            }) {
                                                Icon(Icons.Default.Delete, "Удалить", tint = Color(0xFFF44336))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    // Диалог добавления/редактирования
    if (showAddEditDialog) {
        Dialog(onDismissRequest = { showAddEditDialog = false }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = if (editingStaff != null) "Редактировать" else "Добавить",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            OutlinedTextField(
                                value = tabNumber,
                                onValueChange = { tabNumber = it },
                                label = { Text("Табельный номер") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = lastName,
                                onValueChange = { lastName = it },
                                label = { Text("Фамилия") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = firstName,
                                onValueChange = { firstName = it },
                                label = { Text("Имя") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = middleName,
                                onValueChange = { middleName = it },
                                label = { Text("Отчество") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = position,
                                onValueChange = { position = it },
                                label = { Text("Должность") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        TextButton(onClick = { showAddEditDialog = false }) {
                            Text("Отмена")
                        }
                        Button(
                            onClick = {
                                if (lastName.isNotBlank() && firstName.isNotBlank() && position.isNotBlank()) {
                                    if (editingStaff != null) {
                                        // Обновляем существующего
                                        val updated = editingStaff!!.copy(
                                            tabNumber = tabNumber,
                                            lastName = lastName,
                                            firstName = firstName,
                                            middleName = middleName,
                                            position = position
                                        )
                                        scope.launch {
                                            prefsManager.updateStaffMember(updated)
                                            staffList = prefsManager.loadStaff()
                                        }
                                    } else {
                                        // Создаём нового
                                        val newStaff = Staff(
                                            tabNumber = tabNumber,
                                            lastName = lastName,
                                            firstName = firstName,
                                            middleName = middleName,
                                            position = position
                                        )
                                        scope.launch {
                                            prefsManager.addStaffMember(newStaff)
                                            staffList = prefsManager.loadStaff()
                                        }
                                    }
                                    showAddEditDialog = false
                                }
                            },
                            enabled = lastName.isNotBlank() && firstName.isNotBlank() && position.isNotBlank()
                        ) {
                            Text(if (editingStaff != null) "Сохранить" else "Добавить")
                        }
                    }
                }
            }
        }
    }
}
