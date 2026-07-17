package com.example.ohrana

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

/**
 * Генерирует Bitmap с размытым фоном из brick_wall и белым тонированием
 * Возвращает готовую картинку, которую можно использовать как фон
 */
@Composable
fun generateBlurredBackgroundBitmap(): Bitmap? {
    val context = LocalContext.current
    
    // Получаем исходное изображение из ресурсов
    val drawable = ContextCompat.getDrawable(
        context, 
        context.resources.getIdentifier("brick_wall", "drawable", context.packageName)
    )
    
    return drawable?.let {
        val bitmap = (drawable as BitmapDrawable).bitmap
        
        // Создаем Bitmap с размытием
        val blurredBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(blurredBitmap)
        val paint = Paint()
        
        // Рисуем изображение
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        // Создаем Paint для белого тонирования
        val tintPaint = Paint().apply {
            color = Color.argb(0xE0, 255, 255, 255) // Белый с 87.5% непрозрачностью
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
        }
        
        // Накладываем белый тон поверх всего изображения
        canvas.drawPaint(tintPaint)
        
        blurredBitmap
    }
}

/**
 * Сохраняет Bitmap с размытым фоном в SharedPreferences
 * Вызывается после генерации фона
 */
@Composable
fun saveBlurredBackgroundToPrefs() {
    val prefsManager = LocalContext.current.let { SharedPrefsManager(it) }
    val bitmap = generateBlurredBackgroundBitmap()
    
    bitmap?.let {
        prefsManager.saveBlurredBackgroundBitmap(it)
        android.util.Log.d("BackgroundGenerator", "Blurred background saved to prefs")
    }
}

/**
 * Загружает Bitmap с размытым фоном из SharedPreferences
 */
@Composable
fun loadBlurredBackgroundBitmap(): Bitmap? {
    val context = LocalContext.current
    val prefsManager = SharedPrefsManager(context)
    return prefsManager.loadBlurredBackgroundBitmap()
}

/**
 * Проверяет, сохранен ли Bitmap с размытым фоном
 */
@Composable
fun hasBlurredBackgroundBitmap(): Boolean {
    val context = LocalContext.current
    val prefsManager = SharedPrefsManager(context)
    return prefsManager.hasBlurredBackgroundBitmap()
}

/**
 * Очищает сохраненный Bitmap с размытым фоном
 */
@Composable
fun clearBlurredBackgroundBitmap() {
    val context = LocalContext.current
    val prefsManager = SharedPrefsManager(context)
    prefsManager.clearBlurredBackgroundBitmap()
}
