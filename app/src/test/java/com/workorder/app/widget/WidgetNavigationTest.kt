package com.workorder.app.widget

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.workorder.app.MainActivity
import com.workorder.app.WorkOrderApp
import com.workorder.app.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w320dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun coldAndWarmWidgetLaunchOpenTodayWithoutWritingDay() {
        val app = ApplicationProvider.getApplicationContext<WorkOrderApp>()
        ActivityScenario.launch<MainActivity>(widgetIntent(app, true)).use { scenario ->
            waitFor("Выберите операцию")
            // Пустой каталог тоже доступен; виджет не создаёт пустой рабочий день.
            click("Открыть каталог операций")
            scenario.onActivity {
                InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(it, widgetIntent(it, false))
            }
            waitFor("Записать часы")
            click("Календарь")
            scenario.onActivity {
                InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(it, widgetIntent(it, true))
            }
            waitFor("Выберите операцию")
            runBlocking {
                assertNull(AppDatabase.getDatabase(app).workDayDao().getByDate(LocalDate.now().toString()))
            }
        }
    }

    private fun waitFor(text: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun click(text: String) {
        compose.onAllNodes(hasText(text) and hasClickAction()).onFirst().performClick()
    }

    @After fun closeDatabase() {
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
        (field.get(null) as AppDatabase?)?.close()
        field.set(null, null)
    }
}
