@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.mohithash.byok.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohithash.byok.engine.Field
import com.mohithash.byok.ui.Label
import com.mohithash.byok.ui.MealPhoto
import com.mohithash.byok.ui.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun voiceIntent(prompt: String) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    if (prompt.isNotBlank()) putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
}

/** Whether a voice-typing app is installed (the manifest declares the RECOGNIZE_SPEECH query, so this works on Android 11+). */
@Composable
fun rememberVoiceTypingAvailable(): Boolean {
    val ctx = LocalContext.current
    return remember(ctx) { runCatching { voiceIntent("").resolveActivity(ctx.packageManager) != null }.getOrDefault(false) }
}

/** Mic button for dictation: the recogniser's top result goes to [onText]. Show it only when [rememberVoiceTypingAvailable]. */
@Composable
fun VoiceTypingButton(prompt: String, onText: (String) -> Unit, enabled: Boolean = true) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull { it.isNotBlank() }?.let(onText)
    }
    IconButton({ runCatching { launcher.launch(voiceIntent(prompt)) } }, enabled = enabled, modifier = Modifier.semantics { contentDescription = "Voice input" }) {
        Text("🎤", fontSize = 20.sp, modifier = Modifier.alpha(if (enabled) 1f else 0.38f).clearAndSetSemantics {})
    }
}

/** A field's label; required fields end in " *" (read out as "required"). */
@Composable
private fun FieldLabel(f: Field) =
    Text(ToolFormLogic.label(f), modifier = if (f.required) Modifier.clearAndSetSemantics { contentDescription = "${f.label}, required" } else Modifier)

/**
 * Renders one spec field; photo fields report through [onPhoto]. Text fields get a voice-typing button when a
 * recogniser is installed; number fields accept digits, one decimal separator and a leading minus.
 */
@Composable
fun FieldEditor(f: Field, value: String, onChange: (String) -> Unit, photo: MealPhoto? = null, onPhoto: ((MealPhoto?) -> Unit)? = null, enabled: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    val voice = rememberVoiceTypingAvailable()
    val mic: (@Composable () -> Unit)? = if (voice) { { VoiceTypingButton(f.label, { onChange(ToolFormLogic.appendSpoken(value, it)) }, enabled) } } else null
    val placeholder: (@Composable () -> Unit)? = if (f.placeholder.isNotBlank()) { { Text(f.placeholder) } } else null
    when (f.type) {
        "longtext" -> OutlinedTextField(value, onChange, label = { FieldLabel(f) }, placeholder = placeholder, trailingIcon = mic, enabled = enabled, modifier = Modifier.fillMaxWidth(), minLines = 4, shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
        "number" -> OutlinedTextField(value, { onChange(ToolFormLogic.sanitizeNumber(it)) }, label = { FieldLabel(f) }, placeholder = placeholder, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        "chips" -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val selected = ToolFormLogic.chipSelection(f, value)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(if (f.required) Modifier.clearAndSetSemantics { contentDescription = "${f.label}, required" } else Modifier) { Label(ToolFormLogic.label(f)) }
                if (f.multi) Text("(${selected.size} selected)", style = MaterialTheme.typography.labelMedium, color = cs.primary)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                f.options.forEach { o ->
                    val on = o in selected
                    FilterChip(selected = on, onClick = { onChange(ToolFormLogic.toggleChip(f, value, o)) }, label = { Text(o) }, enabled = enabled,
                        leadingIcon = if (on) { { Icon(Icons.Default.Check, null, Modifier.size(FilterChipDefaults.IconSize)) } } else null)
                }
            }
        }
        "toggle" -> Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).toggleable(value == "yes", enabled = enabled, role = Role.Switch) { onChange(if (it) "yes" else "") }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(ToolFormLogic.label(f), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f)); Switch(value == "yes", null, enabled = enabled)
        }
        "photo" -> PhotoField(f, photo, onPhoto, enabled)
        else -> OutlinedTextField(value, onChange, label = { FieldLabel(f) }, placeholder = placeholder, trailingIcon = mic, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
    }
}

@Composable
private fun PhotoField(f: Field, photo: MealPhoto?, onPhoto: ((MealPhoto?) -> Unit)?, enabled: Boolean) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { b -> b?.let { error = null; onPhoto?.invoke(Photo.fromBitmap(it)) } }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) scope.launch {
            busy = true; error = null
            // Decoding a full-size photo takes a moment: keep it off the main thread.
            val p = withContext(Dispatchers.IO) { runCatching { Photo.fromUri(ctx, u) }.getOrNull() }
            busy = false
            if (p != null) onPhoto?.invoke(p) else error = "Couldn't open that image. Try another one."
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(if (f.required) Modifier.clearAndSetSemantics { contentDescription = "${f.label}, required" } else Modifier) { Label(ToolFormLogic.label(f)) }
        photo?.let { p ->
            Box(Modifier.fillMaxWidth()) {
                Image(p.bitmap.asImageBitmap(), "Selected photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(200.dp).clip(MaterialTheme.shapes.large))
                FilledTonalIconButton({ onPhoto?.invoke(null) }, Modifier.align(Alignment.TopEnd).padding(8.dp), enabled = enabled) { Icon(Icons.Default.Close, "Remove photo") }
            }
        }
        if (busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LoadingIndicator(Modifier.size(28.dp)); Text("Preparing photo…", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ runCatching { camera.launch(null) }.onFailure { error = "No camera app is available. Pick from the gallery instead." } }, enabled = enabled && !busy, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text(if (photo == null) "📷 Camera" else "📷 Retake") }
            OutlinedButton({ gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = enabled && !busy, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("🖼️ Gallery") }
        }
        if (f.placeholder.isNotBlank()) Text(f.placeholder, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}

/** A set of fields with local state, reporting the full map on every change. */
@Composable
fun FieldsForm(fields: List<Field>, initial: Map<String, String>, onChange: (Map<String, String>) -> Unit, photo: MealPhoto? = null, onPhoto: ((MealPhoto?) -> Unit)? = null) {
    var values by remember { mutableStateOf(fields.associate { it.key to (initial[it.key] ?: it.default) }) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        fields.forEach { f -> FieldEditor(f, values[f.key].orEmpty(), { v -> values = values + (f.key to v); onChange(values) }, photo, onPhoto) }
    }
}
