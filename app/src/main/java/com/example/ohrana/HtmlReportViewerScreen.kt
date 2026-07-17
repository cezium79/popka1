package com.example.ohrana

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.ui.res.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HtmlReportViewerScreen(
    onBack: () -> Unit,
    htmlFilePath: String
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Устанавливаем горизонтальную ориентацию при открытии
    LaunchedEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    // Сбрасываем ориентацию при разрушении экрана
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HTML Отчет") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                val file = File(htmlFilePath)

                if (!file.exists()) {
                    Text(
                        text = "Файл отчета не найден",
                        modifier = Modifier.align(Alignment.Center)
                    )
                    return@Box
                }

                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true

                            // Загружаем HTML файл из пути
                            val htmlContent = file.readText()
                            loadDataWithBaseURL(
                                "file://",
                                htmlContent,
                                "text/html",
                                "UTF-8",
                                null
                            )

                            webViewClient = WebViewClient()
                        }
                    },
                    update = { webView ->
                        // Обновляем WebView при необходимости
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Обработка системной кнопки "Назад"
        BackHandler(onBack = onBack)
    }
}
