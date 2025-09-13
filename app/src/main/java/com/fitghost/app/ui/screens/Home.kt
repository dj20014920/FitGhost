package com.fitghost.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fitghost.app.data.CreditStore
import com.fitghost.app.ui.theme.NeumorphicButton
import com.fitghost.app.ui.theme.NeumorphicCard
import com.fitghost.app.util.TryOnBridge

@Composable
fun HomeScreen(onNavigateTryOn: () -> Unit, onNavigateShop: () -> Unit) {
    val vm: HomeViewModel = viewModel()
    val weather by vm.weather.collectAsState(initial = null)
    val top3 by vm.top3.collectAsState(initial = emptyList())

    // 크레딧 상태(주당 10회 + 보너스) 표시 및 Try-On 연결 제어
    val context = LocalContext.current
    val creditStore = remember { CreditStore(context) }
    val creditState by
            creditStore
                    .state()
                    .collectAsState(initial = CreditStore.State(week = "", used = 0, bonus = 0))
    var infoMessage by remember { mutableStateOf("") }

    Column(Modifier.padding(16.dp)) {
        NeumorphicCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("오늘 날씨", style = MaterialTheme.typography.titleMedium)
                val wt =
                        weather?.let {
                            "서울 %.0f℃ / 강수 %.1fmm / 풍속 %.1fm/s".format(
                                    it.temperatureC,
                                    it.precipitationMm,
                                    it.windSpeed
                            )
                        }
                                ?: "날씨 로딩 중..."
                Text(wt)
            }
        }

        Spacer(Modifier.height(12.dp))

        // 남은 크레딧 안내
        NeumorphicCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(
                        "남은 크레딧: ${creditState.remaining}",
                        style = MaterialTheme.typography.bodyMedium
                )
                Text("주당 10회 무료 + 광고 보상 시 +1", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(12.dp))

        Text("오늘의 코디 TOP3", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        AnimatedVisibility(
                visible = top3.isEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
        ) {
            NeumorphicCard {
                Column(Modifier.padding(12.dp)) {
                    Text("추천 준비 중...")
                    Text("옷장을 채우거나 날씨 로딩을 기다려주세요.")
                }
            }
        }

        top3.forEachIndexed { idx, scored ->
            Spacer(Modifier.height(8.dp))
            AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
            ) {
                NeumorphicCard {
                    Column(Modifier.padding(12.dp)) {
                        val o = scored.outfit
                        Text(
                                "#${idx + 1} ${o.top?.color ?: "-"} 상의 + ${o.bottom?.color ?: "-"} 하의" +
                                        (if (o.outer != null) " + 겉옷" else ""),
                                style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                                "점수: %.1f".format(scored.score),
                                style = MaterialTheme.typography.bodyMedium
                        )
                        val tags =
                                ((o.top?.tags
                                                ?: emptyList()) +
                                                (o.bottom?.tags ?: emptyList()) +
                                                (o.outer?.tags ?: emptyList()))
                                        .joinToString(", ")
                        if (tags.isNotBlank()) {
                            Text("태그: $tags", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row {
                            NeumorphicButton(
                                    onClick = {
                                        if (creditState.remaining > 0) {
                                            TryOnBridge.setPart(
                                                    TryOnBridge.TryOnPart.TOP,
                                                    autoLaunchPicker = true,
                                                    itemImageUri = o.top?.imageUri
                                            )
                                            onNavigateTryOn()
                                        } else {
                                            infoMessage =
                                                    "크레딧이 부족합니다. Try-On 화면에서 광고 시청으로 +1 충전할 수 있어요."
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                            ) { Text("상의 Try-On") }
                            Spacer(Modifier.width(8.dp))
                            NeumorphicButton(
                                    onClick = {
                                        if (creditState.remaining > 0) {
                                            TryOnBridge.setPart(
                                                    TryOnBridge.TryOnPart.BOTTOM,
                                                    autoLaunchPicker = true,
                                                    itemImageUri = o.bottom?.imageUri
                                            )
                                            onNavigateTryOn()
                                        } else {
                                            infoMessage =
                                                    "크레딧이 부족합니다. Try-On 화면에서 광고 시청으로 +1 충전할 수 있어요."
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                            ) { Text("하의 Try-On") }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row {
            NeumorphicButton(onClick = onNavigateTryOn) { Text("가상 피팅") }
            Spacer(Modifier.width(8.dp))
            NeumorphicButton(onClick = onNavigateShop) { Text("쇼핑하기") }
        }

        Spacer(Modifier.height(8.dp))

        AnimatedVisibility(
                visible = infoMessage.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
        ) {
            NeumorphicCard {
                Column(Modifier.padding(12.dp)) {
                    Text(infoMessage, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
