package com.workorder.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.workorder.app.ui.component.ThemeColorDialog
import com.workorder.app.ui.screen.HoursDialog
import com.workorder.app.ui.screen.ManualHoursDialog
import com.workorder.app.ui.theme.presetColorScheme
import androidx.compose.material3.MaterialTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Диалоги отдельно от активности: см. ScreenshotTourTest. Запуск с -Pscreenshots.
 * Окно стандартное (320 dp): в Robolectric 4.12 поле ввода в диалоге на окне шире бесконечно перерисовывается.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Без WorkOrderApp: синхронизация с часами и база здесь не нужны.
@Config(application = android.app.Application::class)
class DialogScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val outDir = File(System.getProperty("workorder.screenshotDir") ?: "build/screenshots")

    @Before fun onlyOnRequest() {
        assumeTrue(!System.getProperty("workorder.screenshots").isNullOrEmpty())
    }

    @Test fun regular() = dialogs("320")

    @Test fun largeFont() {
        RuntimeEnvironment.setFontScale(1.3f)
        dialogs("320-font130")
    }

    private fun dialogs(prefix: String) {
        outDir.mkdirs()
        var which by androidx.compose.runtime.mutableIntStateOf(0)
        compose.setContent {
            MaterialTheme(colorScheme = presetColorScheme("DEFAULT", 0, dark = false)) {
                when (which) {
                    0 -> ManualHoursDialog(6.0, 4.5, 1.5, { _, _, _ -> }, {})
                    1 -> ThemeColorDialog(0xFF415F91.toInt(), {}, {})
                    2 -> HoursDialog(4.0, 8.0, true, 4.0, 16.0, { _, _ -> }, {})
                }
            }
        }
        shot("$prefix-dialog-manual")
        which = 1
        shot("$prefix-dialog-theme")
        which = 2
        shot("$prefix-dialog-hours")
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        rootViews().filter { it.isShown && it.width > 0 }.lastOrNull()?.let { view ->
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun rootViews(): List<View> {
        val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
        val field = global.javaClass.getDeclaredField("mViews").apply { isAccessible = true }
        return (field.get(global) as List<View>).toList()
    }
}
