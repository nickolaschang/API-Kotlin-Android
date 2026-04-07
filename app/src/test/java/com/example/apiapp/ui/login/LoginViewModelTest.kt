package com.example.apiapp.ui.login

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.apiapp.data.model.LoginResponse
import com.example.apiapp.data.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: AppRepository
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mock()
        viewModel = LoginViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `login with blank username shows error`() {
        viewModel.login("", "password")

        val state = viewModel.loginState.value
        assertTrue(state is LoginViewModel.LoginState.Error)
        assertEquals("Username and password are required", (state as LoginViewModel.LoginState.Error).message)
    }

    @Test
    fun `login with blank password shows error`() {
        viewModel.login("user", "")

        val state = viewModel.loginState.value
        assertTrue(state is LoginViewModel.LoginState.Error)
    }

    @Test
    fun `successful login emits success state`() = runTest(testDispatcher) {
        whenever(repository.login("user", "pass"))
            .thenReturn(Result.success(LoginResponse("myKeypass")))

        viewModel.login("user", "pass")
        advanceUntilIdle()

        val state = viewModel.loginState.value
        assertTrue(state is LoginViewModel.LoginState.Success)
        assertEquals("myKeypass", (state as LoginViewModel.LoginState.Success).keypass)
    }

    @Test
    fun `failed login emits error state`() = runTest(testDispatcher) {
        whenever(repository.login("user", "pass"))
            .thenReturn(Result.failure(RuntimeException("Invalid credentials")))

        viewModel.login("user", "pass")
        advanceUntilIdle()

        val state = viewModel.loginState.value
        assertTrue(state is LoginViewModel.LoginState.Error)
        assertEquals("Invalid credentials", (state as LoginViewModel.LoginState.Error).message)
    }
}
