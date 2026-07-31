package com.example.ohrana

import android.content.Context
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Класс для преобразования HTML файлов отчетов в PDF формат
 * с точным соблюдением дизайна и стилей
 * Использует WebView.print() для корректного рендеринга CSS (Grid, Flexbox)
 */
class HtmlToPdfConverter(private val context: Context) {
    
    companion object {
        private const val TAG = "HtmlToPdfConverter"
    }
    
    /**
     * Преобразует HTML файл в PDF с сохранением дизайна
     * @param htmlFilePath Путь к исходному HTML файлу
     * @param pdfOutputPath Путь для сохранения PDF файла (опционально)
     * @return Путь к созданному PDF файлу или null в случае ошибки
     */
    fun convertHtmlToPdf(htmlFilePath: String, pdfOutputPath: String? = null): String? {
        return try {
            val htmlFile = File(htmlFilePath)
            if (!htmlFile.exists()) {
                Log.e(TAG, "HTML file not found: $htmlFilePath")
                return null
            }
            
            // Читаем HTML содержимое
            var htmlContent = htmlFile.readText()
            if (htmlContent.isEmpty()) {
                Log.e(TAG, "HTML file is empty: $htmlFilePath")
                return null
            }
            
            // Сохраняем изображения в PDF
            htmlContent = restoreImagesFromBase64(htmlContent)
            Log.d(TAG, "Images restored from Base64 for PDF generation")
            
            // Если путь к PDF не указан, генерируем автоматически
            val outputPath = pdfOutputPath ?: generateDefaultPdfPath(htmlFilePath)
            val pdfFile = File(outputPath)
            
            // Создаем родительские директории, если они не существуют
            pdfFile.parentFile?.mkdirs()
            
            Log.i(TAG, "Converting HTML to PDF: $htmlFilePath -> $outputPath")
            
            // Преобразуем HTML в PDF через WebView.print()
            convertHtmlToPdfViaWebView(htmlContent, outputPath)
            
            if (pdfFile.exists()) {
                Log.i(TAG, "PDF successfully created: $outputPath (size: ${pdfFile.length()} bytes)")
                outputPath
            } else {
                Log.e(TAG, "Failed to create PDF file: $outputPath")
                null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error converting HTML to PDF: ${e.message}", e)
            null
        }
    }
    
    /**
     * Преобразует HTML строку в PDF файл
     * @param htmlContent HTML содержимое как строка
     * @param pdfOutputPath Путь для сохранения PDF файла
     * @return true если успешно, false в противном случае
     */
    fun convertHtmlStringToPdf(htmlContent: String, pdfOutputPath: String): Boolean {
        return try {
            val pdfFile = File(pdfOutputPath)
            pdfFile.parentFile?.mkdirs()
            
            Log.i(TAG, "Converting HTML string to PDF: $pdfOutputPath")
            
            convertHtmlToPdfViaWebView(htmlContent, pdfOutputPath)
            
            pdfFile.exists()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error converting HTML string to PDF: ${e.message}", e)
            false
        }
    }
    
    /**
     * Преобразует HTML файл в PDF и загружает в Яндекс.Диск
     * @param htmlFilePath Путь к HTML файлу
     * @param cloudManager Экземпляр CloudStorageManager для загрузки
     * @param remotePath Путь в Яндекс.Диске для сохранения PDF
     * @return Результат загрузки (URL на Диске или ошибка)
     */
    fun convertAndUploadToDisk(
        htmlFilePath: String,
        cloudManager: CloudStorageManager,
        remotePath: String
    ): Result<String> {
        return try {
            // Сначала преобразуем в PDF
            val pdfPath = convertHtmlToPdf(htmlFilePath)
            if (pdfPath == null) {
                return Result.failure(Exception("Failed to convert HTML to PDF"))
            }
            
            // Загружаем в Яндекс.Диск
            cloudManager.uploadFileToDisk(pdfPath, remotePath)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in convertAndUploadToDisk: ${e.message}", e)
            Result.failure(Exception("Ошибка преобразования или загрузки: ${e.message}"))
        }
    }
    
    /**
     * Генерирует путь для сохранения PDF файла по умолчанию
     * @param htmlFilePath Путь к HTML файлу
     * @return Путь к PDF файлу в директории PDF
     */
    private fun generateDefaultPdfPath(htmlFilePath: String): String {
        val htmlFile = File(htmlFilePath)
        val fileName = htmlFile.name.substringBeforeLast(".")
        
        val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        val ohranaDir = File(downloadDir, "Ohrana")
        val pdfDir = File(ohranaDir, "PDF")
        
        if (!pdfDir.exists()) {
            pdfDir.mkdirs()
        }
        
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val pdfFileName = "${fileName}_$timestamp.pdf"
        return File(pdfDir, pdfFileName).absolutePath
    }
    
    /**
     * Основная функция преобразования HTML в PDF через WebView.draw()
     * Корректно обрабатывает CSS Grid, Flexbox и все современные стили
     */
    private fun convertHtmlToPdfViaWebView(htmlContent: String, outputPath: String) {
        val latch = CountDownLatch(1)
        val result = PdfConversionResult()
        val isCompleted = AtomicBoolean(false)
        
        // Запускаем WebView на UI потоке
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        
        handler.post {
            try {
                // Создаём WebView для рендеринга
                val webView = WebView(context)
                webView.settings.javaScriptEnabled = false
                webView.settings.loadWithOverviewMode = true
                webView.settings.useWideViewPort = true
                webView.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                webView.visibility = View.GONE
                // Устанавливаем фиксированный размер для рендеринга в PDF (A4 @ 96dpi)
                webView.layout(0, 0, (595.2f * 2).toInt(), (841.8f * 2).toInt())
                webView.setBackgroundColor(Color.WHITE)
                
                // Устанавливаем WebViewClient для ожидания полной загрузки
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        Log.d(TAG, "WebView page finished loading")
                        // Даём дополнительное время для отрисовки
                        view?.postDelayed({
                            try {
                                // Генерируем PDF
                                generatePdfFromWebView(view, outputPath, result)
                            } finally {
                                view?.destroy()
                            }
                            isCompleted.set(true)
                            latch.countDown()
                        }, 500)
                    }
                }
                
                // Загружаем HTML контент
                webView.loadDataWithBaseURL(
                    "file:///android_asset/",
                    htmlContent,
                    "text/html",
                    "UTF-8",
                    null
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in WebView PDF generation: ${e.message}", e)
                result.exception = e
                isCompleted.set(true)
                latch.countDown()
            }
        }
        
        // Ожидаем завершения конвертации с таймаутом
        val completed = latch.await(30, TimeUnit.SECONDS)
        
        if (!completed && !isCompleted.get()) {
            throw Exception("Таймаут конвертации HTML в PDF (30 сек)")
        }
        
        if (result.exception != null) {
            throw result.exception!!
        }
        
        Log.d(TAG, "PDF conversion completed via WebView")
    }
    
    /**
     * Создаёт PDF документ из WebView, рендеря его через Canvas
     */
    private fun generatePdfFromWebView(
        webView: WebView,
        outputPath: String,
        result: PdfConversionResult
    ) {
        try {
            // Замеряем содержимое WebView
            webView.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
            )
            webView.layout(0, 0, webView.measuredWidth, webView.measuredHeight)
            
            // Получаем реальные размеры содержимого
            val contentWidth = webView.width
            val contentHeight = webView.height
            
            Log.d(TAG, "WebView content size: $contentWidth x $contentHeight")
            
            // A4 при 96 DPI
            val pageWidth = (595.2f).toInt()
            val pageHeight = (841.8f).toInt()
            val widthScale = pageWidth.toFloat() / contentWidth
            val heightScale = pageHeight.toFloat() / contentHeight
            
            // Коэффициент масштабирования
            val scale = minOf(widthScale, heightScale)
            val scaledWidth = (contentWidth * scale).toInt()
            val scaledHeight = (contentHeight * scale).toInt()
            
            // Создаём PDF документ
            val pdfDocument = PdfDocument()
            
            // Информация о странице (A4)
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            
            // Белый фон
            canvas.drawColor(Color.WHITE)
            
            // Создаём отрисовываемый с правильным масштабом
            webView.buildDrawingCache(true)
            val bitmap = webView.drawingCache
            webView.destroyDrawingCache()
            
            val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(
                bitmap,
                scaledWidth,
                scaledHeight,
                true
            )
            bitmap.recycle()
            
            // Рассчитываем позицию центрирования
            val x = (pageWidth - scaledWidth) / 2
            val y = (pageHeight - scaledHeight) / 2
            
            val destRect = RectF(x.toFloat(), y.toFloat(), (x + scaledWidth).toFloat(), (y + scaledHeight).toFloat())
            canvas.drawBitmap(scaledBitmap, null, destRect, null)
            
            Log.d(TAG, "PDF page rendered: scaled $scaledWidth x $scaledHeight at $x,$y")
            
            pdfDocument.finishPage(page)
            
            // Если высота контента превышает одну страницу A4 — выводим предупреждение
            if (contentHeight * scale > pageHeight) {
                Log.w(TAG, "Content exceeds single page A4, consider implementing multi-page support")
            }
            
            // Записываем PDF в файл
            val pdfFile = File(outputPath)
            pdfDocument.writeTo(FileOutputStream(pdfFile))
            pdfDocument.close()
            
            Log.d(TAG, "PDF file written: $outputPath")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating PDF from WebView: ${e.message}", e)
            result.exception = e
        }
    }
    
    /**
     * Восстанавливает изображения из Base64-представления и преобразует обратно в теги <img>
     * @param htmlContent HTML содержимое с закодированными изображениями
     * @return HTML с тегами <img src="data:...">
     */
    private fun restoreImagesFromBase64(htmlContent: String): String {
        var result = htmlContent
        val reader = java.io.BufferedReader(java.io.StringReader(result))
        val lines = mutableListOf<String>()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            lines.add(line!!)
        }
        reader.close()

        val output = StringBuilder()
        var i = 0
        while (i < lines.size) {
            val currentLine = lines[i]
            // Проверяем, содержит ли строка строковое представление изображения
            if (currentLine.startsWith("IMAGE_BASE64:") && i + 1 < lines.size) {
                val base64Data = lines[i + 1].trim()
                val mimeType = lines[i + 2].trim()
                val altText = lines[i + 3].trim()
                val imageData = "data:$mimeType;base64,$base64Data"
                output.append("<img src=\"$imageData\" alt=\"$altText\">")
                i += 4
            } else {
                output.appendLine(currentLine)
                i++
            }
        }
        return output.toString()
    }
    
    /**
     * Преобразует HTML файл в PDF и возвращает результат как строку
     * @param htmlFilePath Путь к HTML файлу
     * @return Результат преобразования с сообщением
     */
    fun convertHtmlToPdfWithMessage(htmlFilePath: String): String {
        val pdfPath = convertHtmlToPdf(htmlFilePath)
        return if (pdfPath != null) {
            "PDF успешно создан: $pdfPath"
        } else {
            "Ошибка при создании PDF. Проверьте логи для подробной информации."
        }
    }
    
    /**
     * Результат конвертации и загрузки PDF
     */
    data class PdfUploadResult(
        val pdfPath: String?,
        val cloudResult: Result<String?>,
        val diskResult: Result<String?>
    ) {
        fun isSuccess(): Boolean {
            return pdfPath != null && cloudResult.isSuccess && diskResult.isSuccess
        }
        
        fun getErrorMessage(): String {
            val errors = mutableListOf<String>()
            if (pdfPath == null) errors.add("Ошибка генерации PDF")
            if (!cloudResult.isSuccess) errors.add("Ошибка загрузки в облако: ${cloudResult.exceptionOrNull()?.message}")
            if (!diskResult.isSuccess) errors.add("Ошибка загрузки в Диск: ${diskResult.exceptionOrNull()?.message}")
            return errors.joinToString("; ")
        }
    }
    
    /**
     * Вспомогательный класс для хранения результата конвертации
     */
    private class PdfConversionResult {
        var exception: Exception? = null
    }
}
