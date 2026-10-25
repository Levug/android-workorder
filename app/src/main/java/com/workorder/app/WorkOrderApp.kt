package com.workorder.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.workorder.app.data.AppDatabase
import com.workorder.app.data.model.DEFAULT_CUSTOM_THEME_COLOR
import com.workorder.app.data.model.Settings
import com.workorder.app.data.model.ThemeMode
import com.workorder.app.data.repository.OperationRepository
import com.workorder.app.data.repository.BankRepository
import com.workorder.app.data.repository.SettingsRepository
import com.workorder.app.data.repository.WorkOrderRepository
import com.workorder.app.util.ExportImportManager
import com.workorder.app.util.ReportExporter
import com.workorder.app.sync.PhoneWearEventProcessor
import com.workorder.app.sync.PhoneWearApi
import com.workorder.app.sync.PhoneWearSyncManager
import com.workorder.app.sync.WearSyncPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.YearMonth

/**
 * Ручной DI-контейнер: единственное место, где создаются база и репозитории.
 * ViewModel получают зависимости через свои companion Factory (см. ui/viewmodel).
 */
class AppContainer(context: Context) {
    private val database = AppDatabase.getDatabase(context)

    val settingsRepository = SettingsRepository(database.settingsDao())
    val workOrderRepository = WorkOrderRepository(database, settingsRepository)
    val operationRepository = OperationRepository(database.operationDao())
    val bankRepository = BankRepository(database)
    /** Мелкие настройки интерфейса этого устройства; в резервную копию не входят. */
    val uiPreferences = context.applicationContext.getSharedPreferences("ui_preferences", Context.MODE_PRIVATE)

    private val phoneWearApi = PhoneWearApi(context.applicationContext)
    private val wearPreferences = WearSyncPreferences(uiPreferences)
    val wearEventProcessor = PhoneWearEventProcessor(
        phoneWearApi,
        operationRepository,
        workOrderRepository,
        wearPreferences
    )
    val wearSyncManager = PhoneWearSyncManager(
        phoneWearApi,
        operationRepository,
        settingsRepository,
        wearEventProcessor,
        wearPreferences
    )

    val reportExporter = ReportExporter(context.applicationContext)
    val exportImportManager = ExportImportManager(
        context.applicationContext,
        com.workorder.app.data.repository.BackupRepository(database)
    )

    /** Месяц, выбранный в календаре и отчётах: один на оба экрана. */
    val selectedMonth = MutableStateFlow(YearMonth.now())

    val themePreferences = ThemePreferences(uiPreferences)
}

/**
 * Последнее известное оформление. База читается асинхронно, поэтому первый кадр
 * рисовался бы темой по умолчанию — и при запуске был бы виден скачок цвета.
 */
class ThemePreferences(private val preferences: SharedPreferences) {
    fun read(): Settings = Settings(
        themeMode = runCatching { ThemeMode.valueOf(preferences.getString(KEY_MODE, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM),
        themePreset = preferences.getString(KEY_PRESET, null) ?: "DEFAULT",
        dynamicColor = preferences.getBoolean(KEY_DYNAMIC, false),
        customThemeColor = preferences.getInt(KEY_CUSTOM_COLOR, DEFAULT_CUSTOM_THEME_COLOR),
        interfaceScale = preferences.getFloat(KEY_SCALE, 1f).toDouble()
    )

    /** Последний ночной режим, объявленный системе, чтобы не пересоздавать экран на каждом запуске. */
    fun appliedNightMode(): Int = preferences.getInt(KEY_NIGHT_MODE, UNSET)

    fun setAppliedNightMode(mode: Int) {
        preferences.edit().putInt(KEY_NIGHT_MODE, mode).apply()
    }

    fun write(settings: Settings) {
        preferences.edit()
            .putString(KEY_MODE, settings.themeMode.name)
            .putString(KEY_PRESET, settings.themePreset)
            .putBoolean(KEY_DYNAMIC, settings.dynamicColor)
            .putInt(KEY_CUSTOM_COLOR, settings.customThemeColor)
            .putFloat(KEY_SCALE, settings.interfaceScale.toFloat())
            .apply()
    }

    companion object {
        const val UNSET = Int.MIN_VALUE
        private const val KEY_NIGHT_MODE = "appliedNightMode"
        private const val KEY_MODE = "themeMode"
        private const val KEY_PRESET = "themePreset"
        private const val KEY_DYNAMIC = "dynamicColor"
        private const val KEY_CUSTOM_COLOR = "customThemeColor"
        private const val KEY_SCALE = "interfaceScale"
    }
}

class WorkOrderApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.ENABLE_WEAR_SYNC) container.wearSyncManager.start()
    }
}
