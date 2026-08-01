package com.workorder.app

import android.app.Application
import android.content.Context
import com.workorder.app.data.AppDatabase
import com.workorder.app.data.repository.OperationRepository
import com.workorder.app.data.repository.BankRepository
import com.workorder.app.data.repository.SettingsRepository
import com.workorder.app.data.repository.WorkOrderRepository
import com.workorder.app.util.ExportImportManager
import com.workorder.app.util.ReportExporter

/**
 * Ручной DI-контейнер: единственное место, где создаются база и репозитории.
 * ViewModel получают зависимости через свои companion Factory (см. ui/viewmodel).
 */
class AppContainer(context: Context) {
    private val database = AppDatabase.getDatabase(context)

    val settingsRepository = SettingsRepository(database.settingsDao())
    val workOrderRepository = WorkOrderRepository(database, settingsRepository)
    val operationRepository = OperationRepository(database.operationDao())
    val bankRepository = BankRepository(database)

    val reportExporter = ReportExporter(context.applicationContext)
    val exportImportManager = ExportImportManager(
        context.applicationContext,
        operationRepository,
        settingsRepository
    )
}

class WorkOrderApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
