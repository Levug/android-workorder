package com.workorder.app.widget

import android.app.Application
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.workorder.app.R
import com.workorder.app.data.AppDatabase
import com.workorder.app.data.model.Settings
import com.workorder.app.data.model.ThemeMode
import com.workorder.app.data.model.WorkDay
import com.workorder.app.data.model.WorkDayType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalendarWidgetTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Test fun monthGridHandlesLeapYearsAndSixWeekMonths() {
        for (month in listOf(YearMonth.of(2024, 2), YearMonth.of(2026, 2), YearMonth.of(2026, 8), YearMonth.of(2026, 11))) {
            val grid = calendarWidgetDates(month)
            assertEquals(42, grid.size)
            assertEquals((1..month.lengthOfMonth()).map { month.atDay(it) }, grid.filterNotNull())
            assertEquals(month.atDay(1).dayOfWeek.value - 1, grid.indexOf(month.atDay(1)))
        }
        assertEquals(LocalDate.of(2026, 8, 31), calendarWidgetDates(YearMonth.of(2026, 8))[35])
    }

    @Test fun monthReadIncludesPlansButNotAdjacentMonths() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        try {
            for (date in listOf("2026-10-31", "2026-11-01", "2026-11-30", "2026-12-01")) {
                db.workDayDao().insert(WorkDay(date = date, totalHours = 8.0, isRecorded = date != "2026-11-30"))
            }
            val days = db.workDayDao().getByMonth("2026-11")
            assertEquals(listOf("2026-11-01", "2026-11-30"), days.map { it.date })
            assertFalse(days.last().isRecorded)
            assertNull(db.workDayDao().getByDate("2026-11-10"))
        } finally { db.close() }
    }

    @Test fun malformedWidgetDatesAreIgnored() {
        for (uri in listOf("workorder-widget://day/2026-02-30", "https://day/2026-11-10", "workorder-widget://other/2026-11-10")) {
            val intent = Intent(ACTION_WIDGET_DATE, Uri.parse(uri))
            assertNull(intent.widgetDate())
            assertNull(intent.widgetAction())
        }
    }

    @Test fun calendarRendersAndEachDayKeepsItsOwnClickTarget() {
        val month = YearMonth.of(2026, 11)
        val today = month.atDay(10)
        val days = (2..10).map { day ->
            WorkDay(date = month.atDay(day).toString(), totalHours = if (day == 4) 4.0 else 8.0,
                compOffHours = if (day == 4) 4.0 else 0.0,
                dayType = when (day) { 5 -> WorkDayType.COMP_OFF; 6 -> WorkDayType.UNPAID; 9 -> WorkDayType.COMP_WORK; else -> WorkDayType.AUTO },
                isRecorded = day != 7)
        }
        for (dark in listOf(false, true)) {
            val context = app.createConfigurationContext(Configuration(app.resources.configuration).apply { fontScale = 1.3f })
            val views = renderCalendarWidget(context, month, today, days, Settings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT))
            val root = views.apply(context, FrameLayout(context))
            val density = context.resources.displayMetrics.density
            val width = (250 * density).toInt()
            val height = (320 * density).toInt()
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
            val weeks = root.findViewById<LinearLayout>(R.id.calendar_weeks)
            assertEquals(6, weeks.childCount)
            calendarWidgetDates(month).forEachIndexed { index, date ->
                val row = weeks.getChildAt(index / 7) as LinearLayout
                assertEquals(7, row.childCount)
                val cell = row.getChildAt(index % 7)
                if (date == null) assertEquals(View.INVISIBLE, cell.visibility)
                else {
                    assertEquals(date.dayOfMonth.toString(), cell.findViewById<TextView>(R.id.calendar_number).text.toString())
                    assertEquals(if (date == today) View.VISIBLE else View.GONE, cell.findViewById<View>(R.id.calendar_today_border).visibility)
                    cell.performClick()
                    assertEquals(date, shadowOf(app).nextStartedActivity.widgetDate())
                    val label = cell.findViewById<TextView>(R.id.calendar_hours)
                    if (date.dayOfMonth == 4) assertEquals("4+4", label.text.toString())
                    if (date.dayOfMonth == 7) assertEquals("план", label.text.toString())
                    if (label.text.isNotEmpty()) assertEquals("Day $date label fits", 0, label.layout.getEllipsisCount(0))
                }
            }
            root.findViewById<View>(R.id.calendar_title).performClick()
            assertEquals(ACTION_WIDGET_CALENDAR, shadowOf(app).nextStartedActivity.action)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            val dir = File("build/screenshots/widgets").apply { mkdirs() }
            File(dir, "calendar-${if (dark) "dark" else "light"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
