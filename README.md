# 🛡️ Приложение «Охрана» (Ohrana)

Android-приложение на базе **Kotlin** и **Jetpack Compose**, предназначенное для автоматизации работы службы безопасности и цифрового контроля обходов. Проект использует современную систему сборки **Gradle (Kotlin DSL)** с централизованным управлением зависимостями через Version Catalogs.

---

## 🚀 Основной функционал

Приложение разделено на логические блоки для управления сменами, маршрутами и отчетностью:

*   **🔐 Авторизация и роли:** Разграничение интерфейса для администраторов (`AdministratorScreen`) и сотрудников охраны (`OhrannikScreen`, `OhrannikCabinetScreen`).
*   **📍 Контроль обходов:** Мониторинг и управление рабочими сменами (`OhrannikShiftControl`) и маршрутами движения (`Marshruti`).
*   **📍 Трехуровневая проверка чекпоинтов:**
    - Чужеродная метка (QR/NFC не найден в базе)
    - Чекпоинт вне маршрута (существует, но не в текущем маршруте)
    - Точка вне очереди (в маршруте, но не следующая)
*   **📷 Сканер QR-кодов:** Интеграция модуля `QrHandler` для сканирования контрольных точек на объекте.
*   **📷 Фото чекпоинтов:** Съемка и сохранение фото приборов в папку `/Pictures/Ohrana/`.
*   **📷 Просмотр фото в журнале:** Открытие фотофайлов при нажатии на значок глаза в JournalScreen.
*   **📋 Отчетность:** Просмотр и формирование рапортов или журналов событий (`SpisokOtchetov`).
*   **💾 Локальное хранение:** Менеджер `SharedPrefsManager` для быстрого сохранения сессий и настроек пользователя.

---

## 📂 Структура проекта

Ниже представлен разбор ключевых директорий исходного кода приложения:

```text
app/src/main/
├── AndroidManifest.xml         # Главный манифест приложения (активности, разрешения)
├── java/com/example/ohrana/    # Исходный код на Kotlin
│   ├── ui/                     # UI компоненты и экраны
│   │   ├── theme/              # Дизайн-система (Цвета, Шрифты, Темы Jetpack Compose)
│   │   ├── journal/            # Экраны журнала и отчетов
│   │   └── ...                 # Экраны охраны и администратора
│   ├── JournalScreen.kt        # Основной экран журнала текущей смены
│   ├── JournalActivity.kt      # Активность для JournalScreen
│   ├── PhotoDialog.kt          # Диалог для просмотра фото
│   ├── OhrannikCabinetScreen.kt # Кабинет охранника
│   ├── OhrannikScreen.kt       # Основной экран сотрудника охраны
│   ├── QrHandler.kt            # Модуль обработки QR-кодов и NFC
│   ├── ShiftDatabaseManager.kt # Управление базой данных обходов
│   ├── SharedPrefsManager.kt   # Управление SharedPreferences
│   ├── Checkpoint.kt           # Модель чекпоинта
│   └── ...                     # Бизнес-логика и модели данных
└── res/                        # Ресурсы приложения
    ├── drawable/               # Графические элементы интерфейса
    ├── mipmap-.../             # Иконки приложения под разные разрешения
    ├── values/                 # Локализация (strings.xml) и базовые стили
    └── xml/                    # XML конфигурации (file_paths, data_extraction_rules)
```

---

## 📝 Журнал текущей смены

**JournalScreen** - основной экран для просмотра истории обходов:

### Структура таблицы:
- **Столбец 1**: Дата сканирования
- **Столбец 2**: Время сканирования
- **Столбец 3**: Название чекпоинта
- **Столбец 4**: Вопрос/Пояснение (берется из базы чекпоинтов)
- **Столбец 5**: Ответ/Данные
  - CHECKPOINT: "Точка пройдена"
  - QUESTION: Ответ пользователя
  - INPUT: Введенное значение
  - PHOTO: "Фото снято"
  - Нарушение: "-"
- **Столбец 6**: Фото (👁️ при наличии photoPath)
- **Столбец 7**: Примечание (нарушение последовательности)

### Три уровня нарушений:
1. **Чужеродная метка** - QR/NFC не найден в базе данных
2. **Чекпоинт вне маршрута** - чекпоинт существует, но не в текущем маршруте
3. **Точка вне очереди** - чекпоинт в маршруте, но не следующий ожидаемый

### Особенности:
- Отображаются только записи текущей активной смены
- Нарушения выделяются красным цветом
- Фото чекпоинты можно открыть для просмотра (нажатие на значок 👁️)
- Фото сохраняются в `/Pictures/Ohrana/` папку

---

## 🛠️ Стек технологий

*   **Язык программирования:** Kotlin
*   **Архитектура UI:** Jetpack Compose (Declarative UI)
*   **Система сборки:** Gradle (Kotlin DSL) + Gradle Wrapper
*   **Управление зависимостями:** Gradle Version Catalogs (`libs.versions.toml`)
*   **База данных:** SharedPreferences для локального хранения сессий, чекпоинтов, обходов и логов
*   **Камера:** CameraX для сканирования QR-кодов и съемки фото
*   **ML Kit:** Google ML Kit для распознавания QR-кодов и текста
*   **Тестирование:** JUnit (`ExampleUnitTest`) и Espresso/Compose UI Test (`ExampleInstrumentedTest`)

---

## 📷 Работа с фотографиями

### Сохранение фото:
- Фото чекпоинтов сохраняются в папку `/Pictures/Ohrana/`
- Имя файла: `{checkpointId}_{timestamp}.jpg`
- Используется функция `savePhotoToGallery()` для копирования из приватной папки
- PhotoCaptureScreen обновляет SCAN запись на PHOTO с photoPath

### Просмотр в журнале:
- Значок глаза (👁️) отображает наличие фото
- Нажатие на значок открывает PhotoDialog
- PhotoDialog загружает и отображает фото из файла
- При ошибке загрузки показывается сообщение "Невозможно загрузить фото"

---

## 💻 Развертывание и сборка

### Требования
*   Android Studio (рекомендуется актуальная версия Hedgehog / Iguana или новее)
*   JDK 17+

### Сборка проекта
Для сборки проекта из терминала используйте Gradle Wrapper:

**Windows:**
```bash
./gradlew.bat assembleDebug
```

**Linux / macOS:**
```bash
./gradlew assembleDebug
```

### Установка на устройство:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Запуск экрана журнала:
```bash
adb shell am start -n com.example.ohrana/.JournalActivity
```

---

## ⚙️ Полезная информация для разработчиков

### Конфигурация
*   **ADB:** `C:\Program Files\Genymobile\Genymotion\tools\adb.exe`
*   **Устройство:** `5VH9X19330G01139`
*   **Путь к фото:** `/Pictures/Ohrana/`
*   **Формат фото:** `{checkpointId}_{timestamp}.jpg`

### Основные классы:
*   **JournalScreen** - основной экран журнала текущей смены
*   **JournalActivity** - активность для JournalScreen (landscape ориентация)
*   **PhotoDialog** - диалог для просмотра фото
*   **QrHandler** - обработка QR-кодов и NFC
*   **ShiftDatabaseManager** - управление базой данных обходов
*   **SharedPrefsManager** - управление SharedPreferences
*   **OhrannikCabinetScreen** - кабинет охранника

### Три уровня нарушений:
1. **Чужеродная метка** - QR/NFC не найден в базе данных
2. **Чекпоинт вне маршрута** - чекпоинт существует, но не в текущем маршруте
3. **Точка вне очереди** - чекпоинт в маршруте, но не следующий ожидаемый

### Работа с данными:
*   **questionText** и **inputTitle** берутся из чекпоинта по checkpointId
*   **photoPath** сохраняется в логах для PHOTO чекпоинтов
*   **isSequenceCorrect** = false для нарушений последовательности
*   **actionType** может быть: CHECKPOINT, QUESTION, INPUT, PHOTO, SCAN

### Кэширование сборки:** В проекте активно используется `Configuration Cache`. В случае возникновения проблем с плагинами, интерактивные отчеты сборщика генерируются в папку `build/reports/configuration-cache/`.
*   **Логи компилятора:** Внутренний кэш компилятора Kotlin и логи ошибок (при наличии) находятся в локальной директории `.kotlin/errors/`.
*   **Игнорирование файлов:** Сборка, локальные настройки (`local.properties`) и кэш IDE (`.idea/`, `.gradle/`, `build/`) автоматически исключены из отслеживания Git.


implementation - Implementation only dependencies for 'main' sources. (n)
+--- androidx.compose.runtime:runtime (n)
+--- androidx.compose.ui:ui (n)
+--- androidx.compose:compose-bom:2024.02.02 (n)
+--- androidx.activity:activity-compose:1.13.0 (n)
+--- androidx.compose.material3:material3 (n)
+--- androidx.compose.ui:ui (n)
+--- androidx.compose.ui:ui-graphics (n)
+--- androidx.compose.ui:ui-text:1.11.3 (n)
+--- androidx.compose.ui:ui-tooling-preview (n)
+--- androidx.core:core-ktx:1.15.0 (n)
+--- androidx.glance:glance:1.1.1 (n)
+--- androidx.lifecycle:lifecycle-runtime-ktx:2.8.7 (n)
+--- androidx.compose.material:material-icons-core (n)
+--- com.google.code.gson:gson:2.10.1 (n)
+--- androidx.camera:camera-core:1.4.0 (n)
+--- androidx.camera:camera-camera2:1.4.0 (n)
+--- androidx.camera:camera-lifecycle:1.4.0 (n)
+--- androidx.camera:camera-view:1.4.0 (n)
+--- androidx.camera:camera-mlkit-vision:1.4.0 (n)
+--- com.google.mlkit:barcode-scanning:17.3.0 (n)
+--- com.google.mlkit:text-recognition:16.0.1 (n)
\--- com.google.mlkit:barcode-scanning:17.2.0 (n)
