package com.example.ohrana

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlin.time.Duration.Companion.seconds
import androidx.compose.ui.tooling.preview.Preview
import com.example.ohrana.ui.theme.OhranaTheme

@Composable
fun GoodbyeScreen(
    onCloseShift: () -> Unit
) {
    val context = LocalContext.current
    val goodbyeBitmap = android.graphics.BitmapFactory.decodeResource(
        context.resources,
        com.example.ohrana.R.drawable.goodbye
    )
    val fon3Bitmap = android.graphics.BitmapFactory.decodeResource(
        context.resources,
        com.example.ohrana.R.drawable.fon3
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Image(
            bitmap = fon3Bitmap.asImageBitmap(),
            contentDescription = "Фон",
            modifier = Modifier
                .matchParentSize()
                .background(Color.White),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Верхний отступ, который сдвигает картинку в верхнюю треть
            Spacer(modifier = Modifier.weight(1f))

            // 2. Картинка (центрируется в своей области)
            Image(
                bitmap = goodbyeBitmap.asImageBitmap(),
                contentDescription = "Goodbye",
                modifier = Modifier.size(350.dp)
            )

            // 3. Пространство между картинкой и текстом (баланс нижних двух третей)
            Spacer(modifier = Modifier.weight(1f))

            // 4. Блок с текстом
            Text(
                text = "Смена завершена.",
                fontSize = 30.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFF6EDED)
            )
           // 5. Нижний отступ, чтобы текст не прижимался к краю экрана
            Spacer(modifier = Modifier.weight(1f))
        }


    }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(3.seconds)
        onCloseShift()
    }
}

@Composable
@Preview(showBackground = true)
fun GoodbyeScreenPreview() {
    OhranaTheme {
        GoodbyeScreen(onCloseShift = {})
    }
}
