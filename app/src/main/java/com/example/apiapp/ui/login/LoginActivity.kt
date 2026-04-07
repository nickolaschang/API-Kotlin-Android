package com.example.apiapp.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
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
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val usernameInput = findViewById<TextInputEditText>(R.id.usernameInput)
        val passwordInput = findViewById<TextInputEditText>(R.id.passwordInput)
        val loginButton = findViewById<MaterialButton>(R.id.loginButton)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val errorText = findViewById<TextView>(R.id.errorText)

        loginButton.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            viewModel.login(username, password)
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
