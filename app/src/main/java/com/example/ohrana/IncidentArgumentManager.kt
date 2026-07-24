package com.example.ohrana

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Менеджер аргументов происшествия
 * Хранит все данные о происшествии во временном JSON файле
 */
class IncidentArgumentManager(private val context: Context) {
    companion object {
        private const val TAG = "IncidentArgumentManager"  // Тег для логов
        private const val INCIDENT_ARGS_DIR = "Ohrana/IncidentArgs"  // Имя папки для аргументов
        private const val INCIDENT_ARGS_PREFIX = "incident_"  // Префикс имен файлов
        private const val INCIDENT_ARGS_SUFFIX = ".json"  // Суффикс имен файлов
    }
    
    private val incidentArgsDir = File(  // Директория для хранения аргументов
        context.getExternalFilesDir(null), 
        INCIDENT_ARGS_DIR
    )
    
    init {
        if (!incidentArgsDir.exists()) {
            incidentArgsDir.mkdirs()
            Log.d(TAG, "Created incident args directory: ${incidentArgsDir.absolutePath}")
        }
    }
    
    /**
     * Создает новый архив с аргументами происшествия
     * Возвращает ID архива
     */
    fun createIncidentArgs(
        shiftId: String,
        roundId: Int?,
        employeeName: String,
        timestamp: String,
        incidentType: IncidentType,
        description: String,
        photoPath: String?
    ): String {
        val id = "incident_${System.currentTimeMillis()}"
        val argsFile = File(incidentArgsDir, "$id$INCIDENT_ARGS_SUFFIX")
        
        val args = JSONObject().apply {
            put("id", id)
            put("shiftId", shiftId)
            put("roundId", roundId ?: -1)
            put("employeeName", employeeName)
            put("timestamp", timestamp)
            put("incidentType", incidentType.name)
            put("description", description)
            put("photoPath", photoPath ?: "")
            put("latitude", "")
            put("longitude", "")
            put("checkpointId", "")
            put("checkpointName", "")
        }
        
        argsFile.writeText(args.toString(4))
        Log.d(TAG, "Created incident args: $id")
        
        return id
    }
    
    /**
     * Загружает аргументы происшествия по ID
     */
    fun loadIncidentArgs(id: String): IncidentArgs? {
        val argsFile = File(incidentArgsDir, "$id$INCIDENT_ARGS_SUFFIX")
        if (!argsFile.exists()) {
            Log.e(TAG, "Incident args not found: $id")
            return null
        }
        
        return try {
            val jsonString = argsFile.readText()
            val json = JSONObject(jsonString)
            IncidentArgs(
                id = json.getString("id"),
                shiftId = json.getString("shiftId"),
                roundId = if (json.getInt("roundId") == -1) null else json.getInt("roundId"),
                employeeName = json.getString("employeeName"),
                timestamp = json.getString("timestamp"),
                incidentType = IncidentType.valueOf(json.getString("incidentType")),
                description = json.getString("description"),
                photoPath = if (json.getString("photoPath").isNotBlank()) json.getString("photoPath") else null,
                latitude = if (json.getString("latitude").isNotBlank()) json.getString("latitude").toDouble() else null,
                longitude = if (json.getString("longitude").isNotBlank()) json.getString("longitude").toDouble() else null,
                checkpointId = if (json.getString("checkpointId").isNotBlank()) json.getString("checkpointId") else null,
                checkpointName = if (json.getString("checkpointName").isNotBlank()) json.getString("checkpointName") else null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading incident args: $id", e)
            null
        }
    }
    
    /**
     * Обновляет описание в аргументах происшествия
     */
    fun updateDescription(id: String, newDescription: String): Boolean {
        return try {
            val argsFile = File(incidentArgsDir, "$id$INCIDENT_ARGS_SUFFIX")
            if (!argsFile.exists()) {
                Log.e(TAG, "Incident args not found: $id")
                return false
            }
            
            val jsonString = argsFile.readText()
            val json = JSONObject(jsonString)
            json.put("description", newDescription)
            
            argsFile.writeText(json.toString(4))
            Log.d(TAG, "Updated description for incident: $id")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating description: $id", e)
            false
        }
    }
    
    /**
     * Обновляет путь к фото в аргументах происшествия
     */
    fun updatePhotoPath(id: String, newPhotoPath: String?): Boolean {
        return try {
            val argsFile = File(incidentArgsDir, "$id$INCIDENT_ARGS_SUFFIX")
            if (!argsFile.exists()) {
                Log.e(TAG, "Incident args not found: $id")
                return false
            }
            
            val jsonString = argsFile.readText()
            val json = JSONObject(jsonString)
            json.put("photoPath", newPhotoPath ?: "")
            
            argsFile.writeText(json.toString(4))
            Log.d(TAG, "Updated photo path for incident: $id")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating photo path: $id", e)
            false
        }
    }
    
    /**
     * Обновляет координаты в аргументах происшествия
     */
    fun updateCoordinates(id: String, latitude: Double?, longitude: Double?): Boolean {
        return try {
            val argsFile = File(incidentArgsDir, "$id$INCIDENT_ARGS_SUFFIX")
            if (!argsFile.exists()) {
                Log.e(TAG, "Incident args not found: $id")
                return false
            }
            
            val jsonString = argsFile.readText()
            val json = JSONObject(jsonString)
            json.put("latitude", latitude?.toString() ?: "")
            json.put("longitude", longitude?.toString() ?: "")
            
            argsFile.writeText(json.toString(4))
            Log.d(TAG, "Updated coordinates for incident: $id")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating coordinates: $id", e)
            false
        }
    }
    
    /**
     * Обновляет информацию о чекпоинте в аргументах происшествия
     */
    fun updateCheckpointInfo(id: String, checkpointId: String?, checkpointName: String?): Boolean {
        return try {
            val argsFile = File(incidentArgsDir, "$id$INCIDENT_ARGS_SUFFIX")
            if (!argsFile.exists()) {
                Log.e(TAG, "Incident args not found: $id")
                return false
            }
            
            val jsonString = argsFile.readText()
            val json = JSONObject(jsonString)
            json.put("checkpointId", checkpointId ?: "")
            json.put("checkpointName", checkpointName ?: "")
            
            argsFile.writeText(json.toString(4))
            Log.d(TAG, "Updated checkpoint info for incident: $id")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating checkpoint info: $id", e)
            false
        }
    }
    
    /**
     * Удаляет аргументы происшествия
     */
    fun deleteIncidentArgs(id: String): Boolean {
        val argsFile = File(incidentArgsDir, "$id$INCIDENT_ARGS_SUFFIX")
        return try {
            if (argsFile.exists()) {
                argsFile.delete()
                Log.d(TAG, "Deleted incident args: $id")
                true
            } else {
                Log.e(TAG, "Incident args not found for deletion: $id")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting incident args: $id", e)
            false
        }
    }
    
    /**
     * Загружает все активные аргументы происшествий для смены
     */
    fun loadActiveIncidentArgs(shiftId: String): List<IncidentArgs> {
        if (!incidentArgsDir.exists()) {
            return emptyList()
        }
        
        return incidentArgsDir.listFiles()
            ?.filter { it.name.startsWith(INCIDENT_ARGS_PREFIX) && it.name.endsWith(INCIDENT_ARGS_SUFFIX) }
            ?.mapNotNull { 
                try {
                    val jsonString = it.readText()
                    val json = JSONObject(jsonString)
                    if (json.getString("shiftId") == shiftId) {
                        IncidentArgs(
                            id = json.getString("id"),
                            shiftId = json.getString("shiftId"),
                            roundId = if (json.getInt("roundId") == -1) null else json.getInt("roundId"),
                            employeeName = json.getString("employeeName"),
                            timestamp = json.getString("timestamp"),
                            incidentType = IncidentType.valueOf(json.getString("incidentType")),
                            description = json.getString("description"),
                            photoPath = if (json.getString("photoPath").isNotBlank()) json.getString("photoPath") else null,
                            latitude = if (json.getString("latitude").isNotBlank()) json.getString("latitude").toDouble() else null,
                            longitude = if (json.getString("longitude").isNotBlank()) json.getString("longitude").toDouble() else null,
                            checkpointId = if (json.getString("checkpointId").isNotBlank()) json.getString("checkpointId") else null,
                            checkpointName = if (json.getString("checkpointName").isNotBlank()) json.getString("checkpointName") else null
                        )
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading incident args from file: ${it.name}", e)
                    null
                }
            } ?: emptyList()
    }
    
    /**
     * Очищает все аргументы происшествий
     */
    fun clearAllIncidentArgs(): Boolean {
        return try {
            incidentArgsDir.listFiles()
                ?.filter { it.name.startsWith(INCIDENT_ARGS_PREFIX) && it.name.endsWith(INCIDENT_ARGS_SUFFIX) }
                ?.forEach { it.delete() }
            Log.d(TAG, "Cleared all incident args")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing incident args", e)
            false
        }
    }
}

/**
 * Класс для хранения аргументов происшествия
 */
data class IncidentArgs(
    val id: String,              // Уникальный ID архива
    val shiftId: String,         // ID смены
    val roundId: Int?,           // ID обхода (null если вне обхода)
    val employeeName: String,    // Имя охранника
    val timestamp: String,       // Время и дата создания
    val incidentType: IncidentType,  // Тип происшествия
    val description: String,     // Описание происшествия
    val photoPath: String?,      // Путь к фото
    val latitude: Double?,       // Широта (GPS)
    val longitude: Double?,      // Долгота (GPS)
    val checkpointId: String?,   // ID чекпоинта
    val checkpointName: String?  // Название чекпоинта
) {
    fun toJSONObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("shiftId", shiftId)
            put("roundId", roundId ?: -1)
            put("employeeName", employeeName)
            put("timestamp", timestamp)
            put("incidentType", incidentType.name)
            put("description", description)
            put("photoPath", photoPath ?: "")
            put("latitude", latitude?.toString() ?: "")
            put("longitude", longitude?.toString() ?: "")
            put("checkpointId", checkpointId ?: "")
            put("checkpointName", checkpointName ?: "")
        }
    }
}
