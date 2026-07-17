package com.example.ohrana

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale

@Composable
fun BlurredBackground() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Фоновое изображение
        Image(
            painter = painterResource(id = LocalContext.current.resources.getIdentifier("brick_wall", "drawable", LocalContext.current.packageName)),
            contentDescription = "Фон",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        // Размытая версия фона
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(20.dp)
        ) {
            Image(
                painter = painterResource(id = LocalContext.current.resources.getIdentifier("brick_wall", "drawable", LocalContext.current.packageName)),
                contentDescription = "Фон размытый",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        
        // Белый тонированный слой поверх размытия
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x56FFFFFF)) // Белый с прозрачностью
        )
    }
}
