package com.fitghost.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun CupertinoNeumorphicBottomBar(
        selected: Int,
        onSelect: (Int) -> Unit,
        modifier: Modifier = Modifier
) {
    // 4-Tab 통합: Home / Try-On / Wardrobe / Discover
    val items =
            listOf(
                    "홈" to Icons.Filled.Home,
                    "피팅" to Icons.Filled.Image,
                    "옷장" to Icons.Filled.Person,
                    "디스커버" to Icons.Filled.LocalMall
            )

    Surface(
            modifier =
                    modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(22.dp)),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            tonalElevation = 10.dp,
            shadowElevation = 10.dp
    ) {
        Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, (label, icon) ->
                NavigationBarItem(
                        selected = selected == index,
                        onClick = { onSelect(index) },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        alwaysShowLabel = false,
                        colors =
                                androidx.compose.material3.NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor =
                                                MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.6f
                                                ),
                                        indicatorColor =
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.7f
                                                )
                                )
                )
            }
        }
    }
}
