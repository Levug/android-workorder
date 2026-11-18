package com.workorder.app.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.room.InvalidationTracker
import androidx.room.withTransaction
import com.workorder.app.MainActivity
import com.workorder.app.R
import com.workorder.app.WorkOrderApp
import com.workorder.app.data.AppDatabase
import com.workorder.app.data.model.Settings
import com.workorder.app.data.model.WorkDay
import com.workorder.app.data.model.WorkDayType
import com.workorder.app.ui.theme.colorSchemeFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Отдельные имена в списке виджетов, общие данные и адаптивная разметка. */
class CompactDayWidgetProvider : DayWidgetProvider()

open class DayWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action !in setOf(
                AppWidgetManager.ACTION_APPWIDGET_UPDATE,
                AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED,
                AppWidgetManager.ACTION_APPWIDGET_ENABLED,
                AppWidgetManager.ACTION_APPWIDGET_RESTORED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED
            )) return
        val pending = goAsync()
        (context.applicationContext as WorkOrderApp).container.dayWidgets.refreshAsync { pending.finish() }
    }
}

internal data class DayWidgetData(val date: LocalDate, val day: WorkDay?, val producedHours: Double) {
    val sickLeave get() = day?.dayType == WorkDayType.SICK_LEAVE
    private val recorded get() = day?.isRecorded == true && !sickLeave
    private fun number(value: Double) = DecimalFormat("0.##", DecimalFormatSymbols(Locale("ru"))).format(value)
    val hours: String get() = if (recorded) number(day!!.totalHours) else "—"
    val produced: String get() = if (sickLeave) "—" else number(producedHours)
    // Та же база для выработки, что у карточки дня: отгул на часть дня не увеличивает отработанные часы.
    val efficiency: String get() = if (recorded && day!!.totalHours > 0)
        "${(producedHours / day.totalHours * 100).roundToInt()}%" else "—"
    val status: String get() {
        if (sickLeave) return "Больничный"
        if (!recorded) return "День не заполнен"
        if (day!!.compOffHours > 0) return "+ ${number(day.compOffHours)} ч отгула"
        return when (day.dayType) {
            WorkDayType.AUTO -> "По графику"
            WorkDayType.REGULAR -> "Рабочий день"
            WorkDayType.HOLIDAY -> "Праздник ×2"
            WorkDayType.COMP_WORK -> "Выход за отгул"
            WorkDayType.COMP_OFF -> "Отгул"
            WorkDayType.MANUAL -> "Ручной режим"
            WorkDayType.UNPAID -> "Без содержания"
            WorkDayType.SICK_LEAVE -> "Больничный"
        }
    }
}

/** Чтение виджета не создаёт наряд и не меняет часы. Все запросы снимка — в одной транзакции. */
internal suspend fun readWidgetData(db: AppDatabase, date: LocalDate): DayWidgetData = db.withTransaction {
    val day = db.workDayDao().getByDate(date.toString())
    val produced = if (day?.isRecorded == true && day.dayType != WorkDayType.SICK_LEAVE)
        db.operationEntryDao().getProducedHours(day.id) else 0.0
    DayWidgetData(date, day, produced)
}

class DayWidgetController(private val context: Context, private val db: AppDatabase) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val updateLock = Mutex()
    private val observer = object : InvalidationTracker.Observer("work_days", "operation_entries", "operations", "settings") {
        override fun onInvalidated(tables: Set<String>) { requests.trySend(Unit) }
    }
    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { requests.trySend(Unit) }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag") // Только защищённые системные события.
    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(clockReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else context.registerReceiver(clockReceiver, filter)
        scope.launch {
            db.invalidationTracker.addObserver(observer)
            for (request in requests) {
                delay(200) // Объединяем уведомления одной записи/импорта.
                safelyRefresh()
            }
        }
        requests.trySend(Unit)
    }

    fun refreshAsync(onFinished: () -> Unit) {
        scope.launch { try { safelyRefresh() } finally { onFinished() } }
    }

    private suspend fun safelyRefresh() {
        try {
            withTimeout(8_000) { refresh() }
        } catch (error: Exception) {
            Log.w("DayWidget", "Не удалось обновить виджет", error)
        }
    }

    private suspend fun refresh() = updateLock.withLock {
        val manager = AppWidgetManager.getInstance(context)
        val ids = listOf(DayWidgetProvider::class.java, CompactDayWidgetProvider::class.java)
            .flatMap { manager.getAppWidgetIds(ComponentName(context, it)).toList() }
        val calendarIds = manager.getAppWidgetIds(ComponentName(context, CalendarWidgetProvider::class.java))
        if (ids.isEmpty() && calendarIds.isEmpty()) return@withLock
        val today = LocalDate.now()
        val settings = db.settingsDao().get() ?: Settings()
        if (calendarIds.isNotEmpty()) {
            val month = YearMonth.from(today)
            val days = db.workDayDao().getByMonth(month.toString())
            val calendar = renderCalendarWidget(context, month, today, days, settings)
            calendarIds.forEach { manager.updateAppWidget(it, calendar) }
        }
        if (ids.isEmpty()) return@withLock
        val data = readWidgetData(db, today)
        ids.forEach { id ->
            val options = manager.getAppWidgetOptions(id)
            val fallbackWidth = if (manager.getAppWidgetInfo(id)?.provider?.className ==
                CompactDayWidgetProvider::class.java.name) 130 else 250
            fun width(key: String) = options.getInt(key).takeIf { it > 0 } ?: fallbackWidth
            // RemoteViews выбирает нужную ширину и при повороте домашнего экрана.
            val portrait = renderDayWidget(context, data, settings,
                width(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) >= 250)
            val landscape = renderDayWidget(context, data, settings,
                width(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) >= 250)
            manager.updateAppWidget(id, RemoteViews(landscape, portrait))
        }
    }
}

const val ACTION_WIDGET_TODAY = "com.workorder.app.widget.TODAY"
const val ACTION_WIDGET_ADD = "com.workorder.app.widget.ADD"

internal fun widgetIntent(context: Context, add: Boolean) = Intent(context, MainActivity::class.java).apply {
    action = if (add) ACTION_WIDGET_ADD else ACTION_WIDGET_TODAY
    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
}

internal fun renderDayWidget(context: Context, data: DayWidgetData, settings: Settings, expanded: Boolean): RemoteViews {
    val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val colors = colorSchemeFor(context, settings, dark)
    return RemoteViews(context.packageName, R.layout.widget_day).apply {
        setTextViewText(R.id.widget_date, data.date.format(DateTimeFormatter.ofPattern("d MMM", Locale("ru"))))
        setTextViewText(R.id.widget_hours, data.hours)
        setTextViewText(R.id.widget_produced, data.produced)
        setTextViewText(R.id.widget_efficiency, data.efficiency)
        setTextViewText(R.id.widget_status, data.status)
        setViewVisibility(R.id.widget_add_container, if (data.sickLeave) View.GONE else View.VISIBLE)
        setViewVisibility(R.id.widget_efficiency_group, if (expanded) View.VISIBLE else View.GONE)
        setInt(R.id.widget_background, "setColorFilter", colors.surfaceContainer.toArgb())
        setInt(R.id.widget_button_background, "setColorFilter", colors.primaryContainer.toArgb())
        listOf(R.id.widget_hours, R.id.widget_produced, R.id.widget_efficiency).forEach {
            setTextColor(it, colors.onSurface.toArgb())
        }
        listOf(R.id.widget_date, R.id.widget_hours_label, R.id.widget_produced_label,
            R.id.widget_efficiency_label, R.id.widget_status).forEach {
            setTextColor(it, colors.onSurfaceVariant.toArgb())
        }
        setTextColor(R.id.widget_add, colors.onPrimaryContainer.toArgb())
        setOnClickPendingIntent(R.id.widget_content, PendingIntent.getActivity(context, 0,
            widgetIntent(context, false), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        setOnClickPendingIntent(R.id.widget_add, PendingIntent.getActivity(context, 1,
            widgetIntent(context, true), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }
}
