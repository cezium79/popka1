package com.example.ohrana

import android.content.Context
import android.util.Log
import com.itextpdf.html2pdf.HtmlConverter
import com.itextpdf.kernel.pdf.PdfWriter
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * Класс для преобразования HTML файлов отчетов в PDF формат
 * с точным соблюдением дизайна и стилей
 * Работает в фоновом режиме по внешней команде
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
            
            // Удаляем изображения для уменьшения размера PDF
            val originalSize = htmlContent.length
            htmlContent = removeImagesFromHtml(htmlContent)
            val newSize = htmlContent.length
            Log.d(TAG, "HTML content reduced from $originalSize to $newSize bytes after removing images")
            
            // Если путь к PDF не указан, генерируем автоматически
            val outputPath = pdfOutputPath ?: generateDefaultPdfPath(htmlFilePath)
            val pdfFile = File(outputPath)
            
            // Создаем родительские директории, если они не существуют
            pdfFile.parentFile?.mkdirs()
            
            Log.i(TAG, "Converting HTML to PDF: $htmlFilePath -> $outputPath")
            
            // Преобразуем HTML в PDF
            convertToPdf(htmlContent, outputPath)
            
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
            
            convertToPdf(htmlContent, pdfOutputPath)
            
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
     * Основная функция преобразования HTML в PDF
     * Соблюдает все стили и изображения из HTML
     */
    private fun convertToPdf(htmlContent: String, outputPath: String) {
        val writer = PdfWriter(FileOutputStream(outputPath))
        val properties = com.itextpdf.html2pdf.ConverterProperties()
        properties.setBaseUri("file:///")
        
        val pdf = HtmlConverter.convertToPdf(
            htmlContent,
            writer,
            properties
        )
        // HtmlConverter.convertToPdf возвращает Document, который автоматически закрывается
    }
    
    /**
     * Удаляет изображения из HTML содержимого (для уменьшения размера PDF)
     * @param htmlContent HTML содержимое
     * @return HTML без изображений
     */
    private fun removeImagesFromHtml(htmlContent: String): String {
        // Удаляем все теги <img ...>
        val imgPattern = Pattern.compile("<img[^>]*src=\"[^\"]*\"[^>]*>", Pattern.CASE_INSENSITIVE)
        return imgPattern.matcher(htmlContent).replaceAll("")
            // Удаляем пустые div или span, которые могли содержать изображения
            .replace(Regex("<div[^>]*style=[^>]*>\\s*<span[^>]*>\\s*</span>\\s*</div>", RegexOption.MULTILINE), "")
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
}
