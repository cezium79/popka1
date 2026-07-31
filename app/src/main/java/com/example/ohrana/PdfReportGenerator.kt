package com.example.ohrana

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/**
 * Генератор PDF-отчётов напрямую из базы данных (без HTML).
 * Использует нативный Android PdfDocument API.
 */
object PdfReportGenerator {

    private const val TAG = "PdfReportGenerator"

    /**
     * Строка чекпоинта для таблицы в PDF.
     */
    private data class CheckpointRow(
        val number: Int,
        val checkpointName: String,
        val timestamp: String,
        val employeeName: String,
        val isAborted: Boolean,
        val actionText: String,
        val hasViolation: Boolean
    )

    // Основные цвета
    private val PRIMARY_BLUE = Color.rgb(30, 64, 175)
    private val LIGHT_BLUE_BG = Color.rgb(239, 246, 255)
    private val RED_VIOLATION = Color.rgb(185, 28, 28)
    private val ORANGE_INCIDENT = Color.rgb(194, 65, 12)
    private val GREEN_STATUS = Color.rgb(21, 128, 61)
    private val DARK_GRAY = Color.rgb(38, 38, 38)
    private val MID_GRAY = Color.rgb(117, 117, 117)

    // Размеры страницы A4 в точках (1 point = 1/72 inch)
    private const val PAGE_WIDTH = 595f
    private const val PAGE_HEIGHT = 842f
    private const val MARGIN = 40f
    private const val FONT_SIZE_TITLE = 18f
    private const val FONT_SIZE_HEADER = 14f
    private const val FONT_SIZE_NORMAL = 11f
    private const val FONT_SIZE_SMALL = 9f
    private const val DIVIDER_SPACING_BEFORE = 8f    // отступ перед линией
    private const val DIVIDER_SPACING_AFTER = 6f     // отступ после линии

    // Константы для таблицы чекпоинтов
    // Шрины столбцов таблицы чекпоинтов (сумма = PAGE_WIDTH - 2*MARGIN = 515)
    private const val COL_NUMBER = 25f
    private const val COL_CHECKPOINT_NAME = 105f
    private const val COL_TIME = 85f
    private const val COL_GUARD = 80f
    private const val COL_STATUS = 60f
    private const val COL_ACTION = 160f
    private const val CHECKPOINT_ROW_HEIGHT = 14f
    private const val CHECKPOINT_HEADER_HEIGHT = 16f
    private const val CHECKPOINT_FONT_SIZE = 8f
    private const val CHECKPOINT_TABLE_TOP_PADDING = 4f
    private const val CHECKPOINT_TABLE_LEFT = MARGIN

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale("ru", "RU"))

    /**
     * Генерирует PDF-отчёт по данным смены и сохраняет файл.
     * @return путь к созданному PDF-файлу или null при ошибке
     */
    fun generateShiftReportPdf(
        context: Context,
        shiftId: String,
        database: ShiftDatabaseManager
    ): String? {
        return try {
            // Загружаем все данные из базы
            val shift = database.loadAllShifts().find { it.id == shiftId }
                ?: throw IllegalArgumentException("Смена $shiftId не найдена")

            val rounds = database.loadAllRounds().filter { it.shiftId == shiftId }.sortedBy { it.startTime }
            val logs = database.loadLogsByShift(shiftId).sortedBy { it.timestamp }
            val violations = database.loadAllViolations().filter { it.shiftId == shiftId }
            val incidents = database.loadIncidentsByShift(shiftId)

            // Создаём PDF
            val pdfDocument = PdfDocument()

            // Заголовок отчёта
            val title = "ОТЧЁТ_ПО_СМЕНЕ_№${extractShiftSequenceNumber(shiftId)}"

            // Формируем содержимое отчёта как текст
            val reportContent = buildReportContent(shift, rounds, logs, violations, incidents)

            // Определяем количество страниц
            val pageCount = estimatePageCount(reportContent, logs, incidents)
            Log.d(TAG, "Estimated pages: $pageCount, content lines: ${reportContent.size}, logs: ${logs.size}, incidents: ${incidents.size}")
            Log.d(TAG, "Shift: $shiftId, Rounds: ${rounds.size}")
            logs.take(5).forEach { l ->
                Log.d(TAG, "  Log: ${l.checkpointName} roundId=${l.roundId} shiftId=${l.shiftId}")
            }
            rounds.forEach { r ->
                val matchingLogs = logs.filter { it.roundId == r.id }
                Log.d(TAG, "Round #${r.id} (${r.routeName}): matchingLogs=${matchingLogs.size}")
                matchingLogs.take(3).forEach { l ->
                    Log.d(TAG, "  - ${l.checkpointName} roundId=${l.roundId}")
                }
            }

            // Рисуем контент постранично
            var lineIndex = 0
            var pageIndex = 0
            while (lineIndex < reportContent.size) {
                val pageInfo = PdfDocument.PageInfo.Builder(
                    PAGE_WIDTH.toInt(),
                    PAGE_HEIGHT.toInt(),
                    pageIndex + 1
                ).create()

                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                lineIndex = drawReportPage(canvas, reportContent, logs, incidents, lineIndex, pageIndex)
                pageIndex++

                pdfDocument.finishPage(page)

                // Если дошли до конца контента, выходим
                if (lineIndex >= reportContent.size) break
            }

            // Сохраняем файл
            val pdfPath = savePdfDocument(pdfDocument, title)
            Log.d(TAG, "PDF saved to: $pdfPath")

            pdfDocument.close()
            pdfPath
        } catch (e: Exception) {
            Log.e(TAG, "Error generating PDF", e)
            null
        }
    }

    /**
     * Формирует текстовое содержимое отчёта из данных базы.
     */
    private fun buildReportContent(
        shift: ShiftRecord,
        rounds: List<RoundRecord>,
        logs: List<ShiftLogEntry>,
        violations: List<SequenceViolation>,
        incidents: List<IncidentRecord>
    ): List<String> {
        val lines = mutableListOf<String>()

        // Шапка смены
        lines.add("═══════════════════════════════════════")
        lines.add("СМЕНА №${extractShiftSequenceNumber(shift.id)}")
        lines.add("═══════════════════════════════════════")
        lines.add("Дата начала: ${shift.startTime}")
        lines.add("Дата окончания: ${shift.endTime ?: "Не завершена"}")

        // Охранники
        if (shift.guardList.isNotEmpty()) {
            lines.add("")
            lines.add("СОСТАВ СМЕНЫ:")
            for (guard in shift.guardList) {
                lines.add("  • ${guard.name} (${guard.role})")
            }
        }

        lines.add("")

        // Общая статистика
        val completedRounds = rounds.count { it.isCompleted }
        val totalCheckpoints = rounds.sumOf { it.checkpointsCount }
        val passedCheckpoints = rounds.sumOf { it.checkpointsPassed }

        lines.add("СТАТИСТИКА:")
        lines.add("  Обходов выполнено: ${completedRounds}/${rounds.size}")
        lines.add("  Чекпоинтов пройдено: $passedCheckpoints/$totalCheckpoints")
        lines.add("  Нарушений последовательности: ${violations.size}")
        lines.add("  Происшествий: ${incidents.size}")
        lines.add("")

        // Детали обходов
        if (rounds.isNotEmpty()) {
            lines.add("═══════════════════════════════════════")
            lines.add("ОБХОДЫ:")
            lines.add("═══════════════════════════════════════")

            for ((index, round) in rounds.withIndex()) {
                lines.add("")
                lines.add("|||ROUND_START|||${round.id}")
                lines.add("Обход #${index + 1}: ${round.routeName ?: "Без названия"}")
                lines.add("")

                // Формируем "Время обхода 30.07.2026 11:20 - 11:45"
                fun formatTimeRange(start: String?, end: String?): String {
                    if (start.isNullOrEmpty()) return "Время обхода: (неизвестно)"
                    val startParts = start.split(" ")
                    val date = startParts.getOrElse(0) { "" }
                    val startTime = startParts.getOrElse(1) { "" }
                    val (startH, startM) = if (startTime.contains(":")) {
                        val idx = startTime.indexOf(':')
                        val mEnd = startTime.lastIndexOf(':')
                        Pair(startTime.substring(0, idx), startTime.substring(idx + 1, mEnd).ifEmpty { startTime.substring(idx + 1) })
                    } else Pair(startTime, "")
                    return if (!end.isNullOrEmpty()) {
                        val endParts = end.split(" ")
                        val endTime = endParts.getOrElse(1) { "" }
                        val (endH, endM) = if (endTime.contains(":")) {
                            val idx = endTime.indexOf(':')
                            val mEnd = endTime.lastIndexOf(':')
                            Pair(endTime.substring(0, idx), endTime.substring(idx + 1, mEnd).ifEmpty { endTime.substring(idx + 1) })
                        } else Pair(endTime, "")
                        "Время обхода: $date $startH:$startM - $endH:$endM"
                    } else {
                        "Время обхода: $date $startH:$startM - (не завершён)"
                    }
                }
                lines.add("  ${formatTimeRange(round.startTime, round.endTime)}")
                lines.add("  Статус: ${if (round.isCompleted) "Завершён" else "Не завершён"}${"  ".repeat(4)}Чекпоинтов: ${round.checkpointsPassed}/${round.checkpointsCount}${"  ".repeat(4)}Нарушений: ${round.sequenceViolations}")

                val roundHasLogs = logs.any { it.roundId == round.id }
                if (roundHasLogs) {
                    lines.add("|||ROUND_LOGS|||${round.id}")
                }
            }
            lines.add("")
        }

        // Нарушения последовательности
        if (violations.isNotEmpty()) {
            lines.add("═══════════════════════════════════════")
            lines.add("НАРУШЕНИЯ ПОСЛЕДОВАТЕЛЬНОСТИ:")
            lines.add("═══════════════════════════════════════")

            for ((index, violation) in violations.withIndex()) {
                lines.add("")
                lines.add("Нарушение #${index + 1}:")
                lines.add("  Время: ${violation.timestamp}")
                lines.add("  Охранник: ${violation.employeeName}")
                lines.add("  Ожидается: ${violation.expectedCheckpointName}")
                lines.add("  Фактически: ${violation.actualCheckpointName}")
                lines.add("  Тип: ${violation.sequenceErrorType.name}")
            }
            lines.add("")
        }

        // Происшествия
        if (incidents.isNotEmpty()) {
            lines.add("═══════════════════════════════════════")
            lines.add("ПРОИСШЕСТВИЯ:")
            lines.add("═══════════════════════════════════════")

            for ((index, incident) in incidents.withIndex()) {
                lines.add("")
                lines.add("|||INCIDENT|||${index + 1}")
                lines.add("Происшествие #${index + 1}:")
                lines.add("  Время: ${incident.timestamp}")
                lines.add("  Охранник: ${incident.employeeName}")
                lines.add("  Тип: ${incident.incidentType.ruName}")
                lines.add("  Описание: ${incident.description}")
            }
            lines.add("")
        }

        // Фотографии за смену (чекпоинты + происшествия)
        val checkpointPhotos = logs.filter { it.actionType == "PHOTO" && it.photoPath != null && it.photoPath!!.isNotEmpty() }
        val incidentPhotos = incidents.filter { it.photoPath.isNotEmpty() }
        val totalPhotoCount = checkpointPhotos.size + incidentPhotos.size
        if (totalPhotoCount > 0) {
            lines.add("═══════════════════════════════════════")
            lines.add("ФОТОТЕКА:")
            lines.add("═══════════════════════════════════════")
            lines.add("|||PHOTO_TECA_BLOCK|||$totalPhotoCount")

            // Создаём мапу roundId -> порядковый номер обхода
            val roundOrdinalMap = rounds.mapIndexed { idx, r -> r.id to (idx + 1) }.toMap()

            // Для каждого обхода строим список уникальных чекпоинтов по порядку их посещения
            val checkpointOrdinalMap = rounds.associate { r ->
                val sortedLogs = logs.filter { it.roundId == r.id }.sortedBy { it.timestamp }
                val visitedCheckpoints = mutableListOf<String>()
                for (entry in sortedLogs) {
                    if (!visitedCheckpoints.contains(entry.checkpointName)) {
                        visitedCheckpoints.add(entry.checkpointName)
                    }
                }
                r.id to visitedCheckpoints.mapIndexed { idx, name -> name to (idx + 1) }.toMap()
            }

            // Сначала фото чекпоинтов
            for (log in checkpointPhotos) {
                val roundNum = roundOrdinalMap.getOrElse(log.roundId) { 0 }
                val cpOrdinal = checkpointOrdinalMap.getOrElse(log.roundId) { emptyMap() }.getOrElse(log.checkpointName) { 0 }
                lines.add("|||PHOTO_ITEM|||checkpoint")
                lines.add("  путь: ${log.photoPath!!}")
                lines.add("  Обход_номер: $roundNum")
                lines.add("  Чекпоинт_номер: $cpOrdinal")
                lines.add("  Чекпоинт_имя: ${log.checkpointName}")
            }

            // Потом фото происшествий
            for ((idx, incident) in incidentPhotos.withIndex()) {
                lines.add("|||PHOTO_ITEM|||incident")
                lines.add("  путь: ${incident.photoPath}")
                lines.add("  номер: ${idx + 1}")
            }
            lines.add("")
        }

        lines.add("═══════════════════════════════════════")
        lines.add("КОНЕЦ ОТЧЁТА")
        lines.add("═══════════════════════════════════════")

        return lines
    }

    /**
     * Рисует одну страницу PDF отчёта.
     * @param startLineIndex индекс строки, с которой начинать рисование
     * @return индекс следующей строки для рисования на следующей странице
     */
    private fun drawReportPage(
        canvas: Canvas,
        reportContent: List<String>,
        allLogs: List<ShiftLogEntry>,
        incidents: List<IncidentRecord>,
        startLineIndex: Int,
        pageIndex: Int
    ): Int {
        val linePaint = Paint().apply {
            color = DARK_GRAY
            textSize = FONT_SIZE_NORMAL
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val headerPaint = Paint().apply {
            color = PRIMARY_BLUE
            textSize = FONT_SIZE_HEADER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val titlePaint = Paint().apply {
            color = PRIMARY_BLUE
            textSize = FONT_SIZE_TITLE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val smallPaint = Paint().apply {
            color = MID_GRAY
            textSize = FONT_SIZE_SMALL
        }

        val dividerPaint = Paint().apply {
            color = Color.rgb(209, 213, 219)
            strokeWidth = 1f
        }

        val headerDividerPaint = Paint().apply {
            color = PRIMARY_BLUE
            strokeWidth = 2f
        }

        // Шрифты для таблицы чекпоинтов
        val tableHeaderPaint = Paint().apply {
            color = Color.WHITE
            textSize = CHECKPOINT_FONT_SIZE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val tableRowPaint = Paint().apply {
            color = DARK_GRAY
            textSize = CHECKPOINT_FONT_SIZE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val tableDividerPaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 0.5f
        }

        val bgGrayPaint = Paint().apply {
            color = LIGHT_BLUE_BG
        }

        var y = MARGIN

        // Если это первая страница — рисуем заголовок документа
        if (pageIndex == 0) {
            // Синяя плашка на фоне заголовка
            val titleText = "ОХРАНА — Отчёт по смене"
            val titleWidth = titlePaint.measureText(titleText)
            val bgPaintForTitle = Paint().apply { color = PRIMARY_BLUE }
            canvas.drawRect(MARGIN - 4f, y + 2f, MARGIN + titleWidth + 12f, y + 26f, bgPaintForTitle)
            canvas.drawText(titleText, MARGIN + 6f, y + 18f, Paint().apply {
                color = Color.WHITE
                textSize = FONT_SIZE_TITLE
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            })
            y += 32

            // Тонкая синяя линия
            dividerPaint.color = PRIMARY_BLUE
            dividerPaint.strokeWidth = 1.5f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, dividerPaint)
            dividerPaint.strokeWidth = 1f
            y += 15
        }

        // Рисуем контент начиная с указанной строки
        var i = startLineIndex
        while (i < reportContent.size) {
            val line = reportContent[i]

            // Пропускаем маркер ROUND_LOGS — может остаться при возврате после defer таблицы
            if (line.startsWith("|||ROUND_LOGS|||")) {
                // Пропускаем все строки до следующего ROUND_START или КОНЕЦ
                while (i < reportContent.size) {
                    val next = reportContent[i]
                    if (next.startsWith("|||ROUND_START|||") || next.startsWith("КОНЕЦ") || next == "═══════════════════════════════════════") {
                        break
                    }
                    i++
                }
                continue
            }

            // Проверяем, не начало ли это блока обхода
            if (line.startsWith("|||ROUND_START|||")) {
                val roundId = line.substring("|||ROUND_START|||".length).toIntOrNull() ?: 0
                val blockStart = i
                i++

                // Собираем все строки блока обхода до следующего ROUND_START или КОНЕЦ ОТЧЁТА
                val blockLines = mutableListOf<String>()
                var hasTable = false
                while (i < reportContent.size) {
                    val current = reportContent[i]
                    if (current.startsWith("|||ROUND_START|||") || current.startsWith("КОНЕЦ") || current == "═══════════════════════════════════════") {
                        break
                    }
                    if (current.startsWith("|||ROUND_LOGS|||")) {
                        hasTable = true
                        i++
                        continue
                    }
                    blockLines.add(current)
                    i++
                }

                // Собираем CheckpointRow если есть таблица
                val checkpointRows = if (hasTable) {
                    var checkpointCounter = 0
                    allLogs
                        .filter { it.roundId == roundId }
                        .sortedBy { it.timestamp }
                        .map { entry ->
                            val hasViolation = !entry.isSequenceCorrect
                            val timeOnly = try {
                                val inputFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale("ru", "RU"))
                                val outputFormat = SimpleDateFormat("HH:mm:ss", Locale("ru", "RU"))
                                val date = inputFormat.parse(entry.timestamp)
                                date?.let { outputFormat.format(it) } ?: entry.timestamp
                            } catch (_: Exception) {
                                entry.timestamp
                            }
                            if (!hasViolation) checkpointCounter++
                            CheckpointRow(
                                number = if (hasViolation) -1 else checkpointCounter,
                                checkpointName = entry.checkpointName,
                                timestamp = timeOnly,
                                employeeName = entry.employeeName,
                                isAborted = entry.hasAborted,
                                actionText = buildActionText(entry),
                                hasViolation = hasViolation
                            )
                        }
                } else null

                // Считаем высоту текстовой части блока
                var textHeight = 0f
                for (bl in blockLines) {
                    when {
                        bl.isEmpty() -> textHeight += 6f
                        bl.startsWith("  ") -> textHeight += 12f
                        else -> textHeight += FONT_SIZE_HEADER + DIVIDER_SPACING_AFTER + 4f
                    }
                }

                // Считаем высоту таблицы
                val tableHeight = checkpointRows?.let { rows ->
                    CHECKPOINT_HEADER_HEIGHT + (rows.size * CHECKPOINT_ROW_HEIGHT) + 6f + CHECKPOINT_TABLE_TOP_PADDING
                } ?: 0f

                val totalBlockHeight = textHeight + tableHeight
                val remainingSpace = PAGE_HEIGHT - MARGIN - 40 - y

                if (checkpointRows != null && tableHeight > remainingSpace) {
                    // Таблица больше доступного пространства — рисуем текст, таблицу переносим
                    // Рисуем текстовую часть блока
                    var blockI = 0
                    for (bl in blockLines) {
                        when {
                            bl.isEmpty() -> {
                                if (y + 6 > PAGE_HEIGHT - MARGIN - 40) break
                                y += 6
                            }
                            bl.startsWith("  ") -> {
                                if (y + 12 > PAGE_HEIGHT - MARGIN - 40) break
                                canvas.drawText(bl, MARGIN + 10f, y, linePaint)
                                y += 12
                            }
                            else -> {
                                val neededHeight = FONT_SIZE_HEADER + DIVIDER_SPACING_AFTER + 4f
                                if (y + neededHeight > PAGE_HEIGHT - MARGIN - 40) break
                                if (y > MARGIN + 20) {
                                    canvas.drawLine(MARGIN, y + DIVIDER_SPACING_BEFORE, PAGE_WIDTH - MARGIN, y + DIVIDER_SPACING_BEFORE, headerDividerPaint)
                                    y += DIVIDER_SPACING_BEFORE + 2f
                                }
                                canvas.drawText(bl, MARGIN, y + FONT_SIZE_HEADER, headerPaint)
                                y += neededHeight
                            }
                        }
                        blockI++
                    }
                    // Переносим таблицу на следующую страницу
                    return blockStart + blockLines.size + 1
                }

                // Проверяем, поместится ли весь блок целиком
                if (totalBlockHeight > remainingSpace) {
                    // Не помещается — переносим весь блок на следующую страницу
                    return blockStart
                }

                // Рисуем весь блок целиком
                var blockI = 0
                for (bl in blockLines) {
                    when {
                        bl.isEmpty() -> y += 6
                        bl.startsWith("  ") -> {
                            canvas.drawText(bl, MARGIN + 10f, y, linePaint)
                            y += 12
                        }
                        else -> {
                            val neededHeight = FONT_SIZE_HEADER + DIVIDER_SPACING_AFTER + 4f
                            if (y > MARGIN + 20) {
                                canvas.drawLine(MARGIN, y + DIVIDER_SPACING_BEFORE, PAGE_WIDTH - MARGIN, y + DIVIDER_SPACING_BEFORE, headerDividerPaint)
                                y += DIVIDER_SPACING_BEFORE + 2f
                            }
                            canvas.drawText(bl, MARGIN, y + FONT_SIZE_HEADER, headerPaint)
                            y += neededHeight
                        }
                    }
                    blockI++
                }

                // Рисуем таблицу если есть
                checkpointRows?.let { rows ->
                    val used = drawCheckpointTable(canvas, y, rows,
                        tableHeaderPaint, tableRowPaint, tableDividerPaint, bgGrayPaint)
                    y += used
                }
                continue
            }

            // Обработка блока фототеки
            if (line.startsWith("|||PHOTO_TECA_BLOCK|||")) {
                val tecaBlockStart = i
                i++

                data class PhotoEntry(val path: String, val photoType: String, val roundNumber: Int, val checkpointNumber: Int, val checkpointName: String, val incidentNumber: Int)
                val tecaPhotos = mutableListOf<PhotoEntry>()

                while (i < reportContent.size) {
                    val current = reportContent[i]
                    if (current.startsWith("|||PHOTO_TECA_BLOCK|||") || current.startsWith("КОНЕЦ") || current == "═══════════════════════════════════════") {
                        break
                    }
                    if (current.startsWith("|||PHOTO_ITEM|||")) {
                        val photoType = current.substring("|||PHOTO_ITEM|||".length).trim()
                        val path = StringBuilder()
                        val checkpointName = StringBuilder()
                        var roundNumber = 0
                        var checkpointNumber = 0
                        var incidentNumber = 0
                        val tempStart = i + 1
                        for (checkIdx in tempStart until minOf(tempStart + 3, reportContent.size)) {
                            val cl = reportContent[checkIdx]
                            if (cl.startsWith("  путь:")) path.append(cl.substring("  путь:".length).trim())
                            else if (cl.startsWith("  Обход_номер:")) roundNumber = cl.substring("  Обход_номер:".length).trim().toIntOrNull() ?: 0
                            else if (cl.startsWith("  Чекпоинт_номер:")) checkpointNumber = cl.substring("  Чекпоинт_номер:".length).trim().toIntOrNull() ?: 0
                            else if (cl.startsWith("  Чекпоинт_имя:")) checkpointName.append(cl.substring("  Чекпоинт_имя:".length).trim())
                            else if (cl.startsWith("  номер:")) incidentNumber = cl.substring("  номер:".length).trim().toIntOrNull() ?: 0
                            else break
                        }
                        tecaPhotos.add(PhotoEntry(path.toString(), photoType, roundNumber, checkpointNumber, checkpointName.toString(), incidentNumber))
                        i = tempStart + 3
                    }
                    i++
                }

                // Размеры для сетки 3 в строку
                val cellWidth = ((PAGE_WIDTH - 3 * MARGIN) / 3f).toInt()
                val captionHeight = 24f
                val cellSpacing = 4f

                // Рисуем фото по 3 в строку
                var photoIdx = 0
                while (photoIdx < tecaPhotos.size) {
                    val rowY = y
                    val photosInRow = minOf(3, tecaPhotos.size - photoIdx)

                    // Вычисляем высоту фото по реальным изображениям первой строки
                    var computedHeight = -1
                    if (rowY == y) {
                        var maxH = 0
                        for (ci in 0 until photosInRow) {
                            val pe = tecaPhotos[photoIdx + ci]
                            val opts = android.graphics.BitmapFactory.Options().apply {
                                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                                inJustDecodeBounds = true
                            }
                            android.graphics.BitmapFactory.decodeFile(pe.path, opts)
                            if (opts.outWidth > 0 && opts.outHeight > 0) {
                                val aspectRatio = (opts.outWidth.toFloat() / opts.outHeight.toFloat())
                                var h = (cellWidth / aspectRatio).toInt()
                                h = h.coerceAtMost(140)
                                maxH = maxOf(maxH, h)
                            }
                        }
                        computedHeight = maxH
                    }
                    val photoH = if (computedHeight > 0) computedHeight else 100

                    // Проверка что поместится строка
                    val neededRowHeight = photoH + captionHeight + cellSpacing
                    if (y + neededRowHeight > PAGE_HEIGHT - MARGIN - 40) {
                        return tecaBlockStart
                    }

                    // Рисуем фото в строке
                    for (ci in 0 until photosInRow) {
                        val pe = tecaPhotos[photoIdx + ci]
                        val photoX = MARGIN + (ci * (cellWidth + cellSpacing.toInt()))

                        val bitmap = android.graphics.BitmapFactory.decodeFile(pe.path, android.graphics.BitmapFactory.Options().apply {
                            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                        })

                        if (bitmap != null) {
                            val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                            var finalWidth = cellWidth
                            var finalHeight = (finalWidth / aspectRatio).toInt()
                            if (finalHeight > photoH) {
                                finalHeight = photoH
                                finalWidth = (finalHeight * aspectRatio).toInt()
                            }
                            val offsetX = ((cellWidth - finalWidth) / 2)

                            val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, finalWidth, finalHeight, true)
                            canvas.drawBitmap(scaledBitmap, (photoX + offsetX).toFloat(), rowY + 4f, null)
                            bitmap.recycle()
                            scaledBitmap.recycle()

                            // Подпись
                            val captionText = when (pe.photoType) {
                                "incident" -> "Происшествие #${pe.incidentNumber}"
                                else -> "Обход ${pe.roundNumber}, точка ${pe.checkpointNumber}"
                            }
                            val captionP = Paint().apply {
                                color = MID_GRAY
                                textSize = FONT_SIZE_SMALL
                                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                            }
                            val maxWidthPx = cellWidth.toFloat()
                            if (captionP.measureText(captionText) > maxWidthPx) {
                                val maxLen = captionText.length.coerceAtLeast(1)
                                var trimmed = captionText.substring(0, maxLen - 1)
                                while (captionP.measureText(trimmed) > maxWidthPx && trimmed.isNotEmpty()) {
                                    trimmed = trimmed.substring(0, trimmed.length - 1)
                                }
                                trimmed = trimmed + "…"
                                canvas.drawText(trimmed, photoX.toFloat(), rowY + photoH + 16f, captionP)
                            } else {
                                canvas.drawText(captionText, photoX.toFloat(), rowY + photoH + 16f, captionP)
                            }
                        }
                    }

                    y = rowY + photoH + captionHeight + cellSpacing
                    photoIdx += photosInRow
                }
                continue
            }

            // Обработка блока происшествия
            if (line.startsWith("|||INCIDENT|||")) {
                val incidentBlockStart = i
                i++

                // Собираем все строки инцидента
                val incidentLines = mutableListOf<String>()
                while (i < reportContent.size) {
                    val current = reportContent[i]
                    if (current.startsWith("|||INCIDENT|||") || current.startsWith("КОНЕЦ") || current == "═══════════════════════════════════════") {
                        break
                    }
                    incidentLines.add(current)
                    i++
                }

                // Считаем высоту текстовой части
                var textHeight = 0f
                for (il in incidentLines) {
                    if (il.startsWith("  ")) {
                        textHeight += 12f
                    } else {
                        textHeight += FONT_SIZE_HEADER + DIVIDER_SPACING_AFTER + 4f
                    }
                }

                // Проверяем, поместится ли инцидент
                val remainingSpace = PAGE_HEIGHT - MARGIN - 40 - y
                if (textHeight > remainingSpace) {
                    // Не помещается — переносим на следующую страницу
                    return incidentBlockStart
                }

                // Рисуем текст инцидента
                var incidentY = y
                for (il in incidentLines) {
                    if (il.startsWith("  ")) {
                        val indentedPaint = Paint().apply {
                            color = DARK_GRAY
                            textSize = FONT_SIZE_NORMAL
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                        }
                        canvas.drawText(il, MARGIN + 10f, incidentY, indentedPaint)
                        incidentY += 12f
                    } else {
                        // Заголовок инцидента — цветной
                        headerPaint.color = ORANGE_INCIDENT
                        canvas.drawText(il, MARGIN, incidentY + FONT_SIZE_HEADER, headerPaint)
                        incidentY += FONT_SIZE_HEADER + DIVIDER_SPACING_AFTER + 4f
                    }
                }

                y = y + textHeight
                continue
            }

            when {
                line.startsWith("═") -> {
                    // Пропускаем строки-разделители (линии рисуются перед заголовками)
                    i++
                    continue
                }
                line.startsWith("СМЕНА") || line.startsWith("ОБХОД") ||
                        line.startsWith("НАРУШЕНИЯ") || line.startsWith("ПРОИСШЕСТВИЯ") ||
                        line.startsWith("ДЕТАЛЬНЫЙ") || line.startsWith("СТАТИСТИКА") ||
                        line.startsWith("СОСТАВ") || line.startsWith("КОНЕЦ ОТЧ") -> {
                    // Определяем цвет заголовка по типу секции
                    val headerColor = when {
                        line.startsWith("НАРУШЕНИЯ") -> RED_VIOLATION
                        line.startsWith("ПРОИСШЕСТВИЯ") -> ORANGE_INCIDENT
                        line.startsWith("КОНЕЦ") -> MID_GRAY
                        else -> PRIMARY_BLUE
                    }
                    val dividerColor = when {
                        line.startsWith("НАРУШЕНИЯ") -> RED_VIOLATION
                        line.startsWith("ПРОИСШЕСТВИЯ") -> ORANGE_INCIDENT
                        else -> PRIMARY_BLUE
                    }

                    // Проверяем, поместится ли заголовок
                    val neededHeight = FONT_SIZE_HEADER + DIVIDER_SPACING_AFTER + 4f
                    if (y + neededHeight > PAGE_HEIGHT - MARGIN - 40) {
                        break
                    }
                    // Рисуем цветную линию над заголовком
                    if (y > MARGIN + 20) {
                        dividerPaint.color = dividerColor
                        dividerPaint.strokeWidth = 2f
                        canvas.drawLine(
                            MARGIN,
                            y + DIVIDER_SPACING_BEFORE,
                            PAGE_WIDTH - MARGIN,
                            y + DIVIDER_SPACING_BEFORE,
                            dividerPaint
                        )
                        dividerPaint.strokeWidth = 1f
                        y += DIVIDER_SPACING_BEFORE + 2f
                    }
                    // Рисуем текст заголовка цветным
                    headerPaint.color = headerColor
                    canvas.drawText(line, MARGIN, y + FONT_SIZE_HEADER, headerPaint)
                    y += FONT_SIZE_HEADER + DIVIDER_SPACING_AFTER + 4f
                }
                line.startsWith("  ") -> {
                    // Проверяем, поместится ли строка
                    if (y + 12 > PAGE_HEIGHT - MARGIN - 40) {
                        break
                    }
                    // Подстрока с отступом — подсвечиваем статус и нарушения
                    val indentedPaint = Paint().apply {
                        color = when {
                            line.contains("Завершён") || line.contains("Пройден") -> GREEN_STATUS
                            line.contains("Не завершён") -> Color.rgb(234, 179, 8)
                            line.contains("Нарушений") && !line.contains("0") -> RED_VIOLATION
                            line.contains("Прерван") -> RED_VIOLATION
                            else -> DARK_GRAY
                        }
                        textSize = FONT_SIZE_NORMAL
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    }
                    canvas.drawText(line, MARGIN + 10f, y, indentedPaint)
                    y += 12
                }
                line.isEmpty() -> {
                    // Проверяем, не заполнена ли страница
                    if (y + 6 > PAGE_HEIGHT - MARGIN - 40) {
                        break
                    }
                    y += 6
                }
                else -> {
                    // Проверяем, поместится ли строка
                    if (y + 12 > PAGE_HEIGHT - MARGIN - 40) {
                        break
                    }
                    canvas.drawText(line, MARGIN, y, linePaint)
                    y += 12
                }
            }

            i++
        }

        // Номер страницы внизу
        val pageNumText = "Страница ${pageIndex + 1}"
        val pageNumWidth = smallPaint.measureText(pageNumText)
        canvas.drawText(
            pageNumText,
            PAGE_WIDTH - MARGIN - pageNumWidth,
            PAGE_HEIGHT - 20,
            smallPaint
        )

        return i
    }

    /**
     * Формирует текст действия из ShiftLogEntry.
     */
    private fun buildActionText(entry: ShiftLogEntry): String {
        val violationSuffix = if (!entry.isSequenceCorrect) " ⚠" else ""
        return when (entry.actionType) {
            "QUESTION" -> "${entry.questionText ?: "Вопрос"}: ${entry.answer ?: "-"}$violationSuffix"
            "INPUT" -> "${entry.inputTitle ?: "Ввод"}: ${entry.inputValue ?: "-"}$violationSuffix"
            "PHOTO" -> "Фото: снято$violationSuffix"
            else -> "Проход$violationSuffix"
        }
    }

    /**
     * Рисует таблицу чекпоинтов.
     * @return высота занятого пространства
     */
    private fun drawCheckpointTable(
        canvas: Canvas,
        startY: Float,
        rows: List<CheckpointRow>,
        headerPaint: Paint,
        rowPaint: Paint,
        dividerPaint: Paint,
        bgPaint: Paint
    ): Float {
        if (rows.isEmpty()) return 0f

        val totalRows = rows.size + 1 // +1 для заголовка
        val tableHeight = CHECKPOINT_HEADER_HEIGHT + (totalRows * CHECKPOINT_ROW_HEIGHT) + 6f

        // Позиции столбцов
        val colX = FloatArray(7)
        colX[0] = CHECKPOINT_TABLE_LEFT
        colX[1] = CHECKPOINT_TABLE_LEFT + COL_NUMBER
        colX[2] = colX[1] + COL_CHECKPOINT_NAME
        colX[3] = colX[2] + COL_TIME
        colX[4] = colX[3] + COL_GUARD
        colX[5] = colX[4] + COL_STATUS
        colX[6] = colX[5] + COL_ACTION

        // Определяем текст столбцов по ширине
        val colHeaders = arrayOf("№", "Чекпоинт", "Время", "Охранник", "Статус", "Действие")

        // --- Заголовок таблицы ---
        val headerTopY = startY + CHECKPOINT_TABLE_TOP_PADDING
        val headerBotY = headerTopY + CHECKPOINT_HEADER_HEIGHT

        // Фон заголовка — синий
        bgPaint.color = Color.rgb(30, 64, 175)
        canvas.drawRect(colX[0], headerTopY, colX[6], headerBotY, bgPaint)

        // Текст заголовков столбцов
        headerPaint.color = Color.BLACK
        val headerTextY = headerTopY + 11f
        canvas.drawText(colHeaders[0], colX[0] + 2f, headerTextY, headerPaint)
        canvas.drawText(colHeaders[1], colX[1] + 2f, headerTextY, headerPaint)
        canvas.drawText(colHeaders[2], colX[2] + 2f, headerTextY, headerPaint)
        canvas.drawText(colHeaders[3], colX[3] + 2f, headerTextY, headerPaint)
        canvas.drawText(colHeaders[4], colX[4] + 2f, headerTextY, headerPaint)
        canvas.drawText(colHeaders[5], colX[5] + 2f, headerTextY, headerPaint)

        // Горизонтальная линия под заголовком
        canvas.drawLine(colX[0], headerBotY, colX[6], headerBotY, dividerPaint)

        // --- Данные строк ---
        var currentY = headerBotY + 2f

        for ((idx, row) in rows.withIndex()) {
            // Чередующийся фон
            if (idx % 2 == 1) {
                bgPaint.color = Color.rgb(245, 245, 245)
                canvas.drawRect(colX[0], currentY - 1f, colX[6], currentY - 1f + CHECKPOINT_ROW_HEIGHT, bgPaint)
            }

            // Вертикальные разделители
            dividerPaint.color = Color.rgb(200, 200, 200)
            for (cx in colX) {
                canvas.drawLine(cx, currentY - 1f, cx, currentY - 1f + CHECKPOINT_ROW_HEIGHT, dividerPaint)
            }

            // Текст ячеек
            rowPaint.color = DARK_GRAY
            val numText = if (row.number == -1) "!" else row.number.toString()
            canvas.drawText(numText, colX[0] + 2f, currentY + 10f, rowPaint)
            canvas.drawText(row.checkpointName, colX[1] + 2f, currentY + 10f, rowPaint)
            canvas.drawText(row.timestamp, colX[2] + 2f, currentY + 10f, rowPaint)
            canvas.drawText(row.employeeName, colX[3] + 2f, currentY + 10f, rowPaint)
            // Статус — зелёный если пройден, красный если прерван
            val statusPaint = Paint().apply {
                color = if (row.isAborted) RED_VIOLATION else GREEN_STATUS
                textSize = CHECKPOINT_FONT_SIZE
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(if (row.isAborted) "Прерван" else "Пройден", colX[4] + 2f, currentY + 10f, statusPaint)
            // Нарушение — красный
            val actionPaint = if (row.hasViolation) {
                Paint().apply {
                    color = RED_VIOLATION
                    textSize = CHECKPOINT_FONT_SIZE
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                }
            } else rowPaint
            canvas.drawText(row.actionText, colX[5] + 2f, currentY + 10f, actionPaint)

            currentY += CHECKPOINT_ROW_HEIGHT
        }

        // Нижняя граница таблицы
        dividerPaint.color = Color.GRAY
        dividerPaint.strokeWidth = 1f
        canvas.drawLine(colX[0], currentY, colX[6], currentY, dividerPaint)

        return tableHeight
    }

    /**
     * Приблизительно оценивает количество страниц.
     */
    private fun estimatePageCount(
        reportContent: List<String>,
        logs: List<ShiftLogEntry>,
        incidents: List<IncidentRecord>
    ): Int {
        // Строки контента (~36 строк на страницу)
        val contentLines = reportContent.size
        // Таблица чекпоинтов: каждая запись обхода ~1 строка контента
        val checkpointTableLines = logs.size
        // Фото в фототеке (чекпоинты + происшествия) — 3 фото в строку, каждая строка ~6 строк контента
        val checkpointPhotoCount = logs.count { it.actionType == "PHOTO" && it.photoPath != null && it.photoPath!!.isNotEmpty() }
        val incidentPhotoCount = incidents.count { it.photoPath.isNotEmpty() }
        val totalPhotoCount = checkpointPhotoCount + incidentPhotoCount
        val photoLines = if (totalPhotoCount > 0) ceil(totalPhotoCount / 3.0) * 6.0 else 0.0

        val totalLines = contentLines + checkpointTableLines + photoLines + 10
        return maxOf(1, ceil(totalLines / 36.0).toInt())
    }

    /**
     * Сохраняет PdfDocument в файл.
     */
    private fun savePdfDocument(pdfDocument: PdfDocument, title: String): String {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val appDir = File(downloadsDir, "Ohrana/PDF")
        if (!appDir.exists()) {
            appDir.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale("ru", "RU")).format(Date())
        val fileName = "${title}_${timestamp}.pdf"
        val file = File(appDir, fileName)

        pdfDocument.writeTo(FileOutputStream(file))
        return file.absolutePath
    }

    /**
     * Извлекает порядковый номер смены из ID.
     */
    private fun extractShiftSequenceNumber(shiftId: String): Int {
        val regex = Regex("NS\\d{6}_(\\d{3})")
        val match = regex.find(shiftId)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }
}
