package com.fitghost.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitghost.app.data.weather.WeatherSnapshot
import com.fitghost.app.domain.HomeOutfitRecommendation
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
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModelFactory(context))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val modelManager = remember { com.fitghost.app.ai.ModelManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var modelState by remember { mutableStateOf(com.fitghost.app.ai.ModelManager.ModelState.NOT_READY) }
    var downloadProgress by remember { mutableStateOf<com.fitghost.app.ai.ModelManager.DownloadProgress?>(null) }

    LaunchedEffect(Unit) {
        modelManager.reconcileState()
        modelManager.observeModelState().collect { stateUpdate ->
            modelState = stateUpdate
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHostState.showSnackbar(it) }
    }

    fun requestRecommendations() {
        scope.launch {
            val location = if (LocationProvider.hasLocationPermission(context)) {
                LocationProvider.getLastKnownLocation(context)
            } else {
                null
            }
            viewModel.refresh(location?.latitude, location?.longitude)
        }
    }

    LaunchedEffect(Unit) {
        requestRecommendations()
    }

    fun startModelDownload() {
        if (downloadProgress != null || modelState == com.fitghost.app.ai.ModelManager.ModelState.DOWNLOADING) return
        scope.launch {
            downloadProgress = com.fitghost.app.ai.ModelManager.DownloadProgress(0f, 664f, 0)
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

    Scaffold(
        modifier = modifier.background(FitGhostColors.BgPrimary),
        topBar = {
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
                        var showSettingsDialog by remember { mutableStateOf(false) }
                        SoftClayIconButton(
                            onClick = { showSettingsDialog = true },
                            icon = Icons.Outlined.Settings,
                            contentDescription = "설정"
                        )

                        if (showSettingsDialog) {
                            SettingsDialog(
                                modelManager = modelManager,
                                modelState = modelState,
                                onDismiss = { showSettingsDialog = false }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FitGhostColors.BgGlass)
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                val hasPermission = LocationProvider.hasLocationPermission(context)
                WeatherCard(
                    weather = uiState.weather?.weather,
                    isLoading = uiState.isLoading && uiState.weather == null,
                    hasLocationPermission = hasPermission,
                    onRefresh = { requestRecommendations() },
                    onRequestPermission = {
                        (context as? android.app.Activity)?.let { activity ->
                            val granted = LocationProvider.ensureLocationPermission(activity)
                            if (granted) {
                                requestRecommendations()
                            }
                        }
                    }
                )
            }
            item {
                ModelDownloadBanner(
                    modelState = modelState,
                    downloadProgress = downloadProgress,
                    onDownload = { startModelDownload() }
                )
            }
            item {
                OutfitRecommendationSection(
                    outfits = uiState.outfits,
                    isLoading = uiState.isLoading && uiState.outfits.isEmpty(),
                    onViewShop = { query ->
                        com.fitghost.app.ui.screens.shop.ShopViewModel.setPendingSearchQuery(query)
                        onNavigateToShop()
                    }
                )
            }
        }
    }
}

@Composable
private fun WeatherCard(
    weather: WeatherSnapshot?,
    isLoading: Boolean,
    hasLocationPermission: Boolean,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit
) {
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
            } else if (weather != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "%.1f°C".format(weather.tempC),
                            style = MaterialTheme.typography.headlineLarge,
                            color = FitGhostColors.TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "바람 %.0f km/h".format(weather.windKph),
                            style = MaterialTheme.typography.bodyMedium,
                            color = FitGhostColors.TextSecondary
                        )
                    }
                    Text(text = weatherEmoji(weather.tempC), style = MaterialTheme.typography.headlineLarge)
                }
            } else {
                Text(
                    text = "날씨 정보를 불러오지 못했습니다.",
                    color = FitGhostColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRefresh,
                    shape = RoundedCornerShape(12.dp)
                ) { Text("새로고침") }
                if (!hasLocationPermission) {
                    OutlinedButton(
                        onClick = onRequestPermission,
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("위치 권한 요청") }
                }
            }
        }
    }
}

@Composable
private fun OutfitRecommendationSection(
    outfits: List<HomeOutfitRecommendation>,
    isLoading: Boolean,
    onViewShop: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "오늘의 추천 코디",
            style = MaterialTheme.typography.headlineMedium,
            color = FitGhostColors.TextPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        when {
            isLoading && outfits.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            outfits.isEmpty() -> {
                Text(
                    text = "옷장 데이터를 기반으로 추천을 준비하고 있습니다.",
                    color = FitGhostColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            else -> {
                outfits.forEachIndexed { index, outfit ->
                    HomeOutfitCard(
                        outfit = outfit,
                        index = index + 1,
                        onViewShop = onViewShop
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeOutfitCard(
    outfit: HomeOutfitRecommendation,
    index: Int,
    onViewShop: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().softClayInset(),
        colors = CardDefaults.cardColors(containerColor = FitGhostColors.BgPrimary),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(FitGhostColors.BgTertiary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = index.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        color = FitGhostColors.TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = outfit.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = FitGhostColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = outfit.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = FitGhostColors.TextSecondary
                    )
                }
            }

            if (outfit.items.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "옷장 아이템",
                        style = MaterialTheme.typography.labelLarge,
                        color = FitGhostColors.TextSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        outfit.items.forEach { item ->
                            AssistChip(
                                onClick = {},
                                label = { Text(item.name) },
                                leadingIcon = {
                                    if (item.favorite) {
                                        Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                                    }
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = FitGhostColors.BgSecondary,
                                    labelColor = FitGhostColors.TextPrimary
                                )
                            )
                        }
                    }
                }
            }

            if (outfit.styleTips.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "스타일 가이드",
                        style = MaterialTheme.typography.labelLarge,
                        color = FitGhostColors.TextSecondary
                    )
                    outfit.styleTips.take(2).forEach { tip ->
                        Text(
                            text = "• $tip",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FitGhostColors.TextPrimary
                        )
                    }
                }
            }

            if (outfit.complementaryProducts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "추천 상품",
                        style = MaterialTheme.typography.labelLarge,
                        color = FitGhostColors.TextSecondary
                    )
                    outfit.complementaryProducts.take(2).forEach { product ->
                        Text(
                            text = "• ${product.name} ${product.price.takeIf { it > 0 }?.formatAsCurrency().orEmpty()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FitGhostColors.TextPrimary
                        )
                    }
                }
            }

            FilledTonalButton(
                onClick = { onViewShop(outfit.shopQuery) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("상점에서 보기")
            }
        }
    }
}

private fun weatherEmoji(temp: Double): String = when {
    temp <= 0 -> "\u2744\uFE0F"
    temp <= 10 -> "\u2601\uFE0F"
    temp <= 20 -> "\u26C5"
    temp <= 28 -> "\u2600\uFE0F"
    else -> "\uD83C\uDF21\uFE0F"
}

private fun Int.formatAsCurrency(): String {
    if (this <= 0) return ""
    return kotlin.runCatching {
        java.text.NumberFormat.getCurrencyInstance(java.util.Locale.KOREA).format(this)
    }.getOrElse { "${this}원" }
}

@Composable
private fun SettingsDialog(
    modelManager: com.fitghost.app.ai.ModelManager,
    modelState: com.fitghost.app.ai.ModelManager.ModelState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isDeleting by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "설정",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // AI 모델 관리 섹션
                Text(
                    text = "AI 모델 관리",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = FitGhostColors.TextPrimary
                )
                
                when (modelState) {
                    com.fitghost.app.ai.ModelManager.ModelState.READY -> {
                        val modelInfo = modelManager.getModelInfo()
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = FitGhostColors.BgSecondary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = FitGhostColors.AccentPrimary
                                    )
                                    Text(
                                        text = "다운로드 완료",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = FitGhostColors.AccentPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                Divider(modifier = Modifier.padding(vertical = 4.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "모델 이름",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = FitGhostColors.TextSecondary
                                    )
                                    Text(
                                        text = modelInfo.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = FitGhostColors.TextPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "버전",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = FitGhostColors.TextSecondary
                                    )
                                    Text(
                                        text = modelInfo.version,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = FitGhostColors.TextPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "크기",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = FitGhostColors.TextSecondary
                                    )
                                    Text(
                                        text = "${modelInfo.sizeMB} MB",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = FitGhostColors.TextPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                OutlinedButton(
                                    onClick = { showDeleteConfirm = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isDeleting,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (isDeleting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("삭제 중...")
                                    } else {
                                        Text("모델 삭제")
                                    }
                                }
                            }
                        }
                    }
                    com.fitghost.app.ai.ModelManager.ModelState.NOT_READY -> {
                        Text(
                            text = "AI 모델이 다운로드되지 않았습니다.\n홈 화면에서 다운로드할 수 있습니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FitGhostColors.TextSecondary
                        )
                    }
                    else -> {
                        Text(
                            text = "모델 상태: ${modelState.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FitGhostColors.TextSecondary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
    
    // 삭제 확인 다이얼로그
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = "모델 삭제",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "AI 모델을 삭제하시겠습니까?\n\n" +
                            "삭제 후 다시 사용하려면 664MB를 다시 다운로드해야 합니다.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        isDeleting = true
                        scope.launch {
                            val result = modelManager.deleteModel()
                            isDeleting = false
                            result.onSuccess {
                                android.widget.Toast.makeText(
                                    context,
                                    "AI 모델이 삭제되었습니다",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                onDismiss()
                            }.onFailure { error ->
                                android.widget.Toast.makeText(
                                    context,
                                    "삭제 실패: ${error.message}",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("취소")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
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
            // 다운로드 완료 시 배너 숨김 (사용자 경험 최적화)
            // 설정에서 모델 관리 가능
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
