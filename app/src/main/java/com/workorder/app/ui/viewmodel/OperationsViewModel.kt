package com.workorder.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workorder.app.WorkOrderApp
import com.workorder.app.data.model.Operation
import com.workorder.app.data.repository.OperationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OperationsViewModel(
    private val operationRepository: OperationRepository
) : ViewModel() {

    val operations: StateFlow<List<Operation>> = operationRepository
        .observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** Создание (id = 0) или обновление операции. */
    fun save(id: Long, name: String, durationHours: Double, grade: Int) {
        if (name.isBlank() || durationHours <= 0 || grade !in 3..6) return
        viewModelScope.launch {
            operationRepository.save(
                Operation(id = id, name = name.trim(), durationHours = durationHours, grade = grade)
            )
        }
    }

    fun delete(operation: Operation) {
        viewModelScope.launch { operationRepository.delete(operation) }
    }

    fun moveUp(operationId: Long) {
        viewModelScope.launch { operationRepository.move(operationId, -1) }
    }

    fun moveDown(operationId: Long) {
        viewModelScope.launch { operationRepository.move(operationId, 1) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WorkOrderApp
                OperationsViewModel(app.container.operationRepository)
            }
        }
    }
}
