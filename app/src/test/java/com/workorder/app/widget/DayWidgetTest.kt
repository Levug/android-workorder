package com.workorder.app.widget

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.workorder.app.R
import com.workorder.app.data.AppDatabase
import com.workorder.app.data.model.Operation
import com.workorder.app.data.model.OperationEntry
import com.workorder.app.data.model.Settings
import com.workorder.app.data.model.ThemeMode
import com.workorder.app.data.model.WorkDay
import com.workorder.app.data.model.WorkDayType
import com.workorder.app.data.repository.SettingsRepository
import com.workorder.app.data.repository.WorkOrderRepository
import kotlinx.coroutines.flow.first
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

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DayWidgetTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val date = LocalDate.of(2026, 11, 8)

    @Test fun sickLeaveSuspendsAndRestoresRecordedHoursAndOperations() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repository = WorkOrderRepository(db, SettingsRepository(db.settingsDao()))
            val iso = date.toString()
            val month = iso.substring(0, 7)
            val operation = db.operationDao().insert(Operation(name = "Деталь", durationHours = 0.5))
            repository.setDayType(iso, WorkDayType.SICK_LEAVE)
            assertEquals("Больничный", readWidgetData(db, date).status)
            assertFalse(repository.observeDay(iso).first()!!.isRecorded)
            repository.setDayType(iso, WorkDayType.REGULAR)
            repository.setDayHours(iso, 4.0, compOffHours = 4.0)
            val entry = repository.addEntry(iso, operation, 8)
            assertEquals(-4.0, repository.observeCompTimeBalance().first(), 0.0)

            repository.setDayType(iso, WorkDayType.SICK_LEAVE)
            val stored = repository.observeDay(iso).first()!!
            assertEquals(4.0, stored.totalHours, 0.0)
            assertEquals(4.0, stored.compOffHours, 0.0)
            assertEquals("—", readWidgetData(db, date).hours)
            assertEquals("—", readWidgetData(db, date).produced)
            assertEquals("бол.", calendarWidgetHoursLabel(stored, false))
            assertTrue(repository.observeMonthTotals(month).first().isEmpty())
            assertTrue(repository.observeDailyProducedHours(month).first().isEmpty())
            assertEquals(0.0, repository.observeCompTimeBalance().first(), 0.0)

            repository.setDayType(iso, WorkDayType.REGULAR)
            assertEquals(entry, repository.observeDayEntries(iso).first().single().id)
            assertEquals(8, repository.observeMonthTotals(month).first().single().totalCount)
            assertEquals(-4.0, repository.observeCompTimeBalance().first(), 0.0)
        } finally { db.close() }
    }

    @Test fun emptyAndPlannedDaysDoNotBecomeRecorded() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val empty = readWidgetData(db, date)
            assertEquals("—", empty.hours)
            assertEquals("0", empty.produced)
            assertNull(db.workDayDao().getByDate(date.toString()))
            db.workDayDao().insert(WorkDay(date = date.toString(), totalHours = 8.0, isRecorded = false))
            val planned = readWidgetData(db, date)
            assertEquals("—", planned.hours)
            assertEquals("День не заполнен", planned.status)
            assertFalse(db.workDayDao().getByDate(date.toString())!!.isRecorded)
        } finally { db.close() }
    }

    @Test fun partialTimeOffAndCorrectionsUseCurrentDatabaseValues() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dayId = db.workDayDao().insert(WorkDay(date = date.toString(), totalHours = 4.0, compOffHours = 4.0))
            val operation = Operation(name = "Деталь", durationHours = 0.5)
            val operationId = db.operationDao().insert(operation)
            db.operationEntryDao().insert(OperationEntry(workDayId = dayId, operationId = operationId, quantity = 10, createdAt = 1))
            val correction = db.operationEntryDao().insert(OperationEntry(workDayId = dayId, operationId = operationId, quantity = -2, createdAt = 2))
            val data = readWidgetData(db, date)
            assertEquals("4", data.hours)
            assertEquals("4", data.produced)
            assertEquals("100%", data.efficiency)
            assertEquals("+ 4 ч отгула", data.status)
            db.operationDao().update(operation.copy(id = operationId, durationHours = 1.0, archived = true))
            assertEquals("8", readWidgetData(db, date).produced)
            db.operationEntryDao().deleteById(correction)
            assertEquals("10", readWidgetData(db, date).produced)
            assertEquals("—", readWidgetData(db, date.plusDays(1)).hours)
        } finally { db.close() }
    }

    @Test fun specialDayTypesKeepHoursAndStatusDistinct() {
        for ((type, hours, status) in listOf(
            Triple(WorkDayType.COMP_WORK, 8.0, "Выход за отгул"),
            Triple(WorkDayType.COMP_OFF, 8.0, "Отгул"),
            Triple(WorkDayType.UNPAID, 0.0, "Без содержания"),
            Triple(WorkDayType.MANUAL, 6.0, "Ручной режим")
        )) {
            val data = DayWidgetData(date, WorkDay(date = date.toString(), totalHours = hours, dayType = type), 0.0)
            assertEquals(status, data.status)
            assertEquals(hours.toInt().toString(), data.hours)
            if (hours == 0.0) assertEquals("—", data.efficiency)
        }
    }

    @Test fun remoteViewsFitBothSizesAndKeepActionsSeparate() {
        val data = DayWidgetData(date, WorkDay(date = date.toString(), totalHours = 4.0, compOffHours = 4.0), 5.5)
        for (expanded in listOf(false, true)) {
            for (dark in listOf(false, true)) {
                val configuration = Configuration(context.resources.configuration).apply { fontScale = 1.3f }
                val themed = context.createConfigurationContext(configuration)
                val views = renderDayWidget(themed, data, Settings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT), expanded)
                val root = views.apply(themed, FrameLayout(themed))
                val widthDp = if (expanded) 300 else 130
                measure(root, themed, widthDp, 160)
                assertEquals(if (expanded) View.VISIBLE else View.GONE, root.findViewById<View>(R.id.widget_efficiency_group).visibility)
                listOf(R.id.widget_hours, R.id.widget_produced, R.id.widget_add, R.id.widget_status).forEach { id ->
                    val text = root.findViewById<TextView>(id)
                    assertTrue("Text fits: $id", text.layout.getEllipsisCount(0) == 0)
                    assertTrue(text.height > 0)
                }
                root.findViewById<View>(R.id.widget_add).performClick()
                assertEquals(ACTION_WIDGET_ADD, shadowOf(context).nextStartedActivity.action)
                root.findViewById<View>(R.id.widget_content).performClick()
                assertEquals(ACTION_WIDGET_TODAY, shadowOf(context).nextStartedActivity.action)
                val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap))
                val dir = File("build/screenshots/widgets").apply { mkdirs() }
                File(dir, "${if (expanded) "wide" else "compact"}-${if (dark) "dark" else "light"}.png")
                    .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }

    private fun measure(view: View, context: Context, width: Int, height: Int) {
        val density = context.resources.displayMetrics.density
        view.measure(View.MeasureSpec.makeMeasureSpec((width * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((height * density).toInt(), View.MeasureSpec.EXACTLY))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }
}
