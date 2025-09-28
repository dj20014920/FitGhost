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
import com.fitghost.app.data.CreditStore
import com.fitghost.app.data.LocalImageStore
import com.fitghost.app.data.db.WardrobeItemEntity
import com.fitghost.app.engine.TryOnEngine
import com.fitghost.app.ui.screens.wardrobe.WardrobeUiUtil
import com.fitghost.app.ui.screens.wardrobe.WardrobeViewModel
import com.fitghost.app.ui.screens.wardrobe.WardrobeViewModelFactory
import com.fitghost.app.ui.theme.FitGhostColors
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

    var modelUri by remember { mutableStateOf<Uri?>(null) }
    var clothingUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var systemPrompt by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var showWardrobePicker by remember { mutableStateOf(false) }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var lastSavedFile by remember { mutableStateOf<java.io.File?>(null) }

    val pickModelLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia()
            ) { uri -> modelUri = uri }

    val pickClothingLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia()
            ) { uri -> uri?.let { clothingUris = clothingUris + it } }

    val engine: TryOnEngine = remember { CompositeTryOnEngine() }
    val creditStore = remember { CreditStore(context) }
    val activity = remember(context) { context as? android.app.Activity }
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
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
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
                        onPick = {
                            pickModelLauncher.launch(
                                    PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                            )
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
                        label = { Text("고급 옵션: 프롬프트") },
                        placeholder = { Text("예) 상의는 약간 루즈핏, 광택은 줄이고 자연광 느낌") },
                        shape = RoundedCornerShape(12.dp)
                )
            }

            // 4. 가상 피팅 프리뷰 생성 버튼 (최하단)
            item {
                FittingActionButton(
                        enabled = modelUri != null && clothingUris.isNotEmpty() && !isProcessing,
                        isProcessing = isProcessing,
                        onRun = {
                            scope.launch {
                                // 크레딧 차감
                                val ok = creditStore.consumeOne()
                                if (!ok) {
                                    val res =
                                            snackbarHostState.showSnackbar(
                                                    message = "크레딧이 부족합니다.",
                                                    actionLabel = "광고보고 +1"
                                            )
                                    if (res == SnackbarResult.ActionPerformed) {
                                        activity?.let { act ->
                                            val app = act.application as? com.fitghost.app.App
                                            val controller = app?.rewardedAdController
                                            if (controller != null) {
                                                controller.showAd(
                                                        activity = act,
                                                        onReward = { _ ->
                                                            // 광고 시청 리워드 지급 시 보너스 +1
                                                            scope.launch {
                                                                creditStore.addBonusOne()
                                                            }
                                                        }
                                                )
                                            }
                                        }
                                    }
                                }
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
                                                    "서버 TLS 인증서와 도메인이 일치하지 않습니다. local.properties에서 NANOBANANA_BASE_URL을 실제 배포 도메인(예: <app>.up.railway.app)으로 임시 지정하거나, 서버 인증서에 api.nanobanana.ai SAN을 추가하세요."
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

/** 모델 사진 선택 섹션 (사진 선택 버튼을 오른쪽에 배치) */
@Composable
private fun ModelImagePickSection(selectedUri: Uri?, onPick: () -> Unit, onClear: () -> Unit) {
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
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(start = 16.dp)
            ) { Text("사진 선택") }
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
                                        .clip(RoundedCornerShape(12.dp))
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
                    OutlinedButton(onClick = onClear, shape = RoundedCornerShape(12.dp)) {
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

/** 의상 이미지 아이템 (3×n 그리드용) */
@Composable
private fun ClothingImageItem(uri: Uri, onRemove: () -> Unit) {
    Box {
        SubcomposeAsyncImage(
                model = uri,
                contentDescription = null,
                modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
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
            shape = RoundedCornerShape(16.dp)
    ) {
        if (isProcessing) {
            CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
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
