package com.workorder.app.ui.viewmodel

import android.net.Uri
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
import com.workorder.app.util.PreparedImport
import com.workorder.app.util.TransferKind
import kotlinx.coroutines.CancellationException
import java.io.File
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

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _exportFile = MutableStateFlow<File?>(null)
    val exportFile = _exportFile.asStateFlow()
    private val _pendingImport = MutableStateFlow<PreparedImport?>(null)
    val pendingImport = _pendingImport.asStateFlow()
    private val _hasSafetyCopy = MutableStateFlow(exportImportManager.hasSafetyCopy())
    val hasSafetyCopy = _hasSafetyCopy.asStateFlow()

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

    fun prepareExport(kind: TransferKind) = transfer {
        _exportFile.value = exportImportManager.prepareExport(kind)
    }

    fun prepareSafetyExport() = transfer {
        _exportFile.value = exportImportManager.prepareSafetyExport()
    }

    fun saveExport(uri: Uri) = transfer {
        val file = checkNotNull(_exportFile.value)
        exportImportManager.saveExport(file, uri)
        _exportFile.value = null
        _message.value = "Файл сохранён в выбранном месте"
    }

    fun readImport(uri: Uri, kind: TransferKind) = transfer {
        _pendingImport.value = exportImportManager.readImport(uri, kind)
    }

    fun confirmImport() = transfer {
        val prepared = checkNotNull(_pendingImport.value)
        exportImportManager.importData(prepared)
        _pendingImport.value = null
        _message.value = if (prepared.kind == TransferKind.FULL_DATABASE) "База восстановлена. Предыдущая база сохранена в страховочной копии."
            else "Операции и настройки импортированы. Журнал сохранён."
    }

    fun dismissExport() { if (!_busy.value) _exportFile.value = null }
    fun dismissImport() { if (!_busy.value) _pendingImport.value = null }
    fun showTransferError() { _message.value = "Не удалось открыть системное окно. Попробуйте сохранить файл на устройстве." }

    private fun transfer(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = "Ошибка: ${e.message ?: "не удалось обработать файл"}"
            } finally {
                _hasSafetyCopy.value = exportImportManager.hasSafetyCopy()
                _busy.value = false
            }
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
