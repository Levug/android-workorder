package com.workorder.app.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ruLocale = Locale("ru")

private val numberFormat = DecimalFormat("#,##0.##", DecimalFormatSymbols(ruLocale))

private val timeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", ruLocale)

private val fullDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM, EEEE", ruLocale)

private val shortDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM", ruLocale)

private val monthFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("LLLL yyyy", ruLocale)

/** 2.5 -> "2,5"; 8.0 -> "8". */
fun Double.formatNumber(): String = numberFormat.format(this)

fun Double.formatHours(): String = "${formatNumber()} ч"

fun formatTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(timeFormatter)

/** "9 июля, среда". */
fun LocalDate.formatFull(): String = format(fullDateFormatter)

/** "9 июля". */
fun LocalDate.formatShort(): String = format(shortDateFormatter)

/** "Июль 2026". */
fun YearMonth.formatDisplay(): String =
    format(monthFormatter).replaceFirstChar { it.titlecase(ruLocale) }

/** "+10" / "-3". */
fun Int.formatSigned(): String = if (this > 0) "+$this" else toString()

/** Отправить файл (отчёт) через системный диалог «Поделиться». */
fun shareFile(context: Context, file: File, mimeType: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Отправить отчёт"))
}
