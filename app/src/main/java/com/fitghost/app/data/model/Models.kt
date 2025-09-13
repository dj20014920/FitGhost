package com.fitghost.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Wardrobe Garment
 *
 * - imageUri: Optional local or remote URI reference to the item's image (nullable for text-only
 * entries)
 * - tags: Free-form keywords; defaults to emptyList() to avoid null-handling and simplify queries
 */
@Entity(tableName = "wardrobe")
data class Garment(
        @PrimaryKey(autoGenerate = true) val id: Long = 0,
        val type: String, // T/B/O
        val color: String,
        val pattern: String? = null,
        val fabric: String? = null,
        val warmth: Int = 3, // 1..5
        val waterResist: Boolean = false,
        val imageUri: String? = null,
        val tags: List<String> = emptyList()
)

@Entity(tableName = "cart")
data class CartItem(
        @PrimaryKey(autoGenerate = true) val id: Long = 0,
        val title: String,
        val price: Double,
        val image: String?,
        val mall: String,
        val link: String,
        val optionsJson: String? = null,
        val status: String = "PENDING" // PENDING | DONE
)

data class WeatherSnapshot(
        val temperatureC: Double,
        val precipitationMm: Double,
        val windSpeed: Double
)
