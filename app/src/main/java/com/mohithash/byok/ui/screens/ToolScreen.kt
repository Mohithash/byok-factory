@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.mohithash.byok.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohithash.byok.data.ResultRow
import com.mohithash.byok.engine.Tool
import com.mohithash.byok.ui.AppViewModel
import com.mohithash.byok.ui.FormSeed
import com.mohithash.byok.ui.Job
import com.mohithash.byok.ui.Label
import com.mohithash.byok.ui.MealPhoto
import com.mohithash.byok.ui.StatCard
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * The tool form's state, scoped to this screen's back-stack entry so typed values and a picked photo survive
 * rotation and a trip to Settings (to add a key) and back.
 */
private class ToolFormHolder : ViewModel() {
    /** Tool the values belong to; plain field because it is only compared, never shown. */
    var toolId = ""
    var values by mutableStateOf<Map<String, String>>(emptyMap())
    var photo by mutableStateOf<MealPhoto?>(null)
    /** Repeats the last request (generate, follow-up or regenerate) for "Try again". */
    var retry by mutableStateOf<(() -> Unit)?>(null)

    fun load(t: Tool, seed: FormSeed?) { toolId = t.id; values = ToolFormLogic.seeded(t.inputs, seed?.values); photo = seed?.photo; retry = null }
}

private enum class ToolScreenDialog { Rename, Note, Delete, Stop }

@Composable
fun ToolScreen(vm: AppViewModel, onBack: () -> Unit, onSettings: () -> Unit) {
    val tool by vm.tool.collectAsState()
    val t = tool ?: run { LaunchedEffect(Unit) { onBack() }; return }
    val current by vm.current.collectAsState()
    val job by vm.job.collectAsState()
    val ai by vm.ai.collectAsState()
    val thread by vm.thread.collectAsState()
    val seed by vm.seed.collectAsState()
    val cs = MaterialTheme.colorScheme
    val loading = job == Job.Loading
    val form = viewModel(key = "tool-form") { ToolFormHolder() }
    // A different tool resets the form; a pending seed pre-fills it from the first frame.
    remember(t.id) { if (form.toolId != t.id) form.load(t, vm.seed.value) }

    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    fun message(m: String) { scope.launch { snack.showSnackbar(m) } }
    var dialog by rememberSaveable { mutableStateOf<Pair<ToolScreenDialog, Long>?>(null) }
    var ask by rememberSaveable(t.id) { mutableStateOf("") }
    // Once leaving, keep showing what was on screen during the exit animation and ignore further back presses.
    var left by remember { mutableStateOf(false) }
    val frozen = remember { arrayOfNulls<ResultRow>(1) }
    if (!left) frozen[0] = current
    val r = frozen[0]
    val seen = remember { HashMap<Long, ResultRow>() }
    r?.let { seen[it.id] = it }
    // Edit & rerun, shared content and shortcuts pre-fill the form through a seed, read once.
    LaunchedEffect(t.id, seed) {
        if (seed == null || left) return@LaunchedEffect
        vm.consumeSeed()?.let { s -> form.values = ToolFormLogic.seeded(t.inputs, s.values); form.photo = s.photo ?: form.photo; form.retry = null }
    }

    fun exit() { left = true; vm.clearJob(); onBack() }
    fun leave() { if (left) return; if (loading) dialog = ToolScreenDialog.Stop to 0L else exit() }
    BackHandler(enabled = !left) { leave() }
    LaunchedEffect(loading) { if (!loading && dialog?.first == ToolScreenDialog.Stop) dialog = null }
    LaunchedEffect(dialog, r?.id) { dialog?.let { (d, id) -> if (d != ToolScreenDialog.Stop && id != r?.id) dialog = null } }

    fun generate(values: Map<String, String>, photo: MealPhoto?) {
        keyboard?.hide(); focus.clearFocus()
        val image = photo?.base64
        form.retry = { vm.run(t, values, image) }
        vm.run(t, values, image)
    }
    fun askFollowUp(q: String): Boolean {
        if (q.isBlank() || loading) return false
        keyboard?.hide(); focus.clearFocus()
        form.retry = { vm.followUp(q) }
        vm.followUp(q)
        return true
    }
    fun regenerate() { form.retry = { vm.regenerate() }; vm.regenerate() }

    // A finished regenerate / follow-up / rerun shows the new result from the top.
    val scroll = rememberSaveable(r?.id, saver = ScrollState.Saver) { ScrollState(0) }
    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    LaunchedEffect(r?.id) { bar.state.heightOffset = 0f; bar.state.contentOffset = 0f }

    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(if (r != null) "${r.emoji} ${r.toolTitle}" else "${t.emoji} ${t.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                subtitle = if (r == null) null else { { val row = r; Text((if (row.isFollowUp) "Follow-up · " else "") + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(row.createdAt)), maxLines = 1) } },
                navigationIcon = { IconButton(::leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    r?.let { row ->
                        IconToggleButton(row.favorite, { vm.toggleFavorite(row) }) {
                            Icon(if (row.favorite) Icons.Default.Star else Icons.Default.FavoriteBorder, if (row.favorite) "Remove from favourites" else "Add to favourites", tint = if (row.favorite) cs.primary else cs.onSurfaceVariant)
                        }
                        ResultMenu(
                            canRegenerate = remember(row.id, row.inputsJson) { vm.canRegenerate(row) }, canEdit = !row.isFollowUp && vm.toolFor(row) != null, hasNote = row.note.isNotBlank(), loading = loading,
                            onRegenerate = ::regenerate,
                            onEditAndRerun = {
                                val needsPhoto = vm.hadPhoto(row) && form.photo == null
                                vm.editAndRerun()
                                if (needsPhoto) message("Add the photo again to rerun.")
                            },
                            onRename = { dialog = ToolScreenDialog.Rename to row.id }, onNote = { dialog = ToolScreenDialog.Note to row.id }, onDelete = { dialog = ToolScreenDialog.Delete to row.id },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface, scrolledContainerColor = cs.surfaceContainer),
                scrollBehavior = bar,
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad).imePadding().verticalScroll(scroll).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loading && r != null) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
            AnimatedContent(r?.id ?: 0L, label = "mode") { id ->
                if (id == 0L) ToolForm(t, form, ai.configured, job,
                    onGenerate = { generate(form.values, form.photo) }, onCancel = vm::cancel,
                    onRetry = form.retry ?: { generate(form.values, form.photo) }, onSettings = onSettings)
                else (if (id == r?.id) r else seen[id])?.let { row ->
                    ResultBody(vm, row, thread, job, ask, { ask = it },
                        onAsk = { q -> if (askFollowUp(q) && q == ask) ask = "" },
                        onRetry = form.retry, onSettings = onSettings, onEditNote = { dialog = ToolScreenDialog.Note to row.id },
                        onMessage = ::message, onStartOver = if (vm.toolFor(row) != null) { { vm.openTool(t) } } else null)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    val d = dialog
    val target = r?.takeIf { d != null && it.id == d.second }
    when (d?.first) {
        ToolScreenDialog.Rename -> target?.let { row ->
            TextDialog("Rename", row.title, "Title", multiLine = false, onDismiss = { dialog = null }) { vm.rename(row, it) }
        }
        ToolScreenDialog.Note -> target?.let { row ->
            TextDialog(if (row.note.isBlank()) "Add a note" else "Edit note", row.note, "Your note", multiLine = true, onDismiss = { dialog = null }) { vm.setNote(row, it) }
        }
        ToolScreenDialog.Delete -> target?.let { row ->
            AlertDialog(onDismissRequest = { dialog = null }, icon = { Icon(Icons.Default.Delete, null) }, title = { Text("Delete this result?") },
                text = { Text("“${row.title}” will be removed from your history.") },
                confirmButton = { TextButton({ dialog = null; if (loading) vm.cancel(); vm.delete(row); exit() }, colors = ButtonDefaults.textButtonColors(contentColor = cs.error)) { Text("Delete") } },
                dismissButton = { TextButton({ dialog = null }) { Text("Cancel") } })
        }
        ToolScreenDialog.Stop -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text("Stop generating?") },
            text = { Text("The answer isn't ready yet. Leaving now cancels it.") },
            confirmButton = { TextButton({ dialog = null; vm.cancel(); exit() }) { Text("Stop") } },
            dismissButton = { TextButton({ dialog = null }) { Text("Keep waiting") } })
        null -> Unit
    }
}

/** The form: fields, readiness hint, key hint, error card, and the generate / cancel buttons. */
@Composable
private fun ToolForm(t: Tool, form: ToolFormHolder, configured: Boolean, job: Job<Unit>, onGenerate: () -> Unit, onCancel: () -> Unit, onRetry: () -> Unit, onSettings: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val loading = job == Job.Loading
    val hasPhoto = form.photo != null
    val ready = ToolFormLogic.ready(t.inputs, form.values, hasPhoto)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard {
            if (t.subtitle.isNotBlank()) Text(t.subtitle, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            if (t.inputs.isEmpty()) Text("Nothing to fill in. This one works from your profile.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            else {
                Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    t.inputs.forEach { f -> FieldEditor(f, form.values[f.key].orEmpty(), { v -> form.values = form.values + (f.key to v) }, form.photo, { p -> form.photo = p }) }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (t.inputs.any { it.required }) Text("* required", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    TextButton({ form.values = ToolFormLogic.defaults(t.inputs); form.photo = null }, shapes = ButtonDefaults.shapes(),
                        enabled = !loading && ToolFormLogic.isDirty(t.inputs, form.values, hasPhoto)) { Text("Clear") }
                }
            }
        }
        if (!configured) StatCard(container = cs.secondaryContainer) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Add your AI key in Settings to start generating.", style = MaterialTheme.typography.bodyMedium, color = cs.onSecondaryContainer, modifier = Modifier.weight(1f))
                FilledTonalButton(onSettings, shapes = ButtonDefaults.shapes()) { Text("Settings") }
            }
        }
        (job as? Job.Failed)?.let { f -> ErrorCard(f.message, onRetry.takeIf { configured || !ToolFormLogic.pointsToSettings(f.message) }, onSettings) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onGenerate, enabled = configured && ready && !loading, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f).height(56.dp),
                colors = if (loading) ButtonDefaults.buttonColors(disabledContainerColor = cs.primaryContainer, disabledContentColor = cs.onPrimaryContainer) else ButtonDefaults.buttonColors()) {
                if (loading) {
                    LoadingIndicator(Modifier.size(28.dp), color = cs.onPrimaryContainer); Spacer(Modifier.width(10.dp))
                    Text(t.loading, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                } else Text(t.button, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (loading) OutlinedButton(onCancel, shapes = ButtonDefaults.shapes(), modifier = Modifier.height(56.dp)) { Text("Cancel") }
        }
        if (configured && !loading && !ready) {
            val missing = ToolFormLogic.missing(t.inputs, form.values, hasPhoto)
            Text(if (missing.isNotEmpty()) "Still needed: " + missing.joinToString(", ") { it.label } else "Fill in at least one field to continue.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

/** A result: thread strip, what was asked, the note, the document, errors, the follow-up composer and "Start over". */
@Composable
private fun ResultBody(
    vm: AppViewModel, r: ResultRow, thread: List<ResultRow>, job: Job<Unit>, ask: String, onAskChange: (String) -> Unit,
    onAsk: (String) -> Unit, onRetry: (() -> Unit)?, onSettings: () -> Unit, onEditNote: () -> Unit, onMessage: (String) -> Unit, onStartOver: (() -> Unit)?,
) {
    val cs = MaterialTheme.colorScheme
    val loading = job == Job.Loading
    val doc = remember(r.json) { vm.decode(r) }
    val ticks = remember(r.ticks) { vm.ticks(r) }
    val asked = remember(r.id, r.inputsJson, r.inputSummary) {
        if (r.isFollowUp) vm.question(r) else if (vm.hadPhoto(r)) "📷 " + (if (r.inputSummary == "photo") "a photo" else r.inputSummary) else r.inputSummary
    }
    val turns = remember(thread, r.threadId) { thread.filter { it.threadId == r.threadId } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (turns.size > 1) ThreadStrip(vm, turns, r.id, enabled = !loading)
        if (asked.isNotBlank()) Text("You asked: $asked", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
        if (r.note.isNotBlank()) Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(cs.tertiaryContainer).clickable(onClickLabel = "Edit note", onClick = onEditNote).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label("📝 Your note", cs.onTertiaryContainer)
            Text(r.note, style = MaterialTheme.typography.bodyMedium, color = cs.onTertiaryContainer)
        }
        DocView(doc = doc, ticks = ticks, onTick = { vm.toggleTick(r, it) }, onFollowUp = { onAsk(it) }, enabled = !loading, onMessage = { msg -> onMessage(msg) }, onSaveFile = vm::writeText)
        (job as? Job.Failed)?.let { ErrorCard(it.message, onRetry, onSettings) }
        FollowUpComposer(ask, onAskChange, loading, onAsk = { onAsk(ask) }, onCancel = vm::cancel)
        onStartOver?.let { OutlinedButton(it, enabled = !loading, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Start over") } }
    }
}

/** One compact row per turn of the thread; the open one is highlighted, the others open on tap. */
@Composable
private fun ThreadStrip(vm: AppViewModel, turns: List<ResultRow>, currentId: Long, enabled: Boolean) {
    val cs = MaterialTheme.colorScheme
    val questions = remember(turns) { turns.associate { it.id to (if (it.isFollowUp) vm.question(it) else it.inputSummary) } }
    StatCard {
        Label("In this thread")
        turns.forEach { row ->
            val selected = row.id == currentId
            val fg = if (selected) cs.onSecondaryContainer else cs.onSurface
            Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(if (selected) cs.secondaryContainer else Color.Transparent)
                .selectable(selected, enabled = enabled, role = Role.Tab) { if (!selected) vm.openResult(row) }
                .padding(horizontal = 12.dp, vertical = 8.dp)) {
                val q = questions[row.id].orEmpty()
                if (row.isFollowUp) Text("↳ $q", style = MaterialTheme.typography.bodyMedium, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (selected) FontWeight.SemiBold else null)
                else {
                    Text("${row.emoji} ${row.toolTitle}", style = MaterialTheme.typography.titleSmall, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (q.isNotBlank()) Text(q, style = MaterialTheme.typography.bodySmall, color = if (selected) fg else cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun FollowUpComposer(text: String, onText: (String) -> Unit, loading: Boolean, onAsk: () -> Unit, onCancel: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val voice = rememberVoiceTypingAvailable()
    val canAsk = text.isNotBlank() && !loading
    StatCard {
        Label("Ask a follow‑up")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, onText, placeholder = { Text("e.g. make it shorter / cheaper / for a beginner") }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.extraLarge, maxLines = 4,
                trailingIcon = if (voice) { { VoiceTypingButton("Ask a follow-up", { onText(ToolFormLogic.appendSpoken(text, it)) }, enabled = !loading) } } else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canAsk) onAsk() }))
            Button(onAsk, enabled = canAsk, shapes = ButtonDefaults.shapes()) { Text("Ask") }
        }
        if (loading) Row(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
            LoadingIndicator(Modifier.size(36.dp)); Spacer(Modifier.width(8.dp))
            Text("Thinking…", color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
            OutlinedButton(onCancel, shapes = ButtonDefaults.shapes()) { Text("Cancel") }
        }
    }
}

/** A failed request: the message, "Try again" and, when the problem is the key or settings, a shortcut there. */
@Composable
private fun ErrorCard(message: String, onRetry: (() -> Unit)?, onSettings: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    StatCard(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, container = cs.errorContainer) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Warning, null, tint = cs.onErrorContainer)
            Text(message, color = cs.onErrorContainer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (ToolFormLogic.pointsToSettings(message)) TextButton(onSettings, shapes = ButtonDefaults.shapes(), colors = ButtonDefaults.textButtonColors(contentColor = cs.onErrorContainer)) { Text("Settings") }
            onRetry?.let { Button(it, shapes = ButtonDefaults.shapes(), colors = ButtonDefaults.buttonColors(containerColor = cs.error, contentColor = cs.onError)) { Text("Try again") } }
        }
    }
}

@Composable
private fun ResultMenu(
    canRegenerate: Boolean, canEdit: Boolean, hasNote: Boolean, loading: Boolean,
    onRegenerate: () -> Unit, onEditAndRerun: () -> Unit, onRename: () -> Unit, onNote: () -> Unit, onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton({ open = true }) { Icon(Icons.Default.MoreVert, "More options") }
        DropdownMenu(open, { open = false }) {
            if (canRegenerate) DropdownMenuItem(text = { Text("Regenerate") }, onClick = { open = false; onRegenerate() }, enabled = !loading, leadingIcon = { Icon(Icons.Default.Refresh, null) })
            if (canEdit) DropdownMenuItem(text = { Text("Edit & rerun") }, onClick = { open = false; onEditAndRerun() }, enabled = !loading, leadingIcon = { Icon(Icons.Default.Edit, null) })
            DropdownMenuItem(text = { Text("Rename…") }, onClick = { open = false; onRename() }, leadingIcon = { Icon(Icons.Default.Create, null) })
            DropdownMenuItem(text = { Text(if (hasNote) "Edit note…" else "Add note…") }, onClick = { open = false; onNote() }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, null) })
            DropdownMenuItem(text = { Text("Delete") }, onClick = { open = false; onDelete() }, leadingIcon = { Icon(Icons.Default.Delete, null) })
        }
    }
}

/** A dialog with one text field (rename, note). Save is disabled while a single-line value is blank. */
@Composable
private fun TextDialog(title: String, initial: String, label: String, multiLine: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            OutlinedTextField(text, { text = it }, label = { Text(label) }, singleLine = !multiLine, minLines = if (multiLine) 3 else 1, maxLines = if (multiLine) 8 else 1,
                modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
        },
        confirmButton = { TextButton({ onSave(text); onDismiss() }, enabled = multiLine || text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}
