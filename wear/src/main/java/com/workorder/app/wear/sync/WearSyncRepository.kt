package com.workorder.app.wear.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.workorder.shared.WearCatalogDto
import com.workorder.shared.WearEntryEventDto
import com.workorder.shared.WearOperationDto
import com.workorder.shared.WearProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.util.UUID

class WearSyncRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val dataClient get() = Wearable.getDataClient(appContext)

    private val _operations = MutableStateFlow(loadStoredCatalog())
    val operations: StateFlow<List<WearOperationDto>> = _operations.asStateFlow()

    private val _status = MutableStateFlow(
        preferences.getString(KEY_STATUS, null)
            ?: if (_operations.value.isEmpty()) "Ожидаю список с телефона" else "Готово"
    )
    val status: StateFlow<String> = _status.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    suspend fun refreshCatalog() {
        setStatus("Обновляю список…")
        val buffer = runCatching { dataClient.getDataItems().await() }
            .getOrElse {
                setStatus(if (_operations.value.isEmpty()) "Открой «Наряд» на телефоне" else "Офлайн · список сохранён")
                return
            }
        val catalogs = try {
            buildList {
                buffer.forEach { item ->
                    Log.i(TAG, "Visible DataItem: ${item.uri}")
                    val bytes = item.data
                    if (item.uri.path == WearProtocol.CATALOG_PATH && bytes != null) {
                        runCatching { WearProtocol.decodeCatalog(bytes) }.getOrNull()?.let(::add)
                    }
                }
            }
        } finally {
            buffer.release()
        }
        val newest = catalogs.maxByOrNull { it.updatedAt }
        Log.i(TAG, "Catalog candidates: ${catalogs.size}")
        if (newest != null) {
            acceptCatalog(WearProtocol.encodeCatalog(newest))
        } else {
            setStatus(if (_operations.value.isEmpty()) "Открой «Наряд» на телефоне" else "Готово")
        }
    }

    fun acceptCatalog(payload: ByteArray) {
        val catalog = runCatching { WearProtocol.decodeCatalog(payload) }
            .getOrElse {
                Log.w(TAG, "Invalid catalog", it)
                return
            }
        if (catalog.schemaVersion != WearProtocol.SCHEMA_VERSION) return
        val operations = catalog.operations
            .filter { it.id > 0 && it.name.isNotBlank() && it.durationHours > 0 && it.grade in 3..6 }
            .sortedWith(compareBy<WearOperationDto> { it.sortOrder }.thenBy { it.name.lowercase() })
        preferences.edit()
            .putString(KEY_CATALOG, payload.decodeToString())
            .apply()
        _operations.value = operations
        setStatus(if (operations.isEmpty()) "На телефоне пока нет операций" else "Синхронизировано")
    }

    suspend fun queueOperation(operation: WearOperationDto, quantity: Int): Boolean {
        if (quantity !in 1..999 || _isSending.value) return false
        _isSending.value = true
        val eventId = UUID.randomUUID().toString()
        val event = WearEntryEventDto(
            eventId = eventId,
            operationId = operation.id,
            operationName = operation.name,
            quantity = quantity,
            date = LocalDate.now().toString(),
            createdAt = System.currentTimeMillis()
        )
        return try {
            dataClient.putDataItem(
                PutDataRequest.create(WearProtocol.entryPath(eventId))
                    .setData(WearProtocol.encodeEntry(event))
                    .setUrgent()
            ).await()
            Log.i(TAG, "Queued watch event $eventId: +$quantity · ${operation.name}")
            preferences.edit()
                .putString(KEY_PENDING_EVENT, eventId)
                .putString(KEY_PENDING_LABEL, "+$quantity · ${operation.name}")
                .apply()
            setStatus("В очереди: +$quantity · ${operation.name}")
            true
        } catch (error: Exception) {
            Log.w(TAG, "Unable to queue operation", error)
            setStatus("Не удалось сохранить · повтори")
            false
        } finally {
            _isSending.value = false
        }
    }

    fun acceptAck(payload: ByteArray) {
        val ack = runCatching { WearProtocol.decodeAck(payload) }.getOrNull() ?: return
        if (ack.schemaVersion != WearProtocol.SCHEMA_VERSION) return
        val pendingEvent = preferences.getString(KEY_PENDING_EVENT, null)
        if (pendingEvent == ack.eventId) {
            val label = preferences.getString(KEY_PENDING_LABEL, null).orEmpty()
            preferences.edit()
                .remove(KEY_PENDING_EVENT)
                .remove(KEY_PENDING_LABEL)
                .apply()
            setStatus(if (label.isBlank()) "Добавлено в телефон" else "Добавлено: $label")
        }
    }

    private fun loadStoredCatalog(): List<WearOperationDto> {
        val json = preferences.getString(KEY_CATALOG, null) ?: return emptyList()
        return runCatching { WearProtocol.decodeCatalog(json.encodeToByteArray()).operations }
            .getOrDefault(emptyList())
            .sortedWith(compareBy<WearOperationDto> { it.sortOrder }.thenBy { it.name.lowercase() })
    }

    private fun setStatus(value: String) {
        preferences.edit().putString(KEY_STATUS, value).apply()
        _status.value = value
    }

    companion object {
        private const val TAG = "WearSyncRepository"
        private const val PREFERENCES = "wear_sync"
        private const val KEY_CATALOG = "catalog_json"
        private const val KEY_STATUS = "status"
        private const val KEY_PENDING_EVENT = "pending_event"
        private const val KEY_PENDING_LABEL = "pending_label"
    }
}
