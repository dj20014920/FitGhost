package com.fitghost.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fitghost.app.data.model.Garment
import com.fitghost.app.ui.theme.NeumorphicButton
import com.fitghost.app.ui.theme.NeumorphicCard
import com.fitghost.app.ui.theme.NeumorphicIconButton
import com.fitghost.app.util.ServiceLocator
import kotlinx.coroutines.launch

@Composable
fun WardrobeScreen(onNavigateAddGarment: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { ServiceLocator.db(context).wardrobeDao() }
    val scope = rememberCoroutineScope()
    var garments by remember { mutableStateOf<List<Garment>>(emptyList()) }

    LaunchedEffect(Unit) {
        dao.all().collect { garments = it }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "내 옷장",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            NeumorphicIconButton(onClick = onNavigateAddGarment) {
                Icon(Icons.Default.Add, contentDescription = "Add Garment")
            }
        }

        if (garments.isEmpty()) {
            // Empty State
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("옷장이 비어있어요.", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))
                    NeumorphicButton(onClick = onNavigateAddGarment) {
                        Text("첫 의상 추가하기")
                    }
                }
            }
        } else {
            // Grid View
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(garments) { garment ->
                    NeumorphicCard(
                        modifier = Modifier.clickable { /* Navigate to detail view */ }
                    ) {
                        Column {
                            AsyncImage(
                                model = garment.imageUri ?: "", // Placeholder or error image
                                contentDescription = garment.description(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                            )
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text(garment.description(), style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(8.dp))
                                NeumorphicIconButton(
                                    onClick = { scope.launch { dao.delete(garment) } },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Garment.description(): String {
    return "$color ${tags.firstOrNull() ?: type}"
}