package com.example.ohrana

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.ohrana.ShiftRecord
import com.example.ohrana.GuardMember

// ============================================
// УПРАВЛЕНИЕ БАЗОЙ ДАННЫХ ОБХОДОВ
// ============================================

class ShiftDatabaseManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("ohrana_shift_db", Context.MODE_PRIVATE)
    
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US)
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)

    // ============================================
    // СМЕНЫ (SHIFTS)
    // ============================================

    /**
     * Считать количество смен для определенной даты
     */
    private fun getShiftCountForDate(date: String): Int {
        val allShifts = loadAllShifts()
        val datePrefix = date.substring(0, 10) // "01.06.2026"
        return allShifts.filter { it.startTime.startsWith(datePrefix) }.size
    }

    /**
     * Сформировать ID смены в формате NSDDMMYY_NNN
     */
    private fun generateShiftId(): String {
        val today = Date()
        val todayStr = dateFormat.format(today)
        val datePrefix = todayStr.substring(0, 10) // "01.06.2026"
        
        // Извлекаем день, месяц, год
        val parts = datePrefix.split(".")
        val day = parts[0] // 01
        val month = parts[1] // 06
        val year = parts[2].substring(2) // 26
        
        // Получаем количество смен за сегодня
        val count = getShiftCountForDate(datePrefix)
        val sequence = String.format("%03d", count + 1)
        
        // Формируем ID: NS010626_001
        return "NS${day}${month}${year}_$sequence"
    }

    /**
     * Начать новую смену
     */
    fun startNewShift(
        employeeName: String, 
        guardList: List<GuardMember>,
        strictSequenceEnabled: Boolean
    ): String {
        val shiftId = generateShiftId()
        val currentTime = dateFormat.format(Date())
        
        val shift = ShiftRecord(
            id = shiftId,
            employeeName = employeeName,
            guardList = guardList,
            startTime = currentTime,
            isShiftActive = true,
            strictSequenceEnabled = strictSequenceEnabled
        )
        
        saveShift(shift)
        return shiftId
    }

    /**
     * Завершить смену
     */
    fun closeShift(shiftId: String) {
        val currentTime = dateFormat.format(Date())
        
        // Загружаем текущую смену
        val shift = loadAllShifts().find { it.id == shiftId }
        shift?.let {
            val updatedShift = shift.copy(
                endTime = currentTime,
                isShiftActive = false
            )
            saveShift(updatedShift)
        }
        
        // Обновляем все обходы этой смены
        val rounds = loadAllRounds()
        rounds.forEach { round ->
            if (round.shiftId == shiftId && !round.isCompleted) {
                completeRound(round.id, currentTime)
            }
        }
    }

    /**
     * Получить активную смену
     */
    fun getActiveShift(): ShiftRecord? {
        val allShifts = loadAllShifts()
        return allShifts.find { it.isShiftActive }
    }

    /**
     * Сохранить смену
     */
    private fun saveShift(shift: ShiftRecord) {
        val json = JSONObject().apply {
            put("id", shift.id)
            put("employeeName", shift.employeeName)
            
            // Сохраняем список охранников
            val guardsArray = JSONArray()
            shift.guardList.forEach { guard ->
                val guardObj = JSONObject()
                guardObj.put("nfcId", guard.nfcId)
                guardObj.put("name", guard.name)
                guardObj.put("role", guard.role)
                guard.startTime?.let { guardObj.put("startTime", it) }
                guardsArray.put(guardObj)
            }
            put("guardList", guardsArray)
            
            put("startTime", shift.startTime)
            shift.endTime?.let { put("endTime", it) }
            put("isShiftActive", shift.isShiftActive)
            put("strictSequenceEnabled", shift.strictSequenceEnabled)
        }
        
        prefs.edit().putString("shift_${shift.id}", json.toString()).apply()
    }

    /**
     * Загрузить все смены
     */
    fun loadAllShifts(): List<ShiftRecord> {
        val all = prefs.all ?: return emptyList()
        return all.filter { it.key.startsWith("shift_") }
            .mapNotNull { 
                try {
                    val json = JSONObject(it.value as String)
                    // Загружаем список охранников
                    val guardList = if (json.has("guardList")) {
                        val guardsArray = json.getJSONArray("guardList")
                        List(guardsArray.length()) { index ->
                            val guardObj = guardsArray.getJSONObject(index)
                            GuardMember(
                                nfcId = guardObj.optString("nfcId", ""),
                                name = guardObj.optString("name", ""),
                                role = guardObj.optString("role", "охранник"),
                                startTime = if (guardObj.has("startTime")) guardObj.getString("startTime") else null
                            )
                        }
                    } else {
                        // Для совместимости с旧ими данными
                        listOf(GuardMember(
                            nfcId = "",
                            name = json.getString("employeeName"),
                            role = "охранник"
                        ))
                    }
                    
                    ShiftRecord(
                        id = json.getString("id"),
                        employeeName = json.getString("employeeName"),
                        guardList = guardList,
                        startTime = json.getString("startTime"),
                        endTime = if (json.has("endTime")) json.getString("endTime") else null,
                        isShiftActive = json.optBoolean("isShiftActive", false),
                        strictSequenceEnabled = json.optBoolean("strictSequenceEnabled", false)
                    )
                } catch (e: Exception) {
                    null
                }
            }
    }

    // ============================================
    // ОБХОДЫ (ROUNDS)
    // ============================================

    /**
     * Начать новый обход
     */
    fun startRound(roundId: Int, shiftId: String, routeId: String?, routeName: String?, checkpointsCount: Int): RoundRecord {
        val currentTime = dateFormat.format(Date())
        
        val round = RoundRecord(
            id = roundId,
            shiftId = shiftId,
            startTime = currentTime,
            routeId = routeId,
            routeName = routeName,
            checkpointsCount = checkpointsCount
        )
        
        saveRound(round)
        return round
    }

    /**
     * Завершить обход
     */
    fun completeRound(roundId: Int, endTime: String) {
        // Загружаем текущий обход
        val round = loadRound(roundId)
        round?.let {
            // Подсчитываем количество пройденных чекпоинтов на основе логов
            val logs = loadLogsByShift(round.shiftId).filter { it.roundId == roundId }
            val checkpointsPassed = logs.filter { it.actionType != "SCAN" }.size
            
            val updatedRound = round.copy(
                endTime = endTime,
                isCompleted = true,
                checkpointsPassed = checkpointsPassed
            )
            saveRound(updatedRound)
        }
    }

    /**
     * Обновить количество пройденных чекпоинтов
     * Подсчитывает на основе логов для точности
     */
    fun updateRoundProgress(roundId: Int, sequenceViolations: Int) {
        // Загружаем текущий обход
        val round = loadRound(roundId)
        round?.let {
            // Подсчитываем количество пройденных чекпоинтов на основе логов
            val logs = loadLogsByShift(round.shiftId).filter { it.roundId == roundId }
            val checkpointsPassed = logs.filter { it.actionType != "SCAN" }.size
            
            val updatedRound = round.copy(
                checkpointsPassed = checkpointsPassed,
                sequenceViolations = sequenceViolations
            )
            saveRound(updatedRound)
        }
    }

    /**
     * Сохранить обход
     */
    private fun saveRound(round: RoundRecord) {
        val json = JSONObject().apply {
            put("id", round.id)
            put("shiftId", round.shiftId)
            put("startTime", round.startTime)
            round.endTime?.let { put("endTime", it) }
            put("isCompleted", round.isCompleted)
            round.routeId?.let { put("routeId", it) }
            round.routeName?.let { put("routeName", it) }
            put("checkpointsCount", round.checkpointsCount)
            put("checkpointsPassed", round.checkpointsPassed)
            put("sequenceViolations", round.sequenceViolations)
        }
        
        prefs.edit().putString("round_${round.id}", json.toString()).apply()
    }

    /**
     * Загрузить обход по ID
     */
    fun loadRound(roundId: Int): RoundRecord? {
        val jsonString = prefs.getString("round_$roundId", null) ?: return null
        
        return try {
            val json = JSONObject(jsonString)
            RoundRecord(
                id = json.getInt("id"),
                shiftId = json.getString("shiftId"),
                startTime = json.getString("startTime"),
                endTime = if (json.has("endTime")) json.getString("endTime") else null,
                isCompleted = json.optBoolean("isCompleted", false),
                routeId = if (json.has("routeId")) json.getString("routeId") else null,
                routeName = if (json.has("routeName")) json.getString("routeName") else null,
                checkpointsCount = json.optInt("checkpointsCount", 0),
                checkpointsPassed = json.optInt("checkpointsPassed", 0),
                sequenceViolations = json.optInt("sequenceViolations", 0)
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Загрузить все обходы
     */
    fun loadAllRounds(): List<RoundRecord> {
        val all = prefs.all ?: return emptyList()
        return all.filter { it.key.startsWith("round_") }
            .mapNotNull { 
                val roundId = it.key.removePrefix("round_").toIntOrNull() ?: return@mapNotNull null
                loadRound(roundId)
            }
    }

    // ============================================
    // ЛОГИ ОБХОДОВ (LOGS)
    // ============================================

    /**
     * Добавить запись в лог
     */
    fun addLogEntry(
        checkpointName: String,
        checkpointId: String,
        employeeName: String,
        roundId: Int,
        routeName: String,
        sequenceIndex: Int,
        isSequenceCorrect: Boolean,
        scanType: String,
        actionType: String,
        answer: String? = null,
        inputValue: String? = null,
        photoPath: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        sequenceErrorExpected: String? = null
    ): String {
        val logId = "log_${System.currentTimeMillis()}"
        val timestamp = dateFormat.format(Date())
        
        // Получаем ID активной смены из SharedPreferences
        val prefs = context.getSharedPreferences("ohrana_prefs", Context.MODE_PRIVATE)
        val shiftId = prefs.getString("active_shift_id", "unknown_shift") ?: "unknown_shift"
        
        val entry = ShiftLogEntry(
            id = logId,
            timestamp = timestamp,
            checkpointName = checkpointName,
            checkpointId = checkpointId,
            employeeName = employeeName,
            roundId = roundId,
            shiftId = shiftId,
            routeName = routeName,
            sequenceIndex = sequenceIndex,
            isSequenceCorrect = isSequenceCorrect,
            scanType = scanType,
            actionType = actionType,
            answer = answer,
            inputValue = inputValue,
            photoPath = photoPath,
            latitude = latitude,
            longitude = longitude,
            sequenceErrorExpected = sequenceErrorExpected
        )
        
        saveLogEntry(entry)
        return logId
    }

    /**
     * Добавить запись о нарушении последовательности
     */
    fun addSequenceViolation(
        employeeName: String,
        roundId: Int,
        expectedCheckpointId: String,
        expectedCheckpointName: String,
        actualCheckpointId: String,
        actualCheckpointName: String,
        isNfc: Boolean = false
    ): String {
        val violationId = "violation_${System.currentTimeMillis()}"
        val timestamp = dateFormat.format(Date())
        
        val violation = SequenceViolation(
            id = violationId,
            timestamp = timestamp,
            employeeName = employeeName,
            roundId = roundId,
            expectedCheckpointId = expectedCheckpointId,
            expectedCheckpointName = expectedCheckpointName,
            actualCheckpointId = actualCheckpointId,
            actualCheckpointName = actualCheckpointName,
            isNfc = isNfc
        )
        
        saveViolation(violation)
        return violationId
    }

    /**
     * Сохранить запись лога
     */
    private fun saveLogEntry(entry: ShiftLogEntry) {
        val json = JSONObject().apply {
            put("id", entry.id)
            put("timestamp", entry.timestamp)
            put("checkpointName", entry.checkpointName)
            put("checkpointId", entry.checkpointId)
            put("employeeName", entry.employeeName)
            put("roundId", entry.roundId)
            put("shiftId", entry.shiftId)
            put("routeName", entry.routeName)
            put("sequenceIndex", entry.sequenceIndex)
            put("isSequenceCorrect", entry.isSequenceCorrect)
            put("scanType", entry.scanType)
            put("actionType", entry.actionType)
            entry.answer?.let { put("answer", it) }
            entry.inputValue?.let { put("inputValue", it) }
            entry.photoPath?.let { put("photoPath", it) }
            entry.latitude?.let { put("latitude", it) }
            entry.longitude?.let { put("longitude", it) }
            entry.sequenceErrorExpected?.let { put("sequenceErrorExpected", it) }
        }
        
        prefs.edit().putString("log_${entry.id}", json.toString()).apply()
    }

    /**
     * Сохранить запись о нарушении
     */
    private fun saveViolation(violation: SequenceViolation) {
        val json = JSONObject().apply {
            put("id", violation.id)
            put("timestamp", violation.timestamp)
            put("employeeName", violation.employeeName)
            put("roundId", violation.roundId)
            put("expectedCheckpointId", violation.expectedCheckpointId)
            put("expectedCheckpointName", violation.expectedCheckpointName)
            put("actualCheckpointId", violation.actualCheckpointId)
            put("actualCheckpointName", violation.actualCheckpointName)
            put("isNfc", violation.isNfc)
        }
        
        prefs.edit().putString("violation_${violation.id}", json.toString()).apply()
    }

    /**
     * Загрузить все логи
     */
    fun loadAllLogs(): List<ShiftLogEntry> {
        val all = prefs.all ?: return emptyList()
        return all.filter { it.key.startsWith("log_") }
            .mapNotNull { 
                try {
                    val json = JSONObject(it.value as String)
                    ShiftLogEntry(
                        id = json.getString("id"),
                        timestamp = json.getString("timestamp"),
                        checkpointName = json.getString("checkpointName"),
                        checkpointId = json.getString("checkpointId"),
                        employeeName = json.getString("employeeName"),
                        roundId = json.getInt("roundId"),
                        shiftId = json.getString("shiftId"),
                        routeName = json.getString("routeName"),
                        sequenceIndex = json.getInt("sequenceIndex"),
                        isSequenceCorrect = json.getBoolean("isSequenceCorrect"),
                        scanType = json.getString("scanType"),
                        actionType = json.getString("actionType"),
                        answer = if (json.has("answer")) json.getString("answer") else null,
                        inputValue = if (json.has("inputValue")) json.getString("inputValue") else null,
                        photoPath = if (json.has("photoPath")) json.getString("photoPath") else null,
                        latitude = json.optDouble("latitude", Double.NaN).takeIf { !it.isNaN() },
                        longitude = json.optDouble("longitude", Double.NaN).takeIf { !it.isNaN() },
                        sequenceErrorExpected = if (json.has("sequenceErrorExpected")) json.getString("sequenceErrorExpected") else null
                    )
                } catch (e: Exception) {
                    null
                }
            }
    }

    /**
     * Загрузить логи по ID обхода
     */
    fun loadLogsByRound(roundId: Int): List<ShiftLogEntry> {
        return loadAllLogs().filter { it.roundId == roundId }
    }

    /**
     * Загрузить логи по ID смены
     */
    fun loadLogsByShift(shiftId: String): List<ShiftLogEntry> {
        return loadAllLogs().filter { logEntry -> logEntry.shiftId == shiftId }
    }

    /**
     * Загрузить все нарушения
     */
    fun loadAllViolations(): List<SequenceViolation> {
        val all = prefs.all ?: return emptyList()
        return all.filter { it.key.startsWith("violation_") }
            .mapNotNull { 
                try {
                    val json = JSONObject(it.value as String)
                    SequenceViolation(
                        id = json.getString("id"),
                        timestamp = json.getString("timestamp"),
                        employeeName = json.getString("employeeName"),
                        roundId = json.getInt("roundId"),
                        expectedCheckpointId = json.getString("expectedCheckpointId"),
                        expectedCheckpointName = json.getString("expectedCheckpointName"),
                        actualCheckpointId = json.getString("actualCheckpointId"),
                        actualCheckpointName = json.getString("actualCheckpointName"),
                        isNfc = json.optBoolean("isNfc", false)
                    )
                } catch (e: Exception) {
                    null
                }
            }
    }

    /**
     * Загрузить нарушения по ID обхода
     */
    fun loadViolationsByRound(roundId: Int): List<SequenceViolation> {
        return loadAllViolations().filter { it.roundId == roundId }
    }
    
    /**
     * Обновить последнюю запись SCAN на правильный тип действия
     * Возвращает true если запись найдена и обновлена
     */
    fun updateLastScanEntry(
        roundId: Int,
        actionType: String,
        answer: String? = null,
        inputValue: String? = null,
        photoPath: String? = null
    ): Boolean {
        val logs = loadAllLogs().filter { it.roundId == roundId }
        // Ищем последнюю запись, которая имеет тип SCAN (еще не обновлена)
        val lastScanEntry = logs.asReversed().find { it.actionType == "SCAN" }
        
        android.util.Log.d("ShiftDatabaseManager", "updateLastScanEntry: roundId=$roundId, actionType=$actionType, found=${lastScanEntry != null}")
        lastScanEntry?.let { android.util.Log.d("ShiftDatabaseManager", "  - Updating log: ${it.checkpointName} (${it.checkpointId})") }
        
        lastScanEntry?.let { entry ->
            val updatedEntry = entry.copy(
                actionType = actionType,
                answer = answer,
                inputValue = inputValue,
                photoPath = photoPath
            )
            
            // Удаляем старую запись и сохраняем новую
            prefs.edit().remove("log_${entry.id}").apply()
            saveLogEntry(updatedEntry)
            android.util.Log.d("ShiftDatabaseManager", "  - Log updated successfully")
            return true
        }
        
        android.util.Log.d("ShiftDatabaseManager", "  - No SCAN entry found to update")
        return false
    }

    // ============================================
    // ГЕНЕРАЦИЯ ОТЧЕТОВ
    // ============================================

    /**
     * Генерировать полный отчет для администратора
     */
    fun generateAdminReport(shiftId: String): AdminReport? {
        val shift = loadAllShifts().find { it.id == shiftId } ?: return null
        val rounds = loadAllRounds().filter { it.shiftId == shiftId }
        val logs = loadLogsByShift(shiftId)
        val violations = loadAllViolations().filter { it.roundId in rounds.map { it.id } }
        
        return AdminReport(shift, rounds, logs, violations)
    }

    /**
     * Генерировать упрощенный отчет для охранника
     */
    fun generateGuardReport(shiftId: String): GuardReport? {
        val shift = loadAllShifts().find { it.id == shiftId } ?: return null
        val rounds = loadAllRounds().filter { it.shiftId == shiftId }
        val logs = loadLogsByShift(shiftId)
        val violations = loadAllViolations().filter { it.roundId in rounds.map { it.id } }
        
        val simpleLogs = logs.map { 
            val result = when (it.actionType) {
                "QUESTION" -> "Ответ: ${it.answer ?: "-"}"
                "INPUT" -> "Введено: ${it.inputValue ?: "-"}"
                "PHOTO" -> "Фото снято"
                else -> "Пройдено"
            }
            SimpleLogEntry(it.timestamp, it.checkpointName, result)
        }
        
        return GuardReport(
            shiftStartTime = shift.startTime,
            shiftEndTime = shift.endTime,
            roundsCount = rounds.size,
            roundsCompleted = rounds.count { it.isCompleted },
            checkpointsTotal = rounds.sumOf { it.checkpointsCount },
            checkpointsPassed = rounds.sumOf { it.checkpointsPassed },
            sequenceViolations = violations.size,
            logs = simpleLogs
        )
    }

    // ============================================
    // ОЧИСТКА ДАННЫХ
    // ============================================

    /**
     * Очистить все данные старше указанной даты
     */
    fun clearOldData(days: Int) {
        val cutoffDate = System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000)
        val all = prefs.all ?: return
        
        val keysToRemove = all.filterKeys { key ->
            when {
                key.startsWith("shift_") -> {
                    val shiftJson = prefs.getString(key, null) ?: return@filterKeys false
                    try {
                        val shift = JSONObject(shiftJson)
                        val timestamp = isoFormat.parse(shift.getString("startTime"))?.time ?: return@filterKeys false
                        timestamp < cutoffDate
                    } catch (e: Exception) {
                        false
                    }
                }
                else -> false
            }
        }.keys.toList()
        
        val editor = prefs.edit()
        keysToRemove.forEach { key -> editor.remove(key) }
        editor.apply()
    }
}
