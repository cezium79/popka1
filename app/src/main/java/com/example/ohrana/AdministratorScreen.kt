package com.example.ohrana

import android.app.Activity
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdministratorScreen(
    onNavigateToEmployeeList: () -> Unit,
    onNavigateToRoutes: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToCloudSettings: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefsManager = remember { SharedPrefsManager(context) }

    var isStrictSequence by remember { mutableStateOf(prefsManager.isStrictSequenceEnabled()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Панель администратора") },
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
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Кнопки навигации
            Button(
                onClick = onNavigateToEmployeeList,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).height(56.dp)
            ) {
                Text("Редактировать список сотрудников", fontSize = 16.sp)
            }
            
            Button(
                onClick = onNavigateToRoutes,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).height(56.dp)
            ) {
                Text("Редактирование маршрутов", fontSize = 16.sp)
            }
            
            Button(
                onClick = onNavigateToLogs,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).height(56.dp)
            ) {
                Text("Журналы", fontSize = 16.sp)
            }
            
            Button(
                onClick = onNavigateToCloudSettings,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).height(56.dp)
            ) {
                Text("Настройки облачных хранилищ", fontSize = 16.sp)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Настройки контроля последовательности
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Строгий контроль",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            text = "Охранник обязан сканировать точки строго по порядку",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isStrictSequence,
                        onCheckedChange = { isChecked ->
                            isStrictSequence = isChecked
                            prefsManager.setStrictSequenceEnabled(isChecked)
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
