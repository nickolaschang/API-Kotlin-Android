package com.example.apiapp.data.model

/**
 * Response from `GET /dashboard/{keypass}`.
 *
 * The API returns a list of entities whose field names vary per topic
 * (food has `dishName`/`origin`/`mainIngredient`/`mealType`/`description`,
 * animals has different keys, etc.). Because the schema is not fixed, each
 * entity is modelled as a generic [Map] of field name → value.
 *
 * [EntityPresentation][com.example.apiapp.ui.dashboard.EntityPresentation]
 * is responsible for inspecting these dynamic keys and picking the best
 * ones to use as title / subtitle / badge at render time.
 */
data class DashboardResponse(
    val entities: List<Map<String, String>>,
    val entityTotal: Int
)
