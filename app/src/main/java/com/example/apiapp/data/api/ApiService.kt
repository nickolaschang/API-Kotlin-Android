package com.example.apiapp.data.api

import com.example.apiapp.data.model.DashboardResponse
import com.example.apiapp.data.model.LoginRequest
import com.example.apiapp.data.model.LoginResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {

    @POST("sydney/auth")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("dashboard/{keypass}")
    suspend fun getDashboard(@Path("keypass") keypass: String): DashboardResponse
}
