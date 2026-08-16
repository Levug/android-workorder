package com.workorder.app.sync

import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.DataApi
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.PutDataRequest
import com.workorder.app.data.model.Operation
import com.workorder.app.data.repository.OperationRepository
import com.workorder.app.data.repository.WorkOrderRepository
import com.workorder.shared.WearCatalogDto
import com.workorder.shared.WearEntryAckDto
import com.workorder.shared.WearOperationDto
import com.workorder.shared.WearProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

class PhoneWearEventProcessor(
    private val wearApi: PhoneWearApi,
    private val operationRepository: OperationRepository,
    private val workOrderRepository: WorkOrderRepository
) {
    private val processingMutex = Mutex()

    suspend fun processPendingEvents() = processingMutex.withLock {
        val pending = runCatching { wearApi.getDataItems() }
            .getOrElse {
                Log.d(TAG, "Wear Data Layer is not available yet", it)
                return@withLock
            }
        pending.filter { WearProtocol.isEntryPath(it.first.path) }
            .forEach { (uri, payload) -> processEventLocked(uri, payload) }
    }

    suspend fun processEvent(uri: Uri, payload: ByteArray): Boolean = processingMutex.withLock {
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

class PhoneWearSyncManager(
    private val wearApi: PhoneWearApi,
    private val operationRepository: OperationRepository,
    private val eventProcessor: PhoneWearEventProcessor
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val liveDataListener = DataApi.DataListener { dataEvents ->
        val payloads = buildList<Pair<Uri, ByteArray>> {
            dataEvents.forEach { event ->
                val item = event.dataItem
                val data = item.data
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
    }

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
        retryPendingEvents()
        scope.launch {
            operationRepository.observeAll()
                .distinctUntilChanged()
                .collect { publishCatalog(it) }
        }
    }

    /** Повторно подхватывает сохранённую на часах очередь, например после возврата приложения из фона. */
    fun retryPendingEvents() {
        scope.launch { eventProcessor.processPendingEvents() }
    }

    private suspend fun publishCatalog(operations: List<Operation>) {
        val payload = WearProtocol.encodeCatalog(
            WearCatalogDto(
                updatedAt = System.currentTimeMillis(),
                operations = operations.map {
                    WearOperationDto(
                        id = it.id,
                        name = it.name,
                        durationHours = it.durationHours,
                        grade = it.grade,
                        sortOrder = it.sortOrder
                    )
                }
            )
        )
        if (payload.size > MAX_CATALOG_BYTES) {
            Log.e(TAG, "Operation catalog is too large for Wear Data Layer: ${payload.size}")
            return
        }
        runCatching {
            wearApi.putDataItem(
                PutDataRequest.create(WearProtocol.CATALOG_PATH)
                    .setData(payload)
                    .setUrgent()
            )
        }.onSuccess {
            Log.i(TAG, "Catalog published: ${operations.size} operations, ${payload.size} bytes")
        }.onFailure { Log.w(TAG, "Catalog will be published when Data Layer is available", it) }
    }

    companion object {
        private const val TAG = "PhoneWearSync"
        private const val MAX_CATALOG_BYTES = 90_000
    }
}
