package com.workorder.app.wear

import android.app.Application
import android.content.Context
import com.workorder.app.wear.sync.WearSyncRepository

class WearContainer(context: Context) {
    val syncRepository = WearSyncRepository(context.applicationContext)
}

class WearWorkOrderApp : Application() {
    val container: WearContainer by lazy { WearContainer(this) }
}
