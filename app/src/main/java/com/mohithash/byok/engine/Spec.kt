package com.mohithash.byok.engine

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A user-profile or tool-input field. type: text | longtext | number | chips | photo | toggle */
@Serializable
data class Field(
    val key: String,
    val label: String,
    val type: String = "text",
    val placeholder: String = "",
    val options: List<String> = emptyList(),
    val default: String = "",
    val required: Boolean = false,
    val multi: Boolean = false,
)

@Serializable
data class Tool(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val emoji: String = "✨",
    val inputs: List<Field> = emptyList(),
    /** Instruction to the model. `{key}` is replaced by the input value, `{profile}` by the profile summary. */
    val prompt: String,
    /** Suggested section kinds, purely advisory for the model. */
    val shape: String = "",
    val button: String = "Generate",
    val loading: String = "Working…",
)

@Serializable
data class Onboarding(val title: String, val subtitle: String, val bullets: List<String> = emptyList())

@Serializable
data class AppSpec(
    val id: String,
    val name: String,
    val tagline: String,
    val category: String,
    val colors: List<String>,
    val icon: String = "spark",
    val about: String = "",
    val persona: String,
    val onboarding: Onboarding,
    val profile: List<Field> = emptyList(),
    val tools: List<Tool>,
    val disclaimer: String = "",
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun load(ctx: Context): AppSpec = json.decodeFromString(serializer(), ctx.assets.open("app.json").bufferedReader().readText())
    }
}
