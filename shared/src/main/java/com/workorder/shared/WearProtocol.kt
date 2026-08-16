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
    val operations: List<WearOperationDto>
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

    fun decodeAck(bytes: ByteArray): WearEntryAckDto =
        gson.fromJson(bytes.decodeToString(), WearEntryAckDto::class.java)

    private fun encode(value: Any): ByteArray = gson.toJson(value).encodeToByteArray()
}
