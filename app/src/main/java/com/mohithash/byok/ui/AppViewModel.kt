package com.mohithash.byok.ui

import android.net.Uri
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mohithash.byok.App
import com.mohithash.byok.ai.AiSettings
import com.mohithash.byok.data.Backup
import com.mohithash.byok.data.Backups
import com.mohithash.byok.data.ResultRow
import com.mohithash.byok.engine.AppSpec
import com.mohithash.byok.engine.Doc
import com.mohithash.byok.engine.Prefs
import com.mohithash.byok.engine.Tool
import com.mohithash.byok.engine.Turn
import com.mohithash.byok.engine.UsageStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

sealed interface Job<out T> {
    data object Idle : Job<Nothing>
    data object Loading : Job<Nothing>
    data class Done<T>(val value: T) : Job<T>
    data class Failed(val message: String) : Job<Nothing>
}

/** Values (and optionally a photo) to pre-fill a tool's form with: edit & rerun, shared content, shortcuts. */
data class FormSeed(val values: Map<String, String> = emptyMap(), val photo: MealPhoto? = null)

/** Text and/or an image another app shared to us, waiting for the user to pick a tool. */
data class Incoming(val text: String = "", val photo: MealPhoto? = null)

private val mapSer = MapSerializer(String.serializer(), String.serializer())
const val PHOTO_FLAG = "__photo"
/** Backups are JSON text; anything bigger than this isn't one of ours (and would risk running out of memory). */
private const val MAX_BACKUP_BYTES = 50 * 1024 * 1024

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(private val app: App) : ViewModel() {
    val spec: AppSpec get() = app.spec
    val client get() = app.client
    private val db = app.db
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /* ───────── settings ───────── */
    val ai: StateFlow<AiSettings> = app.secrets.flow("ai", AiSettings.serializer(), AiSettings())
    val profile: StateFlow<Map<String, String>> = app.store.flow("profile", mapSer, emptyMap())
    val onboarded: StateFlow<Boolean> = app.store.flow("onboarded", Boolean.serializer(), false)
    val prefs: StateFlow<Prefs> = app.store.flow("prefs", Prefs.serializer(), Prefs())
    val usage: StateFlow<UsageStats> = app.store.flow("usage", UsageStats.serializer(), UsageStats())
    fun saveAi(a: AiSettings) = app.secrets.set("ai", AiSettings.serializer(), a)
    fun saveProfile(p: Map<String, String>) { app.store.set("profile", mapSer, p); app.store.set("onboarded", Boolean.serializer(), true) }
    fun savePrefs(p: Prefs) = app.store.set("prefs", Prefs.serializer(), p.copy(instructions = p.instructions.take(Prefs.MAX_INSTRUCTIONS)))
    fun resetUsage() = app.store.set("usage", UsageStats.serializer(), UsageStats())
    /** Puts the app back to first-run: onboarding shows again. Results and key are kept. */
    fun restartOnboarding() = app.store.set("onboarded", Boolean.serializer(), false)

    /* ───────── results ───────── */
    val results = db.results().all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recent = db.results().recent(5).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favorites = db.results().favorites().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val count = db.results().count().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Tool currently open and its latest result. */
    val tool = MutableStateFlow<Tool?>(null)
    val current = MutableStateFlow<ResultRow?>(null)
    /** The whole thread the current result belongs to (first result, then its follow-ups, oldest first). */
    val thread: StateFlow<List<ResultRow>> = current.map { it?.threadId ?: 0L }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == 0L) flowOf(emptyList()) else db.results().thread(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    /** Pre-fill for the tool form; consumed by the form when it opens. */
    val seed = MutableStateFlow<FormSeed?>(null)
    /** Content shared from another app, until the user picks a tool for it. */
    val incoming = MutableStateFlow<Incoming?>(null)
    /** Tool id a launcher shortcut asked to open. */
    val pendingToolId = MutableStateFlow<String?>(null)

    private val _job = MutableStateFlow<Job<Unit>>(Job.Idle)
    val job: StateFlow<Job<Unit>> = _job
    private var running: kotlinx.coroutines.Job? = null
    /** Last deleted row, kept so a snackbar can undo it. */
    private var lastDeleted: ResultRow? = null

    fun decode(r: ResultRow): Doc = runCatching { json.decodeFromString(Doc.serializer(), r.json) }.getOrDefault(Doc())
    fun ticks(r: ResultRow): Set<String> = r.ticks.split(',').filter { it.isNotBlank() }.toSet()
    /** The text inputs a result was made from (no photo — photos aren't stored). */
    fun inputs(r: ResultRow): Map<String, String> = runCatching { json.decodeFromString(mapSer, r.inputsJson) }.getOrDefault(emptyMap()) - PHOTO_FLAG
    fun hadPhoto(r: ResultRow): Boolean = runCatching { json.decodeFromString(mapSer, r.inputsJson)[PHOTO_FLAG] == "yes" }.getOrDefault(false)
    fun toolFor(r: ResultRow): Tool? = spec.tools.firstOrNull { it.id == r.toolId }
    fun toolById(id: String): Tool? = spec.tools.firstOrNull { it.id == id }

    fun openTool(t: Tool, seed: FormSeed? = null) {
        tool.value = t; current.value = null; this.seed.value = seed; _job.value = Job.Idle
        // Lets the launcher rank this tool's long-press shortcut by how often it's used.
        runCatching { ShortcutManagerCompat.reportShortcutUsed(app, "tool_${t.id}") }
    }
    fun openResult(r: ResultRow) { tool.value = toolFor(r) ?: spec.tools.firstOrNull(); current.value = r; seed.value = null; _job.value = Job.Idle }
    /** Called by the form once it has read the seed, so it isn't applied twice. */
    fun consumeSeed(): FormSeed? = seed.value.also { seed.value = null }

    private fun launchJob(block: suspend () -> ResultRow) {
        running?.cancel()
        _job.value = Job.Loading
        running = viewModelScope.launch {
            try {
                val row = block()
                val id = db.results().insert(row); current.value = row.copy(id = id); _job.value = Job.Done(Unit)
            } catch (e: CancellationException) {
                throw e // cancel() / a newer job owns the state now
            } catch (e: SerializationException) {
                _job.value = Job.Failed("The answer came back in an unexpected format. Please try again.")
            } catch (e: Exception) {
                _job.value = Job.Failed(e.message ?: "Failed")
            }
        }
    }

    fun run(t: Tool, inputs: Map<String, String>, image: String?) = launchJob {
        val doc = app.engine.run(ai.value, t, inputs, profile.value, image, prefs.value)
        val summary = t.inputs.mapNotNull { f -> inputs[f.key]?.takeIf { it.isNotBlank() && f.type != "photo" } }.joinToString(" · ").ifBlank { if (image != null) "photo" else "" }
        val stored = t.inputs.filter { it.type != "photo" }.associate { it.key to inputs[it.key].orEmpty() } + (if (image != null) mapOf(PHOTO_FLAG to "yes") else emptyMap())
        ResultRow(toolId = t.id, toolTitle = t.title, emoji = t.emoji, title = doc.title.ifBlank { t.title }, inputSummary = summary.take(140),
            json = json.encodeToString(Doc.serializer(), doc), inputsJson = json.encodeToString(mapSer, stored))
    }

    /** What the user asked for a result, as the user turn of a follow-up conversation. */
    private fun askOf(r: ResultRow): String = if (r.isFollowUp) question(r) else buildString {
        // The full inputs (not the 140-char summary), so follow-ups can refer to anything the user pasted.
        val ins = inputs(r)
        val lines = toolFor(r)?.inputs.orEmpty().mapNotNull { f -> ins[f.key]?.takeIf { it.isNotBlank() }?.let { "${f.label}: $it" } }
        append(r.toolTitle)
        if (lines.isNotEmpty()) append('\n').append(lines.joinToString("\n")) else if (r.inputSummary.isNotBlank()) append(": ").append(r.inputSummary)
        if (hadPhoto(r)) append("\n(a photo was attached to this request)")
    }

    /** The full follow-up question of a follow-up row (the summary is truncated). */
    fun question(r: ResultRow): String = inputs(r)["question"]?.takeIf { it.isNotBlank() } ?: r.inputSummary

    private suspend fun turnsUpTo(r: ResultRow): List<Turn> =
        db.results().threadOnce(r.threadId).filter { it.createdAt < r.createdAt || it.id == r.id }.map { Turn(askOf(it), decode(it)) }

    /** Ask a follow-up about the current result; the answer joins the same thread. */
    fun followUp(question: String) {
        val prev = current.value ?: return
        val q = question.trim().ifBlank { return }
        launchJob {
            val doc = app.engine.followUp(ai.value, turnsUpTo(prev), q, profile.value, prefs.value)
            ResultRow(toolId = prev.toolId, toolTitle = prev.toolTitle, emoji = prev.emoji, title = doc.title.ifBlank { q }, inputSummary = q.take(140),
                json = json.encodeToString(Doc.serializer(), doc), inputsJson = json.encodeToString(mapSer, mapOf("question" to q)), rootId = prev.threadId)
        }
    }

    /** Whether [r] can be regenerated directly (a photo-based result needs its photo re-picked: use [editAndRerun]). */
    fun canRegenerate(r: ResultRow): Boolean = r.isFollowUp || (toolFor(r) != null && !hadPhoto(r) && (r.inputsJson.isNotBlank() || toolFor(r)!!.inputs.isEmpty()))

    /** Runs the current result again: same tool and inputs, or the same follow-up question in its thread. */
    fun regenerate() {
        val r = current.value ?: return
        if (!canRegenerate(r)) { editAndRerun(); return }
        if (r.isFollowUp) launchJob {
            val before = db.results().threadOnce(r.threadId).filter { it.createdAt < r.createdAt && it.id != r.id }
            val doc = app.engine.followUp(ai.value, before.map { Turn(askOf(it), decode(it)) }, question(r), profile.value, prefs.value)
            r.copy(id = 0, title = doc.title.ifBlank { question(r) }, json = json.encodeToString(Doc.serializer(), doc), ticks = "", favorite = false, note = "", createdAt = System.currentTimeMillis())
        } else toolFor(r)?.let { run(it, inputs(r), null) }
    }

    /** Re-opens the tool form pre-filled with the current result's inputs. */
    fun editAndRerun() {
        val r = current.value ?: return
        val t = toolFor(r) ?: return
        openTool(t, FormSeed(inputs(r)))
    }

    fun cancel() { running?.cancel(); running = null; _job.value = Job.Idle }

    fun toggleTick(r: ResultRow, key: String) = viewModelScope.launch {
        val t = ticks(r).toMutableSet(); if (!t.add(key)) t.remove(key)
        val u = r.copy(ticks = t.joinToString(",")); db.results().update(u); if (current.value?.id == r.id) current.value = u
    }
    fun toggleFavorite(r: ResultRow) = viewModelScope.launch { val u = r.copy(favorite = !r.favorite); db.results().update(u); if (current.value?.id == r.id) current.value = u }
    fun rename(r: ResultRow, title: String) = viewModelScope.launch { val u = r.copy(title = title.trim().ifBlank { r.title }); db.results().update(u); if (current.value?.id == r.id) current.value = u }
    fun setNote(r: ResultRow, note: String) = viewModelScope.launch { val u = r.copy(note = note.trim()); db.results().update(u); if (current.value?.id == r.id) current.value = u }
    fun delete(r: ResultRow) = viewModelScope.launch { lastDeleted = r; db.results().delete(r.id); if (current.value?.id == r.id) current.value = null }
    /** Restores the most recently deleted result. Returns false when there is nothing to undo. */
    fun undoDelete(): Boolean { val r = lastDeleted ?: return false; lastDeleted = null; viewModelScope.launch { db.results().restore(r) }; return true }
    fun deleteAll() = viewModelScope.launch { db.results().deleteAll(); current.value = null; lastDeleted = null }
    fun clearJob() { _job.value = Job.Idle }

    /* ───────── sharing in & shortcuts ───────── */
    fun receive(i: Incoming?) { incoming.value = i?.takeIf { it.text.isNotBlank() || it.photo != null } }
    /** Opens [t] with the shared content placed in its first text-like field (and photo field). */
    fun useIncoming(t: Tool) {
        val i = incoming.value ?: return
        incoming.value = null
        val target = t.inputs.firstOrNull { it.type == "longtext" } ?: t.inputs.firstOrNull { it.type == "text" }
        openTool(t, FormSeed(if (target != null && i.text.isNotBlank()) mapOf(target.key to i.text) else emptyMap(), i.photo.takeIf { t.inputs.any { f -> f.type == "photo" } }))
    }
    fun dismissIncoming() { incoming.value = null }

    /* ───────── backup & export ───────── */
    suspend fun backupJson(): String = withContext(Dispatchers.IO) {
        Backups.encode(Backup(appId = spec.id, appName = spec.name, exportedAt = System.currentTimeMillis(), profile = profile.value, prefs = prefs.value,
            results = db.results().allOnce().map(Backups::fromRow)))
    }

    /** Writes a backup to [uri]; returns the number of results saved. */
    suspend fun exportBackup(uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = backupJson()
        app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: error("Couldn't write the file.")
        Backups.decode(text).results.size
    }

    /** Imports a backup from [uri], merging results (duplicates skipped) and restoring profile + preferences. Returns results added. */
    suspend fun importBackup(uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = app.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf); if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > MAX_BACKUP_BYTES) throw IllegalArgumentException("That file is too big to be a backup from this app.")
            }
            out.toByteArray().decodeToString()
        } ?: error("Couldn't read the file.")
        val b = Backups.decode(text)
        if (b.appId != spec.id) throw IllegalArgumentException("That backup is from ${b.appName.ifBlank { b.appId }}, not ${spec.name}.")
        val existing = db.results().allOnce().associate { Backups.identity(it.createdAt, it.toolId, it.json) to it.id }
        // Results already on this device keep their id, so imported follow-ups still join their thread.
        val ids = HashMap<Long, Long>()
        b.results.forEach { r -> existing[Backups.identity(r.createdAt, r.toolId, r.json)]?.let { ids[r.id] = it } }
        var added = 0
        Backups.importOrder(b.results, existing.keys).forEach { r -> ids[r.id] = db.results().insert(Backups.link(r, ids)); added++ }
        if (b.profile.isNotEmpty()) app.store.set("profile", mapSer, profile.value + b.profile)
        savePrefs(b.prefs)
        app.store.set("onboarded", Boolean.serializer(), true)
        added
    }

    /** Writes [text] to a user-picked document (Markdown export etc.). */
    suspend fun writeText(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: error("Couldn't write the file.")
        Unit
    }
}
