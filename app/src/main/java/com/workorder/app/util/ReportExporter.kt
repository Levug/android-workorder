package com.workorder.app.util

import android.content.Context
import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.opencsv.CSVWriter
import com.workorder.app.data.dao.OperationTotal
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Locale

class ReportExporter(private val context: Context) {

    fun exportToPdf(
        yearMonth: String,
        totalHours: Double,
        totalProjectHours: Double,
        salary: Double,
        totals: List<OperationTotal>
    ): File? {
        return try {
            val file = File(context.getExternalFilesDir(null), "report_$yearMonth.pdf")

            PdfWriter(file).use { writer ->
                val pdfDocument = PdfDocument(writer)
                val document = Document(pdfDocument)

                // Системный Roboto — единственный гарантированный шрифт с кириллицей
                val font = PdfFontFactory.createFont(
                    "/system/fonts/Roboto-Regular.ttf",
                    PdfEncodings.IDENTITY_H
                )
                document.setFont(font)

                document.add(
                    Paragraph("Отчёт за $yearMonth")
                        .setFontSize(20f)
                        .setBold()
                        .setTextAlignment(TextAlignment.CENTER)
                )
                document.add(Paragraph("\n"))

                document.add(Paragraph("Итоги месяца").setFontSize(16f).setBold())
                document.add(Paragraph("Всего рабочих часов: ${format(totalHours)} ч"))
                document.add(Paragraph("Выработка (нормо-часы): ${format(totalProjectHours)} ч"))
                document.add(Paragraph("Зарплата: ${format(salary)} руб."))
                document.add(Paragraph("\n"))

                if (totals.isNotEmpty()) {
                    document.add(Paragraph("Операции").setFontSize(16f).setBold())

                    val table = Table(UnitValue.createPercentArray(floatArrayOf(46f, 18f, 18f, 18f)))
                    table.setWidth(UnitValue.createPercentValue(100f))
                    table.addHeaderCell("Операция")
                    table.addHeaderCell("Норма, ч")
                    table.addHeaderCell("Кол-во")
                    table.addHeaderCell("Итого, ч")

                    totals.forEach { row ->
                        table.addCell(row.name)
                        table.addCell(format(row.durationHours))
                        table.addCell(row.totalCount.toString())
                        table.addCell(format(row.totalHours))
                    }
                    document.add(table)
                }

                document.close()
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun exportToCsv(
        yearMonth: String,
        totalHours: Double,
        totalProjectHours: Double,
        salary: Double,
        totals: List<OperationTotal>
    ): File? {
        return try {
            val file = File(context.getExternalFilesDir(null), "report_$yearMonth.csv")

            OutputStreamWriter(FileOutputStream(file), StandardCharsets.UTF_8).use { osw ->
                // BOM, чтобы Excel распознал UTF-8; ';' — разделитель для русской локали
                osw.write("\uFEFF")
                CSVWriter(osw, ';', CSVWriter.DEFAULT_QUOTE_CHARACTER,
                    CSVWriter.DEFAULT_ESCAPE_CHARACTER, CSVWriter.DEFAULT_LINE_END).use { writer ->
                    writer.writeNext(arrayOf("Отчёт за $yearMonth"))
                    writer.writeNext(arrayOf(""))
                    writer.writeNext(arrayOf("Всего рабочих часов", format(totalHours)))
                    writer.writeNext(arrayOf("Выработка (нормо-часы)", format(totalProjectHours)))
                    writer.writeNext(arrayOf("Зарплата", format(salary)))
                    writer.writeNext(arrayOf(""))

                    if (totals.isNotEmpty()) {
                        writer.writeNext(arrayOf("Операция", "Норма, ч", "Количество", "Итого, ч"))
                        totals.forEach { row ->
                            writer.writeNext(
                                arrayOf(
                                    row.name,
                                    format(row.durationHours),
                                    row.totalCount.toString(),
                                    format(row.totalHours)
                                )
                            )
                        }
                    }
                }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun format(value: Double): String = String.format(Locale.US, "%.2f", value)
}
