package com.example.apiapp.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.apiapp.R

class EntityAdapter(
    private val entities: List<Map<String, String>>,
    private val onItemClick: (Map<String, String>) -> Unit
) : RecyclerView.Adapter<EntityAdapter.EntityViewHolder>() {

    class EntityViewHolder(val contentLayout: LinearLayout) : RecyclerView.ViewHolder(contentLayout.parent as ViewGroup)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntityViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_entity, parent, false)
        val contentLayout = view.findViewById<LinearLayout>(R.id.contentLayout)
        return EntityViewHolder(contentLayout)
    }

    override fun onBindViewHolder(holder: EntityViewHolder, position: Int) {
        val entity = entities[position]
        val layout = holder.contentLayout
        layout.removeAllViews()

        // Display all properties except "description"
        entity.filter { it.key != "description" }.forEach { (key, value) ->
            val textView = TextView(layout.context).apply {
                text = "${formatKey(key)}: $value"
                textSize = 15f
                setPadding(0, 4, 0, 4)
            }
            layout.addView(textView)
        }

        holder.itemView.setOnClickListener { onItemClick(entity) }
    }

    override fun getItemCount(): Int = entities.size

    private fun formatKey(key: String): String {
        return key.replaceFirstChar { it.uppercase() }
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
    }
}
