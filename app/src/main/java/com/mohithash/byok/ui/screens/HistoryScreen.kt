@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.mohithash.byok.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mohithash.byok.data.ResultRow
import com.mohithash.byok.ui.AppViewModel
import com.mohithash.byok.ui.EmptyState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(vm: AppViewModel, onBack: () -> Unit, onOpen: (ResultRow) -> Unit) {
    val all by vm.results.collectAsState()
    val cs = MaterialTheme.colorScheme
    var q by remember { mutableStateOf("") }
    val shown = all.filter { q.isBlank() || it.title.contains(q, true) || it.inputSummary.contains(q, true) || it.toolTitle.contains(q, true) }
    val fmt = DateTimeFormatter.ofPattern("d MMM, HH:mm")
    Scaffold(topBar = { TopAppBar(title = { Text("Saved") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface), navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { OutlinedTextField(q, { q = it }, placeholder = { Text("Search") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) }
            if (shown.isEmpty()) item { EmptyState(Icons.Default.Star, "Nothing saved yet", "Every result is kept here on your device. Star the ones you love.") }
            items(shown, key = { it.id }) { r ->
                ListItem(leadingContent = { Text(r.emoji, style = MaterialTheme.typography.headlineSmall) }, headlineContent = { Text(r.title, maxLines = 1) },
                    supportingContent = { Text(listOf(r.toolTitle, r.inputSummary, Instant.ofEpochMilli(r.createdAt).atZone(ZoneId.systemDefault()).format(fmt)).filter { it.isNotBlank() }.joinToString(" · "), maxLines = 2) },
                    trailingContent = { IconButton({ vm.delete(r) }) { Icon(Icons.Default.Delete, null, tint = cs.onSurfaceVariant) } },
                    colors = ListItemDefaults.colors(containerColor = if (r.favorite) cs.secondaryContainer else cs.surfaceContainerLow), modifier = Modifier.clip(MaterialTheme.shapes.large).clickable { onOpen(r) })
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
