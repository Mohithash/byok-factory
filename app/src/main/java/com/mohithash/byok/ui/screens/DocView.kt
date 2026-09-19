@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.mohithash.byok.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mohithash.byok.engine.Doc
import com.mohithash.byok.engine.Section
import com.mohithash.byok.ui.HeroCard
import com.mohithash.byok.ui.Label
import com.mohithash.byok.ui.StatCard
import com.mohithash.byok.ui.theme.LocalHeroDeep

fun Doc.plainText(): String = buildString {
    appendLine(title); appendLine(summary); appendLine()
    sections.forEach { s ->
        if (s.heading.isNotBlank()) appendLine(s.heading.uppercase())
        if (s.text.isNotBlank()) appendLine(s.text)
        s.items.forEachIndexed { i, it -> appendLine(if (s.kind == "steps") "${i + 1}. $it" else "• $it") }
        s.cards.forEach { appendLine("${it.title}${if (it.meta.isNotBlank()) " (${it.meta})" else ""}: ${it.body}") }
        s.rows.forEach { appendLine(it.joinToString(" | ")) }
        s.kv.forEach { appendLine("${it.k}: ${it.v}") }
        appendLine()
    }
}

/** Renders a structured [Doc]. [ticks] are checked checklist keys "sectionIdx:itemIdx". */
@Composable
fun DocView(doc: Doc, ticks: Set<String>, onTick: (String) -> Unit, onFollowUp: (String) -> Unit, enabled: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HeroCard(colors = listOf(cs.primary, LocalHeroDeep.current), blobShape = MaterialShapes.Cookie9Sided) {
            Text(doc.title, style = MaterialTheme.typography.headlineSmall, color = cs.onPrimary)
            if (doc.summary.isNotBlank()) Text(doc.summary, color = cs.onPrimary.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyLarge)
            if (doc.tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                doc.tags.forEach { SuggestionChip(onClick = {}, label = { Text(it) }, colors = SuggestionChipDefaults.suggestionChipColors(containerColor = cs.onPrimary.copy(alpha = 0.14f), labelColor = cs.onPrimary), border = null) }
            }
        }
        doc.sections.forEachIndexed { si, s -> SectionView(si, s, ticks, onTick) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton({ clip.setText(AnnotatedString(doc.plainText())) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("Copy") }
            FilledTonalButton({ ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, doc.plainText()) }, "Share")) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("Share") }
        }
        if (doc.followups.isNotEmpty()) {
            Label("Ask next")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                doc.followups.forEach { q -> SuggestionChip(onClick = { if (enabled) onFollowUp(q) }, label = { Text(q) }) }
            }
        }
    }
}

@Composable
private fun SectionView(si: Int, s: Section, ticks: Set<String>, onTick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    when (s.kind) {
        "callout" -> StatCard(container = cs.tertiaryContainer) { if (s.heading.isNotBlank()) Label(s.heading, cs.onTertiaryContainer); Text(s.text.ifBlank { s.items.joinToString("\n") }, color = cs.onTertiaryContainer, style = MaterialTheme.typography.bodyLarge) }
        "quote" -> StatCard(container = cs.secondaryContainer) { if (s.heading.isNotBlank()) Label(s.heading, cs.onSecondaryContainer); Text("“${s.text.ifBlank { s.items.joinToString(" ") }}”", color = cs.onSecondaryContainer, style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic)) }
        "cards" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.heading.isNotBlank()) Text(s.heading, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            s.cards.forEach { c -> StatCard(container = cs.surfaceContainerHigh) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(c.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); if (c.meta.isNotBlank()) Text(c.meta, style = MaterialTheme.typography.labelLarge, color = cs.primary) }
                if (c.body.isNotBlank()) Text(c.body, style = MaterialTheme.typography.bodyMedium)
            } }
        }
        else -> StatCard {
            if (s.heading.isNotBlank()) Label(s.heading, cs.primary)
            when (s.kind) {
                "bullets" -> s.items.forEach { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Box(Modifier.padding(top = 9.dp).size(7.dp).background(cs.primary, CircleShape)); Text(it, style = MaterialTheme.typography.bodyLarge) } }
                "steps" -> s.items.forEachIndexed { i, it ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(28.dp).clip(MaterialShapes.Cookie6Sided.toShape()).background(cs.primaryContainer), contentAlignment = Alignment.Center) { Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = cs.onPrimaryContainer) }
                        Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(top = 3.dp))
                    }
                }
                "checklist" -> s.items.forEachIndexed { i, it ->
                    val key = "$si:$i"; val done = key in ticks
                    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { onTick(key) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(done, { onTick(key) }); Text(it, style = MaterialTheme.typography.bodyLarge, textDecoration = if (done) TextDecoration.LineThrough else null, color = if (done) cs.onSurfaceVariant else cs.onSurface)
                    }
                }
                "table" -> Column(Modifier.horizontalScroll(rememberScrollState())) {
                    s.rows.forEachIndexed { ri, row ->
                        Row(Modifier.background(if (ri == 0) cs.surfaceContainerHighest else if (ri % 2 == 0) cs.surfaceContainer else cs.surfaceContainerLow).padding(vertical = 6.dp)) {
                            row.forEach { cell -> Text(cell, Modifier.width(120.dp).padding(horizontal = 8.dp), style = if (ri == 0) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
                "kv" -> s.kv.forEach { Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(it.k, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f)); Text(it.v, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.4f)) } }
                else -> { if (s.text.isNotBlank()) Text(s.text, style = MaterialTheme.typography.bodyLarge); s.items.forEach { Text("•  $it", style = MaterialTheme.typography.bodyLarge) } }
            }
        }
    }
}
