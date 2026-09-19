@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.mohithash.byok.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mohithash.byok.ui.AppViewModel
import com.mohithash.byok.ui.Job
import com.mohithash.byok.ui.Label
import com.mohithash.byok.ui.MealPhoto
import com.mohithash.byok.ui.StatCard

@Composable
fun ToolScreen(vm: AppViewModel, onBack: () -> Unit, onSettings: () -> Unit) {
    val tool by vm.tool.collectAsState()
    val t = tool ?: run { onBack(); return }
    val current by vm.current.collectAsState()
    val job by vm.job.collectAsState()
    val ai by vm.ai.collectAsState()
    val cs = MaterialTheme.colorScheme
    var values by remember(t.id) { mutableStateOf(t.inputs.associate { it.key to it.default }) }
    var photo by remember(t.id) { mutableStateOf<MealPhoto?>(null) }
    var ask by remember { mutableStateOf("") }
    val hasPhotoField = t.inputs.any { it.type == "photo" }
    val ready = t.inputs.filter { it.required }.all { f -> if (f.type == "photo") photo != null else values[f.key].orEmpty().isNotBlank() } &&
        (t.inputs.isEmpty() || t.inputs.any { f -> if (f.type == "photo") photo != null else values[f.key].orEmpty().isNotBlank() })

    Scaffold(topBar = {
        TopAppBar(title = { Text("${t.emoji} ${t.title}", maxLines = 1) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface),
            navigationIcon = { IconButton({ vm.clearJob(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            actions = { current?.let { r -> IconButton({ vm.toggleFavorite(r) }) { Icon(if (r.favorite) Icons.Default.Star else Icons.Default.FavoriteBorder, null, tint = cs.secondary) }; IconButton({ vm.delete(r); onBack() }) { Icon(Icons.Default.Delete, null) } } })
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AnimatedContent(current == null, label = "mode") { showForm ->
                if (showForm) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard {
                        Text(t.subtitle, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                        FieldsForm(t.inputs, values, { values = it }, photo, if (hasPhotoField) { p -> photo = p } else null)
                    }
                    if (!ai.configured) Row(verticalAlignment = Alignment.CenterVertically) { Text("Add an API key first.", color = cs.error, style = MaterialTheme.typography.bodySmall); TextButton(onSettings) { Text("Settings") } }
                    (job as? Job.Failed)?.let { StatCard(container = cs.errorContainer) { Text(it.message, color = cs.onErrorContainer) } }
                    Button({ vm.run(t, values, photo?.base64) }, enabled = ai.configured && ready && job != Job.Loading, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        if (job == Job.Loading) { LoadingIndicator(Modifier.size(22.dp)); Spacer(Modifier.size(10.dp)); Text(t.loading) } else Text(t.button, style = MaterialTheme.typography.titleMedium)
                    }
                } else current?.let { r ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (r.inputSummary.isNotBlank()) Text("You asked: ${r.inputSummary}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        DocView(vm.decode(r), vm.ticks(r), { vm.toggleTick(r, it) }, { vm.followUp(it) }, enabled = job != Job.Loading)
                        if (job == Job.Loading) Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { LoadingIndicator(); Spacer(Modifier.size(10.dp)); Text("Thinking…", color = cs.onSurfaceVariant) }
                        (job as? Job.Failed)?.let { StatCard(container = cs.errorContainer) { Text(it.message, color = cs.onErrorContainer) } }
                        StatCard {
                            Label("Ask a follow‑up")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(ask, { ask = it }, placeholder = { Text("e.g. make it shorter / cheaper / for a beginner") }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.extraLarge, maxLines = 3)
                                Button({ vm.followUp(ask); ask = "" }, enabled = ask.isNotBlank() && job != Job.Loading, shapes = ButtonDefaults.shapes()) { Text("Ask") }
                            }
                        }
                        OutlinedButton({ vm.openTool(t) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Start over") }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
