package com.workorder.app.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import com.workorder.app.data.model.Operation
import com.workorder.app.data.model.Settings
import com.workorder.app.data.model.ThemeMode
import com.workorder.app.data.model.WorkSchedule
import com.workorder.app.data.repository.OperationRepository
import com.workorder.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.IOException

data class ExportData(
    val operations: List<Operation>,
    val settings: Settings?
)

data class TransferResult(
    val success: Boolean,
    val message: String
)

/**
 * Экспорт/импорт справочника операций и настроек в JSON.
 * Файл кладётся в общедоступную папку Документы/WorkOrder:
 * на Android 10+ через MediaStore (без разрешений), на 8–9 — напрямую.
 * Если Документы недоступны, используется папка приложения.
 */
class ExportImportManager(
    private val context: Context,
    private val operationRepository: OperationRepository,
    private val settingsRepository: SettingsRepository
) {
    private val gson = Gson()
    private val fileName = "work_order_settings.json"
    private val subDir = "WorkOrder"
    private val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/$subDir/"

    private data class MediaItem(val uri: Uri, val name: String, val modified: Long)

    suspend fun exportSettings(): TransferResult {
        val json = try {
            gson.toJson(
                ExportData(
                    operations = operationRepository.observeAll().first(),
                    settings = settingsRepository.observe().first()
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return TransferResult(false, "Ошибка экспорта данных")
        }

        // 1. Общая папка Документы
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val removedDuplicates = writeToDocumentsMediaStore(json)
                return TransferResult(
                    true,
                    "Сохранено: Документы/$subDir/$fileName" +
                        if (removedDuplicates > 0) " · удалено копий: $removedDuplicates" else ""
                )
            } else {
                writeToDocumentsLegacy(json)
            }
            return TransferResult(true, "Сохранено: Документы/$subDir/$fileName")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Фолбэк: папка приложения
        return try {
            File(context.getExternalFilesDir(null), fileName).writeText(json)
            TransferResult(true, "Документы недоступны, сохранено в папку приложения")
        } catch (e: Exception) {
            e.printStackTrace()
            TransferResult(false, "Ошибка экспорта")
        }
    }

    suspend fun importSettings(): TransferResult {
        val json = readJson()
            ?: return TransferResult(false, "Файл не найден в Документы/$subDir")

        return try {
            val exportData = gson.fromJson(json, ExportData::class.java)

            val importedIds = exportData.operations.map { operation ->
                val existing = operationRepository.getById(operation.id)
                val safeOperation = operation.copy(
                    grade = operation.grade.takeIf { it in 3..6 } ?: existing?.grade ?: 3
                )
                operationRepository.save(
                    if (existing != null) safeOperation else safeOperation.copy(id = 0)
                )
            }
            operationRepository.applyPreferredOrder(importedIds)
            exportData.settings?.let { imported ->
                settingsRepository.replace(
                    Settings(
                        hourlyRate = imported.hourlyRate.coerceAtLeast(0.0),
                        themeMode = imported.themeMode ?: ThemeMode.SYSTEM,
                        themePreset = imported.themePreset ?: "DEFAULT",
                        dynamicColor = imported.dynamicColor,
                        defaultDayHours = imported.defaultDayHours.takeIf { it > 0 } ?: 8.0,
                        contractHourlyRate = imported.contractHourlyRate.coerceAtLeast(0.0),
                        workSchedule = imported.workSchedule ?: WorkSchedule.FIVE_TWO,
                        shiftAnchorDate = imported.shiftAnchorDate
                            ?.takeIf { runCatching { java.time.LocalDate.parse(it) }.isSuccess }
                            ?: "2026-01-01"
                    )
                )
            }
            TransferResult(true, "Операции и настройки импортированы")
        } catch (e: Exception) {
            e.printStackTrace()
            TransferResult(false, "Ошибка импорта: повреждённый файл")
        }
    }

    private fun readJson(): String? {
        // 1. Документы
        try {
            val json = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                findDocumentsMediaItems().firstNotNullOfOrNull { item ->
                    runCatching {
                        context.contentResolver.openInputStream(item.uri)?.use {
                            it.readBytes().decodeToString()
                        }?.takeIf(::isValidExportJson)
                    }.getOrNull()
                }
            } else {
                documentsDirLegacy().listFiles()
                    ?.filter { it.isFile && it.name.startsWith("work_order_settings") && it.extension == "json" }
                    ?.sortedByDescending { it.lastModified() }
                    ?.firstNotNullOfOrNull { file ->
                        runCatching { file.readText().takeIf(::isValidExportJson) }.getOrNull()
                    }
            }
            if (json != null) return json
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Фолбэк: папка приложения (старые экспорты)
        return try {
            val file = File(context.getExternalFilesDir(null), fileName)
            if (file.exists()) file.readText() else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ---------- Android 10+: MediaStore ----------

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun collection(): Uri =
        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun findDocumentsMediaItems(): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        context.contentResolver.query(
            collection(),
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_MODIFIED
            ),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND " +
                "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
            arrayOf(relativePath, "work_order_settings%.json"),
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC, ${MediaStore.MediaColumns._ID} DESC"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                result += MediaItem(
                    uri = ContentUris.withAppendedId(collection(), cursor.getLong(0)),
                    name = cursor.getString(1),
                    modified = cursor.getLong(2)
                )
            }
        }
        return result
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeToDocumentsMediaStore(json: String): Int {
        val resolver = context.contentResolver
        val existing = findDocumentsMediaItems()
        existing.forEach { resolver.delete(it.uri, null, null) }
        val uri = resolver.insert(
            collection(),
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        ) ?: throw IOException("MediaStore: не удалось создать файл")

        resolver.openOutputStream(uri, "w")?.use { stream ->
            stream.write(json.toByteArray())
        } ?: throw IOException("MediaStore: не удалось открыть файл на запись")
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null,
            null
        )
        return existing.size
    }

    private fun isValidExportJson(json: String): Boolean = runCatching {
        val data = gson.fromJson(json, ExportData::class.java)
        data.operations != null
    }.getOrDefault(false)

    // ---------- Android 8–9: прямая запись ----------

    private fun documentsDirLegacy(): File =
        File(
            @Suppress("DEPRECATION")
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            subDir
        )

    private fun writeToDocumentsLegacy(json: String) {
        val dir = documentsDirLegacy()
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Не удалось создать $dir")
        File(dir, fileName).writeText(json)
    }
}
