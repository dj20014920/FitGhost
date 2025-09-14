package com.fitghost.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.fitghost.app.ui.Screen
import com.fitghost.app.ui.bottomBarScreens
import com.fitghost.app.ui.theme.liquidGlass
import com.fitghost.app.ui.theme.neumorphic

data class BottomNavItem(val screen: Screen, val label: String, val icon: ImageVector)

@Composable
fun CupertinoNeumorphicBottomBar(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val navItems = listOf(
        BottomNavItem(Screen.Home, "홈", Icons.Filled.Home),
        BottomNavItem(Screen.TryOn, "피팅", Icons.Filled.Style),
        BottomNavItem(Screen.Wardrobe, "옷장", Icons.Filled.Person),
        BottomNavItem(Screen.Discover, "디스커버", Icons.Filled.ImageSearch)
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val bottomBarDestination = bottomBarScreens.any { it.route == currentDestination?.route }

    if (bottomBarDestination) {
        // Floating bottom bar with liquid glass effect
        Box(
            modifier = modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .height(72.dp)
                .liquidGlass(
                    shape = RoundedCornerShape(36.dp),
                    blur = 24.dp,
                    alpha = 0.9f,
                    borderAlpha = 0.3f
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                navItems.forEach { item ->
                    val isSelected = currentDestination?.route == item.screen.route
                    LiquidNavItem(
                        item = item,
                        isSelected = isSelected,
                        onClick = {
                            navController.navigate(item.screen.route) {
                                popUpTo(navController.graph.startDestinationId)
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LiquidNavItem(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    var isPressed by remember { mutableStateOf(false) }
    
    val animatedScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.9f
            isSelected -> 1.1f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "nav_item_scale"
    )
    
    val animatedIconColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "nav_item_color"
    )

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            isPressed = when (interaction) {
                is androidx.compose.foundation.interaction.PressInteraction.Press -> true
                else -> false
            }
        }
    }

    Column(
        modifier = Modifier
            .size(56.dp)
            .scale(animatedScale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .then(
                if (isSelected) {
                    Modifier
                        .neumorphic(
                            shape = CircleShape,
                            elevation = 8.dp,
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            isPressed = false
                        )
                        .drawWithContent {
                            drawContent()
                            // Subtle glow for selected item
                            drawCircle(
                                color = Color(0xFF007AFF).copy(alpha = 0.2f),
                                radius = size.minDimension / 2 + 4.dp.toPx()
                            )
                        }
                } else {
                    Modifier
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = animatedIconColor,
            modifier = Modifier.size(24.dp)
        )
        
        if (isSelected) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelSmall,
                color = animatedIconColor,
                maxLines = 1
            )
        }
    }
}