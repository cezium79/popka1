# HTML to PDF Converter

## Описание

Класс `HtmlToPdfConverter` преобразует HTML файлы отчетов в PDF формат с точным соблюдением дизайна и стилей. Работает в фоновом режиме по внешней команде.

## Использование

### Инициализация

```kotlin
val converter = HtmlToPdfConverter(context)
```

### Основные методы

#### 1. Простое преобразование HTML в PDF

```kotlin
val htmlFilePath = "/path/to/report.html"
val pdfPath = converter.convertHtmlToPdf(htmlFilePath)

if (pdfPath != null) {
    Log.d("Success", "PDF создан: $pdfPath")
} else {
    Log.e("Error", "Ошибка при создании PDF")
}
```

#### 2. Преобразование с указанием пути сохранения

```kotlin
val htmlFilePath = "/path/to/report.html"
val customPdfPath = "/path/to/custom/report.pdf"
val pdfPath = converter.convertHtmlToPdf(htmlFilePath, customPdfPath)
```

#### 3. Преобразование HTML строки в PDF

```kotlin
val htmlContent = "<html><body><h1>Отчет</h1></body></html>"
val pdfPath = "/path/to/output.pdf"
val success = converter.convertHtmlStringToPdf(htmlContent, pdfPath)
```

#### 4. Преобразование и загрузка в Яндекс.Диск

```kotlin
val htmlFilePath = "/path/to/report.html"
val cloudManager = CloudStorageManager(context)
val remotePath = "Ohrana/PDF/report.pdf"

val result = converter.convertAndUploadToDisk(htmlFilePath, cloudManager, remotePath)

if (result.isSuccess) {
    val downloadUrl = result.getOrNull()
    Log.d("Success", "PDF загружен: $downloadUrl")
} else {
    Log.e("Error", "Ошибка: ${result.exceptionOrNull()?.message}")
}
```

#### 5. Массовое преобразование всех HTML файлов в директории

```kotlin
val directory = "/Download/Ohrana/Reports"
val pdfPaths = converter.convertAllHtmlReportsInDirectory(directory)

Log.d("Success", "Создано ${pdfPaths.size} PDF файлов")
pdfPaths.forEach { path ->
    Log.d("PDF", path)
}
```

#### 6. Преобразование с загрузкой в облако

```kotlin
val htmlFilePath = "/path/to/report.html"
val cloudManager = CloudStorageManager(context)

val result = converter.convertAndUpload(
    htmlFilePath = htmlFilePath,
    cloudManager = cloudManager,
    uploadToCloud = true,   // загрузить в Yandex Cloud
    uploadToDisk = true     // загрузить в Яндекс.Диск
)

if (result.isSuccess()) {
    Log.d("Success", "PDF создан и загружен")
} else {
    Log.e("Error", result.getErrorMessage())
}
```

## Примеры интеграции

### В CloudStorageManager

Добавьте в существующий код в `CloudStorageManager.kt`:

```kotlin
fun exportHtmlToPdfAndUpload(shiftId: String, shiftDatabase: ShiftDatabaseManager): Result<String> {
    // Сначала генерируем HTML отчет
    val (htmlPath, _) = generateHtmlReport(shiftId, shiftDatabase, null)
    
    if (htmlPath == null) {
        return Result.failure(Exception("Не удалось создать HTML отчет"))
    }
    
    // Преобразуем в PDF и загружаем
    val converter = HtmlToPdfConverter(context)
    val cloudManager = this
    val remotePath = "Ohrana/PDF/shift_${shiftId}.pdf"
    
    return converter.convertAndUploadToDisk(htmlPath, cloudManager, remotePath)
}
```

### В фоновом потоке

```kotlin
// Внутри ViewModel или Service
CoroutineScope(Dispatchers.IO).launch {
    val htmlFilePath = findHtmlReportFile(shiftId, context)?.absolutePath
    if (htmlFilePath != null) {
        val converter = HtmlToPdfConverter(context)
        val pdfPath = converter.convertHtmlToPdf(htmlFilePath)
        
        if (pdfPath != null) {
            // Отправляем результат в UI поток
            withContext(Dispatchers.Main) {
                // Обновляем UI
                _pdfGenerated.value = pdfPath
            }
        }
    }
}
```

## Технические особенности

- **Библиотека**: iText 7 (html2pdf)
- **Сохранение стилей**: Полное сохранение CSS стилей из HTML
- **Изображения**: Автоматическое включение изображений (base64 и внешние ссылки)
- **Директория по умолчанию**: `/Download/Ohrana/PDF/`
- **Именование**: `shift_report_<shiftId>_<timestamp>.pdf`

## Пути по умолчанию

- **HTML отчеты**: `/Download/Ohrana/Reports/`
- **PDF файлы**: `/Download/Ohrana/PDF/`

## Обработка ошибок

Все методы возвращают результат с обработкой ошибок:

- `convertHtmlToPdf()` → `String?` (null в случае ошибки)
- `convertHtmlStringToPdf()` → `Boolean` (false в случае ошибки)
- `convertAndUploadToDisk()` → `Result<String>` (исключение в случае ошибки)
- `convertAndUpload()` → `PdfUploadResult` (объект с деталями)

## Логирование

Класс использует Android Logcat с тегом "HtmlToPdfConverter":
- `Log.i()` - информационные сообщения
- `Log.e()` - сообщения об ошибках
