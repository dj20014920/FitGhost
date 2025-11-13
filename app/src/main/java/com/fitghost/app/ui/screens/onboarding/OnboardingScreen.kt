package com.fitghost.app.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fitghost.app.data.settings.UserSettings
import com.fitghost.app.ui.theme.FitGhostColors
import com.fitghost.app.ui.theme.Spacing
import com.fitghost.app.ui.theme.CornerRadius
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    onCompleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selected by remember { mutableStateOf<UserSettings.Gender?>(null) }
    var saving by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .background(FitGhostColors.BgPrimary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "처음 오신 것을 환영합니다",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = FitGhostColors.TextPrimary
            )
            Text(
                text = "AI 기반 추천의 정확도를 높이기 위해 성별 정보를 사용합니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = FitGhostColors.TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = FitGhostColors.BgSecondary),
                shape = RoundedCornerShape(Spacing.lg.times(1.25f))
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.lg.times(1.25f)),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "성별 선택",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = FitGhostColors.TextPrimary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SelectableChip(
                            label = "남성",
                            selected = selected == UserSettings.Gender.MALE
                        ) { selected = UserSettings.Gender.MALE }
                        SelectableChip(
                            label = "여성",
                            selected = selected == UserSettings.Gender.FEMALE
                        ) { selected = UserSettings.Gender.FEMALE }
                    }
                    Text(
                        text = "입력한 성별은 날씨 기반 자동 추천과 상점의 AI 추천에 활용됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = FitGhostColors.TextTertiary
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    if (selected != null && !saving) {
                        saving = true
                        scope.launch {
                            try {
                                UserSettings.setGender(context, selected!!)
                                UserSettings.setOnboardingCompleted(context, true)
                                onCompleted()
                            } finally {
                                saving = false
                            }
                        }
                    }
                },
                enabled = selected != null && !saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(Spacing.lg)
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text("시작하기")
                }
            }
        }
    }
}

@Composable
private fun SelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Outlined.CheckCircle, contentDescription = null) }
        } else null,
        modifier = Modifier.height(40.dp),
        shape = RoundedCornerShape(12.dp)
    )
}
