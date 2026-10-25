package com.workorder.app.sync

import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.DataApi
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.PutDataRequest
import com.workorder.app.data.model.Operation
import com.workorder.app.data.model.quickAmountList
import com.workorder.app.data.repository.OperationRepository
import com.workorder.app.data.repository.SettingsRepository
import com.workorder.app.data.repository.WorkOrderRepository
import com.workorder.shared.WearCatalogDto
import com.workorder.shared.WearEntryAckDto
import com.workorder.shared.WearOperationDto
import com.workorder.shared.WearProtocol
import com.workorder.shared.WearWatchStatusDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

/** Настройки связи с часами на этом телефоне: в резервную копию не входят. */
class WearSyncPreferences(private val preferences: SharedPreferences) {
    var enabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, true)
        set(value) = preferences.edit().putBoolean(KEY_ENABLED, value).apply()

    val lastEntryAt: Long get() = preferences.getLong(KEY_LAST_ENTRY_AT, 0L)
    val lastEntryLabel: String get() = preferences.getString(KEY_LAST_ENTRY_LABEL, null).orEmpty()
    val catalogSentAt: Long get() = preferences.getLong(KEY_CATALOG_AT, 0L)
    val catalogSize: Int get() = preferences.getInt(KEY_CATALOG_SIZE, 0)

    fun recordEntry(label: String, at: Long) {
        preferences.edit().putLong(KEY_LAST_ENTRY_AT, at).putString(KEY_LAST_ENTRY_LABEL, label).apply()
    }

    fun recordCatalog(size: Int, at: Long) {
        preferences.edit().putLong(KEY_CATALOG_AT, at).putInt(KEY_CATALOG_SIZE, size).apply()
    }

    private companion object {
        const val KEY_ENABLED = "wearSyncEnabled"
        const val KEY_LAST_ENTRY_AT = "wearLastEntryAt"
        const val KEY_LAST_ENTRY_LABEL = "wearLastEntryLabel"
        const val KEY_CATALOG_AT = "wearCatalogSentAt"
        const val KEY_CATALOG_SIZE = "wearCatalogSize"
    }
}

class PhoneWearEventProcessor(
    private val wearApi: PhoneWearApi,
    private val operationRepository: OperationRepository,
    private val workOrderRepository: WorkOrderRepository,
    private val preferences: WearSyncPreferences
) {
    private val processingMutex = Mutex()

    /** Вызывается после каждой принятой записи — для экрана «Часы» в настройках. */
    var onEntryAccepted: (() -> Unit)? = null

    suspend fun processPendingEvents() = processingMutex.withLock {
        // Выключенная синхронизация оставляет записи в очереди часов: они придут после включения.
        if (!preferences.enabled) return@withLock
        val pending = runCatching { wearApi.getDataItems() }
            .getOrElse {
                Log.d(TAG, "Wear Data Layer is not available yet", it)
                return@withLock
            }
        pending.filter { WearProtocol.isEntryPath(it.first.path) }
            .forEach { (uri, payload) -> processEventLocked(uri, payload) }
    }

    suspend fun processEvent(uri: Uri, payload: ByteArray): Boolean = processingMutex.withLock {
        if (!preferences.enabled) return@withLock false
        processEventLocked(uri, payload)
    }

    private suspend fun processEventLocked(uri: Uri, payload: ByteArray): Boolean {
        val event = runCatching { WearProtocol.decodeEntry(payload) }
            .getOrElse {
                Log.w(TAG, "Invalid event from watch: $uri", it)
                return false
            }
        if (
            event.schemaVersion != WearProtocol.SCHEMA_VERSION ||
            event.eventId.isBlank() ||
            uri.path != WearProtocol.entryPath(event.eventId) ||
            event.quantity !in 1..100_000 ||
            event.createdAt <= 0 ||
            runCatching { LocalDate.parse(event.date) }.isFailure
        ) {
            Log.w(TAG, "Rejected event from watch: $uri")
            return false
        }

        val operation = operationRepository.getById(event.operationId)
            ?: operationRepository.getByName(event.operationName)
            ?: run {
                Log.w(TAG, "Operation is no longer available: ${event.operationName}")
                return false
            }

        val entryId = workOrderRepository.addSyncedEntry(
            date = event.date,
            operationId = operation.id,
            quantity = event.quantity,
            createdAt = event.createdAt,
            sourceEventId = event.eventId
        )
        Log.i(TAG, "Accepted watch event ${event.eventId}: entry=$entryId, quantity=${event.quantity}")
        preferences.recordEntry("${operation.name} +${event.quantity}", System.currentTimeMillis())
        onEntryAccepted?.invoke()
        val ack = WearEntryAckDto(
            eventId = event.eventId,
            entryId = entryId,
            processedAt = System.currentTimeMillis()
        )
        wearApi.putDataItem(
            PutDataRequest.create(WearProtocol.ackPath(event.eventId))
                .setData(WearProtocol.encodeAck(ack))
                .setUrgent()
        )
        wearApi.deleteDataItems(uri)
        return true
    }

    companion object {
        private const val TAG = "PhoneWearSync"
    }
}

/** Часы, найденные при последней проверке связи. */
data class WatchInfo(
    val id: String,
    val name: String,
    /** «Наряд» на часах объявил о себе (часы 3.5 и новее). */
    val appFound: Boolean,
    val appVersion: String? = null,
    val pendingEvents: Int? = null,
    /** Часы уже получили последний отправленный список операций. */
    val catalogDelivered: Boolean? = null
)

data class WearConnectionState(
    val enabled: Boolean = true,
    val checking: Boolean = false,
    val checkedAt: Long = 0L,
    /** Сервис Wear OS на телефоне недоступен (нет приложения-компаньона или Google Play). */
    val serviceUnavailable: Boolean = false,
    val watches: List<WatchInfo> = emptyList(),
    val catalogSentAt: Long = 0L,
    val catalogSize: Int = 0,
    val lastEntryAt: Long = 0L,
    val lastEntryLabel: String = ""
)

class PhoneWearSyncManager(
    private val wearApi: PhoneWearApi,
    private val operationRepository: OperationRepository,
    private val settingsRepository: SettingsRepository,
    private val eventProcessor: PhoneWearEventProcessor,
    private val preferences: WearSyncPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private var catalogJob: Job? = null
    private val catalogMutex = Mutex()
    private var lastCatalog: Pair<List<Operation>, List<Int>>? = null

    private val _state = MutableStateFlow(storedState())
    val state: StateFlow<WearConnectionState> = _state.asStateFlow()

    private val liveDataListener = DataApi.DataListener { dataEvents ->
        var statusChanged = false
        val payloads = buildList<Pair<Uri, ByteArray>> {
            dataEvents.forEach { event ->
                val item = event.dataItem
                val data = item.data
                if (item.uri.path == WearProtocol.WATCH_STATUS_PATH) statusChanged = true
                if (
                    event.type == DataEvent.TYPE_CHANGED &&
                    WearProtocol.isEntryPath(item.uri.path) &&
                    data != null
                ) {
                    add(item.uri to data.copyOf())
                }
            }
        }
        if (payloads.isNotEmpty()) {
            scope.launch {
                payloads.forEach { (uri, payload) -> eventProcessor.processEvent(uri, payload) }
            }
        }
        if (statusChanged) scope.launch { checkConnection() }
    }

    init {
        eventProcessor.onEntryAccepted = { _state.update { storedState(it) } }
    }

    val isEnabled: Boolean get() = preferences.enabled

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            runCatching { wearApi.connectedNodes() }
                .onSuccess { nodes ->
                    Log.i(TAG, "Connected Wear nodes: ${nodes.joinToString { "${it.displayName}:${it.id}" }}")
                }
                .onFailure { Log.w(TAG, "Unable to query Wear nodes", it) }
        }
        scope.launch {
            runCatching { wearApi.addDataListener(liveDataListener) }
                .onSuccess { Log.i(TAG, "Live Wear data listener registered") }
                .onFailure { Log.w(TAG, "Unable to register live Wear data listener", it) }
        }
        if (preferences.enabled) startPublishing()
        retryPendingEvents()
    }

    /** Включение и выключение из настроек телефона. */
    fun setEnabled(enabled: Boolean) {
        preferences.enabled = enabled
        _state.update { it.copy(enabled = enabled) }
        if (!started.get()) return
        if (enabled) {
            startPublishing()
            retryPendingEvents()
        } else {
            catalogJob?.cancel()
            catalogJob = null
        }
    }

    /** Повторно подхватывает сохранённую на часах очередь, например после возврата приложения из фона. */
    fun retryPendingEvents() {
        scope.launch { eventProcessor.processPendingEvents() }
    }

    /**
     * «Проверить связь»: находит часы и «Наряд» на них, заново отправляет список операций
     * и забирает записи из очереди часов.
     */
    fun refresh() {
        scope.launch {
            _state.update { it.copy(checking = true) }
            if (preferences.enabled) {
                lastCatalog?.let { (operations, amounts) -> publishCatalog(operations, amounts, force = true) }
                eventProcessor.processPendingEvents()
            }
            checkConnection()
        }
    }

    private suspend fun checkConnection() {
        val nodes = runCatching { wearApi.connectedNodes() }.getOrElse {
            Log.w(TAG, "Wear service unavailable", it)
            _state.update {
                storedState(it).copy(checking = false, checkedAt = System.currentTimeMillis(), serviceUnavailable = true)
            }
            return
        }
        val withApp = runCatching { wearApi.reachableNodesWithCapability(WearProtocol.WATCH_CAPABILITY) }
            .getOrDefault(emptySet())
        val statuses = runCatching { wearApi.getDataItems() }.getOrDefault(emptyList())
            .filter { it.first.path == WearProtocol.WATCH_STATUS_PATH }
            .mapNotNull { (uri, payload) ->
                runCatching { WearProtocol.decodeWatchStatus(payload) }.getOrNull()?.let { uri.host to it }
            }
            .toMap()
        val watches = nodes.map { node ->
            val status: WearWatchStatusDto? = statuses[node.id]
            WatchInfo(
                id = node.id,
                name = node.displayName.ifBlank { "Часы" },
                appFound = node.id in withApp || status != null,
                appVersion = status?.appVersion,
                pendingEvents = status?.pendingEvents,
                catalogDelivered = status?.let { preferences.catalogSentAt > 0 && it.catalogUpdatedAt >= preferences.catalogSentAt }
            )
        }
        _state.update {
            storedState(it).copy(
                checking = false,
                checkedAt = System.currentTimeMillis(),
                serviceUnavailable = false,
                watches = watches
            )
        }
    }

    private fun startPublishing() {
        if (catalogJob?.isActive == true) return
        catalogJob = scope.launch {
            // Архивные операции на часы не отправляются; события из очереди часов по ним всё равно принимаются.
            combine(operationRepository.observeActive(), settingsRepository.observe()) { operations, settings ->
                operations to settings.quickAmountList
            }
                .distinctUntilChanged()
                .collect { (operations, amounts) -> publishCatalog(operations, amounts, force = false) }
        }
    }

    private suspend fun publishCatalog(operations: List<Operation>, quickAmounts: List<Int>, force: Boolean) =
        catalogMutex.withLock {
            if (!force && lastCatalog == operations to quickAmounts) return@withLock
            lastCatalog = operations to quickAmounts
            val updatedAt = System.currentTimeMillis()
            val payload = WearProtocol.encodeCatalog(
                WearCatalogDto(
                    updatedAt = updatedAt,
                    operations = operations.map {
                        WearOperationDto(
                            id = it.id,
                            name = it.name,
                            durationHours = it.durationHours,
                            grade = it.grade,
                            sortOrder = it.sortOrder
                        )
                    },
                    quickAmounts = quickAmounts
                )
            )
            if (payload.size > MAX_CATALOG_BYTES) {
                Log.e(TAG, "Operation catalog is too large for Wear Data Layer: ${payload.size}")
                return@withLock
            }
            runCatching {
                wearApi.putDataItem(
                    PutDataRequest.create(WearProtocol.CATALOG_PATH)
                        .setData(payload)
                        .setUrgent()
                )
            }.onSuccess {
                preferences.recordCatalog(operations.size, updatedAt)
                _state.update { storedState(it) }
                Log.i(TAG, "Catalog published: ${operations.size} operations, ${payload.size} bytes")
            }.onFailure { Log.w(TAG, "Catalog will be published when Data Layer is available", it) }
        }

    private fun storedState(current: WearConnectionState = WearConnectionState()) = current.copy(
        enabled = preferences.enabled,
        catalogSentAt = preferences.catalogSentAt,
        catalogSize = preferences.catalogSize,
        lastEntryAt = preferences.lastEntryAt,
        lastEntryLabel = preferences.lastEntryLabel
    )

    companion object {
        private const val TAG = "PhoneWearSync"
        private const val MAX_CATALOG_BYTES = 90_000
    }
}
