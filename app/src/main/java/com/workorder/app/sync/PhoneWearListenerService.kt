package com.workorder.app.sync

import android.net.Uri
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import com.workorder.app.WorkOrderApp
import com.workorder.shared.WearProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class PhoneWearListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        if (!com.workorder.app.BuildConfig.ENABLE_WEAR_SYNC) return
        val container = (application as WorkOrderApp).container
        // Выключено в настройках: записи остаются в очереди часов до включения.
        if (!container.wearSyncManager.isEnabled) return
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
        if (payloads.isEmpty()) return

        val processor = container.wearEventProcessor
        runBlocking(Dispatchers.IO) {
            payloads.forEach { (uri, payload) -> processor.processEvent(uri, payload) }
        }
    }
}
