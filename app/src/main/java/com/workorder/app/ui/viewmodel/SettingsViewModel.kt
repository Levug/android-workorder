package com.workorder.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workorder.app.WorkOrderApp
import com.workorder.app.data.model.Settings
import com.workorder.app.data.model.ThemeMode
import com.workorder.app.data.model.WorkSchedule
import com.workorder.app.data.repository.SettingsRepository
import com.workorder.app.util.ExportImportManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val exportImportManager: ExportImportManager
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsRepository.observe()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Settings()
        )

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setHourlyRate(rate: Double) {
        viewModelScope.launch { settingsRepository.update { it.copy(hourlyRate = rate) } }
    }

    fun setContractHourlyRate(rate: Double) {
        viewModelScope.launch { settingsRepository.update { it.copy(contractHourlyRate = rate) } }
    }

    fun setWorkSchedule(schedule: WorkSchedule) {
        viewModelScope.launch { settingsRepository.update { it.copy(workSchedule = schedule) } }
    }

    fun setShiftAnchorDate(date: String) {
        viewModelScope.launch { settingsRepository.update { it.copy(shiftAnchorDate = date) } }
    }

    fun setDefaultDayHours(hours: Double) {
        viewModelScope.launch { settingsRepository.update { it.copy(defaultDayHours = hours) } }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.update { it.copy(themeMode = mode) } }
    }

    fun setThemePreset(preset: String) {
        viewModelScope.launch { settingsRepository.update { it.copy(themePreset = preset) } }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.update { it.copy(dynamicColor = enabled) } }
    }

    fun exportData() {
        viewModelScope.launch {
            _message.value = exportImportManager.exportSettings().message
        }
    }

    fun importData() {
        viewModelScope.launch {
            _message.value = exportImportManager.importSettings().message
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WorkOrderApp
                SettingsViewModel(
                    settingsRepository = app.container.settingsRepository,
                    exportImportManager = app.container.exportImportManager
                )
            }
        }
    }
}
