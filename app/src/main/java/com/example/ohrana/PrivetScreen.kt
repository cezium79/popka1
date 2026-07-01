package com.example.ohrana // Укажите ваш пакет

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PrivetScreen(
    onNavigateToOhrannik: () -> Unit,
    onNavigateToAdministrator: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Добро пожаловать!", fontSize = 24.sp)

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = onNavigateToOhrannik) {
            Text("Охранник")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onNavigateToAdministrator) {
            Text("Администратор")
        }
    }
}


