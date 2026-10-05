@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.mohithash.byok.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mohithash.byok.ai.AiClient
import com.mohithash.byok.ai.AiProvider
import com.mohithash.byok.ai.AiSettings
import com.mohithash.byok.ai.ChatMsg
import com.mohithash.byok.ui.Label
import com.mohithash.byok.ui.ShapeIcon
import com.mohithash.byok.ui.StatCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Reusable BYOK settings block: provider toggle, API key, model (suggestions or the account's own list), base URL,
 * connection test and save. Edits stay local until Save; [onSave] receives trimmed, normalised settings.
 */
@Composable
fun AiSettingsCard(current: AiSettings, client: AiClient, onSave: (AiSettings) -> Unit, onMessage: (String) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    // Messages arrive after network calls; always deliver them to the latest callback.
    val message by rememberUpdatedState(onMessage)
    var draft by remember { mutableStateOf(current) }
    /** The stored settings the draft was last synced with. */
    var base by remember { mutableStateOf(current) }
    var lastSaved by remember { mutableStateOf<AiSettings?>(null) }
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var fetching by remember { mutableStateOf(false) }
    /** Model ids fetched from the provider, tagged with the provider they came from. */
    var fetched by remember { mutableStateOf<Pair<AiProvider, List<String>>?>(null) }
    var modelMenu by remember { mutableStateOf(false) }

    val provider = draft.provider
    val built = normalizedAiSettings(draft)
    // Stored settings changed elsewhere (a save, a backup import): follow them unless there are unsaved edits.
    LaunchedEffect(current) {
        if (current != base) {
            if (built == normalizedAiSettings(base) || built == lastSaved) draft = current
            base = current
        }
    }
    val urlError = aiBaseUrlError(draft.baseUrl)
    val keyHint = aiKeyHint(built)
    val justSaved = lastSaved != null && built == lastSaved
    val changed = built != normalizedAiSettings(current) && !justSaved
    val fetchedHere = fetched?.takeIf { it.first == provider }?.second
    val suggestions = fetchedHere ?: provider.suggestedModels
    val options = aiModelOptions(suggestions, draft.model)
    val busyNetwork = testing || fetching

    fun pick(p: AiProvider) { if (p != draft.provider) { draft = switchAiProvider(draft, p); modelMenu = false } }
    fun open(url: String) = try { uriHandler.openUri(url) } catch (e: Exception) { onMessage("Open $url in your browser") }

    StatCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShapeIcon(Icons.Default.Lock, cs.primaryContainer, cs.onPrimaryContainer, MaterialShapes.Cookie7Sided)
            Column { Text("AI provider", style = MaterialTheme.typography.titleMedium); Label("Bring your own key") }
        }
        Text("Your key is stored only on this device and sent only to the provider below.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            ToggleButton(checked = provider == AiProvider.ANTHROPIC, onCheckedChange = { pick(AiProvider.ANTHROPIC) },
                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(), contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.weight(1f)) { Text("Claude") }
            ToggleButton(checked = provider == AiProvider.OPENAI_COMPAT, onCheckedChange = { pick(AiProvider.OPENAI_COMPAT) },
                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(), contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.weight(1f)) { Text("OpenAI‑compatible", textAlign = TextAlign.Center) }
        }

        OutlinedTextField(draft.apiKey, { draft = draft.copy(apiKey = it) }, label = { Text("API key") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            trailingIcon = { TextButton({ showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") } },
            supportingText = keyHint?.let { { Text(it) } })
        if (draft.apiKey.isBlank()) {
            val (where, url) = aiKeyPage(provider)
            TextButton({ open(url) }) { Text("Get a key at $where ↗") }
        }

        ExposedDropdownMenuBox(expanded = modelMenu && options.isNotEmpty(), onExpandedChange = { modelMenu = it }) {
            OutlinedTextField(draft.model, { draft = draft.copy(model = it); modelMenu = true }, label = { Text("Model") }, placeholder = { Text(provider.defaultModel) },
                singleLine = true, shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modelMenu && options.isNotEmpty(), Modifier.menuAnchor(ExposedDropdownMenuAnchorType.SecondaryEditable)) },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable))
            ExposedDropdownMenu(expanded = modelMenu && options.isNotEmpty(), onDismissRequest = { modelMenu = false }) {
                options.forEach { id ->
                    DropdownMenuItem(text = { Text(id, maxLines = 1) }, onClick = { draft = draft.copy(model = id); modelMenu = false },
                        trailingIcon = {
                            when {
                                id == built.effectiveModel -> Icon(Icons.Default.Check, "Selected", tint = cs.primary)
                                id == provider.defaultModel -> Text("default", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                            }
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (fetchedHere != null) "${aiModelCount(fetchedHere.size)} from your account. You can also type any model id."
                else "Leave blank for ${provider.defaultModel}, pick a suggestion or type any model id.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                val s = built
                fetching = true
                scope.launch {
                    try {
                        val ids = aiChatModels(client.listModels(s), s.provider)
                        if (ids.isEmpty()) message("Your provider didn't list any models.")
                        else { fetched = s.provider to ids; if (draft.provider == s.provider) modelMenu = true; message("Found ${aiModelCount(ids.size)}") }
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { message(e.message ?: "Couldn't fetch models") } finally { fetching = false }
                }
            }, enabled = built.configured && urlError == null && !fetching) {
                if (fetching) LoadingIndicator(Modifier.size(20.dp)) else Text("Fetch models")
            }
        }

        OutlinedTextField(draft.baseUrl, { draft = draft.copy(baseUrl = it) }, label = { Text("Base URL") }, placeholder = { Text(provider.defaultBaseUrl) }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, isError = urlError != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
            supportingText = {
                Text(when {
                    urlError != null -> urlError
                    draft.baseUrl.trim().startsWith("http://", ignoreCase = true) -> "Android usually blocks plain http:// — use https:// if requests fail."
                    provider == AiProvider.OPENAI_COMPAT -> "Works with OpenAI, Groq, OpenRouter, Ollama, LM Studio…"
                    else -> "Leave blank unless you use a proxy or gateway."
                })
            })

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = {
                    val s = built
                    testing = true
                    scope.launch {
                        try {
                            val r = client.chatFull(s, "Reply with the single word OK.", listOf(ChatMsg("user", "ping")), maxTokens = 64)
                            message("Connected — ${r.model.ifBlank { s.effectiveModel }} replied “${r.text.trim().take(40)}”")
                        } catch (e: CancellationException) { throw e } catch (e: Exception) { message(e.message ?: "Connection failed") } finally { testing = false }
                    }
                }, enabled = built.configured && urlError == null && !busyNetwork, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f),
            ) { if (testing) LoadingIndicator(Modifier.size(20.dp)) else Text("Test") }
            Button(
                onClick = { onSave(built); lastSaved = built; onMessage("AI settings saved") },
                enabled = changed && urlError == null, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f),
                colors = if (justSaved) ButtonDefaults.buttonColors(disabledContainerColor = cs.secondaryContainer, disabledContentColor = cs.onSecondaryContainer) else ButtonDefaults.buttonColors(),
            ) { Text(if (justSaved) "Saved ✓" else "Save") }
        }
    }
}

/* ───────── pure helpers (unit-tested) ───────── */

/** Settings as they should be stored: trimmed, base URL without a trailing slash or `/v1` (the client adds it), blank when it's the provider default. */
internal fun normalizedAiSettings(s: AiSettings): AiSettings {
    var url = s.baseUrl.trim().trimEnd('/')
    if (url.endsWith("/v1", ignoreCase = true)) url = url.dropLast(3).trimEnd('/')
    if (url.equals(s.provider.defaultBaseUrl, ignoreCase = true)) url = ""
    return s.copy(apiKey = s.apiKey.trim(), model = s.model.trim(), baseUrl = url)
}

/** Switches provider keeping the key; model and base URL are cleared only when they were the old provider's defaults. */
internal fun switchAiProvider(s: AiSettings, to: AiProvider): AiSettings {
    if (s.provider == to) return s
    val old = s.provider
    val m = s.model.trim()
    val model = if (m == old.defaultModel || m in old.suggestedModels) "" else s.model
    val url = s.baseUrl.trim().trimEnd('/')
    val base = if (url.equals(old.defaultBaseUrl, ignoreCase = true) || url.equals(old.defaultBaseUrl + "/v1", ignoreCase = true)) "" else s.baseUrl
    return s.copy(provider = to, model = model, baseUrl = base)
}

/** Suggestions to show under the model field: all of them until the typed text narrows the list. */
internal fun aiModelOptions(all: List<String>, typed: String): List<String> {
    val t = typed.trim()
    if (t.isEmpty() || t in all) return all
    return all.filter { it.contains(t, ignoreCase = true) }
}

private val NON_CHAT_MODEL = Regex("embed|whisper|tts|dall-e|moderation|transcribe|audio|realtime|davinci|babbage|rerank|image|search", RegexOption.IGNORE_CASE)

/** Cleans a provider's model list for the picker: no blanks or duplicates, no embedding/speech/image models (unless that's all there is). */
internal fun aiChatModels(ids: List<String>, provider: AiProvider): List<String> {
    val clean = ids.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val chat = clean.filterNot { NON_CHAT_MODEL.containsMatchIn(it) }.ifEmpty { clean }
    // Anthropic lists newest first; OpenAI-compatible servers list in no useful order.
    return if (provider == AiProvider.OPENAI_COMPAT) chat.sortedWith(String.CASE_INSENSITIVE_ORDER) else chat
}

/** A gentle warning when the key looks wrong for the chosen provider, or null. */
internal fun aiKeyHint(s: AiSettings): String? {
    val k = s.apiKey.trim()
    if (k.isEmpty()) return null
    if (k.any { it.isWhitespace() }) return "The key contains a space or line break — paste it again."
    return when (s.provider) {
        AiProvider.ANTHROPIC -> if (s.baseUrl.isBlank() && !k.startsWith("sk-ant-")) "Claude keys start with sk-ant- — check you pasted the whole key." else null
        AiProvider.OPENAI_COMPAT -> if (k.startsWith("sk-ant-")) "That looks like a Claude key — choose Claude above." else null
    }
}

/** Why [url] can't be used as a base URL, or null when it's fine (blank means the provider default). */
internal fun aiBaseUrlError(url: String): String? {
    val u = url.trim()
    if (u.isEmpty()) return null
    val scheme = u.substringBefore("://", "").lowercase()
    if (scheme != "https" && scheme != "http") return "Start with https://"
    val host = u.substringAfter("://").substringBefore('/')
    if (host.isBlank() || u.any { it.isWhitespace() }) return "That doesn't look like a web address."
    return null
}

/** Where to get a key for [p]: a short label and the page URL. */
internal fun aiKeyPage(p: AiProvider): Pair<String, String> = when (p) {
    AiProvider.ANTHROPIC -> "console.anthropic.com" to "https://console.anthropic.com/settings/keys"
    AiProvider.OPENAI_COMPAT -> "platform.openai.com" to "https://platform.openai.com/api-keys"
}

internal fun aiModelCount(n: Int): String = if (n == 1) "1 model" else "$n models"
