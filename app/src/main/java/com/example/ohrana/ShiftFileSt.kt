package com.example.ohrana

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Информация о файле смены
 */
data class ShiftFileInfo(
    val filePath: String,
    val fileName: String,
    val shiftId: String,
    val date: String?,
    val lastModified: Long
)

/**
 * Данные смены для загрузки из файла
 */
data class ShiftFileData(
    val shiftId: String,
    val startTime: String,
    val endTime: String?,
    val guards: List<GuardMember>,
    val rounds: List<RoundRecord>,
    val logs: List<ShiftLogEntry>,
    val violations: List<SequenceViolation>,
    val incidents: List<IncidentRecord>,
    val staffEvents: List<StaffEvent>,
    val guardMessages: List<GuardMessage>,
    val stats: Map<String, Any>
)

/**
 * Файловая система хранения данных смен.
 * Каждая смена = один JSON-файл в папке /Download/Ohrana/Shifts/
 */
object ShiftFileSt {

    private const val TAG = "ShiftFileSt"
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)

    // ============================================
    // СОХРАНЕНИЕ СМЕНЫ В ФАЙЛ
    // ============================================

    @JvmStatic
    fun saveShiftFile(
        shift: ShiftRecord,
        rounds: List<RoundRecord>,
        logs: List<ShiftLogEntry>,
        violations: List<SequenceViolation>,
        incidents: List<IncidentRecord>,
        staffEvents: List<StaffEvent>,
        guardMessages: List<GuardMessage>,
        ctx: Context
    ): String? {
        return try {
            val dateStr = shift.startTime.substring(0, 10)
            val fileName = "shift_${shift.id}_${convertDateForFileName(dateStr)}.json"
            val dir = getShiftDirectory(ctx)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)

            val json = createJson(shift, rounds, logs, violations, incidents, staffEvents, guardMessages)

            file.writeText(json.toString(2))
            Log.d(TAG, "Shift file saved: ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving shift file: ${e.message}", e)
            null
        }
    }

    // ============================================
    // ЗАГРУЗКА СМЕНЫ ИЗ ФАЙЛА
    // ============================================

    @JvmStatic
    fun loadShiftFile(shiftId: String, ctx: Context): ShiftFileData? {
        return try {
            val filePath = getShiftFilePath(shiftId, ctx)
            if (filePath == null) return null
            val file = File(filePath)
            if (!file.exists()) return null

            val jsonText = file.readText()
            val json = JSONObject(jsonText)

            val loadedShiftId = json.getString("shiftId")
            val startTime = json.getString("startTime")
            val endTime = if (json.has("endTime") && !json.isNull("endTime")) json.getString("endTime") else null

            val guards = mutableListOf<GuardMember>()
            json.optJSONArray("guards")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val g = arr.getJSONObject(i)
                    guards.add(GuardMember(
                        nfcId = g.getString("nfcId"),
                        name = g.getString("name"),
                        role = g.getString("role"),
                        startTime = if (g.has("startTime") && !g.isNull("startTime")) g.getString("startTime") else null
                    ))
                }
            }

            val rounds = mutableListOf<RoundRecord>()
            json.optJSONArray("rounds")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val r = arr.getJSONObject(i)
                    rounds.add(RoundRecord(
                        id = r.getInt("id"),
                        shiftId = r.getString("shiftId"),
                        startTime = r.getString("startTime"),
                        endTime = if (r.has("endTime") && !r.isNull("endTime")) r.getString("endTime") else null,
                        isCompleted = r.getBoolean("isCompleted"),
                        routeId = if (r.has("routeId") && !r.isNull("routeId")) r.getString("routeId") else null,
                        routeName = if (r.has("routeName") && !r.isNull("routeName")) r.getString("routeName") else null,
                        checkpointsCount = r.optInt("checkpointsCount", 0),
                        checkpointsPassed = r.optInt("checkpointsPassed", 0),
                        sequenceViolations = r.optInt("sequenceViolations", 0)
                    ))
                }
            }

            val logs = mutableListOf<ShiftLogEntry>()
            json.optJSONArray("logs")?.let { arr ->
                for (i in 0 until arr.length()) {
                    logs.add(logFromJson(arr.getJSONObject(i)))
                }
            }

            val violations = mutableListOf<SequenceViolation>()
            json.optJSONArray("violations")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val v = arr.getJSONObject(i)
                    violations.add(SequenceViolation(
                        id = v.getString("id"),
                        timestamp = v.getString("timestamp"),
                        employeeName = v.getString("employeeName"),
                        roundId = v.getInt("roundId"),
                        shiftId = v.getString("shiftId"),
                        expectedCheckpointId = v.getString("expectedCheckpointId"),
                        expectedCheckpointName = v.getString("expectedCheckpointName"),
                        actualCheckpointId = v.getString("actualCheckpointId"),
                        actualCheckpointName = v.getString("actualCheckpointName"),
                        sequenceErrorType = try { SequenceErrorType.valueOf(v.getString("sequenceErrorType")) } catch (e: Exception) { SequenceErrorType.OUT_OF_SEQUENCE },
                        isNfc = v.optBoolean("isNfc", false)
                    ))
                }
            }

            val incidents = mutableListOf<IncidentRecord>()
            json.optJSONArray("incidents")?.let { arr ->
                for (i in 0 until arr.length()) {
                    incidents.add(incidentFromJson(arr.getJSONObject(i)))
                }
            }

            val staffEvents = mutableListOf<StaffEvent>()
            json.optJSONArray("staffEvents")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val s = arr.getJSONObject(i)
                    staffEvents.add(StaffEvent(
                        id = s.getString("id"),
                        timestamp = s.getString("timestamp"),
                        shiftId = s.getString("shiftId"),
                        staffId = s.getString("staffId"),
                        staffName = s.getString("staffName"),
                        eventType = try { EventType.valueOf(s.getString("eventType")) } catch (e: Exception) { EventType.NOTE },
                        customText = if (s.has("customText") && !s.isNull("customText")) s.getString("customText") else null,
                        templateText = if (s.has("templateText") && !s.isNull("templateText")) s.getString("templateText") else null
                    ))
                }
            }

            val guardMessages = mutableListOf<GuardMessage>()
            json.optJSONArray("guardMessages")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val m = arr.getJSONObject(i)
                    guardMessages.add(GuardMessage(
                        id = m.getString("id"),
                        shiftId = m.getString("shiftId"),
                        timestamp = m.getString("timestamp"),
                        guardName = m.getString("guardName"),
                        text = m.getString("text")
                    ))
                }
            }

            val stats = mutableMapOf<String, Any>()
            json.optJSONObject("stats")?.let { st ->
                st.keys().forEachRemaining { key ->
                    stats[key] = st.get(key)
                }
            }

            ShiftFileData(
                shiftId = loadedShiftId,
                startTime = startTime,
                endTime = endTime,
                guards = guards,
                rounds = rounds,
                logs = logs,
                violations = violations,
                incidents = incidents,
                staffEvents = staffEvents,
                guardMessages = guardMessages,
                stats = stats
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading shift file: ${e.message}", e)
            null
        }
    }

    // ============================================
    // УПРАВЛЕНИЕ ФАЙЛАМИ
    // ============================================

    @JvmStatic
    fun getShiftFilePath(shiftId: String, ctx: Context): String? {
        val dateStr = try {
            convertDateForFileName(SimpleDateFormat("dd.MM.yyyy", Locale.US).format(Date()))
        } catch (e: Exception) {
            fileDateFormat.format(Date())
        }
        val fileName = "shift_${shiftId}_${dateStr}.json"
        val dir = getShiftDirectory(ctx)
        val file = File(dir, fileName)
        return if (file.exists()) file.absolutePath else null
    }

    @JvmStatic
    fun deleteShiftFile(shiftId: String, ctx: Context): Boolean {
        val filePath = getShiftFilePath(shiftId, ctx)
        return if (filePath != null) {
            val file = File(filePath)
            val deleted = file.delete()
            Log.d(TAG, "Deleted shift file: ${file.name}, success=$deleted")
            deleted
        } else {
            false
        }
    }

    @JvmStatic
    fun getAllShiftFiles(ctx: Context): List<ShiftFileInfo> {
        val dir = getShiftDirectory(ctx)
        return dir.listFiles()?.filter { it.name.startsWith("shift_") && it.name.endsWith(".json") }
            ?.map { file ->
                val fileName = file.name
                val shiftId = extractShiftIdFromName(fileName)
                val datePart = fileName.removePrefix("shift_").removeSuffix(".json").substringAfterLast("_")
                val date = try {
                    SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US).format(
                        SimpleDateFormat("yyyyMMdd", Locale.US).parse(datePart)
                    )?.substring(0, 10)
                } catch (e: Exception) {
                    null
                }
                ShiftFileInfo(
                    filePath = file.absolutePath,
                    fileName = fileName,
                    shiftId = shiftId,
                    date = date,
                    lastModified = file.lastModified()
                )
            }?.sortedByDescending { it.lastModified } ?: emptyList()
    }

    @JvmStatic
    fun cleanupOldShiftFiles(daysToKeep: Int, ctx: Context): Int {
        val cutoffDate = Date(System.currentTimeMillis() - daysToKeep * 24L * 60 * 60 * 1000)
        val dir = getShiftDirectory(ctx)
        var deletedCount = 0
        dir.listFiles()?.filter { it.name.startsWith("shift_") && it.name.endsWith(".json") }?.forEach { file ->
            val datePart = file.name.removePrefix("shift_").removeSuffix(".json").substringAfterLast("_")
            try {
                val parsedDate = SimpleDateFormat("yyyyMMdd", Locale.US).parse(datePart)?.time ?: 0L
                if (parsedDate < cutoffDate.time) {
                    file.delete()
                    deletedCount++
                    Log.d(TAG, "Deleted old shift file: ${file.name}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not parse date for cleanup: ${file.name}")
            }
        }
        Log.d(TAG, "Cleanup completed: $deletedCount files deleted")
        return deletedCount
    }

    // ============================================
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ============================================

    private fun convertDateForFileName(dateStr: String): String {
        return try {
            val parts = dateStr.split(".")
            "${parts[2]}${parts[1]}${parts[0]}"
        } catch (e: Exception) {
            fileDateFormat.format(Date())
        }
    }

    private fun extractShiftIdFromName(fileName: String): String {
        return fileName.removePrefix("shift_")
            .removeSuffix(".json")
            .substringBeforeLast("_")
    }

    private fun getShiftDirectory(ctx: Context): File {
        val externalDir = ctx.getExternalFilesDir(null)
            ?: return File(ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                ?: ctx.filesDir, "Ohrana")
        return File(externalDir, "Ohrana/Shifts").also { it.mkdirs() }
    }

    private fun createJson(
        shift: ShiftRecord,
        rounds: List<RoundRecord>,
        logs: List<ShiftLogEntry>,
        violations: List<SequenceViolation>,
        incidents: List<IncidentRecord>,
        staffEvents: List<StaffEvent>,
        guardMessages: List<GuardMessage>
    ): JSONObject {
        val json = JSONObject()

        json.put("shiftId", shift.id)
        json.put("startTime", shift.startTime)
        if (shift.endTime != null) json.put("endTime", shift.endTime) else json.put("endTime", JSONObject.NULL)
        json.put("strictSequenceEnabled", shift.strictSequenceEnabled)

        val guardsArray = JSONArray()
        shift.guardList.forEach { g ->
            val obj = JSONObject()
            obj.put("nfcId", g.nfcId)
            obj.put("name", g.name)
            obj.put("role", g.role)
            if (g.startTime != null) obj.put("startTime", g.startTime) else obj.put("startTime", JSONObject.NULL)
            guardsArray.put(obj)
        }
        json.put("guards", guardsArray)

        val roundsArray = JSONArray()
        rounds.forEach { r ->
            val obj = JSONObject()
            obj.put("id", r.id)
            obj.put("shiftId", r.shiftId)
            obj.put("startTime", r.startTime)
            if (r.endTime != null) obj.put("endTime", r.endTime) else obj.put("endTime", JSONObject.NULL)
            obj.put("isCompleted", r.isCompleted)
            if (r.routeId != null) obj.put("routeId", r.routeId) else obj.put("routeId", JSONObject.NULL)
            if (r.routeName != null) obj.put("routeName", r.routeName) else obj.put("routeName", JSONObject.NULL)
            obj.put("checkpointsCount", r.checkpointsCount)
            obj.put("checkpointsPassed", r.checkpointsPassed)
            obj.put("sequenceViolations", r.sequenceViolations)
            roundsArray.put(obj)
        }
        json.put("rounds", roundsArray)

        val logsArray = JSONArray()
        logs.forEach { logsArray.put(logToJson(it)) }
        json.put("logs", logsArray)

        val violationsArray = JSONArray()
        violations.forEach { v ->
            val obj = JSONObject()
            obj.put("id", v.id)
            obj.put("timestamp", v.timestamp)
            obj.put("employeeName", v.employeeName)
            obj.put("roundId", v.roundId)
            obj.put("shiftId", v.shiftId)
            obj.put("expectedCheckpointId", v.expectedCheckpointId)
            obj.put("expectedCheckpointName", v.expectedCheckpointName)
            obj.put("actualCheckpointId", v.actualCheckpointId)
            obj.put("actualCheckpointName", v.actualCheckpointName)
            obj.put("sequenceErrorType", v.sequenceErrorType.name)
            obj.put("isNfc", v.isNfc)
            violationsArray.put(obj)
        }
        json.put("violations", violationsArray)

        val incidentsArray = JSONArray()
        incidents.forEach { incidentsArray.put(incidentToJson(it)) }
        json.put("incidents", incidentsArray)

        val staffArray = JSONArray()
        staffEvents.forEach { ev ->
            val obj = JSONObject()
            obj.put("id", ev.id)
            obj.put("timestamp", ev.timestamp)
            obj.put("shiftId", ev.shiftId)
            obj.put("staffId", ev.staffId)
            obj.put("staffName", ev.staffName)
            obj.put("eventType", ev.eventType.name)
            if (ev.customText != null) obj.put("customText", ev.customText) else obj.put("customText", JSONObject.NULL)
            if (ev.templateText != null) obj.put("templateText", ev.templateText) else obj.put("templateText", JSONObject.NULL)
            staffArray.put(obj)
        }
        json.put("staffEvents", staffArray)

        val messagesArray = JSONArray()
        guardMessages.forEach { m ->
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("shiftId", m.shiftId)
            obj.put("timestamp", m.timestamp)
            obj.put("guardName", m.guardName)
            obj.put("text", m.text)
            messagesArray.put(obj)
        }
        json.put("guardMessages", messagesArray)

        val stats = JSONObject()
        stats.put("completedRounds", rounds.count { it.isCompleted })
        stats.put("totalViolations", violations.size)
        stats.put("totalIncidents", incidents.size)
        stats.put("totalLogs", logs.size)
        stats.put("totalStaffEvents", staffEvents.size)
        stats.put("totalGuardMessages", guardMessages.size)
        json.put("stats", stats)

        return json
    }

    // ============================================
    // СЕРИАЛИЗАЦИЯ/ДЕСЕРИАЛИЗАЦИЯ JSON
    // ============================================

    private fun logToJson(log: ShiftLogEntry): JSONObject {
        val obj = JSONObject()
        obj.put("id", log.id)
        obj.put("timestamp", log.timestamp)
        obj.put("checkpointName", log.checkpointName)
        obj.put("checkpointId", log.checkpointId)
        obj.put("employeeName", log.employeeName)
        obj.put("roundId", log.roundId)
        obj.put("shiftId", log.shiftId)
        obj.put("routeName", log.routeName)
        obj.put("sequenceIndex", log.sequenceIndex)
        obj.put("isSequenceCorrect", log.isSequenceCorrect)
        obj.put("scanType", log.scanType)
        obj.put("actionType", log.actionType)
        if (log.questionText != null) obj.put("questionText", log.questionText)
        if (log.inputTitle != null) obj.put("inputTitle", log.inputTitle)
        if (log.answer != null) obj.put("answer", log.answer)
        if (log.inputValue != null) obj.put("inputValue", log.inputValue)
        if (log.photoPath != null) obj.put("photoPath", log.photoPath)
        log.latitude?.let { obj.put("latitude", it) }
        log.longitude?.let { obj.put("longitude", it) }
        obj.put("sequenceErrorType", log.sequenceErrorType.name)
        obj.put("hasAborted", log.hasAborted)
        return obj
    }

    private fun logFromJson(json: JSONObject): ShiftLogEntry {
        return ShiftLogEntry(
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
            questionText = if (json.has("questionText")) json.getString("questionText") else null,
            inputTitle = if (json.has("inputTitle")) json.getString("inputTitle") else null,
            answer = if (json.has("answer")) json.getString("answer") else null,
            inputValue = if (json.has("inputValue")) json.getString("inputValue") else null,
            photoPath = if (json.has("photoPath")) json.getString("photoPath") else null,
            latitude = if (json.has("latitude")) json.optDouble("latitude", 0.0) else null,
            longitude = if (json.has("longitude")) json.optDouble("longitude", 0.0) else null,
            sequenceErrorType = try {
                SequenceErrorType.valueOf(json.getString("sequenceErrorType"))
            } catch (e: Exception) {
                SequenceErrorType.NONE
            },
            hasAborted = json.optBoolean("hasAborted", false)
        )
    }

    private fun incidentToJson(incident: IncidentRecord): JSONObject {
        val obj = JSONObject()
        obj.put("id", incident.id)
        obj.put("timestamp", incident.timestamp)
        obj.put("shiftId", incident.shiftId)
        obj.put("roundId", incident.roundId)
        obj.put("employeeName", incident.employeeName)
        obj.put("incidentType", incident.incidentType.name)
        obj.put("description", incident.description)
        obj.put("photoPath", incident.photoPath)
        incident.latitude?.let { obj.put("latitude", it) }
        incident.longitude?.let { obj.put("longitude", it) }
        return obj
    }

    private fun incidentFromJson(json: JSONObject): IncidentRecord {
        return IncidentRecord(
            id = json.getString("id"),
            timestamp = json.getString("timestamp"),
            shiftId = json.getString("shiftId"),
            roundId = json.getInt("roundId"),
            employeeName = json.getString("employeeName"),
            incidentType = try {
                IncidentType.valueOf(json.getString("incidentType"))
            } catch (e: Exception) {
                IncidentType.OTHER
            },
            description = json.getString("description"),
            photoPath = json.getString("photoPath"),
            latitude = if (json.has("latitude")) json.optDouble("latitude", 0.0) else null,
            longitude = if (json.has("longitude")) json.optDouble("longitude", 0.0) else null
        )
    }
}