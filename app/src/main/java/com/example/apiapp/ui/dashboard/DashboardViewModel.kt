package com.example.apiapp.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.apiapp.data.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: AppRepository
) : ViewModel() {

    private val _dashboardState = MutableLiveData<DashboardState>()
    val dashboardState: LiveData<DashboardState> = _dashboardState

    fun loadEntities(keypass: String) {
        _dashboardState.value = DashboardState.Loading
        viewModelScope.launch {
            val result = repository.getDashboard(keypass)
            result.onSuccess { response ->
                _dashboardState.value = DashboardState.Success(response.entities)
            }.onFailure { error ->
                _dashboardState.value = DashboardState.Error(
                    error.message ?: "Failed to load data"
                )
            }
        }
    }

    sealed class DashboardState {
        data object Loading : DashboardState()
        data class Success(val entities: List<Map<String, String>>) : DashboardState()
        data class Error(val message: String) : DashboardState()
    }
}
