@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.mohithash.byok.ui.screens

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mohithash.byok.engine.Field
import com.mohithash.byok.ui.Label
import com.mohithash.byok.ui.MealPhoto
import com.mohithash.byok.ui.Photo

/** Renders one spec field; photo fields report through [onPhoto]. */
@Composable
fun FieldEditor(f: Field, value: String, onChange: (String) -> Unit, photo: MealPhoto? = null, onPhoto: ((MealPhoto?) -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    when (f.type) {
        "longtext" -> OutlinedTextField(value, onChange, label = { Text(f.label) }, placeholder = { Text(f.placeholder) }, modifier = Modifier.fillMaxWidth(), minLines = 4, shape = MaterialTheme.shapes.large)
        "number" -> OutlinedTextField(value, { onChange(it.filter { c -> c.isDigit() || c == '.' }) }, label = { Text(f.label) }, placeholder = { Text(f.placeholder) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        "chips" -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label(f.label)
            val selected = value.split(", ").filter { it.isNotBlank() }.toSet()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                f.options.forEach { o ->
                    FilterChip(selected = o in selected, onClick = {
                        onChange(if (f.multi) (if (o in selected) selected - o else selected + o).joinToString(", ") else if (o in selected) "" else o)
                    }, label = { Text(o) })
                }
            }
        }
        "toggle" -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(f.label, style = MaterialTheme.typography.bodyLarge); Switch(value == "yes", { onChange(if (it) "yes" else "") }) }
        "photo" -> {
            val ctx = LocalContext.current
            val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { b -> b?.let { onPhoto?.invoke(Photo.fromBitmap(it)) } }
            val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u -> u?.let { onPhoto?.invoke(Photo.fromUri(ctx, it)) } }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Label(f.label)
                photo?.let { p -> Box(Modifier.fillMaxWidth()) { Image(p.bitmap.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(200.dp).clip(MaterialTheme.shapes.large)); FilledTonalIconButton({ onPhoto?.invoke(null) }, Modifier.align(Alignment.TopEnd).padding(8.dp)) { Icon(Icons.Default.Close, null) } } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ camera.launch(null) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("📷 Camera") }
                    OutlinedButton({ gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("🖼️ Gallery") }
                }
                if (f.placeholder.isNotBlank()) Text(f.placeholder, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
        else -> OutlinedTextField(value, onChange, label = { Text(f.label) }, placeholder = { Text(f.placeholder) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large)
    }
}

/** A set of fields with local state, reporting the full map on every change. */
@Composable
fun FieldsForm(fields: List<Field>, initial: Map<String, String>, onChange: (Map<String, String>) -> Unit, photo: MealPhoto? = null, onPhoto: ((MealPhoto?) -> Unit)? = null) {
    var values by remember { mutableStateOf(fields.associate { it.key to (initial[it.key] ?: it.default) }) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        fields.forEach { f -> FieldEditor(f, values[f.key].orEmpty(), { v -> values = values + (f.key to v); onChange(values) }, photo, onPhoto) }
    }
    Spacer(Modifier.size(0.dp))
}
