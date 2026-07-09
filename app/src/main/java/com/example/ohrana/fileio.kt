package com.example.ohrana

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

fun uploadHtmlFile(filePath: String) {
    val file = File(filePath)
    if (!file.exists()) {
        println("Ошибка: Файл не найден по пути $filePath")
        return
    }

    val client = OkHttpClient()

    // 1. Создаем тело запроса Multipart. Ключ для файла должен быть "file"
    val fileRequestBody = file.asRequestBody("text/html".toMediaType())
    val requestBody = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("file", file.name, fileRequestBody)
        // Опционально: можно добавить срок действия, например ?expires=1w в URL
        // или параметром формы, если это поддерживает API
        .build()

    // 2. Формируем POST-запрос
    val request = Request.Builder()
        .url("https://file.io")
        .post(requestBody)
        .build()

    // 3. Выполняем синхронный запрос (запускайте в фоновом потоке, если это Android/UI)
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw IOException("Ошибка сервера: ${response.code} ${response.message}")
        }

        // В переменной responseBody будет JSON-строка со ссылкой: {"success":true,"link":"https://file.io..."}
        val responseBody = response.body?.string()
        println("Ответ сервера: $responseBody")
    }
}

fun main() {
    // Пример вызова (замените на свой путь к файлу)
    uploadHtmlFile("path/to/your/index.html")
}
