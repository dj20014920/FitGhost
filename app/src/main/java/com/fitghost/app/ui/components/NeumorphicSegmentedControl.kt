package com.fitghost.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun NeumorphicSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .shadow(
                elevation = 6.dp,
                ambientColor = Color.White,
                spotColor = Color.Black.copy(alpha = 0.12f),
                shape = shape
            ),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        shape = shape
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val segShape = RoundedCornerShape(10.dp)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(height - 8.dp)
                        .clip(segShape)
                        .clickable { onSelect(index) }
                        .shadow(
                            elevation = if (selected) 2.dp else 6.dp,
                            ambientColor = if (selected) Color.Black.copy(alpha = 0.06f) else Color.White,
                            spotColor = if (selected) Color.White else Color.Black.copy(alpha = 0.12f),
                            shape = segShape
                        ),
                    color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    shape = segShape
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
