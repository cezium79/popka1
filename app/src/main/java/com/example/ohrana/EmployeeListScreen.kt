package com.example.ohrana

import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import android.annotation.SuppressLint
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp




@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter") // ДЛЯ MATERIAL 3
@Composable
fun EmployeeListScreen(
    employees: List<Employee>,
    onAddEmployee: (String, String) -> Unit,
    onDeleteEmployee: (Employee) -> Unit,
    onEditEmployee: (Employee, String, String) -> Unit,
    onBack: () -> Unit
) {
    var nameInput by remember { mutableStateOf("") }
    var roleInput by remember { mutableStateOf("") }
    var editingEmployee by remember { mutableStateOf<Employee?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editingEmployee == null) "Список сотрудников" else "Редактирование") },
                actions = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // <- 1. ПРИМЕНЯЕМ ОТСТУПЫ ОТ SCAFFOLD В ПЕРВУЮ ОЧЕРЕДЬ
                .padding(16.dp)         // 2. Ваши собственные дополнительные отступы
        ) {
            // Форма ввода / редактирования
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text("ФИО Сотрудника") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = roleInput,
                onValueChange = { roleInput = it },
                label = { Text("Должность") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (nameInput.isNotBlank() && roleInput.isNotBlank()) {
                        val currentEditing = editingEmployee
                        if (currentEditing != null) {
                            onEditEmployee(currentEditing, nameInput, roleInput)
                            editingEmployee = null
                        } else {
                            onAddEmployee(nameInput, roleInput)
                        }
                        nameInput = ""
                        roleInput = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (editingEmployee == null) "Добавить сотрудника" else "Сохранить изменения")
            }

            if (editingEmployee != null) {
                TextButton(
                    onClick = {
                        editingEmployee = null
                        nameInput = ""
                        roleInput = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Отмена")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Список сотрудников
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(employees) { employee ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = employee.name, style = MaterialTheme.typography.titleMedium)
                                Text(text = employee.role, style = MaterialTheme.typography.bodyMedium)
                            }
                            Row {
                                IconButton(onClick = {
                                    editingEmployee = employee
                                    nameInput = employee.name
                                    roleInput = employee.role
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Редактировать", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { onDeleteEmployee(employee) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


