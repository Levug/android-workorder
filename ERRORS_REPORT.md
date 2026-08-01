# Отчет об ошибках в проекте "Приложение нарядов"

Дата анализа: 2026-05-18

## Критические ошибки

### 1. Утечка памяти в WorkDayViewModel (строка 68)
**Файл:** `app/src/main/java/com/workorder/app/ui/viewmodel/WorkDayViewModel.kt:68`

**Проблема:**
```kotlin
workDayRepository.getOperationsByWorkDay(workDay.id).collect { workDayOps ->
    // ...
}
```

Вызов `collect` внутри `viewModelScope.launch` создает бесконечную подписку на Flow, которая никогда не отменяется. Это приводит к утечке памяти и продолжению работы корутины даже после закрытия экрана.

**Решение:**
Использовать `first()` вместо `collect`, так как нужно получить данные только один раз:
```kotlin
val workDayOps = workDayRepository.getOperationsByWorkDay(workDay.id).first()
val opsWithCount = workDayOps.mapNotNull { wdo ->
    val op = operationRepository.getOperationById(wdo.operationId)
    op?.let { OperationWithCount(it, wdo.count) }
}
_selectedOperations.value = opsWithCount
_isLoading.value = false
```

---

### 2. Утечка памяти в OperationsViewModel (строка 25)
**Файл:** `app/src/main/java/com/workorder/app/ui/viewmodel/OperationsViewModel.kt:25`

**Проблема:**
```kotlin
operationRepository.getAllOperations().collect { ops ->
    _operations.value = ops
}
```

Аналогичная проблема - бесконечная подписка на Flow в `viewModelScope.launch` без возможности отмены.

**Решение:**
Сохранить Job и отменить его в `onCleared()`, или использовать `stateIn`:
```kotlin
val operations: StateFlow<List<Operation>> = operationRepository
    .getAllOperations()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
```

---

### 3. Утечка памяти в HomeViewModel (строка 51)
**Файл:** `app/src/main/java/com/workorder/app/ui/viewmodel/HomeViewModel.kt:51`

**Проблема:**
```kotlin
workDayRepository.getWorkDaysByMonth(_currentMonth.value).collect { days ->
    _workDays.value = days
}
```

При каждом вызове `loadWorkDays()` создается новая подписка, но старые не отменяются. При переключении месяцев накапливаются активные корутины.

**Решение:**
Сохранить Job и отменять предыдущую подписку:
```kotlin
private var loadJob: Job? = null

private fun loadWorkDays() {
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
        workDayRepository.getWorkDaysByMonth(_currentMonth.value).collect { days ->
            _workDays.value = days
        }
    }
}
```

---

### 4. Создание новых ViewModel на каждую рекомпозицию в MainActivity
**Файл:** `app/src/main/java/com/workorder/app/MainActivity.kt`

**Проблема:**
```kotlin
composable(Screen.Home.route) {
    val viewModel = HomeViewModel(workDayRepository)  // Создается заново при каждой рекомпозиции!
    HomeScreen(...)
}
```

ViewModel создается напрямую в composable функции, что приводит к:
- Потере состояния при рекомпозиции
- Утечкам памяти (старые ViewModel не уничтожаются)
- Повторной загрузке данных

**Решение:**
Использовать `viewModel()` функцию с фабрикой или внедрение зависимостей (Hilt/Koin):
```kotlin
composable(Screen.Home.route) {
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(workDayRepository)
    )
    HomeScreen(...)
}
```

Или создать ViewModelFactory:
```kotlin
class HomeViewModelFactory(
    private val workDayRepository: WorkDayRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(workDayRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
```

Эта проблема касается ВСЕХ экранов в MainActivity (строки 53, 72, 87, 104, 128, 142, 151).

---

## Средние ошибки

### 5. Отсутствие обработки ошибок в saveWorkDay
**Файл:** `app/src/main/java/com/workorder/app/ui/viewmodel/WorkDayViewModel.kt:127`

**Проблема:**
Метод `saveWorkDay` не обрабатывает возможные исключения при работе с базой данных.

**Решение:**
```kotlin
fun saveWorkDay(onSuccess: () -> Unit, onError: (String) -> Unit) {
    viewModelScope.launch {
        try {
            val hours = _totalHours.value.toDoubleOrNull() ?: run {
                onError("Неверный формат часов")
                return@launch
            }
            // ... остальной код
            onSuccess()
        } catch (e: Exception) {
            onError("Ошибка сохранения: ${e.message}")
        }
    }
}
```

---

### 6. Отсутствие валидации в AddEditOperationViewModel
**Файл:** `app/src/main/java/com/workorder/app/ui/viewmodel/AddEditOperationViewModel.kt:49`

**Проблема:**
```kotlin
fun saveOperation(onSuccess: () -> Unit) {
    viewModelScope.launch {
        val duration = _durationHours.value.toDoubleOrNull() ?: return@launch
        if (_name.value.isBlank()) return@launch
        // ...
    }
}
```

Нет обратной связи пользователю о том, почему операция не сохранилась.

**Решение:**
Добавить состояние ошибки и callback:
```kotlin
private val _error = MutableStateFlow<String?>(null)
val error: StateFlow<String?> = _error.asStateFlow()

fun saveOperation(onSuccess: () -> Unit) {
    viewModelScope.launch {
        _error.value = null

        if (_name.value.isBlank()) {
            _error.value = "Введите название операции"
            return@launch
        }

        val duration = _durationHours.value.toDoubleOrNull()
        if (duration == null || duration <= 0) {
            _error.value = "Введите корректное время выполнения"
            return@launch
        }
        // ... сохранение
    }
}
```

---

### 7. Отсутствие проверки разрешений для Android 13+
**Файл:** `app/src/main/AndroidManifest.xml:4-6`

**Проблема:**
```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
```

Для экспорта PDF/CSV на Android 13+ (API 33+) нужны другие разрешения или использование MediaStore API.

**Решение:**
Добавить разрешения для Android 13+:
```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
```

И в коде проверять версию API и запрашивать соответствующие разрешения.

---

## Незначительные замечания

### 8. Неоптимальный запрос в WorkDayOperationDao
**Файл:** `app/src/main/java/com/workorder/app/data/dao/WorkDayOperationDao.kt:12-17`

**Замечание:**
Запрос можно оптимизировать, добавив индекс на `date` в таблице `work_days`.

**Рекомендация:**
В модели WorkDay добавить:
```kotlin
@Entity(
    tableName = "work_days",
    indices = [Index(value = ["date"], unique = true)]
)
```

---

### 9. Отсутствие экспорта схемы базы данных
**Файл:** `app/src/main/java/com/workorder/app/data/AppDatabase.kt:20`

**Замечание:**
```kotlin
exportSchema = false
```

Лучше экспортировать схему для отслеживания изменений и тестирования миграций.

**Рекомендация:**
```kotlin
exportSchema = true
```

И в build.gradle.kts добавить:
```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

---

## Резюме

**Критические ошибки:** 4
- Утечки памяти в ViewModels (3 случая)
- Неправильное создание ViewModel в Compose (затрагивает все экраны)

**Средние ошибки:** 3
- Отсутствие обработки ошибок
- Недостаточная валидация
- Проблемы с разрешениями

**Незначительные замечания:** 2
- Оптимизация запросов
- Экспорт схемы БД

**Приоритет исправления:**
1. Исправить создание ViewModel в MainActivity (критично)
2. Исправить утечки памяти в ViewModels (критично)
3. Добавить обработку ошибок (важно)
4. Исправить разрешения для Android 13+ (важно)
5. Остальные улучшения (по возможности)
