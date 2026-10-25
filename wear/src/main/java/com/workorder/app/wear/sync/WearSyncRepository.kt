package com.workorder.app.wear.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.workorder.app.wear.BuildConfig
import com.workorder.shared.WearCatalogDto
import com.workorder.shared.WearEntryEventDto
import com.workorder.shared.WearOperationDto
import com.workorder.shared.WearProtocol
import com.workorder.shared.WearWatchStatusDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.util.UUID

class WearSyncRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val dataClient get() = Wearable.getDataClient(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val storedCatalog = loadStoredCatalog()

    private val _operations = MutableStateFlow(sorted(storedCatalog?.operations.orEmpty()))
    val operations: StateFlow<List<WearOperationDto>> = _operations.asStateFlow()

    /** Быстрые партии из настроек телефона; телефон старше 3.8 их не присылает — тогда 1, 5, 10, 17. */
    private val _quickAmounts = MutableStateFlow(
        storedCatalog?.let(WearProtocol::quickAmounts) ?: WearProtocol.DEFAULT_QUICK_AMOUNTS
    )
    val quickAmounts: StateFlow<List<Int>> = _quickAmounts.asStateFlow()

    private val _status = MutableStateFlow(
        preferences.getString(KEY_STATUS, null)
            ?: if (_operations.value.isEmpty()) "Ожидаю список с телефона" else "Готово"
    )
    val status: StateFlow<String> = _status.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _pendingCount = MutableStateFlow(pendingIds().size)
    /** Записи, которые телефон ещё не подтвердил. */
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    suspend fun refreshCatalog() {
        setStatus("Обновляю список…")
        val phoneConnected = runCatching {
            Wearable.getNodeClient(appContext).connectedNodes.await().isNotEmpty()
        }.getOrDefault(true)
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
        when {
            newest != null -> acceptCatalog(WearProtocol.encodeCatalog(newest))
            !phoneConnected -> setStatus(
                if (_pendingCount.value > 0) "Нет связи · в очереди ${_pendingCount.value}" else "Нет связи с телефоном"
            )
            else -> setStatus(if (_operations.value.isEmpty()) "Открой «Наряд» на телефоне" else "Готово")
        }
        publishStatus()
    }

    fun acceptCatalog(payload: ByteArray) {
        val catalog = runCatching { WearProtocol.decodeCatalog(payload) }
            .getOrElse {
                Log.w(TAG, "Invalid catalog", it)
                return
            }
        if (catalog.schemaVersion != WearProtocol.SCHEMA_VERSION) return
        val operations = sorted(
            catalog.operations.filter { it.id > 0 && it.name.isNotBlank() && it.durationHours > 0 && it.grade in 3..6 }
        )
        preferences.edit()
            .putString(KEY_CATALOG, payload.decodeToString())
            .putLong(KEY_CATALOG_UPDATED_AT, catalog.updatedAt)
            .apply()
        _operations.value = operations
        _quickAmounts.value = WearProtocol.quickAmounts(catalog)
        setStatus(if (operations.isEmpty()) "На телефоне пока нет операций" else "Синхронизировано")
        scope.launch { publishStatus() }
    }

    suspend fun queueOperation(operation: WearOperationDto, quantity: Int): Boolean {
        if (quantity !in 1..9999 || _isSending.value) return false
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
            savePending(pendingIds() + eventId)
            preferences.edit().putString(KEY_PENDING_LABEL, "+$quantity · ${operation.name}").apply()
            setStatus("В очереди: +$quantity · ${operation.name}")
            scope.launch { publishStatus() }
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
        val pending = pendingIds()
        if (ack.eventId !in pending) return
        val left = pending - ack.eventId
        savePending(left)
        val label = preferences.getString(KEY_PENDING_LABEL, null).orEmpty()
        setStatus(
            when {
                left.isNotEmpty() -> "Добавлено · в очереди ${left.size}"
                label.isBlank() -> "Добавлено в телефон"
                else -> "Добавлено: $label"
            }
        )
        scope.launch { publishStatus() }
    }

    /** Сообщает телефону версию, полученный список и очередь — для экрана «Часы» в настройках телефона. */
    private suspend fun publishStatus() {
        val status = WearWatchStatusDto(
            appVersion = BuildConfig.VERSION_NAME,
            catalogUpdatedAt = preferences.getLong(KEY_CATALOG_UPDATED_AT, 0L),
            pendingEvents = pendingIds().size,
            reportedAt = System.currentTimeMillis()
        )
        runCatching {
            dataClient.putDataItem(
                PutDataRequest.create(WearProtocol.WATCH_STATUS_PATH)
                    .setData(WearProtocol.encodeWatchStatus(status))
                    .setUrgent()
            ).await()
        }.onFailure { Log.w(TAG, "Unable to publish watch status", it) }
    }

    private fun pendingIds(): Set<String> {
        val stored = preferences.getStringSet(KEY_PENDING_EVENTS, null)?.toSet().orEmpty()
        // Часы 3.4 хранили только последнюю запись.
        val legacy = preferences.getString(KEY_PENDING_EVENT, null)
        return stored + listOfNotNull(legacy)
    }

    private fun savePending(ids: Set<String>) {
        preferences.edit()
            .putStringSet(KEY_PENDING_EVENTS, ids)
            .remove(KEY_PENDING_EVENT)
            .apply()
        _pendingCount.value = ids.size
    }

    private fun loadStoredCatalog(): WearCatalogDto? {
        val json = preferences.getString(KEY_CATALOG, null) ?: return null
        return runCatching { WearProtocol.decodeCatalog(json.encodeToByteArray()) }.getOrNull()
    }

    private fun sorted(operations: List<WearOperationDto>) =
        operations.sortedWith(compareBy<WearOperationDto> { it.sortOrder }.thenBy { it.name.lowercase() })

    private fun setStatus(value: String) {
        preferences.edit().putString(KEY_STATUS, value).apply()
        _status.value = value
    }

    companion object {
        private const val TAG = "WearSyncRepository"
        private const val PREFERENCES = "wear_sync"
        private const val KEY_CATALOG = "catalog_json"
        private const val KEY_CATALOG_UPDATED_AT = "catalog_updated_at"
        private const val KEY_STATUS = "status"
        private const val KEY_PENDING_EVENT = "pending_event"
        private const val KEY_PENDING_EVENTS = "pending_events"
        private const val KEY_PENDING_LABEL = "pending_label"
    }
}
