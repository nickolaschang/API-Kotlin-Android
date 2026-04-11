package com.example.apiapp.data.api

import com.example.apiapp.data.model.DashboardResponse
import com.example.apiapp.data.model.LoginRequest
import com.example.apiapp.data.model.LoginResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit interface describing the NIT3213 dummy API.
 *
 * Base URL is configured in [com.example.apiapp.di.AppModule]:
 * `https://nit3213api.onrender.com/`.
 *
 * All methods are suspend functions — Retrofit automatically runs them on
 * its internal I/O thread pool, so callers can invoke them from any
 * coroutine scope without worrying about blocking the main thread.
 */
interface ApiService {

    /**
     * POST `/{location}/auth` — standard login.
     *
     * On success returns the parsed [LoginResponse] (with a keypass).
     * On any HTTP error this throws [retrofit2.HttpException] which the
     * repository catches and converts into a user-friendly message.
     */
    @POST("{location}/auth")
    suspend fun login(
        @Path("location") location: String,
        @Body request: LoginRequest
    ): LoginResponse

    /**
     * POST `/{location}/auth` — same endpoint, but wrapped in a [Response]
     * so the caller can inspect the HTTP status code WITHOUT catching an
     * exception on 4xx/5xx.
     *
     * This is used exclusively by the vuln demo's student ID enumeration,
     * which needs to distinguish `400` (user exists) from `404`
     * (user not found) thousands of times per second without exception
     * overhead.
     */
    @POST("{location}/auth")
    suspend fun loginRaw(
        @Path("location") location: String,
        @Body request: LoginRequest
    ): Response<LoginResponse>

    /**
     * GET `/dashboard/{keypass}` — retrieve the entity list for a topic.
     *
     * Note: this endpoint does NOT require any auth header. The keypass
     * alone is sufficient, which is itself an OWASP API1 (Broken Object
     * Level Authorization) issue that the vuln demo screen highlights.
     */
    @GET("dashboard/{keypass}")
    suspend fun getDashboard(@Path("keypass") keypass: String): DashboardResponse
}
