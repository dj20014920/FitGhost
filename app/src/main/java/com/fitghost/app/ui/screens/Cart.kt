package com.fitghost.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fitghost.app.util.ServiceLocator
import com.fitghost.app.data.model.CartItem
import com.fitghost.app.util.Browser
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.Button
import com.fitghost.app.ui.theme.NeumorphicButton
import com.fitghost.app.ui.theme.NeumorphicCard

@Composable
fun CartScreen() {
    val context = LocalContext.current
    val db = remember { ServiceLocator.db(context) }
    val dao = db.cartDao()
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<CartItem>>(emptyList()) }

    LaunchedEffect(Unit) { dao.all().collect { items = it } }

    // 몰별 그룹
    val groups = items.groupBy { it.mall }

    Column(Modifier.padding(16.dp)) {
        AnimatedVisibility(
            visible = groups.isEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            NeumorphicCard {
                Column(Modifier.padding(16.dp)) {
                    Text("장바구니가 비어 있습니다", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Text("쇼핑하러 가시겠습니까?", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    NeumorphicButton(onClick = { /* Navigate to shop */ }) {
                        Text("쇼핑하러 가기")
                    }
                }
            }
        }
        
        groups.forEach { (mall, list) ->
            NeumorphicCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${mall} (${list.size})", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    LazyColumn {
                        items(list) { c ->
                            ListItem(
                                headlineContent = { Text(c.title) }, 
                                supportingContent = { Text("${c.price}") }, 
                                trailingContent = {
                                    Row { 
                                        NeumorphicButton(
                                            onClick = { 
                                                scope.launch { 
                                                    dao.updateStatus(c.id, if (c.status=="DONE") "PENDING" else "DONE") 
                                                } 
                                            }
                                        ) { 
                                            Text(if (c.status=="DONE") "미완료" else "완료") 
                                        } 
                                    }
                                }
                            )
                            HorizontalDivider()
                        } 
                    }
                    Spacer(Modifier.height(8.dp))
                    NeumorphicButton(
                        onClick = {
                            // 순차 오픈 UX
                            scope.launch {
                                list.filter { it.status != "DONE" }.forEach { Browser.open(context, it.link) }
                            }
                        }
                    ) { 
                        Text("${mall} 에서 결제하기(${list.count{it.status!="DONE"}}건)") 
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}