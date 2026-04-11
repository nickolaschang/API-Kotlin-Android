package com.example.apiapp.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.apiapp.R
import com.example.apiapp.ui.details.DetailsActivity
import com.example.apiapp.ui.vulndemo.VulnDemoActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main data-browsing screen.
 *
 * Displays the entity list returned by `/dashboard/{keypass}` as a
 * beautiful card list with:
 *  - gradient header + hero stats (total / cuisines / meal types)
 *  - live search bar (filters as the user types)
 *  - auto-generated category filter chips
 *  - empty state + error state
 *
 * Most of the heavy lifting (filtering, stats computation, category
 * detection) is delegated to [DashboardViewModel]; this Activity is
 * intentionally thin — it just wires views to LiveData observers.
 *
 * Can be launched two ways:
 *  - With `keypass` extra → fetches live data from the API
 *  - With `demoEntities` extra → renders a hardcoded JSON list
 *    (used by the "Demo Mode" button on the login screen)
 */
@AndroidEntryPoint
class DashboardActivity : AppCompatActivity() {

    private val viewModel: DashboardViewModel by viewModels()
    private lateinit var adapter: EntityAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        setupRecyclerView()
        setupHeader()
        setupSearch()
        setupFilterChips()
        observeViewModel()
        loadData()
    }

    /**
     * Configures the RecyclerView and its adapter. The item click
     * callback serializes the selected entity to JSON and hands it off
     * to [DetailsActivity] via an Intent extra — using Gson here avoids
     * needing the entity class to be Parcelable.
     */
    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = EntityAdapter(emptyList()) { entity ->
            val intent = Intent(this, DetailsActivity::class.java)
            intent.putExtra("entity", Gson().toJson(entity))
            startActivity(intent)
        }
        recyclerView.adapter = adapter
    }

    /**
     * Sets the greeting and the screen title. The title adapts to the
     * current keypass topic so the same dashboard screen can render
     * any topic with an appropriate heading (e.g. "Music Library" vs
     * "Culinary Explorer").
     */
    private fun setupHeader() {
        val greetingText = findViewById<TextView>(R.id.greetingText)
        val titleHeader = findViewById<TextView>(R.id.titleHeader)
        val topic = intent.getStringExtra("topic")

        greetingText.text = "Welcome back, Nickolas"
        titleHeader.text = when (topic?.lowercase()) {
            "food", null -> "Culinary Explorer"
            "music" -> "Music Library"
            "movies" -> "Film Archive"
            "books" -> "Book Collection"
            "travel" -> "Travel Diary"
            "animals" -> "Wildlife Guide"
            "science" -> "Science Hub"
            "history" -> "History Explorer"
            else -> topic.replaceFirstChar { it.uppercase() } + " Explorer"
        }

        findViewById<MaterialButton>(R.id.vulnDemoButton).setOnClickListener {
            startActivity(Intent(this, VulnDemoActivity::class.java))
        }
    }

    /**
     * Wires the search bar to the ViewModel. Every keystroke pushes
     * the current text into [DashboardViewModel.setSearchQuery], which
     * triggers a filter recompute. The "clear" (×) icon appears as
     * soon as there's any text to clear.
     */
    private fun setupSearch() {
        val searchInput = findViewById<EditText>(R.id.searchInput)
        val clearIcon = findViewById<ImageView>(R.id.clearSearchIcon)

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString().orEmpty()
                viewModel.setSearchQuery(query)
                clearIcon.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
            }
        })

        clearIcon.setOnClickListener {
            searchInput.setText("")
        }
    }

    /**
     * Builds the filter chip row from the ViewModel's category list.
     * The list is rebuilt from scratch on every emission so that
     * switching between datasets (e.g. food → travel) produces the
     * correct chips. Hidden entirely when there's only one category
     * or no category key at all.
     */
    private fun setupFilterChips() {
        val chipGroup = findViewById<ChipGroup>(R.id.filterChipGroup)

        viewModel.filterCategories.observe(this) { categories ->
            chipGroup.removeAllViews()
            if (categories.isEmpty()) {
                findViewById<View>(R.id.filterScroll).visibility = View.GONE
                return@observe
            }
            findViewById<View>(R.id.filterScroll).visibility = View.VISIBLE
            categories.forEachIndexed { index, category ->
                val chip = Chip(this).apply {
                    text = category
                    isCheckable = true
                    isCheckedIconVisible = false
                    isChecked = index == 0 // "All" pre-selected
                    setChipBackgroundColorResource(R.color.card_bg)
                    setOnClickListener {
                        // "All" is a UI affordance — internally it means "no filter"
                        viewModel.setFilter(if (category == "All") null else category)
                    }
                }
                chipGroup.addView(chip)
            }
        }
    }

    /**
     * Subscribes to every LiveData the ViewModel exposes.
     *
     * Note the split between [DashboardViewModel.dashboardState] and
     * [DashboardViewModel.filteredEntities] — the first controls the
     * top-level visibility (loading / error / empty / success), while
     * the second updates the adapter's data every time the user types
     * in the search box or taps a filter chip, without re-triggering
     * the loading spinner.
     */
    private fun observeViewModel() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val errorText = findViewById<TextView>(R.id.errorText)
        val emptyState = findViewById<LinearLayout>(R.id.emptyState)

        viewModel.dashboardState.observe(this) { state ->
            when (state) {
                is DashboardViewModel.DashboardState.Loading -> {
                    progressBar.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                    errorText.visibility = View.GONE
                    emptyState.visibility = View.GONE
                }
                is DashboardViewModel.DashboardState.Success -> {
                    progressBar.visibility = View.GONE
                    errorText.visibility = View.GONE
                }
                is DashboardViewModel.DashboardState.Error -> {
                    progressBar.visibility = View.GONE
                    recyclerView.visibility = View.GONE
                    emptyState.visibility = View.GONE
                    errorText.visibility = View.VISIBLE
                    errorText.text = state.message
                }
            }
        }

        viewModel.filteredEntities.observe(this) { entities ->
            adapter.updateEntities(entities)
            if (viewModel.dashboardState.value is DashboardViewModel.DashboardState.Success) {
                if (entities.isEmpty()) {
                    recyclerView.visibility = View.GONE
                    emptyState.visibility = View.VISIBLE
                } else {
                    recyclerView.visibility = View.VISIBLE
                    emptyState.visibility = View.GONE
                }
            }
        }

        viewModel.stats.observe(this) { stats ->
            findViewById<TextView>(R.id.statTotal).text = stats.total.toString()
            findViewById<TextView>(R.id.statCategories).text = stats.originCount.toString()
            findViewById<TextView>(R.id.statCategoriesLabel).text = stats.originLabel
            findViewById<TextView>(R.id.statTypes).text = stats.categoryCount.toString()
            findViewById<TextView>(R.id.statTypesLabel).text = stats.categoryLabel
        }
    }

    /**
     * Decides how to populate the dashboard based on Intent extras.
     *
     * If we were launched with a `demoEntities` extra (from the Demo
     * Mode button on login), we deserialize that JSON list and hand it
     * to the ViewModel without hitting the API. Otherwise we pull the
     * keypass from the Intent and do a normal fetch.
     */
    private fun loadData() {
        val demoJson = intent.getStringExtra("demoEntities")
        if (demoJson != null) {
            val type = object : com.google.gson.reflect.TypeToken<List<Map<String, String>>>() {}.type
            val entities: List<Map<String, String>> = Gson().fromJson(demoJson, type)
            viewModel.setDemoEntities(entities)
        } else {
            val keypass = intent.getStringExtra("keypass") ?: ""
            viewModel.loadEntities(keypass)
        }
    }
}
