package com.example.apiapp.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.apiapp.data.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel backing [com.example.apiapp.ui.login.LoginActivity].
 *
 * Owns the login state machine and delegates the actual HTTP call to
 * [AppRepository]. The Activity observes [loginState] via LiveData and
 * reacts to transitions: Loading → spinner, Success → navigate to
 * dashboard, Error → display message.
 *
 * Injected by Hilt through `@HiltViewModel`; the repository is a
 * constructor dependency provided by [com.example.apiapp.di.AppModule].
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: AppRepository
) : ViewModel() {

    private val _loginState = MutableLiveData<LoginState>()
    val loginState: LiveData<LoginState> = _loginState

    /**
     * Kicks off a login attempt.
     *
     * Blank fields short-circuit straight to an error state — we don't
     * bother hitting the network for input we already know is invalid.
     * Otherwise we emit [LoginState.Loading] immediately so the UI can
     * show its spinner, then fire the request inside [viewModelScope] so
     * it's automatically cancelled if the Activity is destroyed.
     */
    fun login(location: String, username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _loginState.value = LoginState.Error("Username and password are required")
            return
        }

        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            val result = repository.login(location, username, password)
            result.onSuccess { response ->
                _loginState.value = LoginState.Success(response.keypass)
            }.onFailure { error ->
                _loginState.value = LoginState.Error(
                    error.message ?: "Login failed. Please try again."
                )
            }
        }
    }

    /** Sealed hierarchy representing every possible UI state of the login form. */
    sealed class LoginState {
        /** A request is in flight — the button should be disabled. */
        data object Loading : LoginState()

        /** Login succeeded; [keypass] is the topic token for the dashboard. */
        data class Success(val keypass: String) : LoginState()

        /** Login failed; [message] is already user-friendly from the repository. */
        data class Error(val message: String) : LoginState()
    }
}
