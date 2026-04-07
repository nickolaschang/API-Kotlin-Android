package com.example.apiapp.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.apiapp.data.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: AppRepository
) : ViewModel() {

    private val _loginState = MutableLiveData<LoginState>()
    val loginState: LiveData<LoginState> = _loginState

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _loginState.value = LoginState.Error("Username and password are required")
            return
        }

        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            val result = repository.login(username, password)
            result.onSuccess { response ->
                _loginState.value = LoginState.Success(response.keypass)
            }.onFailure { error ->
                _loginState.value = LoginState.Error(
                    error.message ?: "Login failed. Please try again."
                )
            }
        }
    }

    sealed class LoginState {
        data object Loading : LoginState()
        data class Success(val keypass: String) : LoginState()
        data class Error(val message: String) : LoginState()
    }
}
