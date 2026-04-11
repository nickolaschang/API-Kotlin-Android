package com.example.apiapp.data.repository

import com.example.apiapp.data.api.ApiService
import com.example.apiapp.data.model.DashboardResponse
import com.example.apiapp.data.model.LoginRequest
import com.example.apiapp.data.model.LoginResponse
import retrofit2.HttpException
import javax.inject.Inject

/**
 * Result of probing a student ID against the auth endpoint.
 *
 * The API leaks user existence through the HTTP status code — see
 * [AppRepository.enumerateStudent] for the full mapping.
 */
enum class StudentStatus { EXISTS, NOT_FOUND, LOGGED_IN, UNKNOWN, ERROR }

/**
 * Single data-access façade over [ApiService].
 *
 * All methods return [Result] so the ViewModels can handle success and
 * failure declaratively without dealing with exception types directly.
 * This is also where raw HTTP error bodies and status codes are turned
 * into user-friendly messages.
 *
 * Injected as a Hilt singleton via
 * [com.example.apiapp.di.AppModule.provideAppRepository].
 */
class AppRepository @Inject constructor(
    private val apiService: ApiService
) {

    /**
     * Authenticate against `/{location}/auth`.
     *
     * The server returns very sparse error bodies, so we fall back to
     * status-code-based messages when the body is empty:
     *  - 401/404 → "Invalid username or password" (the API uses 404 when
     *    the student ID doesn't exist, and some variants return 401)
     *  - 403    → "Access denied"
     *  - 5xx    → "Server error, try again later"
     */
    suspend fun login(location: String, username: String, password: String): Result<LoginResponse> {
        return try {
            val response = apiService.login(location, LoginRequest(username, password))
            Result.success(response)
        } catch (e: HttpException) {
            val body = e.response()?.errorBody()?.string().orEmpty()
            val message = when {
                body.isNotBlank() -> body
                e.code() == 404 -> "Invalid username or password."
                e.code() == 401 -> "Invalid username or password."
                e.code() == 403 -> "Access denied."
                e.code() in 500..599 -> "Server error (${e.code()}). Try again later."
                else -> "Login failed (${e.code()})"
            }
            Result.failure(Exception(message))
        } catch (e: Exception) {
            // Typically a network / socket timeout from the Render cold start
            Result.failure(Exception(e.message ?: "Network error. Check your connection."))
        }
    }

    /**
     * Probe a single student ID for existence WITHOUT triggering an
     * exception on HTTP error. Used by the vuln demo's enumeration scan.
     *
     * Status code → meaning mapping (determined empirically against the
     * NIT3213 dummy API on `/sydney/auth`):
     *  - `200` → credentials were actually correct (should never happen
     *    here since we pass password "x", but handled for safety)
     *  - `400` → student ID EXISTS, wrong password → [StudentStatus.EXISTS]
     *  - `404` → no such student ID → [StudentStatus.NOT_FOUND]
     *  - anything else → [StudentStatus.UNKNOWN]
     */
    suspend fun enumerateStudent(studentId: String): StudentStatus {
        return try {
            val response = apiService.loginRaw("sydney", LoginRequest(studentId, "x"))
            when (response.code()) {
                200 -> StudentStatus.LOGGED_IN
                400 -> StudentStatus.EXISTS
                404 -> StudentStatus.NOT_FOUND
                else -> StudentStatus.UNKNOWN
            }
        } catch (e: Exception) {
            StudentStatus.ERROR
        }
    }

    /**
     * Fetch the dashboard entity list for a given keypass/topic.
     *
     * See [ApiService.getDashboard] — this endpoint is unauthenticated, so
     * any keypass (food, music, movies, …) works without needing a prior
     * login call. The vuln demo exploits this directly.
     */
    suspend fun getDashboard(keypass: String): Result<DashboardResponse> {
        return try {
            val response = apiService.getDashboard(keypass)
            Result.success(response)
        } catch (e: HttpException) {
            val body = e.response()?.errorBody()?.string().orEmpty()
            val message = when {
                body.isNotBlank() -> body
                e.code() == 404 -> "Dashboard not found (404)."
                e.code() in 500..599 -> "Server error (${e.code()}). Try again later."
                else -> "Request failed (${e.code()})"
            }
            Result.failure(Exception(message))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Network error. Check your connection."))
        }
    }
}
