package com.example.apiapp.ui.login

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.apiapp.R
import com.example.apiapp.ui.dashboard.DashboardActivity
import com.example.apiapp.ui.vulndemo.VulnDemoActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint

/**
 * Entry point activity — the screen users land on when the app starts.
 *
 * Responsibilities:
 *  - Render the gradient hero + sign-in card
 *  - Collect campus / student ID / first name from the user
 *  - Delegate validation + the network call to [LoginViewModel]
 *  - Navigate to [DashboardActivity] on success (passing the keypass)
 *
 * Also exposes two secondary entry points:
 *  - "Demo Mode" → opens Dashboard with a hardcoded list of entities,
 *    useful for showing the UI without hitting the (slow) Render API
 *  - "Vuln Demo" → opens [VulnDemoActivity] which tests OWASP API Top 10
 *    vulnerabilities against the dummy API
 *
 * `@AndroidEntryPoint` lets Hilt inject the ViewModel at creation time.
 */
@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels()

    // Display label → API path segment. The dropdown shows the keys,
    // the login call uses the value. ORT was removed because
    // `/ort/auth` returns 404 on the real API.
    private val locations = mapOf(
        "Footscray" to "footscray",
        "Sydney" to "sydney"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Top-right "Vuln Demo" button → bypasses login entirely and opens
        // the security audit screen. The whole point of that screen is to
        // show that you don't need to authenticate, so it's accessible
        // from here without credentials.
        val vulnDemoButton = findViewById<MaterialButton>(R.id.vulnDemoButton)
        vulnDemoButton.setOnClickListener {
            startActivity(Intent(this, VulnDemoActivity::class.java))
        }

        // "Demo Mode" button — hands the dashboard a hardcoded JSON list of
        // entities so the UI can be demoed even when the Render API is
        // cold-booting or the network is flaky. Not used in production flow.
        val demoButton = findViewById<MaterialButton>(R.id.demoButton)
        demoButton.setOnClickListener {
            val demoEntities = listOf(
                mapOf(
                    "name" to "Great Barrier Reef",
                    "location" to "Queensland, Australia",
                    "type" to "Coral Reef System",
                    "description" to "The Great Barrier Reef is the world's largest coral reef system, stretching over 2,300 kilometres. It is composed of over 2,900 individual reef systems and hundreds of islands. The reef is home to a wide diversity of life, including many vulnerable and endangered species."
                ),
                mapOf(
                    "name" to "Amazon Rainforest",
                    "location" to "South America",
                    "type" to "Tropical Rainforest",
                    "description" to "The Amazon Rainforest covers over 5.5 million square kilometres and represents over half of the planet's remaining rainforests. It is the most biodiverse tropical rainforest in the world, home to around 10% of all species on Earth."
                ),
                mapOf(
                    "name" to "Mount Everest",
                    "location" to "Nepal/Tibet",
                    "type" to "Mountain",
                    "description" to "Mount Everest is Earth's highest mountain above sea level, located in the Mahalangur Himal sub-range of the Himalayas. Its summit is 8,849 metres above sea level. It attracts many climbers, including highly experienced mountaineers."
                ),
                mapOf(
                    "name" to "Sahara Desert",
                    "location" to "Northern Africa",
                    "type" to "Hot Desert",
                    "description" to "The Sahara is the largest hot desert in the world and the third-largest desert overall. It covers most of North Africa, spanning over 9 million square kilometres. Temperatures can exceed 50 degrees Celsius during the hottest months."
                ),
                mapOf(
                    "name" to "Mariana Trench",
                    "location" to "Western Pacific Ocean",
                    "type" to "Oceanic Trench",
                    "description" to "The Mariana Trench is the deepest oceanic trench on Earth, reaching a maximum known depth of about 11,034 metres at the Challenger Deep. It is located in the western Pacific Ocean, east of the Mariana Islands."
                )
            )
            val intent = Intent(this, DashboardActivity::class.java)
            intent.putExtra("demoEntities", Gson().toJson(demoEntities))
            startActivity(intent)
        }

        val locationDropdown = findViewById<AutoCompleteTextView>(R.id.locationDropdown)
        val usernameInput = findViewById<TextInputEditText>(R.id.usernameInput)
        val passwordInput = findViewById<TextInputEditText>(R.id.passwordInput)
        val loginButton = findViewById<MaterialButton>(R.id.loginButton)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val errorText = findViewById<TextView>(R.id.errorText)

        // Populate the campus dropdown and pre-select Sydney. The `false`
        // argument to setText suppresses the autocomplete filter —
        // otherwise it would try to filter the adapter to entries
        // matching "Sydney" on load, which collapses the list to one item.
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, locations.keys.toList())
        locationDropdown.setAdapter(adapter)
        locationDropdown.setText("Sydney", false)

        loginButton.setOnClickListener {
            val selectedLabel = locationDropdown.text.toString()
            val location = locations[selectedLabel]
            val username = usernameInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            Log.d(TAG, "Login tapped — label='$selectedLabel', location='$location', user='$username', passLen=${password.length}")
            if (location == null) {
                errorText.visibility = View.VISIBLE
                errorText.text = "Please select a campus location"
                return@setOnClickListener
            }
            if (username.isBlank() || password.isBlank()) {
                errorText.visibility = View.VISIBLE
                errorText.text = "Enter your student ID and first name"
                return@setOnClickListener
            }
            viewModel.login(location, username, password)
        }

        // Observer driven by LiveData from the ViewModel. Every time the
        // login state transitions we update the UI accordingly. On Success
        // we navigate to the dashboard passing the keypass, then finish()
        // so the back button takes the user out of the app rather than
        // back to the login form with stale state.
        viewModel.loginState.observe(this) { state ->
            Log.d(TAG, "loginState=$state")
            when (state) {
                is LoginViewModel.LoginState.Loading -> {
                    progressBar.visibility = View.VISIBLE
                    errorText.visibility = View.GONE
                    loginButton.isEnabled = false
                }
                is LoginViewModel.LoginState.Success -> {
                    progressBar.visibility = View.GONE
                    val intent = Intent(this, DashboardActivity::class.java)
                    intent.putExtra("keypass", state.keypass)
                    startActivity(intent)
                    finish()
                }
                is LoginViewModel.LoginState.Error -> {
                    progressBar.visibility = View.GONE
                    errorText.visibility = View.VISIBLE
                    errorText.text = state.message
                    loginButton.isEnabled = true
                }
            }
        }
    }

    companion object {
        private const val TAG = "LoginActivity"
    }
}
