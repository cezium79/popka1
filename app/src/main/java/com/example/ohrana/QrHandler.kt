package com.example.ohrana

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.ohrana.CheckpointAction
import com.example.ohrana.Checkpoint
import com.example.ohrana.SequenceErrorType

// 1. Все возможные исходы сканирования (добавлены новые типы)
sealed class QrResult {
    data class CheckpointPassed(val checkpointId: String, val name: String, val timestamp: String) : QrResult()
    data class SequenceError(val expectedCheckpointId: String?, val message: String) : QrResult()
    data class QuestionFormat(val checkpointId: String, val checkpointName: String, val text: String, val answers: List<String>) : QrResult()
    data class InputFormat(val checkpointId: String, val checkpointName: String, val title: String) : QrResult()
    data class PhotoFormat(val checkpointId: String, val checkpointName: String, val imageUri: String? = null) : QrResult()
    data class Error(val message: String) : QrResult()
}

// Extension properties для обратной совместимости
val QrResult.QuestionFormat.questionText: String get() = text
val QrResult.QuestionFormat.answersList: List<String> get() = answers
val QrResult.InputFormat.titleText: String get() = title
val QrResult.PhotoFormat.photoCheckpointId: String get() = checkpointId
val QrResult.PhotoFormat.photoCheckpointName: String get() = checkpointName

// Extension property для NFC-чекпоинтов
val QrResult.CheckpointPassed.nfcCheckpointId: String get() = checkpointId


/**
 * Внутренний класс, представляющий маршрут обхода.
 */
private class PatrolRoute(val routeName: String, private val checkpoints: List<String>) {
    /**
     * Проверяет ID чекпоинта и, если он верный, продвигает обход к следующей точке.
     * Использует SharedPrefsManager как источник истины для текущего индекса чекпоинта.
     * Возвращает Pair(валидность, следующий ожидаемый ID или null если маршрут завершен)
     */
    fun validateAndAdvance(checkpointId: String, prefsManager: SharedPrefsManager): Pair<Boolean, String?> {
        // Берём актуальный индекс из SharedPrefsManager — это источник истины
        val activeRoundIndex = prefsManager.getActiveRoundIndex()
        val currentIndex = if (activeRoundIndex != -1) {
            prefsManager.getRoundCheckpointIndex(activeRoundIndex)
        } else {
            0
        }

        // Проверяем, совпадает ли отсканированный ID с ожидаемым
        if (currentIndex < checkpoints.size && checkpoints[currentIndex] == checkpointId) {
            // Проверяем, следующий ли это чекпоинт
            if (currentIndex + 1 >= checkpoints.size) {
                return Pair(true, null)
            }

            // Возвращаем следующий ожидаемый ID
            val nextExpectedId = checkpoints[currentIndex + 1]
            return Pair(true, nextExpectedId)

        } else {
            // В случае ошибки возвращаем тот ID, который ожидался
            val expectedId =
                if (currentIndex < checkpoints.size) checkpoints[currentIndex] else null
            return Pair(false, expectedId)
        }
    }
    
    /**
     * Проверяет, находится ли чекпоинт в маршруте
     */
    fun containsCheckpoint(checkpointId: String): Boolean {
        return checkpoints.contains(checkpointId)
    }
}

// Модель для записи в лог
data class CheckpointEntry(
    val type: String,
    val titleOrLocation: String,
    val userResult: String,
    val timestamp: String
)

object QrHandler {
    // Единый формат даты для всего класса
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US)

    // Список логов завершенных или текущих смен
    private val shiftLogs = mutableListOf<CheckpointEntry>()
    
    // Буфер состояния чекпоинта (в памяти) — используется для сбора данных до сохранения в БД
    private var lastCheckpointState: CheckpointState? = null

    /**
     * Очищает shiftLogs - вызывается при открытии/закрытии смены
     */
    fun clearShiftLogs() {
        shiftLogs.clear()
    }
    
    /**
     * Очищает буфер состояний чекпоинтов
     */
    fun clearCheckpointState() {
        lastCheckpointState = null
    }
    
    /**
     * Получить последнее состояние чекпоинта из буфера
     */
    fun getLastCheckpointState(): CheckpointState? {
        return lastCheckpointState
    }
    
    /**
     * Отменить прохождение чекпоинта (нажата кнопка "Назад")
     * Устанавливает hasAborted = true и сохраняет запись в БД
     */
    fun abortLastCheckpointState(prefsManager: SharedPrefsManager) {
        val state = lastCheckpointState ?: return
        
        if (state.hasAborted) return
        
        state.hasAborted = true
        
        val activeRoundIndex = prefsManager.getActiveRoundIndex()
        val activeShiftId = prefsManager.prefs.getString("active_shift_id", null)
        
        android.util.Log.d("QrHandler", "abortLastCheckpointState: roundIndex=$activeRoundIndex, shiftId=$activeShiftId, checkpoint=${state.checkpointName}")
        
        activeShiftId?.let { shiftId ->
            if (activeRoundIndex != -1) {
                prefsManager.shiftDatabase.addLogEntry(
                    checkpointName = state.checkpointName,
                    checkpointId = state.checkpointId,
                    employeeName = state.employeeName,
                    roundId = activeRoundIndex,
                    routeName = "Маршрут обхода",
                    sequenceIndex = state.sequenceIndex,
                    isSequenceCorrect = state.isSequenceCorrect,
                    scanType = state.scanType,
                    actionType = "SCAN",
                    questionText = state.questionText,
                    inputTitle = state.inputTitle,
                    hasAborted = true
                )
                android.util.Log.d("QrHandler", "Checkpoint aborted: ${state.checkpointName}")
            } else {
                android.util.Log.e("QrHandler", "Cannot abort checkpoint: activeRoundIndex is -1")
            }
        } ?: run {
            android.util.Log.e("QrHandler", "Cannot abort checkpoint: activeShiftId is null")
        }
        
        prefsManager.removeCheckpointState(state.checkpointId)
        lastCheckpointState = null
    }

    // Активные маршруты обхода. Ключ - уникальное имя смены/маршрута.
// Модификатор 'private' скрывает карту от внешнего доступа,
// а внутренние функции QrHandler могут работать с ней через публичные методы.
    private val activeRounds = mutableMapOf<String, PatrolRoute>()

    // Ключ для маршрута по умолчанию (единственного в проекте)
    private const val DEFAULT_ROUND_KEY = "Маршрут_Смены_1"

    /**
     * Основная функция парсинга QR-кода.
     * Логика изменена: сначала берется только ID из QR-кода,
     * затем вся информация загружается из базы данных чекпоинтов.
     * QR-коды без ID в базе данных игнорируются.
     */
    fun parseQrCode(rawText: String, prefsManager: SharedPrefsManager): QrResult {

        val trimmed = rawText.trim()

        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return QrResult.Error("Игнорируется: это не рабочий QR-код системы")
        }

        return try {
            val json = JSONObject(trimmed)

            // --- НОВАЯ ЛОГИКА ---
            // 1. Сначала пробуем получить только ID из QR-кода
            val checkpointId = json.optString("id", "").trim()
            
            if (checkpointId.isEmpty()) {
                return QrResult.Error("Неизвестный QR-код")
            }
            
            // 2. Ищем чекпоинт в базе данных по этому ID
            val checkpointFromDatabase = prefsManager.getCheckpointById(checkpointId)
            
            if (checkpointFromDatabase == null) {
                // Чекпоинт не найден в базе данных - ЧУЖЕРОДНАЯ МЕТКА
                // Сохраняем факт сканирования для аудита
                val activeRoundIndex = prefsManager.getActiveRoundIndex()
                val activeShiftId = prefsManager.prefs.getString("active_shift_id", null)
                val activeEmployeeName = prefsManager.getActiveShiftEmployeeName()
                
                activeShiftId?.let { shiftId ->
                    if (activeRoundIndex != -1) {
                        prefsManager.shiftDatabase.addLogEntry(
                            checkpointName = "Неизвестный QR",
                            checkpointId = checkpointId,
                            employeeName = activeEmployeeName,
                            roundId = activeRoundIndex,
                            routeName = "Маршрут обхода",
                            sequenceIndex = prefsManager.getCurrentCheckpointIndex(),
                            isSequenceCorrect = false,
                            scanType = "QR",
                            actionType = "SCAN",
                            sequenceErrorType = SequenceErrorType.FOREIGN_CHECKPOINT
                        )
                    }
                }
                
                return QrResult.SequenceError(
                    null,
                    "Чужеродная метка: QR-код не найден в базе данных чекпоинтов."
                )
            }
            
            // 3. Если чекпоинт найден, берем все данные из базы данных
            val name = checkpointFromDatabase.name
            val currentTime = dateFormat.format(Date())
            val action = checkpointFromDatabase.action
            
            // Сохраняем статус контроля последовательности при первом сканировании
            prefsManager.saveSequenceControlStatus(prefsManager.isStrictSequenceEnabled())
            
            // НЕСТРОГИЙ РЕЖИМ: просто сохраняем факт прохода, без проверки последовательности
            if (!prefsManager.isStrictSequenceEnabled()) {
                // Создаем CheckpointState вместо SCAN записи
                createCheckpointState(
                    prefsManager = prefsManager,
                    checkpointId = checkpointId,
                    checkpointName = name,
                    checkpointAction = action,
                    roundId = prefsManager.getActiveRoundIndex(),
                    sequenceIndex = prefsManager.getCurrentCheckpointIndex(),
                    isSequenceCorrect = true,
                    scanType = "QR"
                )
                
                // НЕ обновляем индекс при сканировании - обновление будет при подтверждении прохождения
                // Индекс будет увеличен в OhrannikCabinetScreen при закрытии диалога
                // или в PhotoCaptureScreen при сохранении фото
                
                // Действия на основе типа чекпоинта
                return when (action) {
                    CheckpointAction.QUESTION -> {
                        val questionText = checkpointFromDatabase.questionText ?: ""
                        val answersList = checkpointFromDatabase.answers.ifEmpty { emptyList() }
                        QrResult.QuestionFormat(checkpointId, name, questionText, answersList)
                    }
                    CheckpointAction.INPUT -> {
                        val title = checkpointFromDatabase.inputTitle ?: "Показания"
                        QrResult.InputFormat(checkpointId, name, title)
                    }
                    CheckpointAction.PHOTO -> {
                        val imageUri = checkpointFromDatabase.imageUri
                        QrResult.PhotoFormat(checkpointId, name, imageUri)
                    }
                    else -> {
                        QrResult.CheckpointPassed(checkpointId, name, currentTime)
                    }
                }
            }
            
            // СТРОГИЙ РЕЖИМ: проверяем последовательность
            val activeRoute = activeRounds[DEFAULT_ROUND_KEY]
            
            // Загружаем ID чекпоинтов из настроек активного маршрута
            val routeId = prefsManager.getActiveRoundRouteId()
            val route = routeId?.let { prefsManager.getRouteById(it) }
            val routeCheckpoints = route?.checkpointIds ?: prefsManager.getAllCheckpointIds()
            
            // Проверяем, находится ли чекпоинт вообще в маршруте
            val isCheckpointInRoute = routeCheckpoints.contains(checkpointId)
            
            // Если маршрута нет - создаем и сразу проверяем первую точку
            val (isValid, expectedId) = if (activeRoute == null) {
                startNewRound(DEFAULT_ROUND_KEY, routeCheckpoints)
                activeRounds[DEFAULT_ROUND_KEY]!!.validateAndAdvance(checkpointId, prefsManager)
            } else {
                activeRoute.validateAndAdvance(checkpointId, prefsManager)
            }
            
            if (!isValid) {
                // Если чекпоинт не в маршруте - это "ВНЕ МАРШРУТА"
                if (!isCheckpointInRoute) {
                    // Сохраняем факт сканирования (для аудита)
                    val activeRoundIndex = prefsManager.getActiveRoundIndex()
                    val activeShiftId = prefsManager.prefs.getString("active_shift_id", null)
                    val activeEmployeeName = prefsManager.getActiveShiftEmployeeName()
                    
                    activeShiftId?.let { shiftId ->
                        if (activeRoundIndex != -1) {
                            prefsManager.shiftDatabase.addLogEntry(
                                checkpointName = name,
                                checkpointId = checkpointId,
                                employeeName = activeEmployeeName,
                                roundId = activeRoundIndex,
                                routeName = "Маршрут обхода",
                                sequenceIndex = prefsManager.getCurrentCheckpointIndex(),
                                isSequenceCorrect = false,
                                scanType = "QR",
                                actionType = "SCAN",
                                sequenceErrorType = SequenceErrorType.OUTSIDE_ROUTE
                            )
                        }
                    }
                    
                    return QrResult.SequenceError(
                        null,
                        "Чекпоинт '${name}' не найден в текущем маршруте обхода."
                    )
                }
                
                // Сохраняем факт сканирования (для аудита) даже при нарушении последовательности
                val activeRoundIndex = prefsManager.getActiveRoundIndex()
                val activeShiftId = prefsManager.prefs.getString("active_shift_id", null)
                val activeEmployeeName = prefsManager.getActiveShiftEmployeeName()
                
                activeShiftId?.let { shiftId ->
                    if (activeRoundIndex != -1) {
                        prefsManager.shiftDatabase.addLogEntry(
                            checkpointName = name,
                            checkpointId = checkpointId,
                            employeeName = activeEmployeeName,
                            roundId = activeRoundIndex,
                            routeName = "Маршрут обхода",
                            sequenceIndex = prefsManager.getCurrentCheckpointIndex(),
                            isSequenceCorrect = false,
                            scanType = "QR",
                            actionType = "SCAN",
                            sequenceErrorType = SequenceErrorType.OUT_OF_SEQUENCE
                        )
                    }
                }
                
                // Записываем нарушение последовательности в базу данных
                activeShiftId?.let { shiftId ->
                    if (activeRoundIndex != -1) {
                        prefsManager.shiftDatabase.addSequenceViolation(
                            employeeName = activeEmployeeName,
                            roundId = activeRoundIndex,
                            shiftId = shiftId,
                            expectedCheckpointId = expectedId ?: "",
                            expectedCheckpointName = "Чекпоинт #${expectedId ?: ""}",
                            actualCheckpointId = checkpointId,
                            actualCheckpointName = name,
                            sequenceErrorType = SequenceErrorType.OUT_OF_SEQUENCE,
                            isNfc = false
                        )
                    }
                }
                
                return QrResult.SequenceError(
                    expectedId ?: "",
                    "Нарушена последовательность обхода."
                )
            }

            // Если проверка пройдена, создаем CheckpointState
            createCheckpointState(
                prefsManager = prefsManager,
                checkpointId = checkpointId,
                checkpointName = name,
                checkpointAction = action,
                roundId = prefsManager.getActiveRoundIndex(),
                sequenceIndex = prefsManager.getCurrentCheckpointIndex(),
                isSequenceCorrect = true,
                scanType = "QR"
            )
            
            // НЕ обновляем индекс при сканировании - обновление будет при подтверждении прохождения
            // Индекс будет увеличен в OhrannikCabinetScreen при закрытии диалога
            // или в PhotoCaptureScreen при сохранении фото
            // activeRoute?.let { prefsManager.updateCurrentCheckpointIndex(it.currentIndex) }

            // Если маршрут завершен, сбрасываем его
            if (expectedId == null) {
                endRoundIfActive()
            }

            // Действия на основе типа чекпоинта
            when (action) {
                CheckpointAction.QUESTION -> {
                    val questionText = checkpointFromDatabase.questionText ?: ""
                    val answersList = checkpointFromDatabase.answers.ifEmpty { emptyList() }
                    QrResult.QuestionFormat(checkpointId, name, questionText, answersList)
                }
                CheckpointAction.INPUT -> {
                    val title = checkpointFromDatabase.inputTitle ?: "Показания"
                    QrResult.InputFormat(checkpointId, name, title)
                }
                CheckpointAction.PHOTO -> {
                    val imageUri = checkpointFromDatabase.imageUri
                    QrResult.PhotoFormat(checkpointId, name, imageUri)
                }
                else -> {
                    QrResult.CheckpointPassed(checkpointId, name, currentTime)
                }
            }
            
        } catch (e: Exception) {
            return QrResult.Error("Игнорируется: невалидный JSON-формат")
        }
    }

    fun startNewRound(roundName: String, checkpointIds: List<String>) {
        activeRounds[roundName] = PatrolRoute(roundName, checkpointIds)
    }

    /**
     * Создает CheckpointState и сохраняет его в буфер.
     * Данные будут сохранены в БД позже через CheckpointPassedDialog.
     */
    fun createCheckpointState(
        prefsManager: SharedPrefsManager,
        checkpointId: String,
        checkpointName: String,
        checkpointAction: CheckpointAction,
        roundId: Int,
        sequenceIndex: Int,
        isSequenceCorrect: Boolean,
        scanType: String = "QR",
        routeName: String = "Маршрут обхода"
    ): CheckpointState? {
        val activeShiftId = prefsManager.prefs.getString("active_shift_id", null)
        val activeEmployeeName = prefsManager.getActiveShiftEmployeeName()
        
        activeShiftId ?: return null
        if (roundId == -1) return null
        
        // Загружаем чекпоинт для получения вопроса и заголовка
        val checkpoint = prefsManager.getCheckpointById(checkpointId)
        
        // Создаем состояние чекпоинта
        val state = CheckpointState(
            checkpointId = checkpointId,
            checkpointName = checkpointName,
            checkpointAction = checkpointAction,
            roundId = roundId,
            scanType = scanType,
            scanTimestamp = dateFormat.format(Date()),
            sequenceIndex = sequenceIndex,
            isSequenceCorrect = isSequenceCorrect,
            employeeName = activeEmployeeName,
            questionText = checkpoint?.questionText,
            inputTitle = checkpoint?.inputTitle
        )
        
        // Сохраняем в буфер
        lastCheckpointState = state
        prefsManager.setCheckpointState(state)
        
        // Сохраняем лог в SharedPreferences для Excel-отчета
        val logText = "Чекпоинт: $checkpointName"
        prefsManager.saveScanResult("Маршрут", logText)
        
        return state
    }
    
    /**
     * Завершает активный обход, если он был начат.
     * Эту функцию можно вызывать безопасно в любой момент.
     */
    fun endRoundIfActive() {
        val activeKey = getActiveRouteKey()

        if (activeKey != null) {
            activeRounds.remove(activeKey)?.let { completedRound ->
                println("Обход '${completedRound.routeName}' завершен.")
            }
        } else {
            println("Завершение обхода: обход не был активен.")
        }
    }

    /**
     * Возвращает ключ активного обхода, если он существует.
     */
    fun getActiveRouteKey(): String? {
        return if (activeRounds.containsKey(DEFAULT_ROUND_KEY)) {
            DEFAULT_ROUND_KEY
        } else {
            null
        }
    }

    fun generateFullReport(): String {
        if (shiftLogs.isEmpty()) {
            return "Журнал пуст. За смену не было зафиксировано ни одного обхода."
        }
        val builder = StringBuilder()
        builder.append("=== ЖУРНАЛ ОБХОДОВ ===\n\n")
        shiftLogs.forEach { entry ->
            // Упрощенный формат: время - название - результат
            // Извлекаем только время (без даты) для компактности
            val timeOnly = entry.timestamp.split(" ").getOrNull(1) ?: entry.timestamp
            builder.append("$timeOnly - ${entry.titleOrLocation} - ${entry.userResult}\n")
        }
        return builder.toString()
    }
    
    /**
     * Парсит NFC-данные (rawText - это NFC-ID тега)
     * Ищет чекпоинт по NFC-ID в базе данных
     */
    fun parseNfcData(nfcId: String, prefsManager: SharedPrefsManager): QrResult {
        val trimmed = nfcId.trim()
        
        if (trimmed.isEmpty()) {
            return QrResult.Error("Пустой NFC-ID")
        }
        
        // Ищем чекпоинт по NFC-ID
        val checkpoints = prefsManager.loadCheckpoints()
        val checkpointFromDatabase = checkpoints.find { it.nfcId == trimmed }
        
        if (checkpointFromDatabase == null) {
            return QrResult.SequenceError(
                null,
                "Чужеродная метка: NFC-тег не найден в базе данных чекпоинтов."
            )
        }
        
        val checkpointId = checkpointFromDatabase.id
        val name = checkpointFromDatabase.name
        val currentTime = dateFormat.format(Date())
        val action = checkpointFromDatabase.action
        
        // Сохраняем статус контроля последовательности при первом сканировании
        prefsManager.saveSequenceControlStatus(prefsManager.isStrictSequenceEnabled())
        
        // НЕСТРОГИЙ РЕЖИМ: просто сохраняем факт прохода, без проверки последовательности
        if (!prefsManager.isStrictSequenceEnabled()) {
            // Создаем CheckpointState вместо SCAN записи
            createCheckpointState(
                prefsManager = prefsManager,
                checkpointId = checkpointId,
                checkpointName = name,
                checkpointAction = action,
                roundId = prefsManager.getActiveRoundIndex(),
                sequenceIndex = prefsManager.getCurrentCheckpointIndex(),
                isSequenceCorrect = true,
                scanType = "NFC"
            )
            
            // НЕ обновляем индекс при сканировании - обновление будет при подтверждении прохождения
            // Индекс будет увеличен в OhrannikCabinetScreen при закрытии диалога
            // или в PhotoCaptureScreen при сохранении фото
            
            // Действия на основе типа чекпоинта
            return when (action) {
                CheckpointAction.QUESTION -> {
                    val questionText = checkpointFromDatabase.questionText ?: ""
                    val answersList = checkpointFromDatabase.answers.ifEmpty { emptyList() }
                    QrResult.QuestionFormat(checkpointId, name, questionText, answersList)
                }
                CheckpointAction.INPUT -> {
                    val title = checkpointFromDatabase.inputTitle ?: "Показания"
                    QrResult.InputFormat(checkpointId, name, title)
                }
                CheckpointAction.PHOTO -> {
                    val imageUri = checkpointFromDatabase.imageUri
                    QrResult.PhotoFormat(checkpointId, name, imageUri)
                }
                else -> {
                    QrResult.CheckpointPassed(checkpointId, name, currentTime)
                }
            }
        }
        
        // СТРОГИЙ РЕЖИМ: проверяем последовательность
        val activeRoute = activeRounds[DEFAULT_ROUND_KEY]
        
        // Загружаем ID чекпоинтов из настроек активного маршрута
        val routeId = prefsManager.getActiveRoundRouteId()
        val route = routeId?.let { prefsManager.getRouteById(it) }
        val routeCheckpoints = route?.checkpointIds ?: prefsManager.getAllCheckpointIds()
        
        // Проверяем, находится ли чекпоинт вообще в маршруте
        val isCheckpointInRoute = routeCheckpoints.contains(checkpointId)
        
        // Если маршрута нет - создаем и сразу проверяем первую точку
        val (isValid, expectedId) = if (activeRoute == null) {
            startNewRound(DEFAULT_ROUND_KEY, routeCheckpoints)
            activeRounds[DEFAULT_ROUND_KEY]!!.validateAndAdvance(checkpointId, prefsManager)
        } else {
            activeRoute.validateAndAdvance(checkpointId, prefsManager)
        }
        
        if (!isValid) {
            // Для всех нарушений используем одни и те же переменные
            val activeRoundIndex = prefsManager.getActiveRoundIndex()
            val activeShiftId = prefsManager.prefs.getString("active_shift_id", null)
            val activeEmployeeName = prefsManager.getActiveShiftEmployeeName()
            
            // Если чекпоинт не в маршруте - это "ВНЕ МАРШРУТА"
            if (!isCheckpointInRoute) {
                // Сохраняем факт сканирования (для аудита)
                activeShiftId?.let { shiftId ->
                    if (activeRoundIndex != -1) {
                        prefsManager.shiftDatabase.addLogEntry(
                            checkpointName = name,
                            checkpointId = checkpointId,
                            employeeName = activeEmployeeName,
                            roundId = activeRoundIndex,
                            routeName = "Маршрут обхода",
                            sequenceIndex = prefsManager.getCurrentCheckpointIndex(),
                            isSequenceCorrect = false,
                            scanType = "NFC",
                            actionType = "SCAN",
                            sequenceErrorType = SequenceErrorType.OUTSIDE_ROUTE
                        )
                        
                        // Записываем нарушение последовательности в базу данных
                        prefsManager.shiftDatabase.addSequenceViolation(
                            employeeName = activeEmployeeName,
                            roundId = activeRoundIndex,
                            shiftId = shiftId,
                            expectedCheckpointId = "",
                            expectedCheckpointName = "ВНЕ МАРШРУТА",
                            actualCheckpointId = checkpointId,
                            actualCheckpointName = name,
                            sequenceErrorType = SequenceErrorType.OUTSIDE_ROUTE,
                            isNfc = true
                        )
                    }
                }
                
                return QrResult.SequenceError(
                    null,
                    "Чекпоинт '${name}' не найден в текущем маршруте обхода."
                )
            }
            
            // Для нарушения последовательности используем уже объявленные переменные
            activeShiftId?.let { shiftId ->
                if (activeRoundIndex != -1) {
                    prefsManager.shiftDatabase.addLogEntry(
                        checkpointName = name,
                        checkpointId = checkpointId,
                        employeeName = activeEmployeeName,
                        roundId = activeRoundIndex,
                        routeName = "Маршрут обхода",
                        sequenceIndex = prefsManager.getCurrentCheckpointIndex(),
                        isSequenceCorrect = false,
                        scanType = "NFC",
                        actionType = "SCAN",
                        sequenceErrorType = SequenceErrorType.OUT_OF_SEQUENCE
                    )
                    
                    prefsManager.shiftDatabase.addSequenceViolation(
                        employeeName = activeEmployeeName,
                        roundId = activeRoundIndex,
                        shiftId = shiftId,
                        expectedCheckpointId = expectedId ?: "",
                        expectedCheckpointName = "Чекпоинт #${expectedId ?: ""}",
                        actualCheckpointId = checkpointId,
                        actualCheckpointName = name,
                        sequenceErrorType = SequenceErrorType.OUT_OF_SEQUENCE,
                        isNfc = true
                    )
                }
            }
            
            return QrResult.SequenceError(
                expectedId ?: "",
                "Нарушена последовательность обхода."
            )
        }

        // Если проверка пройдена, создаем CheckpointState
        createCheckpointState(
            prefsManager = prefsManager,
            checkpointId = checkpointId,
            checkpointName = name,
            checkpointAction = action,
            roundId = prefsManager.getActiveRoundIndex(),
            sequenceIndex = prefsManager.getCurrentCheckpointIndex(),
            isSequenceCorrect = true,
            scanType = "NFC"
        )
        
        // Сохраняем индекс следующего чекпоинта в SharedPreferences
        // activeRoute?.let { prefsManager.updateCurrentCheckpointIndex(it.currentIndex) }

        // Если маршрут завершен, сбрасываем его
        if (expectedId == null) {
            endRoundIfActive()
        }

        // Действия на основе типа чекпоинта
        when (action) {
            CheckpointAction.QUESTION -> {
                val questionText = checkpointFromDatabase.questionText ?: ""
                val answersList = checkpointFromDatabase.answers.ifEmpty { emptyList() }
                QrResult.QuestionFormat(checkpointId, name, questionText, answersList)
            }
            CheckpointAction.INPUT -> {
                val title = checkpointFromDatabase.inputTitle ?: "Показания"
                QrResult.InputFormat(checkpointId, name, title)
            }
            CheckpointAction.PHOTO -> {
                val imageUri = checkpointFromDatabase.imageUri
                QrResult.PhotoFormat(checkpointId, name, imageUri)
            }
            else -> {
                QrResult.CheckpointPassed(checkpointId, name, currentTime)
            }
        }
        
        return QrResult.Error("Неизвестная ошибка")
    }
}
