package com.example.apiapp.ui.dashboard

import com.example.apiapp.R

/**
 * Detects and presents dynamic entity fields in a visually appealing way.
 *
 * The dashboard API returns entities with different keys depending on the topic
 * (food has dishName/origin, animals has species/habitat, etc). This helper
 * inspects the keys and picks the best field to use as title / subtitle / badge.
 */
data class EntityPresentation(
    /** Primary text — usually the name/title field of the entity. */
    val title: String,
    /** One-line secondary text built from up to two short fields joined with " • ". */
    val subtitle: String,
    /** Short uppercase badge text (e.g. "LUNCH/DINNER"), or null if no suitable field. */
    val badge: String?,
    /** Full description for the 2-line preview on the card and the details screen. */
    val descriptionPreview: String,
    /** Unicode emoji (country flag or food icon) shown in the avatar circle. */
    val emoji: String,
    /** Background color resource for the avatar circle (rotates through a palette). */
    val avatarColorRes: Int,
    /** Background color resource for the badge chip. */
    val badgeBgColor: Int,
    /** Foreground/text color resource for the badge chip. */
    val badgeFgColor: Int
) {
    companion object {

        /**
         * Inspects an entity map and produces a ready-to-render [EntityPresentation].
         *
         * Field selection is priority-based:
         *  1. Title: first key ending in "name" / "title", else first non-description key
         *  2. Subtitle: next up to 2 short fields (≤40 chars)
         *  3. Badge: first preferred categorical field (mealType/type/category/…)
         *  4. Emoji: country flag from origin field → type keyword → title keyword
         *
         * [index] is the row position in the list, used only to rotate
         * through the avatar color palette for visual variety.
         */
        fun from(entity: Map<String, String>, index: Int): EntityPresentation {
            val titleKey = pickTitleKey(entity)
            val title = titleKey?.let { entity[it] } ?: "Untitled"

            val used = mutableSetOf<String>()
            titleKey?.let { used.add(it) }
            used.add("description")

            val subtitleParts = pickSubtitleParts(entity, used)
            val subtitle = subtitleParts.joinToString(" • ").ifEmpty { "—" }

            val badgeKey = pickBadgeKey(entity, used)
            val badge = badgeKey?.let { entity[it]?.uppercase() }

            val description = entity["description"] ?: entity.values.lastOrNull() ?: ""

            val originValue = findField(entity, listOf("origin", "country", "location", "region", "habitat"))
            val typeValue = findField(entity, listOf("mealtype", "type", "category", "class", "genre"))

            val emoji = pickEmoji(entity, originValue, typeValue, title)
            val avatarColor = avatarColors[index % avatarColors.size]
            val (bgColor, fgColor) = pickBadgeColors(badgeKey, entity[badgeKey ?: ""])

            return EntityPresentation(
                title = title,
                subtitle = subtitle,
                badge = badge,
                descriptionPreview = description,
                emoji = emoji,
                avatarColorRes = avatarColor,
                badgeBgColor = bgColor,
                badgeFgColor = fgColor
            )
        }

        private fun pickTitleKey(entity: Map<String, String>): String? {
            val nameKey = entity.keys.firstOrNull {
                val k = it.lowercase()
                k == "name" || k.endsWith("name") || k == "title"
            }
            if (nameKey != null) return nameKey
            return entity.keys.firstOrNull { it != "description" }
        }

        private fun pickSubtitleParts(entity: Map<String, String>, used: MutableSet<String>): List<String> {
            val parts = mutableListOf<String>()
            for ((key, value) in entity) {
                if (key in used) continue
                if (parts.size >= 2) break
                if (value.length > 40) continue
                parts.add(value)
                used.add(key)
            }
            return parts
        }

        private fun pickBadgeKey(entity: Map<String, String>, used: Set<String>): String? {
            val preferredKeys = listOf("mealtype", "type", "category", "status", "class", "genre")
            for (pref in preferredKeys) {
                val match = entity.keys.firstOrNull { it.lowercase() == pref }
                if (match != null && match !in used) return match
            }
            return entity.entries
                .filter { it.key !in used && it.key != "description" && it.value.length <= 20 }
                .maxByOrNull { it.value.length }?.key
        }

        private fun findField(entity: Map<String, String>, candidates: List<String>): String? {
            for (c in candidates) {
                val match = entity.entries.firstOrNull { it.key.lowercase() == c }
                if (match != null) return match.value
            }
            return null
        }

        private fun pickEmoji(
            entity: Map<String, String>,
            origin: String?,
            type: String?,
            title: String
        ): String {
            // First try country flag from origin
            origin?.let { o ->
                countryFlags[normalizeCountry(o)]?.let { return it }
            }
            // Then try category/type emoji
            type?.lowercase()?.let { t ->
                typeEmojis.entries.firstOrNull { t.contains(it.key) }?.value?.let { return it }
            }
            // Then try title keywords
            val titleLower = title.lowercase()
            titleEmojis.entries.firstOrNull { titleLower.contains(it.key) }?.value?.let { return it }
            // Any field value as fallback
            entity.values.forEach { v ->
                val vl = v.lowercase()
                countryFlags[normalizeCountry(v)]?.let { return it }
                titleEmojis.entries.firstOrNull { vl.contains(it.key) }?.value?.let { return it }
            }
            return "✨"
        }

        private fun normalizeCountry(value: String): String {
            return value.lowercase()
                .substringBefore(",")
                .substringBefore("/")
                .trim()
        }

        private fun pickBadgeColors(badgeKey: String?, badgeValue: String?): Pair<Int, Int> {
            if (badgeKey?.lowercase() == "mealtype" && badgeValue != null) {
                val v = badgeValue.lowercase()
                return when {
                    "breakfast" in v -> Pair(R.color.meal_breakfast_bg, R.color.meal_breakfast_fg)
                    "lunch" in v && "dinner" in v -> Pair(R.color.meal_lunch_bg, R.color.meal_lunch_fg)
                    "dinner" in v -> Pair(R.color.meal_dinner_bg, R.color.meal_dinner_fg)
                    "lunch" in v -> Pair(R.color.meal_lunch_bg, R.color.meal_lunch_fg)
                    "snack" in v -> Pair(R.color.meal_snack_bg, R.color.meal_snack_fg)
                    else -> Pair(R.color.meal_default_bg, R.color.meal_default_fg)
                }
            }
            return Pair(R.color.meal_default_bg, R.color.meal_default_fg)
        }

        private val avatarColors = listOf(
            R.color.avatar_1, R.color.avatar_2, R.color.avatar_3, R.color.avatar_4,
            R.color.avatar_5, R.color.avatar_6, R.color.avatar_7, R.color.avatar_8
        )

        private val countryFlags = mapOf(
            "japan" to "🇯🇵",
            "italy" to "🇮🇹",
            "mexico" to "🇲🇽",
            "france" to "🇫🇷",
            "thailand" to "🇹🇭",
            "united states" to "🇺🇸",
            "usa" to "🇺🇸",
            "america" to "🇺🇸",
            "india" to "🇮🇳",
            "china" to "🇨🇳",
            "korea" to "🇰🇷",
            "spain" to "🇪🇸",
            "greece" to "🇬🇷",
            "germany" to "🇩🇪",
            "united kingdom" to "🇬🇧",
            "uk" to "🇬🇧",
            "england" to "🇬🇧",
            "brazil" to "🇧🇷",
            "argentina" to "🇦🇷",
            "vietnam" to "🇻🇳",
            "turkey" to "🇹🇷",
            "morocco" to "🇲🇦",
            "ethiopia" to "🇪🇹",
            "lebanon" to "🇱🇧",
            "indonesia" to "🇮🇩",
            "australia" to "🇦🇺",
            "canada" to "🇨🇦",
            "russia" to "🇷🇺",
            "portugal" to "🇵🇹",
            "netherlands" to "🇳🇱",
            "belgium" to "🇧🇪",
            "switzerland" to "🇨🇭"
        )

        private val typeEmojis = mapOf(
            "breakfast" to "🥞",
            "lunch" to "🥗",
            "dinner" to "🍽️",
            "snack" to "🍿",
            "dessert" to "🍰",
            "drink" to "🥤",
            "mammal" to "🐾",
            "bird" to "🦅",
            "reptile" to "🦎",
            "fish" to "🐟",
            "insect" to "🐛",
            "plant" to "🌿",
            "flower" to "🌸",
            "tree" to "🌳"
        )

        private val titleEmojis = mapOf(
            "sushi" to "🍣",
            "pizza" to "🍕",
            "taco" to "🌮",
            "croissant" to "🥐",
            "pad thai" to "🍜",
            "hamburger" to "🍔",
            "burger" to "🍔",
            "curry" to "🍛",
            "pasta" to "🍝",
            "noodle" to "🍜",
            "rice" to "🍚",
            "bread" to "🍞",
            "cake" to "🍰",
            "ice cream" to "🍦",
            "coffee" to "☕",
            "tea" to "🍵",
            "salad" to "🥗",
            "soup" to "🍲",
            "steak" to "🥩",
            "chicken" to "🍗",
            "fish" to "🐟",
            "shrimp" to "🍤",
            "cheese" to "🧀",
            "egg" to "🍳",
            "dumpling" to "🥟",
            "sandwich" to "🥪",
            "donut" to "🍩",
            "cookie" to "🍪",
            "chocolate" to "🍫"
        )
    }
}
