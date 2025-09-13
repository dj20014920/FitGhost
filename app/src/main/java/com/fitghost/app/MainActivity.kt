package com.fitghost.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.fitghost.app.ui.AppNavHost
import com.fitghost.app.ui.components.CupertinoNeumorphicBottomBar
import com.fitghost.app.ui.theme.NeumorphicTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NeumorphicTheme {
                var selected by remember { mutableStateOf(0) }

                Scaffold(
                    bottomBar = {
                        CupertinoNeumorphicBottomBar(
                            selected = selected,
                            onSelect = { selected = it }
                        )
                    }
                ) { paddingValues ->
                    AppNavHost(
                        modifier = Modifier.padding(paddingValues),
                        selectedIndex = selected,
                        onNavigateIndex = { selected = it }
                    )
                }
            }
        }
    }
}
