package com.mohithash.byok.ui

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.lang.ref.WeakReference
import java.util.Locale

/**
 * Read-aloud for long text, on top of [TextToSpeech].
 *
 * The engine is bound lazily on the first [speak] (so showing a result costs nothing), speaks in the device language
 * when the engine has it, and splits text into chunks the engine accepts, breaking after sentences. [speaking] and
 * [available] are Compose state and are only written on the main thread. Only one speaker talks at a time: starting
 * one stops the others. Call [shutdown] when done (see [rememberDocSpeaker]).
 *
 * Self-contained (Android + Compose runtime only) so it can be copied into other apps unchanged.
 */
class DocSpeaker(context: Context) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var released = false
    private var pending: String? = null
    private var pendingError: ((String) -> Unit)? = null
    private var session = 0
    private var lastUtterance: String? = null

    /** null until the engine has been tried, then whether text-to-speech works on this device. */
    var available by mutableStateOf<Boolean?>(null)
        private set

    /** True from [speak] until the last chunk has been spoken, [stop] is called or the engine fails. */
    var speaking by mutableStateOf(false)
        private set

    /** Speaks [text], replacing anything this speaker was saying. [onError] gets a user-facing message if it can't. */
    fun speak(text: String, onError: (String) -> Unit = {}, locale: Locale? = null) {
        if (released) return
        val clean = text.trim()
        if (clean.isEmpty()) return
        if (available == false) { onError(UNAVAILABLE); return }
        claim()
        speaking = true
        wanted = locale
        if (!ready) { pending = clean; pendingError = onError; bind(); return }
        play(clean, onError)
    }

    /** Voice language asked for by the latest [speak] (null = the phone's language). */
    private var wanted: Locale? = null

    /** Stops speaking (safe to call at any time, also after [shutdown]). */
    fun stop() {
        pending = null; pendingError = null
        session++; lastUtterance = null
        if (ready) runCatching { tts?.stop() }
        speaking = false
    }

    /** Stops and releases the engine; this speaker can't be used afterwards. */
    fun shutdown() {
        if (released) return
        released = true
        stop()
        main.removeCallbacksAndMessages(null)
        runCatching { tts?.shutdown() }
        tts = null; ready = false
        if (active?.get() === this) active = null
    }

    private fun claim() {
        active?.get()?.takeIf { it !== this }?.stop()
        active = WeakReference(this)
    }

    private fun bind() {
        if (tts != null) return
        // The listener can fire inside the constructor (no engine installed), so always hop to the next main-loop turn.
        tts = try { TextToSpeech(app) { status -> main.post { onInit(status) } } } catch (e: Exception) { null }
        if (tts == null) main.post { onInit(TextToSpeech.ERROR) }
    }

    private fun onInit(status: Int) {
        if (released) return
        val t = tts
        if (status != TextToSpeech.SUCCESS || t == null) {
            runCatching { t?.shutdown() }
            tts = null; available = false; speaking = false
            val err = pendingError
            pending = null; pendingError = null
            err?.invoke(UNAVAILABLE)
            return
        }
        runCatching {
            val locale = Locale.getDefault()
            if (t.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) t.language = locale
        }
        runCatching {
            t.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        }
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { main.post { if (utteranceId != null && utteranceId == lastUtterance) ended() } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { main.post { if (ownsUtterance(utteranceId)) ended() } }
            override fun onStop(utteranceId: String?, interrupted: Boolean) { main.post { if (ownsUtterance(utteranceId)) ended() } }
        })
        ready = true; available = true
        val text = pending
        val err = pendingError ?: {}
        pending = null; pendingError = null
        if (text != null) play(text, err)
    }

    private fun ownsUtterance(id: String?) = id != null && id.startsWith("doc-$session-")
    private fun ended() { speaking = false; lastUtterance = null }

    private fun play(text: String, onError: (String) -> Unit) {
        val t = tts ?: return
        // Read in the language the answer is written in when a voice for it is installed.
        runCatching {
            val want = wanted ?: Locale.getDefault()
            if (t.isLanguageAvailable(want) >= TextToSpeech.LANG_AVAILABLE) t.language = want
            else wanted?.let { onError("No ${it.getDisplayLanguage(Locale.ENGLISH)} voice is installed — add one in your phone's text-to-speech settings.") }
        }
        val max = runCatching { TextToSpeech.getMaxSpeechInputLength() }.getOrDefault(4000) - 1
        val parts = chunks(text, max.coerceAtLeast(200))
        if (parts.isEmpty()) { speaking = false; return }
        session++
        val ids = parts.indices.map { "doc-$session-$it" }
        lastUtterance = ids.last()
        speaking = true
        parts.forEachIndexed { i, p ->
            val r = runCatching { t.speak(p, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, ids[i]) }.getOrDefault(TextToSpeech.ERROR)
            if (r != TextToSpeech.SUCCESS) {
                if (i == 0) { ended(); onError("Couldn't start reading aloud. Please try again.") } else lastUtterance = ids[i - 1]
                return
            }
        }
    }

    companion object {
        private const val UNAVAILABLE = "Read aloud isn't available. Install or turn on a text-to-speech engine in your phone's settings."
        private var active: WeakReference<DocSpeaker>? = null
        private val SENTENCE_BREAK = Regex("(?<=[.!?…。！？;:])\\s+|\\s*\\n+\\s*")

        /** Splits [text] into pieces of at most [max] chars: whole sentences where possible, then words, then hard cuts. */
        fun chunks(text: String, max: Int): List<String> {
            require(max > 1) { "max must be > 1" }
            val out = ArrayList<String>()
            val cur = StringBuilder()
            fun flush() { if (cur.isNotBlank()) out += cur.toString().trim(); cur.setLength(0) }
            text.split(SENTENCE_BREAK).map { it.trim() }.filter { it.isNotEmpty() }.forEach { sentence ->
                var s = sentence
                if (s.length > max) flush()
                while (s.length > max) {
                    var cut = s.lastIndexOf(' ', max).takeIf { it > 0 } ?: max
                    if (cut == max && s[cut - 1].isHighSurrogate()) cut--
                    out += s.substring(0, cut).trim()
                    s = s.substring(cut).trim()
                }
                if (s.isEmpty()) return@forEach
                if (cur.isNotEmpty() && cur.length + 1 + s.length > max) flush()
                if (cur.isNotEmpty()) cur.append(' ')
                cur.append(s)
            }
            flush()
            return out.filter { it.isNotEmpty() }
        }
    }
}

/** A [DocSpeaker] tied to the composition: it stops and releases its engine when this leaves the screen. */
@Composable
fun rememberDocSpeaker(): DocSpeaker {
    val context = LocalContext.current
    val speaker = remember { DocSpeaker(context) }
    DisposableEffect(speaker) { onDispose { speaker.shutdown() } }
    return speaker
}

/** Text-to-speech locale for an answer-language preference ([com.mohithash.byok.engine.Prefs.LANGUAGES]); null = the phone's language. */
fun ttsLocale(language: String): Locale? {
    val tag = when (language.trim()) {
        "", "English" -> return null
        "Spanish" -> "es"; "French" -> "fr"; "German" -> "de"; "Portuguese" -> "pt"; "Italian" -> "it"; "Dutch" -> "nl"; "Polish" -> "pl"
        "Turkish" -> "tr"; "Russian" -> "ru"; "Ukrainian" -> "uk"; "Arabic" -> "ar"; "Hebrew" -> "he"; "Persian" -> "fa"; "Hindi" -> "hi"
        "Bengali" -> "bn"; "Urdu" -> "ur"; "Punjabi" -> "pa"; "Marathi" -> "mr"; "Gujarati" -> "gu"; "Tamil" -> "ta"; "Telugu" -> "te"
        "Kannada" -> "kn"; "Malayalam" -> "ml"; "Indonesian" -> "id"; "Malay" -> "ms"; "Filipino" -> "fil"; "Vietnamese" -> "vi"; "Thai" -> "th"
        "Japanese" -> "ja"; "Korean" -> "ko"; "Chinese (Simplified)" -> "zh-CN"; "Chinese (Traditional)" -> "zh-TW"; "Swahili" -> "sw"
        "Greek" -> "el"; "Swedish" -> "sv"; "Norwegian" -> "nb"; "Danish" -> "da"; "Finnish" -> "fi"; "Czech" -> "cs"; "Romanian" -> "ro"
        "Hungarian" -> "hu"
        else -> return null
    }
    return Locale.forLanguageTag(tag)
}
