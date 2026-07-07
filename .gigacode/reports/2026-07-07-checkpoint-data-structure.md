# Документация: Структура данных для разных типов чекпоинтов

## Обзор

В системе Ohrana существуют разные типы чекпоинтов, каждый из которых сохраняет свои данные при сканировании.

## Типы чекпоинтов

### 1. CHECKPOINT (Обычный чекпоинт)

**Действие:** Простое сканирование

**Что сохраняется в логе (ShiftLogEntry):**
```kotlin
actionType = "SCAN"  // или "CHECKPOINT" после подтверждения
checkpointName = "Название чекпоинта"
checkpointId = "ID_чекпоинта"
employeeName = "Имя охранника"
roundId = 1
shiftId = "ID_смены"
scanType = "QR" или "NFC"
isSequenceCorrect = true
// остальные поля заполняются стандартными значениями
```

**Поля questionText, inputTitle, answer, inputValue = null**

---

### 2. QUESTION (Чекпоинт с вопросом)

**Действие:** Сканирование → выбор ответа из предложенных

**Что сохраняется в логе (ShiftLogEntry):**
```kotlin
actionType = "SCAN" → обновляется на "QUESTION" после выбора ответа
checkpointName = "Название чекпоинта"
checkpointId = "ID_чекпоинта"
employeeName = "Имя охранника"
roundId = 1
shiftId = "ID_смены"
scanType = "QR" или "NFC"
isSequenceCorrect = true

// Поля для вопроса (берутся из базы данных):
questionText = null  // сохраняется null, берется из базы при отображении
inputTitle = null    // не используется

// Поля для ответа:
answer = "Выбранный ответ"  // сохраняется только при выборе ответа
inputValue = null  // не используется
```

**Структура диалога:**
```
[Диалог с вопросом]
- Отображается: текст вопроса из checkpoint.questionText
- Пользователь выбирает один из ответов из checkpoint.answers
- Сохраняется: checkpoint.questionText (в базе) + выбранный ответ (в логе)
```

---

### 3. INPUT (Чекпоинт с вводом данных)

**Действие:** Сканирование → ввод данных в поле

**Что сохраняется в логе (ShiftLogEntry):**
```kotlin
actionType = "SCAN" → обновляется на "INPUT" после сохранения
checkpointName = "Название чекпоинта"
checkpointId = "ID_чекпоинта"
employeeName = "Имя охранника"
roundId = 1
shiftId = "ID_смены"
scanType = "QR" или "NFC"
isSequenceCorrect = true

// Поля для заголовка запроса:
questionText = null  // не используется
inputTitle = null    // сохраняется null, берется из базы при отображении

// Поля для данных:
answer = null  // не используется
inputValue = "Введенные данные"  // сохраняется только после ввода
```

**Структура диалога:**
```
[Диалог с вводом данных]
- Отображается: заголовок из checkpoint.inputTitle
- Пользователь вводит данные в поле OutlinedTextField
- Сохраняется: checkpoint.inputTitle (в базе) + введенные данные (в логе)
```

---

### 4. PHOTO (Чекпоинт для фото)

**Действие:** Сканирование → съемка фото

**Что сохраняется в логе (ShiftLogEntry):**
```kotlin
actionType = "SCAN" → обновляется на "PHOTO" после съемки
checkpointName = "Название чекпоинта"
checkpointId = "ID_чекпоинта"
employeeName = "Имя охранника"
roundId = 1
shiftId = "ID_смены"
scanType = "QR" или "NFC"
isSequenceCorrect = true

// Поля для фото:
questionText = null  // не используется
inputTitle = null    // не используется
answer = null  // не используется
inputValue = null  // не используется
photoPath = "path/to/photo.jpg"  // путь к сохраненному фото
```

**Структура экрана:**
```
[Экран фото]
- Переход на PhotoCaptureScreen
- Пользователь снимает фото
- Сохраняется: путь к фото (photoPath)
```

---

### 5. НАРУШЕНИЕ ПОСЛЕДОВАТЕЛЬНОСТИ

**Действие:** Сканирование вне очереди в строгом режиме

**Что сохраняется в логе (ShiftLogEntry):**
```kotlin
actionType = "SCAN"
checkpointName = "Название чекпоинта"
checkpointId = "ID_чекпоинта"
employeeName = "Имя охранника"
roundId = 1
shiftId = "ID_смены"
scanType = "QR" или "NFC"
isSequenceCorrect = false  // ВНИМАНИЕ: false для нарушений
sequenceErrorExpected = "ID_ожидаемого_чекпоинта"
```

**Что сохраняется в нарушениях (SequenceViolation):**
```kotlin
id = "violation_1234567890"
timestamp = "07.07.2026 15:30:45"
employeeName = "Имя охранника"
roundId = 1
shiftId = "ID_смены"
expectedCheckpointId = "ID_ожидаемого_чекпоинта"
expectedCheckpointName = "Ожидаемый чекпоинт"
actualCheckpointId = "ID_фактического_чекпоинта"
actualCheckpointName = "Фактический чекпоинт"
isNfc = true или false
```

---

## Сводная таблица

| Тип чекпоинта | actionType | questionText | inputTitle | answer | inputValue | photoPath |
|---------------|------------|--------------|------------|--------|------------|-----------|
| CHECKPOINT | SCAN → CHECKPOINT | null | null | null | null | null |
| QUESTION | SCAN → QUESTION | из базы | null | выбранный ответ | null | null |
| INPUT | SCAN → INPUT | null | из базы | null | введенные данные | null |
| PHOTO | SCAN → PHOTO | null | null | null | null | путь к фото |

---

## Как отображаются данные в журнале

### Для CHECKPOINT
- **Столбец 3 (Чекпоинт):** Название чекпоинта
- **Столбец 4 (Вопрос/Пояснение):** "-"
- **Столбец 5 (Ответ/Данные):** "-"
- **Столбец 7 (Примечание):** "-"

### Для QUESTION
- **Столбец 3 (Чекпоинт):** Название чекпоинта
- **Столбец 4 (Вопрос/Пояснение):** Текст вопроса (из базы)
- **Столбец 5 (Ответ/Данные):** Выбранный ответ
- **Столбец 7 (Примечание):** "-"

### Для INPUT
- **Столбец 3 (Чекпоинт):** Название чекпоинта
- **Столбец 4 (Вопрос/Пояснение):** Заголовок поля (из базы)
- **Столбец 5 (Ответ/Данные):** Введенные данные
- **Столбец 7 (Примечание):** "-"

### Для PHOTO
- **Столбец 3 (Чекпоинт):** Название чекпоинта
- **Столбец 4 (Вопрос/Пояснение):** Название чекпоинта
- **Столбец 5 (Ответ/Данные):** "Фото снято"
- **Столбец 6 (Фото):** "👁️" (ссылка на фото)

### Для НАРУШЕНИЯ
- **Столбец 3 (Чекпоинт):** Название чекпоинта (красным)
- **Столбец 4 (Вопрос/Пояснение):** "-"
- **Столбец 5 (Ответ/Данные):** "-"
- **Столбец 7 (Примечание):** "Вне очереди" (красным)
- **Рамка и текст:** Красные

---

## Модели данных

### ShiftLogEntry
```kotlin
data class ShiftLogEntry(
    val id: String,
    val timestamp: String,
    val checkpointName: String,
    val checkpointId: String,
    val employeeName: String,
    val roundId: Int,
    val shiftId: String,
    val routeName: String,
    val sequenceIndex: Int,
    val isSequenceCorrect: Boolean,
    val scanType: String,
    val actionType: String,
    val questionText: String? = null,
    val inputTitle: String? = null,
    val answer: String? = null,
    val inputValue: String? = null,
    val photoPath: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val sequenceErrorExpected: String? = null
)
```

### SequenceViolation
```kotlin
data class SequenceViolation(
    val id: String,
    val timestamp: String,
    val employeeName: String,
    val roundId: Int,
    val shiftId: String,
    val expectedCheckpointId: String,
    val expectedCheckpointName: String,
    val actualCheckpointId: String,
    val actualCheckpointName: String,
    val isNfc: Boolean = false
)
```

---

## Заключение

Система использует базу данных чекпоинтов для хранения статических данных (вопросы, заголовки полей ввода), а логи содержат только динамические данные (ответы, введенные значения, пути к фото). Это позволяет:

1. **Экономить место** - не дублировать тексты вопросов в каждом логе
2. **Легко обновлять** - изменение вопроса в базе автоматически отражается в журнале
3. **Упростить структуру** - логи содержат только необходимую информацию
