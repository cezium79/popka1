# Plan: Четкое разделение нарушений обхода

## Задача

Разделить нарушения обхода на две группы:
1. **Нарушения по времени** - пока не реализованы (отложены)
2. **Нарушения по идентификации** - подлежат улучшению

Вторая группа должна включать три типа нарушений:
1. **Чекпоинт вне очереди** - чекпоинт в маршруте, но не следующий ожидаемый
2. **Чекпоинт вне маршрута** - чекпоинт существует в базе, но не в текущем маршруте
3. **Чекпоинт вне базы** - чекпоинт не найден в базе данных вообще

## Текущее состояние

### Уже реализовано:
- ✅ Три уровня нарушений в QrHandler.kt
- ✅ Отображение ошибок в OhrannikCabinetScreen
- ✅ Сохранение `sequenceErrorType` в логах

### Проблемы:
1. `sequenceErrorExpected` не сохраняется в JSON базы данных
2. Нет отдельного поля для типа нарушения (FOREIGN/OUTSIDE_ROUTE/OUT_OF_SEQUENCE)
3. Журнал показывает "Вне очереди" для всех нарушений
4. Нарушения не классифицируются в базе данных

## Implementation Plan

### 1. Обновить модель ShiftLogEntry

**File:** `app/src/main/java/com/example/ohrana/ShiftDatabaseModels.kt`

Добавить поле `sequenceErrorType`:

```kotlin
enum class SequenceErrorType {
    NONE,
    FOREIGN_CHECKPOINT,    // Чужеродная метка
    OUTSIDE_ROUTE,         // Вне маршрута
    OUT_OF_SEQUENCE        // Вне очереди
}

data class ShiftLogEntry(
    // ... существующие поля ...
    val sequenceErrorType: SequenceErrorType = SequenceErrorType.NONE  // Новое поле
)
```

### 2. Обновить ShiftDatabaseManager.saveLogEntry()

**File:** `app/src/main/java/com/example/ohrana/ShiftDatabaseManager.kt`

Сохранять новое поле в JSON:

```kotlin
private fun saveLogEntry(entry: ShiftLogEntry) {
    val json = JSONObject().apply {
        // ... существующие поля ...
        put("sequenceErrorType", entry.sequenceErrorType.name)
    }
    // ...
}
```

### 3. Обновить ShiftDatabaseManager.loadAllLogs()

**File:** `app/src/main/java/com/example/ohrana/ShiftDatabaseManager.kt`

Загружать новое поле из JSON:

```kotlin
val entry = ShiftLogEntry(
    // ... существующие поля ...
    sequenceErrorType = try {
        SequenceErrorType.valueOf(json.getString("sequenceErrorType"))
    } catch (e: Exception) {
        SequenceErrorType.NONE
    }
)
```

### 4. Обновить QrHandler.parseQrCode()

**File:** `app/src/main/java/com/example/ohrana/QrHandler.kt`

Уточнить типы нарушений при добавлении логов:

```kotlin
// Внешняя ошибка - чужеродная метка
prefsManager.shiftDatabase.addLogEntry(
    // ...
    sequenceErrorType = SequenceErrorType.FOREIGN_CHECKPOINT,
    // ...
)

// Вне маршрута
prefsManager.shiftDatabase.addLogEntry(
    // ...
    sequenceErrorType = SequenceErrorType.OUTSIDE_ROUTE,
    // ...
)

// Вне очереди
prefsManager.shiftDatabase.addLogEntry(
    // ...
    sequenceErrorType = SequenceErrorType.OUT_OF_SEQUENCE,
    // ...
)
```

### 5. Обновить JournalScreen

**File:** `app/src/main/java/com/example/ohrana/JournalScreen.kt`

Изменить логику отображения примечаний:

```kotlin
val violationNote = when (log.sequenceErrorType) {
    SequenceErrorType.FOREIGN_CHECKPOINT -> "Чужеродная метка"
    SequenceErrorType.OUTSIDE_ROUTE -> "Вне маршрута"
    SequenceErrorType.OUT_OF_SEQUENCE -> "Вне очереди"
    else -> "-"
}
```

### 6. Обновить OhrannikCabinetScreen

**File:** `app/src/main/java/com/example/ohrana/OhrannikCabinetScreen.kt`

Уточнить сообщения об ошибках:

```kotlin
// В QrHandler уже есть правильные сообщения
// Проверить, что они правильно передаются

// При показе диалога ошибки
when (result.sequenceErrorType) {
    SequenceErrorType.FOREIGN_CHECKPOINT -> {
        title = "Чужеродная метка"
        message = "QR-код не найден в базе данных чекпоинтов."
    }
    SequenceErrorType.OUTSIDE_ROUTE -> {
        title = "Чекпоинт вне маршрута"
        message = "Чекпоинт существует, но не в текущем маршруте обхода."
    }
    SequenceErrorType.OUT_OF_SEQUENCE -> {
        title = "Точка вне очереди"
        message = "Нарушена последовательность обхода."
    }
    else -> { /*正常ное прохождение*/ }
}
```

### 7. Обновить addSequenceViolation()

**File:** `app/src/main/java/com/example/ohrana/ShiftDatabaseManager.kt`

Добавить параметр `sequenceErrorType`:

```kotlin
fun addSequenceViolation(
    employeeName: String,
    roundId: Int,
    shiftId: String,
    expectedCheckpointId: String,
    expectedCheckpointName: String,
    actualCheckpointId: String,
    actualCheckpointName: String,
    sequenceErrorType: SequenceErrorType,  // Новый параметр
    isNfc: Boolean = false
): String {
    // ...
}
```

## Файлы для модификации

1. `app/src/main/java/com/example/ohrana/ShiftDatabaseModels.kt`
2. `app/src/main/java/com/example/ohrana/ShiftDatabaseManager.kt`
3. `app/src/main/java/com/example/ohrana/QrHandler.kt`
4. `app/src/main/java/com/example/ohrana/OhrannikCabinetScreen.kt`
5. `app/src/main/java/com/example/ohrana/JournalScreen.kt`

## Ожидаемые результаты

✅ В базе данных каждое нарушение имеет тип (FOREIGN/OUTSIDE_ROUTE/OUT_OF_SEQUENCE)
✅ В JournalScreen каждое нарушение отображается с правильным сообщением
✅ В OhrannikCabinetScreen ошибки показываются с правильными заголовками
✅ Нарушения четко классифицируются и сохраняются

## Тестирование

1. Отсканировать несуществующий QR (чужеродная метка)
2. Отсканировать чекпоинт из другого маршрута (вне маршрута)
3. Отсканировать чекпоинт не по порядку (вне очереди)
4. Проверить Journal - каждый тип должен иметь свое сообщение
5. Проверить диалог ошибки - каждый тип должен показывать правильный заголовок

## ✅ ЗАВЕРШЕНО (2026-07-08)

### Реализованные изменения:

#### 1. Обновлена модель ShiftLogEntry
- **File:** `app/src/main/java/com/example/ohrana/ShiftDatabaseModels.kt`
- Добавлен enum `SequenceErrorType` с тремя типами нарушений
- Обновлен `ShiftLogEntry` с полем `sequenceErrorType`

#### 2. Обновлен ShiftDatabaseManager
- **File:** `app/src/main/java/com/example/ohrana/ShiftDatabaseManager.kt`
- `saveLogEntry()` сохраняет `sequenceErrorType`
- `loadAllLogs()` загружает `sequenceErrorType`
- `addLogEntry()` принимает `sequenceErrorType`
- `saveViolation()` сохраняет `sequenceErrorType`
- `loadAllViolations()` загружает `sequenceErrorType`

#### 3. Обновлен QrHandler
- **File:** `app/src/main/java/com/example/ohrana/QrHandler.kt`
- Импортирован `SequenceErrorType`
- Добавлено сохранение логов для FOREIGN_CHECKPOINT при неизвестных QR кодах
- Обновлены все вызовы `addLogEntry()` и `addSequenceViolation()` с правильными типами
- NFC-обработка теперь также передает правильный `sequenceErrorType`

#### 4. Обновлен JournalScreen
- **File:** `app/src/main/java/com/example/ohrana/JournalScreen.kt`
- Столбец "Примечание" теперь показывает тип нарушения:
  - `FOREIGN_CHECKPOINT` → "Чужая метка"
  - `OUTSIDE_ROUTE` → "Вне маршрута"
  - `OUT_OF_SEQUENCE` → "Вне очереди"
- Для CHECKPOINT нарушений показывается "-"
- PHOTO нарушения показывают "Фото снято"
- Удалён столбец "Дата", добавлен столбец "Сотрудник"

#### 5. Обновлены другие файлы
- **CloudStorageManager.kt** - обновлен экспорт в JSON
- **OhrannikCabinetScreen.kt** - обновлены вызовы базы данных
- **ShiftLogDetailScreen.kt** - обновлены методы отображения нарушений

#### 6. Сборка и установка
- Приложение успешно собралось: `BUILD SUCCESSFUL`
- Приложение установлено на устройство: `Success`

## Актуальное состояние

### Реализованные требования:

1. ✅ **Три уровня нарушений** - FOREIGN_CHECKPOINT, OUTSIDE_ROUTE, OUT_OF_SEQUENCE
2. ✅ **Правильное сохранение в базе** - `sequenceErrorType` хранится в логах и нарушениях
3. ✅ **Журнал показывает типы нарушений** - разные тексты для разных типов
4. ✅ **CHECKPOINT нарушения показывают "-"** - не "Точка пройдена"
5. ✅ **PHOTO нарушения показывают фото** - "Фото снято"
6. ✅ **Удалён столбец "Дата"** - таблица теперь компактнее
7. ✅ **Добавлен столбец "Сотрудник"** - после "Время"
8. ✅ **Нарушения разделены на две группы** - по времени (отложено) и по идентификации (реализовано)

### Текущие ограничения:

1. ⏭️ **Нарушения по времени** - отложены (требуется дополнительная логика)
2. ⏭️ **Документация** - не обновлена (README.md, CHANGES.md)

### Следующие шаги:

1. Обновить документацию (README.md, CHANGES.md) с описанием новых типов нарушений
2. Протестировать три типа нарушений на реальном устройстве:
   - Чужеродная метка (неизвестный QR/NFC)
   - Вне маршрута (чекпоинт из другой смены)
   - Вне очереди (неправильный порядок сканирования)
3. Проверить, что фото отображаются корректно для нарушений типа PHOTO
4. Протестировать экспорт в JSON с новым полем `sequenceErrorType`

## Тестовые сценарии

### Сценарий 1: Чужеродная метка (FOREIGN_CHECKPOINT)
1. Отсканировать QR-код, не существующий в базе
2. Проверить Journal - должно отобразиться "Чужая метка"
3. Проверить диалог ошибки - правильный заголовок и сообщение

### Сценарий 2: Вне маршрута (OUTSIDE_ROUTE)
1. Выбрать чекпоинт из другого маршрута
2. Отсканировать его в текущем обходе
3. Проверить Journal - должно отобразиться "Вне маршрута"
4. Проверить диалог ошибки

### Сценарий 3: Вне очереди (OUT_OF_SEQUENCE)
1. Пропустить следующий чекпоинт
2. Отсканировать последующий чекпоинт
3. Проверить Journal - должно отобразиться "Вне очереди"
4. Проверить диалог ошибки

### Сценарий 4: PHOTO нарушение
1. Отсканировать PHOTO чекпоинт не по порядку
2. Снять фото
3. Проверить Journal - должно отобразиться "Фото снято" (не "-")
4. Проверить фото - должно быть видно в диалоге
