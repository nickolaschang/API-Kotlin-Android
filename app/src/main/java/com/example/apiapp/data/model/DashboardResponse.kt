package com.example.apiapp.data.model

data class DashboardResponse(
    val entities: List<Map<String, String>>,
    val entityTotal: Int
)
