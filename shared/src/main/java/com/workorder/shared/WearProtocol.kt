package com.workorder.shared

import com.google.gson.Gson

data class WearOperationDto(
    val id: Long,
    val name: String,
    val durationHours: Double,
    val grade: Int,
    val sortOrder: Int
)

data class WearCatalogDto(
    val schemaVersion: Int = WearProtocol.SCHEMA_VERSION,
    val updatedAt: Long,
    val operations: List<WearOperationDto>,
    /** Быстрые партии из настроек телефона (с 3.8). Null — телефон старше, на часах кнопки по умолчанию. */
    val quickAmounts: List<Int>? = null
)

/**
 * Состояние часов для экрана «Часы» на телефоне (с часов 3.5). Часы 3.4 его не публикуют,
 * но записи с них принимаются как раньше.
 */
data class WearWatchStatusDto(
    val schemaVersion: Int = WearProtocol.SCHEMA_VERSION,
    val appVersion: String,
    /** updatedAt последнего принятого списка операций, 0 — списка ещё нет. */
    val catalogUpdatedAt: Long,
    val pendingEvents: Int,
    val reportedAt: Long
)

data class WearEntryEventDto(
    val schemaVersion: Int = WearProtocol.SCHEMA_VERSION,
    val eventId: String,
    val operationId: Long,
    val operationName: String,
    val quantity: Int,
    val date: String,
    val createdAt: Long
)

data class WearEntryAckDto(
    val schemaVersion: Int = WearProtocol.SCHEMA_VERSION,
    val eventId: String,
    val entryId: Long,
    val processedAt: Long
)

object WearProtocol {
    const val SCHEMA_VERSION = 1
    const val ROOT_PATH = "/workorder/v1"
    const val CATALOG_PATH = "$ROOT_PATH/catalog"
    const val ENTRY_PATH_PREFIX = "$ROOT_PATH/entries"
    const val ACK_PATH_PREFIX = "$ROOT_PATH/acks"
    const val WATCH_STATUS_PATH = "$ROOT_PATH/watch-status"

    /** Статические возможности из res/values/wear.xml: по ним приложения находят друг друга. */
    const val WATCH_CAPABILITY = "workorder_watch_app"
    const val PHONE_CAPABILITY = "workorder_phone_app"

    val DEFAULT_QUICK_AMOUNTS = listOf(1, 5, 10, 17)

    private val gson = Gson()

    fun entryPath(eventId: String): String = "$ENTRY_PATH_PREFIX/$eventId"

    fun ackPath(eventId: String): String = "$ACK_PATH_PREFIX/$eventId"

    fun isEntryPath(path: String?): Boolean =
        path != null && path.startsWith("$ENTRY_PATH_PREFIX/")

    fun isAckPath(path: String?): Boolean =
        path != null && path.startsWith("$ACK_PATH_PREFIX/")

    fun encodeCatalog(value: WearCatalogDto): ByteArray = encode(value)

    fun decodeCatalog(bytes: ByteArray): WearCatalogDto =
        gson.fromJson(bytes.decodeToString(), WearCatalogDto::class.java)

    fun encodeEntry(value: WearEntryEventDto): ByteArray = encode(value)

    fun decodeEntry(bytes: ByteArray): WearEntryEventDto =
        gson.fromJson(bytes.decodeToString(), WearEntryEventDto::class.java)

    fun encodeAck(value: WearEntryAckDto): ByteArray = encode(value)

    fun encodeWatchStatus(value: WearWatchStatusDto): ByteArray = encode(value)

    fun decodeWatchStatus(bytes: ByteArray): WearWatchStatusDto =
        gson.fromJson(bytes.decodeToString(), WearWatchStatusDto::class.java)

    /** Кнопки часов: от 1 до 6 разных чисел 1…9999 (как в настройках телефона), иначе — по умолчанию. */
    fun quickAmounts(catalog: WearCatalogDto): List<Int> {
        val values = catalog.quickAmounts ?: return DEFAULT_QUICK_AMOUNTS
        val valid = values.size in 1..6 && values.all { it in 1..9999 } && values.toSet().size == values.size
        return if (valid) values else DEFAULT_QUICK_AMOUNTS
    }

    fun decodeAck(bytes: ByteArray): WearEntryAckDto =
        gson.fromJson(bytes.decodeToString(), WearEntryAckDto::class.java)

    private fun encode(value: Any): ByteArray = gson.toJson(value).encodeToByteArray()
}
