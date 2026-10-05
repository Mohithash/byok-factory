package com.mohithash.byok.engine

import kotlinx.serialization.Serializable

/** User preferences that shape the look of the app and every answer. Never contains the API key. */
@Serializable
data class Prefs(
    /** system | light | dark */
    val theme: String = "system",
    /** Use the wallpaper-based Material You palette instead of the app's brand colours (Android 12+). */
    val dynamicColor: Boolean = false,
    /** Language every answer is written in; blank means English. */
    val language: String = "",
    /** brief | standard | detailed */
    val detail: String = "standard",
    /** Standing instructions appended to every request ("I'm in the UK", "metric units"…). */
    val instructions: String = "",
) {
    companion object {
        const val MAX_INSTRUCTIONS = 1000
        val THEMES = listOf("system", "light", "dark")
        val DETAILS = listOf("brief", "standard", "detailed")
        val LANGUAGES = listOf(
            "English", "Spanish", "French", "German", "Portuguese", "Italian", "Dutch", "Polish", "Turkish", "Russian", "Ukrainian",
            "Arabic", "Hebrew", "Persian", "Hindi", "Bengali", "Urdu", "Punjabi", "Marathi", "Gujarati", "Tamil", "Telugu", "Kannada",
            "Malayalam", "Indonesian", "Malay", "Filipino", "Vietnamese", "Thai", "Japanese", "Korean", "Chinese (Simplified)",
            "Chinese (Traditional)", "Swahili", "Greek", "Swedish", "Norwegian", "Danish", "Finnish", "Czech", "Romanian", "Hungarian",
        )
    }
}

/** Running tally of requests and tokens, so BYOK users can see what they're spending. */
@Serializable
data class UsageStats(
    val requests: Int = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    /** Epoch millis the tally started (0 = never used). */
    val since: Long = 0,
) {
    fun plus(input: Long, output: Long, now: Long): UsageStats =
        copy(requests = requests + 1, inputTokens = inputTokens + input, outputTokens = outputTokens + output, since = if (since == 0L) now else since)
}
