package com.example.ohrana

import android.content.Context
import android.util.Log
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject
import org.json.JSONArray
import android.os.Environment
import android.widget.Toast
import java.net.URL
import java.io.OutputStream
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import android.content.Intent
import android.net.Uri
import android.content.SharedPreferences
import com.example.ohrana.SharedPrefsManager
import com.example.ohrana.ShiftRecord
import com.example.ohrana.RoundRecord
import com.example.ohrana.ShiftLogEntry

/**
 * Класс для работы с облачным хранилищем Yandex Cloud
 * Пока реализует простую генерацию JSON-файлов отчетов
 */
class CloudStorageManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("ohrana_prefs", Context.MODE_PRIVATE)
    private val tokenManager = CloudTokenManager(context)
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US)
    private val jsonFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    private val client = okhttp3.OkHttpClient() // OkHttp client для повторного использования

    companion object {
        private const val TAG = "CloudStorageManager"

        // Yandex Cloud API endpoints
        private const val YANDEX_CLOUD_STORAGE_HOST = "storage.yandexcloud.net"
        private const val YANDEX_DISK_API_HOST = "https://cloud-api.yandex.ru"
        private const val YANDEX_DISK_API_V1_PATH = "/v1/disk/resources"

        // Preference keys
        private const val YANDEX_CLOUD_TOKEN_KEY = "yandex_cloud_oauth_token"
        private const val YANDEX_CLOUD_BUCKET_KEY = "yandex_cloud_bucket_name"
        private const val YANDEX_CLOUD_PATH_KEY = "yandex_cloud_path"

        // Yandex Disk keys
        private const val YANDEX_DISK_TOKEN_KEY = "yandex_disk_oauth_token"
        private const val YANDEX_DISK_PATH_KEY = "yandex_disk_path"

        // Storage type constants
        const val STORAGE_TYPE_CLOUD = "cloud"
        const val STORAGE_TYPE_DISK = "disk"
    }

    /**
     * Сохраняет OAuth token в SharedPreferences
     */
    fun saveOAuthToken(token: String): Boolean {
        return prefs.edit().putString(YANDEX_CLOUD_TOKEN_KEY, token).commit()
    }

    /**
     * Получает сохраненный OAuth token
     */
    fun getOAuthToken(): String? {
        return prefs.getString(YANDEX_CLOUD_TOKEN_KEY, null)
    }

    /**
     * Сохраняет имя бакета
     */
    fun saveBucketName(bucketName: String): Boolean {
        return prefs.edit().putString(YANDEX_CLOUD_BUCKET_KEY, bucketName).commit()
    }

    /**
     * Получает имя бакета
     */
    fun getBucketName(): String? {
        return prefs.getString(YANDEX_CLOUD_BUCKET_KEY, null)
    }

    /**
     * Сохраняет путь в бакете
     */
    fun saveBucketPath(path: String): Boolean {
        return prefs.edit().putString(YANDEX_CLOUD_PATH_KEY, path).commit()
    }

    /**
     * Получает путь в бакете
     */
    fun getBucketPath(): String? {
        return prefs.getString(YANDEX_CLOUD_PATH_KEY, null)
    }

    /**
     * Сохраняет OAuth token для Яндекс.Диска
     */
    fun saveDiskToken(token: String): Boolean {
        return prefs.edit().putString(YANDEX_DISK_TOKEN_KEY, token).commit()
    }

    /**
     * Получает сохраненный OAuth token для Яндекс.Диска
     */
    fun getDiskToken(): String? {
        return prefs.getString(YANDEX_DISK_TOKEN_KEY, null)
    }

    /**
     * Получает токен из CloudTokenManager (по умолчанию)
     */
    fun getDefaultDiskToken(): String? {
        return tokenManager.getDefaultToken()?.token
    }

    /**
     * Получает путь из CloudTokenManager (по умолчанию)
     */
    fun getDefaultDiskPath(): String? {
        return tokenManager.getDefaultToken()?.path
    }

    /**
     * Сохраняет путь в Яндекс.Диске
     */
    fun saveDiskPath(path: String): Boolean {
        return prefs.edit().putString(YANDEX_DISK_PATH_KEY, path).commit()
    }

    /**
     * Получает путь в Яндекс.Диске
     */
    fun getDiskPath(): String? {
        return prefs.getString(YANDEX_DISK_PATH_KEY, null)
    }

    /**
     * Загружает файл в Yandex Cloud Storage через REST API (OkHttp)
     * @param filePath Путь к локальному файлу
     * @param remoteFileName Имя файла в облаке
     * @return Результат загрузки: успешный путь в облаке или сообщение об ошибке
     */
    fun uploadFileToCloud(filePath: String, remoteFileName: String): Result<String> {
        return try {
            val token = getOAuthToken() ?: return Result.failure(Exception("OAuth token not found"))
            val bucket =
                getBucketName() ?: return Result.failure(Exception("Bucket name not configured"))
            val path = getBucketPath() ?: ""

            val file = File(filePath)
            if (!file.exists()) {
                return Result.failure(Exception("File not found: $filePath"))
            }

            // Формируем путь в бакете
            val fullRemotePath = if (path.isNotEmpty()) "$path/$remoteFileName" else remoteFileName
            val urlString = "https://storage.yandexcloud.net/$bucket/$fullRemotePath"

            Log.i(TAG, "Uploading to: $urlString")
            Log.i(TAG, "File size: ${file.length()} bytes")

            // Используем OkHttp для загрузки (современный OkHttp API)
            val mimeType = "application/octet-stream".toMediaType()
            val fileRequestBody = okhttp3.RequestBody.create(mimeType, file.readBytes())

            // Формируем PUT-запрос
            val request = okhttp3.Request.Builder()
                .url(urlString)
                .put(fileRequestBody)
                .addHeader("Authorization", "OAuth $token")
                .build()

            // Выполняем запрос
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                Log.i(TAG, "uploadFileToCloud: Response code: ${response.code}")
                Log.i(TAG, "uploadFileToCloud: Response: $responseBody")

                if (response.isSuccessful) {
                    Log.i(TAG, "Upload successful")
                    Result.success("https://storage.yandexcloud.net/$bucket/$fullRemotePath")
                } else {
                    Log.e(TAG, "uploadFileToCloud: Upload failed with code ${response.code}")
                    Result.failure(Exception("Upload failed: ${response.code} ${response.message}"))
                }
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Неизвестная ошибка"
            Log.e(TAG, "uploadFileToCloud: Error: $errorMsg", e)
            Result.failure(Exception("Ошибка загрузки: ${e.message}"))
        }
    }

    /**
     * Получает ссылку для загрузки файла в Яндекс.Диск
     * @param filePath Путь к файлу на диске (например, "reports/shift_001.json")
     * @return Ссылка для загрузки или null в случае ошибки
     */
    fun getUploadLinkForDisk(filePath: String): Result<String> {
        return try {
            var token = getDiskToken()
            // Если токен не найден в SharedPreferences, используем токен из CloudTokenManager
            if (token == null) {
                token = getDefaultDiskToken()
            }
            if (token == null) {
                Log.e(TAG, "getUploadLinkForDisk: OAuth token is null")
                return Result.failure(Exception("OAuth token not found"))
            }

            Log.i(TAG, "getUploadLinkForDisk: token length=${token.length}, path=$filePath")

            val urlString =
                "${YANDEX_DISK_API_HOST}${YANDEX_DISK_API_V1_PATH}/upload?path=$filePath&overwrite=true"

            Log.i(TAG, "getUploadLinkForDisk: Getting upload link: $urlString")

            // Используем OkHttp для запроса (GET для получения ссылки)
            val request = okhttp3.Request.Builder()
                .url(urlString)
                .get()
                .addHeader("Authorization", "OAuth $token")
                .addHeader("Accept", "application/json")
                .build()

            // Выполняем запрос
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                Log.i(TAG, "getUploadLinkForDisk: Response code: ${response.code}")
                Log.i(TAG, "getUploadLinkForDisk: Response: $responseBody")

                if (response.isSuccessful) {
                    val json = JSONObject(responseBody)
                    val href = json.getString("href")
                    Log.i(TAG, "Upload link obtained: $href")
                    Result.success(href)
                } else {
                    Log.e(TAG, "getUploadLinkForDisk: Failed to get upload link: ${response.code}")
                    Result.failure(Exception("Ошибка получения ссылки: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Неизвестная ошибка"
            Log.e(TAG, "getUploadLinkForDisk: Error: $errorMsg", e)
            Result.failure(Exception("Ошибка получения ссылки: $errorMsg"))
        }
    }

    /**
     * Загружает файл в Яндекс.Диск через получение ссылки для загрузки
     * Использует GET /upload endpoint для получения ссылки, затем PUT для загрузки
     * @param filePath Путь к локальному файлу
     * @param remotePath Путь в Яндекс.Диске (например, "Ohrana/Reports/shift.json")
     * @return Результат загрузки: успешный URL или сообщение об ошибке
     */
    fun uploadFileToDisk(filePath: String, remotePath: String): Result<String> {
        return try {
            var token = getDiskToken()
            // Если токен не найден в SharedPreferences, используем токен из CloudTokenManager
            if (token == null) {
                token = getDefaultDiskToken()
            }
            if (token == null) {
                Log.e(TAG, "uploadFileToDisk: OAuth token is null")
                return Result.failure(Exception("OAuth token not found"))
            }

            Log.i(
                TAG,
                "uploadFileToDisk: token length=${token.length}, filePath=$filePath, remotePath=$remotePath"
            )

            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "uploadFileToDisk: File not found: $filePath")
                return Result.failure(Exception("File not found: $filePath"))
            }

            Log.i(TAG, "uploadFileToDisk: File size=${file.length()} bytes")

            // Создаем родительские директории, если они не существуют
            val parentPath = remotePath.substringBeforeLast("/")
            if (parentPath != remotePath) {
                Log.i(TAG, "uploadFileToDisk: Creating parent directories for path: $parentPath")

                // Получаем список всех директорий в пути
                val pathParts = parentPath.split("/").filter { it.isNotEmpty() }
                var currentPath = ""

                for (part in pathParts) {
                    currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"

                    // Проверяем, существует ли уже эта директория
                    if (!checkDirectoryExists(currentPath, token)) {
                        // Директория не существует, создаем ее через PUT запрос
                        Log.i(TAG, "uploadFileToDisk: Creating directory: $currentPath")
                        createSingleDirectory(currentPath, token)
                    } else {
                        Log.i(TAG, "uploadFileToDisk: Directory already exists: $currentPath")
                    }
                }
            }

            // Получаем ссылку для загрузки
            val urlString =
                "${YANDEX_DISK_API_HOST}${YANDEX_DISK_API_V1_PATH}/upload?path=$remotePath&overwrite=true"

            Log.i(TAG, "uploadFileToDisk: Getting upload link: $urlString")

            // Используем OkHttp для запроса (GET для получения ссылки)
            val request = okhttp3.Request.Builder()
                .url(urlString)
                .get()
                .addHeader("Authorization", "OAuth $token")
                .addHeader("Accept", "application/json")
                .build()

            // Выполняем запрос
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                Log.i(TAG, "uploadFileToDisk: Response code: ${response.code}")

                if (response.isSuccessful) {
                    val json = JSONObject(responseBody)
                    val href = json.getString("href")
                    Log.i(TAG, "Upload link href: $href")

                    // Загружаем файл по полученной ссылке
                    val mimeType = "application/pdf".toMediaType()
                    val uploadFileRequest = okhttp3.Request.Builder()
                        .url(href)
                        .put(okhttp3.RequestBody.create(mimeType, file.readBytes()))
                        .build()

                    client.newCall(uploadFileRequest).execute().use { uploadResponse ->
                        val uploadResponseBody = uploadResponse.body?.string() ?: ""

                        Log.i(
                            TAG,
                            "uploadFileToDisk: File upload response code: ${uploadResponse.code}"
                        )

                        if (uploadResponse.isSuccessful) {
                            Log.i(TAG, "uploadFileToDisk: File uploaded successfully, now getting download URL")
                            
                            // Небольшая задержка для индексации файла
                            Thread.sleep(1000)
                            
                            // Публикуем файл для получения публичного URL
                            val publishResult = publishFileToDisk(remotePath)
                            if (publishResult.isSuccess) {
                                Log.i(TAG, "uploadFileToDisk: File published successfully")
                                
                                // Получаем public_url напрямую из результата публикации
                                val publishedUrl = publishResult.getOrNull() ?: ""
                                
                                // Если ссылка не публичная (downloader.disk.yandex.ru), пробуем получить public_url через getPublicUrlForDisk
                                if (publishedUrl.contains("downloader.disk.yandex.ru")) {
                                    Log.w(TAG, "uploadFileToDisk: Got download link instead of public URL, trying to get public URL")
                                    val publicUrlResult = getPublicUrlForDisk(remotePath)
                                    if (publicUrlResult.isSuccess) {
                                        val publicUrl = publicUrlResult.getOrNull() ?: ""
                                        Log.i(TAG, "uploadFileToDisk: Public URL = '$publicUrl'")
                                        Result.success(publicUrl)
                                    } else {
                                        Log.e(TAG, "uploadFileToDisk: Failed to get public URL")
                                        Result.failure(Exception("Ошибка получения публичного URL: ${publicUrlResult.exceptionOrNull()?.message}"))
                                    }
                                } else {
                                    Log.i(TAG, "uploadFileToDisk: Public URL = '$publishedUrl'")
                                    Result.success(publishedUrl)
                                }
                            } else {
                                Log.e(TAG, "uploadFileToDisk: Failed to publish file")
                                Result.failure(Exception("Ошибка публикации файла: ${publishResult.exceptionOrNull()?.message}"))
                            }
                        } else {
                            Log.e(
                                TAG,
                                "uploadFileToDisk: File upload failed: ${uploadResponse.code}"
                            )
                            Result.failure(Exception("Ошибка загрузки файла: ${uploadResponse.code}"))
                        }
                    }
                } else {
                    Log.e(TAG, "uploadFileToDisk: Failed to get upload link: ${response.code}")
                    Result.failure(Exception("Ошибка получения ссылки для загрузки: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Неизвестная ошибка"
            Log.e(TAG, "Upload to disk failed: $errorMsg", e)
            Log.e(TAG, "Stack trace:", e)
            Result.failure(Exception("Ошибка загрузки в Яндекс.Диск: $errorMsg"))
        }
    }

    /**
     * Проверяет существование директории в Яндекс.Диске (OkHttp)
     * @param path Путь к директории
     * @param token OAuth token
     * @return true если существует, false если нет
     */
    private fun checkDirectoryExists(path: String, token: String): Boolean {
        return try {
            val urlString = "${YANDEX_DISK_API_HOST}${YANDEX_DISK_API_V1_PATH}?path=$path"

            // Используем OkHttp для запроса
            val request = okhttp3.Request.Builder()
                .url(urlString)
                .get()
                .addHeader("Authorization", "OAuth $token")
                .addHeader("Accept", "application/json")
                .build()

            // Выполняем запрос
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                Log.i(TAG, "checkDirectoryExists: Response code: ${response.code}, path: $path")

                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.w(TAG, "checkDirectoryExists: Error checking $path - ${e.message}")
            false
        }
    }

    /**
     * Создает одну директорию в Яндекс.Диске (OkHttp)
     * @param path Путь к директории
     * @param token OAuth token
     */
    private fun createSingleDirectory(path: String, token: String) {
        try {
            val urlString = "${YANDEX_DISK_API_HOST}${YANDEX_DISK_API_V1_PATH}?path=$path&type=dir"

            // Используем OkHttp для запроса (современный API с asRequestBody)
            val emptyBody = "".toByteArray()
            val mimeType = "application/octet-stream".toMediaType()
            val request = okhttp3.Request.Builder()
                .url(urlString)
                .put(okhttp3.RequestBody.create(mimeType, emptyBody))
                .addHeader("Authorization", "OAuth $token")
                .addHeader("Content-Type", "application/octet-stream")
                .build()

            // Выполняем запрос
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                Log.i(TAG, "createSingleDirectory: Response code: ${response.code}, path: $path")

                // 201 - создано, 200 - уже существует, 409 - конфликт (уже существует)
                if (response.code != 201 && response.code != 200 && response.code != 409) {
                    Log.w(
                        TAG,
                        "createSingleDirectory: Failed to create $path - code: ${response.code}"
                    )
                } else {
                    Log.i(
                        TAG,
                        "createSingleDirectory: Created or exists: $path (code: ${response.code})"
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "createSingleDirectory: Error creating $path - ${e.message}")
        }
    }

    /**
     * Получает публичный URL файла в Яндекс.Диске (OkHttp)
     * @param filePath Путь к файлу в диске
     * @return Публичный URL или null в случае ошибки
     */
    fun getPublicUrlForDisk(filePath: String): Result<String> {
        return try {
            var token = getDiskToken()
            // Если токен не найден в SharedPreferences, используем токен из CloudTokenManager
            if (token == null) {
                token = getDefaultDiskToken()
            }
            if (token == null) {
                return Result.failure(Exception("OAuth token not found"))
            }

            val urlString =
                "${YANDEX_DISK_API_HOST}${YANDEX_DISK_API_V1_PATH}?path=$filePath&fields=public_url"

            Log.i(TAG, "getPublicUrlForDisk: Getting public URL for path: $filePath")
            Log.i(TAG, "getPublicUrlForDisk: URL: $urlString")

            // Используем OkHttp для запроса
            val request = okhttp3.Request.Builder()
                .url(urlString)
                .get()
                .addHeader("Authorization", "OAuth $token")
                .addHeader("Accept", "application/json")
                .build()

            // Выполняем запрос
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                Log.i(TAG, "getPublicUrlForDisk: Response code: ${response.code}")
                Log.i(TAG, "getPublicUrlForDisk: Response body: $responseBody")

                if (response.isSuccessful) {
                    val json = JSONObject(responseBody)
                    val publicUrl = json.optString("public_url", "")
                    Log.i(TAG, "getPublicUrlForDisk: publicUrl from response = '$publicUrl'")
                    Result.success(publicUrl)
                } else {
                    Log.e(TAG, "getPublicUrlForDisk: Failed to get public URL, response code: ${response.code}")
                    Result.failure(Exception("Failed to get public URL: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Неизвестная ошибка"
            Log.e(TAG, "getPublicUrlForDisk: Error: $errorMsg", e)
            Result.failure(Exception("Ошибка получения публичного URL: $errorMsg"))
        }
    }

    /**
     * Публикует файл в Яндекс.Диске для получения публичного URL (OkHttp)
     * @param filePath Путь к файлу в диске
     * @return Публичный URL или null в случае ошибки
     */
    private fun publishFileToDisk(filePath: String): Result<String> {
        return try {
            var token = getDiskToken()
            // Если токен не найден в SharedPreferences, используем токен из CloudTokenManager
            if (token == null) {
                token = getDefaultDiskToken()
            }
            if (token == null) {
                return Result.failure(Exception("OAuth token not found"))
            }

            val urlString =
                "${YANDEX_DISK_API_HOST}${YANDEX_DISK_API_V1_PATH}/publish?path=$filePath"

            Log.i(TAG, "publishFileToDisk: Publishing file: $urlString")

            // Используем OkHttp для запроса (PUT для публикации, как указано в документации Яндекс.Диска)
            val request = okhttp3.Request.Builder()
                .url(urlString)
                .put(okhttp3.RequestBody.create(null, ""))
                .addHeader("Authorization", "OAuth $token")
                .addHeader("Accept", "application/json")
                .build()

            // Выполняем запрос
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                Log.i(TAG, "publishFileToDisk: Response code: ${response.code}")
                Log.i(TAG, "publishFileToDisk: Response body: $responseBody")

                if (response.isSuccessful) {
                    // Публикация успешна, возвращаем public_url из ответа
                    val json = JSONObject(responseBody)
                    val publicUrl = json.optString("public_url", "")
                    Log.i(TAG, "publishFileToDisk: public_url from response = '$publicUrl'")
                    if (publicUrl.isNotEmpty()) {
                        Log.i(TAG, "publishFileToDisk: Public URL: $publicUrl")
                        Result.success(publicUrl)
                    } else {
                        // Если public_url не вернулся, пробуем получить его через getPublicUrlForDisk
                        Log.w(TAG, "publishFileToDisk: public_url is empty, trying to get it via getPublicUrlForDisk")
                        val publicUrlResult = getPublicUrlForDisk(filePath)
                        if (publicUrlResult.isSuccess) {
                            val url = publicUrlResult.getOrNull() ?: ""
                            Log.i(TAG, "publishFileToDisk: Got public URL via getPublicUrlForDisk: $url")
                            Result.success(url)
                        } else {
                            Log.e(TAG, "publishFileToDisk: Failed to get public URL via getPublicUrlForDisk")
                            Result.failure(Exception("Ошибка получения публичного URL: ${publicUrlResult.exceptionOrNull()?.message}"))
                        }
                    }
                } else {
                    Log.e(TAG, "publishFileToDisk: Failed to publish file, response code: ${response.code}")
                    Result.failure(Exception("Ошибка публикации файла: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Неизвестная ошибка"
            Log.e(TAG, "publishFileToDisk: Error: $errorMsg", e)
            Result.failure(Exception("Ошибка публикации файла: $errorMsg"))
        }
    }

    /**
     * Получает прямую ссылку для скачивания файла из Яндекс.Диска
     * @param filePath Путь к файлу в диске
     * @return Прямая ссылка для скачивания или null в случае ошибки
     */
    private fun getDownloadLinkForDisk(filePath: String): Result<String> {
        return try {
            var token = getDiskToken()
            // Если токен не найден в SharedPreferences, используем токен из CloudTokenManager
            if (token == null) {
                token = getDefaultDiskToken()
            }
            if (token == null) {
                return Result.failure(Exception("OAuth token not found"))
            }

            val urlString =
                "${YANDEX_DISK_API_HOST}${YANDEX_DISK_API_V1_PATH}/download?path=$filePath"

            Log.i(TAG, "getDownloadLinkForDisk: Getting download link for path: $filePath")
            Log.i(TAG, "getDownloadLinkForDisk: URL: $urlString")

            // Используем OkHttp для запроса (GET для получения ссылки для скачивания)
            val request = okhttp3.Request.Builder()
                .url(urlString)
                .get()
                .addHeader("Authorization", "OAuth $token")
                .addHeader("Accept", "application/json")
                .build()

            // Выполняем запрос
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                Log.i(TAG, "getDownloadLinkForDisk: Response code: ${response.code}")
                Log.i(TAG, "getDownloadLinkForDisk: Response body: $responseBody")

                if (response.isSuccessful) {
                    val json = JSONObject(responseBody)
                    val href = json.optString("href", "")
                    Log.i(TAG, "getDownloadLinkForDisk: download href = '$href'")
                    if (href.isNotEmpty()) {
                        Result.success(href)
                    } else {
                        Result.failure(Exception("Не удалось получить ссылку для скачивания"))
                    }
                } else if (response.code == 302) {
                    // Если получили редирект, берем ссылку из Location заголовка
                    val location = response.header("Location", "") ?: ""
                    Log.i(TAG, "getDownloadLinkForDisk: Received 302 redirect to: $location")
                    if (location.isNotEmpty()) {
                        Result.success(location)
                    } else {
                        Result.failure(Exception("Не удалось получить ссылку для скачивания (302 redirect)"))
                    }
                } else if (response.code == 405) {
                    // Если /download не поддерживает GET, пробуем альтернативный метод
                    Log.w(TAG, "getDownloadLinkForDisk: /download returned 405, trying alternative approach")
                    
                    // Получаем public URL через /download?fields=public_url
                    val publicUrlResult = getPublicUrlForDisk(filePath)
                    if (publicUrlResult.isSuccess) {
                        val publicUrl = publicUrlResult.getOrNull() ?: ""
                        Log.i(TAG, "getDownloadLinkForDisk: Using public_url as download link: $publicUrl")
                        Result.success(publicUrl)
                    } else {
                        Result.failure(Exception("Не удалось получить ссылку для скачивания: ${publicUrlResult.exceptionOrNull()?.message}"))
                    }
                } else {
                    Log.e(TAG, "getDownloadLinkForDisk: Failed to get download link, response code: ${response.code}")
                    Result.failure(Exception("Ошибка получения ссылки для скачивания: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Неизвестная ошибка"
            Log.e(TAG, "getDownloadLinkForDisk: Error: $errorMsg", e)
            Result.failure(Exception("Ошибка получения ссылки для скачивания: $errorMsg"))
        }
    }

    /**
     * Извлекает время из даты в формате HH:mm:ss
     * @param dateTime Время в формате "dd.MM.yyyy HH:mm:ss" или null
     * @return Время в формате HH:mm:ss или "-" если null
     */
    private fun getTimePart(dateTime: String?): String {
        if (dateTime.isNullOrBlank()) return "-"
        return if (dateTime.length >= 11) {
            dateTime.substring(11)
        } else {
            "-"
        }
    }

    /**
     * Вычисляет длительность обхода между startTime и endTime
     * @param startTime Время начала в формате "dd.MM.yyyy HH:mm:ss"
     * @param endTime Время окончания в формате "dd.MM.yyyy HH:mm:ss" или null
     * @return Строка с длительностью в формате "mm мин. ss сек." или "-"
     */
    private fun calculateRoundDuration(startTime: String, endTime: String?): String {
        if (endTime.isNullOrBlank()) return "-"
        
        try {
            val format = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale.getDefault())
            val start = format.parse(startTime)
            val end = format.parse(endTime)
            
            if (start == null || end == null) return "-"
            
            val duration = end.time - start.time
            val hours = duration / (60 * 60 * 1000)
            val minutes = (duration % (60 * 60 * 1000)) / (60 * 1000)
            val seconds = (duration % (60 * 1000)) / 1000
            
            // Формат: mm мин. ss сек. (часы не отображаются, так как обходы короткие)
            return "$minutes мин. $seconds сек."
        } catch (e: Exception) {
            return "-"
        }
    }

    /**
     * Генерирует JSON-файл отчета для смены
     * Возвращает путь к созданному файлу
     */
    fun generateJsonReport(shiftId: String, shiftDatabase: ShiftDatabaseManager): String? {
        try {
            val shift = shiftDatabase.loadAllShifts().find { it.id == shiftId }
            if (shift == null) {
                Log.e(TAG, "Shift not found: $shiftId")
                return null
            }

            val rounds = shiftDatabase.loadAllRounds().filter { it.shiftId == shiftId }
            val logs = shiftDatabase.loadLogsByShift(shiftId)
            val violations =
                shiftDatabase.loadAllViolations().filter { it.roundId in rounds.map { it.id } }

            // Создаем JSON объект
            val reportJson = JSONObject()

            // Информация о смене
            reportJson.put("shift_id", shift.id)
            reportJson.put("employee_name", shift.employeeName)
            reportJson.put("start_time", shift.startTime)
            shift.endTime?.let { reportJson.put("end_time", it) }
            reportJson.put("strict_sequence_enabled", shift.strictSequenceEnabled)

            // Обходы
            val roundsArray = JSONArray()
            rounds.forEach { round ->
                val roundObj = JSONObject()
                roundObj.put("round_id", round.id)
                roundObj.put("start_time", round.startTime)
                round.endTime?.let { roundObj.put("end_time", it) }
                round.routeId?.let { roundObj.put("route_id", it) }
                round.routeName?.let { roundObj.put("route_name", it) }
                roundObj.put("checkpoints_count", round.checkpointsCount)
                roundObj.put("checkpoints_passed", round.checkpointsPassed)
                roundObj.put("sequence_violations", round.sequenceViolations)
                roundsArray.put(roundObj)
            }
            reportJson.put("rounds", roundsArray)

            // Логи
            val logsArray = JSONArray()
            logs.forEach { log ->
                val logObj = JSONObject()
                logObj.put("checkpoint_name", log.checkpointName)
                logObj.put("checkpoint_id", log.checkpointId)
                logObj.put("timestamp", log.timestamp)
                logObj.put("round_id", log.roundId)
                logObj.put("route_name", log.routeName)
                logObj.put("sequence_index", log.sequenceIndex)
                logObj.put("is_sequence_correct", log.isSequenceCorrect)
                logObj.put("scan_type", log.scanType)
                logObj.put("action_type", log.actionType)
                log.answer?.let { logObj.put("answer", it) }
                log.inputValue?.let { logObj.put("input_value", it) }
                log.photoPath?.let { logObj.put("photo_path", it) }
                log.latitude?.let { logObj.put("latitude", it) }
                log.longitude?.let { logObj.put("longitude", it) }
                log.sequenceErrorType?.let { logObj.put("sequence_error_type", it.name) }
                logsArray.put(logObj)
            }
            reportJson.put("logs", logsArray)

            // Нарушения
            val violationsArray = JSONArray()
            violations.forEach { violation ->
                val violationObj = JSONObject()
                violationObj.put("timestamp", violation.timestamp)
                violationObj.put("round_id", violation.roundId)
                violationObj.put("expected_checkpoint_id", violation.expectedCheckpointId)
                violationObj.put("expected_checkpoint_name", violation.expectedCheckpointName)
                violationObj.put("actual_checkpoint_id", violation.actualCheckpointId)
                violationObj.put("actual_checkpoint_name", violation.actualCheckpointName)
                violationObj.put("is_nfc", violation.isNfc)
                violationsArray.put(violationObj)
            }
            reportJson.put("violations", violationsArray)

            // Происшествия
            val incidents = shiftDatabase.loadIncidentsByShift(shiftId)
            if (incidents.isNotEmpty()) {
                val incidentsArray = JSONArray()
                incidents.forEach { incident ->
                    val incidentObj = JSONObject()
                    incidentObj.put("timestamp", incident.timestamp)
                    incidentObj.put("shift_id", incident.shiftId)
                    incidentObj.put("round_id", incident.roundId)
                    incidentObj.put("employee_name", incident.employeeName)
                    incidentObj.put("incident_type", incident.incidentType.name)
                    incidentObj.put("description", incident.description)
                    incidentObj.put("photo_path", incident.photoPath)
                    incident.latitude?.let { incidentObj.put("latitude", it) }
                    incident.longitude?.let { incidentObj.put("longitude", it) }
                    incidentsArray.put(incidentObj)
                }
                reportJson.put("incidents", incidentsArray)
            }

            // Сохраняем файл
            val fileName = "shift_report_${shift.id}_${jsonFormat.format(Date())}.json"
            val downloadDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val ohranaDir = File(downloadDir, "Ohrana")
            val reportsDir = File(ohranaDir, "Reports")

            if (!reportsDir.exists()) {
                reportsDir.mkdirs()
            }

            val outputFile = File(reportsDir, fileName)
            FileOutputStream(outputFile).use { fos ->
                fos.write(reportJson.toString(4).toByteArray())
            }

            Log.i(TAG, "Report saved to: ${outputFile.absolutePath}")

            return outputFile.absolutePath

        } catch (e: Exception) {
            Log.e(TAG, "Error generating JSON report: ${e.message}", e)
            return null
        }
    }

    /**
     * Генерирует текстовый отчет для смены (для текстового журнала)
     * Возвращает путь к созданному файлу
     */
    fun generateTextReport(shiftId: String, shiftDatabase: ShiftDatabaseManager): String? {
        try {
            val shift = shiftDatabase.loadAllShifts().find { it.id == shiftId }
            if (shift == null) {
                Log.e(TAG, "Shift not found: $shiftId")
                return null
            }

            val rounds = shiftDatabase.loadAllRounds().filter { it.shiftId == shiftId }
            val logs = shiftDatabase.loadLogsByShift(shiftId)
            val incidents = shiftDatabase.loadIncidentsByShift(shiftId)

            val text = StringBuilder()
            val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US)

            // Заголовок
            text.append("=".repeat(60)).append("\n")
            text.append("ТЕКСТОВЫЙ ЖУРНАЛ ОБХОДОВ\n")
            text.append("=".repeat(60)).append("\n\n")

            // Информация о смене
            text.append("СМЕНА №${shift.id.takeLast(3)}\n")
            text.append("-".repeat(40)).append("\n")
            text.append("Дата: ${shift.startTime.split(" ").firstOrNull() ?: "-"}\n")
            text.append("Время начала: ${shift.startTime.split(" ").lastOrNull() ?: "-"}\n")
            shift.endTime?.let { text.append("Время окончания: ${it.split(" ").lastOrNull() ?: "-"}\n") }
            text.append("Охранник: ${shift.employeeName}\n")
            text.append("Контроль последовательности: ${if (shift.strictSequenceEnabled) "ВКЛ" else "ВЫКЛ"}\n\n")

            // Обходы
            text.append("ОБХОДЫ\n")
            text.append("-".repeat(40)).append("\n\n")

            rounds.forEach { round ->
                text.append("Обход №${round.id}\n")
                text.append("  Маршрут: ${round.routeName ?: "не указан"}\n")
                text.append("  Время начала: ${round.startTime.split(" ").lastOrNull() ?: "-"}\n")
                round.endTime?.let { text.append("  Время окончания: ${it.split(" ").lastOrNull() ?: "-"}\n") }
                text.append("  Чекпоинтов: ${round.checkpointsCount}\n")
                text.append("  Пройдено: ${round.checkpointsPassed}\n")
                text.append("  Нарушений: ${round.sequenceViolations}\n\n")
            }

            // Логи
            text.append("ЛОГИ ОБХОДОВ\n")
            text.append("-".repeat(40)).append("\n\n")

            rounds.forEach { round ->
                val roundLogs = logs.filter { it.roundId == round.id }
                if (roundLogs.isNotEmpty()) {
                    text.append("Обход №${round.id}:\n")
                    roundLogs.forEach { log ->
                        val status = if (log.isSequenceCorrect) "OK" else "НАРУШЕНИЕ"
                        val timePart = log.timestamp.split(" ").lastOrNull() ?: "-"
                        
                        text.append("  [$timePart] ${log.checkpointName} - ${log.actionType} - $status\n")
                        
                        // Дополнительная информация по действиям
                        log.questionText?.let { text.append("    Вопрос: $it\n") }
                        log.answer?.let { text.append("    Ответ: $it\n") }
                        log.inputTitle?.let { text.append("    Поле: $it\n") }
                        log.inputValue?.let { text.append("    Ввод: $it\n") }
                        log.photoPath?.let { text.append("    Фото: $it\n") }
                        log.sequenceErrorType?.let { text.append("    Тип нарушения: ${it.name}\n") }
                    }
                    text.append("\n")
                }
            }

            // Происшествия (текстовый журнал)
            text.append("ПРОИСШЕСТВИЯ\n")
            text.append("-".repeat(40)).append("\n\n")

            if (incidents.isNotEmpty()) {
                text.append("Всего зафиксировано происшествий: ${incidents.size}\n\n")

                // Группируем по типам
                val incidentsByType = incidents.groupBy { it.incidentType }
                
                incidentsByType.forEach { (type, typeIncidents) ->
                    val typeText = when (type) {
                        IncidentType.FOREIGN_ITEM -> "Посторонний предмет"
                        IncidentType.MISSING_ITEM -> "Пропажа предмета"
                        IncidentType.VANDALISM_DAMAGE -> "Последствия вандализма"
                        IncidentType.BREAKDOWN -> "Поломка"
                        IncidentType.OTHER -> "Другое"
                    }
                    
                    text.append("$typeText (${typeIncidents.size} шт.):\n")
                    
                    typeIncidents.forEach { incident ->
                        val timePart = incident.timestamp.split(" ").lastOrNull() ?: "-"
                        text.append("  [$timePart] ${incident.employeeName}\n")
                        text.append("    Описание: ${incident.description}\n")
                        text.append("    Фото: ${incident.photoPath}\n")
                        text.append("    Обход: ${incident.roundId}\n\n")
                    }
                    text.append("\n")
                }
            } else {
                text.append("Происшествий не зафиксировано\n\n")
            }

            // Итоговая статистика
            text.append("=".repeat(60)).append("\n")
            text.append("ИТОГО\n")
            text.append("-".repeat(40)).append("\n")
            text.append("Всего обходов: ${rounds.size}\n")
            text.append("Всего зафиксированных происшествий: ${incidents.size}\n")
            text.append("Генератор: Ohrana Security System v1.0\n")
            text.append("Время генерации: ${dateFormat.format(Date())}\n")
            text.append("=".repeat(60)).append("\n")

            // Сохраняем файл
            val fileName = "shift_text_journal_${shift.id}_${jsonFormat.format(Date())}.txt"
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val ohranaDir = File(downloadDir, "Ohrana")
            val reportsDir = File(ohranaDir, "Reports")

            if (!reportsDir.exists()) {
                reportsDir.mkdirs()
            }

            val outputFile = File(reportsDir, fileName)
            FileOutputStream(outputFile).use { fos ->
                fos.write(text.toString().toByteArray())
            }

            Log.i(TAG, "Text report saved to: ${outputFile.absolutePath}")

            return outputFile.absolutePath

        } catch (e: Exception) {
            Log.e(TAG, "Error generating text report: ${e.message}", e)
            return null
        }
    }

    /**
     * Генерирует HTML-файл отчета для смены (удобный для просмотра в браузере)
     * Возвращает путь к созданному файлу
     */
    fun generateHtmlReport(shiftId: String, shiftDatabase: ShiftDatabaseManager, context: Context? = null, sharedPrefsManager: SharedPrefsManager? = null): Pair<String?, String?> {
        Log.d("CloudStorageManager", "generateHtmlReport: sharedPrefsManager=$sharedPrefsManager, context=$context")
        val spm = sharedPrefsManager ?: context?.let { SharedPrefsManager(it) }
        Log.d("CloudStorageManager", "generateHtmlReport: created spm=$spm")
        if (spm != null) {
            Log.d("CloudStorageManager", "generateHtmlReport: spm.isStrictSequenceEnabled()=${spm.isStrictSequenceEnabled()}")
        }
        return generateHtmlReportWithDesign(shiftId, shiftDatabase, spm, context)
    }
    
    /**
     * Генерирует HTML-файл отчета для смены
     * @param shiftId ID смены
     * @param shiftDatabase Менеджер базы данных смен
     * @param sharedPrefsManager SharedPrefsManager для получения данных о маршрутах
     * @return Pair(путь к созданному файлу, результат смены) или Pair(null, null) в случае ошибки
     */
    fun generateHtmlReportWithDesign(shiftId: String, shiftDatabase: ShiftDatabaseManager, sharedPrefsManager: SharedPrefsManager?, context: Context? = null): Pair<String?, String?> {
        Log.d("CloudStorageManager", "generateHtmlReportWithDesign: sharedPrefsManager=$sharedPrefsManager")
        
        // Добавляем логирование для отладки
        if (sharedPrefsManager != null) {
            Log.d("CloudStorageManager", "generateHtmlReportWithDesign: sharedPrefsManager.prefs = ${sharedPrefsManager.prefs}")
            Log.d("CloudStorageManager", "generateHtmlReportWithDesign: isStrictSequenceEnabled() = ${sharedPrefsManager.isStrictSequenceEnabled()}")
        } else {
            Log.d("CloudStorageManager", "generateHtmlReportWithDesign: sharedPrefsManager is null")
        }
        
        return try {
            val shift = shiftDatabase.loadAllShifts().find { it.id == shiftId }
            if (shift == null) {
                Log.e(TAG, "Shift not found: $shiftId")
                return null to null
            }

            val rounds = shiftDatabase.loadAllRounds().filter { it.shiftId == shiftId }
            val logs = shiftDatabase.loadLogsByShift(shiftId)
            
            // Загружаем будильники для получения времени каждого обхода
            val routeAlarms = sharedPrefsManager?.loadRouteAlarms() ?: emptyList()
            
            // Логируем количество логов и примеры значений inputValue
            Log.d(TAG, "generateHtmlReportWithDesign: shiftId=$shiftId, logsCount=${logs.size}")
            logs.forEach { log ->
                if (log.actionType == "INPUT") {
                    Log.d(TAG, "generateHtmlReportWithDesign: INPUT log - checkpoint=${log.checkpointName}, inputValue='${log.inputValue}', inputTitle='${log.inputTitle}', isNull=${log.inputValue == null}, length=${log.inputValue?.length}")
                }
            }
            
            // Логируем для отладки: все INPUT записи
            logs.filter { it.actionType == "INPUT" }.forEach { log ->
                Log.d(TAG, "generateHtmlReportWithDesign DEBUG: actionType=${log.actionType}, checkpoint=${log.checkpointName}, inputValue='${log.inputValue}', isNull=${log.inputValue == null}")
            }

            val html = StringBuilder()

            // Извлекаем номер смены из ID (формат: NSDDMMYY_NNN)
            val shiftNumber = run {
                val pattern = java.util.regex.Pattern.compile("NS\\d{6}_(\\d{3})")
                val matcher = pattern.matcher(shiftId)
                if (matcher.find()) {
                    matcher.group(1)?.toInt() ?: 0
                } else {
                    0
                }
            }

            // === СПИСОК ОХРАННИКОВ ===
            val guards = shift.guardList
            val guardsNames =
                if (guards.isNotEmpty()) guards.joinToString(", ") { it.name } else shift.employeeName

            // === СТАТИСТИКА ПО ОБХОДАМ ===
            val completedRounds = rounds.filter { it.isCompleted }

            // === СТАТИСТИКА ПО ЧЕКПОИНТАМ ===
            val validCheckpoints = logs.filter { it.isSequenceCorrect }
            val totalCheckpoints = logs.size
            // Используем уже сохраненное значение общего количества чекпоинтов из SharedPreferences
            // Если sharedPrefsManager недоступен, будет отображено "NULL"

            // === СТАТИСТИКА ПО НАРУШЕНИЯМ ===
            val totalViolations = logs.count { !it.isSequenceCorrect }

            // === ЛОГИЧЕСКИЙ РЕЗУЛЬТАТ СМЕНЫ ===
            // completedRounds.size - фактически пройденные обходы
            // loadRouteRoundsCount() - запланированные обходы (из настроек)
            // validCheckpoints.size - чекпоинты без нарушений (с нарушениями не учитываются)
            // totalCheckpointsPlan - запланированные чекпоинты (из SharedPreferences)
            val completedRoundsCount = completedRounds.size
            val totalRoundsCount = sharedPrefsManager?.loadRouteRoundsCount() ?: rounds.size
            val validCheckpointsCount = validCheckpoints.size
            val totalCheckpointsCount = logs.size
            
            // Проверяем, включен ли строгий контроль
            val strictSequenceEnabled = sharedPrefsManager?.isStrictSequenceEnabled() ?: false
            Log.d("CloudStorageManager", "generateHtmlReportWithDesign: strictSequenceEnabled=$strictSequenceEnabled")
            
            // Определяем результат смены
            val roundsMatchPlan = completedRoundsCount == totalRoundsCount
            // Используем sharedPrefsManager для загрузки totalCheckpointsPlan, если доступен
            val totalCheckpointsPlan = sharedPrefsManager?.loadTotalScheduledCheckpoints() ?: context?.let { SharedPrefsManager(it).loadTotalScheduledCheckpoints() }
            val checkpointsMatchPlan = totalCheckpointsPlan != null && validCheckpointsCount == totalCheckpointsPlan
            val hasViolations = totalViolations > 0
            
            val shiftResult = when {
                // Если строгий контроль выключен - свободный режим
                !strictSequenceEnabled -> "Свободный режим"
                // Если количество обходов и чекпоинтов соответствуют плану, но были нарушения
                roundsMatchPlan && checkpointsMatchPlan && hasViolations -> "ПОЙДЕТ"
                // Если количество обходов или чекпоинтов не соответствуют плану (чекпоинты с нарушениями не учитываются)
                !roundsMatchPlan || !checkpointsMatchPlan -> "ЭТО ФИАСКО"
                // Если количество обходов и чекпоинтов соответствуют плану (без нарушений)
                roundsMatchPlan && checkpointsMatchPlan -> "✓"
                // Если totalCheckpointsPlan равен null, показываем предупреждение
                totalCheckpointsPlan == null -> "⚠️ (NULL)"
                // Иначе - есть проблема
                else -> "⚠️"
            }

            // === СОРТИРОВКА ЛОГОВ ПО ВРЕМЕНИ ===
            val sortedLogs = logs.sortedBy { it.timestamp }

            // HTML заголовок
            html.append(
                """
                <!DOCTYPE html>
                <html lang="ru">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Отчет о смене</title>
                    <style>
                        body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }
                        .container { max-width: 1200px; margin: 0 auto; background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                        .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 20px; border-radius: 8px; margin-bottom: 20px; }
                        .header h1 { margin: 0 0 10px 0; font-size: 24px; }
                        .header-info { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; }
                        .info-item { background: rgba(255,255,255,0.2); padding: 10px; border-radius: 4px; }
                        .info-item label { font-size: 11px; opacity: 0.9; display: block; }
                        .info-item value { font-size: 13px; font-weight: bold; }
                        .section { margin-bottom: 20px; }
                        .section h2 { background: #e0e0e0; padding: 10px; border-radius: 4px; color: #333; }
                        .round-card { background: #fafafa; border: 1px solid #ddd; border-radius: 8px; padding: 15px; margin-bottom: 15px; }
                        .round-card h3 { margin: 0 0 10px 0; color: #667eea; font-size: 16px; }
                        .round-info { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-bottom: 10px; }
                        .round-info-item { background: #fff; padding: 8px; border-radius: 4px; border: 1px solid #eee; text-align: center; }
                        .round-info-item label { font-size: 11px; color: #666; }
                        .round-info-item value { font-size: 14px; font-weight: bold; color: #333; }
                        .round-duration { color: #667eea; font-weight: bold; }
                        .guards-list { background: #fff3cd; padding: 8px; border-radius: 4px; margin: 10px 0; font-size: 13px; }
                        .stats-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 15px; }
                        .stat-item { text-align: center; padding: 12px; background: #f5f5f5; border-radius: 4px; }
                        .stat-value { font-size: 28px; font-weight: bold; color: #333; }
                        .stat-value.violations { color: #f44336; }
                        .stat-value.success { color: #4caf50; }
                        .stat-label { font-size: 12px; color: #666; }
                        .logs-table { width: 100%; border-collapse: collapse; margin-top: 10px; }
                        .logs-table th, .logs-table td { padding: 10px; text-align: left; border-bottom: 1px solid #eee; }
                        .logs-table th { background: #667eea; color: white; font-size: 12px; }
                        .logs-table tr:hover { background: #f9f9f9; }
                        .violation { color: #f44336; font-weight: bold; }
                        .success { color: #4caf50; }
                        .photo-item { margin: 10px 0; }
                        .photo-item img { max-width: 100%; max-height: 200px; border-radius: 4px; border: 1px solid #ddd; }
                        .photo-info { font-size: 12px; color: #666; margin-top: 5px; }
                        .action-details { background: #f9f9f9; padding: 8px; border-radius: 4px; margin: 5px 0; font-size: 12px; }
                        .action-details span { display: block; margin: 2px 0; }
                        .action-details label { font-weight: bold; color: #667eea; }
                        .footer { margin-top: 20px; text-align: center; color: #666; font-size: 12px; border-top: 1px solid #eee; padding-top: 15px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>ОТЧЕТ О СМЕНЕ</h1>
                            <div class="header-info">
                                <div class="info-item">
                                    <label>Смена №${shiftNumber}</label>
                                    <value>${shift.startTime.substring(0, 10)}</value>
                                </div>
                                <div class="info-item">
                                    <label>Охранники</label>
                                    <value style="font-size: 11px;">$guardsNames</value>
                                </div>
                                <div class="info-item">
                                    <label>Время начала</label>
                                    <value>${shift.startTime.substring(11)}</value>
                                </div>
                                <div class="info-item">
                                    <label>Время окончания</label>
                                    <value>${shift.endTime?.substring(11) ?: "-"}</value>
                                </div>
                            </div>
                        </div>
            """.trimIndent()
            )

            // Статистика
            // Используем уже объявленные переменные: completedRoundsCount, totalRoundsCount,
            // validCheckpointsCount, totalCheckpointsCount, totalScheduledCheckpoints, shiftResult
            
            html.append(
                """
                <div class="section">
                    <h2>📊 Статистика смены</h2>
                    <div class="stats-grid">
                        <div class="stat-item">
                            <div class="stat-value success">$completedRoundsCount из ${context?.let { SharedPrefsManager(it).loadRouteRoundsCount() } ?: rounds.size}</div>
                            <div class="stat-label">Обходов (пройдено)</div>
                        </div>
                        <div class="stat-item">
                            <div class="stat-value success">$validCheckpointsCount из ${context?.let { SharedPrefsManager(it).loadTotalScheduledCheckpoints() } ?: "NULL"}</div>
                            <div class="stat-label">Чекпоинтов (без нарушений)</div>
                        </div>
                        <div class="stat-item">
                            <div class="stat-value violations">$totalViolations</div>
                            <div class="stat-label">Нарушений</div>
                        </div>
                        <div class="stat-item">
                            <div class="stat-value" style="font-size: 32px; color: ${if (shiftResult == "ПОЙДЕТ" || shiftResult == "✓") "#4caf50" else "#f44336"}">$shiftResult</div>
                            <div class="stat-label">Результат</div>
                        </div>
                    </div>
                </div>
            """.trimIndent()
            )

            // Обходы и логи
            rounds.forEach { round ->
                // Получаем время будильника для этого обхода
                val alarmTime = routeAlarms.find { it.id == round.id }?.time ?: "-"
                
                html.append(
                    """
                    <div class="section">
                        <div class="round-card">
                            <h3>🔄 Обход №${round.id} (${alarmTime})</h3>
                            <div class="stats-grid">
                                <div class="stat-item">
                                    <div class="stat-value">${round.routeName ?: "-"}</div>
                                    <div class="stat-label">Маршрут</div>
                                </div>
                                <div class="stat-item">
                                    <div class="stat-value">${getTimePart(round.startTime)}</div>
                                    <div class="stat-label">Время начала</div>
                                </div>
                                <div class="stat-item">
                                    <div class="stat-value">${getTimePart(round.endTime) ?: "-"}</div>
                                    <div class="stat-label">Время окончания</div>
                                </div>
                                <div class="stat-item">
                                    <div class="stat-value round-duration">${calculateRoundDuration(round.startTime, round.endTime)}</div>
                                    <div class="stat-label">Длительность</div>
                                </div>
                            </div>
                """.trimIndent()
                )

                val roundLogs = logs.filter { it.roundId == round.id }
                // Сортируем логи по времени
                val sortedRoundLogs = roundLogs.sortedBy { it.timestamp }

                // Получаем охранников, участвовавших в этом обходе
                val roundGuards = sortedRoundLogs.map { it.employeeName }.distinct()

                if (sortedRoundLogs.isNotEmpty()) {
                    html.append(
                        """
                        <table class="logs-table">
                            <thead>
                                <tr>
                                    <th>Чекпоинт</th>
                                    <th>Время</th>
                                    <th>Охранник</th>
                                    <th>Статус</th>
                                    <th>Действие</th>
                                </tr>
                            </thead>
                            <tbody>
                    """.trimIndent()
                    )

                    sortedRoundLogs.forEach { log ->
                        val statusClass = if (log.isSequenceCorrect) "success" else "violation"
                        val statusIcon = if (log.isSequenceCorrect) "✓" else "⚠️"
                        val statusText = when (log.actionType) {
                            "CHECKPOINT" -> "Пройден"
                            "QUESTION" -> "Вопрос"
                            "INPUT" -> "Ввод"
                            "PHOTO" -> "Фото"
                            else -> log.actionType
                        }

                        // Формируем дополнительную информацию по действиям
                        val actionDetails = buildString {
                            log.questionText?.let { append("<span><label>Вопрос:</label> ${it}</span>") }
                            log.answer?.let { append("<span><label>Ответ:</label> ${it}</span>") }
                            log.inputTitle?.let { append("<span><label>Поле:</label> ${it}</span>") }
                            log.inputValue?.let { append("<span><label>Ввод:</label> ${it}</span>") }
                        }

                        html.append(
                            """
                            <tr>
                                <td>${log.checkpointName}</td>
                                <td>${log.timestamp.substring(11)}</td>
                                <td>${log.employeeName}</td>
                                <td class="$statusClass">$statusIcon ${if (log.isSequenceCorrect) "OK" else "НАРУШЕНИЕ"}</td>
                                <td>
                                    $statusText
                                    ${if (actionDetails.isNotBlank()) "<div class=\"action-details\">$actionDetails</div>" else ""}
                                </td>
                            </tr>
                        """.trimIndent()
                        )
                    }

                    html.append(
                        """
                            </tbody>
                        </table>
                    """.trimIndent()
                    )
                }

                html.append(
                    """
                        </div>
                    </div>
                """.trimIndent()
                )
            }

            // Фотографии за смену
            val photos = logs.filter { it.photoPath != null }
            if (photos.isNotEmpty()) {
                html.append(
                    """
                    <div class="section">
                        <h2>📷 Фотографии</h2>
                """.trimIndent()
                )

                photos.forEach { photoLog ->
                    // Получаем имя файла из пути
                    val photoFileName = photoLog.photoPath?.substringAfterLast("/") ?: "photo.jpg"

                    // Пытаемся загрузить фото и встроить его как base64
                    val photoBase64 = try {
                        val photoPath = photoLog.photoPath
                        if (photoPath != null) {
                            val photoFile = File(photoPath)
                            if (photoFile.exists()) {
                                val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                                if (bitmap != null) {
                                    val outputStream = ByteArrayOutputStream()
                                    bitmap.compress(
                                        android.graphics.Bitmap.CompressFormat.JPEG,
                                        80,
                                        outputStream
                                    )
                                    val imageBytes = outputStream.toByteArray()
                                    Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                                } else {
                                    null
                                }
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load photo: ${photoLog.photoPath}", e)
                        null
                    }

                    html.append(
                        """
                        <div class="photo-item">
                            <div class="photo-info">
                                <strong>Обход:</strong> ${photoLog.roundId}<br>
                                <strong>Чекпоинт:</strong> ${photoLog.checkpointName}<br>
                                <strong>Время:</strong> ${photoLog.timestamp.substring(11)}<br>
                                <strong>Охранник:</strong> ${photoLog.employeeName}
                            </div>
                            ${
                            if (photoBase64 != null)
                                "<img src=\"data:image/jpeg;base64,$photoBase64\" alt=\"Фото смены\">"
                            else
                                "<p>Фото недоступно</p>"
                        }
                        </div>
                    """.trimIndent()
                    )
                }

                html.append(
                    """
                    </div>
                """.trimIndent()
                )
            }
            
            // Происшествия за смену
            val incidents = shiftDatabase.loadIncidentsByShift(shiftId)
            if (incidents.isNotEmpty()) {
                html.append(
                    """
                    <div class="section">
                        <h2>📸 Происшествия</h2>
                        <div class="stats-grid">
                            <div class="stat-item">
                                <div class="stat-value" style="font-size: 32px; color: #ff9800;">${incidents.size}</div>
                                <div class="stat-label">Всего происшествий</div>
                            </div>
                        </div>
                """.trimIndent()
                )

                incidents.forEach { incident ->
                    val photoBase64 = try {
                        val photoFile = File(incident.photoPath)
                        if (photoFile.exists()) {
                            val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                            if (bitmap != null) {
                                val outputStream = ByteArrayOutputStream()
                                bitmap.compress(
                                    android.graphics.Bitmap.CompressFormat.JPEG,
                                    80,
                                    outputStream
                                )
                                val imageBytes = outputStream.toByteArray()
                                Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load incident photo: ${incident.photoPath}", e)
                        null
                    }

                    html.append(
                        """
                        <div class="photo-item">
                            <h3>${incident.incidentType.name.replace('_', ' ')}</h3>
                            <div class="photo-info">
                                <strong>Время:</strong> ${incident.timestamp.substring(11)}<br>
                                <strong>Охранник:</strong> ${incident.employeeName}<br>
                                <strong>Обход:</strong> ${incident.roundId}
                            </div>
                            <div class="action-details">
                                <span><label>Описание:</label> ${incident.description}</span>
                            </div>
                            ${
                            if (photoBase64 != null)
                                "<img src=\"data:image/jpeg;base64,$photoBase64\" alt=\"Фото происшествия\">"
                            else
                                "<p>Фото недоступно</p>"
                        }
                        </div>
                    """.trimIndent()
                    )
                }

                html.append(
                    """
                    </div>
                """.trimIndent()
                )
            }

            // Футер
            html.append(
                """
                        <div class="footer">
                            Сгенерировано: ${dateFormat.format(Date())}<br>
                            Ohrana Security System v1.0
                        </div>
                    </div>
                </body>
                </html>
            """.trimIndent()
            )

            // Сохраняем файл
            val fileName = "shift_report_${shift.id}_${jsonFormat.format(Date())}.html"
            val downloadDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val ohranaDir = File(downloadDir, "Ohrana")
            val reportsDir = File(ohranaDir, "Reports")

            if (!reportsDir.exists()) {
                reportsDir.mkdirs()
            }

            val outputFile = File(reportsDir, fileName)
            FileOutputStream(outputFile).use { fos ->
                fos.write(html.toString().toByteArray())
            }

            Log.i(TAG, "HTML report saved to: ${outputFile.absolutePath}")

            // Добавляем shiftResult в конец HTML комментарием для передачи в email
            html.append("\n<!-- shift_result:$shiftResult -->")
            
            // Перезаписываем файл с shiftResult
            FileOutputStream(outputFile).use { fos ->
                fos.write(html.toString().toByteArray())
            }

            return outputFile.absolutePath to shiftResult

        } catch (e: Exception) {
            Log.e(TAG, "Error generating HTML report: ${e.message}", e)
            return null to null
        }
    }
    
    /**
     * Генерирует минималистичный HTML-файл отчета для смены
     * Возвращает путь к созданному файлу
     */
    fun exportShiftReport(shiftId: String, shiftDatabase: ShiftDatabaseManager): String? {
        Log.d("CloudStorageManager", "exportShiftReport: shiftId=$shiftId")
        // Сначала генерируем JSON
        generateJsonReport(shiftId, shiftDatabase)

        // Потом HTML (для просмотра)
        val result = generateHtmlReport(shiftId, shiftDatabase, null)
        Log.d("CloudStorageManager", "exportShiftReport: result=$result")
        return result?.first
    }

    /**
     * Экспортирует отчет в оба формата и загружает в облако
     * @param shiftId ID смены
     * @param shiftDatabase Менеджер базы данных смен
     * @param uploadToCloud Флаг, нужно ли загружать в Yandex Cloud
     * @param uploadToDisk Флаг, нужно ли загружать в Яндекс.Диск
     * @return Путь к HTML файлу, результаты загрузки в облако и диск
     */
    fun exportShiftReportWithCloud(
        shiftId: String,
        shiftDatabase: ShiftDatabaseManager,
        uploadToCloud: Boolean = false,
        uploadToDisk: Boolean = false
    ): ExportResult {
        // Генерируем JSON
        val jsonPath = generateJsonReport(shiftId, shiftDatabase)

        // Генерируем HTML
        val (htmlPath, _) = generateHtmlReport(shiftId, shiftDatabase, null)

        var jsonUploadResult: Result<String?> = Result.success(null)
        var htmlUploadResult: Result<String?> = Result.success(null)
        var jsonDiskResult: Result<String?> = Result.success(null)
        var htmlDiskResult: Result<String?> = Result.success(null)

        if (uploadToCloud && jsonPath != null) {
            jsonUploadResult = uploadFileToCloud(jsonPath, "shift_${shiftId}_report.json")
        }

        if (uploadToCloud && htmlPath != null) {
            htmlUploadResult = uploadFileToCloud(htmlPath, "shift_${shiftId}_report.html")
        }

        if (uploadToDisk && jsonPath != null) {
            val defaultDiskPath = getDefaultDiskPath() ?: "Ohrana"
            val diskPath = "$defaultDiskPath/shift_${shiftId}_report.json"
            jsonDiskResult = uploadFileToDisk(jsonPath, diskPath)
        }

        if (uploadToDisk && htmlPath != null) {
            val defaultDiskPath = getDefaultDiskPath() ?: "Ohrana"
            val diskPath = "$defaultDiskPath/shift_${shiftId}_report.html"
            htmlDiskResult = uploadFileToDisk(htmlPath, diskPath)
        }

        return ExportResult(
            jsonPath,
            htmlPath,
            jsonUploadResult,
            htmlUploadResult,
            jsonDiskResult,
            htmlDiskResult
        )
    }

    /**
     * Экспортирует отчет в PDF и загружает в Яндекс.Диск
     * @param shiftId ID смены
     * @param shiftDatabase Менеджер базы данных смен
     * @param uploadToDisk Флаг, нужно ли загружать в Яндекс.Диск
     * @param context Контекст Android
     * @return Путь к PDF файлу, результат загрузки в Диск
     */
    fun exportShiftReportToPdfAndDisk(
        shiftId: String,
        shiftDatabase: ShiftDatabaseManager,
        uploadToDisk: Boolean = false,
        context: Context
    ): PdfExportResult {
        // Сначала экспортируем в PDF (генерирует файл и возвращает путь)
        val shift = shiftDatabase.loadAllShifts().find { it.id == shiftId }
        if (shift == null) {
            Log.e(TAG, "exportShiftReportToPdfAndDisk: Shift not found: $shiftId")
            return PdfExportResult(null, Result.failure(Exception("Смена не найдена")))
        }

        val rounds = shiftDatabase.loadAllRounds().filter { it.shiftId == shiftId }
        val logs = shiftDatabase.loadLogsByShift(shiftId)

        // Создаем временный файл для PDF
        val downloadDir =
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val ohranaDir = java.io.File(downloadDir, "Ohrana")
        val pdfDir = java.io.File(ohranaDir, "PDF")

        if (!pdfDir.exists()) {
            pdfDir.mkdirs()
        }

        val fileName = "shift_report_${shift.id}_${
            java.text.SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                java.util.Locale.US
            ).format(java.util.Date())
        }.pdf"
        val pdfPath = java.io.File(pdfDir, fileName).absolutePath

        // Сохраняем PDF в файл
        var pdfGenerated = false
        var pdfErrorMessage: String? = null
        try {
            // Экспортируем в PDF с указанным путем сохранения (без предпросмотра)
            val generatedPath =
                exportToPdf(shift, rounds, logs, context, pdfPath, showPreview = false, shiftDatabase = shiftDatabase)
            if (generatedPath != null) {
                pdfGenerated = true
            } else {
                Log.e(TAG, "exportShiftReportToPdfAndDisk: Failed to generate PDF")
                pdfErrorMessage = "Ошибка генерации PDF"
            }
        } catch (e: Exception) {
            Log.e(TAG, "exportShiftReportToPdfAndDisk: Error generating PDF: ${e.message}", e)
            pdfErrorMessage = "Ошибка генерации PDF: ${e.message}"
        }

        var diskResult: Result<String?> = Result.success(null)

        if (uploadToDisk && pdfGenerated) {
            val defaultDiskPath = getDefaultDiskPath() ?: "Ohrana"
            val diskPath = "$defaultDiskPath/PDF/$fileName"
            diskResult = uploadFileToDisk(pdfPath, diskPath)
        }

        // Возвращаем результат и сообщение об ошибке (если есть)
        if (!pdfGenerated && pdfErrorMessage != null) {
            return PdfExportResult(null, Result.failure(Exception(pdfErrorMessage)))
        }

        return PdfExportResult(pdfPath, diskResult)
    }

    /**
     * Экспортирует отчет в HTML и загружает в Яндекс.Диск без сохранения на телефоне
     * @param shiftId ID смены
     * @param shiftDatabase Менеджер базы данных смен
     * @param uploadToDisk Флаг, нужно ли загружать в Яндекс.Диск
     * @return Результат загрузки (URL на Диске или ошибка)
     */
    fun exportHtmlToDisk(
        shiftId: String,
        shiftDatabase: ShiftDatabaseManager,
        uploadToDisk: Boolean = false,
        context: Context? = null,
        sharedPrefsManager: SharedPrefsManager? = null
    ): HtmlExportResult {
        Log.i(TAG, "exportHtmlToDisk: shiftId=$shiftId, uploadToDisk=$uploadToDisk")
        
        // Если не нужно загружать в Диск, просто генерируем и возвращаем путь
        if (!uploadToDisk) {
            val (htmlPath, _) = generateHtmlReport(shiftId, shiftDatabase, context, sharedPrefsManager)
            Log.i(TAG, "exportHtmlToDisk: Not uploading to disk, htmlPath=$htmlPath")
            return HtmlExportResult(htmlPath, Result.success(null))
        }

        // Загружаем HTML напрямую в Яндекс.Диск без сохранения на телефоне
        val diskResult = uploadHtmlToDiskDirect(shiftId, shiftDatabase, context)
        
        Log.i(TAG, "exportHtmlToDisk: diskResult=$diskResult")
        Log.i(TAG, "exportHtmlToDisk: isSuccess=${diskResult.isSuccess}")
        if (diskResult.isSuccess) {
            val url = diskResult.getOrNull()
            Log.i(TAG, "exportHtmlToDisk: downloadUrl=$url")
        }

        // Возвращаем null как путь (так как файл не сохраняется на телефоне)
        return HtmlExportResult(null, diskResult)
    }

    /**
     * Экспортирует JSON отчет и загружает в Яндекс.Диск без сохранения на телефоне
     * @param shiftId ID смены
     * @param shiftDatabase Менеджер базы данных смен
     * @param uploadToDisk Флаг, нужно ли загружать в Яндекс.Диск
     * @return Результат загрузки (URL на Диске или ошибка)
     */
    fun exportJsonToDisk(
        shiftId: String,
        shiftDatabase: ShiftDatabaseManager,
        uploadToDisk: Boolean = false
    ): JsonExportResult {
        // Если не нужно загружать в Диск, просто генерируем и возвращаем путь
        if (!uploadToDisk) {
            val jsonPath = generateJsonReport(shiftId, shiftDatabase)
            return JsonExportResult(jsonPath, Result.success(null))
        }

        // Для JSON пока используем старый метод с сохранением
        // (можно добавить аналог uploadJsonToDiskDirect() позже)
        val jsonPath = generateJsonReport(shiftId, shiftDatabase)

        if (jsonPath == null) {
            Log.e(TAG, "exportJsonToDisk: Failed to generate JSON report for shift $shiftId")
            return JsonExportResult(null, Result.failure(Exception("Ошибка генерации JSON")))
        }

        var diskResult: Result<String?> = Result.success(null)

        if (uploadToDisk) {
            val defaultDiskPath = getDefaultDiskPath() ?: "Ohrana"
            val fileName = File(jsonPath).name
            val diskPath = "$defaultDiskPath/$fileName"
            diskResult = uploadFileToDisk(jsonPath, diskPath)
        }

        return JsonExportResult(jsonPath, diskResult)
    }
    /**
     * Получает список всех сгенерированных отчетов
     */
    data class PdfExportResult(
        val pdfPath: String?,
        val diskResult: Result<String?>
    ) {
        fun isSuccess(): Boolean {
            return pdfPath != null && diskResult.isSuccess
        }

        fun getDiskErrorMessage(): String {
            if (!diskResult.isSuccess) {
                return "Ошибка загрузки PDF в Диск: ${diskResult.exceptionOrNull()?.message ?: "неизвестная ошибка"}"
            }
            return ""
        }
    }

    /**
     * Результат экспорта HTML отчета и загрузки в Диск
     */
    data class HtmlExportResult(
        val htmlPath: String?,
        val diskResult: Result<String?>
    ) {
        fun isSuccess(): Boolean {
            // Если htmlPath не сохранялся (null), проверяем только успешность загрузки в Диск
            // Если htmlPath сохранялся (не null), проверяем и то и другое
            return if (htmlPath == null) {
                diskResult.isSuccess
            } else {
                diskResult.isSuccess
            }
        }

        fun getDiskErrorMessage(): String {
            if (!diskResult.isSuccess) {
                return "Ошибка загрузки HTML в Диск: ${diskResult.exceptionOrNull()?.message ?: "неизвестная ошибка"}"
            }
            return ""
        }

        fun getDownloadUrl(): String? {
            return diskResult.getOrNull()
        }
    }

    /**
     * Результат экспорта JSON отчета и загрузки в Диск
     */
    data class JsonExportResult(
        val jsonPath: String?,
        val diskResult: Result<String?>
    ) {
        fun isSuccess(): Boolean {
            // Если jsonPath не сохранялся (null), проверяем только успешность загрузки в Диск
            // Если jsonPath сохранялся (не null), проверяем и то и другое
            return if (jsonPath == null) {
                diskResult.isSuccess
            } else {
                diskResult.isSuccess
            }
        }

        fun getDiskErrorMessage(): String {
            if (!diskResult.isSuccess) {
                return "Ошибка загрузки JSON в Диск: ${diskResult.exceptionOrNull()?.message ?: "неизвестная ошибка"}"
            }
            return ""
        }
    }

    /**
     * Результат экспорта отчета
     */
    data class ExportResult(
        val jsonPath: String?,
        val htmlPath: String?,
        val jsonUploadResult: Result<String?>,
        val htmlUploadResult: Result<String?>,
        val jsonDiskResult: Result<String?> = Result.success(null),
        val htmlDiskResult: Result<String?> = Result.success(null)
    ) {
        fun isSuccess(): Boolean {
            return jsonPath != null && htmlPath != null &&
                    jsonUploadResult.isSuccess && htmlUploadResult.isSuccess
        }

        fun isDiskSuccess(): Boolean {
            return jsonDiskResult.isSuccess && htmlDiskResult.isSuccess
        }

        fun getErrorMessage(): String {
            val errors = mutableListOf<String>()
            if (jsonPath == null) errors.add("Ошибка генерации JSON")
            if (htmlPath == null) errors.add("Ошибка генерации HTML")
            if (!jsonUploadResult.isSuccess) errors.add("Ошибка загрузки JSON в облако: ${jsonUploadResult.exceptionOrNull()?.message ?: "неизвестная ошибка"}")
            if (!htmlUploadResult.isSuccess) errors.add("Ошибка загрузки HTML в облако: ${htmlUploadResult.exceptionOrNull()?.message ?: "неизвестная ошибка"}")
            if (!jsonDiskResult.isSuccess) errors.add("Ошибка загрузки JSON в Диск: ${jsonDiskResult.exceptionOrNull()?.message ?: "неизвестная ошибка"}")
            if (!htmlDiskResult.isSuccess) errors.add("Ошибка загрузки HTML в Диск: ${htmlDiskResult.exceptionOrNull()?.message ?: "неизвестная ошибка"}")
            return errors.joinToString("; ")
        }

        fun getDiskErrorMessage(): String {
            val errors = mutableListOf<String>()
            if (!jsonDiskResult.isSuccess) errors.add("Ошибка загрузки JSON в Диск: ${jsonDiskResult.exceptionOrNull()?.message ?: "неизвестная ошибка"}")
            if (!htmlDiskResult.isSuccess) errors.add("Ошибка загрузки HTML в Диск: ${htmlDiskResult.exceptionOrNull()?.message ?: "неизвестная ошибка"}")
            return errors.joinToString("; ")
        }
    }

    /**
     * Получает список всех сгенерированных отчетов
     */
    fun getGeneratedReports(): List<File> {
        val reportsDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Ohrana/Reports"
        )

        if (!reportsDir.exists()) {
            return emptyList()
        }

        return reportsDir.listFiles()?.sortedArray()?.reversed() ?: emptyList()
    }

    /**
     * Генерирует HTML отчет в памяти и загружает его на Яндекс.Диск без сохранения на телефоне
     * @param shiftId ID смены
     * @param shiftDatabase Менеджер базы данных смен
     * @return Результат загрузки (URL на Диске или ошибка)
     */
    fun uploadHtmlToDiskDirect(
        shiftId: String,
        shiftDatabase: ShiftDatabaseManager,
        context: Context? = null,
        sharedPrefsManager: SharedPrefsManager? = null
    ): Result<String> {
        return try {
            val spm = sharedPrefsManager ?: context?.let { SharedPrefsManager(it) }
            // Загружаем будильники для получения времени каждого обхода
            val routeAlarms = spm?.loadRouteAlarms() ?: emptyList()
            val shift = shiftDatabase.loadAllShifts().find { it.id == shiftId }
            if (shift == null) {
                Log.e(TAG, "uploadHtmlToDiskDirect: Shift not found: $shiftId")
                return Result.failure(Exception("Смена не найдена"))
            }

            val rounds = shiftDatabase.loadAllRounds().filter { it.shiftId == shiftId }
            val logs = shiftDatabase.loadLogsByShift(shiftId)

            // Логируем количество логов и примеры значений inputValue
            Log.d(TAG, "uploadHtmlToDiskDirect: shiftId=$shiftId, logsCount=${logs.size}")
            logs.forEach { log ->
                if (log.actionType == "INPUT") {
                    Log.d(TAG, "uploadHtmlToDiskDirect: INPUT log - checkpoint=${log.checkpointName}, inputValue='${log.inputValue}', inputTitle='${log.inputTitle}'")
                }
            }
            
            // Логируем для отладки: все INPUT записи
            logs.filter { it.actionType == "INPUT" }.forEach { log ->
                Log.d(TAG, "uploadHtmlToDiskDirect DEBUG: actionType=${log.actionType}, checkpoint=${log.checkpointName}, inputValue='${log.inputValue}', isNull=${log.inputValue == null}")
            }

            val html = StringBuilder()

            // Извлекаем номер смены из ID (формат: NSDDMMYY_NNN)
            val shiftNumber = run {
                val pattern = java.util.regex.Pattern.compile("NS\\d{6}_(\\d{3})")
                val matcher = pattern.matcher(shiftId)
                if (matcher.find()) {
                    matcher.group(1)?.toInt() ?: 0
                } else {
                    0
                }
            }

            // === СПИСОК ОХРАННИКОВ ===
            val guards = shift.guardList
            val guardsNames =
                if (guards.isNotEmpty()) guards.joinToString(", ") { it.name } else shift.employeeName

            // === СТАТИСТИКА ПО ОБХОДАМ ===
            // Количество пройденных обходов (completed rounds)
            val completedRounds = rounds.filter { it.isCompleted }

            // === СТАТИСТИКА ПО ЧЕКПОИНТАМ ===
            // Всего чекпоинтов (без нарушений)
            val validCheckpoints = logs.filter { it.isSequenceCorrect }

            // === СТАТИСТИКА ПО НАРУШЕНИЯМ ===
            // Всего нарушений (sequence violations from logs)
            val totalViolations = logs.count { !it.isSequenceCorrect }

            // === СОРТИРОВКА ЛОГОВ ПО ВРЕМЕНИ ===
            val sortedLogs = logs.sortedBy { it.timestamp }

            // === СТАТИСТИКА ДЛЯ ОТОБРАЖЕНИЯ (X из Y) ===
            val completedRoundsCount = completedRounds.size
            val totalRoundsCount = spm?.loadRouteRoundsCount() ?: rounds.size
            val validCheckpointsCount = validCheckpoints.size
            val totalCheckpointsCount = logs.size
            // Используем уже сохраненное значение общего количества чекпоинтов из SharedPreferences
            val totalScheduledCheckpoints = spm?.loadTotalScheduledCheckpoints() ?: "NULL"
            
            // === ЛОГИЧЕСКИЙ РЕЗУЛЬТАТ СМЕНЫ ===
            val roundsMatchPlan = completedRoundsCount == totalRoundsCount
            val totalCheckpointsPlan = spm?.loadTotalScheduledCheckpoints()
            val checkpointsMatchPlan = totalCheckpointsPlan != null && totalCheckpointsCount == totalCheckpointsPlan
            val hasViolations = totalViolations > 0
            
            // Проверяем, включен ли строгий контроль
            val strictSequenceEnabled = spm?.isStrictSequenceEnabled() ?: false
            
            val shiftResult = when {
                // Если строгий контроль выключен - свободный режим
                !strictSequenceEnabled -> "Свободный режим"
                roundsMatchPlan && checkpointsMatchPlan && hasViolations -> "ПОЙДЕТ"
                (!roundsMatchPlan || !checkpointsMatchPlan) && hasViolations -> "ЭТО ФИАСКО"
                roundsMatchPlan && checkpointsMatchPlan -> "✓"
                totalCheckpointsPlan == null -> "⚠️ (NULL)"
                else -> "⚠️"
            }
            
            // Загружаем происшествия для статистики
            val incidents = shiftDatabase.loadIncidentsByShift(shiftId)
            val incidentsCount = incidents.size

            // HTML заголовок
            html.append(
                """
                <!DOCTYPE html>
                <html lang="ru">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Отчет о смене</title>
                    <style>
                        body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }
                        .container { max-width: 1200px; margin: 0 auto; background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                        .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 20px; border-radius: 8px; margin-bottom: 20px; }
                        .header h1 { margin: 0 0 10px 0; font-size: 24px; }
                        .header-info { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; }
                        .info-item { background: rgba(255,255,255,0.2); padding: 10px; border-radius: 4px; }
                        .info-item label { font-size: 11px; opacity: 0.9; display: block; }
                        .info-item value { font-size: 13px; font-weight: bold; }
                        .section { margin-bottom: 20px; }
                        .section h2 { background: #e0e0e0; padding: 10px; border-radius: 4px; color: #333; }
                        .round-card { background: #fafafa; border: 1px solid #ddd; border-radius: 8px; padding: 15px; margin-bottom: 15px; }
                        .round-card h3 { margin: 0 0 10px 0; color: #667eea; font-size: 16px; }
                        .round-info { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; margin-bottom: 10px; }
                        .round-info-item { background: #fff; padding: 8px; border-radius: 4px; border: 1px solid #eee; text-align: center; }
                        .round-info-item label { font-size: 11px; color: #666; }
                        .round-info-item value { font-size: 14px; font-weight: bold; color: #333; }
                        .round-duration { color: #667eea; font-weight: bold; }
                        .guards-list { background: #fff3cd; padding: 8px; border-radius: 4px; margin: 10px 0; font-size: 13px; }
                        .stats-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 15px; }
                        .stat-item { text-align: center; padding: 12px; background: #f5f5f5; border-radius: 4px; }
                        .stat-value { font-size: 28px; font-weight: bold; color: #333; }
                        .stat-value.violations { color: #f44336; }
                        .stat-value.success { color: #4caf50; }
                        .stat-label { font-size: 12px; color: #666; }
                        .logs-table { width: 100%; border-collapse: collapse; margin-top: 10px; }
                        .logs-table th, .logs-table td { padding: 10px; text-align: left; border-bottom: 1px solid #eee; }
                        .logs-table th { background: #667eea; color: white; font-size: 12px; }
                        .logs-table tr:hover { background: #f9f9f9; }
                        .violation { color: #f44336; font-weight: bold; }
                        .success { color: #4caf50; }
                        .photo-item { margin: 10px 0; }
                        .photo-item img { max-width: 100%; max-height: 200px; border-radius: 4px; border: 1px solid #ddd; }
                        .photo-info { font-size: 12px; color: #666; margin-top: 5px; }
                        .action-details { background: #f9f9f9; padding: 8px; border-radius: 4px; margin: 5px 0; font-size: 12px; }
                        .action-details span { display: block; margin: 2px 0; }
                        .action-details label { font-weight: bold; color: #667eea; }
                        .footer { margin-top: 20px; text-align: center; color: #666; font-size: 12px; border-top: 1px solid #eee; padding-top: 15px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>ОТЧЕТ О СМЕНЕ</h1>
                            <div class="header-info">
                                <div class="info-item">
                                    <label>Смена №${shiftNumber}</label>
                                    <value>${shift.startTime.substring(0, 10)}</value>
                                </div>
                                <div class="info-item">
                                    <label>Охранники</label>
                                    <value style="font-size: 11px;">$guardsNames</value>
                                </div>
                                <div class="info-item">
                                    <label>Время начала</label>
                                    <value>${shift.startTime.substring(11)}</value>
                                </div>
                                <div class="info-item">
                                    <label>Время окончания</label>
                                    <value>${shift.endTime?.substring(11) ?: "-"}</value>
                                </div>
                            </div>
                        </div>
            """.trimIndent()
            )

            // Статистика
            // Используем уже объявленные переменные: completedRoundsCount, totalRoundsCount,
            // validCheckpointsCount, totalCheckpointsCount, totalScheduledCheckpoints, shiftResult
            
            html.append(
                """
                <div class="section">
                    <h2>📊 Статистика смены</h2>
                    <div class="stats-grid">
                        <div class="stat-item">
                            <div class="stat-value success">$completedRoundsCount из ${context?.let { SharedPrefsManager(it).loadRouteRoundsCount() } ?: rounds.size}</div>
                            <div class="stat-label">Обходов (пройдено)</div>
                        </div>
                        <div class="stat-item">
                            <div class="stat-value success">$validCheckpointsCount из ${context?.let { SharedPrefsManager(it).loadTotalScheduledCheckpoints() } ?: "NULL"}</div>
                            <div class="stat-label">Чекпоинтов (без нарушений)</div>
                        </div>
                        <div class="stat-item">
                            <div class="stat-value violations">$totalViolations</div>
                            <div class="stat-label">Нарушений</div>
                        </div>
                        <div class="stat-item">
                            <div class="stat-value" style="font-size: 32px; color: ${if (shiftResult == "ПОЙДЕТ" || shiftResult == "✓") "#4caf50" else "#f44336"}">$shiftResult</div>
                            <div class="stat-label">Результат</div>
                        </div>
                    </div>
                </div>
            """.trimIndent()
            )

            // Обходы и логи
            rounds.forEach { round ->
                // Получаем время будильника для этого обхода
                val alarmTime = routeAlarms.find { it.id == round.id }?.time ?: "-"
                
                html.append(
                    """
                    <div class="section">
                        <div class="round-card">
                            <h3>🔄 Обход №${round.id} (${alarmTime})</h3>
                            <div class="stats-grid">
                                <div class="stat-item">
                                    <div class="stat-value">${round.routeName ?: "-"}</div>
                                    <div class="stat-label">Маршрут</div>
                                </div>
                                <div class="stat-item">
                                    <div class="stat-value">${getTimePart(round.startTime)}</div>
                                    <div class="stat-label">Время начала</div>
                                </div>
                                <div class="stat-item">
                                    <div class="stat-value">${getTimePart(round.endTime) ?: "-"}</div>
                                    <div class="stat-label">Время окончания</div>
                                </div>
                                <div class="stat-item">
                                    <div class="stat-value round-duration">${calculateRoundDuration(round.startTime, round.endTime)}</div>
                                    <div class="stat-label">Длительность</div>
                                </div>
                            </div>
                """.trimIndent()
                )

                val roundLogs = logs.filter { it.roundId == round.id }
                // Сортируем логи по времени
                val sortedRoundLogs = roundLogs.sortedBy { it.timestamp }

                // Получаем охранников, участвовавших в этом обходе
                val roundGuards = sortedRoundLogs.map { it.employeeName }.distinct()

                if (sortedRoundLogs.isNotEmpty()) {
                    html.append(
                        """
                        <table class="logs-table">
                            <thead>
                                <tr>
                                    <th>Чекпоинт</th>
                                    <th>Время</th>
                                    <th>Охранник</th>
                                    <th>Статус</th>
                                    <th>Действие</th>
                                </tr>
                            </thead>
                            <tbody>
                    """.trimIndent()
                    )

                    sortedRoundLogs.forEach { log ->
                        val statusClass = if (log.isSequenceCorrect) "success" else "violation"
                        val statusIcon = if (log.isSequenceCorrect) "✓" else "⚠️"
                        val statusText = when (log.actionType) {
                            "CHECKPOINT" -> "Пройден"
                            "QUESTION" -> "Вопрос"
                            "INPUT" -> "Ввод"
                            "PHOTO" -> "Фото"
                            else -> log.actionType
                        }

                        // Формируем дополнительную информацию по действиям
                        val actionDetails = buildString {
                            log.questionText?.let { append("<span><label>Вопрос:</label> ${it}</span>") }
                            log.answer?.let { append("<span><label>Ответ:</label> ${it}</span>") }
                            log.inputTitle?.let { append("<span><label>Поле:</label> ${it}</span>") }
                            log.inputValue?.let { append("<span><label>Ввод:</label> ${it}</span>") }
                        }

                        html.append(
                            """
                            <tr>
                                <td>${log.checkpointName}</td>
                                <td>${log.timestamp.substring(11)}</td>
                                <td>${log.employeeName}</td>
                                <td class="$statusClass">$statusIcon ${if (log.isSequenceCorrect) "OK" else "НАРУШЕНИЕ"}</td>
                                <td>
                                    $statusText
                                    ${if (actionDetails.isNotBlank()) "<div class=\"action-details\">$actionDetails</div>" else ""}
                                </td>
                            </tr>
                        """.trimIndent()
                        )
                    }

                    html.append(
                        """
                            </tbody>
                        </table>
                    """.trimIndent()
                    )
                }

                html.append(
                    """
                        </div>
                    </div>
                """.trimIndent()
                )
            }

            // Фотографии за смену
            val photos = logs.filter { it.photoPath != null }
            if (photos.isNotEmpty()) {
                html.append(
                    """
                    <div class="section">
                        <h2>📷 Фотографии</h2>
                """.trimIndent()
                )

                photos.forEach { photoLog ->
                    // Получаем имя файла из пути
                    val photoFileName = photoLog.photoPath?.substringAfterLast("/") ?: "photo.jpg"

                    // Пытаемся загрузить фото и встроить его как base64
                    val photoBase64 = try {
                        val photoPath = photoLog.photoPath
                        if (photoPath != null) {
                            val photoFile = File(photoPath)
                            if (photoFile.exists()) {
                                val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                                if (bitmap != null) {
                                    val outputStream = ByteArrayOutputStream()
                                    bitmap.compress(
                                        android.graphics.Bitmap.CompressFormat.JPEG,
                                        80,
                                        outputStream
                                    )
                                    val imageBytes = outputStream.toByteArray()
                                    Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                                } else {
                                    null
                                }
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load photo: ${photoLog.photoPath}", e)
                        null
                    }

                    html.append(
                        """
                        <div class="photo-item">
                            <div class="photo-info">
                                <strong>Обход:</strong> ${photoLog.roundId}<br>
                                <strong>Чекпоинт:</strong> ${photoLog.checkpointName}<br>
                                <strong>Время:</strong> ${photoLog.timestamp.substring(11)}<br>
                                <strong>Охранник:</strong> ${photoLog.employeeName}
                            </div>
                            ${
                            if (photoBase64 != null)
                                "<img src=\"data:image/jpeg;base64,$photoBase64\" alt=\"Фото смены\">"
                            else
                                "<p>Фото недоступно</p>"
                        }
                        </div>
                    """.trimIndent()
                    )
                }

                html.append(
                    """
                    </div>
                """.trimIndent()
                )
            }

            // Футер
            html.append(
                """
                        <div class="footer">
                            Сгенерировано: ${dateFormat.format(Date())}<br>
                            Ohrana Security System v1.0
                        </div>
                    </div>
                </body>
                </html>
            """.trimIndent()
            )

            // Получаем токен для загрузки
            var token = getDiskToken()
            if (token == null) {
                token = getDefaultDiskToken()
            }
            if (token == null) {
                Log.e(TAG, "uploadHtmlToDiskDirect: OAuth token is null")
                return Result.failure(Exception("OAuth token not found"))
            }

            // Создаем уникальное имя файла
            val timestamp = System.currentTimeMillis()
            val fileName = "shift_${shift.id}_report_${timestamp}.html"
            val defaultDiskPath = getDefaultDiskPath() ?: "Ohrana"
            val remotePath = "$defaultDiskPath/$fileName"

            Log.i(TAG, "uploadHtmlToDiskDirect: Uploading HTML directly to $remotePath")

            // Создаем родительские директории, если они не существуют
            val parentPath = remotePath.substringBeforeLast("/")
            if (parentPath != remotePath) {
                Log.i(
                    TAG,
                    "uploadHtmlToDiskDirect: Creating parent directories for path: $parentPath"
                )

                val pathParts = parentPath.split("/").filter { it.isNotEmpty() }
                var currentPath = ""

                for (part in pathParts) {
                    currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"

                    if (!checkDirectoryExists(currentPath, token)) {
                        Log.i(TAG, "uploadHtmlToDiskDirect: Creating directory: $currentPath")
                        createSingleDirectory(currentPath, token)
                    } else {
                        Log.i(TAG, "uploadHtmlToDiskDirect: Directory already exists: $currentPath")
                    }
                }
            }

            // Получаем ссылку для загрузки
            val urlString =
                "${YANDEX_DISK_API_HOST}${YANDEX_DISK_API_V1_PATH}/upload?path=$remotePath&overwrite=true"

            Log.i(TAG, "uploadHtmlToDiskDirect: Getting upload link: $urlString")

            // Используем OkHttp для запроса (GET для получения ссылки)
            val request = okhttp3.Request.Builder()
                .url(urlString)
                .get()
                .addHeader("Authorization", "OAuth $token")
                .addHeader("Accept", "application/json")
                .build()

            // Выполняем запрос
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                Log.i(TAG, "uploadHtmlToDiskDirect: Response code: ${response.code}")

                if (response.isSuccessful) {
                    val json = JSONObject(responseBody)
                    val href = json.getString("href")
                    Log.i(TAG, "uploadHtmlToDiskDirect: Upload link href: $href")

                    // Загружаем HTML напрямую в памяти (без сохранения на диск)
                    val uploadFileRequestBody = okhttp3.RequestBody.create(
                        "text/html; charset=UTF-8".toMediaType(),
                        html.toString().toByteArray(Charsets.UTF_8)
                    )
                    val uploadRequest = okhttp3.Request.Builder()
                        .url(href)
                        .put(uploadFileRequestBody)
                        .build()

                    client.newCall(uploadRequest).execute().use { uploadResponse ->
                        val uploadResponseBody = uploadResponse.body?.string() ?: ""

                        Log.i(
                            TAG,
                            "uploadHtmlToDiskDirect: File upload response code: ${uploadResponse.code}"
                        )

                        if (uploadResponse.isSuccessful) {
                            Log.i(TAG, "uploadHtmlToDiskDirect: File uploaded successfully, now getting public URL")
                            
                            // Небольшая задержка для индексации файла
                            Thread.sleep(1000)
                            
                            // Публикуем файл для получения публичного URL
                            val publishResult = publishFileToDisk(remotePath)
                            if (publishResult.isSuccess) {
                                Log.i(TAG, "uploadHtmlToDiskDirect: File published successfully")
                                
                                // Получаем public_url напрямую из результата публикации
                                val publishedUrl = publishResult.getOrNull() ?: ""
                                
                                // Если ссылка не публичная (downloader.disk.yandex.ru), пробуем получить public_url через getPublicUrlForDisk
                                if (publishedUrl.contains("downloader.disk.yandex.ru")) {
                                    Log.w(TAG, "uploadHtmlToDiskDirect: Got download link instead of public URL, trying to get public URL")
                                    val publicUrlResult = getPublicUrlForDisk(remotePath)
                                    if (publicUrlResult.isSuccess) {
                                        val publicUrl = publicUrlResult.getOrNull() ?: ""
                                        Log.i(TAG, "uploadHtmlToDiskDirect: Public URL = '$publicUrl'")
                                        Result.success(publicUrl)
                                    } else {
                                        Log.e(TAG, "uploadHtmlToDiskDirect: Failed to get public URL")
                                        Result.failure(Exception("Ошибка получения публичного URL: ${publicUrlResult.exceptionOrNull()?.message}"))
                                    }
                                } else {
                                    Log.i(TAG, "uploadHtmlToDiskDirect: Public URL = '$publishedUrl'")
                                    Result.success(publishedUrl)
                                }
                            } else {
                                Log.e(TAG, "uploadHtmlToDiskDirect: Failed to publish file")
                                Result.failure(Exception("Ошибка публикации файла: ${publishResult.exceptionOrNull()?.message}"))
                            }
                        } else {
                            Log.e(
                                TAG,
                                "uploadHtmlToDiskDirect: File upload failed: ${uploadResponse.code}"
                            )
                            Result.failure(Exception("Ошибка загрузки файла: ${uploadResponse.code}"))
                        }
                    }
                } else {
                    Log.e(
                        TAG,
                        "uploadHtmlToDiskDirect: Failed to get upload link: ${response.code}"
                    )
                    Result.failure(Exception("Ошибка получения ссылки для загрузки: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Неизвестная ошибка"
            Log.e(TAG, "uploadHtmlToDiskDirect: Upload failed: $errorMsg", e)
            Log.e(TAG, "uploadHtmlToDiskDirect: Stack trace:", e)
            Result.failure(Exception("Ошибка загрузки HTML в Яндекс.Диск: $errorMsg"))
        }
    }

    /**
     * Очищает старые отчеты (старше указанного количества дней)
     */
    fun clearOldReports(days: Int) {        val reportsDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Ohrana/Reports"
        )

        if (!reportsDir.exists()) {
            return
        }

        val cutoffDate = System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000)

        reportsDir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoffDate) {
                file.delete()
                Log.i(TAG, "Deleted old report: ${file.name}")
            }
        }
    }

}