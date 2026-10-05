@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.mohithash.byok.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mohithash.byok.data.ResultRow
import com.mohithash.byok.engine.Tool
import com.mohithash.byok.engine.checklistProgress
import com.mohithash.byok.ui.AppViewModel
import com.mohithash.byok.ui.HeroCard
import com.mohithash.byok.ui.heroColors
import com.mohithash.byok.ui.onHeroColor
import com.mohithash.byok.ui.Incoming
import com.mohithash.byok.ui.Label
import com.mohithash.byok.ui.theme.LocalHeroDeep
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What a tool will receive when picked for shared content (mirrors [AppViewModel.useIncoming]). */
internal enum class HomeSharedFit(val label: String) {
    TEXT_AND_PHOTO("✓ Fills in your text and photo"), TEXT("✓ Fills in your text"), PHOTO("✓ Uses your photo"), NONE("Opens without it");
    val receives get() = this != NONE
}

/** Text goes to a tool's first long-text (else text) field, a photo to its photo field. */
internal fun homeSharedFit(t: Tool, hasText: Boolean, hasPhoto: Boolean): HomeSharedFit {
    val text = hasText && t.inputs.any { it.type == "longtext" || it.type == "text" }
    val photo = hasPhoto && t.inputs.any { it.type == "photo" }
    return when { text && photo -> HomeSharedFit.TEXT_AND_PHOTO; text -> HomeSharedFit.TEXT; photo -> HomeSharedFit.PHOTO; else -> HomeSharedFit.NONE }
}

/** One-paragraph preview of shared text: whitespace collapsed, cut at [max] chars without splitting an emoji. */
internal fun homeSharedPreview(text: String, max: Int = 160): String {
    val flat = text.trim().replace(Regex("\\s+"), " ")
    if (flat.length <= max) return flat
    val cut = if (max > 0 && flat[max - 1].isHighSurrogate()) max - 1 else max
    return flat.take(cut).trimEnd() + "…"
}

/** "3/5 ✓" for a result with checklists (done, total); null when it has none. */
internal fun homeChecklistBadge(progress: Pair<Int, Int>): String? = progress.takeIf { it.second > 0 }?.let { "${it.first}/${it.second} ✓" }

/** Title as listed: follow-ups get a "↳" so threads read at a glance. */
internal fun homeRowTitle(r: ResultRow): String = if (r.isFollowUp) "↳ ${r.title}" else r.title

@Composable
fun HomeScreen(vm: AppViewModel, onTool: (Tool) -> Unit, onResult: (ResultRow) -> Unit, onHistory: () -> Unit, onSettings: () -> Unit, onToolOpened: () -> Unit = {}) {
    val spec = vm.spec
    val ai by vm.ai.collectAsState()
    val recent by vm.recent.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val count by vm.count.collectAsState()
    val profile by vm.profile.collectAsState()
    val prefs by vm.prefs.collectAsState()
    val incoming by vm.incoming.collectAsState()
    val cs = MaterialTheme.colorScheme
    val onHero = onHeroColor()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val name = profile["name"]?.takeIf { it.isNotBlank() }
    val fmt = remember { DateTimeFormatter.ofPattern("d MMM") }
    // Navigate only while this screen is the resumed destination: a double tap must not push two screens.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun go(action: () -> Unit) { if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) action() }
    val list = rememberLazyListState()
    // Shared content just arrived: bring its banner into view.
    LaunchedEffect(incoming != null) { if (incoming != null) list.animateScrollToItem(0) }
    Scaffold(modifier = Modifier.nestedScroll(scroll.nestedScrollConnection), topBar = {
        MediumFlexibleTopAppBar(title = { Text(spec.name) }, subtitle = { Text(if (name != null) "Hi $name · ${spec.tagline}" else spec.tagline) },
            actions = {
                IconButton({ go(onHistory) }) { Icon(Icons.AutoMirrored.Filled.List, "History") }
                IconButton({ go(onSettings) }) { Icon(Icons.Default.Settings, "Settings") }
            }, scrollBehavior = scroll, colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface, scrolledContainerColor = cs.surface))
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), state = list, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Everything above Recent is one item: the list anchors its scroll position on the first visible item's key, so a
            // banner or pinned shelf appearing later as separate items above "tools" would land off-screen.
            item(key = "head") {
                Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    incoming?.let { inc ->
                        SharedBanner(spec.name, inc, spec.tools, onPick = { t -> go { if (vm.incoming.value != null) { vm.useIncoming(t); onToolOpened() } } }, onDismiss = vm::dismissIncoming)
                    }
                    if (!ai.configured) HeroCard(colors = heroColors(), blobShape = MaterialShapes.Cookie12Sided) {
                        Label("One step to start", onHero.copy(alpha = 0.8f))
                        Text("Add your AI key", style = MaterialTheme.typography.headlineSmall, color = onHero)
                        Text("Bring your own Claude or OpenAI‑compatible key. It stays on this phone.", color = onHero.copy(alpha = 0.9f))
                        TextButton({ go(onSettings) }) { Text("Open settings →", color = onHero) }
                    }
                    if (favorites.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Pinned", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(favorites, key = { it.id }) { r -> PinnedCard(r) { go { onResult(r) } } }
                        }
                    }
                    Column {
                        Text("Tools", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 2, modifier = Modifier.padding(top = 6.dp)) {
                            spec.tools.forEachIndexed { i, t ->
                                Card(onClick = { go { onTool(t) } }, modifier = Modifier.weight(1f).height(150.dp), shape = MaterialTheme.shapes.extraLarge,
                                    colors = CardDefaults.cardColors(containerColor = when (i % 4) { 0 -> cs.primaryContainer; 1 -> cs.secondaryContainer; 2 -> cs.tertiaryContainer; else -> cs.surfaceContainerHigh })) {
                                    Column(Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                                        Text(t.emoji, style = MaterialTheme.typography.headlineMedium)
                                        Column { Text(t.title, style = MaterialTheme.typography.titleMedium); Text(t.subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (recent.isNotEmpty()) {
                item(key = "recent-head") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
                        TextButton({ go(onHistory) }) { Text(if (count > recent.size) "All ($count)" else "All") }
                    }
                }
                item(key = "recent") {
                    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                        recent.forEachIndexed { i, r ->
                            key(r.id) {
                                val badge = remember(r.json, r.ticks) { homeChecklistBadge(vm.decode(r).checklistProgress(vm.ticks(r))) }
                                val date = remember(r.createdAt) { Instant.ofEpochMilli(r.createdAt).atZone(ZoneId.systemDefault()).format(fmt) }
                                SegmentedListItem(onClick = { go { onResult(r) } }, shapes = ListItemDefaults.segmentedShapes(i, recent.size),
                                    colors = ListItemDefaults.segmentedColors(containerColor = cs.surfaceContainerLow),
                                    leadingContent = { Text(r.emoji, style = MaterialTheme.typography.headlineSmall) },
                                    supportingContent = { Text(listOfNotNull(r.toolTitle, r.inputSummary, date, badge).filter { it.isNotBlank() }.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    trailingContent = { if (r.favorite) Icon(Icons.Default.Star, "Pinned", tint = cs.secondary) },
                                ) { Text(homeRowTitle(r), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                        }
                    }
                }
            }
            item(key = "footer") {
                Column(Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (spec.about.isNotBlank()) Text(spec.about, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    val lang = prefs.language.trim()
                    if (lang.isNotBlank() && !lang.equals("English", ignoreCase = true)) Text("🌐 Answers in $lang · Change",
                        style = MaterialTheme.typography.labelLarge, color = cs.primary,
                        modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClickLabel = "Change answer language", role = Role.Button) { go(onSettings) }.padding(vertical = 8.dp))
                }
            }
            item(key = "end") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** "Shared with <app>" banner: preview of what came in and one row per tool to send it to. */
@Composable
private fun SharedBanner(appName: String, incoming: Incoming, tools: List<Tool>, onPick: (Tool) -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val on = cs.onTertiaryContainer
    val hasText = incoming.text.isNotBlank()
    val thumb = remember(incoming.photo) { incoming.photo?.bitmap?.asImageBitmap() }
    // Tools that will actually receive the content come first; spec order otherwise.
    val ranked = remember(tools, hasText, thumb != null) { tools.map { it to homeSharedFit(it, hasText, thumb != null) }.sortedBy { !it.second.receives } }
    val preview = remember(incoming.text) { homeSharedPreview(incoming.text) }
    HeroCard(colors = listOf(cs.tertiaryContainer, cs.secondaryContainer), blobShape = MaterialShapes.Flower, blobTint = cs.surface.copy(alpha = 0.18f)) {
        Label("Shared with $appName", on.copy(alpha = 0.8f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            thumb?.let { Image(it, "Shared photo", Modifier.size(72.dp).clip(MaterialTheme.shapes.large), contentScale = ContentScale.Crop) }
            Text(if (hasText) preview else "A photo", style = MaterialTheme.typography.bodyMedium, color = on, maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Text("Pick a tool for it:", style = MaterialTheme.typography.titleMedium, color = on, modifier = Modifier.padding(top = 6.dp))
        ranked.forEach { (t, fit) ->
            Surface(onClick = { onPick(t) }, shape = MaterialTheme.shapes.large, color = cs.surface.copy(alpha = if (fit.receives) 0.85f else 0.45f), contentColor = cs.onSurface, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(t.emoji, style = MaterialTheme.typography.titleLarge)
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(fit.label, style = MaterialTheme.typography.bodySmall, color = if (fit.receives) cs.primary else cs.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = cs.onSurfaceVariant)
                }
            }
        }
        TextButton(onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Dismiss", color = on) }
    }
}

/** Compact card for a starred result in the horizontal "Pinned" shelf. */
@Composable
private fun PinnedCard(r: ResultRow, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(onClick = onClick, modifier = Modifier.width(168.dp).height(132.dp), shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = cs.secondaryContainer, contentColor = cs.onSecondaryContainer)) {
        Column(Modifier.padding(14.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(r.emoji, style = MaterialTheme.typography.titleLarge)
                Icon(Icons.Default.Star, null, tint = cs.secondary, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(homeRowTitle(r), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(r.toolTitle, style = MaterialTheme.typography.labelSmall, color = cs.onSecondaryContainer.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
