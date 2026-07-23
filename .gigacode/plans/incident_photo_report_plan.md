# План реализации функциональности: Фиксация происшествий с фото и заметками

## 📋 Общее описание

Необходимо реализовать возможность для охранника во время обхода вызвать экран камеры для фиксации происшествий на объекте (посторонний предмет, пропажа предмета, последствия вандализма и т.д.). После съемки - возможность оставить пояснение к фото. Фото и заметки должны отображаться в отчете о смене как "происшествия", но их наличие/отсутствие не должно считаться нарушением обхода.

---

## 🎯 Цели и требования

### Основные требования:

1. **Добавить кнопку вызова экрана фиксации происшествия** во время обхода
2. **Создать экран камеры** для съемки происшествия (аналогично `PhotoCaptureScreen.kt`)
3. **Добавить поле ввода** для пояснения к фото
4. **Сохранять фото и заметку** в отдельную таблицу "происшествия"
5. **Фото и заметки не должны влиять** на статус завершения обхода
6. **Отображать происшествия** в HTML отчете и в текстовом журнале обходов
7. **Добавить статистику** по происшествиям в итоги смены

### Дополнительные требования (по вашим ответам):

8. **Типы происшествий**: добавить все типы + "ломка":
   - Посторонний предмет
   - Пропажа предмета
   - Последствия вандализма
   - Поломка
   - Другое

9. **Доступ к кнопке**: кнопка должна быть доступна:
   - В `OhrannikCabinetScreen.kt` (во время обхода)
   - В `RoundsScreen.kt` (в списке обходов)

10. **Хранение фото**: сохранять в галерею по пути `Ohrana/Incidents/`

11. **Геолокация**: можно добавить позже (опционально)

---

## 🏗️ Архитектурные решения

### 1. Новые модели данных

**Файл: `ShiftDatabaseModels.kt`**

Добавить новую модель `IncidentRecord`:

```kotlin
data class IncidentRecord(
    val id: String,              // Уникальный ID
    val timestamp: String,       // Время регистрации
    val shiftId: String,         // ID смены
    val roundId: Int,            // ID обхода (если происшествие во время обхода)
    val employeeName: String,    // Имя охранника
    val incidentType: IncidentType,  // Тип происшествия
    val description: String,     // Описание/пояснение
    val photoPath: String,       // Путь к фото
    val latitude: Double? = null, // Геоданные (опционально)
    val longitude: Double? = null
)
```

Добавить enum:

```kotlin
enum class IncidentType {
    FOREIGN_ITEM,        // Посторонний предмет
    MISSING_ITEM,        // Пропажа предмета
    VANDALISM_DAMAGE,    // Последствия вандализма
    BREAKDOWN,           // Поломка
    OTHER                // Другое
}
```

---

### 2. Новые методы в `ShiftDatabaseManager.kt`

**Добавить методы для управления происшествиями:**

```kotlin
// Добавить запись о происшествии
fun addIncident(
    roundId: Int,
    shiftId: String,
    employeeName: String,
    incidentType: IncidentType,
    description: String,
    photoPath: String,
    latitude: Double? = null,
    longitude: Double? = null
): String

// Загрузить все происшествия
fun loadAllIncidents(): List<IncidentRecord>

// Загрузить происшествия по ID смены
fun loadIncidentsByShift(shiftId: String): List<IncidentRecord>

// Загрузить происшествия по ID обхода
fun loadIncidentsByRound(roundId: Int): List<IncidentRecord>
```

---

### 3. Новый экран: `IncidentCaptureScreen.kt`

**Файл: `app/src/main/java/com/example/ohrana/IncidentCaptureScreen.kt`**

Компоненты экрана:
- Камера (Preview + кнопка съемки)
- Выбор типа происшествия (комбобокс/радио-кнопки)
- Поле ввода описания
- Кнопки: "Сохранить", "Отмена", "Повторить фото"

### Порядок действий при нажатии кнопки:

1. Пользователь нажимает "Зафиксировать происшествие" в главном cabinet или списке обходов
2. Открывается `IncidentCaptureScreen` с камерой
3. После съемки фото → выбор типа происшествия → ввод описания
4. Сохранение в галерею и БД

Архитектура аналогична `PhotoCaptureScreen.kt`, но с дополнительными полями.

---

### 4. Интеграция в текущий поток обхода

**Модификация экранов обхода:**

В `OhrannikCabinetScreen.kt` и `RoundsScreen.kt` добавить:

```kotlin
// Кнопка "Зафиксировать происшествие" (всегда видна во время обхода)
Button(
    onClick = { onOpenIncidentScreen(roundId, shiftId) },
    modifier = Modifier.padding(16.dp)
) {
    Icon(Icons.Default.CameraAlt, contentDescription = "Фото")
    Text("Зафиксировать происшествие")
}
```

**Параметры вызова:**
- `roundId` - текущий ID обхода
- `shiftId` - ID активной смены

---

### 5. Генерация отчета с происшествиями

**Модификация `CloudStorageManager.kt`**

В `generateHtmlReportWithDesign()` добавить новую секцию:

```kotlin
// Происшествия за смену
val incidents = shiftDatabase.loadIncidentsByShift(shiftId)
if (incidents.isNotEmpty()) {
    html.append("<div class=\"section\">")
    html.append("<h2>📸 Происшествия</h2>")
    
    incidents.forEach { incident ->
        html.append("<div class=\"incident-card\">")
        html.append("<h3>${incident.incidentType.name}</h3>")
        html.append("<div class=\"incident-info\">")
        html.append("<span>Время: ${incident.timestamp}</span>")
        html.append("<span>Охранник: ${incident.employeeName}</span>")
        html.append("</div>")
        html.append("<p>Описание: ${incident.description}</p>")
        
        // Встроить фото как base64
        val photoBase64 = loadPhotoAsBase64(incident.photoPath)
        html.append("<img src=\"data:image/jpeg;base64,$photoBase64\" alt=\"Incident photo\">")
        
        html.append("</div>")
    }
    html.append("</div>")
}
```

**Добавить статистику:**

```kotlin
// В статистике смены
val incidentsCount = incidents.size
html.append("<div class=\"stat-item\">")
html.append("<div class=\"stat-value\" style=\"color: #ff9800;\">$incidentsCount</div>")
html.append("<div class=\"stat-label\">Происшествий</div>")
html.append("</div>")
```

---

## 📂 Файлы для создания/модификации

### Создание новых файлов:

1. **`app/src/main/java/com/example/ohrana/IncidentCaptureScreen.kt`**
   - Экран камеры для фиксации происшествия

2. **`app/src/main/java/com/example/ohrana/IncidentReportViewerScreen.kt`** (опционально)
   - Экран просмотра списка происшествий

### Модификация существующих файлов:

1. **`app/src/main/java/com/example/ohrana/ShiftDatabaseModels.kt`**
   - Добавить `IncidentRecord` и `IncidentType`

2. **`app/src/main/java/com/example/ohrana/ShiftDatabaseManager.kt`**
   - Добавить методы управления происшествиями
   - Сохранение/загрузка из SharedPreferences

3. **`app/src/main/java/com/example/ohrana/CloudStorageManager.kt`**
   - Добавить отображение происшествий в HTML отчете
   - Добавить отображение происшествий в текстовом журнале
   - Для текстового журнала использовать ссылку на файл в галерее

4. **`app/src/main/java/com/example/ohrana/OhrannikCabinetScreen.kt`**
   - Добавить кнопку вызова экрана фиксации происшествия
   - Обработка нажатия кнопки и передача параметров

5. **`app/src/main/java/com/example/ohrana/RoundsScreen.kt`**
   - Добавить кнопку вызова экрана фиксации происшествия
   - Обработка нажатия кнопки

6. **`app/src/main/java/com/example/ohrana/SharedPrefsManager.kt`**
   - Добавить методы для работы с происшествиями в SharedPreferences (если нужно)

---

## 🎨 UI/UX рекомендации

### Экран выбора типа происшествия:

```kotlin
@Composable
fun IncidentTypeSelector(
    selectedType: IncidentType,
    onSelectType: (IncidentType) -> Unit
) {
    Column {
        Text("Тип происшествия", style = MaterialTheme.typography.subtitle1)
        Spacer(modifier = Modifier.height(8.dp))
        
        IncidentType.values().forEach { type ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectType(type) }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(type.name.replace('_', ' '))
                if (selectedType == type) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Green)
                }
            }
        }
    }
}
```

### Экран описания:

```kotlin
@Composable
fun IncidentDescriptionInput(
    description: String,
    onDescriptionChange: (String) -> Unit
) {
    OutlinedTextField(
        value = description,
        onValueChange = onDescriptionChange,
        label = { Text("Описание происшествия") },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 4
    )
}
```

---

## 🔐 Работа с фото

### Сохранение фото:

Аналогично `savePhotoToGallery()`:

```kotlin
fun saveIncidentPhotoToGallery(sourceFile: File, context: Context): String? {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val destFileName = "incident_${timestamp}.jpg"
    
    val galleryDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
    val ohranaDir = File(galleryDir, "Ohrana/Incidents")
    if (!ohranaDir.exists()) ohranaDir.mkdirs()
    
    val destFile = File(ohranaDir, destFileName)
    
    // Копирование файла
    sourceFile.inputStream().use { input ->
        destFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }
    
    // Обновление галереи
    MediaScannerConnection.scanFile(
        context,
        arrayOf(destFile.absolutePath),
        arrayOf("image/jpeg"),
        null
    )
    
    return destFile.absolutePath
}
```

---

## 📊 Статистика и отчетность

### Показатели для отчета:

1. **Количество происшествий за смену**
2. **Типы происшествий (распределение)**
3. **Время регистрации происшествий**
4. **Охранники, зарегистрировавшие происшествия

### В HTML отчете:

- Отдельная секция "📸 Происшествия" со всеми фото и описаниями
- Статистическая карточка с количеством происшествий
- Группировка по типам происшествий

### В текстовом журнале:

- Добавить раздел "Происшествия" в конец журнала обходов
- Для каждого происшествия: тип, время, описание, ссылка на фото (в галерее)
- Итоговая статистика по происшествиям

### SQL-подобные запросы (в SharedPreferences):

```kotlin
// Получить количество происшествий по типам
val incidents = shiftDatabase.loadIncidentsByShift(shiftId)
val typeCounts = incidents.groupBy { it.incidentType }.mapValues { it.value.size }

// Получить происшествия за последний час
val oneHourAgo = System.currentTimeMillis() - 3600000
val recentIncidents = incidents.filter { 
    val timestamp = dateFormat.parse(it.timestamp)?.time ?: 0
    timestamp > oneHourAgo
}
```

---

## 🚀 План реализации (поэтапно)

### **Этап 1: Базовая структура (2-3 часа)**
- [x] Добавить `IncidentRecord` и `IncidentType` в `ShiftDatabaseModels.kt`
- [x] Добавить методы `addIncident()`, `loadIncidentsByShift()` в `ShiftDatabaseManager.kt`
- [x] Создать структуру папок для хранения incident photos (используется существующая структура галереи)

### **Этап 2: Экран фиксации (4-5 часов)**
- [x] Создать `IncidentCaptureScreen.kt`
- [x] Реализовать выбор типа происшествия
- [x] Реализовать ввод описания
- [x] Интегрировать камеру (аналогично `PhotoCaptureScreen.kt`)

### **Этап 3: Интеграция в обход (2 часа)**
- [x] Добавить кнопку вызова экрана фиксации в `OhrannikCabinetScreen.kt`
- [x] Обработка нажатия кнопки и передача параметров
- [x] Сохранение происшествия в БД (через экран фиксации)
- [x] Добавить кнопку вызова экрана фиксации в `RoundsScreen.kt`

### **Этап 4: Отчетность (4 часа)**
- [x] Добавить секцию "Происшествия" в HTML отчет (в двух функциях: generateHtmlReportWithDesign и uploadHtmlToDiskDirect)
- [x] Добавить секцию "Происшествия" в текстовый журнал
- [x] Добавить статистику по происшествиям в итоги (в двух функциях)
- [x] Реализовать встроенные фото в отчеты

**Примечание:** Код статистики и секции происшествий дублируется в функциях `generateHtmlReportWithDesign` и `uploadHtmlToDiskDirect`. Рекомендуется рефакторинг в будущем для устранения дублирования.

### **Этап 5: Тестирование и отладка (3 часа)**
- [ ] Тест съемки фото
- [ ] Тест сохранения в БД
- [ ] Тест отображения в HTML отчете
- [ ] Тест отображения в текстовом журнале
- [ ] Проверка, что происшествия не влияют на статус обхода

---

## ⚠️ Важные нюансы

1. **Не влияют на нарушения обхода**: Фото происшествий не должны проверяться на правильность последовательности

2. **Оптимальное хранение**: Фото сохраняются в отдельную папку `Ohrana/Incidents/`, не перемешиваются с фото приборов

3. **Геолокация**: можно добавить позже (опционально)

4. **Бэкап данных**: При экспорте настроек сохранять и происшествия (или только ссылки на фото)

5. **Приватность**: Фото происшествий не должны публиковаться публично без необходимости

6. **Текстовый журнал**: Происшествия отображаются в текстовом журнале обходов (рядом с HTML отчетом)

---

## 🧪 Тестовые сценарии

1. **Сценарий 1**: Охранник фиксирует происшествие во время обхода
   - Шаги: Открыть экран → Выбрать тип → Ввести описание → Сделать фото → Сохранить
   - Ожидание: Фото сохранено, появилось в отчете

2. **Сценарий 2**: Происшествие не влияет на статус обхода
   - Шаги: Начать обход → Зафиксировать происшествие → Завершить обход
   - Ожидание: Обход завершен успешно, происшествие отображается отдельно

3. **Сценарий 3**: Отчет содержит происшествия
   - Шаги: Завершить смену с происшествиями → Просмотреть HTML отчет
   - Ожидание: В отчете есть секция "Происшествия" с фото

4. **Сценарий 4**: Текстовый журнал содержит происшествия
   - Шаги: Завершить смену с происшествиями → Просмотреть текстовый журнал
   - Ожидание: В журнале есть раздел "Происшествия" с описаниями

---

## 📝 Примечания

- Можно добавить фильтрацию по типам происшествий в отчете
- Можно добавить возможность редактирования описания до закрытия смены
- Можно добавить уведомления о происшествиях на сервер (через File.io или email)
- Можно добавить кнопку быстрого вызова происшествий для часто встречающихся типов
- Можно добавить статистику по происшествиям за день/неделю/месяц
