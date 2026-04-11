package com.example.apiapp.ui.dashboard

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.apiapp.R

/**
 * RecyclerView adapter for the dashboard's entity list.
 *
 * Each item is rendered from a generic `Map<String, String>` whose keys
 * vary per topic (food has `dishName`/`origin`/…, animals has different
 * keys, etc.). The heavy lifting of picking which fields to show as
 * title / subtitle / badge / emoji lives in [EntityPresentation];
 * this adapter just binds the presentation to the row views.
 *
 * [onItemClick] fires both when the card itself is tapped and when the
 * "Read more →" link is tapped — both navigate to the details screen.
 */
class EntityAdapter(
    private var entities: List<Map<String, String>>,
    private val onItemClick: (Map<String, String>) -> Unit
) : RecyclerView.Adapter<EntityAdapter.EntityViewHolder>() {

    /** Holds references to the views inside a single entity card. */
    class EntityViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatarCircle: FrameLayout = view.findViewById(R.id.avatarCircle)
        val avatarEmoji: TextView = view.findViewById(R.id.avatarEmoji)
        val titleText: TextView = view.findViewById(R.id.titleText)
        val subtitleText: TextView = view.findViewById(R.id.subtitleText)
        val badgeChip: TextView = view.findViewById(R.id.badgeChip)
        val descriptionPreview: TextView = view.findViewById(R.id.descriptionPreview)
        val readMoreLink: TextView = view.findViewById(R.id.readMoreLink)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntityViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_entity, parent, false)
        return EntityViewHolder(view)
    }

    override fun onBindViewHolder(holder: EntityViewHolder, position: Int) {
        val entity = entities[position]
        val presentation = EntityPresentation.from(entity, position)
        val context = holder.itemView.context

        holder.titleText.text = presentation.title
        holder.subtitleText.text = presentation.subtitle
        holder.avatarEmoji.text = presentation.emoji
        holder.descriptionPreview.text = presentation.descriptionPreview

        // Tint the avatar circle background (fresh drawable each bind to avoid recycling leaks)
        val avatarBg = ContextCompat.getDrawable(context, R.drawable.bg_avatar_circle)
            ?.mutate() as? GradientDrawable
        avatarBg?.setColor(ContextCompat.getColor(context, presentation.avatarColorRes))
        holder.avatarCircle.background = avatarBg

        // Badge
        if (presentation.badge != null) {
            holder.badgeChip.visibility = View.VISIBLE
            holder.badgeChip.text = presentation.badge
            val badgeBg = ContextCompat.getDrawable(context, R.drawable.bg_chip_rounded)
                ?.mutate() as? GradientDrawable
            badgeBg?.setColor(ContextCompat.getColor(context, presentation.badgeBgColor))
            holder.badgeChip.background = badgeBg
            holder.badgeChip.setTextColor(ContextCompat.getColor(context, presentation.badgeFgColor))
        } else {
            holder.badgeChip.visibility = View.GONE
        }

        if (presentation.descriptionPreview.isBlank()) {
            holder.descriptionPreview.visibility = View.GONE
            holder.readMoreLink.visibility = View.GONE
        } else {
            holder.descriptionPreview.visibility = View.VISIBLE
            // Show "Read more" when the description likely overflows 2 lines (~80 chars)
            holder.readMoreLink.visibility =
                if (presentation.descriptionPreview.length > 80) View.VISIBLE else View.GONE
        }

        holder.itemView.setOnClickListener { onItemClick(entity) }
        holder.readMoreLink.setOnClickListener { onItemClick(entity) }
    }

    override fun getItemCount(): Int = entities.size

    /**
     * Swaps the backing list and redraws everything. This is called
     * whenever the ViewModel publishes a new filtered list (search
     * query change, filter chip tap, etc.).
     */
    fun updateEntities(newEntities: List<Map<String, String>>) {
        entities = newEntities
        notifyDataSetChanged()
    }
}
