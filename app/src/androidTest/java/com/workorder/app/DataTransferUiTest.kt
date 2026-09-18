package com.workorder.app

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.SavedStateHandle
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.workorder.app.data.AppDatabase
import com.workorder.app.data.model.*
import com.workorder.app.data.repository.*
import com.workorder.app.ui.component.CalendarGrid
import com.workorder.app.ui.component.MonthColumnChart
import com.workorder.app.ui.screen.SettingsScreen
import com.workorder.app.ui.screen.WorkDayScreen
import com.workorder.app.ui.screen.CalendarScreen
import com.workorder.app.ui.viewmodel.CalendarViewModel
import com.workorder.app.ui.theme.themePresets
import com.workorder.app.ui.viewmodel.WorkDayViewModel
import com.workorder.app.ui.theme.WorkOrderTheme
import com.workorder.app.ui.viewmodel.SettingsViewModel
import com.workorder.app.util.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class DataTransferUiTest {
    @get:Rule val compose = createAndroidComposeRule<QaTestActivity>()
    private lateinit var db: AppDatabase
    private lateinit var vm: SettingsViewModel
    private lateinit var context: Context
    private val store = ViewModelStore()
    private val monitors = mutableListOf<Instrumentation.ActivityMonitor>()
    private val fixture = BackupSnapshot(listOf(Operation(1, "Тестовая операция", 0.5, 5)), Settings(),
        listOf(WorkDay(1, "2026-09-01", 12.0, "Тест")), listOf(OperationEntry(1, 1, 1, 17, 12345, "qa-event")))

    @Before fun setUp(): Unit = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        check(context.packageName.endsWith(".qa"))
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        BackupRepository(db).import(fixture, TransferKind.FULL_DATABASE) {}
        vm = SettingsViewModel(SettingsRepository(db.settingsDao()), ExportImportManager(context, BackupRepository(db)))
        store.put("settings", vm)
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent { WorkOrderTheme(themeMode = ThemeMode.LIGHT) { SettingsScreen(vm, {}) } }
        }
    }

    @After fun tearDown() {
        monitors.forEach { InstrumentationRegistry.getInstrumentation().removeMonitor(it) }
        compose.activityRule.scenario.onActivity { it.setContent {} }
        store.clear()
        db.close()
    }

    @Test fun exportAndImportUseSystemDocumentContractsAndConfirmation() {
        val saved = File(context.cacheDir, "ui-roundtrip.json")
        var savedByPicker = false
        var openedByPicker = false
        intercept { intent ->
            when (intent.action) {
                Intent.ACTION_CREATE_DOCUMENT -> {
                    savedByPicker = true
                    assertEquals("application/json", intent.type)
                    Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(saved)))
                }
                Intent.ACTION_OPEN_DOCUMENT -> {
                    openedByPicker = true
                    Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(saved)))
                }
                else -> null
            }
        }
        compose.onNodeWithText("Экспорт базы").performScrollTo().performClick()
        compose.waitUntil(10_000) { vm.exportFile.value != null }
        compose.onNodeWithText("Отправить в приложение").assertIsDisplayed()
        compose.onNodeWithText("Сохранить на устройстве").performClick()
        compose.waitUntil(10_000) { !vm.busy.value && vm.exportFile.value == null }
        assertTrue(savedByPicker)
        assertEquals(fixture, BackupCodec.decode(saved.readText(), TransferKind.FULL_DATABASE))
        // Dismiss the transient saved-message before clicking the next button underneath it.
        compose.runOnIdle { vm.consumeMessage() }
        compose.waitForIdle()
        compose.onNodeWithText("Импорт базы").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue("Document picker was not launched", openedByPicker)
        compose.waitUntil(10_000) { vm.pendingImport.value != null }
        compose.onNodeWithText("Заменить всю базу?").assertIsDisplayed()
        assertTrue(openedByPicker)
        compose.onNodeWithText("Заменить базу").performClick()
        compose.waitUntil(10_000) { !vm.busy.value && vm.pendingImport.value == null }
        assertEquals(fixture, runBlocking { BackupRepository(db).snapshot() })
        assertTrue(vm.hasSafetyCopy.value)
        assertTrue(saved.delete())
    }

    @Test fun configurationPickerCancellationDoesNotModifyDataAndShareGrantsReadOnlyUri() {
        var shareIntent: Intent? = null
        var openCalled = false
        intercept { intent ->
            when (intent.action) {
                Intent.ACTION_OPEN_DOCUMENT -> {
                    openCalled = true
                    Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
                }
                Intent.ACTION_CHOOSER -> {
                    @Suppress("DEPRECATION")
                    shareIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
                }
                else -> null
            }
        }
        compose.onNodeWithText("Импорт конфига").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(openCalled)
        assertNull(vm.pendingImport.value)
        assertEquals(fixture, runBlocking { BackupRepository(db).snapshot() })
        compose.onNodeWithText("Экспорт конфига").performClick()
        compose.waitUntil(10_000) { vm.exportFile.value != null }
        compose.onNodeWithText("Отправить в приложение").performClick()
        compose.waitUntil(10_000) { shareIntent != null }
        val shared = shareIntent!!
        assertEquals(Intent.ACTION_SEND, shared.action)
        assertTrue(shared.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, shared.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        @Suppress("DEPRECATION") val uri = shared.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("content", uri.scheme)
        val decoded = context.contentResolver.openInputStream(uri)!!.bufferedReader().use {
            BackupCodec.decode(it.readText(), TransferKind.CONFIGURATION)
        }
        assertEquals(fixture.operations, decoded.operations)
        assertTrue(decoded.entries.isEmpty())
    }

    @Test fun calendarAndChartScreenshots() {
        val month = YearMonth.of(2026, 9)
        val days = (1..8).associate { n ->
            month.atDay(n) to WorkDay(n.toLong(), month.atDay(n).toString(), n * 2.0)
        }
        listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEach { mode ->
            compose.activityRule.scenario.onActivity { activity ->
                activity.setContent {
                    WorkOrderTheme(themeMode = mode, themePresetName = "AMBER") {
                        Surface {
                            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("Сентябрь 2026")
                                CalendarGrid(month, days, Settings(themePreset = "AMBER", workSchedule = WorkSchedule.TWO_TWO, shiftAnchorDate = "2026-09-01"), {})
                                Text("Нормо-часы по дням")
                                MonthColumnChart(listOf(1 to 8.5, 2 to 17.0, 3 to 0.0, 4 to -1.0, 7 to 12.0), 30, unit = "нч")
                            }
                        }
                    }
                }
            }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("2026-09-01: 2 фактических часов").assertExists()
            capture("calendar-${mode.name.lowercase()}.png")
        }
    }

    @Test fun dataSectionAndExportDialogScreenshots() {
        compose.onNodeWithText("Импорт конфига").performScrollTo()
        capture("data-section.png")
        compose.onNodeWithText("Экспорт базы").performScrollTo().performClick()
        compose.waitUntil(10_000) { vm.exportFile.value != null }
        compose.onNodeWithText("Куда экспортировать?").assertIsDisplayed()
        capture("export-dialog.png", dialog = true)
    }

    @Test fun preferencesSaveTogetherAndDraftSurvivesAppearanceChange() {
        compose.waitUntil { vm.form.value != null }
        compose.onNodeWithText("Реальная ставка в час, ₽").performTextReplacement("450,5")
        compose.onNodeWithText("Часов в смене по умолчанию").performTextReplacement("9,5")
        compose.onNodeWithText("Сохранить настройки").assertIsDisplayed()
        assertEquals(0.0, runBlocking { db.settingsDao().get()!!.hourlyRate }, 0.0)
        compose.runOnIdle { vm.setThemeMode(ThemeMode.DARK) }
        compose.waitUntil { vm.form.value?.stored?.themeMode == ThemeMode.DARK }
        compose.onNodeWithText("Реальная ставка в час, ₽").assertTextContains("450,5")
        capture("settings-save-bar.png")
        compose.onNodeWithText("Сохранить настройки").performClick()
        compose.waitUntil { !vm.savingForm.value && vm.form.value?.dirty == false }
        val saved = runBlocking { db.settingsDao().get()!! }
        assertEquals(450.5, saved.hourlyRate, 0.0)
        assertEquals(9.5, saved.defaultDayHours, 0.0)
        assertEquals(ThemeMode.DARK, saved.themeMode)
    }

    @Test fun feedbackOpensPreparedGitHubMessageWithoutSending() {
        var opened: Intent? = null
        intercept { intent ->
            if (intent.action == Intent.ACTION_VIEW) {
                opened = intent
                Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            } else null
        }
        compose.onNodeWithText("Написать разработчику").performScrollTo().performClick()
        compose.onNodeWithText("Открыть GitHub").assertIsNotEnabled()
        compose.onNodeWithText("Тема обращения").performTextInput("Тест & проверка")
        compose.onNodeWithText("Описание", substring = false).performTextInput("Русский текст\nВторая строка")
        compose.onNodeWithText("Открыть GitHub").performClick()
        compose.waitUntil { opened != null }
        val uri = opened!!.data!!
        assertEquals("github.com", uri.host)
        assertEquals("/Levug/android-workorder/issues/new", uri.path)
        assertEquals("[Ошибка] Тест & проверка", uri.getQueryParameter("title"))
        assertTrue(uri.getQueryParameter("body")!!.contains("Русский текст\nВторая строка"))
        assertFalse(uri.getQueryParameter("body")!!.contains("Android"))
        capture("feedback.png", dialog = true)
    }

    @Test fun scaleAndSpecializationRemainUsableAt130Percent() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                val settings by vm.settings.collectAsStateWithLifecycle()
                WorkOrderTheme(themeMode = settings.themeMode, interfaceScale = settings.interfaceScale) { SettingsScreen(vm, {}) }
            }
        }
        compose.runOnIdle { vm.setInterfaceScale(1.3); vm.setDaySpecializationEnabled(true) }
        compose.waitUntil { vm.form.value?.stored?.interfaceScale == 1.3 && vm.form.value?.stored?.daySpecializationEnabled == true }
        compose.onNodeWithText("Сбросить до 100%").performScrollTo().assertIsDisplayed()
        capture("settings-scale-130.png")
        compose.onNodeWithText("Сбросить до 100%").performClick()
        compose.waitUntil { vm.form.value?.stored?.interfaceScale == 1.0 }
        val date = "2026-09-01"
        val dayVm = WorkDayViewModel(WorkOrderRepository(db, SettingsRepository(db.settingsDao())),
            OperationRepository(db.operationDao()), SettingsRepository(db.settingsDao()), SavedStateHandle(mapOf("date" to date)))
        store.put("workDay", dayVm)
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent { WorkOrderTheme(interfaceScale = 1.3) { WorkDayScreen(dayVm) } }
        }
        compose.waitUntil { !dayVm.uiState.value.isLoading }
        compose.onNodeWithText("Добавить специализацию").performScrollTo().performClick()
        compose.onNodeWithText("Например: сборка, наладка").performTextInput("Наладка сложного оборудования и проверка")
        compose.onNodeWithText("Сохранить", substring = false).performClick()
        compose.waitUntil { dayVm.uiState.value.specialization.isNotEmpty() }
        assertEquals(17, dayVm.uiState.value.totalUnits)
        capture("day-scale-130.png")
        val days = runBlocking { db.workDayDao().observeByMonth("2026-09").first() }.associateBy { LocalDate.parse(it.date) }
        listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEach { mode ->
            compose.activityRule.scenario.onActivity { activity ->
                activity.setContent {
                    WorkOrderTheme(themeMode = mode, interfaceScale = 1.3) {
                        Surface {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
                                CalendarGrid(YearMonth.of(2026, 9), days, Settings(daySpecializationEnabled = true), {}, showSpecialization = true)
                            }
                        }
                    }
                }
            }
            compose.onNodeWithContentDescription("Наладка сложного оборудования", substring = true).assertExists()
            capture("specialization-${mode.name.lowercase()}-130.png")
        }
    }

    @Test fun calendarFlipsFacesWithoutChangingAttendanceAndCanDisableExperiment() {
        val preferences = SettingsRepository(db.settingsDao())
        val work = WorkOrderRepository(db, preferences)
        val month = YearMonth.now()
        val first = month.atDay(1).toString()
        val second = month.atDay(2).toString()
        runBlocking {
            preferences.update { it.copy(daySpecializationEnabled = true) }
            work.setDayHours(first, 12.0)
            work.setDayType(first, WorkDayType.REGULAR)
            work.setDaySpecialization(first, "Сборка")
            work.setDaySpecialization(second, "Наладка оборудования")
        }
        val before = runBlocking { BackupRepository(db).snapshot() }
        val calendar = CalendarViewModel(work, preferences)
        store.put("calendar", calendar)
        var openedDay: String? = null
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent { WorkOrderTheme { CalendarScreen(calendar) { openedDay = it } } }
        }
        compose.onNodeWithText("Показать специализации").assertIsDisplayed()
        compose.onNodeWithContentDescription("$first: 12 фактических часов").assertExists()
        compose.onNodeWithTag("calendar-day-$second").assertContentDescriptionContains("нет наряда", substring = true)
        assertEquals(1, calendar.uiState.value.workedDays)
        assertEquals(12.0, calendar.uiState.value.workedHours, 0.0)
        val bounds = compose.onNodeWithTag("calendar-day-$first").getUnclippedBoundsInRoot()
        capture("calendar-flip-front.png")
        compose.onNodeWithText("Показать специализации").performClick()
        compose.onNodeWithContentDescription("$first: Сборка").assertExists()
        compose.onNodeWithContentDescription("$first: 12 фактических часов").assertDoesNotExist()
        assertEquals(bounds, compose.onNodeWithTag("calendar-day-$first").getUnclippedBoundsInRoot())
        capture("calendar-flip-back.png")
        compose.onNodeWithTag("calendar-day-$second").performClick()
        assertEquals(second, openedDay)
        compose.onNodeWithText("Показать часы").performClick()
        compose.onNodeWithContentDescription("$first: 12 фактических часов").assertExists()
        compose.onNodeWithText("Показать специализации").performClick()
        runBlocking { preferences.update { it.copy(daySpecializationEnabled = false) } }
        compose.waitUntil { !calendar.uiState.value.settings.daySpecializationEnabled }
        compose.onNodeWithText("Показать часы").assertDoesNotExist()
        compose.onNodeWithContentDescription("$first: 12 фактических часов").assertExists()
        assertEquals(before.workDays, runBlocking { BackupRepository(db).snapshot().workDays })
    }

    @Test fun planningSpecializationThroughDayEditorDoesNotAddHours() {
        val preferences = SettingsRepository(db.settingsDao())
        runBlocking { preferences.update { it.copy(daySpecializationEnabled = true) } }
        val date = "2099-01-03"
        val dayVm = WorkDayViewModel(WorkOrderRepository(db, preferences),
            OperationRepository(db.operationDao()), preferences, SavedStateHandle(mapOf("date" to date)))
        store.put("planning", dayVm)
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent { WorkOrderTheme { WorkDayScreen(dayVm) } }
        }
        compose.waitUntil { !dayVm.uiState.value.isLoading }
        compose.onNodeWithText("Добавить специализацию").performScrollTo().performClick()
        compose.onNodeWithText("Например: сборка, наладка").performTextInput("Подготовка оснастки")
        compose.onNodeWithText("Сохранить", substring = false).performClick()
        compose.waitUntil { dayVm.uiState.value.specialization == "Подготовка оснастки" }
        assertFalse(dayVm.uiState.value.isRecorded)
        assertEquals(0.0, dayVm.uiState.value.dayHours, 0.0)
        assertEquals(0, dayVm.uiState.value.totalUnits)
        compose.onNodeWithText("План · смена ещё не отмечена").assertExists()
        compose.onNodeWithText("указать часы ✎").performScrollTo().assertIsDisplayed()
        capture("specialization-plan-editor.png")
    }

    @Test fun allThemePalettesHaveLightAndDarkCalendarScreenshots() {
        val month = YearMonth.of(2026, 9)
        val days = (1..14).associate { n -> month.atDay(n) to
            WorkDay(n.toLong(), month.atDay(n).toString(), ((n - 1) % 7) * 2.5, specialization = "Сборка") }
        themePresets.forEach { preset ->
            listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEach { mode ->
                compose.activityRule.scenario.onActivity { activity ->
                    activity.setContent {
                        WorkOrderTheme(themeMode = mode, themePresetName = preset.name) {
                            Surface {
                                Column(Modifier.fillMaxSize().padding(16.dp)) {
                                    Text("${preset.displayName} · Сентябрь 2026")
                                    CalendarGrid(month, days, Settings(themePreset = preset.name), {})
                                }
                            }
                        }
                    }
                }
                capture("palette-${preset.name.lowercase()}-${mode.name.lowercase()}.png")
            }
        }
    }

    private fun capture(name: String, dialog: Boolean = false) {
        val image = (if (dialog) compose.onNode(isDialog()) else compose.onRoot()).captureToImage()
        val directory = context.getExternalFilesDir("qa-screenshots")!!
        File(directory, name).outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun intercept(action: (Intent) -> Instrumentation.ActivityResult?) {
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? = action(intent)
        }
        monitors += monitor
        InstrumentationRegistry.getInstrumentation().addMonitor(monitor)
    }
}
