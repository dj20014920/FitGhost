package com.fitghost.app.ui.screens.wardrobe

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.fitghost.app.data.db.WardrobeCategory
import com.fitghost.app.data.db.WardrobeItemEntity
import com.fitghost.app.ui.theme.FitGhostColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

// 이미지 프리뷰 동적 크기 및 저장 축소 관련 상수
private val MaxPreviewHeightDp = 360.dp // 프리뷰 최대 높이 한도
private const val MAX_SAVE_DIMENSION_PX = 1280 // 저장 시 축소할 최대 한변 픽셀 (긴 변 기준)
private const val SAVE_JPEG_QUALITY = 85 // 저장 JPEG 품질

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WardrobeAddScreen(
        onSavedAndClose: () -> Unit,
        onNavigateBack: () -> Unit,
        existingItemId: Long? = null, // 편집 모드 지원을 위한 선택적 파라미터 (null이면 신규 추가)
        viewModel: WardrobeViewModel =
                viewModel(factory = WardrobeViewModelFactory(LocalContext.current))
) {
    // 현재 단계: 신규 추가만 고려. 향후 편집 기능 확장 시
    // existingItemId로 기존 아이템을 로드하여 상태 초기화

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 입력 상태들
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(WardrobeCategory.OTHER) }
    var brand by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("") }
    var size by remember { mutableStateOf("") }
    var detailType by remember { mutableStateOf("") } // 상세분류 (예: 후드티 / 블레이저 등 자유 입력)
    var description by remember { mutableStateOf("") } // 설명
    var tagsRaw by remember { mutableStateOf("") } // 추가 태그(콤마 구분)
    var favorite by remember { mutableStateOf(false) }
    var preference by remember { mutableStateOf(5.0f) } // 0.0~10.0 (별 5개 시각화: 1별=2.0)
    var pattern by remember { mutableStateOf("") } // 패턴 선택(선택)
    var imageUri by remember { mutableStateOf<String?>(null) }

    // UI 상태들
    var showColorDialog by remember { mutableStateOf(false) }
    var showPatternDialog by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf(false) }

    // 입력 가이드 표시 여부
    val showNameHelper = name.isBlank()

    // 갤러리에서 이미지 선택
    val pickImageLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) {
                    uri: Uri? ->
                imageUri = uri?.toString()
            }

    // 카테고리 메뉴 표시 여부
    var catMenu by remember { mutableStateOf(false) }

    fun save() {
        if (name.isBlank()) {
            scope.launch {
                snackbarHostState.showSnackbar("이름을 입력해 주세요", duration = SnackbarDuration.Short)
            }
            return
        }

        scope.launch {
            withContext(Dispatchers.IO) {
                val finalImageUri = imageUri?.let { uriStr ->
                    val sourceUri = Uri.parse(uriStr)
                    // 이미지를 축소하여 저장
                    resizeAndPersist(context, sourceUri, MAX_SAVE_DIMENSION_PX, SAVE_JPEG_QUALITY)?.toString()
                }

                val parsedTags = tagsRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                val newItem = WardrobeItemEntity(
                        id = 0L, // 자동 증가
                        name = name,
                        category = category,
                        imageUri = finalImageUri,
                        brand = brand.ifBlank { null },
                        color = color.ifBlank { null },
                        size = size.ifBlank { null },
                        favorite = favorite,
                        tags = parsedTags,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                )

                viewModel.upsert(newItem)

                withContext(Dispatchers.Main) {
                    onSavedAndClose()
                }
            }
        }
    }

    Scaffold(
            topBar = {
                TopAppBar(
                        title = {
                            Text(
                                    text = "아이템 추가",
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.Outlined.ArrowBack, contentDescription = "뒤로")
                            }
                        },
                        actions = { TextButton(onClick = { save() }) { Text("저장") } },
                        colors =
                                TopAppBarDefaults.topAppBarColors(
                                        containerColor = FitGhostColors.BgGlass
                                )
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            containerColor = FitGhostColors.BgPrimary
    ) { inner ->
        Column(
                modifier =
                        Modifier.padding(inner)
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 사용 팁
            AssistiveNote()

            // 이미지 선택 섹션 (동적 크기 조절)
            ImagePickerSection(
                    imageUri = imageUri,
                    onPickImage = { pickImageLauncher.launch("image/*") }
            )

            // 기본 정보 입력 카드
            Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = FitGhostColors.BgSecondary)
            ) {
                Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                            text = "기본 정보",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = FitGhostColors.TextPrimary
                    )

                    // 이름 입력
                    OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("이름 *") },
                            placeholder = { Text("예: 베이직 블랙 티셔츠") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            isError = showNameHelper,
                            supportingText = { if (showNameHelper) Text("필수 항목입니다") }
                    )

                    // 카테고리 선택
                    ExposedDropdownMenuBox(
                            expanded = catMenu,
                            onExpandedChange = { catMenu = it }
                    ) {
                        OutlinedTextField(
                                value = categoryName(category),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("카테고리") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catMenu) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                                expanded = catMenu,
                                onDismissRequest = { catMenu = false }
                        ) {
                            WardrobeCategory.values().forEach { cat ->
                                DropdownMenuItem(
                                        text = { Text(categoryName(cat)) },
                                        onClick = {
                                            category = cat
                                            catMenu = false
                                        }
                                )
                            }
                        }
                    }

                    // 브랜드 입력
                    OutlinedTextField(
                            value = brand,
                            onValueChange = { brand = it },
                            label = { Text("브랜드") },
                            placeholder = { Text("예: 유니클로") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                }
            }

            // 상세 정보 카드
            Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = FitGhostColors.BgSecondary)
            ) {
                Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                            text = "상세 정보",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = FitGhostColors.TextPrimary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                                value = color,
                                onValueChange = { color = it },
                                label = { Text("색상") },
                                placeholder = { Text("블랙") },
                                modifier = Modifier.weight(1f),
                                trailingIcon = {
                                    IconButton(onClick = { showColorDialog = true }) {
                                        Icon(Icons.Outlined.Palette, contentDescription = "색상 선택")
                                    }
                                }
                        )
                        OutlinedTextField(
                                value = size,
                                onValueChange = { size = it },
                                label = { Text("사이즈") },
                                placeholder = { Text("M") },
                                modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                            value = detailType,
                            onValueChange = { detailType = it },
                            label = { Text("상세 분류") },
                            placeholder = { Text("예: 후드티, 블레이저, 스키니진 등") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { showDetailDialog = true }) {
                                    Icon(Icons.Outlined.Category, contentDescription = "분류 선택")
                                }
                            }
                    )

                    OutlinedTextField(
                            value = pattern,
                            onValueChange = { pattern = it },
                            label = { Text("패턴/소재") },
                            placeholder = { Text("예: 무지, 스트라이프, 면 100%") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { showPatternDialog = true }) {
                                    Icon(Icons.Outlined.Texture, contentDescription = "패턴 선택")
                                }
                            }
                    )

                    OutlinedTextField(
                            value = tagsRaw,
                            onValueChange = { tagsRaw = it },
                            label = { Text("태그") },
                            placeholder = { Text("캐주얼, 데일리, 편안함 (콤마로 구분)") },
                            modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("메모") },
                            placeholder = { Text("이 아이템에 대한 추가 설명") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 3
                    )
                }
            }

            // 선호도 설정
            PreferenceSection(
                    favorite = favorite,
                    onFavoriteChange = { favorite = it },
                    preference = preference,
                    onPreferenceChange = { preference = it }
            )
        }
        
    // 다이얼로그들
    if (showColorDialog) {
        SimpleSelectionDialog(
                title = "색상 선택",
                options = listOf("블랙", "화이트", "그레이", "네이비", "브라운", "베이지", "레드", "블루", "그린", "옐로우", "핑크", "퍼플"),
                onSelect = { color = it },
                onDismiss = { showColorDialog = false }
        )
    }

    if (showPatternDialog) {
        SimpleSelectionDialog(
                title = "패턴 선택",
                options = listOf("무지", "스트라이프", "체크", "도트", "플로럴", "지오메트릭", "애니멀", "기타 패턴"),
                onSelect = { pattern = it },
                onDismiss = { showPatternDialog = false }
        )
    }

    if (showDetailDialog) {
        SimpleSelectionDialog(
                title = "상세 분류",
                options = listOf("티셔츠", "셔츠", "후드티", "스웨터", "블레이저", "코트", "청바지", "슬랙스", "스커트", "원피스", "스니커즈", "구두", "부츠"),
                onSelect = { detailType = it },
                onDismiss = { showDetailDialog = false }
        )
    }
}
}

@Composable
private fun AssistiveNote() {
    Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = FitGhostColors.BgGlass),
            shape = RoundedCornerShape(12.dp)
    ) {
        Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                    imageVector = Icons.Outlined.Lightbulb,
                    contentDescription = null,
                    tint = FitGhostColors.AccentPrimary,
                    modifier = Modifier.size(20.dp)
            )
            Text(
                    text = "사진을 추가하면 AI가 더 정확한 스타일링을 제안할 수 있어요!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FitGhostColors.TextSecondary
            )
        }
    }
}

@Composable
private fun ImagePickerSection(
        imageUri: String?,
        onPickImage: () -> Unit
) {
    val context = LocalContext.current

    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = FitGhostColors.BgSecondary)
    ) {
        Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                    text = "사진",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FitGhostColors.TextPrimary
            )

            if (imageUri == null) {
                // 이미지 선택 버튼
                ElevatedButton(
                        onClick = onPickImage,
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                                imageVector = Icons.Outlined.AddAPhoto,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp)
                        )
                        Text("사진 선택")
                    }
                }
            } else {
                // 이미지 프리뷰 (동적 크기 조절)
                BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth()
                ) {
                    val maxWidthPx = with(LocalDensity.current) {
                        maxWidth.toPx()
                    }

                    val imageSize = getImageSize(context, Uri.parse(imageUri))
                    
                    val previewHeight = if (imageSize != null) {
                        val (origW, origH) = imageSize
                        val aspectRatio = origW.toFloat() / origH.toFloat()
                        val calculatedHeight = maxWidthPx / aspectRatio
                        val maxHeightPx = with(LocalDensity.current) {
                            MaxPreviewHeightDp.toPx()
                        }
                        
                        val finalHeightPx = kotlin.math.min(calculatedHeight, maxHeightPx)
                        with(LocalDensity.current) { finalHeightPx.toDp() }
                    } else {
                        200.dp // 기본값
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AsyncImage(
                                model = imageUri,
                                contentDescription = "선택된 이미지",
                                modifier = Modifier
                                        .fillMaxWidth()
                                        .height(previewHeight)
                                        .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Fit
                        )
                        
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                    onClick = onPickImage,
                                    modifier = Modifier.weight(1f)
                            ) {
                                Text("다른 사진 선택")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferenceSection(
        favorite: Boolean,
        onFavoriteChange: (Boolean) -> Unit,
        preference: Float,
        onPreferenceChange: (Float) -> Unit
) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = FitGhostColors.BgSecondary)
    ) {
        Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                    text = "선호도",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FitGhostColors.TextPrimary
            )

            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                        text = "즐겨찾기",
                        style = MaterialTheme.typography.bodyLarge,
                        color = FitGhostColors.TextPrimary
                )
                Switch(
                        checked = favorite,
                        onCheckedChange = onFavoriteChange
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                        text = "선호도 평가",
                        style = MaterialTheme.typography.bodyLarge,
                        color = FitGhostColors.TextPrimary
                )
                StarRow(
                        rating = preference,
                        onRatingChange = onPreferenceChange
                )
            }
        }
    }
}

@Composable
private fun StarRow(
        rating: Float,
        onRatingChange: (Float) -> Unit
) {
    Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(5) { index ->
            val filled = rating >= (index + 1) * 2.0f
            val halfFilled = !filled && rating >= (index * 2.0f + 1.0f)
            IconButton(
                    onClick = {
                        val newRating = (index + 1) * 2.0f
                        onRatingChange(newRating)
                    }
            ) {
                Icon(
                        imageVector = when {
                            filled -> Icons.Outlined.Star
                            halfFilled -> Icons.Outlined.StarHalf
                            else -> Icons.Outlined.StarBorder
                        },
                        contentDescription = null,
                        tint = if (filled || halfFilled) FitGhostColors.AccentPrimary else FitGhostColors.TextTertiary,
                        modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun SimpleSelectionDialog(
    title: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { opt ->
                    ElevatedButton(
                        onClick = {
                            onSelect(opt)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(opt)
                    }
                }
            }
        }
    )
}

// -------- 이미지 유틸: 원본 메타 크기, 샘플링, 리사이즈/저장 --------
private fun getImageSize(context: android.content.Context, uri: Uri): Pair<Int, Int>? {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, opts)
            val w = opts.outWidth
            val h = opts.outHeight
            if (w > 0 && h > 0) Pair(w, h) else null
        }
    }.getOrNull()
}

private fun calculateInSampleSize(origW: Int, origH: Int, maxDim: Int): Int {
    var inSample = 1
    var halfW = origW / 2
    var halfH = origH / 2
    while ((halfW / inSample) >= maxDim || (halfH / inSample) >= maxDim) {
        inSample *= 2
    }
    return max(1, inSample)
}

private fun resizeBitmapIfNeeded(src: Bitmap, maxDim: Int): Bitmap {
    val w = src.width
    val h = src.height
    if (w <= maxDim && h <= maxDim) return src
    val scale = if (w >= h) maxDim.toFloat() / w.toFloat() else maxDim.toFloat() / h.toFloat()
    val nw = (w * scale).toInt().coerceAtLeast(1)
    val nh = (h * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(src, nw, nh, true)
}

private fun ensureWardrobeDir(context: android.content.Context): File {
    val dir = File(context.filesDir, "wardrobe")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

private fun makeOutputFile(context: android.content.Context): File {
    val ts = System.currentTimeMillis()
    return File(ensureWardrobeDir(context), "img_$ts.jpg")
}

private fun decodeSampledBitmap(context: android.content.Context, uri: Uri, maxDim: Int): Bitmap? {
    val size = getImageSize(context, uri) ?: return null
    val inSample = calculateInSampleSize(size.first, size.second, maxDim)
    val opts = BitmapFactory.Options().apply { inSampleSize = inSample }
    return context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, opts)
    }
}

private fun persistJpeg(context: android.content.Context, bmp: Bitmap, quality: Int): Uri? {
    val outFile = makeOutputFile(context)
    return runCatching {
        FileOutputStream(outFile).use { fos ->
            bmp.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), fos)
        }
        Uri.fromFile(outFile)
    }.getOrNull()
}

private fun resizeAndPersist(
    context: android.content.Context,
    source: Uri,
    maxDimensionPx: Int,
    quality: Int
): Uri? {
    val decoded = decodeSampledBitmap(context, source, maxDimensionPx) ?: return null
    val resized = resizeBitmapIfNeeded(decoded, maxDimensionPx)
    if (resized !== decoded) {
        decoded.recycle()
    }
    return persistJpeg(context, resized, quality)
}

// 카테고리 이름 표시
private fun categoryName(category: WardrobeCategory): String = WardrobeUiUtil.categoryLabel(category)
