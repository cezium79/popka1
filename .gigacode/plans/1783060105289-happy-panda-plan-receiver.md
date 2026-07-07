# План создания приложения OhranaReceiver

## Создано 07.07.2026

## Введение

OhranaReceiver - отдельное приложение для приема отчетов и настройки из основного приложения Ohrana через Yandex Cloud.

## Архитектура

```
┌──────────────────┐                    ┌──────────────────┐
│   Ohrana         │                    │  OhranaReceiver  │
│   (основное)     │                    │   (приемник)     │
│                  │                    │                  │
│ - Отчеты →      │   Yandex Cloud     │ - Отчеты ←      │
│ - Настройки →   │   Storage          │ - Настройки ←    │
│                 │   Bucket           │                  │
└──────────────────┘                    └──────────────────┘
```

## Шаг 1: Создание нового проекта

### В Android Studio:

1. **File** → **New** → **New Project**
2. **Empty Activity**
3. Name: **OhranaReceiver**
4. Package name: **com.example.ohranareceiver**
5. Language: **Kotlin**
6. Minimum SDK: **API 21 (Android 5.0)**

## Шаг 2: Зависимости (build.gradle.kts)

```kotlin
dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
}
```

## Шаг 3: Структура проекта

```
app/src/main/java/com/example/ohranareceiver/
├── MainActivity.kt              - Главный экран списка отчетов
├── CloudReceiverManager.kt      - Работа с Yandex Cloud
├── ReportViewModel.kt           - Вьюмодель для управления состоянием
├── ReceiverScreen.kt            - Компоновка экрана
└── ReportViewerActivity.kt      - Просмотр отчета
```

## Шаг 4: Key Features

### 4.1 Подключение к Yandex Cloud
- OAuth token (тот же токен, что и в Ohrana)
-.bucket name и path (те же параметры)
- Список отчетов из бакета

### 4.2 Список отчетов
- Отображение всех отчетов (JSON, HTML, PDF)
- Сортировка по дате (новые первыми)
- Размер отчета
- Дата создания

### 4.3 Загрузка отчета
- Кнопка загрузки на устройство
- Прогресс бар
- Сохранение в /Download/Ohrana/Reports/

### 4.4 Просмотр отчета
- Открытие в браузере (HTML)
- Открытие PDF viewer (PDF)
- Просмотр JSON в текстовом редакторе

### 4.5 Удаленная настройка (вторая фаза)
- Получение команд из облака
- Применение настроек
- Синхронизация

## Шаг 5: AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    
    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="Ohrana Receiver"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.OhranaReceiver">
        
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.OhranaReceiver">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        
        <activity
            android:name=".ReportViewerActivity"
            android:exported="true"
            android:theme="@style/Theme.OhranaReceiver" />
            
    </application>
</manifest>
```

## Шаг 6: Стек технологий

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Architecture:** MVVM (Model-View-ViewModel)
- **Networking:** HttpURLConnection / OkHttp
- **Storage:** SharedPreferences
- **Cloud:** Yandex Cloud Storage API
- **JSON:** Gson / kotlinx-serialization

## Шаг 7: Взаимодействие с Ohrana

### Ожидаемые параметры от Ohrana:

**Токен OAuth:**
- Пользователь вводит токен при первом запуске
- Сохраняется в SharedPreferences
- Используется для доступа к бакету

**Параметры бакета:**
- Имя бакета
- Путь к отчетам (например, "Reports")

### Формат отчетов:

**JSON отчет:**
```json
{
  "shift_id": "NS070726_001",
  "employee_name": "Иванов Иван",
  "start_time": "2026-07-07 09:00:00",
  "rounds": [...],
  "logs": [...]
}
```

**HTML отчет:**
- Самодостаточный HTML файл
- Открывается в браузере

**PDF отчет:**
- Генерируется на стороне Ohrana
- Загружается в облако

## Шаг 8: UI Экранов

### 8.1 MainActivity (Список отчетов)

```
┌─────────────────────────┐
│  Ohrana Receiver        │
├─────────────────────────┤
│ [Список отчетов]        │
│                         │
│ Shift_001_20260707.json │
│ Shift_001_20260707.html │
│ Shift_001.pdf           │
│                         │
│ Shift_002_20260706.json │
│ Shift_002_20260706.html │
│                         │
│ [Обновить]              │
└─────────────────────────┘
```

### 8.2 ReportViewerActivity

```
┌─────────────────────────┐
│  Shift_001.html         │
├─────────────────────────┤
│                         │
│   [Отчет о смене]       │
│                         │
│   Сотрудник: Иванов Иван│
│   Дата: 07.07.2026      │
│                         │
│   [Список кнопок...]    │
│                         │
│   [Обходы и чекпоинты]  │
│                         │
└─────────────────────────┘
```

## Шаг 9: Поток использования

### Для пользователя:

1. **Установка:**
   - Установить OhranaReceiver на устройство
   - Открыть приложение
   - Ввести OAuth token (от Ohrana)
   - Ввести имя бакета и путь

2. **Получение отчетов:**
   - Открыть приложение
   - Увидеть список доступных отчетов
   - Нажать кнопку загрузки
   - Открыть отчет в браузере

3. **Удаленная настройка (фаза 2):**
   - В Ohrana изменить настройки
   - Настройки загружаются в облако
   - OhranaReceiver получает обновления
   - Настройки применяются

## Шаг 10: Тестирование

### Тест-кейсы:

1. **Подключение к облаку:**
   - ✅ Ввод токена и параметров
   - ✅ Сохранение в SharedPreferences
   - ✅ Проверка подключения

2. **Список отчетов:**
   - ✅ Получение списка файлов
   - ✅ Показ только отчетов
   - ✅ Сортировка по дате

3. **Загрузка отчета:**
   - ✅ Загрузка JSON
   - ✅ Загрузка HTML
   - ✅ Загрузка PDF
   - ✅ Прогресс бар

4. **Просмотр отчета:**
   - ✅ Открытие HTML в браузере
   - ✅ Открытие PDF в viewer
   - ✅ Открытие JSON в редакторе

## Шаг 11: Безопасность

### OAuth Token:
- Хранение в SharedPreferences
- Защита от случайного экспорта
- Возможность смены токена

### Сетевой трафик:
- HTTPS для всех запросов
- Валидация сертификатов
- Обработка ошибок сети

### Хранение данных:
- Локальное кэширование
- Очистка старых отчетов
- Контроль объема хранилища

## Шаг 12: Фазы разработки

### Фаза 1: Базовая функциональность
- ✅ Подключение к Yandex Cloud
- ✅ Список отчетов
- ✅ Загрузка отчетов
- ✅ Просмотр отчетов

### Фаза 2: Удаленная настройка
- Добавление команд из облака
- Применение настроек
- Синхронизация состояния

### Фаза 3: Улучшения
- Автоматическая синхронизация
- Уведомления о новых отчетах
- Кэширование
- Оффлайн режим

## Требования к файлам

### CloudReceiverManager.kt
- Список методов из CloudStorageManager.kt
- Адаптация под приемник
- Методы для чтения файлов

### MainActivity.kt
- Список отчетов
- Загрузка отчетов
- Навигация к ReportViewerActivity

### ReportViewModel.kt
- Управление состоянием
- Список отчетов
- Прогресс загрузки

### ReportViewerActivity.kt
- Просмотр отчета
- Кнопки действий (открыть, скачать, удалить)

## Следующие шаги

После создания проекта:

1. Скопировать CloudReceiverManager.kt
2. Создать MainActivity.kt с UI
3. Создать ReportViewModel.kt
4. Создать ReportViewerActivity.kt
5. Настроить AndroidManifest.xml
6. Протестировать подключение к Yandex Cloud
7. Протестировать получение списка отчетов
8. Протестировать загрузку отчетов

## Примечания

- OhranaReceiver - полностью отдельное приложение
- Использует те же Yandex Cloud параметры, что и Ohrana
- Может быть установлено на другое устройство
- Не зависит от основного приложения Ohrana
