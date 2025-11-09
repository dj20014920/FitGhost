package com.fitghost.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.fitghost.app.ui.navigation.FitGhostBottomNavigation
import com.fitghost.app.ui.navigation.FitGhostNavHost
import com.fitghost.app.ui.theme.FitGhostTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** FitGhost 앱 메인 액티비티 Soft Clay 디자인 시스템과 하단 네비게이션을 포함한 전체 앱 구조 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 앱 시작 시 만료된 캐시 정리
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cacheManager = com.fitghost.app.data.cache.CacheManager.getInstance(this@MainActivity)
                cacheManager.cleanExpired()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to clean expired cache", e)
            }
        }
        
        setContent { FitGhostApp() }
    }
}

@Composable
fun FitGhostApp() {
    FitGhostTheme {
        val navController = rememberNavController()
        val context = LocalContext.current
        val onboarded by com.fitghost.app.data.settings.UserSettings
            .onboardingCompletedFlow(context)
            .collectAsState(initial = false)

        if (!onboarded) {
            com.fitghost.app.ui.screens.onboarding.OnboardingScreen(
                onCompleted = { /* recomposition will show main app */ }
            )
            return@FitGhostTheme
        }

        Scaffold(bottomBar = { FitGhostBottomNavigation(navController = navController) }) {
                paddingValues ->
            FitGhostNavHost(
                    navController = navController,
                    modifier = Modifier.padding(paddingValues)
            )
        }
    }
}
