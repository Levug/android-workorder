package com.workorder.app.wear.sync

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.workorder.app.wear.WearWorkOrderApp
import com.workorder.shared.WearProtocol

class WearDataListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val repository = (application as WearWorkOrderApp).container.syncRepository
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            val item = event.dataItem
            val payload = item.data ?: return@forEach
            when {
                item.uri.path == WearProtocol.CATALOG_PATH -> repository.acceptCatalog(payload.copyOf())
                WearProtocol.isAckPath(item.uri.path) -> {
                    repository.acceptAck(payload.copyOf())
                    Wearable.getDataClient(this).deleteDataItems(item.uri)
                }
            }
        }
    }
}
