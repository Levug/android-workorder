package com.workorder.app.data.repository

import com.workorder.app.data.dao.SettingsDao
import com.workorder.app.data.model.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SettingsRepository(private val settingsDao: SettingsDao) {

    /** Настройки со значениями по умолчанию, если ещё не сохранялись. */
    fun observe(): Flow<Settings> = settingsDao.observe().map { it ?: Settings() }

    suspend fun update(transform: (Settings) -> Settings) {
        val current = settingsDao.observe().first() ?: Settings()
        settingsDao.upsert(transform(current))
    }

    suspend fun replace(settings: Settings) = settingsDao.upsert(settings.copy(id = 1))
}
