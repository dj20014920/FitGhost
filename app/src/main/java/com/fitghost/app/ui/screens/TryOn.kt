package com.fitghost.app.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.fitghost.app.ads.RewardedAdController
import com.fitghost.app.data.CreditStore
import com.fitghost.app.data.LocalImageStore
import com.fitghost.app.data.TryOnRepository
import com.fitghost.app.engine.GeminiTryOnEngine
import com.fitghost.app.ui.theme.NeumorphicButton
import com.fitghost.app.ui.theme.NeumorphicCard
import com.fitghost.app.util.TryOnBridge
import kotlinx.coroutines.launch

@Composable
fun TryOnScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val creditStore = remember { CreditStore(context) }
    val repo = remember {
        TryOnRepository(context, creditStore, GeminiTryOnEngine(), LocalImageStore(context))
    }
    val ad = remember { RewardedAdController(context) }

    var picked by remember { mutableStateOf<android.net.Uri?>(null) }
    var resultUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var message by remember { mutableStateOf("") }
    var part by remember { mutableStateOf("TOP") }
    var itemUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val picker =
            rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                picked = uri
            }

    LaunchedEffect(Unit) {
        TryOnBridge.consume()?.let { intent ->
            part = intent.part.wire
            // 추천 아이템 이미지 URI가 있으면 미리 선택
            intent.itemImageUri?.let { s ->
                runCatching { Uri.parse(s) }.getOrNull()?.let { parsed -> itemUri = parsed }
            }
            if (intent.autoLaunchPicker) {
                picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        }
    }

    Column(Modifier.padding(16.dp)) {
        Row {
            NeumorphicButton(
                    onClick = {
                        picker.launch(
                                PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                        )
                    }
            ) { Text("사진 선택") }
            Spacer(Modifier.width(8.dp))
            com.fitghost.app.ui.components.NeumorphicSegmentedControl(
                    options = listOf("상의", "하의"),
                    selectedIndex = if (part == "TOP") 0 else 1,
                    onSelect = { part = if (it == 0) "TOP" else "BOTTOM" },
                    modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))

        AnimatedVisibility(
                visible = picked != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
        ) {
            picked?.let {
                NeumorphicCard {
                    Image(
                            painter = rememberAsyncImagePainter(it),
                            contentDescription = "선택 이미지",
                            modifier = Modifier.height(220.dp).fillMaxWidth()
                    )
                }
            }
        }

        // 추천 아이템 프리뷰
        AnimatedVisibility(
                visible = itemUri != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
        ) {
            itemUri?.let {
                NeumorphicCard {
                    Column(Modifier.padding(8.dp)) {
                        Text("추천 아이템", style = MaterialTheme.typography.bodySmall)
                        Image(
                                painter = rememberAsyncImagePainter(it),
                                contentDescription = "추천 아이템 이미지",
                                modifier = Modifier.height(140.dp).fillMaxWidth()
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        NeumorphicButton(
                onClick = {
                    val uri = picked ?: return@NeumorphicButton
                    scope.launch {
                        val input = context.contentResolver.openInputStream(uri) ?: return@launch
                        val bmp = BitmapFactory.decodeStream(input)
                        val garmentBmp =
                                itemUri?.let { guri ->
                                    context.contentResolver.openInputStream(guri)?.use { gi ->
                                        BitmapFactory.decodeStream(gi)
                                    }
                                }
                        when (val r = repo.runTryOn(bmp, part, garmentBmp)) {
                            is TryOnRepository.Result.Success -> {
                                resultUri = r.uri
                                message = "저장 성공"
                            }
                            is TryOnRepository.Result.NoCredit -> {
                                message = "크레딧 부족: 광고 시청으로 +1"
                                ad.load(
                                        onLoaded = {
                                            ad.show(
                                                    activity =
                                                            (context as
                                                                    androidx.activity.ComponentActivity),
                                                    onReward = { reward ->
                                                        scope.launch { creditStore.addBonusOne() }
                                                    }
                                            )
                                        }
                                )
                            }
                            is TryOnRepository.Result.Error -> {
                                message = "오류: ${r.throwable.message}"
                            }
                        }
                    }
                }
        ) { Text("프리뷰 생성/저장") }
        Spacer(Modifier.height(8.dp))

        AnimatedVisibility(
                visible = resultUri != null || message.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
        ) {
            NeumorphicCard {
                Column(Modifier.padding(12.dp)) {
                    resultUri?.let {
                        Text("결과 저장됨: $it", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (message.isNotEmpty()) {
                        Text(message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
