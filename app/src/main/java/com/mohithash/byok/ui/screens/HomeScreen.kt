@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.mohithash.byok.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.mohithash.byok.data.ResultRow
import com.mohithash.byok.engine.Tool
import com.mohithash.byok.ui.AppViewModel
import com.mohithash.byok.ui.HeroCard
import com.mohithash.byok.ui.Label
import com.mohithash.byok.ui.theme.LocalHeroDeep
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(vm: AppViewModel, onTool: (Tool) -> Unit, onResult: (ResultRow) -> Unit, onHistory: () -> Unit, onSettings: () -> Unit) {
    val spec = vm.spec
    val ai by vm.ai.collectAsState()
    val recent by vm.recent.collectAsState()
    val profile by vm.profile.collectAsState()
    val cs = MaterialTheme.colorScheme
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val name = profile["name"]?.takeIf { it.isNotBlank() }
    val fmt = DateTimeFormatter.ofPattern("d MMM")
    Scaffold(modifier = Modifier.nestedScroll(scroll.nestedScrollConnection), topBar = {
        MediumFlexibleTopAppBar(title = { Text(spec.name) }, subtitle = { Text(if (name != null) "Hi $name · ${spec.tagline}" else spec.tagline) },
            actions = { IconButton(onSettings) { Icon(Icons.Default.Settings, "Settings") } }, scrollBehavior = scroll, colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface, scrolledContainerColor = cs.surface))
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!ai.configured) item {
                HeroCard(colors = listOf(cs.primary, LocalHeroDeep.current), blobShape = MaterialShapes.Cookie12Sided) {
                    Label("One step to start", cs.onPrimary.copy(alpha = 0.8f))
                    Text("Add your AI key", style = MaterialTheme.typography.headlineSmall, color = cs.onPrimary)
                    Text("Bring your own Claude or OpenAI‑compatible key. It stays on this phone.", color = cs.onPrimary.copy(alpha = 0.9f))
                    TextButton(onSettings) { Text("Open settings →", color = cs.secondaryContainer) }
                }
            }
            item {
                Text("Tools", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 2, modifier = Modifier.padding(top = 6.dp)) {
                    spec.tools.forEachIndexed { i, t ->
                        Card(Modifier.weight(1f).height(150.dp).clickable { onTool(t) }, shape = MaterialTheme.shapes.extraLarge,
                            colors = CardDefaults.cardColors(containerColor = when (i % 4) { 0 -> cs.primaryContainer; 1 -> cs.secondaryContainer; 2 -> cs.tertiaryContainer; else -> cs.surfaceContainerHigh })) {
                            Column(Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                                Text(t.emoji, style = MaterialTheme.typography.headlineMedium)
                                Column { Text(t.title, style = MaterialTheme.typography.titleMedium); Text(t.subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2) }
                            }
                        }
                    }
                }
            }
            if (recent.isNotEmpty()) {
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Recent", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp)); TextButton(onHistory) { Text("All") } } }
                items(recent, key = { it.id }) { r ->
                    ListItem(leadingContent = { Text(r.emoji, style = MaterialTheme.typography.headlineSmall) }, headlineContent = { Text(r.title, maxLines = 1) },
                        supportingContent = { Text(listOf(r.toolTitle, r.inputSummary, Instant.ofEpochMilli(r.createdAt).atZone(ZoneId.systemDefault()).format(fmt)).filter { it.isNotBlank() }.joinToString(" · "), maxLines = 1) },
                        trailingContent = { if (r.favorite) Icon(Icons.Default.Star, null, tint = cs.secondary) },
                        colors = ListItemDefaults.colors(containerColor = cs.surfaceContainerLow), modifier = Modifier.clip(MaterialTheme.shapes.large).clickable { onResult(r) })
                }
            }
            if (spec.about.isNotBlank()) item { Text(spec.about, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
