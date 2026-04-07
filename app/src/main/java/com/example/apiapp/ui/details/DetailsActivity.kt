package com.example.apiapp.ui.details

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.apiapp.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class DetailsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_details)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        title = "Details"

        val detailsLayout = findViewById<LinearLayout>(R.id.detailsLayout)
        val entityJson = intent.getStringExtra("entity") ?: return

        val type = object : TypeToken<Map<String, String>>() {}.type
        val entity: Map<String, String> = Gson().fromJson(entityJson, type)

        // Display all properties (non-description first, then description last)
        entity.filter { it.key != "description" }.forEach { (key, value) ->
            addDetailRow(detailsLayout, formatKey(key), value)
        }

        // Show description at the bottom with more emphasis
        entity["description"]?.let { description ->
            addSeparator(detailsLayout)
            addDetailRow(detailsLayout, "Description", description, isDescription = true)
        }
    }

    private fun addDetailRow(
        layout: LinearLayout,
        label: String,
        value: String,
        isDescription: Boolean = false
    ) {
        val labelView = TextView(this).apply {
            text = label
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 16, 0, 4)
        }
        layout.addView(labelView)

        val valueView = TextView(this).apply {
            text = value
            textSize = if (isDescription) 16f else 18f
            setPadding(0, 0, 0, 8)
        }
        layout.addView(valueView)
    }

    private fun addSeparator(layout: LinearLayout) {
        val separator = android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2
            ).apply { setMargins(0, 16, 0, 8) }
            setBackgroundColor(0xFFDDDDDD.toInt())
        }
        layout.addView(separator)
    }

    private fun formatKey(key: String): String {
        return key.replaceFirstChar { it.uppercase() }
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
    }
}
