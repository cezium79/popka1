package com.example.ohrana

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.nio.charset.StandardCharsets
import com.example.ohrana.ShiftDatabaseManager
import com.example.ohrana.ShiftLogEntry
import com.example.ohrana.ShiftRecord
import com.example.ohrana.RoundRecord
import com.example.ohrana.SequenceViolation
import com.example.ohrana.GuardMember
import com.example.ohrana.CheckpointEntry
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.MailTo
import java.net.URLEncoder
import android.content.ClipboardManager
import android.content.ClipData

// Модель для отображения строки в журнале
data class ShiftJournalRow(
    val type: String, // "HEADER", "ROUND_HEADER", "SCAN", "FOOTER"
    val content: String,
    val subContent: String? = null,
    val roundId: Int? = null,
    val rowIndex: Int? = null
)

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
    
    // --- МЕТОДЫ ДЛЯ ГРУППОВОЙ РАБОТЫ ОХРАННИКОВ ---
    
    /**
     * Количество охранников на смене (по умолчанию 1 для совместимости)
     */
    fun getGuardsCount(): Int {
        return prefs.getInt("guards_count", 1)
    }
    
    /**
     * Установить количество охранников на смене
     */
    fun setGuardsCount(count: Int) {
        prefs.edit().putInt("guards_count", count).apply()
    }
    
    /**
     * Получить список охранников на смене (из SharedPreferences)
     */
    fun loadGuards(): List<GuardMember> {
        val localPrefs = context.getSharedPreferences("OhranaPrefs", Context.MODE_PRIVATE)
        val jsonString = localPrefs.getString("guards_list", null)
        if (jsonString.isNullOrBlank()) return emptyList()
        
        return try {
            val jsonArray = org.json.JSONArray(jsonString)
            List(jsonArray.length()) { index ->
                val jsonObject = jsonArray.getJSONObject(index)
                GuardMember(
                    nfcId = jsonObject.optString("nfcId", ""),
                    name = jsonObject.optString("name", ""),
                    role = jsonObject.optString("role", "охранник"),
                    startTime = if (jsonObject.has("startTime")) jsonObject.getString("startTime") else null
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * Сохранить список охранников в SharedPreferences
     */
    fun saveGuards(guards: List<GuardMember>) {
        val localPrefs = context.getSharedPreferences("OhranaPrefs", Context.MODE_PRIVATE)
        val jsonArray = org.json.JSONArray()
        guards.forEach { guard ->
            val jsonObject = org.json.JSONObject()
            jsonObject.put("nfcId", guard.nfcId)
            jsonObject.put("name", guard.name)
            jsonObject.put("role", guard.role)
            guard.startTime?.let { jsonObject.put("startTime", it) }
            jsonArray.put(jsonObject)
        }
        localPrefs.edit().putString("guards_list", jsonArray.toString()).apply()
    }
    
    /**
     * Получить текущего охранника (по NFC ID)
     */
    fun getCurrentGuard(): GuardMember? {
        val currentNfcId = prefs.getString("current_guard_nfc_id", null)
        if (currentNfcId.isNullOrEmpty()) return null
        
        val guards = loadGuards()
        return guards.find { it.nfcId == currentNfcId }
    }
    
    /**
     * Установить текущего охранника по NFC ID
     */
    fun setCurrentGuard(nfcId: String) {
        prefs.edit().putString("current_guard_nfc_id", nfcId).apply()
    }
    
    /**
     * Получить старшего смены
     */
    fun getShiftLeader(): GuardMember? {
        val guards = loadGuards()
        return guards.find { it.role.contains("старший", ignoreCase = true) || it.role.contains("lead", ignoreCase = true) }
    }
    
    // --- МЕТОДЫ УПРАВЛЕНИЯ СМЕНОЙ ОХРАННИКА ---
// Включен ли строгий контроль последовательности сканирования (true/false)
    fun isStrictSequenceEnabled(): Boolean {
        return prefs.getBoolean("strict_sequence_enabled", false)
    }

    fun setStrictSequenceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("strict_sequence_enabled", enabled).apply()
    }

    // Включен ли автоматический перехват обхода при завершении последнего чекпоинта
    fun isAutoEndRoundEnabled(): Boolean {
        return prefs.getBoolean("auto_end_round_enabled", true)
    }

    fun setAutoEndRoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_end_round_enabled", enabled).apply()
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

    // Получить индекс чекпоинта, который мы ждем от сканера (начиная с 0) - устаревший, используйте getRoundCheckpointIndex
    fun getCurrentCheckpointIndex(): Int = prefs.getInt("active_route_current_index", 0)
    
    // Обновить индекс чекпоинта для активного обхода
    fun updateCurrentCheckpointIndex(index: Int) {
        val activeRoundIndex = getActiveRoundIndex()
        if (activeRoundIndex != -1) {
            prefs.edit().putInt("round_${activeRoundIndex}_checkpoint_index", index).apply()
        }
        prefs.edit().putInt("active_route_current_index", index).apply()
    }

    fun resetRouteProgress() {
        prefs.edit().putInt("active_route_current_index", 0).apply()
    }

    // Начать новую смену (сохраняем имя и время старта)
    // Для совместимости - может принимать один employeeName или список guardList
    fun startNewShift(
        guardList: List<GuardMember>, 
        strictSequenceEnabled: Boolean
    ) {
        val currentTime = dateFormat.format(Date()) // Генерирует строку вида "27.06.2026 15:30:00"
        
        // Сохраняем список охранников в SharedPreferences
        saveGuards(guardList)
        
        // Очищаем shiftLogs при старте новой смены
        QrHandler.clearShiftLogs()
        // Сбрасываем прогресс маршрута при старте новой смены
        resetRouteProgress()
        // Сбрасываем статус всех обходов при старте новой смены
        resetAllRounds()
        
        // Создаем новую запись смены в базе данных (передаем первого охранника как основного)
        val mainGuard = guardList.firstOrNull()
        val mainGuardName = mainGuard?.name ?: ""
        val shiftId = shiftDatabase.startNewShift(mainGuardName, guardList, strictSequenceEnabled)
        // Сохраняем ID активной смены в SharedPreferences
        prefs.edit().putString("active_shift_id", shiftId).apply()
        
        // Устанавливаем текущего охранника (первого в списке)
        if (guardList.isNotEmpty()) {
            setCurrentGuard(guardList[0].nfcId)
        }
        
        prefs.edit().apply {
            putString("active_shift_employee", mainGuardName)
            putString("active_shift_start_time", currentTime)
            putLong("active_shift_start_time_epoch", System.currentTimeMillis())  // ДОБАВЛЕНО: epoch time
            putBoolean("active_shift_is_running", true)
            // Сохраняем статус контроля последовательности при старте смены
            putBoolean("sequence_control_was_enabled", strictSequenceEnabled)
            apply()
        }
    }
    
    // Старый метод для совместимости - принимает один employeeName
    fun startNewShift(employeeName: String, strictSequenceEnabled: Boolean) {
        // Для совместимости создаем список с одним охранником
        val guard = GuardMember(
            nfcId = "",
            name = employeeName,
            role = "охранник"
        )
        startNewShift(listOf(guard), strictSequenceEnabled)
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
        
        // ГЕНЕРАЦИЯ ОТЧЕТА - JSON файл для хранения (без автоматической загрузки в Яндекс.Диск)
        val activeShiftId = prefs.getString("active_shift_id", null)
        activeShiftId?.let { shiftId ->
            val cloudManager = CloudStorageManager(context)
            val result = cloudManager.exportShiftReport(shiftId, shiftDatabase)
            if (result != null) {
                Log.d("SharedPrefsManager", "Отчеты сохранены локально в: $result")
                
                // 🔔 ОТПРАВКА EMAIL С HTML ОТЧЕТОМ ПРИ ЗАКРЫТИИ СМЕНЫ
                sendReportViaEmail(shiftId, cloudManager)
            } else {
                Log.e("SharedPrefsManager", "Ошибка генерации отчета")
            }
        }
        
        // Очищаем shiftLogs при закрытии смены
        QrHandler.clearShiftLogs()
        // Сбрасываем прогресс маршрута при закрытии смены
        resetRouteProgress()
        
        // Получаем ID активной смены
        activeShiftId?.let { shiftDatabase.closeShift(it) }
        // Удаляем ID активной смены
        prefs.edit().remove("active_shift_id").apply()

        prefs.edit().apply {
            putBoolean("active_shift_is_running", false)
            putString("active_shift_end_time", endTime) // ФИКСАЦИЯ: Сохраняем время закрытия

            // Удаляем данные сотрудника, так как смена завершена
            remove("active_shift_employee")
            remove("active_shift_start_time")
            remove("active_shift_start_time_epoch")  // УДАЛЕНО: удаляем epoch time при закрытии
            
            // Сбрасываем индексы чекпоинтов всех обходов
            val routeAlarms = loadRouteAlarms()
            routeAlarms.forEach { alarm ->
                prefs.edit().putInt("round_${alarm.id}_checkpoint_index", 0).apply()
            }
            
            apply()
        }
    }
    
    /**
     * Отправляет отчет по email при закрытии смены
     * @param shiftId ID смены
     * @param cloudManager Менедгер облачного хранилища
     */
    fun sendReportViaEmail(shiftId: String, cloudManager: CloudStorageManager) {
        val smtpUsername = getSmtpUsername()
        val smtpRecipient = getSmtpRecipient()
        
        Log.d("SharedPrefsManager", "sendReportViaEmail: smtpUsername=$smtpUsername, smtpRecipient=$smtpRecipient")
        
        // Проверяем, настроен ли SMTP
        if (smtpUsername.isEmpty() || getSmtpPassword().isEmpty()) {
            Log.d("SharedPrefsManager", "sendReportViaEmail: SMTP не настроен")
            return
        }
        
        // Если email получателя не настроен, используем email отправителя
        val recipient = if (smtpRecipient.isEmpty()) smtpUsername else smtpRecipient
        
        val emailManager = EmailManager(context)
        
        // Получаем дизайн отчета
        val reportDesign = getReportDesign()
        
        // Генерируем HTML отчет с учетом выбранного дизайна
        val htmlPath = cloudManager.generateHtmlReportWithDesign(shiftId, shiftDatabase, reportDesign)
        
        if (htmlPath == null) {
            Log.e("SharedPrefsManager", "sendReportViaEmail: Failed to generate HTML report")
            return
        }
        
        // Читаем HTML файл
        val htmlContent = try {
            val file = java.io.File(htmlPath)
            file.readText(charset = StandardCharsets.UTF_8)
        } catch (e: Exception) {
            Log.e("SharedPrefsManager", "sendReportViaEmail: Failed to read HTML file: ${e.message}", e)
            return
        }
        
        // Формируем тему и тело письма
        val shift = shiftDatabase.loadAllShifts().find { it.id == shiftId }
        val subject = "Отчет Ohrana - Смена №${shiftId.substringAfterLast("_")} от ${shift?.startTime?.substring(0, 10) ?: "-"}"
        val body = "Отчет о смене прилагается.\n\nСмена: $shiftId\nВремя начала: ${shift?.startTime ?: "-"}\nВремя окончания: ${shift?.endTime ?: "-"}"
        
        Log.d("SharedPrefsManager", "sendReportViaEmail: Sending email to $recipient")
        
        // Отправляем email с вложением
        CoroutineScope(Dispatchers.Main).launch {
            val success = withContext(Dispatchers.IO) {
                emailManager.sendEmailWithAttachment(
                    to = recipient,
                    subject = subject,
                    body = body,
                    attachmentHtml = htmlContent,
                    attachmentName = "shift_report_${shiftId}.html"
                )
            }
            
            if (success) {
                Log.d("SharedPrefsManager", "Email sent successfully via SMTP to $recipient")
            } else {
                Log.e("SharedPrefsManager", "Failed to send email via SMTP")
            }
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
            // НЕ сбрасываем индекс чекпоинта - он сохраняется при возврате в незавершённый обход
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
            // НЕ сбрасываем индекс чекпоинта - он сохраняется для отображения "Завершен"
            // putInt("round_${roundIndex}_checkpoint_index", 0) // УДАЛЕНО: сбрасывал индекс
            apply()
        }
        
        // Завершаем обход в новой базе данных
        shiftDatabase.completeRound(roundIndex, endTime)
    }

    // Получить индекс обхода, который выполняется прямо сейчас (-1 означает, что никакой обход не запущен)
    fun getActiveRoundIndex(): Int = prefs.getInt("active_shift_current_round_index", -1)
    
    // Получить индекс чекпоинта для конкретного обхода
    fun getRoundCheckpointIndex(roundIndex: Int): Int = prefs.getInt("round_${roundIndex}_checkpoint_index", 0)
    
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
        
        // Получаем индекс текущего чекпоинта для активного обхода
        val checkpointIndex = getRoundCheckpointIndex(activeRoundIndex)
        
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
    
    // Проверить, запущен ли обход (начат, но не завершен)
    // Обход считается запущенным (и готовым к "Продолжить обход"), если:
    // 1. Он не зав��ршен (isRoundCompleted == false)
    // 2. Он был запущен (существует время начала не "-")
    // 3. И есть прогресс (индекс чекпоинта > 0 для этого обхода)
    //
    // Это означает, что:
    // - При нажатии "Начать обход" startTime устанавливается, но checkpointIndex = 0
    // - После первого сканирования checkpointIndex становится > 0
    // - Пока checkpointIndex = 0, обход еще не "начат" в смысле прогресса
    fun isRoundStarted(roundIndex: Int): Boolean {
        val isCompleted = isRoundCompleted(roundIndex)
        val startTime = getRoundStartTime(roundIndex)
        
        // Если время начала "-" или обход завершен, то обход не запущен
        if (startTime == "-" || isCompleted) return false
        
        // Проверяем прогресс: индекс чекпоинта для этого конкретного обхода
        val checkpointIndex = getRoundCheckpointIndex(roundIndex)
        
        // Обход считается запущенным только если есть прогресс (checkpointIndex > 0)
        return checkpointIndex > 0
    }
    
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
                    nfcId = if (jsonObject.has("nfcId")) jsonObject.getString("nfcId") else null
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
        val shiftId = prefs.getString("active_shift_id", null)
        
        // Очищаем контент от точек с запятой и переносов, чтобы не ломать структуру CSV таблицы
        val safeContent = qrContent.replace(";", " ").replace("\n", " ")
        // Добавляем shiftId в начало строки для идентификации
        val logLine = "${shiftId ?: "none"};$employeeName;$currentTime;$safeContent"

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
            if (parts.size >= 3) {
                // Формат: shiftId;employeeName;currentTime;qrContent (или старый формат)
                if (parts.size == 4) {
                    QrScanRecord(parts[1], parts[2], parts[3])
                } else if (parts.size == 3) {
                    // Старый формат без shiftId
                    QrScanRecord(parts[0], parts[1], parts[2])
                } else {
                    null
                }
            } else {
                null
            }
        }
    }
    
    // Получение логов только для текущей смены
    fun getCurrentShiftScanLogs(): List<QrScanRecord> {
        val logsString = prefs.getString("qr_logs", "") ?: ""
        if (logsString.isEmpty()) return emptyList()
        
        val currentShiftId = prefs.getString("active_shift_id", null)
        if (currentShiftId == null) return emptyList()

        return logsString.split("\n").mapNotNull { line ->
            val parts = line.split(";")
            if (parts.size >= 4) {
                // Формат: shiftId;employeeName;currentTime;qrContent
                if (parts[0] == currentShiftId) {
                    QrScanRecord(parts[1], parts[2], parts[3])
                } else {
                    null
                }
            } else if (parts.size == 3) {
                // Старый формат без shiftId - возвращаем как есть (возможно, из предыдущих смен)
                null // Игнорируем старые записи без shiftId
            } else {
                null
            }
        }
    }
    
    // Получение логов сканирования для конкретного обхода
    fun getRoundScanLogs(roundId: Int): List<ShiftLogEntry> {
        val logs = shiftDatabase.loadLogsByRound(roundId)
        val currentShiftId = prefs.getString("active_shift_id", null)
        // Фильтруем только логи текущей смены
        return logs.filter { it.shiftId == currentShiftId }
    }
    
    // Получение нарушений для конкретного обхода
    fun getRoundViolations(roundId: Int): List<SequenceViolation> {
        val currentShiftId = prefs.getString("active_shift_id", null)
        val violations = shiftDatabase.loadViolationsByRound(roundId, currentShiftId)
        // Фильтруем только нарушения текущей смены (дополнительная защита)
        return violations.filter { it.shiftId == currentShiftId }
    }

    // Очистка логов после генерации отчета
    fun clearScanLogs() {
        prefs.edit().remove("qr_logs").apply()
    }
    
    // Получить строки для табличного отображения журнала текущей смены
    fun getShiftJournalRows(): List<ShiftJournalRow> {
        val rows = mutableListOf<ShiftJournalRow>()
        val currentShiftId = prefs.getString("active_shift_id", null)
        if (currentShiftId == null) return emptyList()
        
        // Заголовок с информацией о смене
        val shiftStartTime = prefs.getString("active_shift_start_time", "-") ?: "-"
        val shiftEndTime = prefs.getString("active_shift_end_time", "-") ?: "-"
        val guards = loadGuards()
        
        // Шапка отчета
        rows.add(ShiftJournalRow("HEADER", "ЖУРНАЛ ОБХОДОВ ОХРАННИКОВ", "", null, null))
        rows.add(ShiftJournalRow("HEADER", "за ${shiftStartTime.split(" ").getOrNull(0) ?: ""}", "", null, null))
        rows.add(ShiftJournalRow("HEADER", "---", "", null, null)) // Разделитель
        
        // Информация о смене
        rows.add(ShiftJournalRow("HEADER", "Смена №: ${currentShiftId}", "", null, null))
        rows.add(ShiftJournalRow("HEADER", "Начало: $shiftStartTime", "", null, null))
        rows.add(ShiftJournalRow("HEADER", "Завершение: ${if (shiftEndTime != "-") shiftEndTime else "-"}", "", null, null))
        rows.add(ShiftJournalRow("HEADER", "Сотрудники: ${guards.joinToString(", ") { it.name }}", "", null, null))
        rows.add(ShiftJournalRow("HEADER", "---", "", null, null)) // Разделитель
        rows.add(ShiftJournalRow("HEADER", "", "", null, null)) // Пустая строка
        
        // Информация об обходах
        val routeAlarms = loadRouteAlarms()
        
        routeAlarms.forEach { alarm ->
            val startTime = prefs.getString("round_${alarm.id}_start_time", "-") ?: "-"
            val endTime = prefs.getString("round_${alarm.id}_end_time", "-") ?: "-"
            val isCompleted = prefs.getBoolean("round_${alarm.id}_is_completed", false)
            val checkpointIndex = prefs.getInt("round_${alarm.id}_checkpoint_index", 0)
            val checkpointIds = getActiveRouteCheckpoints()
            val totalCheckpoints = checkpointIds.size
            
            val status = if (isCompleted) "Завершен" else if (startTime != "-") "В процессе" else "Не начат"
            
            // Заголовок обхода
            rows.add(ShiftJournalRow("ROUND_HEADER", "Обход №${alarm.id} ${alarm.time}", "Статус: $status, Пройдено: $checkpointIndex/$totalCheckpoints", alarm.id, null))
            
            // Логи для этого обхода
            val roundLogs = getCurrentShiftScanLogs().filter { log ->
                // Можно добавить фильтрацию по времени или другим критериям
                true
            }
            
            if (roundLogs.isNotEmpty()) {
                rows.add(ShiftJournalRow("HEADER", "Время | Сотрудник | Что сделано", "", alarm.id, null))
                roundLogs.forEach { log ->
                    rows.add(ShiftJournalRow("SCAN", "${log.time} | ${log.employeeName} | ${log.qrContent}", "", alarm.id, null))
                }
            } else {
                rows.add(ShiftJournalRow("SCAN", "Нет записей сканирования", "", alarm.id, null))
            }
            
            rows.add(ShiftJournalRow("HEADER", "", "", alarm.id, null)) // Пустая строка
        }
        
        // Итоги
        rows.add(ShiftJournalRow("HEADER", "---", "", null, null)) // Разделитель
        rows.add(ShiftJournalRow("HEADER", "ИТОГИ:", "", null, null))
        
        // Подсчет статистики
        val completedRounds = routeAlarms.count { prefs.getBoolean("round_${it.id}_is_completed", false) }
        val sequenceControlWasEnabled = prefs.getBoolean("sequence_control_was_enabled", false)
        val totalScans = getCurrentShiftScanLogs().size
        
        rows.add(ShiftJournalRow("FOOTER", "Всего обходов:", "$completedRounds/${routeAlarms.size}", null, null))
        rows.add(ShiftJournalRow("FOOTER", "Сканирований:", "$totalScans", null, null))
        rows.add(ShiftJournalRow("FOOTER", "Контроль последовательности:", if (sequenceControlWasEnabled) "ВКЛ" else "ВЫКЛ", null, null))
        
        return rows
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
                    routeId = if (jsonObject.has("routeId")) jsonObject.getString("routeId") else null
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
                    questionText = if (jsonObject.has("text")) jsonObject.getString("text") else null,
                    answers = if (jsonObject.has("answers")) {
                        val answersArray = jsonObject.getJSONArray("answers")
                        List(answersArray.length()) { answersArray.optString(it) }
                    } else {
                        emptyList()
                    },
                    inputTitle = if (jsonObject.has("title")) jsonObject.getString("title") else null,
                    imageUri = if (jsonObject.has("image")) jsonObject.getString("image") else null,
                    nfcId = if (jsonObject.has("nfcId")) jsonObject.getString("nfcId") else null
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
                // Добавляем имя маршрута для удобства
                alarmObj.put("routeName", if (alarm.routeId != null) getRouteById(alarm.routeId)?.name ?: "" else "")
                alarmsArray.put(alarmObj)
            }
            allData.put("alarms", alarmsArray)
            
            // 7. Настройки контроля последовательности
            allData.put("strict_sequence_enabled", isStrictSequenceEnabled())
            allData.put("auto_end_round_enabled", isAutoEndRoundEnabled())
            
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
        
        return allData.toString()
    }
    
    /**
     * Экспортирует все настройки в текстовом формате (человекочитаемый)
     * Формат: ключ=значение, каждая настройка на новой строке
     * Включает: сотрудники, чекпоинты, маршруты, расписание, будильники, настройки контроля
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
            sb.append("route_tolerance=${loadRouteTolerance()}\n")
            sb.append("max_round_duration=${loadMaxRoundDuration()}\n\n")
            
            // 6. Будильники (формат: id;время;включено;id_маршрута;имя_маршрута)
            sb.append("# Будильники (формат: id;время;включено;id_маршрута;имя_маршрута)\n")
            loadRouteAlarms().forEach { alarm ->
                val routeName = if (alarm.routeId != null) {
                    getRouteById(alarm.routeId)?.name ?: ""
                } else {
                    ""
                }
                sb.append("alarm=${alarm.id};${alarm.time};${alarm.isEnabled};${alarm.routeId ?: ""};${routeName}\n")
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
                        nfcId = if (empObj.has("nfcId")) empObj.getString("nfcId") else null
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
                        questionText = if (checkpointObj.has("text")) checkpointObj.getString("text") else null,
                        answers = answers,
                        inputTitle = if (checkpointObj.has("title")) checkpointObj.getString("title") else null,
                        imageUri = if (checkpointObj.has("image")) checkpointObj.getString("image") else null,
                        nfcId = if (checkpointObj.has("nfcId")) checkpointObj.getString("nfcId") else null
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
                        routeId = if (alarmObj.has("routeId")) alarmObj.getString("routeId") else null
                    ))
                }
                saveRouteAlarms(alarms.toList())
            }
            
            // 5. Настройки контроля последовательности
            if (data.has("strict_sequence_enabled")) {
                setStrictSequenceEnabled(data.getBoolean("strict_sequence_enabled"))
            }
            if (data.has("auto_end_round_enabled")) {
                setAutoEndRoundEnabled(data.getBoolean("auto_end_round_enabled"))
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
                    "max_round_duration" -> {
                        saveMaxRoundDuration(value)
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
    
    // ==================================================
    // 📱 МЕТОДЫ ДЛЯ НАСТРОЕК SMS
    // ==================================================
    
    /**
     * Сохраняет номер телефона для отправки SMS
     */
    fun saveSmsPhone(phone: String) {
        prefs.edit().putString("sms_phone", phone).apply()
    }
    
    /**
     * Получает сохраненный номер телефона для SMS
     */
    fun getSmsPhone(): String {
        return prefs.getString("sms_phone", "") ?: ""
    }
    
    /**
     * Включает/отключает автоматическую отправку SMS после загрузки в Диск
     */
    fun setAutoSendSmsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_send_sms", enabled).apply()
    }
    
    /**
     * Проверяет, включена ли автоматическая отправка SMS
     */
    fun isAutoSendSmsEnabled(): Boolean {
        return prefs.getBoolean("auto_send_sms", false)
    }
    
    /**
     * Сохраняет текст сообщения по умолчанию
     */
    fun saveSmsTemplate(template: String) {
        prefs.edit().putString("sms_template", template).apply()
    }
    
    /**
     * Получает текст сообщения по умолчанию
     */
    fun getSmsTemplate(): String {
        return prefs.getString("sms_template", "Отчет загружен в Яндекс.Диск: {url}") ?: "Отчет загружен в Яндекс.Диск: {url}"
    }
    
    /**
     * Сохраняет email для отправки через почту
     */
    fun saveFileIoEmail(email: String) {
        prefs.edit().putString("fileio_email", email).apply()
    }
    
    /**
     * Получает сохраненный email
     */
    fun getFileIoEmail(): String {
        return prefs.getString("fileio_email", "") ?: ""
    }
    
    /**
     * Сохраняет username Telegram для отправки
     */
    fun saveFileIoTelegram(username: String) {
        prefs.edit().putString("fileio_telegram", username).apply()
    }
    
    /**
     * Получает сохраненный username Telegram
     */
    fun getFileIoTelegram(): String {
        return prefs.getString("fileio_telegram", "") ?: ""
    }
    
    /**
     * Сохраняет выбор действия со ссылкой
     */
    fun saveSmsAction(action: LinkAction) {
        prefs.edit().putString("sms_action", action.value).apply()
    }
    
    /**
     * Получает сохраненное действие со ссылкой
     */
    fun getSmsAction(): LinkAction {
        val value = prefs.getString("sms_action", LinkAction.SAVE_TO_DEVICE.value)
        return LinkAction.values().find { it.value == value } ?: LinkAction.SAVE_TO_DEVICE
    }
    
    /**
     * Сохраняет email получателя для SMTP
     */
    fun saveSmtpRecipient(email: String) {
        prefs.edit().putString("smtp_recipient", email).apply()
    }
    
    /**
     * Получает email получателя для SMTP
     */
    fun getSmtpRecipient(): String {
        return prefs.getString("smtp_recipient", "") ?: ""
    }
    
    /**
     * Сохраняет SMTP хост
     */
    fun saveSmtpHost(host: String) {
        prefs.edit().putString("smtp_host", host).apply()
    }
    
    /**
     * Получает SMTP хост
     */
    fun getSmtpHost(): String {
        return prefs.getString("smtp_host", "smtp.yandex.ru") ?: "smtp.yandex.ru"
    }
    
    /**
     * Сохраняет SMTP порт
     */
    fun saveSmtpPort(port: Int) {
        prefs.edit().putInt("smtp_port", port).apply()
    }
    
    /**
     * Получает SMTP порт
     */
    fun getSmtpPort(): Int {
        return prefs.getInt("smtp_port", 465) // 465 для SSL
    }
    
    /**
     * Сохраняет SMTP username (email)
     */
    fun saveSmtpUsername(username: String) {
        prefs.edit().putString("smtp_username", username).apply()
    }
    
    /**
     * Получает SMTP username
     */
    fun getSmtpUsername(): String {
        return prefs.getString("smtp_username", "") ?: ""
    }
    
    /**
     * Сохраняет SMTP пароль
     */
    fun saveSmtpPassword(password: String) {
        prefs.edit().putString("smtp_password", password).apply()
    }
    
    /**
     * Получает SMTP пароль
     */
    fun getSmtpPassword(): String {
        return prefs.getString("smtp_password", "") ?: ""
    }
    
    /**
     * Проверяет, настроен ли SMTP
     */
    fun isSmtpConfigured(): Boolean {
        val username = getSmtpUsername()
        val password = getSmtpPassword()
        return username.isNotEmpty() && password.isNotEmpty()
    }
    
    /**
     * Сохраняет дизайн отчета (full или minimal)
     */
    fun saveReportDesign(design: String) {
        prefs.edit().putString("report_design", design).apply()
    }
    
    /**
     * Получает дизайн отчета
     */
    fun getReportDesign(): String {
        return prefs.getString("report_design", "full") ?: "full"
    }
    
    /**
     * Тип действия со ссылкой
     */
    enum class LinkAction(val value: String, val description: String) {
        SMS("sms", "📱 Отправить SMS"),
        EMAIL("email", "📧 Открыть Email"),
        EMAIL_SMTP("email_smtp", "📧 Отправить Email (автоматически)"),
        TELEGRAM("telegram", "💬 Отправить Telegram"),
        COPY("copy", "📋 Скопировать в буфер"),
        SAVE_TO_DEVICE("save_device", "💾 Сохранить на устройстве")
    }
    
    /**
     * Отправляет SMS сообщение с ссылкой
     * @param phone Номер телефона получателя
     * @param message Текст сообщения (можно использовать {url} как плейсхолдер)
     * @param url Ссылка для отправки
     */
    fun sendSms(phone: String, message: String, url: String) {
        val context = this.context
        val actualMessage = message.replace("{url}", url)
        
        try {
            val smsUri = Uri.parse("smsto:$phone")
            val intent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
                putExtra("sms_body", actualMessage)
            }
            context.startActivity(intent)
            Toast.makeText(context, "Открыто приложение SMS", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("SharedPrefsManager", "Failed to send SMS: ${e.message}", e)
            Toast.makeText(context, "Ошибка отправки SMS: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Отправляет Email с ссылкой
     * @param email Email получателя
     * @param subject Тема письма
     * @param body Тело письма (можно использовать {url} как плейсхолдер)
     * @param url Ссылка для отправки
     */
    fun sendEmail(email: String, subject: String, body: String, url: String) {
        val context = this.context
        
        Log.d("SharedPrefsManager", "sendEmail: email='$email', subject='$subject', body='$body', url='$url'")
        
        // Проверяем, что URL не пустой
        if (url.isEmpty()) {
            Log.e("SharedPrefsManager", "sendEmail: URL is empty")
            Toast.makeText(context, "Ошибка: ссылка пустая", Toast.LENGTH_LONG).show()
            return
        }
        
        // Заменяем плейсхолдеры в теле письма и теме
        val actualBody = body.replace("{url}", url)
        val actualSubject = subject.replace("{url}", url)
        
        Log.d("SharedPrefsManager", "sendEmail: actualBody='$actualBody'")
        Log.d("SharedPrefsManager", "sendEmail: actualSubject='$actualSubject'")
        
        try {
            // Используем ACTION_SENDTO с mailto: URI
            val emailUri = Uri.parse("mailto:$email")
            val intent = Intent(Intent.ACTION_SENDTO, emailUri).apply {
                // Используем putExtra для темы и тела письма (это более надежно для mailto)
                putExtra(Intent.EXTRA_SUBJECT, actualSubject)
                putExtra(Intent.EXTRA_TEXT, actualBody)
            }
            
            Log.d("SharedPrefsManager", "sendEmail: Starting email intent with uri=$emailUri")
            context.startActivity(intent)
            Toast.makeText(context, "Открыто приложение Email", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("SharedPrefsManager", "Failed to send Email: ${e.message}", e)
            Toast.makeText(context, "Ошибка отправки Email: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Отправляет сообщение в Telegram
     * @param username Username получателя (без @)
     * @param message Текст сообщения (можно использовать {url} как плейсхолдер)
     * @param url Ссылка для отправки
     */
    fun sendTelegram(username: String, message: String, url: String) {
        val context = this.context
        val actualMessage = message.replace("{url}", url)
        
        try {
            // Пытаемся открыть через приложение Telegram
            val telegramUrl = "https://t.me/$username?text=${URLEncoder.encode(actualMessage, StandardCharsets.UTF_8.name())}"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(telegramUrl)
            }
            context.startActivity(intent)
            Toast.makeText(context, "Открыто приложение Telegram", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("SharedPrefsManager", "Failed to send Telegram: ${e.message}", e)
            
            // Fallback: открыть в браузере
            try {
                val browserUrl = "https://t.me/$username?text=${URLEncoder.encode(actualMessage, StandardCharsets.UTF_8.name())}"
                val browserIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(browserUrl)
                }
                context.startActivity(browserIntent)
            } catch (e2: Exception) {
                Log.e("SharedPrefsManager", "Failed to open Telegram fallback: ${e2.message}", e2)
                Toast.makeText(context, "Ошибка отправки Telegram: ${e2.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * Копирует ссылку в буфер обмена
     */
    fun copyToClipboard(url: String) {
        val context = this.context
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Ohrana Report Link", url)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Ссылка скопирована в буфер обмена", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("SharedPrefsManager", "Failed to copy to clipboard: ${e.message}", e)
            Toast.makeText(context, "Ошибка копирования: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Отправляет ссылку по выбранному каналу
     * @param action Тип действия
     * @param url Ссылка для отправки
     */
    fun sendLinkWithAction(action: LinkAction, url: String) {
        Log.d("SharedPrefsManager", "sendLinkWithAction: action=${action.value}, url='$url'")
        
        when (action) {
            LinkAction.SMS -> {
                val phone = getSmsPhone()
                Log.d("SharedPrefsManager", "sendLinkWithAction: SMS phone='$phone'")
                if (phone.isNotEmpty()) {
                    val template = getSmsTemplate()
                    Log.d("SharedPrefsManager", "sendLinkWithAction: SMS template='$template'")
                    sendSms(phone, template, url)
                } else {
                    Toast.makeText(context, "Введите номер телефона в настройках", Toast.LENGTH_LONG).show()
                }
            }
            LinkAction.EMAIL -> {
                val email = getFileIoEmail()
                Log.d("SharedPrefsManager", "sendLinkWithAction: EMAIL='$email'")
                if (email.isNotEmpty()) {
                    val subject = "Отчет Ohrana"
                    val body = "Отчет загружен в Яндекс.Диск: {url}"
                    Log.d("SharedPrefsManager", "sendLinkWithAction: EMAIL body='$body'")
                    sendEmail(email, subject, body, url)
                } else {
                    Toast.makeText(context, "Введите email в настройках", Toast.LENGTH_LONG).show()
                }
            }
            LinkAction.TELEGRAM -> {
                val username = getFileIoTelegram()
                Log.d("SharedPrefsManager", "sendLinkWithAction: TELEGRAM username='$username'")
                if (username.isNotEmpty()) {
                    val message = "Отчет загружен в Яндекс.Диск: {url}"
                    Log.d("SharedPrefsManager", "sendLinkWithAction: TELEGRAM message='$message'")
                    sendTelegram(username, message, url)
                } else {
                    Toast.makeText(context, "Введите username Telegram в настройках", Toast.LENGTH_LONG).show()
                }
            }
            LinkAction.COPY -> {
                Log.d("SharedPrefsManager", "sendLinkWithAction: COPY url='$url'")
                copyToClipboard(url)
            }
            LinkAction.SAVE_TO_DEVICE -> {
                // Просто сохраняем URL в память для последующего использования
                Log.d("SharedPrefsManager", "Link saved to device: $url")
                Toast.makeText(context, "Ссылка сохранена на устройстве", Toast.LENGTH_SHORT).show()
            }
            LinkAction.EMAIL_SMTP -> {
                // Автоматическая отправка через SMTP (без открытия приложения почты)
                val email = getSmtpRecipient() // Email получателя
                sendLinkViaSmtp(email, url)
            }
        }
    }
    
    /**
     * Отправляет ссылку через SMTP без открытия приложения почты
     * @param email Email получателя
     * @param url Ссылка для отправки
     */
    fun sendLinkViaSmtp(email: String, url: String) {
        val context = this.context
        
        // Проверяем, что email получателя не пустой
        if (email.isEmpty()) {
            Log.e("SharedPrefsManager", "sendLinkViaSmtp: email recipient is empty")
            Toast.makeText(context, "Введите email получателя в настройках", Toast.LENGTH_LONG).show()
            return
        }
        
        val emailManager = EmailManager(context)
        
        if (!emailManager.isSmtpConfigured()) {
            Log.e("SharedPrefsManager", "SMTP not configured")
            Toast.makeText(context, "Настройте SMTP в настройках", Toast.LENGTH_LONG).show()
            return
        }
        
        val subject = "Отчет Ohrana"
        val body = "Отчет загружен в Яндекс.Диск: $url"
        
        // Запускаем отправку в фоновом потоке
        CoroutineScope(Dispatchers.Main).launch {
            val success = withContext(Dispatchers.IO) {
                emailManager.sendSimpleEmail(email, subject, body)
            }
            
            if (success) {
                Log.d("SharedPrefsManager", "Email sent successfully via SMTP to $email")
                Toast.makeText(context, "Письмо отправлено!", Toast.LENGTH_SHORT).show()
            } else {
                Log.e("SharedPrefsManager", "Failed to send email via SMTP")
                Toast.makeText(context, "Ошибка отправки письма", Toast.LENGTH_LONG).show()
            }
        }
    }
}