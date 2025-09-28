package com.fitghost.app.util

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

object Browser {
    fun open(context: Context, url: String) {
        val intent = CustomTabsIntent.Builder().build()
        intent.launchUrl(context, Uri.parse(url))
    }
}