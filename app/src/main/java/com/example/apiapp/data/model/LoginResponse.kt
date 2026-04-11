package com.example.apiapp.data.model

/**
 * Successful response from `/{location}/auth`.
 *
 * The server returns a single-field JSON object: `{"keypass":"<topic>"}`.
 * That [keypass] is then used as the path segment for the subsequent
 * `GET /dashboard/{keypass}` call — it doubles as both a session token and
 * the topic selector (e.g. "food", "music", "movies").
 */
data class LoginResponse(
    val keypass: String
)
