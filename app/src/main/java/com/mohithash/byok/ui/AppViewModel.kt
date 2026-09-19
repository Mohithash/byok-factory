package com.mohithash.byok.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mohithash.byok.App
import com.mohithash.byok.ai.AiSettings
import com.mohithash.byok.data.ResultRow
import com.mohithash.byok.engine.AppSpec
import com.mohithash.byok.engine.Doc
import com.mohithash.byok.engine.Tool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

sealed interface Job<out T> {
    data object Idle : Job<Nothing>
    data object Loading : Job<Nothing>
    data class Done<T>(val value: T) : Job<T>
    data class Failed(val message: String) : Job<Nothing>
}

private val mapSer = MapSerializer(String.serializer(), String.serializer())

class AppViewModel(private val app: App) : ViewModel() {
    val spec: AppSpec get() = app.spec
    val client get() = app.client
    private val db = app.db
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val ai: StateFlow<AiSettings> = app.store.flow("ai", AiSettings.serializer(), AiSettings())
    val profile: StateFlow<Map<String, String>> = app.store.flow("profile", mapSer, emptyMap())
    val onboarded: StateFlow<Boolean> = app.store.flow("onboarded", Boolean.serializer(), false)
    fun saveAi(a: AiSettings) = app.store.set("ai", AiSettings.serializer(), a)
    fun saveProfile(p: Map<String, String>) { app.store.set("profile", mapSer, p); app.store.set("onboarded", Boolean.serializer(), true) }

    val results = db.results().all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recent = db.results().recent(5).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Tool currently open and its latest result. */
    val tool = MutableStateFlow<Tool?>(null)
    val current = MutableStateFlow<ResultRow?>(null)
    private val _job = MutableStateFlow<Job<Unit>>(Job.Idle)
    val job: StateFlow<Job<Unit>> = _job

    fun decode(r: ResultRow): Doc = runCatching { json.decodeFromString(Doc.serializer(), r.json) }.getOrDefault(Doc())
    fun ticks(r: ResultRow): Set<String> = r.ticks.split(',').filter { it.isNotBlank() }.toSet()

    fun openTool(t: Tool) { tool.value = t; current.value = null; _job.value = Job.Idle }
    fun openResult(r: ResultRow) { tool.value = spec.tools.firstOrNull { it.id == r.toolId }; current.value = r; _job.value = Job.Idle }

    fun run(t: Tool, inputs: Map<String, String>, image: String?) {
        _job.value = Job.Loading
        viewModelScope.launch {
            _job.value = runCatching { app.engine.run(ai.value, t, inputs, profile.value, image) }.fold({ doc ->
                val summary = t.inputs.mapNotNull { f -> inputs[f.key]?.takeIf { it.isNotBlank() && f.type != "photo" } }.joinToString(" · ").ifBlank { if (image != null) "photo" else "" }
                val row = ResultRow(toolId = t.id, toolTitle = t.title, emoji = t.emoji, title = doc.title.ifBlank { t.title }, inputSummary = summary.take(140), json = json.encodeToString(Doc.serializer(), doc))
                val id = db.results().insert(row); current.value = row.copy(id = id); Job.Done(Unit)
            }, { Job.Failed(it.message ?: "Failed") })
        }
    }

    fun followUp(question: String) {
        val prev = current.value ?: return
        _job.value = Job.Loading
        viewModelScope.launch {
            _job.value = runCatching { app.engine.followUp(ai.value, decode(prev), question, profile.value) }.fold({ doc ->
                val row = ResultRow(toolId = prev.toolId, toolTitle = prev.toolTitle, emoji = prev.emoji, title = doc.title.ifBlank { question }, inputSummary = question.take(140), json = json.encodeToString(Doc.serializer(), doc))
                val id = db.results().insert(row); current.value = row.copy(id = id); Job.Done(Unit)
            }, { Job.Failed(it.message ?: "Failed") })
        }
    }

    fun toggleTick(r: ResultRow, key: String) = viewModelScope.launch {
        val t = ticks(r).toMutableSet(); if (!t.add(key)) t.remove(key)
        val u = r.copy(ticks = t.joinToString(",")); db.results().update(u); if (current.value?.id == r.id) current.value = u
    }
    fun toggleFavorite(r: ResultRow) = viewModelScope.launch { val u = r.copy(favorite = !r.favorite); db.results().update(u); if (current.value?.id == r.id) current.value = u }
    fun delete(r: ResultRow) = viewModelScope.launch { db.results().delete(r.id); if (current.value?.id == r.id) current.value = null }
    fun clearJob() { _job.value = Job.Idle }
}
