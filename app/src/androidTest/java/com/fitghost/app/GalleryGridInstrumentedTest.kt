package com.fitghost.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GalleryGridInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun gridRenders() {
        composeRule.setContent { com.fitghost.app.ui.screens.DiscoverScreen() }
        composeRule.onNodeWithText("저장된 결과가 없습니다.").assertExists()
    }
}