package com.fitghost.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.fitghost.app.ui.navigation.FitGhostBottomNavigation
import com.fitghost.app.ui.navigation.FitGhostNavHost
import com.fitghost.app.ui.theme.FitGhostTheme

/** FitGhost 앱 메인 액티비티 Soft Clay 디자인 시스템과 하단 네비게이션을 포함한 전체 앱 구조 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FitGhostApp() }
    }
}

@Composable
fun FitGhostApp() {
    FitGhostTheme {
        val navController = rememberNavController()

        Scaffold(bottomBar = { FitGhostBottomNavigation(navController = navController) }) {
                paddingValues ->
            FitGhostNavHost(
                    navController = navController,
                    modifier = Modifier.padding(paddingValues)
            )
        }
    }
}
