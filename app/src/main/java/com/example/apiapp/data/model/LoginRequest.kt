package com.example.apiapp.data.model

/**
 * POST body sent to `/{location}/auth`.
 *
 * Per the NIT3213 course update:
 *  - [username] is the student ID, e.g. `s8131175`
 *  - [password] is the student's first name, e.g. `Nickolas`
 */
data class LoginRequest(
    val username: String,
    val password: String
)
