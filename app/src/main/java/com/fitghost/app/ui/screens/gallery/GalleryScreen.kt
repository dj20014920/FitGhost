package com.fitghost.app.ui.screens.gallery

import android.content.ContentValues
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.fitghost.app.data.LocalImageStore
import com.fitghost.app.ui.theme.FitGhostColors
import com.fitghost.app.ui.theme.Spacing
import com.fitghost.app.ui.theme.IconSize
import java.io.File

/** 갤러리 화면 PRD: Try-On 결과 PNG를 Adaptive 그리드로 열람 폴더블/대화면에서 칼럼 자동 증가(2→3→4~6) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(modifier: Modifier = Modifier, onNavigateToFitting: () -> Unit = {}) {
    Column(modifier = modifier.fillMaxSize().background(FitGhostColors.BgPrimary)) {
        // Header
        TopAppBar(
                title = {
                    Text(
                            text = "갤러리",
                            style = MaterialTheme.typography.headlineLarge,
                            color = FitGhostColors.TextPrimary,
                            fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FitGhostColors.BgGlass)
        )

        // Gallery Content
        GalleryContent(onNavigateToFitting)
    }
}

@Composable
private fun GalleryContent(onNavigateToFitting: () -> Unit = {}) {
    val context = LocalContext.current
    val files = remember { mutableStateOf(emptyList<File>()) }
    DisposableEffect(Unit) {
        val remove = LocalImageStore.addOnChangedListener { files.value = it }
        LocalImageStore.refresh(context)
        onDispose { remove() }
    }

    if (files.value.isNotEmpty()) {
        LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
        ) { items(files.value) { file -> GalleryImageItem(file.absolutePath) } }
    } else {
        EmptyGalleryContent(onNavigateToFitting)
    }
}

@Composable
private fun GalleryImageItem(path: String) {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    val file = remember(path) { File(path) }
    Card(
            modifier = Modifier.aspectRatio(0.75f), // 세로가 더 긴 비율
            colors = CardDefaults.cardColors(containerColor = FitGhostColors.BgSecondary),
            shape = RoundedCornerShape(Spacing.lg)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 이미지 placeholder
            Box(
                    modifier =
                            Modifier.fillMaxSize()
                                    .clip(RoundedCornerShape(Spacing.lg))
                                    .background(FitGhostColors.BgTertiary),
                    contentAlignment = Alignment.Center
            ) {
                Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AsyncImage(
                            model = File(path),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Action buttons overlay
            Row(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 공유 버튼
                IconButton(
                        onClick = {
                            // FileProvider URI 생성 후 공유
                            val uri =
                                    FileProvider.getUriForFile(
                                            context,
                                            context.packageName + ".fileprovider",
                                            file
                                    )
                            val share =
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                            context.startActivity(Intent.createChooser(share, "피팅 결과 공유"))
                        },
                        modifier =
                                Modifier.size(44.dp)
                                        .background(
                                                FitGhostColors.BgSecondary.copy(alpha = 0.9f),
                                                RoundedCornerShape(8.dp)
                                        )
                ) {
                    Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "공유",
                            tint = FitGhostColors.TextPrimary,
                            modifier = Modifier.size(16.dp)
                    )
                }

                // 다운로드 버튼
                IconButton(
                        onClick = {
                            // MediaStore로 Pictures/Download 저장
                            try {
                                val values =
                                        ContentValues().apply {
                                            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                                            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                                            put(
                                                    MediaStore.Images.Media.RELATIVE_PATH,
                                                    "Pictures/FitGhost"
                                            )
                                        }
                                val uri =
                                        contentResolver.insert(
                                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                                values
                                        )
                                if (uri != null) {
                                    contentResolver.openOutputStream(uri).use { out ->
                                        file.inputStream().use { input -> input.copyTo(out!!) }
                                    }
                                    Toast.makeText(context, "갤러리에 저장되었습니다", Toast.LENGTH_SHORT)
                                            .show()
                                } else {
                                    Toast.makeText(context, "저장 실패", Toast.LENGTH_SHORT).show()
                                }
                            } catch (t: Throwable) {
                                Toast.makeText(context, "오류: ${'$'}{t.message}", Toast.LENGTH_SHORT)
                                        .show()
                            }
                        },
                        modifier =
                                Modifier.size(44.dp)
                                        .background(
                                                FitGhostColors.BgSecondary.copy(alpha = 0.9f),
                                                RoundedCornerShape(8.dp)
                                        )
                ) {
                    Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = "다운로드",
                            tint = FitGhostColors.TextPrimary,
                            modifier = Modifier.size(16.dp)
                    )
                }

                // 삭제 버튼
                IconButton(
                        onClick = {
                            val ok = LocalImageStore.deleteTryOnFile(context, file)
                            if (ok) {
                                Toast.makeText(context, "삭제되었습니다", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "삭제 실패", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier =
                                Modifier.size(44.dp)
                                        .background(
                                                FitGhostColors.BgSecondary.copy(alpha = 0.9f),
                                                RoundedCornerShape(8.dp)
                                        )
                ) {
                    Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "삭제",
                            tint = FitGhostColors.TextPrimary,
                            modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyGalleryContent(onNavigateToFitting: () -> Unit = {}) {
    Card(
            modifier = Modifier.fillMaxSize().padding(Spacing.lg),
            colors = CardDefaults.cardColors(containerColor = FitGhostColors.BgSecondary),
            shape = RoundedCornerShape(24.dp)
    ) {
        Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
            Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = FitGhostColors.TextTertiary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                    text = "저장된 피팅 결과가 없습니다.",
                    style = MaterialTheme.typography.titleLarge,
                    color = FitGhostColors.TextSecondary,
                    fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                    text = "가상 피팅을 실행하여 결과를 저장해보세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FitGhostColors.TextTertiary
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onNavigateToFitting, shape = RoundedCornerShape(12.dp)) {
                Text("가상 피팅 하러가기")
            }
        }
    }
}
