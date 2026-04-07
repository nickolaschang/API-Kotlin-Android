package com.example.apiapp.ui.login

import android.content.Intent
import android.os.Bundle
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels()

    private val locations = mapOf(
        "Footscray" to "footscray",
        "Sydney" to "sydney",
        "ORT" to "ort"
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

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, locations.keys.toList())
        locationDropdown.setAdapter(adapter)
        locationDropdown.setText("Sydney", false)

        loginButton.setOnClickListener {
            val selectedLabel = locationDropdown.text.toString()
            val location = locations[selectedLabel]
            if (location == null) {
                errorText.visibility = View.VISIBLE
                errorText.text = "Please select a campus location"
                return@setOnClickListener
            }
            val username = usernameInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            viewModel.login(location, username, password)
        }

        viewModel.loginState.observe(this) { state ->
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
}
