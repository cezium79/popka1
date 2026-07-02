package com.example.ohrana

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.ohrana.ShiftDatabaseManager
import com.example.ohrana.ShiftLogEntry
import com.example.ohrana.ShiftRecord
import com.example.ohrana.RoundRecord
import com.example.ohrana.SequenceViolation
import com.example.ohrana.AdminReport
import com.example.ohrana.GuardReport
import com.example.ohrana.OwnerReport
import com.example.ohrana.SimpleLogEntry

// Модель для сохранения сканирований
data class QrScanRecord(val employeeName: String, val time: String, val qrContent: String)

// Модель маршрута
data class Route(
    val id: String,           // Уникальный ID маршрута
    val name: String,         // Отображаемое имя маршрута
    val checkpointIds: List<String>,  // Список ID чекпоинтов в маршруте
    val isActive: Boolean = false     // Флаг активного маршрута
)

class SharedPrefsManager(private val context: Context) {
    val prefs = context.getSharedPreferences("ohrana_prefs", Context.MODE_PRIVATE)
    
    // Инициализируем базу данных обходов
    val shiftDatabase = ShiftDatabaseManager(context)

    // ОБНОВЛЕНО: Новый, понятный формат даты для логов и смен
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US)
    // --- МЕТОДЫ УПРАВЛЕНИЯ СМЕНОЙ ОХРАННИКА ---
// Включен ли строгий контроль последовательности сканирования (true/false)
    fun isStrictSequenceEnabled(): Boolean {
        return prefs.getBoolean("strict_sequence_enabled", false)
    }

    fun setStrictSequenceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("strict_sequence_enabled", enabled).apply()
    }

    // Сохраняет, был ли контроль последовательности включён в этой смене
    fun saveSequenceControlStatus(isEnabled: Boolean) {
        prefs.edit().putBoolean("sequence_control_was_enabled", isEnabled).apply()
    }

    // Получает статус контроля последовательности для отчета
    fun getSequenceControlStatus(): String {
        val wasEnabled = prefs.getBoolean("sequence_control_was_enabled", false)
        return if (wasEnabled) "Контроль последовательности: ВКЛ" else "Контроль последовательности: ВЫКЛ"
    }

    // Сохранить текущий рабочий маршрут (список названий точек через запятую)
    fun saveCurrentRouteCheckpoints(points: List<String>) {
        prefs.edit().putString("active_route_points", points.joinToString(",")).apply()
    }

    // Получить список точек текущего активного маршрута
    fun getActiveRouteCheckpoints(): List<String> {
        val saved = prefs.getString("active_route_points", "") ?: ""
        if (saved.isEmpty()) {
            // Дефолтный тестовый маршрут, если админ ничего не настроил в "routes"
            return listOf("Точка 1", "Точка 2", "Точка 3")
        }
        return saved.split(",")
    }

    // Получить индекс чекпоинта, который мы ждем от сканера (начиная с 0)
    fun getCurrentCheckpointIndex(): Int {
        return prefs.getInt("active_route_current_index", 0)
    }

    fun updateCurrentCheckpointIndex(index: Int) {
        prefs.edit().putInt("active_route_current_index", index).apply()
    }

    fun resetRouteProgress() {
        prefs.edit().putInt("active_route_current_index", 0).apply()
    }

    // Начать новую смену (сохраняем имя и время старта)
    fun startNewShift(employeeName: String, strictSequenceEnabled: Boolean) {
        val currentTime = dateFormat.format(Date()) // Генерирует строку вида "27.06.2026 15:30:00"
        
        // Очищаем shiftLogs при старте новой смены
        QrHandler.clearShiftLogs()
        // Сбрасываем прогресс маршрута при старте новой смены
        resetRouteProgress()
        // Сбрасываем статус всех обходов при старте новой смены
        resetAllRounds()
        
        // Создаем новую запись смены в базе данных
        val shiftId = shiftDatabase.startNewShift(employeeName, strictSequenceEnabled)
        // Сохраняем ID активной смены в SharedPreferences
        prefs.edit().putString("active_shift_id", shiftId).apply()
        
        prefs.edit().apply {
            putString("active_shift_employee", employeeName)
            putString("active_shift_start_time", currentTime)
            putBoolean("active_shift_is_running", true)
            // Сохраняем статус контроля последовательности при старте смены
            putBoolean("sequence_control_was_enabled", strictSequenceEnabled)
            apply()
        }
    }
    // Просто возвращает true, если смена запущена на устройстве, и false, если закрыта
    fun isShiftActive(): Boolean {
        return prefs.getBoolean("active_shift_is_running", false)
    }


    // Проверить, идет ли сейчас активная смена у конкретного сотрудника
    fun isShiftActiveFor(employeeName: String): Boolean {
        val savedName = prefs.getString("active_shift_employee", "") ?: ""
        val isRunning = prefs.getBoolean("active_shift_is_running", false)
        return isRunning && savedName == employeeName
    }

    // Получить имя сотрудника, у которого сейчас открыта смена
    fun getActiveShiftEmployeeName(): String {
        val isRunning = prefs.getBoolean("active_shift_is_running", false)
        return if (isRunning) {
            prefs.getString("active_shift_employee", "") ?: ""
        } else {
            ""
        }
    }

    // Проверить, есть ли активная смена у КОГО-ЛИБО (для блокировки кнопки "Начать смену")
    fun isAnyShiftActive(): Boolean {
        return prefs.getBoolean("active_shift_is_running", false)
    }

    // Проверить, открыта ли смена у КОГО-ТО ДРУГОГО (не у этого сотрудника)
    fun isShiftActiveByOther(employeeName: String): Boolean {
        val activeEmployee = getActiveShiftEmployeeName()
        return activeEmployee.isNotEmpty() && activeEmployee != employeeName
    }

    // Получить имя сотрудника, у которого открыта смена (если открыта другим)
    fun getActiveShiftEmployeeNameByOther(employeeName: String): String {
        val activeEmployee = getActiveShiftEmployeeName()
        return if (activeEmployee.isNotEmpty() && activeEmployee != employeeName) {
            activeEmployee
        } else {
            ""
        }
    }

    // Получить время начала текущей смены
    fun getShiftStartTime(): String = prefs.getString("active_shift_start_time", "-") ?: "-"





    // Дополнительный метод (пригодится для вывода на экран отчетов)
    fun getShiftEndTime(): String = prefs.getString("active_shift_end_time", "-") ?: "-"

    // Закрыть смену (с фиксацией времени закрытия)
    fun closeCurrentShift() {
        val endTime = dateFormat.format(Date()) // Генерирует время закрытия, например "27.06.2026 10:15:00"
        
        // Очищаем shiftLogs при закрытии смены
        QrHandler.clearShiftLogs()
        // Сбрасываем прогресс маршрута при закрытии смены
        resetRouteProgress()
        
        // Получаем ID активной смены
        val activeShiftId = prefs.getString("active_shift_id", null)
        activeShiftId?.let { shiftDatabase.closeShift(it) }
        // Удаляем ID активной смены
        prefs.edit().remove("active_shift_id").apply()

        prefs.edit().apply {
            putBoolean("active_shift_is_running", false)
            putString("active_shift_end_time", endTime) // ФИКСАЦИЯ: Сохраняем время закрытия

            // Удаляем данные сотрудника, так как смена завершена
            remove("active_shift_employee")
            remove("active_shift_start_time")
            apply()
        }
    }


// --- УПРАВЛЕНИЕ АКТИВНЫМ ОБХОДОМ ---

    // Запустить конкретный обход по его номеру (индексу)
    fun startRound(roundIndex: Int, startTime: String, routeId: String? = null) {
        prefs.edit().apply {
            putInt("active_shift_current_round_index", roundIndex)
            putString("round_${roundIndex}_start_time", startTime)
            putString("round_${roundIndex}_scanned_points", "") // Очищаем отсканированные точки для этого обхода
            putString("round_${roundIndex}_route_id", routeId ?: "")
            putBoolean("round_${roundIndex}_is_completed", false)
            // Сбрасываем индекс чекпоинта при запуске нового обхода
            putInt("active_route_current_index", 0)
            apply()
        }
        
        // Запускаем обход в новой базе данных
        val route = routeId?.let { getRouteById(it) }
        val routeName = route?.name ?: "Маршрут не найден"
        val checkpointIds = route?.checkpointIds ?: emptyList()
        val activeShiftId = prefs.getString("active_shift_id", "unknown_shift") ?: "unknown_shift"
        shiftDatabase.startRound(roundIndex, activeShiftId, routeId, routeName, checkpointIds.size)
    }

    // Завершить конкретный обход по его номеру (индексу)
    fun completeRound(roundIndex: Int, endTime: String) {
        prefs.edit().apply {
            putBoolean("round_${roundIndex}_is_completed", true)
            putString("round_${roundIndex}_end_time", endTime)
            putInt("active_shift_current_round_index", -1)
            // Сбрасываем индекс чекпоинта при завершении обхода
            putInt("active_route_current_index", 0)
            apply()
        }
        
        // Завершаем обход в новой базе данных
        shiftDatabase.completeRound(roundIndex, endTime)
    }

    // Получить индекс обхода, который выполняется прямо сейчас (-1 означает, что никакой обход не запущен)
    fun getActiveRoundIndex(): Int = prefs.getInt("active_shift_current_round_index", -1)
    
    // Получить ID маршрута активного обхода
    fun getActiveRoundRouteId(): String? {
        val activeRoundIndex = getActiveRoundIndex()
        if (activeRoundIndex == -1) return null
        return getRoundRouteId(activeRoundIndex)
    }

    // Получить время начала конкретного обхода
    fun getRoundStartTime(roundIndex: Int): String = prefs.getString("round_${roundIndex}_start_time", "-") ?: "-"

    // Получить время завершения конкретного обхода
    fun getRoundEndTime(roundIndex: Int): String = prefs.getString("round_${roundIndex}_end_time", "-") ?: "-"

    // Получить ID маршрута для конкретного обхода
    fun getRoundRouteId(roundIndex: Int): String? {
        val routeId = prefs.getString("round_${roundIndex}_route_id", null)
        return if (routeId.isNullOrEmpty()) null else routeId
    }
    
    // Получить имя следующего чекпоинта для активного обхода
    fun getNextCheckpointName(): String {
        val activeRoundIndex = getActiveRoundIndex()
        if (activeRoundIndex == -1) return "Свободный обход"
        
        val routeId = getRoundRouteId(activeRoundIndex)
        if (routeId.isNullOrEmpty()) return "Свободный обход"
        
        val route = getRouteById(routeId)
        if (route == null || route.checkpointIds.isEmpty()) return "Свободный обход"
        
        // Получаем индекс текущего чекпоинта
        val checkpointIndex = getCurrentCheckpointIndex()
        
        // Если индекс вышел за границы, показываем сообщение о завершении
        if (checkpointIndex >= route.checkpointIds.size) {
            return "Обход завершен"
        }
        
        val checkpointId = route.checkpointIds[checkpointIndex]
        val checkpoint = getCheckpointById(checkpointId)
        return checkpoint?.name ?: "Чекпоинт ${checkpointIndex + 1}"
    }

    // Проверить, завершен ли обход
    fun isRoundCompleted(roundIndex: Int): Boolean = prefs.getBoolean("round_${roundIndex}_is_completed", false)
    
    // Сбросить статус всех обходов
    fun resetAllRounds() {
        val routeAlarms = loadRouteAlarms()
        prefs.edit().apply {
            // Сбрасываем статус каждого обхода
            routeAlarms.forEach { alarm ->
                putBoolean("round_${alarm.id}_is_completed", false)
                putString("round_${alarm.id}_end_time", "-")
                remove("round_${alarm.id}_scanned_points")
            }
            // Сбрасываем индекс активного обхода
            putInt("active_shift_current_round_index", -1)
            apply()
        }
    }

    // --- СОХРАНЕНИЕ НАСТРОЕК МАРШРУТА И РАСПИСАНИЯ ---
    fun saveRouteSettings(roundsCount: Int, times: List<String>, tolerance: String, checkpoints: List<String>) {
        prefs.edit().apply {
            putInt("route_rounds_count", roundsCount)
            putString("route_times", times.joinToString(","))
            putString("route_tolerance", tolerance)
            putString("route_checkpoints", checkpoints.joinToString(","))
            commit() // Используем commit() для синхронного сохранения
        }
        // Проверяем, что данные действительно сохранены
        val savedRounds = prefs.getInt("route_rounds_count", -1)
        val savedTimes = prefs.getString("route_times", null)
        Log.d("SharedPrefsManager", "Verification - rounds=$savedRounds, times=$savedTimes")
        Log.d("SharedPrefsManager", "Route settings saved successfully: rounds=${savedRounds == roundsCount}")
    }

    /**
     * Сохраняет максимальную длительность обхода
     */
    fun saveMaxRoundDuration(duration: String) {
        Log.d("SharedPrefsManager", "Saving max round duration: $duration")
        prefs.edit().putString("max_round_duration_key", duration).commit()
        
        // Проверка
        val saved = prefs.getString("max_round_duration_key", null)
        Log.d("SharedPrefsManager", "Max round duration saved successfully: ${saved == duration}")
    }

    fun loadRouteRoundsCount(): Int = prefs.getInt("route_rounds_count", 3)

    fun loadRouteTimes(): List<String> {
        val raw = prefs.getString("route_times", "") ?: ""
        if (raw.isEmpty()) return listOf("08:00", "14:00", "20:00") // Базовые значения
        return raw.split(",")
    }

    fun loadRouteTolerance(): String = prefs.getString("route_tolerance", "15") ?: "15"

    /**
     * Загружает максимальную длительность обхода
     */
    fun loadMaxRoundDuration(): String = prefs.getString("max_round_duration_key", "30") ?: "30"

    fun loadRouteCheckpoints(): List<String> {
        val raw = prefs.getString("route_checkpoints", "") ?: ""
        if (raw.isEmpty()) return listOf("Точка_Вход", "Точка_Склад_1", "Точка_Забор") // Базовые значения
        return raw.split(",")
    }

    /**
     * Сохраняет только список чекпоинтов маршрута
     * Используется в MarshrutiScreen для сохранения изменений без перезаписи других настроек
     */
    fun saveRouteCheckpoints(checkpoints: List<String>) {
        Log.d("SharedPrefsManager", "Saving route checkpoints: $checkpoints")
        prefs.edit().putString("route_checkpoints", checkpoints.joinToString(",")).commit()
        
        // Проверка
        val saved = prefs.getString("route_checkpoints", null)
        Log.d("SharedPrefsManager", "Route checkpoints saved successfully: ${saved == checkpoints.joinToString(",")}")
    }


    fun saveEmployees(list: List<Employee>) {
        val localPrefs = context.getSharedPreferences("OhranaPrefs", Context.MODE_PRIVATE)
        val jsonArray = org.json.JSONArray()
        list.forEach { employee ->
            val jsonObject = org.json.JSONObject()
            jsonObject.put("id", employee.id)
            jsonObject.put("name", employee.name)
            jsonObject.put("role", employee.role)
            employee.nfcId?.let { jsonObject.put("nfcId", it) }
            jsonArray.put(jsonObject)
        }
        localPrefs.edit().putString("employees_list", jsonArray.toString()).apply()
    }

    /**
     * Загружает список сотрудников из SharedPreferences
     */
    fun loadEmployees(): List<Employee> {
        val localPrefs = context.getSharedPreferences("OhranaPrefs", Context.MODE_PRIVATE)
        val jsonString = localPrefs.getString("employees_list", null)
        if (jsonString.isNullOrBlank()) return emptyList()
        
        return try {
            val jsonArray = org.json.JSONArray(jsonString)
            List(jsonArray.length()) { index ->
                val jsonObject = jsonArray.getJSONObject(index)
                Employee(
                    id = jsonObject.optString("id", ""),
                    name = jsonObject.optString("name", ""),
                    role = jsonObject.optString("role", ""),
                    nfcId = jsonObject.optString("nfcId", null)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // Сохранение логов сканирования в SharedPrefs (в виде простой строки для упрощения)
    fun saveScanResult(employeeName: String, qrContent: String) {
        val currentTime = dateFormat.format(Date())
        val currentLogs = prefs.getString("qr_logs", "") ?: ""

        // Очищаем контент от точек с запятой и переносов, чтобы не ломать структуру CSV таблицы
        val safeContent = qrContent.replace(";", " ").replace("\n", " ")
        val logLine = "$employeeName;$currentTime;$safeContent"

        val newLogs = if (currentLogs.isEmpty()) {
            logLine
        } else {
            "$currentLogs\n$logLine"
        }
        prefs.edit().putString("qr_logs", newLogs).apply()
    }

    // Получение всех логов
    fun getScanLogs(): List<QrScanRecord> {
        val logsString = prefs.getString("qr_logs", "") ?: ""
        if (logsString.isEmpty()) return emptyList()

        return logsString.split("\n").mapNotNull { line ->
            val parts = line.split(";")
            if (parts.size == 3) QrScanRecord(parts[0], parts[1], parts[2]) else null
        }
    }

    // Очистка логов после генерации отчета
    fun clearScanLogs() {
        prefs.edit().remove("qr_logs").apply()
    }

    // Генерация CSV отчета для Excel
    fun generateExcelReport(employeeName: String): File? {
        val logs = getScanLogs()

        // 🗓️ Понятный формат: день.месяц.yy_HH-mm (например, 25.06.26_14-30)
        val fileTimestamp = SimpleDateFormat("dd.MM.yy_HH-mm", Locale.US).format(Date())

        // Шаблон имени файла: Фамилия_Имя_ДАТА_ВРЕМЯ.csv
        val fileName = "${employeeName.replace(" ", "_")}_$fileTimestamp.csv"

        val reportsDir = File(context.filesDir, "reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()

        val file = File(reportsDir, fileName)

        try {
            // Открываем поток записи строго в кодировке UTF-8
            file.bufferedWriter(charset = Charsets.UTF_8).use { writer ->

                // Записываем правильный UTF-8 BOM маркер, чтобы Excel сразу понял русский язык
                writer.write("\uFEFF")

                // Заголовки столбцов
                writer.write("Сотрудник;Дата и Время;Данные QR-кода\n")

                // Запись статуса контроля последовательности
                val sequenceControlStatus = getSequenceControlStatus()
                writer.write("; $sequenceControlStatus\n")

                // Запись накопленных данных
                for (log in logs) {
                    writer.write("${log.employeeName};${log.time};${log.qrContent}\n")
                }
            }
            // После успешной записи отчета очищаем текущие логи (включая shiftLogs)
            clearScanLogs()
            QrHandler.clearShiftLogs()
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    // Получить список всех файлов отчетов
    fun getReportsList(): List<File> {
        val reportsDir = File(context.filesDir, "reports")
        return reportsDir.listFiles()?.filter { it.isFile && it.extension == "csv" }?.toList() ?: emptyList()
    }

    // ==================================================
    // ⏰ МЕТОДЫ ДЛЯ РАБОТЫ С БУДИЛЬНИКАМИ (RouteAlarm)
    // ==================================================

    /**
     * Сохраняет список настроенных будильников в память устройства
     */
    fun saveRouteAlarms(alarms: List<RouteAlarm>) {
        // Удаление дубликатов по ID (сохраняем только первый встреченный)
        val seenIds = mutableSetOf<Int>()
        val uniqueAlarms = alarms.filter { alarm ->
            if (seenIds.contains(alarm.id)) {
                Log.w("SharedPrefsManager", "saveRouteAlarms: Skipping duplicate alarm with id=${alarm.id}")
                false
            } else {
                seenIds.add(alarm.id)
                true
            }
        }
        
        val localPrefs = context.getSharedPreferences("OhranaPrefs", Context.MODE_PRIVATE)
        // Используем JSON для надежного сохранения порядка
        val jsonArray = org.json.JSONArray()
        uniqueAlarms.forEach { alarm ->
            val jsonObject = org.json.JSONObject()
            jsonObject.put("id", alarm.id)
            jsonObject.put("time", alarm.time)
            jsonObject.put("enabled", alarm.isEnabled)
            jsonObject.put("routeId", alarm.routeId ?: "")
            jsonArray.put(jsonObject)
        }
        val jsonString = jsonArray.toString()
        Log.d("SharedPrefsManager", "Saving ${uniqueAlarms.size} alarms (after deduplication): $jsonString")
        localPrefs.edit().putString("route_alarms_json", jsonString).commit()
        // Проверяем, что данные действительно сохранены
        val savedString = localPrefs.getString("route_alarms_json", null)
        Log.d("SharedPrefsManager", "Verification - Saved string: $savedString")
        Log.d("SharedPrefsManager", "Alarms saved successfully: ${savedString == jsonString}")
    }

    /**
     * Загружает сохраненный список будильников. Если данных нет — вернет пустой список
     */
    fun loadRouteAlarms(): List<RouteAlarm> {
        val localPrefs = context.getSharedPreferences("OhranaPrefs", Context.MODE_PRIVATE)
        val jsonString = localPrefs.getString("route_alarms_json", null)
        Log.d("SharedPrefsManager", "loadRouteAlarms: jsonString=$jsonString")
        if (jsonString.isNullOrBlank()) return emptyList()
        
        return try {
            val jsonArray = org.json.JSONArray(jsonString)
            val alarms = mutableListOf<RouteAlarm>()
            val seenIds = mutableSetOf<Int>() // Для отслеживания дубликатов
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val id = jsonObject.optInt("id", 0)
                // Пропускаем дубликаты
                if (seenIds.contains(id)) {
                    Log.w("SharedPrefsManager", "loadRouteAlarms: Skipping duplicate alarm with id=$id")
                    continue
                }
                seenIds.add(id)
                alarms.add(RouteAlarm(
                    id = id,
                    time = jsonObject.optString("time", "08:00"),
                    isEnabled = jsonObject.optBoolean("enabled", true),
                    routeId = jsonObject.optString("routeId", null)
                ))
            }
            Log.d("SharedPrefsManager", "Loaded ${alarms.size} alarms (after deduplication): $alarms")
            alarms
        } catch (e: Exception) {
            Log.e("SharedPrefsManager", "Error loading alarms: ${e.message}")
            emptyList()
        }
    }

    // ==================================================
    // 🖼️ МЕТОДЫ ДЛЯ РАБОТЫ С КАРТИНКАМИ ПРИБОРОВ
    // ==================================================

    /**
     * Сохраняет URI картинки прибора для чекпоинта по его ID
     * Формат ключа: checkpoint_image_uri_<id>
     */
    fun saveCheckpointImageUri(checkpointId: String, uri: String) {
        val localPrefs = context.getSharedPreferences("OhranaPrefs", Context.MODE_PRIVATE)
        localPrefs.edit().putString("checkpoint_image_uri_$checkpointId", uri).apply()
    }

    /**
     * Получает URI картинки прибора для чекпоинта по его ID
     */
    fun getCheckpointImageUri(checkpointId: String): String? {
        val localPrefs = context.getSharedPreferences("OhranaPrefs", Context.MODE_PRIVATE)
        return localPrefs.getString("checkpoint_image_uri_$checkpointId", null)
    }

    /**
     * Удаляет сохраненную картинку прибора по ID чекпоинта
     */
    fun clearCheckpointImageUri(checkpointId: String) {
        val localPrefs = context.getSharedPreferences("OhranaPrefs", Context.MODE_PRIVATE)
        localPrefs.edit().remove("checkpoint_image_uri_$checkpointId").apply()
    }

    /**
     * Получает все сохраненные URI картинок приборов
     * Возвращает Map<checkpointId, uri>
     */
    fun getAllCheckpointImageUris(): Map<String, String> {
        val localPrefs = context.getSharedPreferences("OhranaPrefs", Context.MODE_PRIVATE)
        val all = localPrefs.all
        return all.filterKeys { it.startsWith("checkpoint_image_uri_") }
            .mapKeys { it.key.removePrefix("checkpoint_image_uri_") }
            .mapValues { it.value as String }
    }

    // ==================================================
    // 🗃️ МЕТОДЫ ДЛЯ РАБОТЫ С ЧЕКПОИНТАМИ (БАЗА ДАННЫХ)
    // ==================================================

    /**
     * Сохраняет список чекпоинтов в SharedPreferences
     * Формат ключа: checkpoints_list (JSON-строка)
     */
    fun saveCheckpoints(checkpoints: List<Checkpoint>) {
        val localPrefs = context.getSharedPreferences("OhranaPrefs", Context.MODE_PRIVATE)
        val jsonArray = org.json.JSONArray()
        checkpoints.forEach { checkpoint ->
            val jsonObject = org.json.JSONObject()
            jsonObject.put("id", checkpoint.id)
            jsonObject.put("name", checkpoint.name)
            jsonObject.put("action", checkpoint.action.name.lowercase())
            checkpoint.questionText?.let { jsonObject.put("text", it) }
            checkpoint.inputTitle?.let { jsonObject.put("title", it) }
            checkpoint.imageUri?.let { jsonObject.put("image", it) }
            checkpoint.nfcId?.let { jsonObject.put("nfcId", it) }
            
            val answersArray = org.json.JSONArray()
            checkpoint.answers.forEach { answersArray.put(it) }
            jsonObject.put("answers", answersArray)
            
            jsonArray.put(jsonObject)
        }
        localPrefs.edit().putString("checkpoints_list", jsonArray.toString()).apply()
    }

    /**
     * Загружает список чекпоинтов из SharedPreferences
     */
    fun loadCheckpoints(): List<Checkpoint> {
        val localPrefs = context.getSharedPreferences("OhranaPrefs", Context.MODE_PRIVATE)
        val jsonString = localPrefs.getString("checkpoints_list", null)
        if (jsonString.isNullOrBlank()) return emptyList()
        
        return try {
            val jsonArray = org.json.JSONArray(jsonString)
            List(jsonArray.length()) { index ->
                val jsonObject = jsonArray.getJSONObject(index)
                Checkpoint(
                    id = jsonObject.optString("id", ""),
                    name = jsonObject.optString("name", ""),
                    action = CheckpointAction.valueOf(
                        jsonObject.optString("action", "checkpoint").uppercase()
                    ),
                    questionText = jsonObject.optString("text", null),
                    answers = if (jsonObject.has("answers")) {
                        val answersArray = jsonObject.getJSONArray("answers")
                        List(answersArray.length()) { answersArray.getString(it) }
                    } else {
                        emptyList()
                    },
                    inputTitle = jsonObject.optString("title", null),
                    imageUri = jsonObject.optString("image", null),
                    nfcId = jsonObject.optString("nfcId", null)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Получает чекпоинт по ID
     */
    fun getCheckpointById(id: String): Checkpoint? {
        return loadCheckpoints().find { it.id == id }
    }

    /**
     * Добавляет новый чекпоинт в список
     */
    fun addCheckpoint(checkpoint: Checkpoint) {
        val checkpoints = loadCheckpoints()
        val updated = checkpoints.filter { it.id != checkpoint.id } + checkpoint
        saveCheckpoints(updated)
    }

    /**
     * Удаляет чекпоинт по ID
     */
    fun removeCheckpoint(id: String) {
        val checkpoints = loadCheckpoints()
        val updated = checkpoints.filter { it.id != id }
        saveCheckpoints(updated)
    }

    /**
     * Обновляет существующий чекпоинт
     */
    fun updateCheckpoint(checkpoint: Checkpoint) {
        addCheckpoint(checkpoint)
    }

    /**
     * Получает все ID чекпоинтов
     */
    fun getAllCheckpointIds(): List<String> {
        return loadCheckpoints().map { it.id }
    }

    /**
     * Получает список всех чекпоинтов для редактора
     */
    fun getAllCheckpoints(): List<Checkpoint> {
        return loadCheckpoints()
    }

    /**
     * Получает чекпоинт по NFC-ID
     */
    fun getCheckpointByNfcId(nfcId: String): Checkpoint? {
        return loadCheckpoints().find { it.nfcId == nfcId }
    }
    
    // ==================================================
    // 💾 МЕТОДЫ ЭКСПОРТА/ИМПОРТА НАСТРОЕК (ДЛЯ ВОССТАНОВЛЕНИЯ ПОСЛЕ ПЕРЕУСТАНОВКИ)
    // ==================================================
    
    /**
     * Экспортирует все настройки в JSON-строку
     * Включает: сотрудники, чекпоинты, маршруты, расписание, будильники, настройки контроля
     */
    fun exportSettings(): String {
        val allData = org.json.JSONObject()
        
        try {
            // 1. Сотрудники
            val employeesArray = org.json.JSONArray()
            loadEmployees().forEach { employee ->
                val empObj = org.json.JSONObject()
                empObj.put("name", employee.name)
                empObj.put("role", employee.role)
                employee.nfcId?.let { empObj.put("nfcId", it) }
                employeesArray.put(empObj)
            }
            allData.put("employees", employeesArray)
            
            // 2. Чекпоинты (включая картинки)
            val checkpointsArray = org.json.JSONArray()
            loadCheckpoints().forEach { checkpoint ->
                val checkpointObj = org.json.JSONObject()
                checkpointObj.put("id", checkpoint.id)
                checkpointObj.put("name", checkpoint.name)
                checkpointObj.put("action", checkpoint.action.name.lowercase())
                checkpoint.questionText?.let { checkpointObj.put("text", it) }
                checkpoint.inputTitle?.let { checkpointObj.put("title", it) }
                checkpoint.imageUri?.let { checkpointObj.put("image", it) }
                checkpoint.nfcId?.let { checkpointObj.put("nfcId", it) }
                
                val answersArray = org.json.JSONArray()
                checkpoint.answers.forEach { answersArray.put(it) }
                checkpointObj.put("answers", answersArray)
                
                checkpointsArray.put(checkpointObj)
            }
            allData.put("checkpoints", checkpointsArray)
            
            // 2.1. Маршруты (список всех маршрутов с их ID и чекпоинтами)
            val routesArray = org.json.JSONArray()
            loadRoutes().forEach { route ->
                val routeObj = org.json.JSONObject()
                routeObj.put("id", route.id)
                routeObj.put("name", route.name)
                
                val checkpointIdsArray = org.json.JSONArray()
                route.checkpointIds.forEach { checkpointIdsArray.put(it) }
                routeObj.put("checkpointIds", checkpointIdsArray)
                
                routeObj.put("isActive", route.isActive)
                
                routesArray.put(routeObj)
            }
            allData.put("routes", routesArray)
            
            // 3. Маршрут (точки маршрута)
            val routeCheckpointsArray = org.json.JSONArray()
            loadRouteCheckpoints().forEach { routeCheckpointsArray.put(it) }
            allData.put("route_checkpoints", routeCheckpointsArray)
            
            // 4. Расписание (время обходов)
            val routeTimesArray = org.json.JSONArray()
            loadRouteTimes().forEach { routeTimesArray.put(it) }
            allData.put("route_times", routeTimesArray)
            
            // 5. Настройки маршрута
            val routeSettingsObj = org.json.JSONObject()
            routeSettingsObj.put("rounds_count", loadRouteRoundsCount())
            routeSettingsObj.put("tolerance", loadRouteTolerance())
            allData.put("route_settings", routeSettingsObj)
            
            // 6. Будильники
            val alarmsArray = org.json.JSONArray()
            loadRouteAlarms().forEach { alarm ->
                val alarmObj = org.json.JSONObject()
                alarmObj.put("id", alarm.id)
                alarmObj.put("time", alarm.time)
                alarmObj.put("enabled", alarm.isEnabled)
                alarmObj.put("routeId", alarm.routeId ?: "")
                alarmsArray.put(alarmObj)
            }
            allData.put("alarms", alarmsArray)
            
            // 7. Настройки контроля последовательности
            allData.put("strict_sequence_enabled", isStrictSequenceEnabled())
            
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
        
        return allData.toString()
    }
    
    /**
     * Экспортирует все настройки в текстовом формате (человекочитаемый)
     * Формат: ключ=значение, каждая настройка на новой строке
     */
    fun exportSettingsAsText(): String {
        val sb = StringBuilder()
        
        try {
            sb.append("# =====================================\n")
            sb.append("# Резервная копия настроек Ohrana\n")
            sb.append("# Дата: ${SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US).format(Date())}\n")
            sb.append("# =====================================\n\n")
            
            // 1. Сотрудники
            sb.append("# Сотрудники (формат: имя;роль;nfcId)\n")
            loadEmployees().forEach { employee ->
                sb.append("employee=${employee.name};${employee.role};${employee.nfcId ?: ""}\n")
            }
            sb.append("\n")
            
            // 2. Чекпоинты (формат: id;имя;тип;вопрос;ответы;заголовок;картинка;nfcId)
            sb.append("# Чекпоинты (формат: id;имя;тип;вопрос;ответы;заголовок;картинка;nfcId)\n")
            loadCheckpoints().forEach { checkpoint ->
                val answers = checkpoint.answers.joinToString("|")
                sb.append("checkpoint=${checkpoint.id};${checkpoint.name};${checkpoint.action.name.lowercase()};${checkpoint.questionText ?: ""};${answers};${checkpoint.inputTitle ?: ""};${checkpoint.imageUri ?: ""};${checkpoint.nfcId ?: ""}\n")
            }
            sb.append("\n")
            
            // 2.1. Маршруты (формат: id;имя;чекпоинты;активен)
            sb.append("# Маршруты (формат: id;имя;чекпоинты;активен)\n")
            loadRoutes().forEach { route ->
                val checkpoints = route.checkpointIds.joinToString(",")
                sb.append("route=${route.id};${route.name};${checkpoints};${route.isActive}\n")
            }
            sb.append("\n")
            
            // 3. Маршрут (точки маршрута)
            sb.append("# Маршрут (список точек через запятую)\n")
            sb.append("route_checkpoints=${loadRouteCheckpoints().joinToString(",")}\n\n")
            
            // 4. Расписание (время обходов)
            sb.append("# Расписание (время обходов через запятую)\n")
            sb.append("route_times=${loadRouteTimes().joinToString(",")}\n\n")
            
            // 5. Настройки маршрута
            sb.append("# Настройки маршрута\n")
            sb.append("route_rounds_count=${loadRouteRoundsCount()}\n")
            sb.append("route_tolerance=${loadRouteTolerance()}\n\n")
            
            // 6. Будильники (формат: id;время;включено;маршрут)
            sb.append("# Будильники (формат: id;время;включено;маршрут)\n")
            loadRouteAlarms().forEach { alarm ->
                sb.append("alarm=${alarm.id};${alarm.time};${alarm.isEnabled};${alarm.routeId ?: ""}\n")
            }
            sb.append("\n")
            
            // 7. Настройки контроля последовательности
            sb.append("# Настройки контроля последовательности\n")
            sb.append("strict_sequence_enabled=${isStrictSequenceEnabled()}\n")
            
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
        
        return sb.toString()
    }
    
    /**
     * Импортирует настройки из JSON-строки
     * Возвращает true если успешно, false если произошла ошибка
     */
    fun importSettings(jsonString: String): Boolean {
        return try {
            val data = org.json.JSONObject(jsonString)
            
            // 1. Импорт сотрудников
            if (data.has("employees")) {
                val employeesArray = data.getJSONArray("employees")
                val employees = mutableListOf<Employee>()
                for (i in 0 until employeesArray.length()) {
                    val empObj = employeesArray.getJSONObject(i)
                    employees.add(Employee(
                        name = empObj.optString("name", ""),
                        role = empObj.optString("role", ""),
                        nfcId = empObj.optString("nfcId", null)
                    ))
                }
                saveEmployees(employees.toList())
            }
            
            // 2. Импорт чекпоинтов
            if (data.has("checkpoints")) {
                val checkpointsArray = data.getJSONArray("checkpoints")
                val checkpoints = mutableListOf<Checkpoint>()
                for (i in 0 until checkpointsArray.length()) {
                    val checkpointObj = checkpointsArray.getJSONObject(i)
                    val answersArray = checkpointObj.optJSONArray("answers")
                    val answers = if (answersArray != null) {
                        mutableListOf<String>().apply {
                            for (j in 0 until answersArray.length()) {
                                add(answersArray.optString(j, ""))
                            }
                        }.toList()
                    } else {
                        emptyList()
                    }
                    checkpoints.add(Checkpoint(
                        id = checkpointObj.optString("id", ""),
                        name = checkpointObj.optString("name", ""),
                        action = CheckpointAction.valueOf(
                            checkpointObj.optString("action", "checkpoint").uppercase()
                        ),
                        questionText = checkpointObj.optString("text", null),
                        answers = answers,
                        inputTitle = checkpointObj.optString("title", null),
                        imageUri = checkpointObj.optString("image", null),
                        nfcId = checkpointObj.optString("nfcId", null)
                    ))
                }
                saveCheckpoints(checkpoints.toList())
            }
            
            // 2.1. Импорт маршрутов
            if (data.has("routes")) {
                val routesArray = data.getJSONArray("routes")
                val routes = mutableListOf<Route>()
                for (i in 0 until routesArray.length()) {
                    val routeObj = routesArray.getJSONObject(i)
                    val checkpointIdsArray = routeObj.optJSONArray("checkpointIds")
                    val checkpointIds = if (checkpointIdsArray != null) {
                        mutableListOf<String>().apply {
                            for (j in 0 until checkpointIdsArray.length()) {
                                add(checkpointIdsArray.optString(j, ""))
                            }
                        }.toList()
                    } else {
                        emptyList()
                    }
                    routes.add(Route(
                        id = routeObj.optString("id", ""),
                        name = routeObj.optString("name", "Маршрут ${i + 1}"),
                        checkpointIds = checkpointIds,
                        isActive = routeObj.optBoolean("isActive", false)
                    ))
                }
                saveRoutes(routes.toList())
            }
            
            // 3. Импорт маршрута
            if (data.has("route_checkpoints")) {
                val routeCheckpointsArray = data.getJSONArray("route_checkpoints")
                val routeCheckpoints = mutableListOf<String>()
                for (i in 0 until routeCheckpointsArray.length()) {
                    routeCheckpoints.add(routeCheckpointsArray.optString(i, ""))
                }
                saveRouteCheckpoints(routeCheckpoints)
                
                // Также сохраняем rounds_count и tolerance из JSON
                if (data.has("route_settings")) {
                    val routeSettingsObj = data.getJSONObject("route_settings")
                    prefs.edit().putInt("route_rounds_count", routeSettingsObj.optInt("rounds_count", 3)).apply()
                    prefs.edit().putString("route_tolerance", routeSettingsObj.optString("tolerance", "15")).apply()
                }
            }
            
            // 4. Импорт будильников
            if (data.has("alarms")) {
                val alarmsArray = data.getJSONArray("alarms")
                val alarms = mutableListOf<RouteAlarm>()
                for (i in 0 until alarmsArray.length()) {
                    val alarmObj = alarmsArray.getJSONObject(i)
                    alarms.add(RouteAlarm(
                        id = alarmObj.optInt("id", 0),
                        time = alarmObj.optString("time", "08:00"),
                        isEnabled = alarmObj.optBoolean("enabled", true),
                        routeId = alarmObj.optString("routeId", null)
                    ))
                }
                saveRouteAlarms(alarms.toList())
            }
            
            // 5. Настройки контроля последовательности
            if (data.has("strict_sequence_enabled")) {
                setStrictSequenceEnabled(data.getBoolean("strict_sequence_enabled"))
            }
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Импортирует настройки из текстового формата
     * Возвращает true если успешно, false если произошла ошибка
     */
    fun importSettingsFromText(text: String): Boolean {
        return try {
            val employees = mutableListOf<Employee>()
            
            text.lines().forEach { line ->
                val trimmed = line.trim()
                // Пропускаем пустые строки и комментарии
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
                
                // Парсим строку ключ=значение
                val parts = trimmed.split("=", limit = 2)
                if (parts.size != 2) return@forEach
                
                val key = parts[0].trim()
                val value = parts[1].trim()
                
                when (key) {
                    "employee" -> {
                        val empParts = value.split(";")
                        if (empParts.size >= 2) {
                            employees.add(Employee(
                                name = empParts[0],
                                role = empParts[1],
                                nfcId = if (empParts.size > 2 && empParts[2].isNotEmpty()) empParts[2] else null
                            ))
                        }
                    }
                    "checkpoint" -> {
                        val parts2 = value.split(";")
                        if (parts2.size >= 7) {
                            val answers = parts2[4].split("|")
                            val checkpoint = Checkpoint(
                                id = parts2[0],
                                name = parts2[1],
                                action = CheckpointAction.valueOf(parts2[2].uppercase()),
                                questionText = if (parts2[3].isEmpty()) null else parts2[3],
                                answers = answers,
                                inputTitle = if (parts2[5].isEmpty()) null else parts2[5],
                                imageUri = if (parts2[6].isEmpty()) null else parts2[6],
                                nfcId = if (parts2.size > 7 && parts2[7].isNotEmpty()) parts2[7] else null
                            )
                            addCheckpoint(checkpoint)
                        }
                    }
                    "route" -> {
                        val parts2 = value.split(";")
                        if (parts2.size >= 3) {
                            val checkpointIds = if (parts2.size > 2 && parts2[2].isNotEmpty()) {
                                parts2[2].split(",")
                            } else {
                                emptyList()
                            }
                            val route = Route(
                                id = parts2[0],
                                name = if (parts2.size > 1 && parts2[1].isNotEmpty()) parts2[1] else "Маршрут",
                                checkpointIds = checkpointIds,
                                isActive = if (parts2.size > 3 && parts2[3].isNotEmpty()) parts2[3].toBoolean() else false
                            )
                            val routes = loadRoutes().toMutableList()
                            routes.add(route)
                            saveRoutes(routes)
                        }
                    }
                    "route_checkpoints" -> {
                        saveRouteCheckpoints(value.split(","))
                    }
                    "route_times" -> {
                        // Сохраняем в SharedPreferences напрямую
                        prefs.edit().putString("route_times", value).apply()
                    }
                    "route_rounds_count" -> {
                        prefs.edit().putInt("route_rounds_count", value.toInt()).apply()
                    }
                    "route_tolerance" -> {
                        prefs.edit().putString("route_tolerance", value).apply()
                    }
                    "alarm" -> {
                        val parts2 = value.split(";")
                        if (parts2.size >= 3) {
                            val alarm = RouteAlarm(
                                id = parts2[0].toInt(),
                                time = parts2[1],
                                isEnabled = parts2[2].toBoolean(),
                                routeId = if (parts2.size >= 4 && parts2[3].isNotEmpty()) parts2[3] else null
                            )
                            val alarms = loadRouteAlarms().toMutableList()
                            alarms.add(alarm)
                            saveRouteAlarms(alarms)
                        }
                    }
                    "strict_sequence_enabled" -> {
                        setStrictSequenceEnabled(value.toBoolean())
                    }
                }
            }
            
            // Сохраняем сотрудников после парсинга всех строк
            if (employees.isNotEmpty()) {
                saveEmployees(employees.toList())
            }
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    // ==================================================
    // 🛣️ МЕТОДЫ ДЛЯ РАБОТЫ С МАРШРУТАМИ
    // ==================================================
    
    /**
     * Сохраняет список маршрутов в SharedPreferences
     * Формат ключа: routes_list (JSON-строка)
     */
    fun saveRoutes(routes: List<Route>) {
        val localPrefs = context.getSharedPreferences("OhranaPrefs", Context.MODE_PRIVATE)
        val jsonArray = org.json.JSONArray()
        routes.forEach { route ->
            val jsonObject = org.json.JSONObject()
            jsonObject.put("id", route.id)
            jsonObject.put("name", route.name)
            
            val checkpointIdsArray = org.json.JSONArray()
            route.checkpointIds.forEach { checkpointIdsArray.put(it) }
            jsonObject.put("checkpointIds", checkpointIdsArray)
            
            jsonObject.put("isActive", route.isActive)
            
            jsonArray.put(jsonObject)
        }
        localPrefs.edit().putString("routes_list", jsonArray.toString()).apply()
    }
    
    /**
     * Загружает список маршрутов из SharedPreferences
     */
    fun loadRoutes(): List<Route> {
        val localPrefs = context.getSharedPreferences("OhranaPrefs", Context.MODE_PRIVATE)
        val jsonString = localPrefs.getString("routes_list", null)
        if (jsonString.isNullOrBlank()) return emptyList()
        
        return try {
            val jsonArray = org.json.JSONArray(jsonString)
            List(jsonArray.length()) { index ->
                val jsonObject = jsonArray.getJSONObject(index)
                val checkpointIdsArray = jsonObject.optJSONArray("checkpointIds")
                val checkpointIds = if (checkpointIdsArray != null) {
                    mutableListOf<String>().apply {
                        for (i in 0 until checkpointIdsArray.length()) {
                            add(checkpointIdsArray.optString(i, ""))
                        }
                    }.toList()
                } else {
                    emptyList()
                }
                Route(
                    id = jsonObject.optString("id", ""),
                    name = jsonObject.optString("name", "Маршрут ${index + 1}"),
                    checkpointIds = checkpointIds,
                    isActive = jsonObject.optBoolean("isActive", false)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * Получает маршрут по ID
     */
    fun getRouteById(id: String): Route? {
        return loadRoutes().find { it.id == id }
    }
    
    /**
     * Добавляет новый маршрут в список
     */
    fun addRoute(route: Route) {
        val routes = loadRoutes()
        val updated = routes.filter { it.id != route.id } + route
        saveRoutes(updated)
    }
    
    /**
     * Удаляет маршрут по ID
     */
    fun removeRoute(id: String) {
        val routes = loadRoutes()
        val updated = routes.filter { it.id != id }
        saveRoutes(updated)
    }
    
    /**
     * Обновляет существующий маршрут
     */
    fun updateRoute(route: Route) {
        addRoute(route)
    }
    
    /**
     * Устанавливает активный маршрут
     */
    fun setActiveRoute(id: String) {
        val routes = loadRoutes()
        val updated = routes.map { route ->
            route.copy(isActive = route.id == id)
        }
        saveRoutes(updated)
    }
    
    /**
     * Получает активный маршрут
     */
    fun getActiveRoute(): Route? {
        return loadRoutes().find { it.isActive }
    }
    
    /**
     * Получает все ID маршрутов
     */
    fun getAllRouteIds(): List<String> {
        return loadRoutes().map { it.id }
    }
    
    /**
     * Получает список всех маршрутов
     */
    fun getAllRoutes(): List<Route> {
        return loadRoutes()
    }
    
    /**
     * Экспортирует настройки в файл во внешнее хранилище (Download)
     * Возвращает true если успешно, false если произошла ошибка
     */
    fun exportSettingsToFile(context: Context): Boolean {
        val settingsText = exportSettingsAsText()
        if (settingsText.isEmpty()) {
            android.util.Log.e("SharedPrefsManager", "exportSettingsAsText() returned empty string")
            return false
        }
        
        return try {
            android.util.Log.d("SharedPrefsManager", "Exporting settings to external storage...")
            // Сохраняем во внешнее хранилище (Download папка)
            val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            android.util.Log.d("SharedPrefsManager", "Download dir: ${downloadDir.absolutePath}")
            
            val backupDir = File(downloadDir, "Ohrana")
            android.util.Log.d("SharedPrefsManager", "Backup dir: ${backupDir.absolutePath}")
            if (!backupDir.exists()) backupDir.mkdirs()
            
            val fileTimestamp = SimpleDateFormat("dd.MM.yy_HH-mm-ss", Locale.US).format(Date())
            val fileName = "settings_backup_$fileTimestamp.txt"  // .txt вместо .json
            val file = File(backupDir, fileName)
            android.util.Log.d("SharedPrefsManager", "Export file: ${file.absolutePath}")
            
            file.bufferedWriter(charset = Charsets.UTF_8).use { writer ->
                writer.write(settingsText)
            }
            android.util.Log.d("SharedPrefsManager", "Settings exported successfully!")
            
            // Уведомляем систему о новом файле через MediaScannerConnection (работает на всех версиях)
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf("text/plain"),
                null
            )
            
            true
        } catch (e: Exception) {
            android.util.Log.e("SharedPrefsManager", "Error exporting settings: ${e.message}", e)
            false
        }
    }
    
    /**
     * Импортирует настройки из файла во внешнем хранилище (Download)
     * Возвращает true если успешно, false если произошла ошибка
     */
    fun importSettingsFromFile(context: Context): Boolean {
        android.util.Log.d("SharedPrefsManager", "Starting import from file...")
        return try {
            // Ищем во внешнем хранилище (Download папка)
            val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val backupDir = File(downloadDir, "Ohrana")
            android.util.Log.d("SharedPrefsManager", "Download dir: ${downloadDir.absolutePath}")
            android.util.Log.d("SharedPrefsManager", "Backup dir: ${backupDir.absolutePath}")
            
            if (!backupDir.exists()) {
                android.util.Log.d("SharedPrefsManager", "Backup dir does not exist")
                android.widget.Toast.makeText(context, "Нет резервных копий", android.widget.Toast.LENGTH_SHORT).show()
                return false
            }
            
            val files = backupDir.listFiles()?.filter { it.extension == "json" }?.toList() ?: emptyList()
            android.util.Log.d("SharedPrefsManager", "Found ${files.size} JSON files: ${files.map { it.name }}")
            
            if (files.isEmpty()) {
                android.util.Log.d("SharedPrefsManager", "No JSON files found")
                android.widget.Toast.makeText(context, "Нет резервных копий", android.widget.Toast.LENGTH_SHORT).show()
                return false
            }
            
            // Сортируем по дате, берем последний (самый свежий)
            val latestFile = files.maxByOrNull { it.lastModified() } ?: return false
            android.util.Log.d("SharedPrefsManager", "Latest file: ${latestFile.absolutePath}")
            
            val jsonString = latestFile.bufferedReader(charset = Charsets.UTF_8).use { it.readText() }
            android.util.Log.d("SharedPrefsManager", "JSON loaded, length: ${jsonString.length}")
            return importSettings(jsonString)
        } catch (e: Exception) {
            android.util.Log.e("SharedPrefsManager", "Error importing from file: ${e.message}", e)
            false
        }
    }
    
    // ==================================================
    // 📊 ГЕНЕРАЦИЯ ОТЧЕТОВ ИЗ БАЗЫ ДАННЫХ ОБХОДОВ
    // ==================================================
    
    /**
     * Генерирует полный отчет для администратора
     */
    fun generateAdminReport(): AdminReport? {
        val activeShiftId = prefs.getString("active_shift_id", null)
        return activeShiftId?.let { shiftDatabase.generateAdminReport(it) }
    }
    
    /**
     * Генерирует упрощенный отчет для охранника
     */
    fun generateGuardReport(): GuardReport? {
        val activeShiftId = prefs.getString("active_shift_id", null)
        return activeShiftId?.let { shiftDatabase.generateGuardReport(it) }
    }
    
    /**
     * Генерирует краткий отчет для владельца объекта
     */
    fun generateOwnerReport(): OwnerReport? {
        val activeShiftId = prefs.getString("active_shift_id", null)
        return activeShiftId?.let { shiftDatabase.generateOwnerReport(it) }
    }
}

