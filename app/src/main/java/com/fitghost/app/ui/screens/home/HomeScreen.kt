package com.fitghost.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitghost.app.data.weather.WeatherRepo
import com.fitghost.app.data.weather.WeatherSnapshot
import com.fitghost.app.ui.components.SoftClayIconButton
import com.fitghost.app.ui.components.softClay
import com.fitghost.app.ui.components.softClayInset
import com.fitghost.app.ui.theme.FitGhostColors
import com.fitghost.app.util.LocationProvider
import kotlinx.coroutines.launch

/** 홈 화면 - 날씨 정보와 AI 추천 코디 표시 PRD: 홈 진입 시 오늘의 날씨 표시 + 추천 TOP3 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
        modifier: Modifier = Modifier,
        onNavigateToFitting: () -> Unit = {},
        onNavigateToWardrobe: () -> Unit = {},
        onNavigateToShop: () -> Unit = {},
        onNavigateToGallery: () -> Unit = {}
) {
    Column(modifier = modifier.fillMaxSize().background(FitGhostColors.BgPrimary)) {
        // Header
        TopAppBar(
                title = {
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                                text = "FitGhost",
                                style = MaterialTheme.typography.displayLarge,
                                color = FitGhostColors.TextPrimary,
                                fontWeight = FontWeight.Black
                        )
                        SoftClayIconButton(
                                onClick = { /* 설정 화면 */},
                                icon = Icons.Outlined.Settings,
                                contentDescription = "설정"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FitGhostColors.BgGlass)
        )

        // Content
        LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item { WeatherCard() }
            item { OutfitRecommendationSection() }
        }
    }
}

@Composable
private fun WeatherCard() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val repo = remember { WeatherRepo.create() }
    var snapshot by remember { mutableStateOf<WeatherSnapshot?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoading = true
        val ctx = context
        val result = runCatching {
            if (LocationProvider.hasLocationPermission(ctx)) {
                val loc = LocationProvider.getLastKnownLocation(ctx)
                if (loc != null) {
                    repo.getCurrent(loc.latitude, loc.longitude)
                } else {
                    // fallback: lastKnown 없음 → 기본 좌표
                    repo.getCurrent(37.5665, 126.9780)
                }
            } else {
                // 권한 미허용 → 기본 좌표
                repo.getCurrent(37.5665, 126.9780)
            }
        }
        result.onSuccess { snapshot = it }.onFailure { snapshot = null }
        isLoading = false
    }

    Card(
            modifier = Modifier.fillMaxWidth().softClay(),
            colors = CardDefaults.cardColors(containerColor = FitGhostColors.BgSecondary),
            shape = RoundedCornerShape(24.dp)
    ) {
        Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                    text = "오늘의 날씨",
                    style = MaterialTheme.typography.headlineMedium,
                    color = FitGhostColors.TextPrimary,
                    fontWeight = FontWeight.Bold
            )

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                val temp = snapshot?.tempC ?: 22.0
                val wind = snapshot?.windKph ?: 5.0
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                                text = "%.1f°C".format(temp),
                                style = MaterialTheme.typography.headlineLarge,
                                color = FitGhostColors.TextPrimary,
                                fontWeight = FontWeight.Bold
                        )
                        Text(
                                text = "바람 %.0f km/h".format(wind),
                                style = MaterialTheme.typography.bodyMedium,
                                color = FitGhostColors.TextSecondary
                        )
                    }
                    Text(text = "☀️", style = MaterialTheme.typography.headlineLarge)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    val ctx = context
                                    val result = runCatching {
                                        if (LocationProvider.hasLocationPermission(ctx)) {
                                            val loc = LocationProvider.getLastKnownLocation(ctx)
                                            if (loc != null) {
                                                repo.getCurrent(loc.latitude, loc.longitude)
                                            } else {
                                                repo.getCurrent(37.5665, 126.9780)
                                            }
                                        } else {
                                            repo.getCurrent(37.5665, 126.9780)
                                        }
                                    }
                                    result.onSuccess { snapshot = it }.onFailure { /* no-op */}
                                    isLoading = false
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                    ) { Text("새로고침") }
                    if (!LocationProvider.hasLocationPermission(context)) {
                        OutlinedButton(
                                onClick = {
                                    (context as? android.app.Activity)?.let {
                                        LocationProvider.ensureLocationPermission(it)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                        ) { Text("위치 권한 요청") }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutfitRecommendationSection() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
                text = "오늘의 추천 코디",
                style = MaterialTheme.typography.headlineMedium,
                color = FitGhostColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
        )
        repeat(3) { index -> OutfitRecommendationCard(index = index + 1) }
    }
}

@Composable
private fun OutfitRecommendationCard(index: Int) {
    Card(
            modifier = Modifier.fillMaxWidth().softClayInset(),
            colors = CardDefaults.cardColors(containerColor = FitGhostColors.BgPrimary),
            shape = RoundedCornerShape(20.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                    modifier =
                            Modifier.size(80.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(FitGhostColors.BgTertiary),
                    contentAlignment = Alignment.Center
            ) {
                Text(
                        text = "$index",
                        style = MaterialTheme.typography.headlineMedium,
                        color = FitGhostColors.TextSecondary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = "추천 코디 $index",
                        style = MaterialTheme.typography.titleLarge,
                        color = FitGhostColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                )
                Text(
                        text = "오늘 날씨에 perfect!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FitGhostColors.TextSecondary
                )
            }
        }
    }
}
