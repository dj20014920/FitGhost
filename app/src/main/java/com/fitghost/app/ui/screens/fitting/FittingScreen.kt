package com.fitghost.app.ui.screens.fitting

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.fitghost.app.data.LocalImageStore
import com.fitghost.app.data.db.WardrobeItemEntity
import com.fitghost.app.engine.TryOnEngine
import com.fitghost.app.ui.screens.wardrobe.WardrobeUiUtil
import com.fitghost.app.ui.screens.wardrobe.WardrobeViewModel
import com.fitghost.app.ui.screens.wardrobe.WardrobeViewModelFactory
import com.fitghost.app.ui.theme.FitGhostColors
import com.fitghost.app.ui.theme.Spacing
import com.fitghost.app.ui.theme.IconSize
import com.fitghost.app.ui.theme.CornerRadius
import kotlinx.coroutines.launch

import com.fitghost.app.engine.CompositeTryOnEngine
import com.fitghost.app.BuildConfig

/** 가상 피팅 화면 PRD: Try-On 프리뷰 생성/저장/갤러리 표출 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FittingScreen(modifier: Modifier = Modifier, onNavigateToGallery: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 옷장 VM 주입 및 상태 수집
    val wardrobeViewModel: WardrobeViewModel =
            viewModel(factory = WardrobeViewModelFactory(context))
    val wardrobeUiState by wardrobeViewModel.uiState.collectAsState()
    
    // 피팅 ViewModel (장바구니에서 전달된 의상 URL 처리)
    val fittingViewModel = remember { FittingViewModel.getInstance() }
    val pendingClothingUrl by fittingViewModel.pendingClothingUrl.collectAsState()

    var modelUri by remember { mutableStateOf<Uri?>(null) }
    var clothingUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var systemPrompt by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var showWardrobePicker by remember { mutableStateOf(false) }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var lastSavedFile by remember { mutableStateOf<java.io.File?>(null) }
    
    // 최근 사용한 모델 사진
    val recentModelPhotos by com.fitghost.app.data.settings.RecentModelPhotos
        .getRecentPhotos(context)
        .collectAsState(initial = emptyList())
    
    // 장바구니에서 전달된 의상 URL 처리
    LaunchedEffect(pendingClothingUrl) {
        pendingClothingUrl?.let { url ->
            // URL을 Uri로 변환하여 clothingUris에 추가
            try {
                val uri = Uri.parse(url)
                if (!clothingUris.contains(uri)) {
                    clothingUris = clothingUris + uri
                }
                // URL 소비 (한 번만 사용)
                fittingViewModel.consumePendingClothingUrl()
                
                // 사용자에게 알림
                snackbarHostState.showSnackbar("의상 이미지가 추가되었습니다")
            } catch (e: Exception) {
                Log.e(FIT_TAG, "Failed to parse clothing URL: $url", e)
                snackbarHostState.showSnackbar("이미지를 불러올 수 없습니다")
            }
        }
    }

    val pickModelLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia()
            ) { uri -> 
                uri?.let {
                    modelUri = it
                    // 최근 사용한 모델 사진에 추가
                    scope.launch {
                        com.fitghost.app.data.settings.RecentModelPhotos.addRecentPhoto(context, it)
                    }
                }
            }

    val pickClothingLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia()
            ) { uri -> uri?.let { clothingUris = clothingUris + it } }

    val engine: TryOnEngine = remember { CompositeTryOnEngine() }
    // 정책: 추천/설명 텍스트 생성은 금지. 이미지 합성 지시문은 허용(기본 템플릿 적용됨).

    if (showPreviewDialog && lastSavedFile != null) {
        AlertDialog(
                onDismissRequest = { showPreviewDialog = false },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                                onClick = {
                                    lastSavedFile?.let {
                                        val intent =
                                                com.fitghost.app.data.LocalImageStore
                                                        .buildShareIntent(context, it)
                                        context.startActivity(
                                                android.content.Intent.createChooser(
                                                        intent,
                                                        "피팅 결과 공유"
                                                )
                                        )
                                    }
                                }
                        ) { Text("공유") }
                        TextButton(
                                onClick = {
                                    lastSavedFile?.let {
                                        com.fitghost.app.data.LocalImageStore.saveToMediaStore(
                                                context,
                                                it
                                        )
                                        com.fitghost.app.data.LocalImageStore.refresh(context)
                                    }
                                }
                        ) { Text("갤러리에 저장") }
                        TextButton(onClick = { showPreviewDialog = false }) { Text("닫기") }
                    }
                },
                title = { Text("미리보기") },
                text = {
                    Column {
                        AsyncImage(
                                model = java.io.File(lastSavedFile!!.absolutePath),
                                contentDescription = "미리보기",
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(CornerRadius.md))
                        )
                    }
                }
        )
    }
    val currentStep =
            when {
                modelUri == null && clothingUris.isEmpty() -> 1
                modelUri != null && clothingUris.isNotEmpty() && !isProcessing -> 2
                isProcessing -> 3
                else -> 2
            }

    Scaffold(
            topBar = {
                TopAppBar(
                        title = {
                            Text(
                                    text = "가상 피팅",
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = FitGhostColors.TextPrimary,
                                    fontWeight = FontWeight.Bold
                            )
                        },
                        colors =
                                TopAppBarDefaults.topAppBarColors(
                                        containerColor = FitGhostColors.BgGlass
                                )
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            containerColor = FitGhostColors.BgPrimary
    ) { padding ->
        // Main Content
        LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. 모델 사진 선택 섹션 (상단, 사진 선택 버튼 오른쪽 배치)
            item {
                ModelImagePickSection(
                        selectedUri = modelUri,
                        recentPhotos = recentModelPhotos,
                        onPick = {
                            pickModelLauncher.launch(
                                    PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                            )
                        },
                        onSelectRecent = { uri ->
                            modelUri = uri
                            // 최근 사진 목록 업데이트 (맨 앞으로 이동)
                            scope.launch {
                                com.fitghost.app.data.settings.RecentModelPhotos.addRecentPhoto(context, uri)
                            }
                        },
                        onClear = { modelUri = null }
                )
            }

            // 2. 의상 선택 섹션 (3×n 그리드 레이아웃, 추가+옷장선택 버튼 연결)
            item {
                ClothingSelectionSection(
                        clothingUris = clothingUris,
                        onAddClothing = {
                            val totalMax = BuildConfig.MAX_TRYON_TOTAL_IMAGES.coerceAtLeast(2)
                            val allowedClothes = (totalMax - 1).coerceAtLeast(1)
                            if (clothingUris.size >= allowedClothes) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "의상 이미지는 최대 ${'$'}allowedClothes장까지 선택할 수 있습니다."
                                    )
                                }
                            } else {
                                pickClothingLauncher.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            }
                        },
                        onRemoveClothing = { index ->
                            clothingUris = clothingUris.filterIndexed { i, _ -> i != index }
                        },
                        onSelectFromWardrobe = { showWardrobePicker = true }
                )
            }

            // 3. 고급옵션 프롬프트 (가상피팅 프리뷰 생성 버튼 위 최하단)
            item {
                OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("고급 옵션: 프롬프트 (선택사항)") },
                        placeholder = { Text("입력하지 않으면 최적의 프롬프트를 자동으로 사용합니다") },
                        supportingText = {
                            Text(
                                text = "예: 상의는 약간 루즈핏, 광택은 줄이고 자연광 느낌",
                                style = MaterialTheme.typography.bodySmall,
                                color = FitGhostColors.TextTertiary
                            )
                        },
                        shape = RoundedCornerShape(CornerRadius.md),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = FitGhostColors.BgTertiary,
                            focusedBorderColor = FitGhostColors.AccentPrimary
                        )
                )
            }

            // 4. 가상 피팅 프리뷰 생성 버튼 (최하단)
            item {
                FittingActionButton(
                        enabled = modelUri != null && clothingUris.isNotEmpty() && !isProcessing,
                        isProcessing = isProcessing,
                        onRun = {
                            scope.launch {
                                if (modelUri != null && clothingUris.isNotEmpty()) {
                                    isProcessing = true

                                    // 최종 프롬프트: 사용자가 입력한 프롬프트(없으면 엔진에서 기본 템플릿 주입)
                                    var finalPrompt: String? = systemPrompt.ifBlank { null }
                                    Log.d(
                                            FIT_TAG,
                                            "Initial systemPrompt present=${finalPrompt != null}"
                                    )
                                    // 요구사항: 텍스트 가이드/추천 로직 사용 금지. 오직 모델샷+의상 합성만 수행.
// 기존 AI guidance 경로는 완전히 제거되었습니다.

                                    try {
                                        val result =
                                                engine.renderPreview(
                                                        context = context,
                                                        modelUri = modelUri!!,
                                                        clothingUris = clothingUris,
                                                        systemPrompt = finalPrompt
                                                )

                                        val savedFile =
                                                LocalImageStore.saveTryOnPng(context, result)
                                        Log.d(
                                                FIT_TAG,
                                                "Saved preview to: ${savedFile.absolutePath}"
                                        )

                                        scope.launch {
                                            lastSavedFile = savedFile
                                            com.fitghost.app.data.LocalImageStore.refresh(context)
                                            showPreviewDialog = true
                                        }
                                    } catch (e: Exception) {
                                        Log.e(FIT_TAG, "Try-on generation failed", e)
                                        scope.launch {
                                            val msg = when {
                                                e is javax.net.ssl.SSLPeerUnverifiedException ||
                                                        (e.message?.contains("Hostname", ignoreCase = true) == true &&
                                                         e.message?.contains("not verified", ignoreCase = true) == true) -> {
                                                    // 개발자 가이드 메시지(디버그 빌드 가정)
                                                    "Cloudflare 프록시 TLS 구성이 올바른지 확인하세요. local.properties의 PROXY_BASE_URL이 실제 워커 도메인과 일치해야 합니다."
                                                }
                                                else -> "가상 피팅 생성에 실패했습니다: ${e.message}"
                                            }
                                            snackbarHostState.showSnackbar(msg)
                                        }
                                    } finally {
                                        isProcessing = false
                                    }
                                }
                            }
                        }
                )
            }
        }

        // 옷장 선택 다이얼로그
        if (showWardrobePicker) {
            WardrobePickerDialog(
                    items = wardrobeUiState.items,
                    onItemsSelected = { selectedItems ->
                        val newUris =
                                selectedItems.mapNotNull { item ->
                                    item.imageUri?.let { Uri.parse(it) }
                                }
                        clothingUris = clothingUris + newUris
                        showWardrobePicker = false
                    },
                    onDismiss = { showWardrobePicker = false }
            )
        }
    }
}

/** 모델 사진 선택 섹션 (최근 사진 빠른 선택 + 사진 선택 버튼) */
@Composable
private fun ModelImagePickSection(
    selectedUri: Uri?, 
    recentPhotos: List<Uri>,
    onPick: () -> Unit, 
    onSelectRecent: (Uri) -> Unit,
    onClear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 제목과 사진 선택 버튼을 같은 행에 배치
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = "모델 사진을 선택해주세요",
                        style = MaterialTheme.typography.headlineMedium,
                        color = FitGhostColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                )
                Text(
                        text = "가상 피팅에 사용할 모델을 선택하세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FitGhostColors.TextSecondary
                )
            }

            // 사진 선택 버튼 (오른쪽 배치)
            OutlinedButton(
                    onClick = onPick,
                    shape = RoundedCornerShape(CornerRadius.md),
                    modifier = Modifier.padding(start = 16.dp)
            ) { Text("사진 선택") }
        }
        
        // 최근 사용한 모델 사진 빠른 선택 (1x3 그리드)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "최근 사용한 모델",
                style = MaterialTheme.typography.titleMedium,
                color = FitGhostColors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            
            // 1x3 그리드 레이아웃
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 150.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 실제 최근 사진들 표시
                items(recentPhotos.take(3)) { uri ->
                    RecentModelPhotoItem(
                        uri = uri,
                        isSelected = selectedUri == uri,
                        onSelect = { onSelectRecent(uri) }
                    )
                }
                
                // 빈 슬롯 채우기 (플레이스홀더)
                val emptySlots = 3 - recentPhotos.size.coerceAtMost(3)
                items(emptySlots) {
                    EmptyRecentPhotoPlaceholder(
                        onAddClick = onPick
                    )
                }
            }
        }

        // 선택된 이미지 표시
        Card(
                modifier =
                        Modifier.fillMaxWidth()
                                .then(
                                        if (selectedUri == null) Modifier.height(180.dp)
                                        else Modifier.wrapContentHeight()
                                ),
                colors = CardDefaults.cardColors(containerColor = FitGhostColors.BgSecondary)
        ) {
            if (selectedUri == null) {
                Box(
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        contentAlignment = Alignment.Center
                ) { Text(text = "이미지가 선택되지 않았습니다", color = FitGhostColors.TextSecondary) }
            } else {
                SubcomposeAsyncImage(
                        model = selectedUri,
                        contentDescription = null,
                        modifier =
                                Modifier.fillMaxWidth()
                                        .heightIn(min = 180.dp)
                                        .clip(RoundedCornerShape(CornerRadius.md))
                                        .clickable { /* TODO: full-screen preview */},
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(
                                    modifier = Modifier.fillMaxWidth().height(180.dp),
                                    contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator() }
                        },
                        error = {
                            Box(
                                    modifier = Modifier.fillMaxWidth().height(180.dp),
                                    contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                            imageVector = Icons.Outlined.BrokenImage,
                                            contentDescription = null
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(text = "이미지를 불러올 수 없습니다")
                                }
                            }
                        }
                )
                Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onClear, shape = RoundedCornerShape(CornerRadius.md)) {
                        Text("선택 해제")
                    }
                }
            }
        }
    }
}

/** 의상 선택 섹션 (3×n 그리드 레이아웃, 추가+옷장선택 버튼 연결) */
@Composable
private fun ClothingSelectionSection(
        clothingUris: List<Uri>,
        onAddClothing: () -> Unit,
        onRemoveClothing: (Int) -> Unit,
        onSelectFromWardrobe: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
                text = "의상 사진을 선택해주세요",
                style = MaterialTheme.typography.headlineMedium,
                color = FitGhostColors.TextPrimary,
                fontWeight = FontWeight.Bold
        )

        // 추가 + 옷장에서 선택 버튼들 (연결 배치)
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                    onClick = onAddClothing,
                    modifier = Modifier.weight(1f),
                    colors =
                            ButtonDefaults.buttonColors(
                                    containerColor = FitGhostColors.AccentPrimary,
                                    disabledContainerColor = FitGhostColors.BgTertiary
                            ),
            ) {
                Icon(imageVector = Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("추가")
            }

            OutlinedButton(onClick = onSelectFromWardrobe, modifier = Modifier.weight(1f)) {
                Icon(imageVector = Icons.Outlined.Checkroom, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("옷장에서 선택")
            }
        }

        // 선택된 의상들을 3×n 그리드로 표시
        if (clothingUris.isNotEmpty()) {
            LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.heightIn(max = 400.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(clothingUris.size) { index ->
                    ClothingImageItem(
                            uri = clothingUris[index],
                            onRemove = { onRemoveClothing(index) }
                    )
                }
            }
        } else {
            Card(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    colors = CardDefaults.cardColors(containerColor = FitGhostColors.BgSecondary)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "의상이 선택되지 않았습니다", color = FitGhostColors.TextSecondary)
                }
            }
        }
    }
}

/** 최근 모델 사진 아이템 (1x3 그리드용) */
@Composable
private fun RecentModelPhotoItem(
    uri: Uri,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable { onSelect() },
        shape = RoundedCornerShape(CornerRadius.md),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                FitGhostColors.AccentPrimary.copy(alpha = 0.1f)
            } else {
                FitGhostColors.BgSecondary
            }
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(
                2.dp,
                FitGhostColors.AccentPrimary
            )
        } else null
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SubcomposeAsyncImage(
                model = uri,
                contentDescription = "최근 모델 사진",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(IconSize.lg),
                            color = FitGhostColors.AccentPrimary,
                            strokeWidth = 2.dp
                        )
                    }
                },
                error = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BrokenImage,
                            contentDescription = null,
                            tint = FitGhostColors.TextTertiary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            )

            // 선택된 표시
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "선택됨",
                    tint = FitGhostColors.AccentPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(IconSize.lg)
                )
            }
        }
    }
}

/** 빈 최근 모델 사진 슬롯 플레이스홀더 */
@Composable
private fun EmptyRecentPhotoPlaceholder(
    onAddClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable { onAddClick() },
        shape = RoundedCornerShape(CornerRadius.md),
        colors = CardDefaults.cardColors(
            containerColor = FitGhostColors.BgSecondary
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            FitGhostColors.TextTertiary.copy(alpha = 0.3f)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AddAPhoto,
                    contentDescription = "사진 추가",
                    tint = FitGhostColors.TextTertiary.copy(alpha = 0.5f),
                    modifier = Modifier.size(IconSize.xl)
                )
                Text(
                    text = "사진 추가",
                    style = MaterialTheme.typography.bodySmall,
                    color = FitGhostColors.TextTertiary.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/** 의상 이미지 아이템 (3×n 그리드용) */
@Composable
private fun ClothingImageItem(uri: Uri, onRemove: () -> Unit) {
    Box {
        SubcomposeAsyncImage(
                model = uri,
                contentDescription = null,
                modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(CornerRadius.md)),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(IconSize.lg))
                    }
                },
                error = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                                imageVector = Icons.Outlined.BrokenImage,
                                contentDescription = null,
                                tint = FitGhostColors.TextSecondary
                        )
                    }
                }
        )

        // 제거 버튼
        IconButton(onClick = onRemove, modifier = Modifier.align(Alignment.TopEnd)) {
            Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "제거",
                    tint = FitGhostColors.Error
            )
        }
    }
}

@Composable
private fun FittingActionButton(enabled: Boolean, isProcessing: Boolean, onRun: () -> Unit) {
    Button(
            onClick = onRun,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors =
                    ButtonDefaults.buttonColors(
                            containerColor = FitGhostColors.AccentPrimary,
                            disabledContainerColor = FitGhostColors.BgTertiary
                    ),
            shape = RoundedCornerShape(Spacing.lg)
    ) {
        if (isProcessing) {
            CircularProgressIndicator(
                    modifier = Modifier.size(IconSize.md),
                    color = FitGhostColors.TextInverse,
                    strokeWidth = 2.dp
            )
            Spacer(Modifier.width(8.dp))
            Text("생성 중...")
        } else {
            Text(
                    text = "가상 피팅 프리뷰 생성",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
            )
        }
    }
}

/** 옷장 선택 다이얼로그 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WardrobePickerDialog(
        items: List<WardrobeItemEntity>,
        onItemsSelected: (List<WardrobeItemEntity>) -> Unit,
        onDismiss: () -> Unit
) {
    var selectedItems by remember { mutableStateOf<Set<WardrobeItemEntity>>(emptySet()) }

    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("옷장에서 선택") },
            text = {
                LazyColumn(
                        modifier = Modifier.height(400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items) { item ->
                        Card(
                                modifier =
                                        Modifier.fillMaxWidth().clickable {
                                            selectedItems =
                                                    if (selectedItems.contains(item)) {
                                                        selectedItems - item
                                                    } else {
                                                        selectedItems + item
                                                    }
                                        },
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor =
                                                        if (selectedItems.contains(item)) {
                                                            FitGhostColors.BgTertiary
                                                        } else {
                                                            FitGhostColors.BgSecondary
                                                        }
                                        )
                        ) {
                            Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                item.imageUri?.let { uri ->
                                    AsyncImage(
                                            model = uri,
                                            contentDescription = item.name,
                                            modifier =
                                                    Modifier.size(60.dp)
                                                            .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                            text =
                                                    "${WardrobeUiUtil.categoryLabel(item.category)} • ${item.color.orEmpty()}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = FitGhostColors.TextSecondary
                                    )
                                }
                                if (selectedItems.contains(item)) {
                                    Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "선택됨",
                                            tint = FitGhostColors.AccentPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                        onClick = { onItemsSelected(selectedItems.toList()) },
                        enabled = selectedItems.isNotEmpty()
                ) { Text("선택 (${selectedItems.size})") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

private const val FIT_TAG = "FittingScreen"
