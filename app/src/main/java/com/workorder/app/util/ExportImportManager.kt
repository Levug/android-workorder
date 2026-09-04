package com.workorder.app.util

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.workorder.app.data.repository.BackupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class PreparedImport(val kind: TransferKind, val data: BackupSnapshot)

/** SAF URIs for user files; private temporary files for sharing. Never scan public folders. */
class ExportImportManager(
    private val context: Context,
    private val backupRepository: BackupRepository
) {
    private val exportDir get() = File(context.cacheDir, "exports")
    private val safetyDir get() = File(context.filesDir, "import-backups")

    suspend fun prepareExport(kind: TransferKind): File = withContext(Dispatchers.IO) {
        writeExport(BackupCodec.encode(backupRepository.snapshot(), kind),
            if (kind == TransferKind.FULL_DATABASE) "workorder_database" else "workorder_settings")
    }

    suspend fun saveExport(file: File, uri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            file.inputStream().use { input -> input.copyTo(output) }
            output.flush()
        } ?: throw IOException("Не удалось открыть выбранный файл для записи")
    }

    suspend fun readImport(uri: Uri, kind: TransferKind): PreparedImport = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val result = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(result.size().toLong() + count <= MAX_BYTES) { "Файл больше 50 МБ" }
                result.write(buffer, 0, count)
            }
            result.toByteArray()
        } ?: throw IOException("Не удалось прочитать выбранный файл")
        PreparedImport(kind, BackupCodec.decode(bytes.toString(Charsets.UTF_8), kind))
    }

    suspend fun importData(prepared: PreparedImport) = withContext(Dispatchers.IO) {
        backupRepository.import(prepared.data, prepared.kind) { current ->
            val json = BackupCodec.encode(current, TransferKind.FULL_DATABASE)
            check(safetyDir.isDirectory || safetyDir.mkdirs()) { "Не удалось создать папку страховочной копии" }
            val target = AtomicFile(File(safetyDir, uniqueName("before_import")))
            val stream = target.startWrite()
            try {
                stream.write(json.toByteArray(Charsets.UTF_8))
                target.finishWrite(stream)
            } catch (e: Exception) {
                target.failWrite(stream)
                throw e
            }
        }
    }

    fun hasSafetyCopy(): Boolean = latestSafetyCopy() != null

    suspend fun prepareSafetyExport(): File = withContext(Dispatchers.IO) {
        val safety = checkNotNull(latestSafetyCopy()) { "Копия до импорта пока не создавалась" }
        writeExport(safety.readText(Charsets.UTF_8), "workorder_before_import")
    }

    private fun latestSafetyCopy(): File? = safetyDir.listFiles()
        ?.filter { it.isFile && it.extension == "json" }
        ?.maxByOrNull { it.lastModified() }

    private fun writeExport(json: String, prefix: String): File {
        val bytes = json.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "Копия больше 50 МБ" }
        check(exportDir.isDirectory || exportDir.mkdirs()) { "Не удалось подготовить файл" }
        return File(exportDir, uniqueName(prefix)).apply { writeBytes(bytes) }
    }

    private fun uniqueName(prefix: String): String =
        "${prefix}_${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))}_${UUID.randomUUID().toString().take(8)}.json"

    companion object { private const val MAX_BYTES = 50 * 1024 * 1024 }
}
