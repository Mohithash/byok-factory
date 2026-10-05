package com.mohithash.byok.engine

import com.mohithash.byok.ai.AiClient
import com.mohithash.byok.ai.AiSettings
import com.mohithash.byok.ai.ChatMsg
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** One earlier exchange in a thread: what was asked and the document that came back. */
data class Turn(val ask: String, val doc: Doc)

class Engine(private val client: AiClient, private val spec: AppSpec) {

    private fun profileSummary(profile: Map<String, String>): String =
        spec.profile.mapNotNull { f -> profile[f.key]?.takeIf { it.isNotBlank() }?.let { "${f.label}: $it" } }.joinToString("; ").ifBlank { "not provided" }

    /** Extra system lines from [Prefs]: answer language, length, and the user's standing instructions. */
    fun prefsLines(prefs: Prefs): String = buildList {
        when (prefs.detail) {
            "brief" -> add("Length: brief — 2-3 sections, short items, no preamble.")
            "detailed" -> add("Length: detailed — up to 8 sections, with more depth, examples and specifics.")
        }
        val lang = prefs.language.trim()
        if (lang.isNotBlank() && !lang.equals("English", true))
            add("Write every user-facing string (title, summary, headings, text, items, cards, table cells, tags, follow-ups) in $lang. Keep JSON keys and section kinds exactly as specified in English.")
        val extra = prefs.instructions.trim().take(Prefs.MAX_INSTRUCTIONS)
        if (extra.isNotBlank()) add("The user's standing instructions (follow them unless unsafe or impossible): $extra")
    }.joinToString("\n")

    fun system(profile: Map<String, String>, prefs: Prefs = Prefs()): String {
        val base = """${spec.persona}
        |Today is ${LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy"))}. User profile — ${profileSummary(profile)}.
        |${DocSchema.GUIDE}
        |Be specific and practical; never pad; if the request is unsafe or outside your remit say so briefly in a callout.${if (spec.disclaimer.isNotBlank()) " Always end with a callout: ${spec.disclaimer}" else ""}""".trimMargin()
        val extra = prefsLines(prefs)
        return if (extra.isBlank()) base else "$base\n$extra"
    }

    /**
     * Fills the tool's prompt template. Photo fields never carry text — the image travels as a separate
     * content block — so `{photo}` (or any photo-typed key) renders as a reference to the attachment, and
     * is never reported as "(not given)". [hasImage] says whether an image is actually attached to this call.
     */
    fun render(tool: Tool, inputs: Map<String, String>, profile: Map<String, String>, hasImage: Boolean = false): String {
        var p = tool.prompt.replace("{profile}", profileSummary(profile))
        val (photoFields, textFields) = tool.inputs.partition { it.type == "photo" }
        // Text inputs the prompt template forgot to reference are appended, so nothing the user typed is lost.
        val orphan = textFields.filter { f -> !tool.prompt.contains("{${f.key}}") && inputs[f.key].orEmpty().isNotBlank() }
        textFields.forEach { f -> p = p.replace("{${f.key}}", inputs[f.key].orEmpty().ifBlank { "(not given)" }) }
        photoFields.forEach { f -> p = p.replace("{${f.key}}", if (hasImage) "the attached photo" else "(no photo attached)") }
        if (orphan.isNotEmpty()) p += "\n\n" + orphan.joinToString("\n") { f -> "${f.label}: ${inputs[f.key]}" }
        return p + if (tool.shape.isNotBlank()) "\n\nPreferred sections: ${tool.shape}" else ""
    }

    fun decode(raw: String): Doc = client.json.decodeFromString(Doc.serializer(), client.extractJson(raw))

    suspend fun run(ai: AiSettings, tool: Tool, inputs: Map<String, String>, profile: Map<String, String>, image: String?, prefs: Prefs = Prefs()): Doc {
        val raw = client.chat(ai, system(profile, prefs), listOf(ChatMsg("user", render(tool, inputs, profile, hasImage = image != null), image)), DocSchema.schema, MAX_TOKENS)
        return decode(raw)
    }

    /**
     * The conversation for a follow-up: each earlier [Turn] becomes a user ask and the assistant's document
     * (as text), then the new [question]. Only the most recent turns are kept so the request stays small.
     */
    fun followUpMessages(thread: List<Turn>, question: String): List<ChatMsg> {
        val kept = thread.takeLast(MAX_TURNS)
        val per = (CONTEXT_CHARS / kept.size.coerceAtLeast(1)).coerceAtLeast(1500)
        return kept.flatMap { t -> listOf(ChatMsg("user", t.ask.ifBlank { "(earlier request)" }.take(2000)), ChatMsg("assistant", t.doc.asContext(per))) } +
            ChatMsg("user", question)
    }

    /** Follow-up question that keeps the earlier results in the thread as context; returns another doc. */
    suspend fun followUp(ai: AiSettings, thread: List<Turn>, question: String, profile: Map<String, String>, prefs: Prefs = Prefs()): Doc {
        val raw = client.chat(ai, system(profile, prefs), followUpMessages(thread, question), DocSchema.schema, MAX_TOKENS)
        return decode(raw)
    }

    companion object {
        const val MAX_TOKENS = 16000
        const val MAX_TURNS = 4
        const val CONTEXT_CHARS = 12000
    }
}
