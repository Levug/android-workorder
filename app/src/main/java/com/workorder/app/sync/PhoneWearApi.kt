package com.workorder.app.sync

import android.content.Context
import android.net.Uri
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.wearable.DataApi
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Совместимый транспорт для телефонов с китайской реализацией Wear OS.
 * На таких устройствах современные getDataClient/getNodeClient могут возвращать API_UNAVAILABLE,
 * а официальный legacy GoogleApiClient подключается к системному провайдеру Wearable API.
 */
@Suppress("DEPRECATION")
class PhoneWearApi(context: Context) {
    private val client = GoogleApiClient.Builder(context.applicationContext)
        .addApi(Wearable.API)
        .build()

    suspend fun connectedNodes(): List<Node> = withContext(Dispatchers.IO) {
        ensureConnected()
        val result = Wearable.NodeApi.getConnectedNodes(client).await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        check(result.status.isSuccess) { "Unable to get Wear nodes: ${result.status}" }
        result.nodes
    }

    suspend fun addDataListener(listener: DataApi.DataListener) = withContext(Dispatchers.IO) {
        ensureConnected()
        val result = Wearable.DataApi.addListener(client, listener)
            .await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        check(result.isSuccess) { "Unable to register Wear data listener: $result" }
    }

    suspend fun getDataItems(): List<Pair<Uri, ByteArray>> = withContext(Dispatchers.IO) {
        ensureConnected()
        val buffer = Wearable.DataApi.getDataItems(client).await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        check(buffer.status.isSuccess) { "Unable to get Wear data: ${buffer.status}" }
        try {
            buildList {
                buffer.forEach { item ->
                    item.data?.let { add(item.uri to it.copyOf()) }
                }
            }
        } finally {
            buffer.release()
        }
    }

    suspend fun putDataItem(request: PutDataRequest) = withContext(Dispatchers.IO) {
        ensureConnected()
        val result = Wearable.DataApi.putDataItem(client, request)
            .await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        check(result.status.isSuccess) { "Unable to put Wear data: ${result.status}" }
    }

    suspend fun deleteDataItems(uri: Uri) = withContext(Dispatchers.IO) {
        ensureConnected()
        val result = Wearable.DataApi.deleteDataItems(client, uri)
            .await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        check(result.status.isSuccess) { "Unable to delete Wear data: ${result.status}" }
    }

    @Synchronized
    private fun ensureConnected() {
        if (client.isConnected) return
        val result = client.blockingConnect(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        check(result.isSuccess) { "Wearable API connection failed: $result" }
    }

    companion object {
        private const val TIMEOUT_SECONDS = 15L
    }
}
