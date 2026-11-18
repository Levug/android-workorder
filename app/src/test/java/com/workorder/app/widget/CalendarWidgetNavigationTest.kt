package com.workorder.app.widget

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ApplicationProvider
import com.workorder.app.MainActivity
import com.workorder.app.WorkOrderApp
import com.workorder.app.data.AppDatabase
import com.workorder.app.util.formatDayTitle
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalendarWidgetNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun coldAndWarmLaunchSelectExactDateThenCurrentCalendarMonth() {
        val app = ApplicationProvider.getApplicationContext<WorkOrderApp>()
        val first = LocalDate.now().minusMonths(1).withDayOfMonth(2)
        val second = first.plusDays(1)
        // ActivityScenario сопоставляет события по исходному Intent.data; onNewIntent меняет дату.
        val controller = Robolectric.buildActivity(MainActivity::class.java, calendarWidgetIntent(app, first)).setup()
        try {
            waitFor(first.formatDayTitle())
            compose.runOnUiThread {
                controller.newIntent(calendarWidgetIntent(app, second))
            }
            waitFor(second.formatDayTitle())
            app.container.selectedMonth.value = YearMonth.from(first)
            compose.runOnUiThread {
                controller.newIntent(calendarWidgetIntent(app))
            }
            compose.waitForIdle()
            assertEquals(YearMonth.now(), app.container.selectedMonth.value)
            runBlocking {
                assertNull(AppDatabase.getDatabase(app).workDayDao().getByDate(first.toString()))
                assertNull(AppDatabase.getDatabase(app).workDayDao().getByDate(second.toString()))
            }
        } finally { controller.pause().stop().destroy() }
    }

    private fun waitFor(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun closeDatabase() {
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
        (field.get(null) as AppDatabase?)?.close()
        field.set(null, null)
    }
}
