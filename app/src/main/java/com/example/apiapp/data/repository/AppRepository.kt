package com.example.apiapp.data.repository

import com.example.apiapp.data.api.ApiService
import com.example.apiapp.data.model.DashboardResponse
import com.example.apiapp.data.model.LoginRequest
import com.example.apiapp.data.model.LoginResponse
import javax.inject.Inject

class AppRepository @Inject constructor(
    private val apiService: ApiService
) {

    suspend fun login(username: String, password: String): Result<LoginResponse> {
        return try {
            val response = apiService.login(LoginRequest(username, password))
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDashboard(keypass: String): Result<DashboardResponse> {
        return try {
            val response = apiService.getDashboard(keypass)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
