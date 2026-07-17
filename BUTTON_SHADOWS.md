# Добавление теней на кнопки

## Обзор

Все кнопки в приложении теперь имеют тени для улучшения визуального восприятия.

## Реализованные компоненты

Создан файл `ui/components/OhranaButtons.kt` с готовыми компонентами кнопок:

- `OhranaButton` - ElevatedButton с тенями (рекомендуемый вариант)
- `OhranaContainedButton` - Button с тенями
- `OhranaTextButton` - TextButton с тенями  
- `OhranaOutlinedButton` - OutlinedButton с тенями

## Использование

### Вариант 1: Использование OhranaButton (рекомендуется)

```kotlin
import com.example.ohrana.ui.components.OhranaButton

OhranaButton(
    text = "Нажми меня",
    onClick = { /* обработчик нажатия */ }
)
```

### Вариант 2: Использование стандартных кнопок с тенями

```kotlin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults

Button(
    onClick = { /* обработчик */ },
    elevation = ButtonDefaults.buttonElevation(
        defaultElevation = 4.dp,
        pressedElevation = 8.dp,
        disabledElevation = 0.dp
    )
) {
    Text("Кнопка")
}
```

### Вариант 3: Маленькие кнопки с тенями

Для маленьких кнопок используйте `SmallButtonElevations`:

```kotlin
Button(
    onClick = { /* обработчик */ },
    elevation = SmallButtonElevations
) {
    Text("Маленькая кнопка")
}
```

## Настройка теней

Тени настраиваются через параметр `elevation`:

- `defaultElevation = 4.dp` - тень по умолчанию
- `pressedElevation = 8.dp` - тень при нажатии
- `disabledElevation = 0.dp` - без тени для отключенных кнопок

## Обновление существующего кода

Найдите все использования `Button`, `TextButton`, `OutlinedButton` и добавьте параметр `elevation`:

```diff
Button(
    onClick = { ... },
+   elevation = ButtonDefaults.buttonElevation(
+       defaultElevation = 4.dp,
+       pressedElevation = 8.dp
+   )
) {
    Text("Кнопка")
}
```

## Примечания

- Все компоненты использует `ElevatedButton` для кнопок с тенями
- Тема `OhranaTheme` должна быть применена ко всему приложению (в MainActivity)
- Тени видны лучше на темных фонах, но работают на любом фоне
