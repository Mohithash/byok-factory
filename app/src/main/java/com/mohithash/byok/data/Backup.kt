package com.mohithash.byok.data

import com.mohithash.byok.engine.Prefs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One saved result in a backup file. [id]/[rootId] are the ids on the exporting device (threads are re-linked on import). */
@Serializable
data class BackupResult(
    val id: Long = 0,
    val toolId: String,
    val toolTitle: String,
    val emoji: String = "✨",
    val title: String,
    val inputSummary: String = "",
    val json: String,
    val ticks: String = "",
    val favorite: Boolean = false,
    val createdAt: Long = 0,
    val inputsJson: String = "",
    val rootId: Long = 0,
    val note: String = "",
)

/** Portable backup of everything except the API key. */
@Serializable
data class Backup(
    val format: String = FORMAT,
    val version: Int = 1,
    val appId: String,
    val appName: String = "",
    val exportedAt: Long = 0,
    val profile: Map<String, String> = emptyMap(),
    val prefs: Prefs = Prefs(),
    val results: List<BackupResult> = emptyList(),
) { companion object { const val FORMAT = "byok-backup" } }

object Backups {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun fromRow(r: ResultRow) = BackupResult(r.id, r.toolId, r.toolTitle, r.emoji, r.title, r.inputSummary, r.json, r.ticks, r.favorite, r.createdAt, r.inputsJson, r.rootId, r.note)

    fun encode(b: Backup): String = json.encodeToString(Backup.serializer(), b)

    /** Parses a backup file; throws [IllegalArgumentException] with a readable message when it isn't one. */
    fun decode(text: String): Backup {
        val b = runCatching { json.decodeFromString(Backup.serializer(), text) }.getOrElse { throw IllegalArgumentException("That file isn't a backup from this app.") }
        require(b.format == Backup.FORMAT) { "That file isn't a backup from this app." }
        return b
    }

    /** Key that identifies the same result across devices, so importing twice doesn't duplicate. Built from fields the user can't edit (renaming must not create a copy). */
    fun identity(createdAt: Long, toolId: String, json: String) = "$createdAt|$toolId|${json.hashCode()}"

    /**
     * Rows to insert for an import, in an order where every thread's first result comes before its follow-ups.
     * Results already present ([existing] identities) are skipped. [newId] maps the backup's ids to the ids
     * the database assigned so far; call [link] on each follow-up just before inserting it.
     */
    fun importOrder(results: List<BackupResult>, existing: Set<String>): List<BackupResult> =
        results.filter { identity(it.createdAt, it.toolId, it.json) !in existing }
            .sortedWith(compareBy<BackupResult> { if (it.rootId == 0L) 0 else 1 }.thenBy { it.createdAt }.thenBy { it.id })

    /** The row to insert for [r], with its thread link translated through [newIds] (old id → new id). Orphans become roots. */
    fun link(r: BackupResult, newIds: Map<Long, Long>): ResultRow = ResultRow(
        toolId = r.toolId, toolTitle = r.toolTitle, emoji = r.emoji, title = r.title, inputSummary = r.inputSummary, json = r.json,
        ticks = r.ticks, favorite = r.favorite, createdAt = if (r.createdAt > 0) r.createdAt else System.currentTimeMillis(),
        inputsJson = r.inputsJson, rootId = if (r.rootId == 0L) 0L else newIds[r.rootId] ?: 0L, note = r.note,
    )
}
