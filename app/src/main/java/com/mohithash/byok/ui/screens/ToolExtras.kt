package com.mohithash.byok.ui.screens

import com.mohithash.byok.engine.Field

/** The pure rules behind tool forms (no Android types, so they're unit-tested on the JVM). */
internal object ToolFormLogic {
    private val settingsHint = Regex("""api[\s_-]?key|settings|\bkey\b""", RegexOption.IGNORE_CASE)

    /** Each field's default value. */
    fun defaults(fields: List<Field>): Map<String, String> = fields.associate { it.key to it.default }

    /** Field defaults with [seed] values laid over them. Only keys the form has are kept; values are made valid for their field. */
    fun seeded(fields: List<Field>, seed: Map<String, String>?): Map<String, String> =
        fields.associate { f -> f.key to (seed?.get(f.key)?.let { normalize(f, it) } ?: f.default) }

    /** A value made valid for [f]: chips limited to its options, numbers cleaned, toggles yes/blank. Blank stays blank. */
    fun normalize(f: Field, v: String): String = when {
        v.isBlank() -> ""
        f.type == "chips" && f.options.isNotEmpty() -> if (f.multi) chipSelection(f, v).filter { it in f.options }.joinToString(", ") else if (v in f.options) v else f.default
        f.type == "number" -> sanitizeNumber(v)
        f.type == "toggle" -> if (v == "yes") "yes" else ""
        else -> v
    }

    /** Whether [f] counts as filled in. */
    fun filled(f: Field, values: Map<String, String>, hasPhoto: Boolean): Boolean =
        if (f.type == "photo") hasPhoto else values[f.key].orEmpty().isNotBlank()

    /** Required fields still empty. */
    fun missing(fields: List<Field>, values: Map<String, String>, hasPhoto: Boolean): List<Field> =
        fields.filter { it.required && !filled(it, values, hasPhoto) }

    /** Ready to generate: every required field filled, and at least one field filled (a tool without fields is always ready). */
    fun ready(fields: List<Field>, values: Map<String, String>, hasPhoto: Boolean): Boolean =
        missing(fields, values, hasPhoto).isEmpty() && (fields.isEmpty() || fields.any { filled(it, values, hasPhoto) })

    /** Whether the form differs from a fresh one (so "Clear" has something to do). */
    fun isDirty(fields: List<Field>, values: Map<String, String>, hasPhoto: Boolean): Boolean =
        hasPhoto || fields.any { f -> f.type != "photo" && values[f.key].orEmpty() != f.default }

    /** Digits, at most one decimal separator ('.' or ','), and an optional leading minus. */
    fun sanitizeNumber(raw: String): String {
        // With both '.' and ',' present, the first one is a thousands separator ("1,250.50", "1.250,5"): drop it.
        val grouping = if ('.' in raw && ',' in raw) raw.first { it == '.' || it == ',' } else null
        val src = if (grouping == null) raw else raw.filter { it != grouping }
        val out = StringBuilder()
        var separator = false
        src.forEach { c ->
            when {
                c.isDigit() -> out.append(c)
                (c == '.' || c == ',') && !separator -> { separator = true; out.append(c) }
                (c == '-' || c == '−') && out.isEmpty() -> out.append('-')
            }
        }
        return out.toString()
    }

    /** [heard] (dictated text) added to the end of [current], separated by a space when needed. */
    fun appendSpoken(current: String, heard: String): String {
        val h = heard.trim()
        return when {
            h.isEmpty() -> current
            current.isBlank() -> h
            current.last().isWhitespace() -> current + h
            else -> "$current $h"
        }
    }

    /** Label shown for a field: required ones end in " *". */
    fun label(f: Field): String = if (f.required) "${f.label} *" else f.label

    /**
     * The chips selected in [value]. Multi-select values are joined with ", ", and an option may itself contain ", "
     * ("Yes, a smaller amount"), so known options are matched first.
     */
    fun chipSelection(f: Field, value: String): List<String> {
        if (value.isBlank()) return emptyList()
        if (!f.multi) return listOf(value)
        val parts = value.split(", ")
        val out = mutableListOf<String>()
        var i = 0
        while (i < parts.size) {
            var take = 1
            for (j in parts.size downTo i + 2) if (parts.subList(i, j).joinToString(", ") in f.options) { take = j - i; break }
            out += parts.subList(i, i + take).joinToString(", ")
            i += take
        }
        return out.filter { it.isNotBlank() }.distinct()
    }

    /** The value after tapping chip [option]: multi-select toggles it (kept in option order), single-select picks it or clears it. */
    fun toggleChip(f: Field, value: String, option: String): String {
        if (!f.multi) return if (value == option) "" else option
        val selected = chipSelection(f, value)
        val next = if (option in selected) selected - option else selected + option
        return (f.options.filter { it in next } + next.filter { it !in f.options }).joinToString(", ")
    }

    /** Whether an error message is about the API key or other settings, so the screen offers a Settings shortcut. */
    fun pointsToSettings(message: String): Boolean = settingsHint.containsMatchIn(message)
}
