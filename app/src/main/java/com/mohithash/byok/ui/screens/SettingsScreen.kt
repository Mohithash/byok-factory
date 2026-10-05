@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.mohithash.byok.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import com.mohithash.byok.engine.Prefs
import com.mohithash.byok.engine.UsageStats
import com.mohithash.byok.ui.AppViewModel
import com.mohithash.byok.ui.KeyValue
import com.mohithash.byok.ui.Label
import com.mohithash.byok.ui.StatCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val PRIVACY_URL = "https://github.com/Mohithash/byok-factory/blob/main/PRIVACY.md"

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val spec = vm.spec
    val ai by vm.ai.collectAsState()
    val prefs by vm.prefs.collectAsState()
    val profile by vm.profile.collectAsState()
    val usage by vm.usage.collectAsState()
    val count by vm.count.collectAsState()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    fun say(m: String) { scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(m) } }

    /** "export" / "import" while a backup operation runs. */
    var busy by remember { mutableStateOf<String?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var confirmResetUsage by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            busy = "export"
            scope.launch {
                // NonCancellable: leaving the screen mid-way must not leave a half-written file.
                try { say("Saved ${settingsCountOf(withContext(NonCancellable) { vm.exportBackup(uri) }, "result")}") }
                catch (e: CancellationException) { throw e } catch (e: Exception) { say(e.message ?: "Couldn't save the backup.") }
                finally { busy = null }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = "import"
            scope.launch {
                try {
                    val n = withContext(NonCancellable) { vm.importBackup(uri) } // never stop half-way through an import
                    say(if (n == 0) "Nothing new — those results are already here" else "Added ${settingsCountOf(n, "result")}")
                } catch (e: CancellationException) { throw e } catch (e: Exception) { say(e.message ?: "Couldn't read that backup.") }
                finally { busy = null }
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(title = { Text("Settings") }, subtitle = { Text(spec.name) }, scrollBehavior = scroll,
                navigationIcon = { IconButton(onClick = dropUnlessResumed { onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface, scrolledContainerColor = cs.surface))
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AiSettingsCard(ai, vm.client, vm::saveAi, ::say)
            SettingsAnswersCard(prefs) { vm.savePrefs(it); say("Answer preferences saved") }
            SettingsAppearanceCard(prefs, spec.name) { vm.savePrefs(it) }
            if (spec.profile.isNotEmpty()) StatCard {
                var draft by remember(profile) { mutableStateOf(profile) }
                Label("About you")
                Text("Used to personalise every answer.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                // Re-created when the saved profile changes (e.g. a backup import), so the fields show it.
                key(profile) { FieldsForm(spec.profile, profile, { draft = it }) }
                Button({ vm.saveProfile(draft); say("Saved") }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Save") }
            }
            SettingsUsageCard(usage) { confirmResetUsage = true }

            StatCard {
                Label("Your data")
                Text(if (count == 0) "No saved results yet." else "${settingsCountOf(count, "saved result")} on this device.", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton({ exportLauncher.launch(settingsBackupName(spec.id, LocalDate.now())) }, enabled = busy == null, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                        if (busy == "export") LoadingIndicator(Modifier.size(20.dp)) else Text("Export backup", maxLines = 1)
                    }
                    OutlinedButton({ importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = busy == null, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
                        if (busy == "import") LoadingIndicator(Modifier.size(20.dp)) else Text("Import backup", maxLines = 1)
                    }
                }
                Text("A backup holds your results, profile and preferences as a JSON file. It never includes your API key. Importing adds results and skips ones you already have.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                OutlinedButton({ confirmDeleteAll = true }, enabled = count > 0 && busy == null, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error)) {
                    Icon(Icons.Default.Delete, null, Modifier.size(ButtonDefaults.IconSize)); Spacer(Modifier.size(ButtonDefaults.IconSpacing)); Text("Delete all results")
                }
                TextButton({ vm.restartOnboarding() }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(ButtonDefaults.IconSize)); Spacer(Modifier.size(ButtonDefaults.IconSpacing)); Text("Show welcome again")
                }
            }

            SettingsAboutCard(vm, ctx, ::say)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDeleteAll) AlertDialog(
        onDismissRequest = { confirmDeleteAll = false },
        icon = { Icon(Icons.Default.Delete, null) },
        title = { Text("Delete all results?") },
        text = { Text("This permanently removes ${settingsCountOf(count, "result")} from this device, including favourites and notes. Export a backup first if you might want them back.") },
        confirmButton = { TextButton({ confirmDeleteAll = false; val n = count; vm.deleteAll(); say("Deleted ${settingsCountOf(n, "result")}") }) { Text("Delete all", color = cs.error) } },
        dismissButton = { TextButton({ confirmDeleteAll = false }) { Text("Cancel") } },
    )
    if (confirmResetUsage) AlertDialog(
        onDismissRequest = { confirmResetUsage = false },
        title = { Text("Reset usage?") },
        text = { Text("Request and token counts go back to zero. This doesn't change anything with your provider.") },
        confirmButton = { TextButton({ confirmResetUsage = false; vm.resetUsage(); say("Usage reset") }) { Text("Reset") } },
        dismissButton = { TextButton({ confirmResetUsage = false }) { Text("Cancel") } },
    )
}

/** Answer language, length and standing instructions; edits are drafts until Save. */
@Composable
private fun SettingsAnswersCard(prefs: Prefs, onSave: (Prefs) -> Unit) {
    val cs = MaterialTheme.colorScheme
    // Drafts survive rotation and follow the saved values whenever those change (save, backup import).
    var language by rememberSaveable(prefs.language) { mutableStateOf(prefs.language) }
    var detail by rememberSaveable(prefs.detail) { mutableStateOf(prefs.detail) }
    var instructions by rememberSaveable(prefs.instructions) { mutableStateOf(prefs.instructions) }
    var saved by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val changed = language != prefs.language || detail != prefs.detail || instructions.trim() != prefs.instructions.trim()
    StatCard {
        Label("Answers")
        ExposedDropdownMenuBox(expanded = menu, onExpandedChange = { menu = it }) {
            OutlinedTextField(settingsLanguageLabel(language), {}, readOnly = true, singleLine = true, label = { Text("Language") }, shape = MaterialTheme.shapes.large,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menu) },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable))
            ExposedDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                settingsLanguageOptions().forEach { l ->
                    DropdownMenuItem(text = { Text(settingsLanguageLabel(l)) }, onClick = { language = l; menu = false; saved = false },
                        trailingIcon = { if (settingsLanguageLabel(l) == settingsLanguageLabel(language)) Icon(Icons.Default.Check, "Selected", tint = cs.primary) },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Length", style = MaterialTheme.typography.titleSmall)
        SettingsSegmented(Prefs.DETAILS.map { it to it.replaceFirstChar(Char::uppercase) }, detail) { detail = it; saved = false }
        Text(when (detail) { "brief" -> "Just the essentials, short sections."; "detailed" -> "Thorough answers with more depth and examples."; else -> "Balanced answers — the default." },
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(instructions, { instructions = it.take(Prefs.MAX_INSTRUCTIONS); saved = false }, label = { Text("Standing instructions") },
            placeholder = { Text("I live in the UK; use metric units; keep it beginner-friendly") },
            minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
            supportingText = {
                Row(Modifier.fillMaxWidth()) {
                    Text("Added to every request.", modifier = Modifier.weight(1f))
                    Text("${instructions.length}/${Prefs.MAX_INSTRUCTIONS}", textAlign = TextAlign.End)
                }
            })
        val showSaved = saved && !changed
        Button({ onSave(prefs.copy(language = language, detail = detail, instructions = instructions.trim())); saved = true }, enabled = changed,
            shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = if (showSaved) ButtonDefaults.buttonColors(disabledContainerColor = cs.secondaryContainer, disabledContentColor = cs.onSecondaryContainer) else ButtonDefaults.buttonColors(),
        ) { Text(if (showSaved) "Saved ✓" else "Save") }
    }
}

/** Theme and Material You; both apply immediately. */
@Composable
private fun SettingsAppearanceCard(prefs: Prefs, appName: String, onChange: (Prefs) -> Unit) {
    StatCard {
        Label("Appearance")
        Text("Theme", style = MaterialTheme.typography.titleSmall)
        SettingsSegmented(Prefs.THEMES.map { it to it.replaceFirstChar(Char::uppercase) }, prefs.theme.takeIf { it in Prefs.THEMES } ?: "system") { onChange(prefs.copy(theme = it)) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().toggleable(prefs.dynamicColor, role = Role.Switch) { onChange(prefs.copy(dynamicColor = it)) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Material You colours", style = MaterialTheme.typography.bodyLarge)
                    Text("Use your wallpaper's colours instead of $appName's.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(prefs.dynamicColor, onCheckedChange = null)
            }
        }
    }
}

@Composable
private fun SettingsUsageCard(usage: UsageStats, onReset: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    StatCard {
        Label("Usage")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(settingsNumber(usage.requests.toLong()), style = MaterialTheme.typography.displaySmall, color = cs.primary)
            Text(if (usage.requests == 1) "request made" else "requests made", style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        }
        KeyValue("Input tokens", settingsNumber(usage.inputTokens))
        KeyValue("Output tokens", settingsNumber(usage.outputTokens))
        Text(if (usage.since > 0) "Since ${settingsSince(usage.since)}" else "Nothing counted yet.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Text("Counted on this device. Your provider bills you directly.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        OutlinedButton(onReset, enabled = usage != UsageStats(), shapes = ButtonDefaults.shapes()) { Text("Reset") }
    }
}

@Composable
private fun SettingsAboutCard(vm: AppViewModel, ctx: Context, say: (String) -> Unit) {
    val spec = vm.spec
    val cs = MaterialTheme.colorScheme
    val version = remember { settingsVersionName(ctx) }
    StatCard {
        Label("About")
        Text(listOf(spec.name, spec.category, version?.let { "v$it" }).filter { !it.isNullOrBlank() }.joinToString(" · "), style = MaterialTheme.typography.titleSmall)
        if (spec.about.isNotBlank()) Text(spec.about, style = MaterialTheme.typography.bodyMedium)
        if (spec.disclaimer.isNotBlank()) Text(spec.disclaimer, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Text("Bring your own key: there's no account and no ${spec.name} server. Your key, profile and results stay on this phone; requests go straight from it to the AI provider you chose.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        TextButton({
            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))) }
            catch (e: ActivityNotFoundException) { say("No browser found. The policy is at $PRIVACY_URL") }
        }) { Text("Privacy policy ↗") }
    }
}

/** A connected single-choice button group. [options] are value → label. */
@Composable
private fun SettingsSegmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { i, (value, label) ->
            ToggleButton(checked = value == selected, onCheckedChange = { onSelect(value) },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.weight(1f)) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

private fun settingsVersionName(ctx: Context): String? = try {
    val pm = ctx.packageManager
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) pm.getPackageInfo(ctx.packageName, PackageManager.PackageInfoFlags.of(0))
        else @Suppress("DEPRECATION") pm.getPackageInfo(ctx.packageName, 0)
    info.versionName
} catch (e: Exception) { null }

/* ───────── pure helpers (unit-tested) ───────── */

/** Language choices: blank (English, the default) first, then every other language. */
internal fun settingsLanguageOptions(): List<String> = listOf("") + Prefs.LANGUAGES.filterNot { it.equals("English", ignoreCase = true) }

internal fun settingsLanguageLabel(language: String): String =
    if (language.isBlank() || language.equals("English", ignoreCase = true)) "English (default)" else language

/** "1 result", "1,234 results". */
internal fun settingsCountOf(n: Int, noun: String, locale: Locale = Locale.getDefault()): String =
    settingsNumber(n.toLong(), locale) + " " + if (n == 1) noun else noun + "s"

internal fun settingsNumber(n: Long, locale: Locale = Locale.getDefault()): String = String.format(locale, "%,d", n)

internal fun settingsSince(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
    Instant.ofEpochMilli(epochMillis).atZone(zone).format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))

/** Default file name for a backup: `<appId>-backup-<yyyy-MM-dd>.json`. */
internal fun settingsBackupName(appId: String, date: LocalDate): String = "$appId-backup-$date.json"
