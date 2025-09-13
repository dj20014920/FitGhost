package com.fitghost.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.fitghost.app.ui.screens.*

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object TryOn : Screen("try_on")
    object Wardrobe : Screen("wardrobe")
    object Discover : Screen("discover")
    object AddGarment : Screen("add_garment")
}

val bottomBarScreens = listOf(
    Screen.Home,
    Screen.TryOn,
    Screen.Wardrobe,
    Screen.Discover,
)

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateTryOn = { navController.navigate(Screen.TryOn.route) },
                onNavigateShop = { navController.navigate(Screen.Discover.route) } // Navigate to Discover
            )
        }
        composable(Screen.TryOn.route) {
            TryOnScreen()
        }
        composable(Screen.Wardrobe.route) {
            WardrobeScreen(
                onNavigateAddGarment = { navController.navigate(Screen.AddGarment.route) }
            )
        }
        composable(Screen.Discover.route) {
            DiscoverScreen()
        }
        composable(Screen.AddGarment.route) {
            AddGarmentScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
