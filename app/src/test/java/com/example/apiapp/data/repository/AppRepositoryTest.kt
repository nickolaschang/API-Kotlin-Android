package com.example.apiapp.data.repository

import com.example.apiapp.data.api.ApiService
import com.example.apiapp.data.model.DashboardResponse
import com.example.apiapp.data.model.LoginRequest
import com.example.apiapp.data.model.LoginResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AppRepositoryTest {

    private lateinit var apiService: ApiService
    private lateinit var repository: AppRepository

    @Before
    fun setup() {
        apiService = mock()
        repository = AppRepository(apiService)
    }

    @Test
    fun `login returns success when API call succeeds`() = runTest {
        val expected = LoginResponse("testKeypass")
        whenever(apiService.login(LoginRequest("user", "pass"))).thenReturn(expected)

        val result = repository.login("user", "pass")

        assertTrue(result.isSuccess)
        assertEquals("testKeypass", result.getOrNull()?.keypass)
    }

    @Test
    fun `login returns failure when API call throws`() = runTest {
        whenever(apiService.login(LoginRequest("user", "pass")))
            .thenThrow(RuntimeException("Network error"))

        val result = repository.login("user", "pass")

        assertTrue(result.isFailure)
        assertEquals("Network error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `getDashboard returns success with entities`() = runTest {
        val entities = listOf(mapOf("name" to "Test", "description" to "Desc"))
        val expected = DashboardResponse(entities, 1)
        whenever(apiService.getDashboard("keypass")).thenReturn(expected)

        val result = repository.getDashboard("keypass")

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.entities?.size)
        assertEquals("Test", result.getOrNull()?.entities?.get(0)?.get("name"))
    }

    @Test
    fun `getDashboard returns failure when API call throws`() = runTest {
        whenever(apiService.getDashboard("keypass"))
            .thenThrow(RuntimeException("Server error"))

        val result = repository.getDashboard("keypass")

        assertTrue(result.isFailure)
        assertEquals("Server error", result.exceptionOrNull()?.message)
    }
}
