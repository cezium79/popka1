package com.example.ohrana

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Менеджер архивации данных смен старше 7 дней
 * Копирует данные в папку "Ohrana/archive" и удаляет из базы
 */
class ArchiveManager(private val context: Context) {
    private val archiveDir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        "Ohrana/archive"
    )
    
    private val TAG = "ArchiveManager"
    private val archiveDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.US)
    private val archiveFileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    
    /**
     * Инициализирует папку архива
     */
    fun init() {
        if (!archiveDir.exists()) {
            archiveDir.mkdirs()
            Log.d(TAG, "Archive directory created: ${archiveDir.absolutePath}")
        }
    }
    
    /**
     * Копирует данные смены в архив
     * @param shiftId ID смены
     * @param shiftDatabase Менеджер базы данных смен
     * @return true если успешно, false если ошибка
     */
    fun archiveShift(shiftId: String, shiftDatabase: ShiftDatabaseManager): Boolean {
        init()
        
        try {
            val shift = shiftDatabase.loadAllShifts().find { it.id == shiftId } ?: return false
            
            // Генерируем имя файла на основе даты и времени архивации
            val archiveFileName = "shift_${shiftId}_${archiveFileNameFormat.format(Date())}.json"
            val archiveFile = File(archiveDir, archiveFileName)
            
            // Создаем JSON-объект с данными смены
            val jsonData = org.json.JSONObject().apply {
                put("id", shift.id)
                put("employeeName", shift.employeeName)
                put("guardList", org.json.JSONArray().apply {
                    shift.guardList.forEach { guard ->
                        put(org.json.JSONObject().apply {
                            put("nfcId", guard.nfcId)
                            put("name", guard.name)
                            put("role", guard.role)
                            guard.startTime?.let { put("startTime", it) }
                        })
                    }
                })
                put("startTime", shift.startTime)
                put("endTime", shift.endTime)
                put("isShiftActive", shift.isShiftActive)
                put("strictSequenceEnabled", shiftDatabase.isStrictSequenceEnabledForShift(shiftId))
            }
            
            // Записываем данные в файл
            archiveFile.writeText(jsonData.toStringPretty())
            
            Log.d(TAG, "Shift archived: ${archiveFile.absolutePath}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error archiving shift: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Копирует данные всех обходов смены в архив
     * @param shiftId ID смены
     * @param shiftDatabase Менеджер базы данных смен
     * @return Количество архивированных обходов
     */
    fun archiveShiftRounds(shiftId: String, shiftDatabase: ShiftDatabaseManager): Int {
        var archivedCount = 0
        
        try {
            val rounds = shiftDatabase.loadAllRounds().filter { it.shiftId == shiftId }
            
            rounds.forEach { round ->
                val roundFileName = "round_${round.id}_${archiveFileNameFormat.format(Date())}.json"
                val roundFile = File(archiveDir, roundFileName)
                
                val roundData = org.json.JSONObject().apply {
                    put("id", round.id)
                    put("shiftId", round.shiftId)
                    put("routeId", round.routeId)
                    put("routeName", round.routeName)
                    put("startTime", round.startTime)
                    put("endTime", round.endTime)
                    put("checkpointsCount", round.checkpointsCount)
                    put("checkpointsPassed", round.checkpointsPassed)
                    put("sequenceViolations", round.sequenceViolations)
                    put("isCompleted", round.isCompleted)
                }
                
                roundFile.writeText(roundData.toStringPretty())
                archivedCount++
            }
            
            Log.d(TAG, "Archived $archivedCount rounds for shift $shiftId")
        } catch (e: Exception) {
            Log.e(TAG, "Error archiving rounds: ${e.message}", e)
        }
        
        return archivedCount
    }
    
    /**
     * Копирует данные логов смены в архив
     * @param shiftId ID смены
     * @param shiftDatabase Менеджер базы данных смен
     * @return Количество архивированных логов
     */
    fun archiveShiftLogs(shiftId: String, shiftDatabase: ShiftDatabaseManager): Int {
        var archivedCount = 0
        
        try {
            val logs = shiftDatabase.loadAllLogs().filter { it.shiftId == shiftId }
            
            logs.forEach { log ->
                val logFileName = "log_${log.id}_${archiveFileNameFormat.format(Date())}.json"
                val logFile = File(archiveDir, logFileName)
                
                val logData = org.json.JSONObject().apply {
                    put("id", log.id)
                    put("shiftId", log.shiftId)
                    put("roundId", log.roundId)
                    put("checkpointId", log.checkpointId)
                    put("checkpointName", log.checkpointName)
                    put("timestamp", log.timestamp)
                    put("employeeName", log.employeeName)
                    put("scanType", log.scanType)
                    put("actionType", log.actionType)
                    put("isSequenceCorrect", log.isSequenceCorrect)
                    log.questionText?.let { put("questionText", it) }
                    log.inputTitle?.let { put("inputTitle", it) }
                    log.answer?.let { put("answer", it) }
                    log.inputValue?.let { put("inputValue", it) }
                    log.photoPath?.let { put("photoPath", it) }
                    log.latitude?.let { put("latitude", it) }
                    log.longitude?.let { put("longitude", it) }
                    put("sequenceErrorType", log.sequenceErrorType.name)
                }
                
                logFile.writeText(logData.toStringPretty())
                archivedCount++
            }
            
            Log.d(TAG, "Archived $archivedCount logs for shift $shiftId")
        } catch (e: Exception) {
            Log.e(TAG, "Error archiving logs: ${e.message}", e)
        }
        
        return archivedCount
    }
    
    /**
     * Копирует данные нарушений смены в архив
     * @param shiftId ID смены
     * @param shiftDatabase Менеджер базы данных смен
     * @return Количество архивированных нарушений
     */
    fun archiveShiftViolations(shiftId: String, shiftDatabase: ShiftDatabaseManager): Int {
        var archivedCount = 0
        
        try {
            val violations = shiftDatabase.loadAllViolations().filter { it.shiftId == shiftId }
            
            violations.forEach { violation ->
                val violationFileName = "violation_${violation.id}_${archiveFileNameFormat.format(Date())}.json"
                val violationFile = File(archiveDir, violationFileName)
                
                val violationData = org.json.JSONObject().apply {
                    put("id", violation.id)
                    put("shiftId", violation.shiftId)
                    put("roundId", violation.roundId)
                    put("timestamp", violation.timestamp)
                    put("employeeName", violation.employeeName)
                    put("expectedCheckpointId", violation.expectedCheckpointId)
                    put("expectedCheckpointName", violation.expectedCheckpointName)
                    put("actualCheckpointId", violation.actualCheckpointId)
                    put("actualCheckpointName", violation.actualCheckpointName)
                    put("sequenceErrorType", violation.sequenceErrorType.name)
                    put("isNfc", violation.isNfc)
                }
                
                violationFile.writeText(violationData.toStringPretty())
                archivedCount++
            }
            
            Log.d(TAG, "Archived $archivedCount violations for shift $shiftId")
        } catch (e: Exception) {
            Log.e(TAG, "Error archiving violations: ${e.message}", e)
        }
        
        return archivedCount
    }
    
    /**
     * Архивирует все данные смены (смену, обходы, логи, нарушения)
     * @param shiftId ID смены
     * @param shiftDatabase Менеджер базы данных смен
     * @return true если успешно, false если ошибка
     */
    fun archiveCompleteShift(shiftId: String, shiftDatabase: ShiftDatabaseManager): Boolean {
        val shiftArchived = archiveShift(shiftId, shiftDatabase)
        val roundsArchived = archiveShiftRounds(shiftId, shiftDatabase)
        val logsArchived = archiveShiftLogs(shiftId, shiftDatabase)
        val violationsArchived = archiveShiftViolations(shiftId, shiftDatabase)
        
        val totalArchived = 1 + roundsArchived + logsArchived + violationsArchived
        Log.d(TAG, "Complete shift archive: $totalArchived files for shift $shiftId")
        
        return shiftArchived
    }
    
    /**
     * Находит все смены старше указанного количества дней
     * @param daysCount Количество дней
     * @param shiftDatabase Менеджер базы данных смен
     * @return Список ID смен старше указанного срока
     */
    fun findOldShifts(daysCount: Int, shiftDatabase: ShiftDatabaseManager): List<String> {
        val oldShifts = mutableListOf<String>()
        val cutoffDate = Date(System.currentTimeMillis() - daysCount * 24 * 60 * 60 * 1000L)
        
        try {
            val allShifts = shiftDatabase.loadAllShifts()
            
            allShifts.forEach { shift ->
                // Пытаемся распарсить время окончания смены
                shift.endTime?.let { endTime ->
                    try {
                        val endTimeDate = shiftDatabase.parseShiftTime(endTime)
                        if (endTimeDate != null && endTimeDate.before(cutoffDate)) {
                            oldShifts.add(shift.id)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not parse end time: ${endTime}", e)
                    }
                }
            }
            
            Log.d(TAG, "Found ${oldShifts.size} old shifts (older than $daysCount days)")
        } catch (e: Exception) {
            Log.e(TAG, "Error finding old shifts: ${e.message}", e)
        }
        
        return oldShifts
    }
    
    /**
     * Удаляет данные смены из базы данных
     * @param shiftId ID смены
     * @param shiftDatabase Менеджер базы данных смен
     */
    fun removeFromDatabase(shiftId: String, shiftDatabase: ShiftDatabaseManager) {
        try {
            // Удаляем смену (это должно удалить и связанные данные)
            shiftDatabase.deleteShift(shiftId)
            Log.d(TAG, "Removed shift from database: $shiftId")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing shift from database: ${e.message}", e)
        }
    }
    
    /**
     * Архивирует и удаляет смены старше 7 дней
     * @param shiftDatabase Менеджер базы данных смен
     * @return Количество архивированных смен
     */
    fun archiveAndRemoveOldShifts(shiftDatabase: ShiftDatabaseManager): Int {
        init()
        
        val oldShifts = findOldShifts(7, shiftDatabase)
        var archivedCount = 0
        
        oldShifts.forEach { shiftId ->
            try {
                // Архивируем данные
                archiveCompleteShift(shiftId, shiftDatabase)
                
                // Удаляем из базы
                removeFromDatabase(shiftId, shiftDatabase)
                
                archivedCount++
            } catch (e: Exception) {
                Log.e(TAG, "Error processing shift $shiftId: ${e.message}", e)
            }
        }
        
        Log.d(TAG, "Archived and removed $archivedCount old shifts")
        return archivedCount
    }
    
    /**
     * Получает список всех архивных файлов
     * @return Список архивных файлов
     */
    fun getArchiveFiles(): List<File> {
        init()
        return archiveDir.listFiles()?.sortedBy { it.lastModified() } ?: emptyList()
    }
    
    /**
     * Получает размер архивной папки в байтах
     * @return Размер архива
     */
    fun getArchiveSize(): Long {
        init()
        return archiveDir.listFiles().sumOf { it.length() } ?: 0L
    }
    
    /**
     * Очищает архив старше указанного количества дней
     * @param daysCount Количество дней
     */
    fun cleanupOldArchive(daysCount: Int) {
        init()
        
        try {
            val cutoffDate = Date(System.currentTimeMillis() - daysCount * 24 * 60 * 60 * 1000L)
            val archiveFiles = getArchiveFiles()
            
            archiveFiles.forEach { file ->
                if (file.lastModified() < cutoffDate.time) {
                    file.delete()
                    Log.d(TAG, "Deleted old archive file: ${file.name}")
                }
            }
            
            Log.d(TAG, "Cleaned up archive older than $daysCount days")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up archive: ${e.message}", e)
        }
    }
}

// Расширение для красивого вывода JSON
private fun org.json.JSONObject.toStringPretty(): String {
    return this.toString(2)
}
