package com.fitghost.app

import com.fitghost.app.data.model.Garment
import com.fitghost.app.data.model.WeatherSnapshot
import com.fitghost.app.domain.OutfitRecommender
import org.junit.Assert.assertTrue
import org.junit.Test

class OutfitRecommenderTest {
    @Test
    fun testScoring() {
        val r = OutfitRecommender()
        val wardrobe = listOf(
            Garment(type = "T", color = "white", warmth = 2, waterResist = false, tags = listOf("basic")),
            Garment(type = "B", color = "black", warmth = 2, waterResist = false, tags = listOf("basic")),
            Garment(type = "O", color = "navy", warmth = 4, waterResist = true, tags = listOf("rain")),
        )
        val w = WeatherSnapshot(temperatureC = 18.0, precipitationMm = 2.0, windSpeed = 7.0)
        val list = r.recommend(wardrobe, w, 3)
        assertTrue(list.isNotEmpty())
        assertTrue(list.first().score >= list.last().score)
    }
}
