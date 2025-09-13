package com.fitghost.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fitghost.app.data.model.Garment
import com.fitghost.app.data.model.GarmentTaxonomy
import com.fitghost.app.ui.theme.NeumorphicButton
import com.fitghost.app.ui.theme.NeumorphicCard
import com.fitghost.app.ui.theme.NeumorphicOutlinedTextField
import com.fitghost.app.util.ServiceLocator
import kotlinx.coroutines.launch

@Composable
fun WardrobeScreen() {
    val context = LocalContext.current
    val db = remember { ServiceLocator.db(context) }
    val dao = db.wardrobeDao()
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<Garment>>(emptyList()) }

    LaunchedEffect(Unit) { dao.all().collect { list = it } }

    var color by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("T") }
    var subcategory by remember { mutableStateOf("") }

    Column(Modifier.padding(16.dp)) {
        NeumorphicCard {
            Column(Modifier.padding(12.dp)) {
                Row {
                    NeumorphicOutlinedTextField(
                            value = color,
                            onValueChange = { color = it },
                            label = { Text("색상") },
                            modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    NeumorphicOutlinedTextField(
                            value = type,
                            onValueChange = { type = it },
                            label = { Text("타입 T/B/O") },
                            modifier = Modifier.weight(1f)
                    )
                }
                NeumorphicOutlinedTextField(
                        value = subcategory,
                        onValueChange = { subcategory = it },
                        label = { Text("세부종류(예: 반팔/긴팔/가디건/점퍼/바람막이...)") },
                        modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                NeumorphicButton(
                        onClick = {
                            scope.launch {
                                val sub = subcategory
                                val subcat = GarmentTaxonomy.matchSubcategoryFreeText(sub)
                                val resolvedType =
                                        subcat?.let { GarmentTaxonomy.legacyTypeFor(it) } ?: type
                                val tagSet =
                                        (GarmentTaxonomy.suggestTags("$color $sub") +
                                                        (subcat?.aliases ?: emptySet()).map {
                                                            it.lowercase()
                                                        } +
                                                        listOfNotNull(
                                                                sub
                                                                        .takeIf { it.isNotBlank() }
                                                                        ?.lowercase()
                                                        ))
                                                .toSet()
                                dao.upsert(
                                        Garment(
                                                type = resolvedType,
                                                color = color,
                                                tags = tagSet.toList()
                                        )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                ) { Text("추가") }
            }
        }
        Spacer(Modifier.height(12.dp))

        AnimatedVisibility(
                visible = list.isEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
        ) {
            NeumorphicCard {
                Column(Modifier.padding(16.dp)) {
                    Text("옷장이 비어 있습니다", style = MaterialTheme.typography.titleMedium)
                    Text("새로운 아이템을 추가해 보세요", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        LazyColumn {
            items(list) { g ->
                NeumorphicCard {
                    ListItem(
                            headlineContent = { Text("${g.type} - ${g.color}") },
                            supportingContent = { Text("warmth ${g.warmth}") },
                            trailingContent = {
                                Row {
                                    NeumorphicButton(onClick = { scope.launch { dao.delete(g) } }) {
                                        Text("삭제")
                                    }
                                }
                            }
                    )
                }
                HorizontalDivider()
            }
        }
    }
}
