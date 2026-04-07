package com.example.apiapp.ui.dashboard

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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.apiapp.R
import com.example.apiapp.ui.details.DetailsActivity
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DashboardActivity : AppCompatActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dashboard)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        title = "Dashboard"

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val errorText = findViewById<TextView>(R.id.errorText)

        recyclerView.layoutManager = LinearLayoutManager(this)

        viewModel.dashboardState.observe(this) { state ->
            when (state) {
                is DashboardViewModel.DashboardState.Loading -> {
                    progressBar.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                    errorText.visibility = View.GONE
                }
                is DashboardViewModel.DashboardState.Success -> {
                    progressBar.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    recyclerView.adapter = EntityAdapter(state.entities) { entity ->
                        val intent = Intent(this, DetailsActivity::class.java)
                        intent.putExtra("entity", Gson().toJson(entity))
                        startActivity(intent)
                    }
                }
                is DashboardViewModel.DashboardState.Error -> {
                    progressBar.visibility = View.GONE
                    errorText.visibility = View.VISIBLE
                    errorText.text = state.message
                }
            }
        }

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
