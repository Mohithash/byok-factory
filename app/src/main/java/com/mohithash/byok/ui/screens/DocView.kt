@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.mohithash.byok.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.mohithash.byok.engine.Card
import com.mohithash.byok.engine.Doc
import com.mohithash.byok.engine.KV
import com.mohithash.byok.engine.Section
import com.mohithash.byok.engine.checklistProgress
import com.mohithash.byok.engine.toMarkdown
import com.mohithash.byok.engine.toPlainText
import com.mohithash.byok.engine.toSpeech
import com.mohithash.byok.ui.DocSpeaker
import com.mohithash.byok.ui.HeroCard
import com.mohithash.byok.ui.InlineMarkdown
import com.mohithash.byok.ui.InlineMarkdownText
import com.mohithash.byok.ui.Label
import com.mohithash.byok.ui.StatCard
import com.mohithash.byok.ui.docExportFileName
import com.mohithash.byok.ui.docPrintAccent
import com.mohithash.byok.ui.printDocAsPdf
import com.mohithash.byok.ui.rememberDocSpeaker
import com.mohithash.byok.ui.sectionPlainText
import com.mohithash.byok.ui.theme.LocalHeroDeep
import com.mohithash.byok.ui.withoutInlineMarkdown
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Renders a structured [Doc]: hero, every section kind (with inline Markdown), tickable checklists with progress, an
 * action bar (copy, share, read aloud, save as PDF, export / copy Markdown) and follow-up suggestions.
 *
 * [ticks] are checked checklist keys "sectionIdx:itemIdx". Long-pressing a section copies just that section.
 * [onMessage] shows a short message (snackbar) in the host; [onSaveFile] writes text to a document the user picked.
 */
@Composable
fun DocView(
    doc: Doc,
    ticks: Set<String>,
    onTick: (String) -> Unit,
    onFollowUp: (String) -> Unit,
    enabled: Boolean = true,
    onMessage: (String) -> Unit = {},
    onSaveFile: suspend (Uri, String) -> Unit = { _, _ -> },
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val clipboard = LocalClipboard.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val speaker = rememberDocSpeaker()
    val plain = remember(doc) { doc.withoutInlineMarkdown() }
    // Another result in this slot (or leaving the screen) ends read-aloud.
    DisposableEffect(speaker, doc) { onDispose { speaker.stop() } }

    fun copy(text: String, done: String) {
        scope.launch {
            try {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(plain.title.ifBlank { "Result" }, text)))
                onMessage(done)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onMessage("Couldn't copy to the clipboard.")
            }
        }
    }
    val saveMarkdown = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val md = doc.toMarkdown(ticks)
        scope.launch {
            try {
                onSaveFile(uri, md)
                onMessage("Saved")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onMessage(e.message ?: "Couldn't save the file.")
            }
        }
    }
    val accent = docPrintAccent(cs.primary)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DocHero(doc, ticks)
        key(doc) { // per-section state (table scroll) belongs to one result
            doc.sections.forEachIndexed { si, s ->
                DocSection(si, s, ticks, onTick,
                    onCopySection = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        copy(plain.sectionPlainText(si, ticks), "Section copied")
                    },
                    onCopyText = { copy(it, "Copied") })
            }
        }
        DocActions(
            speaker = speaker,
            onCopy = { copy(plain.toPlainText(ticks), "Copied") },
            onShare = { shareDoc(ctx, plain, ticks, onMessage) },
            onReadAloud = { if (speaker.speaking) speaker.stop() else speaker.speak(plain.toSpeech(), onMessage) },
            onPdf = { if (!printDocAsPdf(ctx, doc, ticks, accent, onMessage)) onMessage("Printing isn't available on this device.") },
            onMarkdown = {
                try { saveMarkdown.launch(docExportFileName(doc.title, "md")) } catch (e: ActivityNotFoundException) { onMessage("No app on this device can save files.") }
            },
            onCopyMarkdown = { copy(doc.toMarkdown(ticks), "Copied as Markdown") },
        )
        val followups = plain.followups.filter { it.isNotBlank() }
        if (followups.isNotEmpty()) {
            Label("Ask next")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                followups.forEach { q -> SuggestionChip(onClick = { onFollowUp(q) }, label = { Text(q) }, enabled = enabled) }
            }
        }
    }
}

private fun shareDoc(ctx: Context, doc: Doc, ticks: Set<String>, onMessage: (String) -> Unit) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, doc.toPlainText(ticks))
        if (doc.title.isNotBlank()) { putExtra(Intent.EXTRA_SUBJECT, doc.title); putExtra(Intent.EXTRA_TITLE, doc.title) }
    }
    try { ctx.startActivity(Intent.createChooser(send, "Share")) } catch (e: Exception) { onMessage("No app available to share with.") }
}

/* ───────────── Hero ───────────── */

@Composable
private fun DocHero(doc: Doc, ticks: Set<String>) {
    val cs = MaterialTheme.colorScheme
    HeroCard(colors = listOf(cs.primary, LocalHeroDeep.current), blobShape = MaterialShapes.Cookie9Sided) {
        if (doc.title.isNotBlank()) InlineMarkdownText(doc.title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall, color = cs.onPrimary)
        else if (doc.summary.isBlank() && doc.sections.isEmpty()) Text("This result is empty. Try asking again.", style = MaterialTheme.typography.titleMedium, color = cs.onPrimary)
        if (doc.summary.isNotBlank()) InlineMarkdownText(doc.summary, style = MaterialTheme.typography.bodyLarge, color = cs.onPrimary.copy(alpha = 0.9f))
        if (doc.sections.count { it.kind == "checklist" && it.items.isNotEmpty() } > 1) {
            val (done, total) = doc.checklistProgress(ticks)
            Text("✓ $done of $total done", style = MaterialTheme.typography.labelLarge, color = cs.onPrimary)
        }
        val tags = doc.tags.filter { it.isNotBlank() }
        if (tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
            tags.forEach {
                Text(InlineMarkdown.plain(it), style = MaterialTheme.typography.labelLarge, color = cs.onPrimary,
                    modifier = Modifier.clip(CircleShape).background(cs.onPrimary.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}

/* ───────────── Sections ───────────── */

private val WARNING_WORDS = Regex("warn|caution|danger|unsafe|allerg|emergency|toxic|poison|hazard", RegexOption.IGNORE_CASE)
private val PARAGRAPH_BREAK = Regex("\\n[ \\t]*\\n")
private val BULLET_MARK = Regex("^\\s*[-*+•]\\s+")
private val STEP_MARK = Regex("^\\s*(?:step\\s*)?\\d{1,2}[.):]\\s+", RegexOption.IGNORE_CASE)
private val CHECK_MARK = Regex("^\\s*(?:\\[[ xX]?]\\s*|[-*+•☐☑✓✔]\\s+)")

private fun Section.hasNoContent() = heading.isBlank() && text.isBlank() && items.none { it.isNotBlank() } && cards.isEmpty() && rows.isEmpty() && kv.isEmpty()

@Composable
private fun DocSection(si: Int, s: Section, ticks: Set<String>, onTick: (String) -> Unit, onCopySection: () -> Unit, onCopyText: (String) -> Unit) {
    if (s.hasNoContent()) return
    val cs = MaterialTheme.colorScheme
    val copySection by rememberUpdatedState(onCopySection)
    val copyable = Modifier
        .pointerInput(Unit) { detectTapGestures(onLongPress = { copySection() }) }
        .semantics { onLongClick(label = "Copy section") { copySection(); true } }
    when (s.kind) {
        "callout" -> StatCard(copyable, container = cs.tertiaryContainer) {
            val body = s.text.ifBlank { s.items.joinToString("\n") }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(if (WARNING_WORDS.containsMatchIn(s.heading + " " + body)) Icons.Default.Warning else Icons.Default.Info, contentDescription = null,
                    tint = cs.onTertiaryContainer, modifier = Modifier.padding(top = 2.dp).size(22.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (s.heading.isNotBlank()) SectionHeading(s.heading, cs.onTertiaryContainer)
                    if (body.isNotBlank()) Paragraphs(body, color = cs.onTertiaryContainer)
                    if (s.text.isNotBlank()) Bullets(s.items, cs.onTertiaryContainer, cs.onTertiaryContainer)
                }
            }
            SectionExtras(s)
        }
        "quote" -> StatCard(copyable, container = cs.secondaryContainer) {
            val q = s.text.ifBlank { s.items.joinToString(" ") }.trim()
            if (s.heading.isNotBlank()) SectionHeading(s.heading, cs.onSecondaryContainer)
            if (q.isNotBlank()) {
                val shown = if (q.first() in "\"“”'‘«„") q else "“$q”"
                InlineMarkdownText(shown, style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic), color = cs.onSecondaryContainer)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onCopyText(InlineMarkdown.plain(q)) }, shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.textButtonColors(contentColor = cs.onSecondaryContainer)) {
                        Icon(CopyIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copy text")
                    }
                }
            }
            if (s.text.isNotBlank()) Bullets(s.items, cs.onSecondaryContainer, cs.onSecondaryContainer)
            SectionExtras(s)
        }
        "cards" -> Column(copyable, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.heading.isNotBlank()) Text(InlineMarkdown.plain(s.heading), Modifier.padding(start = 4.dp, top = 4.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            if (s.text.isNotBlank() || s.items.isNotEmpty() || s.rows.isNotEmpty() || s.kv.isNotEmpty()) StatCard {
                if (s.text.isNotBlank()) Paragraphs(s.text)
                Bullets(s.items)
                SectionExtras(s.copy(cards = emptyList()))
            }
            s.cards.forEach { CardItem(it) }
        }
        else -> StatCard(copyable) {
            if (s.kind == "checklist" && s.items.isNotEmpty()) ChecklistHeader(si, s, ticks)
            else if (s.heading.isNotBlank()) SectionHeading(s.heading, cs.primary)
            if (s.text.isNotBlank()) Paragraphs(s.text)
            when (s.kind) {
                "steps" -> Steps(s.items)
                "checklist" -> Checklist(si, s.items, ticks, onTick)
                else -> Bullets(s.items)
            }
            SectionExtras(s)
        }
    }
}

/** Content that arrived in a field the section's kind doesn't normally use is still shown. */
@Composable
private fun SectionExtras(s: Section) {
    if (s.rows.isNotEmpty()) DocTable(s.rows)
    if (s.kv.isNotEmpty()) KvRows(s.kv)
    s.cards.forEach { CardItem(it) }
}

@Composable
private fun SectionHeading(text: String, color: Color) =
    Text(InlineMarkdown.plain(text).uppercase(), Modifier.semantics { heading() }, style = MaterialTheme.typography.labelMedium, color = color)

@Composable
private fun Paragraphs(text: String, style: TextStyle = MaterialTheme.typography.bodyLarge, color: Color = Color.Unspecified) {
    text.split(PARAGRAPH_BREAK).map { it.trim('\n', '\r') }.filter { it.isNotBlank() }.forEach { InlineMarkdownText(it, style = style, color = color) }
}

@Composable
private fun Bullets(items: List<String>, dot: Color = MaterialTheme.colorScheme.primary, color: Color = Color.Unspecified) {
    items.filter { it.isNotBlank() }.forEach {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.padding(top = 9.dp).size(7.dp).background(dot, CircleShape))
            InlineMarkdownText(it.replaceFirst(BULLET_MARK, ""), style = MaterialTheme.typography.bodyLarge, color = color)
        }
    }
}

@Composable
private fun Steps(items: List<String>) {
    val cs = MaterialTheme.colorScheme
    items.filter { it.isNotBlank() }.forEachIndexed { i, it ->
        Row(Modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(28.dp).clip(MaterialShapes.Cookie6Sided.toShape()).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = cs.onPrimaryContainer)
            }
            InlineMarkdownText(it.replaceFirst(STEP_MARK, ""), Modifier.weight(1f).padding(top = 3.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ChecklistHeader(si: Int, s: Section, ticks: Set<String>) {
    val cs = MaterialTheme.colorScheme
    val total = s.items.size
    val done = s.items.indices.count { "$si:$it" in ticks }
    val progress by animateFloatAsState(if (total == 0) 0f else done.toFloat() / total, WavyProgressIndicatorDefaults.ProgressAnimationSpec, label = "checklist")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { if (s.heading.isNotBlank()) SectionHeading(s.heading, cs.primary) }
        Text("$done of $total done" + if (done == total) " ✓" else "", style = MaterialTheme.typography.labelLarge,
            color = if (done == total) cs.tertiary else cs.onSurfaceVariant)
    }
    LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
}

@Composable
private fun Checklist(si: Int, items: List<String>, ticks: Set<String>, onTick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    items.forEachIndexed { i, item ->
        val key = "$si:$i"
        val done = key in ticks
        Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).toggleable(done, role = Role.Checkbox) { onTick(key) }.padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Checkbox(done, onCheckedChange = null) }
            InlineMarkdownText(item.replaceFirst(CHECK_MARK, ""), Modifier.weight(1f).padding(vertical = 8.dp), style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (done) TextDecoration.LineThrough else null, color = if (done) cs.onSurfaceVariant else cs.onSurface)
        }
    }
}

@Composable
private fun KvRows(kv: List<KV>) {
    val cs = MaterialTheme.colorScheme
    kv.forEachIndexed { i, it ->
        if (i > 0) HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InlineMarkdownText(it.k, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            InlineMarkdownText(it.v, Modifier.weight(1.4f), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
        }
    }
}

@Composable
private fun CardItem(c: Card) {
    val cs = MaterialTheme.colorScheme
    StatCard(container = cs.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            InlineMarkdownText(c.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (c.meta.isNotBlank()) Text(InlineMarkdown.plain(c.meta), style = MaterialTheme.typography.labelLarge, color = cs.onPrimaryContainer,
                modifier = Modifier.widthIn(max = 160.dp).clip(CircleShape).background(cs.primaryContainer).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        if (c.body.isNotBlank()) Paragraphs(c.body, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * A real grid: every column is as wide as its widest cell (96–220dp, wrapping beyond that), short tables stretch to
 * the card width, wide ones scroll sideways. The first row is the header; short rows are padded with empty cells.
 */
@Composable
private fun DocTable(rows: List<List<String>>) {
    val cs = MaterialTheme.colorScheme
    val cols = rows.maxOfOrNull { it.size } ?: 0
    if (cols == 0) return
    val header = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
    val body = MaterialTheme.typography.bodyMedium
    Layout(
        content = {
            rows.indices.forEach { ri ->
                Box(Modifier.background(when { ri == 0 -> cs.surfaceContainerHighest; ri % 2 == 0 -> cs.surfaceContainer; else -> Color.Transparent }))
            }
            rows.forEachIndexed { ri, r ->
                repeat(cols) { ci -> InlineMarkdownText(r.getOrElse(ci) { "" }, Modifier.padding(horizontal = 10.dp, vertical = 8.dp), style = if (ri == 0) header else body) }
            }
        },
        // fillMaxWidth before horizontalScroll: the grid gets the visible width as its minWidth.
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).horizontalScroll(rememberScrollState()),
    ) { measurables, constraints ->
        val n = rows.size
        val cells = measurables.subList(n, measurables.size)
        val minPx = 96.dp.roundToPx()
        val maxPx = 220.dp.roundToPx()
        val widths = IntArray(cols) { ci -> (0 until n).maxOf { ri -> cells[ri * cols + ci].maxIntrinsicWidth(Constraints.Infinity) }.coerceIn(minPx, maxPx) }
        val natural = widths.sum()
        if (natural < constraints.minWidth) {
            val extra = constraints.minWidth - natural
            for (ci in 0 until cols) widths[ci] += extra / cols
            widths[cols - 1] += extra % cols
        }
        val placeables = cells.mapIndexed { idx, m -> m.measure(Constraints.fixedWidth(widths[idx % cols])) }
        val heights = IntArray(n) { ri -> (0 until cols).maxOf { ci -> placeables[ri * cols + ci].height } }
        val width = widths.sum()
        val height = heights.sum()
        val backgrounds = measurables.subList(0, n).mapIndexed { ri, m -> m.measure(Constraints.fixed(width, heights[ri])) }
        layout(width, height) {
            var y = 0
            for (ri in 0 until n) {
                backgrounds[ri].place(0, y)
                var x = 0
                for (ci in 0 until cols) { placeables[ri * cols + ci].place(x, y); x += widths[ci] }
                y += heights[ri]
            }
        }
    }
}

/* ───────────── Actions ───────────── */

@Composable
private fun DocActions(
    speaker: DocSpeaker,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onReadAloud: () -> Unit,
    onPdf: () -> Unit,
    onMarkdown: () -> Unit,
    onCopyMarkdown: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val speaking = speaker.speaking // read here so only the action bar recomposes when speech starts/stops
    val size = IconButtonDefaults.mediumContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        DocAction("Copy", Modifier.weight(1f)) {
            FilledTonalIconButton(onClick = onCopy, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(size)) { Icon(CopyIcon, contentDescription = "Copy") }
        }
        DocAction("Share", Modifier.weight(1f)) {
            FilledTonalIconButton(onClick = onShare, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(size)) { Icon(Icons.Default.Share, contentDescription = "Share") }
        }
        DocAction(if (speaking) "Stop" else "Read aloud", Modifier.weight(1f)) {
            FilledTonalIconToggleButton(checked = speaking, onCheckedChange = { onReadAloud() }, shapes = IconButtonDefaults.toggleableShapes(), modifier = Modifier.size(size)) {
                Icon(if (speaking) StopIcon else ReadAloudIcon, contentDescription = "Read aloud")
            }
        }
        DocAction("More", Modifier.weight(1f)) {
            Box {
                FilledTonalIconButton(onClick = { menu = true }, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(size)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.large) {
                    DropdownMenuItem(text = { Text("Save as PDF") }, onClick = { menu = false; onPdf() }, leadingIcon = { Icon(PrintIcon, contentDescription = null) })
                    DropdownMenuItem(text = { Text("Export Markdown") }, onClick = { menu = false; onMarkdown() }, leadingIcon = { Icon(FileIcon, contentDescription = null) })
                    DropdownMenuItem(text = { Text("Copy as Markdown") }, onClick = { menu = false; onCopyMarkdown() }, leadingIcon = { Icon(CopyIcon, contentDescription = null) })
                }
            }
        }
    }
}

/** An icon button with a visible caption (the button's own content description is what accessibility services read). */
@Composable
private fun DocAction(label: String, modifier: Modifier, button: @Composable () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        button()
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics {})
    }
}

/* Icons that aren't in material-icons-core (paths from Material Symbols, Apache 2.0). */
private fun docIcon(name: String, path: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(addPathNodes(path), fill = SolidColor(Color.Black)).build()

private val CopyIcon by lazy { docIcon("Copy", "M16,1H4c-1.1,0 -2,0.9 -2,2v14h2V3h12V1zM19,5H8c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2zM19,21H8V7h11v14z") }
private val ReadAloudIcon by lazy { docIcon("ReadAloud", "M3,9v6h4l5,5V4L7,9H3zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z") }
private val StopIcon by lazy { docIcon("Stop", "M6,6h12v12H6z") }
private val PrintIcon by lazy { docIcon("Print", "M19,8H5c-1.66,0 -3,1.34 -3,3v6h4v4h12v-4h4v-6c0,-1.66 -1.34,-3 -3,-3zM16,19H8v-5h8v5zM19,12c-0.55,0 -1,-0.45 -1,-1s0.45,-1 1,-1 1,0.45 1,1 -0.45,1 -1,1zM18,3H6v4h12V3z") }
private val FileIcon by lazy { docIcon("Markdown", "M14,2H6c-1.1,0 -1.99,0.9 -1.99,2L4,20c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2V8l-6,-6zM16,18H8v-2h8v2zM16,14H8v-2h8v2zM13,9V3.5L18.5,9H13z") }
