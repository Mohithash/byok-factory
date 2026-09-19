package com.mohithash.byok.engine

import com.mohithash.byok.ai.AiClient
import com.mohithash.byok.ai.AiSettings
import com.mohithash.byok.ai.ChatMsg
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class Engine(private val client: AiClient, private val spec: AppSpec) {

    private fun profileSummary(profile: Map<String, String>): String =
        spec.profile.mapNotNull { f -> profile[f.key]?.takeIf { it.isNotBlank() }?.let { "${f.label}: $it" } }.joinToString("; ").ifBlank { "not provided" }

    fun system(profile: Map<String, String>) = """${spec.persona}
        |Today is ${LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy"))}. User profile — ${profileSummary(profile)}.
        |${DocSchema.GUIDE}
        |Be specific and practical; never pad; if the request is unsafe or outside your remit say so briefly in a callout.${if (spec.disclaimer.isNotBlank()) " Always end with a callout: ${spec.disclaimer}" else ""}""".trimMargin()

    fun render(tool: Tool, inputs: Map<String, String>, profile: Map<String, String>): String {
        var p = tool.prompt.replace("{profile}", profileSummary(profile))
        // Inputs the prompt template forgot to reference are appended, so nothing the user typed is lost.
        val orphan = tool.inputs.filter { f -> f.type != "photo" && !tool.prompt.contains("{${f.key}}") && inputs[f.key].orEmpty().isNotBlank() }
        tool.inputs.forEach { f -> p = p.replace("{${f.key}}", inputs[f.key].orEmpty().ifBlank { "(not given)" }) }
        if (orphan.isNotEmpty()) p += "\n\n" + orphan.joinToString("\n") { f -> "${f.label}: ${inputs[f.key]}" }
        return p + if (tool.shape.isNotBlank()) "\n\nPreferred sections: ${tool.shape}" else ""
    }

    suspend fun run(ai: AiSettings, tool: Tool, inputs: Map<String, String>, profile: Map<String, String>, image: String?): Doc {
        val raw = client.chat(ai, system(profile), listOf(ChatMsg("user", render(tool, inputs, profile), image)), DocSchema.schema, 6000)
        return client.json.decodeFromString(Doc.serializer(), client.extractJson(raw))
    }

    /** Follow-up question about a previous result: keeps the doc as context and returns another doc. */
    suspend fun followUp(ai: AiSettings, previous: Doc, question: String, profile: Map<String, String>): Doc {
        val ctx = "Previous result titled \"${previous.title}\": ${previous.summary}\n" + previous.sections.joinToString("\n") { s ->
            s.heading + ": " + (s.text.ifBlank { (s.items + s.cards.map { "${it.title} — ${it.body}" } + s.kv.map { "${it.k}: ${it.v}" }).joinToString("; ") })
        }
        val raw = client.chat(ai, system(profile), listOf(ChatMsg("user", ctx.take(6000)), ChatMsg("assistant", "Understood. What would you like next?"), ChatMsg("user", question)), DocSchema.schema, 5000)
        return client.json.decodeFromString(Doc.serializer(), client.extractJson(raw))
    }
}
