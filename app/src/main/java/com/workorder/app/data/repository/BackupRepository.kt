package com.workorder.app.data.repository

import androidx.room.withTransaction
import com.workorder.app.data.AppDatabase
import com.workorder.app.util.BackupCodec
import com.workorder.app.util.BackupSnapshot
import com.workorder.app.util.TransferKind

class BackupRepository(private val database: AppDatabase) {
    private val dao = database.backupDao()

    suspend fun snapshot(): BackupSnapshot = database.withTransaction { snapshotInTransaction() }

    private suspend fun snapshotInTransaction() = BackupSnapshot(
        operations = dao.operations(), settings = dao.settings(), workDays = dao.days(),
        entries = dao.entries(), monthlyPlans = dao.plans(), bankAllocations = dao.allocations()
    )

    /** The safety copy and the replacement see the same DB state, including concurrent watch writes. */
    suspend fun import(
        data: BackupSnapshot,
        kind: TransferKind,
        saveSafetyCopy: suspend (BackupSnapshot) -> Unit
    ) {
        BackupCodec.validate(data, kind)
        database.withTransaction {
            saveSafetyCopy(snapshotInTransaction()) // If this fails, nothing is changed.
            if (kind == TransferKind.FULL_DATABASE) {
                dao.clearEntries(); dao.clearAllocations(); dao.clearPlans()
                dao.clearDays(); dao.clearOperations(); dao.clearSettings()
                dao.insertOperations(data.operations)
                dao.insertDays(data.workDays)
                dao.insertEntries(data.entries)
                dao.insertPlans(data.monthlyPlans)
                dao.insertAllocations(data.bankAllocations)
            } else {
                // IDs in a foreign settings file are not identities of local operations.
                // Match names; never let an ID collision rename an unrelated operation and its history.
                val operations = database.operationDao()
                val order = mutableListOf<Long>()
                data.operations.forEach { imported ->
                    val current = operations.getByName(imported.name.trim())
                    val id = if (current == null) {
                        operations.insert(imported.copy(id = 0, name = imported.name.trim()))
                    } else {
                        operations.update(imported.copy(id = current.id, name = imported.name.trim(), sortOrder = current.sortOrder))
                        current.id
                    }
                    order += id
                }
                OperationRepository(operations).applyPreferredOrder(order)
            }
            data.settings?.let { database.settingsDao().upsert(it) }
        }
    }
}
