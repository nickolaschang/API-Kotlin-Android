package com.example.apiapp.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.apiapp.data.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for [com.example.apiapp.ui.dashboard.DashboardActivity].
 *
 * Holds the full entity list fetched from the API plus the user-driven
 * filter state (search query + active category), and exposes the derived
 * filtered list as LiveData. Also computes header statistics (total,
 * cuisine count, meal type count) and the available filter categories.
 *
 * Because the API schema is dynamic (see [com.example.apiapp.data.model.DashboardResponse]),
 * all the category and stats detection works by inspecting the keys of
 * the first entity at runtime rather than assuming a fixed shape.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: AppRepository
) : ViewModel() {

    private val _dashboardState = MutableLiveData<DashboardState>()
    val dashboardState: LiveData<DashboardState> = _dashboardState

    // Filter inputs — held as plain fields (not LiveData) because only the
    // derived [filteredEntities] output needs to be observed by the UI.
    private var searchQuery: String = ""
    private var activeFilter: String? = null
    private var allEntities: List<Map<String, String>> = emptyList()

    /** Post-filter entity list. The Activity renders this directly. */
    private val _filteredEntities = MutableLiveData<List<Map<String, String>>>(emptyList())
    val filteredEntities: LiveData<List<Map<String, String>>> = _filteredEntities

    /** Categories that populate the filter chip row (includes "All" at index 0). */
    private val _filterCategories = MutableLiveData<List<String>>(emptyList())
    val filterCategories: LiveData<List<String>> = _filterCategories

    /** Stats shown in the gradient header (counts + dynamic labels). */
    private val _stats = MutableLiveData(Stats(0, 0, 0, "Categories", "Types"))
    val stats: LiveData<Stats> = _stats

    /** Bypasses the API and seeds the dashboard with a hardcoded list (used by "Demo Mode"). */
    fun setDemoEntities(entities: List<Map<String, String>>) {
        _dashboardState.value = DashboardState.Success
        onEntitiesLoaded(entities)
    }

    /** Normal entry point: calls `/dashboard/{keypass}` and feeds the result into the state machine. */
    fun loadEntities(keypass: String) {
        _dashboardState.value = DashboardState.Loading
        viewModelScope.launch {
            val result = repository.getDashboard(keypass)
            result.onSuccess { response ->
                _dashboardState.value = DashboardState.Success
                onEntitiesLoaded(response.entities)
            }.onFailure { error ->
                _dashboardState.value = DashboardState.Error(
                    error.message ?: "Failed to load data"
                )
            }
        }
    }

    /** Called by the Activity on every text change in the search bar. */
    fun setSearchQuery(query: String) {
        searchQuery = query
        recompute()
    }

    /** Called when the user taps a filter chip. `null` / "All" means no filter. */
    fun setFilter(filter: String?) {
        activeFilter = filter
        recompute()
    }

    /** Shared initialization after entities become available from either source. */
    private fun onEntitiesLoaded(entities: List<Map<String, String>>) {
        allEntities = entities
        _filterCategories.value = computeCategories(entities)
        _stats.value = computeStats(entities)
        recompute()
    }

    /**
     * Applies the current search query + category filter to [allEntities]
     * and publishes the result to [_filteredEntities].
     *
     * Search matches against any field value (case-insensitive substring).
     * Filter matches against the auto-detected category key exactly.
     */
    private fun recompute() {
        val query = searchQuery.trim().lowercase()
        val filter = activeFilter

        val categoryKey = detectBadgeKey(allEntities)

        val filtered = allEntities.filter { entity ->
            val matchesQuery = query.isEmpty() || entity.values.any { v ->
                v.lowercase().contains(query)
            }
            val matchesFilter = filter == null || filter == "All" ||
                (categoryKey != null && entity[categoryKey]?.equals(filter, ignoreCase = true) == true)
            matchesQuery && matchesFilter
        }
        _filteredEntities.value = filtered
    }

    /**
     * Builds the list of filter chips to show under the search bar.
     *
     * If the dataset has no category key or only a single unique value,
     * we return an empty list so the Activity hides the chip row
     * entirely (no point showing a filter with only one option).
     */
    private fun computeCategories(entities: List<Map<String, String>>): List<String> {
        val key = detectBadgeKey(entities) ?: return emptyList()
        val unique = entities.mapNotNull { it[key] }.distinct().sorted()
        return if (unique.size <= 1) emptyList() else listOf("All") + unique
    }

    /**
     * Computes the three header stats shown over the gradient.
     *
     * The labels adapt to the topic: for `food` the "origin" field
     * becomes "Cuisines", for `travel` the "location" field becomes
     * "Places", and so on. This keeps the header meaningful without
     * hardcoding any one topic.
     */
    private fun computeStats(entities: List<Map<String, String>>): Stats {
        val total = entities.size
        val originKey = entities.firstOrNull()?.keys?.firstOrNull { k ->
            val l = k.lowercase()
            l == "origin" || l == "country" || l == "location" || l == "region" || l == "habitat"
        }
        val categoryKey = detectBadgeKey(entities)

        val originCount = originKey?.let { k ->
            entities.mapNotNull { it[k] }.distinct().size
        } ?: 0
        val categoryCount = categoryKey?.let { k ->
            entities.mapNotNull { it[k] }.distinct().size
        } ?: 0

        val originLabel = when (originKey?.lowercase()) {
            "origin" -> "Cuisines"
            "country" -> "Countries"
            "location" -> "Places"
            "region" -> "Regions"
            "habitat" -> "Habitats"
            else -> "Variants"
        }
        val categoryLabel = when (categoryKey?.lowercase()) {
            "mealtype" -> "Meal types"
            "type" -> "Types"
            "category" -> "Categories"
            "class" -> "Classes"
            "genre" -> "Genres"
            else -> "Types"
        }

        return Stats(total, originCount, categoryCount, originLabel, categoryLabel)
    }

    /**
     * Picks the "categorical" field used for both the badge on each card
     * and the filter chips. Scans a preferred list in order — mealType
     * wins for food, type/category/class/genre match the other topics.
     */
    private fun detectBadgeKey(entities: List<Map<String, String>>): String? {
        val first = entities.firstOrNull() ?: return null
        val preferred = listOf("mealtype", "type", "category", "class", "genre", "status")
        for (p in preferred) {
            val match = first.keys.firstOrNull { it.lowercase() == p }
            if (match != null) return match
        }
        return null
    }

    /**
     * Header stats payload. The label fields are pre-computed at load
     * time so the View doesn't need to do topic-aware text switching.
     */
    data class Stats(
        val total: Int,
        val originCount: Int,
        val categoryCount: Int,
        val originLabel: String,
        val categoryLabel: String
    )

    /**
     * Top-level state of the dashboard screen. Note that we no longer
     * carry the entity list in [Success] — the filtered list lives in a
     * separate LiveData so filter changes don't have to re-emit the
     * whole state.
     */
    sealed class DashboardState {
        data object Loading : DashboardState()
        data object Success : DashboardState()
        data class Error(val message: String) : DashboardState()
    }
}
