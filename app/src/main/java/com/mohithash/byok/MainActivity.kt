package com.mohithash.byok

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.mohithash.byok.ui.AppViewModel
import com.mohithash.byok.ui.Incoming
import com.mohithash.byok.ui.Nav
import com.mohithash.byok.ui.Photo
import com.mohithash.byok.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun String.color(): Color = Color(("FF" + removePrefix("#")).toLong(16))

class MainActivity : ComponentActivity() {
    private lateinit var vm: AppViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as App
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(app) as T
        }
        vm = ViewModelProvider(this, factory)[AppViewModel::class.java]
        // Re-read the share intent after process death if the user hadn't picked a tool for it yet (the VM is new then).
        if (savedInstanceState == null || (savedInstanceState.getBoolean(SHARE_PENDING) && vm.incoming.value == null)) handle(intent)
        publishShortcuts(app)
        val c = app.spec.colors
        setContent {
            val prefs by vm.prefs.collectAsState()
            val dark = when (prefs.theme) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT) else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            AppTheme(c[0].color(), c.getOrElse(1) { c[0] }.color(), c.getOrElse(2) { c[0] }.color(), darkTheme = dark, dynamic = prefs.dynamicColor) { Nav(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(SHARE_PENDING, vm.incoming.value != null)
    }

    /** Share-sheet content and launcher shortcuts arrive here. */
    private fun handle(i: Intent?) {
        when (i?.action) {
            ACTION_TOOL -> vm.pendingToolId.value = i.getStringExtra(EXTRA_TOOL)
            Intent.ACTION_SEND -> {
                val text = listOfNotNull(i.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString(), i.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString())
                    .filter { it.isNotBlank() }.distinct().joinToString("\n\n")
                val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    else @Suppress("DEPRECATION") i.getParcelableExtra(Intent.EXTRA_STREAM)
                lifecycleScope.launch {
                    val photo = uri?.takeIf { i.type?.startsWith("image/") == true }?.let { u -> withContext(Dispatchers.IO) { runCatching { Photo.fromUri(this@MainActivity, u) }.getOrNull() } }
                    // A shared .txt file arrives as a stream rather than EXTRA_TEXT.
                    val fileText = if (text.isBlank() && uri != null && i.type?.startsWith("text/") == true)
                        withContext(Dispatchers.IO) { runCatching { readText(uri, MAX_SHARED_CHARS) }.getOrNull() }.orEmpty() else ""
                    val incoming = Incoming(text.ifBlank { fileText }.take(MAX_SHARED_CHARS), photo)
                    if (incoming.text.isBlank() && incoming.photo == null) Toast.makeText(this@MainActivity, "Couldn't read what was shared", Toast.LENGTH_SHORT).show()
                    else vm.receive(incoming)
                }
            }
        }
    }

    /** Up to [max] characters of a shared text document. */
    private fun readText(uri: Uri, max: Int): String = contentResolver.openInputStream(uri)?.bufferedReader()?.use { r ->
        val buf = CharArray(max); var n = 0
        while (n < max) { val k = r.read(buf, n, max - n); if (k < 0) break; n += k }
        String(buf, 0, n)
    }.orEmpty()

    /** Long-press launcher shortcuts straight into each tool (as many as the launcher allows). */
    private fun publishShortcuts(app: App) = runCatching {
        val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(this)
        val icon = IconCompat.createWithResource(this, R.mipmap.ic_launcher)
        val list = app.spec.tools.take(max).map { t ->
            ShortcutInfoCompat.Builder(this, "tool_${t.id}")
                .setShortLabel(t.title.take(24)).setLongLabel(t.title.take(40)).setIcon(icon)
                .setIntent(Intent(this, MainActivity::class.java).setAction(ACTION_TOOL).putExtra(EXTRA_TOOL, t.id))
                .build()
        }
        ShortcutManagerCompat.setDynamicShortcuts(this, list)
    }

    companion object {
        const val ACTION_TOOL = "com.mohithash.byok.action.TOOL"
        const val EXTRA_TOOL = "tool"
        private const val SHARE_PENDING = "share_pending"
        private const val MAX_SHARED_CHARS = 20_000
    }
}
