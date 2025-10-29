package com.fitghost.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitghost.app.data.weather.WeatherRepo
import com.fitghost.app.data.weather.WeatherSnapshot
import com.fitghost.app.ui.components.SoftClayIconButton
import com.fitghost.app.ui.components.softClay
import com.fitghost.app.ui.components.softClayInset
import com.fitghost.app.ui.theme.FitGhostColors
import com.fitghost.app.util.LocationProvider
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
        modifier: Modifier = Modifier,
        onNavigateToFitting: () -> Unit = {},
        onNavigateToWardrobe: () -> Unit = {},
        onNavigateToShop: () -> Unit = {},
        onNavigateToGallery: () -> Unit = {}
) {
    val context = LocalContext.current
    val modelManager = remember { com.fitghost.app.ai.ModelManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    var modelState by remember { mutableStateOf(com.fitghost.app.ai.ModelManager.ModelState.NOT_READY) }
    var downloadProgress by remember { mutableStateOf<com.fitghost.app.ai.ModelManager.DownloadProgress?>(null) }

    LaunchedEffect(Unit) {
        val reconciled = modelManager.reconcileState()
        modelState = reconciled
        modelManager.observeModelState().collect { stateUpdate ->
            modelState = stateUpdate
        }
    }

    fun startModelDownload() {
        if (downloadProgress != null || modelState == com.fitghost.app.ai.ModelManager.ModelState.DOWNLOADING) return
        scope.launch {
            downloadProgress = com.fitghost.app.ai.ModelManager.DownloadProgress(0f, 696f, 0)
            val result = modelManager.downloadModel { progress ->
                downloadProgress = progress
            }
            result.onSuccess {
                downloadProgress = null
                android.widget.Toast.makeText(context, "AI 모델이 준비되었습니다!", android.widget.Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                downloadProgress = null
                android.widget.Toast.makeText(
                        context,
                        "다운로드 실패: ${error.message ?: "알 수 없는 오류"}",
                        android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

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
            item { 
                ModelDownloadBanner(
                    modelState = modelState,
                    downloadProgress = downloadProgress,
                    onDownload = { startModelDownload() }
                )
            }
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

@Composable
private fun ModelDownloadBanner(
        modelState: com.fitghost.app.ai.ModelManager.ModelState,
        downloadProgress: com.fitghost.app.ai.ModelManager.DownloadProgress?,
        onDownload: () -> Unit
) {
    val cardModifier = Modifier.fillMaxWidth()

    when {
        downloadProgress != null || modelState == com.fitghost.app.ai.ModelManager.ModelState.DOWNLOADING -> {
            Card(
                    modifier = cardModifier.softClay(),
                    colors = CardDefaults.cardColors(containerColor = FitGhostColors.BgSecondary),
                    shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                            text = "AI 자동 완성 모델 준비 중",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = FitGhostColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    val progress = downloadProgress
                    if (progress != null) {
                        val downloadedText = String.format("%.1f", progress.downloadedMB)
                        val totalText =
                                if (progress.totalMB > 0) String.format("%.1f", progress.totalMB)
                                else null
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                    text = "${progress.percentage}%",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = FitGhostColors.AccentPrimary
                            )
                            Text(
                                    text =
                                    if (totalText != null) "$downloadedText MB / $totalText MB"
                                    else "$downloadedText MB 다운로드 중",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = FitGhostColors.TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                                progress = { progress.percentage / 100f },
                                modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                color = FitGhostColors.AccentPrimary
                        )
                    } else {
                        LinearProgressIndicator(
                                modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                color = FitGhostColors.AccentPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                            text = "다운로드가 끝나면 자동 완성을 바로 이용할 수 있습니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FitGhostColors.TextSecondary
                    )
                }
            }
        }
        modelState == com.fitghost.app.ai.ModelManager.ModelState.READY -> {
            Card(
                    modifier = cardModifier.softClay(),
                    colors = CardDefaults.cardColors(containerColor = FitGhostColors.BgSecondary),
                    shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                        modifier = Modifier.padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = FitGhostColors.AccentPrimary,
                            modifier = Modifier.size(32.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                                text = "AI 자동 완성 준비 완료",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = FitGhostColors.TextPrimary
                        )
                        Text(
                                text = "사진을 추가하면 AI가 이름과 속성을 자동 채워줍니다.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = FitGhostColors.TextSecondary
                        )
                    }
                }
            }
        }
        else -> {
            Card(
                    modifier = cardModifier.softClay(),
                    colors = CardDefaults.cardColors(containerColor = FitGhostColors.BgSecondary),
                    shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = null,
                                tint = FitGhostColors.AccentPrimary,
                                modifier = Modifier.size(28.dp)
                        )
                        Text(
                                text = "AI 자동 완성 모델 다운로드",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = FitGhostColors.TextPrimary
                        )
                    }
                    Text(
                            text = "한 번만 받아두면 네트워크가 느린 환경에서도 빠르게 자동 완성을 사용할 수 있어요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FitGhostColors.TextSecondary
                    )
                    FilledTonalButton(
                            onClick = onDownload,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = FitGhostColors.AccentPrimary.copy(alpha = 0.15f),
                                    contentColor = FitGhostColors.AccentPrimary
                            ),
                            shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Icon(imageVector = Icons.Outlined.Download, contentDescription = null)
                            Text(
                                    text = "AI 모델 준비하기",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
