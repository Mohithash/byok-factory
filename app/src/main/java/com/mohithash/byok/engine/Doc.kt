package com.mohithash.byok.engine

import com.mohithash.byok.ai.Schema
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** The one output shape every tool returns; the renderer knows how to draw each section kind. */
@Serializable data class Card(val title: String, val body: String = "", val meta: String = "")
@Serializable data class KV(val k: String, val v: String)
@Serializable
data class Section(
    /** text | bullets | steps | checklist | cards | table | kv | callout | quote */
    val kind: String = "text",
    val heading: String = "",
    val text: String = "",
    val items: List<String> = emptyList(),
    val cards: List<Card> = emptyList(),
    val rows: List<List<String>> = emptyList(),
    val kv: List<KV> = emptyList(),
)
@Serializable
data class Doc(
    val title: String = "",
    val summary: String = "",
    val sections: List<Section> = emptyList(),
    val tags: List<String> = emptyList(),
    val followups: List<String> = emptyList(),
)

object DocSchema {
    val schema: JsonObject = Schema.obj(
        "title" to Schema.str, "summary" to Schema.str,
        "sections" to Schema.arr(Schema.obj(
            "kind" to Schema.enum("text", "bullets", "steps", "checklist", "cards", "table", "kv", "callout", "quote"),
            "heading" to Schema.str, "text" to Schema.str, "items" to Schema.arr(Schema.str),
            "cards" to Schema.arr(Schema.obj("title" to Schema.str, "body" to Schema.str, "meta" to Schema.str)),
            "rows" to Schema.arr(Schema.arr(Schema.str)),
            "kv" to Schema.arr(Schema.obj("k" to Schema.str, "v" to Schema.str)),
        )),
        "tags" to Schema.arr(Schema.str), "followups" to Schema.arr(Schema.str),
    )
    const val GUIDE = """Respond as a structured document. title: short. summary: 1-2 sentences. sections: 2-6, each with ONE kind and a heading:
- text: prose in `text`.  - bullets: `items`.  - steps: ordered actions in `items`.  - checklist: tickable `items`.
- cards: `cards` with title/body/meta (meta = small label like time, cost, level).  - table: `rows`, first row = header, max 5 columns.
- kv: `kv` pairs for facts/specs.  - callout: one important note in `text`.  - quote: an example phrase/script in `text`.
Only fill the fields relevant to the kind; leave others empty. tags: 2-4 short. followups: 2-3 natural next questions the user might ask, phrased in first person."""
}
