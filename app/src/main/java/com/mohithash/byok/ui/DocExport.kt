package com.mohithash.byok.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.mohithash.byok.engine.Card
import com.mohithash.byok.engine.Doc
import com.mohithash.byok.engine.KV
import com.mohithash.byok.engine.toHtml
import com.mohithash.byok.engine.toPlainText
import com.mohithash.byok.ui.theme.tone
import java.util.Locale

/* Exports of a result: Markdown-aware plain text, file names, and print / Save as PDF through the system dialog. */

/** Applies [f] to every piece of text in the doc (structure, and so checklist tick keys, stay the same). */
fun Doc.mapDocText(f: (String) -> String): Doc = copy(
    title = f(title), summary = f(summary),
    sections = sections.map { s ->
        s.copy(heading = f(s.heading), text = f(s.text), items = s.items.map(f), cards = s.cards.map { Card(f(it.title), f(it.body), f(it.meta)) },
            rows = s.rows.map { r -> r.map(f) }, kv = s.kv.map { KV(f(it.k), f(it.v)) })
    },
    tags = tags.map(f), followups = followups.map(f),
)

/** The doc with inline Markdown markers removed: for plain-text copy, share and read aloud. */
fun Doc.withoutInlineMarkdown(): Doc = mapDocText(InlineMarkdown::plain)

/** One section as plain text (heading, content, ☑/☐ ticks), for "copy this section". */
fun Doc.sectionPlainText(index: Int, ticks: Set<String>): String {
    val s = sections.getOrNull(index) ?: return ""
    val own = ticks.mapNotNullTo(HashSet()) { k -> k.substringAfter(':').takeIf { k.substringBefore(':') == index.toString() }?.let { "0:$it" } }
    return Doc(sections = listOf(s)).toPlainText(own).trim()
}

/** A file name (without extension) for an exported result: the title minus characters file systems reject. */
fun docExportBaseName(title: String): String =
    InlineMarkdown.plain(title).replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ").replace(Regex("\\s+"), " ").trim().trim('.', ' ')
        .take(60).trim().ifBlank { "result" }

fun docExportFileName(title: String, extension: String): String = docExportBaseName(title) + "." + extension

/** "#RRGGBB" for an ARGB colour int. */
fun docHexColor(argb: Int): String = String.format(Locale.ROOT, "#%06X", argb and 0xFFFFFF)

/** The theme's primary as a print accent: dark themes use a light primary, which would wash out on white paper. */
fun docPrintAccent(primary: Color): String = docHexColor((if (primary.luminance() > 0.35f) primary.tone(0.32f) else primary).toArgb())

private const val B_OPEN = ''; private const val B_CLOSE = ''
private const val I_OPEN = ''; private const val I_CLOSE = ''
private const val S_OPEN = ''; private const val S_CLOSE = ''
private const val C_OPEN = ''; private const val C_CLOSE = ''
private const val A_OPEN = ''; private const val A_HREF_END = ''; private const val A_CLOSE = ''
private val MARKER_TAGS = listOf(
    B_OPEN to "<strong>", B_CLOSE to "</strong>", I_OPEN to "<em>", I_CLOSE to "</em>", S_OPEN to "<s>", S_CLOSE to "</s>",
    C_OPEN to "<code>", C_CLOSE to "</code>", A_OPEN to "<a href=\"", A_HREF_END to "\">", A_CLOSE to "</a>",
)

/**
 * Inline Markdown turned into private-use marker characters that survive [toHtml]'s escaping and are swapped for tags
 * afterwards. Markers already present in the input are dropped so model output can't inject tags.
 */
internal fun htmlMarkers(src: String): String {
    val p = InlineMarkdown.parse(src.filterNot { it in ''..'' })
    if (p.spans.isEmpty()) return p.text
    val spans = p.spans.withIndex().toList()
    val opens = spans.sortedWith(compareBy({ it.value.start }, { -it.value.end }, { it.index }))
    val closes = spans.sortedWith(compareBy({ it.value.end }, { -it.value.start }, { -it.index }))
    val sb = StringBuilder(p.text.length + spans.size * 4)
    var oi = 0; var ci = 0
    for (pos in 0..p.text.length) {
        while (ci < closes.size && closes[ci].value.end == pos) {
            sb.append(when (closes[ci].value.style) {
                InlineMarkdown.Style.Bold -> B_CLOSE; InlineMarkdown.Style.Italic -> I_CLOSE; InlineMarkdown.Style.Strike -> S_CLOSE
                InlineMarkdown.Style.Code -> C_CLOSE; InlineMarkdown.Style.Link -> A_CLOSE
            }); ci++
        }
        while (oi < opens.size && opens[oi].value.start == pos) {
            val sp = opens[oi].value
            when (sp.style) {
                InlineMarkdown.Style.Bold -> sb.append(B_OPEN); InlineMarkdown.Style.Italic -> sb.append(I_OPEN)
                InlineMarkdown.Style.Strike -> sb.append(S_OPEN); InlineMarkdown.Style.Code -> sb.append(C_OPEN)
                InlineMarkdown.Style.Link -> sb.append(A_OPEN).append(sp.url).append(A_HREF_END)
            }
            oi++
        }
        if (pos < p.text.length) sb.append(p.text[pos])
    }
    return sb.toString()
}

/** Printable HTML (see [toHtml]) with inline Markdown rendered as bold / italic / code / links. */
fun Doc.toPrintHtml(appName: String, accentHex: String, ticks: Set<String> = emptySet()): String {
    val marked = mapDocText(::htmlMarkers).copy(title = InlineMarkdown.plain(title.filterNot { it in ''..'' }))
    var html = marked.toHtml(appName, accentHex, ticks)
    MARKER_TAGS.forEach { (ch, tag) -> html = html.replace(ch.toString(), tag) }
    return html.replaceFirst("</style>", "code{font-family:monospace;background:#f1f1f1;padding:0 4px;border-radius:4px}a{color:$accentHex}</style>")
}

/** WebViews kept alive until their print job finishes (the print framework only holds the adapter). */
private val printingWebViews = mutableSetOf<WebView>()

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext?.findActivity()
    else -> null
}

/** Forwards to [inner]; tells [onDone] once the print job is over (printed, saved, cancelled or failed). */
private class ReleasingPrintAdapter(private val inner: PrintDocumentAdapter, private val onDone: () -> Unit) : PrintDocumentAdapter() {
    override fun onStart() = inner.onStart()
    override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes, cancellationSignal: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?) =
        inner.onLayout(oldAttributes, newAttributes, cancellationSignal, callback, extras)
    override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal?, callback: WriteResultCallback) =
        inner.onWrite(pages, destination, cancellationSignal, callback)
    override fun onFinish() { try { inner.onFinish() } finally { onDone() } }
}

private fun releasePrintWebView(web: WebView) {
    if (printingWebViews.remove(web)) Handler(Looper.getMainLooper()).post { runCatching { web.destroy() } }
}

/**
 * Opens the system print dialog for [doc], where the user can print or pick "Save as PDF". Renders [toPrintHtml] in an
 * off-screen WebView. Returns false when printing isn't possible here (no activity, no print service, no WebView);
 * [onError] reports a failure after the page has loaded. [accentHex] is "#RRGGBB" (see [docPrintAccent]). Main thread only.
 */
fun printDocAsPdf(context: Context, doc: Doc, ticks: Set<String>, accentHex: String, onError: (String) -> Unit = {}): Boolean {
    val activity = context.findActivity() ?: return false
    if (activity.isFinishing || !activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PRINTING)) return false
    val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return false
    val appName = runCatching { activity.applicationInfo.loadLabel(activity.packageManager).toString() }.getOrDefault("")
    val html = doc.toPrintHtml(appName, accentHex, ticks)
    val jobName = docExportBaseName(doc.title)
    val web = try { WebView(activity) } catch (e: Exception) { return false } // WebView missing or mid-update
    printingWebViews += web
    web.settings.javaScriptEnabled = false
    web.settings.allowFileAccess = false
    web.webViewClient = object : WebViewClient() {
        private var sent = false
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
        override fun onPageFinished(view: WebView, url: String?) {
            if (sent) return
            sent = true
            try {
                val adapter = ReleasingPrintAdapter(view.createPrintDocumentAdapter(jobName)) { releasePrintWebView(view) }
                printManager.print(jobName, adapter, PrintAttributes.Builder().build())
            } catch (e: Exception) {
                releasePrintWebView(view)
                onError("Couldn't open the print dialog.")
            }
        }
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail?): Boolean {
            releasePrintWebView(view)
            if (!sent) { sent = true; onError("Couldn't prepare the PDF. Please try again.") }
            return true
        }
    }
    return try {
        web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        true
    } catch (e: Exception) {
        releasePrintWebView(web)
        false
    }
}
