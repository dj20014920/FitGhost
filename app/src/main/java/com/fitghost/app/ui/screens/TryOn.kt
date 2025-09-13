package com.fitghost.app.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fitghost.app.ads.RewardedAdController
import com.fitghost.app.data.CreditStore
import com.fitghost.app.data.LocalImageStore
import com.fitghost.app.data.TryOnRepository
import com.fitghost.app.engine.GeminiTryOnEngine
import com.fitghost.app.ui.components.NeumorphicSegmentedControl
import com.fitghost.app.ui.theme.NeumorphicButton
import com.fitghost.app.ui.theme.NeumorphicCard
import com.fitghost.app.ui.theme.NeumorphicCircularProgress
import kotlinx.coroutines.launch

@Composable
fun TryOnScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val creditStore = remember { CreditStore(context) }
    val repo = remember { TryOnRepository(context, creditStore, GeminiTryOnEngine(), LocalImageStore(context)) }
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
        // Header
        Text(
            text = "가상 피팅",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )

        Crossfade(targetState = isLoading || resultUri != null, label = "TryOnState") {
            isResultState ->
            if (isResultState) {
                // Result/Loading View
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (isLoading) {
                        NeumorphicCircularProgress(modifier = Modifier.size(64.dp))
                    } else {
                        resultUri?.let {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                                AsyncImage(model = it, contentDescription = "Result Image", modifier = Modifier.fillMaxWidth().weight(1f), contentScale = ContentScale.Fit)
                                Spacer(Modifier.height(16.dp))
                                Text(message, style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.height(16.dp))
                                NeumorphicButton(onClick = { resultUri = null; modelUri = null; garmentUri = null }) {
                                    Text("다시하기")
                                }
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                    }
                }
            } else {
                // Input View
                Column(modifier = Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ImagePickerBox(modifier = Modifier.weight(1f), title = "모델 사진", icon = Icons.Default.Person, uri = modelUri) { modelPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                        ImagePickerBox(modifier = Modifier.weight(1f), title = "의상 사진", icon = Icons.Default.Checkroom, uri = garmentUri) { garmentPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("피팅 부위", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    NeumorphicSegmentedControl(
                        options = listOf("상의", "하의"),
                        selectedIndex = if (part == "TOP") 0 else 1,
                        onSelect = { part = if (it == 0) "TOP" else "BOTTOM" },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(32.dp))
                    NeumorphicButton(
                        onClick = { runTryOn() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = modelUri != null && garmentUri != null
                    ) {
                        Text("가상 피팅 시작", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImagePickerBox(modifier: Modifier = Modifier, title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, uri: Uri?, onClick: () -> Unit) {
    NeumorphicCard(modifier = modifier.aspectRatio(1f).clickable(onClick = onClick)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (uri != null) {
                AsyncImage(model = uri, contentDescription = title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = icon, contentDescription = title, modifier = Modifier.size(48.dp))
                    Text(title, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}
