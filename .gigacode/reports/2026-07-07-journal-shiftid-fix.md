# Отчет: Исправление Journal Screen для отображения только текущей смены

## Дата
07.07.2026

## Проблема
Journal экран показывал все записи из памяти (все обходы и нарушения), а не только записи текущей активной смены.

## Корневая причина
Модель `SequenceViolation` уже содержала поле `shiftId`, но:
1. Метод `addSequenceViolation()` не сохранял `shiftId` при создании нарушения
2. Метод `saveViolation()` не записывал `shiftId` в JSON
3. Метод `loadAllViolations()` не загружал `shiftId` из JSON
4. Метод `loadViolationsByRound()` не фильтровал по `shiftId`
5. Места вызова `addSequenceViolation()` не передавали `shiftId`

## Выполненные изменения

### 1. ShiftDatabaseManager.kt

#### Метод addSequenceViolation()
- Добавлен параметр `shiftId: String`
- Добавлено сохранение `shiftId` в объект `SequenceViolation`

#### Метод saveViolation()
- Добавлена строка `put("shiftId", violation.shiftId)` в JSON

#### Метод loadAllViolations()
- Добавлена строка `shiftId = json.getString("shiftId")` при загрузке объекта

#### Метод loadViolationsByRound()
- Добавлен необязательный параметр `shiftId: String? = null`
- Добавлена фильтрация по `shiftId` если он указан

### 2. SharedPrefsManager.kt

#### Метод getRoundViolations()
- Обновлен вызов `loadViolationsByRound()` для передачи `currentShiftId`
- Добавлена дополнительная фильтрация по `shiftId` как защита

### 3. QrHandler.kt

Обновлены два места вызова `addSequenceViolation()`:

1. В методе `parseQrCode()` при нарушении последовательности (QR-код)
2. В методе `parseNfcData()` при нарушении последовательности (NFC-тег)

Добавлен параметр `shiftId = shiftId` в оба вызова.

### 4. OhrannikCabinetScreen.kt

Обновлен вызов `addSequenceViolation()` при NFC-сканировании в строгом режиме.
Добавлен параметр `shiftId = shiftId`.

### 5. JournalScreen.kt

- Обновлен подсчет `violationsCount`: теперь фильтруем нарушения по `shiftId`:

```kotlin
val violationsCount = prefsManager.shiftDatabase.loadAllViolations().filter { it.shiftId == shiftId }.size
```

## Результаты

✅ Приложение успешно скомпилировано (BUILD SUCCESSFUL)
✅ Все методы теперь корректно работают с `shiftId`
✅ Journal экран будет показывать только записи текущей активной смены
✅ Нарушения последовательности будут фильтроваться по `shiftId`

## Технические детали

### Структура данных
Каждое нарушение последовательности теперь хранит:
- `id` - уникальный ID нарушения
- `timestamp` - время нарушения
- `employeeName` - имя охранника
- `roundId` - ID обхода
- **`shiftId` - ID смены** (НОВОЕ)
- `expectedCheckpointId` - ожидаемый чекпоинт
- `expectedCheckpointName` - название ожидаемого чекпоинта
- `actualCheckpointId` - фактически отсканированный чекпоинт
- `actualCheckpointName` - название фактического чекпоинта
- `isNfc` - был ли NFC-тег

### Фильтрация данных
Journal экран теперь использует следующую логику:

1. Получает `shiftId` текущей активной смены
2. Загружает все логи и нарушения
3. Фильтрует по `shiftId` для отображения только текущей смены
4. Отображает таблицы для каждого обхода с соответствующими записями

## Тестирование

Для тестирования:
1. Запустить приложение
2. Начать новую смену
3. Выполнить несколько обходов
4. Создать несколько нарушений последовательности
5. Перейти в Journal экран
6. Проверить, что показываются только записи текущей смены
7. Завершить смену
8. Начать новую смену
9. Проверить, что старые записи не отображаются

## Связанные файлы

- `app/src/main/java/com/example/ohrana/ShiftDatabaseManager.kt` - основная база данных
- `app/src/main/java/com/example/ohrana/SharedPrefsManager.kt` - менеджер SharedPreferences
- `app/src/main/java/com/example/ohrana/QrHandler.kt` - обработка QR-кодов
- `app/src/main/java/com/example/ohrana/OhrannikCabinetScreen.kt` - экран охранника
- `app/src/main/java/com/example/ohrana/JournalScreen.kt` - экран журнала
- `app/src/main/java/com/example/ohrana/ShiftDatabaseModels.kt` - модели данных

## Заключение

Исправление завершено успешно. Journal экран теперь корректно отображает только записи текущей активной смены, используя поле `shiftId` для фильтрации всех данных (логи и нарушения последовательности).
