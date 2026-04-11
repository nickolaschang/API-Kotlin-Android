package com.example.apiapp.ui.details

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.apiapp.R
import com.example.apiapp.ui.dashboard.EntityPresentation
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Detail view for a single entity — launched by tapping a card on the
 * dashboard (or the "Read more →" link).
 *
 * The entity is received as a JSON string in an Intent extra to avoid
 * the overhead of making every entity class Parcelable. After
 * deserialization it's run through [EntityPresentation] to get the
 * same title/subtitle/badge/emoji that the dashboard card displayed,
 * so the visual identity is consistent across screens.
 *
 * Layout is a vertical stack:
 *  - Gradient hero with emoji, title, subtitle, and colored badge
 *  - Two "quick info" cards for the most important secondary fields
 *  - Full description card
 *  - Raw "all properties" table for reference
 */
class DetailsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_details)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        // Deserialize the entity map that the dashboard passed us.
        // `return` on missing extra is defensive — in practice this
        // activity is only ever launched from the adapter which always
        // sets the extra.
        val entityJson = intent.getStringExtra("entity") ?: return
        val type = object : TypeToken<Map<String, String>>() {}.type
        val entity: Map<String, String> = Gson().fromJson(entityJson, type)
        val presentation = EntityPresentation.from(entity, 0)

        findViewById<com.google.android.material.button.MaterialButton>(R.id.backButton)
            .setOnClickListener { finish() }

        findViewById<TextView>(R.id.heroEmoji).text = presentation.emoji
        findViewById<TextView>(R.id.heroTitle).text = presentation.title
        findViewById<TextView>(R.id.heroSubtitle).text = presentation.subtitle

        val heroBadge = findViewById<TextView>(R.id.heroBadge)
        if (presentation.badge != null) {
            heroBadge.visibility = android.view.View.VISIBLE
            heroBadge.text = presentation.badge
            val bgDrawable = ContextCompat.getDrawable(this, R.drawable.bg_chip_rounded)
                ?.mutate() as? GradientDrawable
            bgDrawable?.setColor(ContextCompat.getColor(this, presentation.badgeBgColor))
            heroBadge.background = bgDrawable
            heroBadge.setTextColor(ContextCompat.getColor(this, presentation.badgeFgColor))
        } else {
            heroBadge.visibility = android.view.View.GONE
        }

        // Description
        val descriptionCard = findViewById<MaterialCardView>(R.id.descriptionCard)
        val descriptionText = findViewById<TextView>(R.id.descriptionText)
        val description = entity["description"]
        if (!description.isNullOrBlank()) {
            descriptionCard.visibility = android.view.View.VISIBLE
            descriptionText.text = description
        } else {
            descriptionCard.visibility = android.view.View.GONE
        }

        // Quick info cards for non-title/description fields
        populateQuickInfo(entity, presentation.title)

        // All properties
        populateAllProperties(entity)
    }

    /**
     * Builds two side-by-side "quick info" cards under the hero.
     *
     * Fields are picked heuristically: skip the title field (already
     * shown in the hero), skip the description (has its own card), and
     * skip anything longer than 30 chars to keep the cards compact.
     * Takes the first two matches, so for food you typically get
     * "Origin" and "Main ingredient".
     */
    private fun populateQuickInfo(entity: Map<String, String>, title: String) {
        val row = findViewById<LinearLayout>(R.id.quickInfoRow)
        row.removeAllViews()
        val quickFields = entity.entries
            .filter { it.key != "description" && it.value != title && it.value.length <= 30 }
            .take(2)

        quickFields.forEachIndexed { index, (key, value) ->
            val card = MaterialCardView(this).apply {
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index > 0) marginStart = 12.dp
                }
                layoutParams = lp
                // Theme defaults provide corner radius, elevation, and bg color
            }
            val inner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16.dp, 14.dp, 16.dp, 14.dp)
            }
            val label = TextView(this).apply {
                text = formatKey(key).uppercase()
                setTextAppearance(R.style.TextAppearance_App_Caption)
                textSize = 10f
            }
            val valueView = TextView(this).apply {
                text = value
                setTextAppearance(R.style.TextAppearance_App_CardTitle)
                textSize = 15f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            inner.addView(label)
            inner.addView(valueView)
            card.addView(inner)
            row.addView(card)
        }

        row.visibility = if (quickFields.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }

    /**
     * Dumps every single key/value in the entity as a label/value row.
     * Acts as a fallback "raw view" — the user sees the full untouched
     * API payload regardless of what the hero and quick info cards
     * chose to highlight.
     */
    private fun populateAllProperties(entity: Map<String, String>) {
        val container = findViewById<LinearLayout>(R.id.allPropertiesContainer)
        container.removeAllViews()
        entity.forEach { (key, value) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                setPadding(0, 8.dp, 0, 8.dp)
            }
            val label = TextView(this).apply {
                text = formatKey(key)
                setTextAppearance(R.style.TextAppearance_App_Caption)
                layoutParams = LinearLayout.LayoutParams(100.dp, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            val valueView = TextView(this).apply {
                text = value
                setTextAppearance(R.style.TextAppearance_App_CardSubtitle)
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
            row.addView(label)
            row.addView(valueView)
            container.addView(row)
        }
    }

    /**
     * Converts API camelCase keys into human-readable labels.
     * "dishName" → "Dish Name", "mainIngredient" → "Main Ingredient".
     */
    private fun formatKey(key: String): String {
        return key.replaceFirstChar { it.uppercase() }
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
    }

    /** Convenient extension to express dimensions in dp inline. */
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
