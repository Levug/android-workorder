package com.workorder.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.view.View
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.workorder.app.MainActivity
import com.workorder.app.R
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.time.LocalDate

/**
 * Материалы для витрины RuStore: иконка 512×512 и скриншоты 1080×1920 на демонстрационных данных.
 * Запуск по одному тесту: .\gradlew.bat testDebugUnitTest -Pscreenshots --tests "*StoreAssetsTest.phoneScreens"
 * Файлы: app/build/store.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreAssetsTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val outDir = File(System.getProperty("workorder.storeDir") ?: "build/store")

    @Before fun onlyOnRequest() {
        assumeTrue(!System.getProperty("workorder.screenshots").isNullOrEmpty())
        outDir.mkdirs()
    }

    /** Значок для магазина: квадрат без прозрачности, магазин сам скругляет углы. */
    @Test fun icon() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val icon = ContextCompat.getDrawable(context, R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        listOfNotNull(icon.background, icon.foreground).forEach { layer ->
            layer.setBounds(0, 0, 512, 512)
            layer.draw(canvas)
        }
        save(bitmap, "icon-512.png")
    }

    @Test @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun phoneScreens() {
        seed()
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("выработка")
            shot("1-today.png")
            click("Рабочий день")
            shot("2-day-type.png")
            // Выбор уже выбранной плитки закрывает шторку.
            click("Рабочий", exact = true)
            click("Календарь")
            shot("3-calendar.png")
            click("Отчёты")
            waitForText("Заработано")
            shot("4-reports.png")
            click("Настройки")
            shot("5-settings.png")
        }
    }

    private fun seed() = runBlocking {
        val container = ApplicationProvider.getApplicationContext<WorkOrderApp>().container
        container.settingsRepository.update { it.copy(hourlyRate = 450.0, contractHourlyRate = 220.0, compTimeInitialHours = 24.0) }
        val ops = container.operationRepository
        val ids = listOf(
            Operation(name = "Сборка узла", durationHours = 0.4, grade = 4),
            Operation(name = "Фрезеровка корпуса", durationHours = 0.75, grade = 5),
            Operation(name = "Сверловка отверстий", durationHours = 0.1, grade = 3),
            Operation(name = "Контроль качества", durationHours = 0.05, grade = 4)
        ).map { ops.save(it) }
        val work = container.workOrderRepository
        val today = LocalDate.now()
        // Снимки могут сниматься в выходной: сегодняшний день — явно рабочий.
        work.setDayType(today.toString(), WorkDayType.REGULAR)
        work.setDayHours(today.toString(), 8.0)
        work.addEntry(today.toString(), ids[0], 12)
        work.addEntry(today.toString(), ids[1], 4)
        work.addEntry(today.toString(), ids[2], 25)
        val month = today.withDayOfMonth(1)
        for (day in 0 until today.dayOfMonth - 1) {
            val date = month.plusDays(day.toLong())
            val weekend = date.dayOfWeek.value >= 6
            when {
                weekend && day % 9 == 5 -> { work.setDayType(date.toString(), WorkDayType.COMP_WORK); work.setDayHours(date.toString(), 6.0) }
                weekend -> Unit
                day % 7 == 2 -> work.setDayHours(date.toString(), 10.0)
                day % 11 == 4 -> work.setDayType(date.toString(), WorkDayType.COMP_OFF)
                else -> work.setDayHours(date.toString(), 8.0)
            }
            if (!weekend) work.addEntry(date.toString(), ids[day % 3], 6 + day % 9)
        }
    }

    private fun click(text: String, exact: Boolean = false) {
        compose.waitForIdle()
        compose.onAllNodes(hasText(text, substring = !exact) and hasClickAction()).onFirst().performClick()
        compose.waitForIdle()
    }

    private fun waitForText(text: String) {
        runCatching {
            compose.waitUntil(10_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }
        }
    }

    /** Все окна (экран и шторка) — на одном снимке, как их видит пользователь. */
    private fun shot(name: String) {
        compose.waitForIdle()
        ShadowLooper.idleMainLooper()
        val views = rootViews().filter { it.isShown && it.width > 0 && it.height > 0 }
        val base = views.first()
        val bitmap = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        views.forEach { view ->
            val location = IntArray(2).also(view::getLocationOnScreen)
            canvas.save()
            canvas.translate(location[0].toFloat(), location[1].toFloat())
            view.draw(canvas)
            canvas.restore()
        }
        save(bitmap, name)
    }

    private fun save(bitmap: Bitmap, name: String) {
        File(outDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun rootViews(): List<View> {
        val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
        val field = global.javaClass.getDeclaredField("mViews").apply { isAccessible = true }
        return (field.get(global) as List<View>).toList()
    }

    @After fun closeDatabase() {
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
        (field.get(null) as AppDatabase?)?.close()
        field.set(null, null)
    }
}
