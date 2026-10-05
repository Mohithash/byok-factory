@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.mohithash.byok.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mohithash.byok.data.ResultRow
import com.mohithash.byok.engine.Doc
import com.mohithash.byok.engine.Tool
import com.mohithash.byok.engine.checklistProgress
import com.mohithash.byok.engine.searchText
import com.mohithash.byok.ui.AppViewModel
import com.mohithash.byok.ui.EmptyState
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/* ───────────── Pure helpers (unit-tested) ───────────── */

/** Date buckets for the saved list; declared newest → oldest. */
internal enum class HistoryDateGroup(val label: String) { TODAY("Today"), YESTERDAY("Yesterday"), THIS_WEEK("This week"), THIS_MONTH("This month"), OLDER("Older") }

/**
 * Which bucket [createdAt] falls in, seen from [today] in [zone]. Calendar based ("This week" starts on [firstDayOfWeek]),
 * and monotonic in time, so a list sorted by date yields each bucket at most once. Future times count as today.
 */
internal fun historyDateGroup(createdAt: Long, today: LocalDate, zone: ZoneId, firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): HistoryDateGroup {
    val d = Instant.ofEpochMilli(createdAt).atZone(zone).toLocalDate()
    return when {
        !d.isBefore(today) -> HistoryDateGroup.TODAY
        d == today.minusDays(1) -> HistoryDateGroup.YESTERDAY
        !d.isBefore(today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))) -> HistoryDateGroup.THIS_WEEK
        d.year == today.year && d.month == today.month -> HistoryDateGroup.THIS_MONTH
        else -> HistoryDateGroup.OLDER
    }
}

/** Splits an already-sorted list into consecutive runs that share a group. */
internal fun <T, G> historySections(items: List<T>, groupOf: (T) -> G): List<Pair<G, List<T>>> {
    val out = ArrayList<Pair<G, MutableList<T>>>()
    items.forEach { val g = groupOf(it); if (out.isEmpty() || out.last().first != g) out += g to mutableListOf(it) else out.last().second += it }
    return out
}

/** Short time for a row inside its date group: just the time for today/yesterday, weekday for this week, date otherwise. */
internal fun historyTimeLabel(createdAt: Long, group: HistoryDateGroup, today: LocalDate, zone: ZoneId, locale: Locale = Locale.getDefault()): String {
    val t = Instant.ofEpochMilli(createdAt).atZone(zone)
    val time = t.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
    return when (group) {
        HistoryDateGroup.TODAY, HistoryDateGroup.YESTERDAY -> time
        HistoryDateGroup.THIS_WEEK -> t.format(DateTimeFormatter.ofPattern("EEE", locale)) + " " + time
        else -> t.format(DateTimeFormatter.ofPattern(if (t.year == today.year) "d MMM" else "d MMM yyyy", locale))
    }
}

/** Search words: lower-cased, split on whitespace. Every word must match somewhere in a result. */
internal fun historyQueryWords(q: String): List<String> = q.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }

/** The short, always-searched fields of a row, lower-cased. */
internal fun historyMeta(r: ResultRow): String = listOf(r.title, r.inputSummary, r.toolTitle, r.note).joinToString("\n").lowercase()

/** Filter key for "everything" and for starred results; any other key is a tool id. */
internal const val HISTORY_FILTER_ALL = ""
internal const val HISTORY_FILTER_FAVOURITES = "★"

/**
 * Rows matching [filter] and all [words] (in the row's fields or its document text from [docText], asked only while searching),
 * sorted by creation time. The DAO orders favourites first, so the order is rebuilt here.
 */
internal fun historyFilter(rows: List<ResultRow>, filter: String, words: List<String>, newestFirst: Boolean, docText: (ResultRow) -> String): List<ResultRow> {
    val picked = rows.filter { r ->
        val kept = when (filter) { HISTORY_FILTER_ALL -> true; HISTORY_FILTER_FAVOURITES -> r.favorite; else -> r.toolId == filter }
        kept && (words.isEmpty() || historyMeta(r).let { meta -> words.all { w -> meta.contains(w) || docText(r).contains(w) } })
    }
    val byTime = compareBy<ResultRow>({ it.createdAt }, { it.id })
    return picked.sortedWith(if (newestFirst) byTime.reversed() else byTime)
}

/** One filter chip per tool that has results: spec order first, then tools no longer in the spec. */
internal data class HistoryToolChip(val id: String, val emoji: String, val title: String)

internal fun historyToolChips(rows: List<ResultRow>, tools: List<Tool>): List<HistoryToolChip> {
    val used = LinkedHashMap<String, ResultRow>()
    rows.forEach { used.putIfAbsent(it.toolId, it) }
    val known = tools.filter { it.id in used }.map { HistoryToolChip(it.id, it.emoji, it.title) }
    val gone = used.values.filter { r -> tools.none { it.id == r.toolId } }.map { HistoryToolChip(it.toolId, it.emoji, it.toolTitle) }
    return known + gone
}

/** Body of the "No matches" state for the current search [q] and [filter]. */
internal fun historyNoMatchText(q: String, filter: String): String = when {
    q.isNotBlank() -> "Nothing saved matches “${q.trim()}”" + (if (filter == HISTORY_FILTER_ALL) "." else " with this filter.")
    filter == HISTORY_FILTER_FAVOURITES -> "No favourites yet. Tap the star on a result to keep it here and pin it on Home."
    else -> "No saved results from this tool yet."
}

/** "1 saved result" / "3 saved results". */
internal fun historyCountLabel(n: Int): String = if (n == 1) "1 saved result" else "$n saved results"

/* ───────────── Screen ───────────── */

/** Decoded docs per result id, reused across keystrokes and list updates: each result's JSON is decoded once. */
private class HistoryDocs(private val decode: (ResultRow) -> Doc) {
    private class Entry(val json: String, val doc: Doc) { val text: String by lazy { doc.searchText() } }
    private val entries = HashMap<Long, Entry>()
    private fun entry(r: ResultRow): Entry = entries[r.id]?.takeIf { it.json == r.json } ?: Entry(r.json, decode(r)).also { entries[r.id] = it }
    fun doc(r: ResultRow): Doc = entry(r).doc
    fun text(r: ResultRow): String = entry(r).text
}

/** Outlined star (material-icons-core has only the filled one). */
private val StarOutline: ImageVector by lazy {
    ImageVector.Builder("StarOutline", 24.dp, 24.dp, 24f, 24f).addPath(
        addPathNodes("M22,9.24l-7.19,-0.62L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21 12,17.27 18.18,21l-1.63,-7.03L22,9.24zM12,15.4l-3.76,2.27 1,-4.28 -3.32,-2.88 4.38,-0.38L12,6.1l1.71,4.04 4.38,0.38 -3.32,2.88 1,4.28L12,15.4z"),
        fill = SolidColor(Color.Black),
    ).build()
}

@Composable
fun HistoryScreen(vm: AppViewModel, onBack: () -> Unit, onOpen: (ResultRow) -> Unit) {
    val all by vm.results.collectAsState()
    val count by vm.count.collectAsState()
    val cs = MaterialTheme.colorScheme
    var q by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(HISTORY_FILTER_ALL) }
    var newestFirst by rememberSaveable { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun go(action: () -> Unit) { if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) action() }

    val docs = remember { HistoryDocs(vm::decode) }
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zone)
    val firstDay = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
    val words = remember(q) { historyQueryWords(q) }
    val shown = remember(all, filter, words, newestFirst) { historyFilter(all, filter, words, newestFirst, docs::text) }
    val sections = remember(shown, today) { historySections(shown) { historyDateGroup(it.createdAt, today, zone, firstDay) } }
    val chips = remember(all) { historyToolChips(all, vm.spec.tools) }
    val filtering = words.isNotEmpty() || filter != HISTORY_FILTER_ALL
    // The results flow starts empty; while the (already warm) count says there are rows, show nothing rather than "Nothing saved yet".
    val loading = all.isEmpty() && count > 0
    fun clearFilters() { q = ""; filter = HISTORY_FILTER_ALL }
    val list = rememberLazyListState()
    // A new search, filter or sort starts at the top (but coming back from a result keeps the restored position).
    LaunchedEffect(list) { snapshotFlow { Triple(q, filter, newestFirst) }.drop(1).collect { list.scrollToItem(0) } }
    fun delete(r: ResultRow) {
        vm.delete(r)
        scope.launch {
            snack.currentSnackbarData?.dismiss()
            if (snack.showSnackbar("Deleted", actionLabel = "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.undoDelete()
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(if (count > 0) "Saved · $count" else "Saved") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface),
            navigationIcon = { IconButton({ go(onBack) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                Box {
                    IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "More options") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Delete all…") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, enabled = count > 0,
                            onClick = { menu = false; confirmDeleteAll = true })
                    }
                }
            })
    }, snackbarHost = { SnackbarHost(snack) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (all.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 16.dp).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(q, { q = it }, placeholder = { Text("Search saved results") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = if (q.isEmpty()) null else { { IconButton({ q = "" }) { Icon(Icons.Default.Clear, "Clear search") } } },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(filter == HISTORY_FILTER_ALL, { filter = HISTORY_FILTER_ALL }, { Text("All") })
                        FilterChip(filter == HISTORY_FILTER_FAVOURITES, { filter = if (filter == HISTORY_FILTER_FAVOURITES) HISTORY_FILTER_ALL else HISTORY_FILTER_FAVOURITES }, { Text("★ Favourites") })
                        // Keep a picked tool's chip visible even after its last result is gone, so it can be turned off.
                        val shownChips = if (filter == HISTORY_FILTER_ALL || filter == HISTORY_FILTER_FAVOURITES || chips.any { it.id == filter }) chips
                            else chips + HistoryToolChip(filter, vm.toolById(filter)?.emoji ?: "✨", vm.toolById(filter)?.title ?: filter)
                        shownChips.forEach { c -> FilterChip(filter == c.id, { filter = if (filter == c.id) HISTORY_FILTER_ALL else c.id }, { Text("${c.emoji} ${c.title}", maxLines = 1) }) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (filtering) "${shown.size} of ${all.size}" else historyCountLabel(all.size), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                        TextButton({ newestFirst = !newestFirst }) {
                            Text(if (newestFirst) "Newest first" else "Oldest first")
                            Icon(if (newestFirst) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp, null, Modifier.padding(start = 4.dp).size(18.dp))
                        }
                    }
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    // A placeholder keeps the trailing spacer from being the scroll anchor, or loaded rows would open scrolled to the end.
                    loading -> item(key = "loading") { Spacer(Modifier.fillMaxWidth().height(1.dp)) }
                    all.isEmpty() -> item(key = "empty") { EmptyState(Icons.Default.Star, "Nothing saved yet", "Every result is kept here on your device. Star the ones you love to pin them on Home.") }
                    shown.isEmpty() -> item(key = "nomatch") {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            EmptyState(Icons.Default.Search, "No matches", historyNoMatchText(q, filter), MaterialShapes.Cookie9Sided)
                            FilledTonalButton(::clearFilters, shapes = ButtonDefaults.shapes()) { Text("Clear filters") }
                        }
                    }
                    else -> sections.forEachIndexed { si, (group, rows) ->
                        item(key = "h-${group.name}", contentType = "header") {
                            Text(group.label, style = MaterialTheme.typography.titleSmall, color = cs.primary,
                                modifier = Modifier.animateItem().padding(start = 4.dp, top = if (si == 0) 0.dp else 10.dp, bottom = 2.dp).semantics { heading() })
                        }
                        items(rows, key = { it.id }, contentType = { "row" }) { r ->
                            val badge = remember(r.json, r.ticks) { homeChecklistBadge(docs.doc(r).checklistProgress(vm.ticks(r))) }
                            val time = remember(r.createdAt, group, today) { historyTimeLabel(r.createdAt, group, today, zone) }
                            HistoryRow(r, listOfNotNull(r.toolTitle, r.inputSummary, time, badge).filter { it.isNotBlank() }.joinToString(" · "),
                                onOpen = { go { onOpen(r) } }, onStar = { vm.toggleFavorite(r) }, onDelete = { delete(r) })
                        }
                    }
                }
                item(key = "end") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (confirmDeleteAll) AlertDialog(
        onDismissRequest = { confirmDeleteAll = false },
        icon = { Icon(Icons.Default.Delete, null) },
        title = { Text("Delete all results?") },
        text = { Text("This removes all ${historyCountLabel(count)} from this device. It can't be undone — export a backup in Settings first if you want to keep them.") },
        confirmButton = {
            TextButton({
                confirmDeleteAll = false; clearFilters(); vm.deleteAll()
                scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar("All results deleted") }
            }) { Text("Delete all", color = cs.error) }
        },
        dismissButton = { TextButton({ confirmDeleteAll = false }) { Text("Cancel") } },
    )
}

/** One saved result: tap to open, star to (un)favourite, swipe right-to-left (or the accessibility action) to delete. */
@Composable
private fun LazyItemScope.HistoryRow(r: ResultRow, supporting: String, onOpen: () -> Unit, onStar: () -> Unit, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    // Plain remember, not the saveable state: an undone delete brings the same key back and must not come back swiped away.
    val swipe = remember(r.id) { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, positionalThreshold = { it * 0.4f }) }
    SwipeToDismissBox(
        state = swipe,
        modifier = Modifier.animateItem(),
        enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            if (swipe.dismissDirection == SwipeToDismissBoxValue.EndToStart) Box(Modifier.fillMaxSize().clip(MaterialTheme.shapes.large).background(cs.errorContainer).padding(horizontal = 24.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(Icons.Default.Delete, null, tint = cs.onErrorContainer)
            }
        },
    ) {
        ListItem(
            onClick = onOpen,
            // Swiping isn't reachable with TalkBack: offer delete as an action on the focusable row.
            modifier = Modifier.semantics { customActions = listOf(CustomAccessibilityAction("Delete") { onDelete(); true }) },
            leadingContent = { Text(r.emoji, style = MaterialTheme.typography.headlineSmall) },
            supportingContent = {
                Column {
                    Text(supporting, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (r.note.isNotBlank()) Text("📝 " + r.note.trim().lineSequence().first(), maxLines = 1, overflow = TextOverflow.Ellipsis, color = cs.onSurface)
                }
            },
            trailingContent = {
                IconButton(onStar) {
                    Icon(if (r.favorite) Icons.Default.Star else StarOutline, if (r.favorite) "Remove from favourites" else "Add to favourites",
                        tint = if (r.favorite) cs.secondary else cs.onSurfaceVariant)
                }
            },
            shapes = ListItemDefaults.shapes(shape = MaterialTheme.shapes.large),
            colors = ListItemDefaults.colors(containerColor = if (r.favorite) cs.secondaryContainer else cs.surfaceContainerLow),
        ) { Text(homeRowTitle(r), maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}
