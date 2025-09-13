package com.fitghost.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.fitghost.app.data.CreditStore
import com.fitghost.app.ui.theme.NeumorphicButton
import com.fitghost.app.ui.theme.NeumorphicCard
import com.fitghost.app.ui.theme.NeumorphicIconButton
import com.fitghost.app.util.TryOnBridge

@Composable
fun HomeScreen(onNavigateTryOn: () -> Unit, onNavigateShop: () -> Unit) {
    val vm: HomeViewModel = viewModel()
    val weather by vm.weather.collectAsState(initial = null)
    val top3 by vm.top3.collectAsState(initial = emptyList())

    val context = LocalContext.current
    val creditStore = remember { CreditStore(context) }
    val creditState by creditStore.state().collectAsState(initial = CreditStore.State("", 0, 0))

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GHOSTFIT",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                NeumorphicIconButton(onClick = { /* Navigate to Settings */ }) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        }

        // Weather and Credits
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                NeumorphicCard(modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(16.dp).fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WbSunny, contentDescription = "Weather", modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("오늘 날씨", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(8.dp))
                        val wt = weather?.let {
                            "%.0f℃, 강수 %.1fmm".format(it.temperatureC, it.precipitationMm)
                        } ?: "로딩 중..."
                        Text(wt, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                NeumorphicCard(modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(16.dp).fillMaxWidth()) {
                        Text("내 크레딧", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("남은 횟수: ${creditState.remaining}", style = MaterialTheme.typography.bodyLarge)
                        NeumorphicIconButton(onClick = onNavigateTryOn, modifier = Modifier.align(Alignment.End)) {
                            Icon(Icons.Default.Add, contentDescription = "Charge Credits")
                        }
                    }
                }
            }
        }

        // Today's Top 3
        item {
            Text("오늘의 코디 TOP 3", style = MaterialTheme.typography.headlineSmall)
        }

        if (top3.isEmpty()) {
            item {
                NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("추천을 준비 중입니다...", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        } else {
            itemsIndexed(top3) { index, scored ->
                NeumorphicCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("#${index + 1} 추천", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            scored.outfit.top?.imageUri?.let {
                                AsyncImage(
                                    model = it,
                                    contentDescription = "Top",
                                    modifier = Modifier.weight(1f).aspectRatio(1f)
                                )
                            }
                            scored.outfit.bottom?.imageUri?.let {
                                AsyncImage(
                                    model = it,
                                    contentDescription = "Bottom",
                                    modifier = Modifier.weight(1f).aspectRatio(1f)
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("점수: %.1f".format(scored.score), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(16.dp))
                        NeumorphicButton(
                            onClick = {
                                TryOnBridge.setPart(TryOnBridge.TryOnPart.TOP, itemImageUri = scored.outfit.top?.imageUri)
                                TryOnBridge.setPart(TryOnBridge.TryOnPart.BOTTOM, itemImageUri = scored.outfit.bottom?.imageUri)
                                onNavigateTryOn()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = creditState.remaining > 0
                        ) {
                            Text(if (creditState.remaining > 0) "이 조합으로 입어보기" else "크레딧 부족")
                        }
                    }
                }
            }
        }

        // CTA Buttons
        item {
            NeumorphicButton(onClick = onNavigateShop, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("새로운 옷 쇼핑하기", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
