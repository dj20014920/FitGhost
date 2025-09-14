package com.fitghost.app.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fitghost.app.ads.RewardedAdController
import com.fitghost.app.data.CreditStore
import com.fitghost.app.data.LocalImageStore
import com.fitghost.app.data.TryOnRepository
import com.fitghost.app.engine.FakeTryOnEngine
import com.fitghost.app.ui.components.*
import com.fitghost.app.ui.theme.*
import kotlinx.coroutines.launch
import com.fitghost.app.util.TryOnBridge

@Composable
fun TryOnScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val creditStore = remember { CreditStore(context) }
    val repo = remember { TryOnRepository(context, creditStore, FakeTryOnEngine(), LocalImageStore(context)) }
    val ad = remember { RewardedAdController(context) }

    var modelUri by remember { mutableStateOf<Uri?>(null) }
    var garmentUri by remember { mutableStateOf<Uri?>(null) }
    var resultUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var part by remember { mutableStateOf("TOP") }
    var showAdDialog by remember { mutableStateOf(false) }

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> modelUri = uri }
    val garmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> garmentUri = uri }

    // 2-A: Home→TryOn 브리지 소비, 자동 포토 피커 실행
    LaunchedEffect(Unit) {
        TryOnBridge.consume()?.let { intent ->
            part = intent.part.wire
            if (intent.itemImageUri != null) {
                garmentUri = Uri.parse(intent.itemImageUri)
            }
            if (intent.autoLaunchPicker) {
                modelPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
    }

    fun runTryOn() {
        val mUri = modelUri ?: return
        val gUri = garmentUri ?: return
        scope.launch {
            isLoading = true
            message = ""
            resultUri = null
            val modelBmp = context.contentResolver.openInputStream(mUri)?.use { BitmapFactory.decodeStream(it) }
            val garmentBmp = context.contentResolver.openInputStream(gUri)?.use { BitmapFactory.decodeStream(it) }

            if (modelBmp == null || garmentBmp == null) {
                message = "이미지를 불러오는데 실패했습니다."
                isLoading = false
                return@launch
            }

            when (val result = repo.runTryOn(modelBmp, part, garmentBmp)) {
                is TryOnRepository.Result.Success -> {
                    resultUri = result.uri
                    message = "성공! 갤러리에서 저장된 이미지를 확인하세요."
                }
                is TryOnRepository.Result.NoCredit -> {
                    showAdDialog = true
                }
                is TryOnRepository.Result.Error -> {
                    message = "오류: ${result.throwable.message}"
                }
            }
            isLoading = false
        }
    }

    if (showAdDialog) {
        AlertDialog(
            onDismissRequest = { showAdDialog = false },
            title = { Text("크레딧 부족") },
            text = { Text("광고를 시청하고 크레딧을 얻으시겠습니까?") },
            confirmButton = {
                TextButton(onClick = {
                    ad.load(onLoaded = {
                        ad.show(
                            activity = (context as androidx.activity.ComponentActivity),
                            onReward = { scope.launch { creditStore.addBonusOne() } }
                        )
                    })
                    showAdDialog = false
                }) { Text("시청하기") }
            },
            dismissButton = { TextButton(onClick = { showAdDialog = false }) { Text("취소") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Enhanced Header
        FloatingGlassPanel(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "가상 피팅",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "AI로 완벽한 피팅을 경험하세요",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Icon(
                        Icons.Default.Style,
                        contentDescription = "Try On",
                        modifier = Modifier
                            .padding(12.dp)
                            .size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Crossfade(targetState = isLoading || resultUri != null, label = "TryOnState") {
            isResultState ->
            if (isResultState) {
                // Enhanced Result/Loading View
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (isLoading) {
                        LiquidGlassCard(
                            shape = RoundedCornerShape(32.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                NeumorphicCircularProgress(modifier = Modifier.size(64.dp))
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "AI가 완벽한 피팅을\n생성하고 있습니다",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        resultUri?.let {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                LiquidGlassCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(24.dp)
                                ) {
                                    AsyncImage(
                                        model = it,
                                        contentDescription = "Result Image",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(0.75f),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Spacer(Modifier.height(24.dp))
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                ) {
                                    Text(
                                        message,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(16.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                                Spacer(Modifier.height(24.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    LiquidGlassButton(
                                        onClick = { resultUri = null; modelUri = null; garmentUri = null },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("다시하기", fontWeight = FontWeight.SemiBold)
                                    }
                                    GlowingIconButton(
                                        onClick = { /* Share functionality */ },
                                        glowColor = MaterialTheme.colorScheme.primary
                                    ) {
                                        Icon(
                                            Icons.Default.Share,
                                            contentDescription = "Share",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Enhanced Input View
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Image Selection Section
                    LiquidGlassCard(
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column {
                            Text(
                                "이미지 선택",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                EnhancedImagePickerBox(
                                    modifier = Modifier.weight(1f),
                                    title = "모델 사진",
                                    subtitle = "본인 사진을 선택하세요",
                                    icon = Icons.Default.Person,
                                    uri = modelUri
                                ) {
                                    modelPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                                EnhancedImagePickerBox(
                                    modifier = Modifier.weight(1f),
                                    title = "의상 사진",
                                    subtitle = "입고 싶은 옷을 선택하세요",
                                    icon = Icons.Default.Checkroom,
                                    uri = garmentUri
                                ) {
                                    garmentPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            }
                        }
                    }
                    
                    // Part Selection Section
                    LiquidGlassCard(
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 16.dp)
                            ) {
                                Icon(
                                    Icons.Default.Style,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "피팅 부위 선택",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            NeumorphicSegmentedControl(
                                options = listOf("상의", "하의"),
                                selectedIndex = if (part == "TOP") 0 else 1,
                                onSelect = { part = if (it == 0) "TOP" else "BOTTOM" },
                                modifier = Modifier.fillMaxWidth(),
                                useLiquidGlass = true
                            )
                        }
                    }
                    
                    // Action Button
                    LiquidGlassButton(
                        onClick = { runTryOn() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = modelUri != null && garmentUri != null,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "AI 가상 피팅 시작",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EnhancedImagePickerBox(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    uri: Uri?,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    
    val animatedScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "picker_scale"
    )

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            isPressed = when (interaction) {
                is androidx.compose.foundation.interaction.PressInteraction.Press -> true
                else -> false
            }
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(0.8f)
            .scale(animatedScale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .neumorphic(
                shape = RoundedCornerShape(20.dp),
                elevation = if (uri != null) 12.dp else 8.dp,
                backgroundColor = MaterialTheme.colorScheme.surface,
                isPressed = isPressed
            ),
        contentAlignment = Alignment.Center
    ) {
        if (uri != null) {
            AsyncImage(
                model = uri,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Success indicator
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        modifier = Modifier
                            .padding(4.dp)
                            .size(16.dp),
                        tint = Color.White
                    )
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        modifier = Modifier
                            .padding(12.dp)
                            .size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
