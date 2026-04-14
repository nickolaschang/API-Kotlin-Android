package com.example.apiapp.ui.vulndemo

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.apiapp.R
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint

/**
 * Security audit / OWASP API Top 10 demonstration screen.
 *
 * Purpose of this screen is educational — it shows, live against the
 * NIT3213 dummy API, that:
 *
 *  1. **`GET /dashboard/{topic}` requires no authentication** — tap any
 *     topic chip to fetch real data without a keypass or login.
 *
 *  2. **`POST /{location}/auth` leaks user existence via status codes** —
 *     400 means "user exists, wrong password", 404 means "no such user".
 *     Combined with the lack of rate limiting, this lets you enumerate
 *     every valid student ID in the system by brute force.
 *
 * All the logic lives in [VulnDemoViewModel]; this Activity is purely
 * responsible for binding the two LiveData state machines
 * ([topicState] and [scanState]) to views.
 */
@AndroidEntryPoint
class VulnDemoActivity : AppCompatActivity() {

    private val viewModel: VulnDemoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vuln_demo)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        findViewById<MaterialButton>(R.id.backButton).setOnClickListener { finish() }

        setupTopicChips()
        setupEnumeration()
        observeStates()
    }

    /**
     * Dynamically creates one filter chip per known topic keyword.
     * Tapping a chip fires an unauthenticated GET against that topic's
     * dashboard endpoint.
     */
    private fun setupTopicChips() {
        val chipGroup = findViewById<ChipGroup>(R.id.topicChipGroup)
        viewModel.topics.forEach { topic ->
            val chip = Chip(this).apply {
                text = topic
                isCheckable = true
                setOnClickListener { viewModel.fetchTopic(topic) }
            }
            chipGroup.addView(chip)
        }
    }

    /**
     * Wires up the enumeration scan section.
     *
     * The scan button is a single-button Start/Stop toggle: its
     * behavior depends on whether [VulnDemoViewModel.scanState] is
     * currently in [VulnDemoViewModel.ScanState.Scanning]. The visual
     * state (text + background color) is kept in sync via
     * [setScanButtonMode] inside the state observer.
     */
    private fun setupEnumeration() {
        val scanButton = findViewById<MaterialButton>(R.id.scanButton)
        val scanStartInput = findViewById<TextInputEditText>(R.id.scanStartInput)
        val scanEndInput = findViewById<TextInputEditText>(R.id.scanEndInput)
        val scanResultsLabel = findViewById<TextView>(R.id.scanResultsLabel)
        val scanResultsText = findViewById<TextView>(R.id.scanResultsText)

        scanButton.setOnClickListener {
            // While scanning, the button is a Stop button.
            if (viewModel.scanState.value is VulnDemoViewModel.ScanState.Scanning) {
                viewModel.stopScan()
                return@setOnClickListener
            }

            val startId = scanStartInput.text.toString().trim().toIntOrNull()
            val endId = scanEndInput.text.toString().trim().toIntOrNull()
            if (startId == null) {
                scanStartInput.error = "Enter a valid number"
                return@setOnClickListener
            }
            if (endId == null) {
                scanEndInput.error = "Enter a valid number"
                return@setOnClickListener
            }
            if (startId > endId) {
                scanStartInput.error = "Must be ≤ end"
                return@setOnClickListener
            }
            scanStartInput.error = null
            scanEndInput.error = null
            // Clear any leftover results from a previous run before
            // firing a new scan, otherwise the UI would show stale
            // "Found so far" text during the startup window.
            scanResultsLabel.visibility = View.GONE
            scanResultsText.visibility = View.GONE
            viewModel.scanStudents(startId, endId)
        }
    }

    /**
     * Subscribes to the two LiveData state machines on the ViewModel
     * and updates the matching UI sections on every transition.
     * [topicState] drives the unauthenticated-dashboard section;
     * [scanState] drives the enumeration section.
     */
    private fun observeStates() {
        val topicProgressBar = findViewById<ProgressBar>(R.id.topicProgressBar)
        val topicResultHeader = findViewById<TextView>(R.id.topicResultHeader)
        val topicResultContainer = findViewById<LinearLayout>(R.id.topicResultContainer)
        val topicErrorText = findViewById<TextView>(R.id.topicErrorText)

        viewModel.topicState.observe(this) { state ->
            when (state) {
                is VulnDemoViewModel.TopicState.Idle -> {
                    topicProgressBar.visibility = View.GONE
                    topicResultHeader.visibility = View.GONE
                    topicResultContainer.visibility = View.GONE
                    topicErrorText.visibility = View.GONE
                }
                is VulnDemoViewModel.TopicState.Loading -> {
                    topicProgressBar.visibility = View.VISIBLE
                    topicResultHeader.visibility = View.GONE
                    topicResultContainer.visibility = View.GONE
                    topicErrorText.visibility = View.GONE
                }
                is VulnDemoViewModel.TopicState.Success -> {
                    topicProgressBar.visibility = View.GONE
                    topicErrorText.visibility = View.GONE
                    topicResultHeader.visibility = View.VISIBLE
                    topicResultHeader.text = "/${state.topic} — ${state.entities.size} of ${state.total} entities (no auth token used)"
                    topicResultContainer.visibility = View.VISIBLE
                    populateTopicResults(topicResultContainer, state.entities)
                }
                is VulnDemoViewModel.TopicState.Error -> {
                    topicProgressBar.visibility = View.GONE
                    topicResultHeader.visibility = View.GONE
                    topicResultContainer.visibility = View.GONE
                    topicErrorText.visibility = View.VISIBLE
                    topicErrorText.text = state.message
                }
            }
        }

        val scanProgressContainer = findViewById<View>(R.id.scanProgressContainer)
        val scanProgressText = findViewById<TextView>(R.id.scanProgressText)
        val scanResultsLabel = findViewById<TextView>(R.id.scanResultsLabel)
        val scanResultsText = findViewById<TextView>(R.id.scanResultsText)
        val scanButton = findViewById<MaterialButton>(R.id.scanButton)

        viewModel.scanState.observe(this) { state ->
            when (state) {
                is VulnDemoViewModel.ScanState.Idle -> {
                    scanProgressContainer.visibility = View.GONE
                    scanResultsLabel.visibility = View.GONE
                    scanResultsText.visibility = View.GONE
                    setScanButtonMode(scanButton, scanning = false)
                }
                is VulnDemoViewModel.ScanState.Scanning -> {
                    scanProgressContainer.visibility = View.VISIBLE
                    scanProgressText.text = "Scanned: ${state.scanned}  •  Found: ${state.found.size}"
                    setScanButtonMode(scanButton, scanning = true)
                    if (state.found.isNotEmpty()) {
                        scanResultsLabel.visibility = View.VISIBLE
                        scanResultsLabel.text = "Found so far (${state.found.size})"
                        scanResultsText.visibility = View.VISIBLE
                        scanResultsText.text = state.found.joinToString("\n")
                    }
                }
                is VulnDemoViewModel.ScanState.Done -> {
                    scanProgressContainer.visibility = View.GONE
                    setScanButtonMode(scanButton, scanning = false)
                    scanResultsLabel.visibility = View.VISIBLE
                    if (state.found.isEmpty()) {
                        scanResultsLabel.text = "Scan stopped — no valid IDs found in ${state.totalScanned} probes"
                        scanResultsText.visibility = View.GONE
                    } else {
                        scanResultsLabel.text = "Students that fetched the API — ${state.found.size} found from ${state.totalScanned} probed"
                        scanResultsText.visibility = View.VISIBLE
                        scanResultsText.text = state.found.joinToString("\n")
                    }
                }
                is VulnDemoViewModel.ScanState.Error -> {
                    scanProgressContainer.visibility = View.GONE
                    setScanButtonMode(scanButton, scanning = false)
                    scanResultsLabel.visibility = View.VISIBLE
                    scanResultsLabel.text = "Error: ${state.message}"
                    scanResultsText.visibility = View.GONE
                }
            }
        }
    }

    /**
     * Flips the scan button between "Start" (orange primary) and
     * "Stop" (red destructive) visual modes. Called from the
     * [scanState] observer on every transition so the button text and
     * color always match reality — even if the state changes for a
     * reason other than the user tapping the button.
     */
    private fun setScanButtonMode(button: MaterialButton, scanning: Boolean) {
        if (scanning) {
            button.text = "⏹  Stop Scan"
            button.backgroundTintList = androidx.core.content.ContextCompat.getColorStateList(
                this, android.R.color.holo_red_dark
            )
        } else {
            button.text = "Start Enumeration Scan"
            button.backgroundTintList = androidx.core.content.ContextCompat.getColorStateList(
                this, R.color.brand_primary
            )
        }
    }

    /**
     * Renders the first 5 entities of a fetched topic as compact
     * outlined cards inside the main topic card. Any remaining
     * entities are summarized with a "... and N more" line to keep
     * the screen from scrolling forever on large datasets.
     */
    private fun populateTopicResults(container: LinearLayout, entities: List<Map<String, String>>) {
        container.removeAllViews()
        entities.take(5).forEach { entity ->
            val cardView = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 8.dpToPx()
                    leftMargin = 0
                    rightMargin = 0
                }
                radius = 14.dpToPx().toFloat()
                cardElevation = 0f
                strokeWidth = 1.dpToPx()
                strokeColor = androidx.core.content.ContextCompat.getColor(context, R.color.divider)
            }
            val inner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(14.dpToPx(), 12.dpToPx(), 14.dpToPx(), 12.dpToPx())
            }
            entity.entries.take(4).forEach { (key, value) ->
                val tv = TextView(this).apply {
                    text = "$key: $value"
                    setTextAppearance(R.style.TextAppearance_App_Caption)
                    if (key == "description") {
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                }
                inner.addView(tv)
            }
            cardView.addView(inner)
            container.addView(cardView)
        }
        if (entities.size > 5) {
            val more = TextView(this).apply {
                text = "... and ${entities.size - 5} more"
                setTextAppearance(R.style.TextAppearance_App_Caption)
                setPadding(4.dpToPx(), 0, 0, 0)
            }
            container.addView(more)
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
