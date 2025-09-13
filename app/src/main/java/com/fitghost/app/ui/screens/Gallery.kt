package com.fitghost.app.ui.screens

import android.os.Environment
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import java.io.File
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import com.fitghost.app.ui.theme.NeumorphicCard
import androidx.compose.ui.Alignment

@Composable
fun GalleryScreen() {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<File>>(emptyList()) }
    LaunchedEffect(Unit) {
        val pictures = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val dir = if (pictures != null) File(pictures, "tryon") else null
        items = dir?.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    
    Column(Modifier.padding(16.dp)) {
        AnimatedVisibility(
            visible = items.isEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            NeumorphicCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("저장된 결과가 없습니다.", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Text("가상 피팅을 실행하여 결과를 저장해 보세요", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                }
            }
        }
        
        AnimatedVisibility(
            visible = items.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp), 
                modifier = Modifier.padding(8.dp)
            ) {
                items(items) { f ->
                    NeumorphicCard(
                        modifier = Modifier
                            .padding(4.dp)
                            .fillMaxWidth()
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(f), 
                            contentDescription = f.name,
                            modifier = Modifier
                                .height(140.dp)
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}