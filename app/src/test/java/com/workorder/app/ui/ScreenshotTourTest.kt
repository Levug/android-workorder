package com.workorder.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.workorder.app.MainActivity
import com.workorder.app.WorkOrderApp
import com.workorder.app.data.AppDatabase
import com.workorder.app.data.model.Operation
import com.workorder.app.data.model.WorkDayType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.time.LocalDate

/**
 * Снимки основных экранов на реальных данных — для проверки вёрстки без телефона.
 * Запуск по одному тесту на процесс (между тестами в одной JVM остаются фоновые подписки на базу):
 * .\gradlew.bat testDebugUnitTest -Pscreenshots --tests "*ScreenshotTourTest.narrowPhoneLargeFont"
 * Снимки: app/build/screenshots. Без -Pscreenshots тест пропускается.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTourTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val outDir = File(System.getProperty("workorder.screenshotDir") ?: "build/screenshots")

    @Before fun onlyOnRequest() {
        assumeTrue(!System.getProperty("workorder.screenshots").isNullOrEmpty())
    }

    @Test @Config(qualifiers = "w393dp-h1500dp-xxhdpi")
    fun regularPhone() = tour("393")

    @Test @Config(qualifiers = "w360dp-h1500dp-xxhdpi")
    fun narrowPhoneLargeFont() {
        RuntimeEnvironment.setFontScale(1.3f)
        tour("360-font130")
    }

    private fun tour(prefix: String) {
        outDir.mkdirs()
        seed()
        ActivityScenario.launch(MainActivity::class.java).use {
            shot(prefix, "01-today")
            clickChip()
            shot(prefix, "02-day-type-sheet")
            // Выбор уже выбранной плитки просто закрывает шторку.
            click("5/2")
            click("Сборка кронштейна крепления двигателя", exact = true)
            shot(prefix, "03-summary-expanded")
            click("Календарь")
            shot(prefix, "04-calendar")
            click("Отчёты")
            waitForText("Заработано")
            shot(prefix, "05-reports")
            click("Из чего складывается")
            shot(prefix, "06-reports-breakdown")
            click("Графики")
            shot(prefix, "07-charts")
            click("Банк β")
            shot(prefix, "08-bank")
            click("Настройки")
            shot(prefix, "09-settings")
            click("Оплата и смена")
            shot(prefix, "10-settings-pay")
            clickDescription("Назад к настройкам")
            click("Оформление")
            shot(prefix, "11-settings-appearance")
            clickDescription("Назад к настройкам")
            click("Эксперименты")
            shot(prefix, "12-settings-experiments")
            click("Сегодня")
            clickDescription("Предыдущий день")
            clickDescription("Предыдущий день")
            waitForText("выработка")
            shot(prefix, "13-empty-day")
        }
    }

    private fun seed() = runBlocking {
        val container = ApplicationProvider.getApplicationContext<WorkOrderApp>().container
        container.settingsRepository.update {
            it.copy(hourlyRate = 423.5, contractHourlyRate = 210.0, compTimeInitialHours = 8.0, daySpecializationEnabled = true)
        }
        val ops = container.operationRepository
        val ids = listOf(
            Operation(name = "Сборка кронштейна крепления двигателя", durationHours = 0.375, grade = 5),
            Operation(name = "Пайка", durationHours = 0.1, grade = 3),
            Operation(name = "Наладка станка ЧПУ после смены инструмента", durationHours = 1.25, grade = 6),
            Operation(name = "Контроль ОТК", durationHours = 0.05, grade = 4)
        ).map { ops.save(it) }
        val work = container.workOrderRepository
        val today = LocalDate.now()
        work.setDayHours(today.toString(), 11.5)
        work.addEntry(today.toString(), ids[0], 17)
        work.addEntry(today.toString(), ids[1], 120)
        work.addEntry(today.toString(), ids[2], 3)
        work.setDayComment(today.toString(), "Задержались до конца партии, наладка после обеда")
        val month = today.withDayOfMonth(1)
        for (day in 0 until today.lengthOfMonth()) {
            val date = month.plusDays(day.toLong())
            if (date == today) continue
            when (day % 7) {
                0 -> work.setDayHours(date.toString(), 8.0)
                1 -> work.setDayHours(date.toString(), 12.0)
                2 -> { work.setDayType(date.toString(), WorkDayType.COMP_WORK); work.setDayHours(date.toString(), 8.0) }
                3 -> work.setDayType(date.toString(), WorkDayType.COMP_OFF)
                4 -> work.setManualHours(date.toString(), 6.0, 4.5)
                5 -> work.setDaySpecialization(date.toString(), "Наладка и сборка узлов")
                else -> Unit
            }
            if (day % 3 == 0) work.addEntry(date.toString(), ids[day % 4], 5 + day)
        }
    }

    private fun click(text: String, optional: Boolean = false, exact: Boolean = false) {
        println("${System.currentTimeMillis() % 100000} click $text")
        compose.waitForIdle()
        val nodes = compose.onAllNodes(hasText(text, substring = !exact) and hasClickAction(), useUnmergedTree = false)
        if (optional && nodes.fetchSemanticsNodes().isEmpty()) return
        nodes.onFirst().performClick()
        compose.waitForIdle()
    }

    /** Room отдаёт данные из фоновых потоков — waitForIdle их не ждёт. */
    private fun waitForText(text: String) {
        runCatching {
            compose.waitUntil(10_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }
        }
    }

    private fun clickDescription(description: String) {
        compose.waitForIdle()
        compose.onAllNodes(hasContentDescription(description) and hasClickAction()).onFirst().performClick()
        compose.waitForIdle()
    }

    private fun clickChip() {
        compose.waitForIdle()
        compose.onAllNodes(hasClickAction() and hasText("По графику", substring = true)).onFirst().performClick()
        compose.waitForIdle()
    }

    /** Все окна (экран, диалог, меню) — отдельными файлами, нижнее окно первым. */
    private fun shot(prefix: String, name: String) {
        println("${System.currentTimeMillis() % 100000} shot $name")
        compose.waitForIdle()
        ShadowLooper.idleMainLooper()
        val views = rootViews().filter { it.isShown && it.width > 0 && it.height > 0 }
        views.forEachIndexed { index, view ->
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val suffix = if (views.size > 1) "-$index" else ""
            File(outDir, "$prefix-$name$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun rootViews(): List<View> {
        val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
        val field = global.javaClass.getDeclaredField("mViews").apply { isAccessible = true }
        return (field.get(global) as List<View>).toList()
    }

    /** База — синглтон: между тестами Robolectric её нужно закрыть и забыть. */
    @After fun closeDatabase() {
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
        (field.get(null) as AppDatabase?)?.close()
        field.set(null, null)
    }
}
