package com.example.apiapp.ui.dashboard

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.apiapp.data.model.DashboardResponse
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
class DashboardViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: AppRepository
    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mock()
        viewModel = DashboardViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadEntities emits success with entities`() = runTest(testDispatcher) {
        val entities = listOf(
            mapOf("name" to "Item1", "type" to "TypeA", "description" to "Desc1"),
            mapOf("name" to "Item2", "type" to "TypeB", "description" to "Desc2")
        )
        val response = DashboardResponse(entities, 2)
        whenever(repository.getDashboard("keypass"))
            .thenReturn(Result.success(response))

        viewModel.loadEntities("keypass")
        advanceUntilIdle()

        val state = viewModel.dashboardState.value
        assertTrue(state is DashboardViewModel.DashboardState.Success)
        assertEquals(2, (state as DashboardViewModel.DashboardState.Success).entities.size)
    }

    @Test
    fun `loadEntities emits error on failure`() = runTest(testDispatcher) {
        whenever(repository.getDashboard("keypass"))
            .thenReturn(Result.failure(RuntimeException("Connection failed")))

        viewModel.loadEntities("keypass")
        advanceUntilIdle()

        val state = viewModel.dashboardState.value
        assertTrue(state is DashboardViewModel.DashboardState.Error)
        assertEquals("Connection failed", (state as DashboardViewModel.DashboardState.Error).message)
    }
}
