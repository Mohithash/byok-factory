package com.mohithash.byok.engine

/** Text renderings of a [Doc] for copy, share, Markdown export, PDF/print and model context. */

fun Doc.toPlainText(ticks: Set<String> = emptySet()): String = buildString {
    appendLine(title); if (summary.isNotBlank()) appendLine(summary); appendLine()
    sections.forEachIndexed { si, s ->
        if (s.heading.isNotBlank()) appendLine(s.heading.uppercase())
        if (s.text.isNotBlank()) appendLine(s.text)
        s.items.forEachIndexed { i, it ->
            appendLine(when (s.kind) {
                "steps" -> "${i + 1}. $it"
                "checklist" -> (if ("$si:$i" in ticks) "☑ " else "☐ ") + it
                else -> "• $it"
            })
        }
        s.cards.forEach { appendLine("${it.title}${if (it.meta.isNotBlank()) " (${it.meta})" else ""}: ${it.body}") }
        s.rows.forEach { appendLine(it.joinToString(" | ")) }
        s.kv.forEach { appendLine("${it.k}: ${it.v}") }
        appendLine()
    }
    if (tags.isNotEmpty()) appendLine(tags.joinToString(" ") { "#" + it.replace(" ", "") })
}.trimEnd() + "\n"

private fun String.mdCell() = replace("|", "\\|").replace("\n", " ")

fun Doc.toMarkdown(ticks: Set<String> = emptySet()): String = buildString {
    appendLine("# $title"); appendLine()
    if (summary.isNotBlank()) { appendLine(summary); appendLine() }
    sections.forEachIndexed { si, s ->
        if (s.heading.isNotBlank()) { appendLine("## ${s.heading}"); appendLine() }
        when (s.kind) {
            "callout" -> { (s.text.ifBlank { s.items.joinToString(" ") }).lines().forEach { appendLine("> **Note:** $it".replace("**Note:** ", if (it === s.text.lines().first()) "**Note:** " else "")) }; appendLine() }
            "quote" -> { (s.text.ifBlank { s.items.joinToString(" ") }).lines().forEach { appendLine("> $it") }; appendLine() }
            "table" -> if (s.rows.isNotEmpty()) {
                val cols = s.rows.maxOf { it.size }
                fun row(r: List<String>) = "| " + (0 until cols).joinToString(" | ") { r.getOrElse(it) { "" }.mdCell() } + " |"
                appendLine(row(s.rows.first())); appendLine("|" + " --- |".repeat(cols))
                s.rows.drop(1).forEach { appendLine(row(it)) }; appendLine()
            }
            else -> {
                if (s.text.isNotBlank()) { appendLine(s.text); appendLine() }
                if (s.items.isNotEmpty()) {
                    s.items.forEachIndexed { i, it ->
                        appendLine(when (s.kind) { "steps" -> "${i + 1}. $it"; "checklist" -> (if ("$si:$i" in ticks) "- [x] " else "- [ ] ") + it; else -> "- $it" })
                    }
                    appendLine()
                }
                s.cards.forEach { c -> appendLine("- **${c.title}**${if (c.meta.isNotBlank()) " · _${c.meta}_" else ""}${if (c.body.isNotBlank()) " — ${c.body}" else ""}") }
                if (s.cards.isNotEmpty()) appendLine()
                s.kv.forEach { appendLine("- **${it.k}:** ${it.v}") }
                if (s.kv.isNotEmpty()) appendLine()
            }
        }
    }
    if (tags.isNotEmpty()) { appendLine(tags.joinToString(" ") { "`$it`" }); appendLine() }
}.trimEnd() + "\n"

private fun String.html() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/** A self-contained, printable HTML page (used for Save as PDF). [accent] is a #RRGGBB colour. */
fun Doc.toHtml(appName: String, accent: String = "#334455", ticks: Set<String> = emptySet()): String = buildString {
    append("""<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${title.html()}</title><style>
body{font:15px/1.55 -apple-system,Roboto,"Segoe UI",sans-serif;color:#1b1b1b;margin:32px;max-width:760px}
h1{font-size:26px;margin:0 0 6px;color:$accent}h2{font-size:17px;margin:22px 0 8px;color:$accent}
.sum{font-size:16px;color:#444;margin:0 0 12px}.app{font-size:12px;color:#777;text-transform:uppercase;letter-spacing:.08em}
.note{border-left:4px solid $accent;background:#f4f4f4;padding:10px 14px;border-radius:6px}
blockquote{font-style:italic;border-left:4px solid #ccc;margin:0;padding:6px 14px;color:#333}
table{border-collapse:collapse;width:100%}th,td{border:1px solid #ddd;padding:6px 8px;text-align:left;vertical-align:top}th{background:#f0f0f0}
.card{border:1px solid #ddd;border-radius:10px;padding:10px 12px;margin:8px 0}.meta{float:right;color:$accent;font-size:13px}
ul.check{list-style:none;padding-left:4px}.tags{color:#777;font-size:13px;margin-top:24px}
</style></head><body>""")
    append("<div class=\"app\">${appName.html()}</div><h1>${title.html()}</h1>")
    if (summary.isNotBlank()) append("<p class=\"sum\">${summary.html()}</p>")
    sections.forEachIndexed { si, s ->
        if (s.heading.isNotBlank()) append("<h2>${s.heading.html()}</h2>")
        when (s.kind) {
            "callout" -> append("<div class=\"note\">${s.text.ifBlank { s.items.joinToString(" ") }.html()}</div>")
            "quote" -> append("<blockquote>${s.text.ifBlank { s.items.joinToString(" ") }.html()}</blockquote>")
            "steps" -> append("<ol>" + s.items.joinToString("") { "<li>${it.html()}</li>" } + "</ol>")
            "checklist" -> append("<ul class=\"check\">" + s.items.mapIndexed { i, it -> "<li>${if ("$si:$i" in ticks) "☑" else "☐"} ${it.html()}</li>" }.joinToString("") + "</ul>")
            "table" -> if (s.rows.isNotEmpty()) append("<table><tr>" + s.rows.first().joinToString("") { "<th>${it.html()}</th>" } + "</tr>" +
                s.rows.drop(1).joinToString("") { r -> "<tr>" + r.joinToString("") { "<td>${it.html()}</td>" } + "</tr>" } + "</table>")
            "kv" -> append("<table>" + s.kv.joinToString("") { "<tr><th>${it.k.html()}</th><td>${it.v.html()}</td></tr>" } + "</table>")
            "cards" -> s.cards.forEach { c -> append("<div class=\"card\">${if (c.meta.isNotBlank()) "<span class=\"meta\">${c.meta.html()}</span>" else ""}<strong>${c.title.html()}</strong>${if (c.body.isNotBlank()) "<br>${c.body.html()}" else ""}</div>") }
            else -> {
                if (s.text.isNotBlank()) s.text.split("\n\n").forEach { append("<p>${it.html().replace("\n", "<br>")}</p>") }
                if (s.items.isNotEmpty()) append("<ul>" + s.items.joinToString("") { "<li>${it.html()}</li>" } + "</ul>")
            }
        }
        // Content that arrived in a field the kind doesn't normally use is still printed.
        if (s.kind != "cards" && s.cards.isNotEmpty()) s.cards.forEach { c -> append("<div class=\"card\"><strong>${c.title.html()}</strong> ${c.body.html()}</div>") }
        if (s.kind != "kv" && s.kv.isNotEmpty()) append("<table>" + s.kv.joinToString("") { "<tr><th>${it.k.html()}</th><td>${it.v.html()}</td></tr>" } + "</table>")
    }
    if (tags.isNotEmpty()) append("<div class=\"tags\">" + tags.joinToString(" · ") { it.html() } + "</div>")
    append("</body></html>")
}

/** Compact rendering used as the assistant turn when a follow-up keeps a result as context. */
fun Doc.asContext(limit: Int = 4000): String = toPlainText().take(limit)

/** Words for text-to-speech: headings and content without bullets or table pipes. */
fun Doc.toSpeech(): String = buildString {
    append(title).append(". "); if (summary.isNotBlank()) append(summary).append(" ")
    sections.forEach { s ->
        if (s.heading.isNotBlank()) append(s.heading).append(". ")
        if (s.text.isNotBlank()) append(s.text).append(" ")
        s.items.forEachIndexed { i, it -> if (s.kind == "steps") append("Step ${i + 1}. ") ; append(it.trimEnd('.')).append(". ") }
        s.cards.forEach { append(it.title).append(". ").append(it.body).append(" ") }
        s.rows.drop(1).forEach { r -> append(r.joinToString(", ")).append(". ") }
        s.kv.forEach { append(it.k).append(": ").append(it.v).append(". ") }
    }
}.replace(Regex("\\s+"), " ").trim()

/** Every searchable word in a doc, lower-cased (history search looks inside results, not just titles). */
fun Doc.searchText(): String = toPlainText().lowercase()

/** Checklist progress: (done, total) across all checklist sections. */
fun Doc.checklistProgress(ticks: Set<String>): Pair<Int, Int> {
    var done = 0; var total = 0
    sections.forEachIndexed { si, s -> if (s.kind == "checklist") s.items.indices.forEach { i -> total++; if ("$si:$i" in ticks) done++ } }
    return done to total
}
