@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.mohithash.byok.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import com.mohithash.byok.ai.AiSettings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.mohithash.byok.engine.Field
import com.mohithash.byok.engine.Prefs
import com.mohithash.byok.ui.AppViewModel
import com.mohithash.byok.ui.HeroCard
import com.mohithash.byok.ui.heroColors
import com.mohithash.byok.ui.onHeroColor
import com.mohithash.byok.ui.Label
import com.mohithash.byok.ui.StatCard
import com.mohithash.byok.ui.theme.LocalHeroDeep
import kotlinx.coroutines.launch

/** Stored value for an answer-language choice: blank means English (the default). */
internal fun onboardingLanguagePref(choice: String): String = choice.trim().takeUnless { it.isBlank() || it.equals("English", ignoreCase = true) }.orEmpty()

/** Required "about you" fields still empty in [values] (photo fields can't be filled here, so they never block). */
internal fun onboardingMissing(fields: List<Field>, values: Map<String, String>): List<Field> =
    fields.filter { it.required && it.type != "photo" && values[it.key].isNullOrBlank() }

@Composable
fun OnboardingScreen(vm: AppViewModel) {
    val spec = vm.spec
    val cs = MaterialTheme.colorScheme
    val onHero = onHeroColor()
    /** What's typed in the AI card; Get started saves it if it's a usable key the user didn't save yet. */
    var aiDraft by remember { mutableStateOf<AiSettings?>(null) }
    val ai by vm.ai.collectAsState()
    // Onboarding can be restarted from Settings: start from what's already saved.
    val saved = remember { vm.profile.value }
    var values by remember { mutableStateOf(spec.profile.associate { it.key to (saved[it.key] ?: it.default) }) }
    var language by rememberSaveable { mutableStateOf(vm.prefs.value.language.ifBlank { "English" }) }
    var languageOpen by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val missing = onboardingMissing(spec.profile, values)
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scroll.nestedScrollConnection), snackbarHost = { SnackbarHost(snack) },
        topBar = { LargeFlexibleTopAppBar(title = { Text(spec.onboarding.title) }, subtitle = { Text(spec.onboarding.subtitle) }, scrollBehavior = scroll, colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface, scrolledContainerColor = cs.surface)) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HeroCard(colors = heroColors(), blobShape = MaterialShapes.Cookie12Sided) {
                Label(spec.category, onHero.copy(alpha = 0.8f)); Text(spec.name, style = MaterialTheme.typography.displaySmall, color = onHero); Text(spec.tagline, color = onHero.copy(alpha = 0.9f), style = MaterialTheme.typography.titleMedium)
                spec.onboarding.bullets.forEach { Text("✓  $it", color = onHero.copy(alpha = 0.9f)) }
            }
            if (spec.profile.isNotEmpty()) StatCard {
                Label("About you")
                Text("Used to personalise every answer. Change any time in Settings.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                FieldsForm(spec.profile, saved, { values = it })
            }
            StatCard {
                Label("Answer language")
                Text("Every answer is written in this language. Change any time in Settings.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                ExposedDropdownMenuBox(languageOpen, { languageOpen = it }) {
                    OutlinedTextField(language, {}, readOnly = true, singleLine = true, label = { Text("Language") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(languageOpen) }, shape = MaterialTheme.shapes.large,
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth())
                    ExposedDropdownMenu(languageOpen, { languageOpen = false }) {
                        Prefs.LANGUAGES.forEach { l -> DropdownMenuItem(text = { Text(l) }, onClick = { language = l; languageOpen = false }, contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding) }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Connect your AI", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
                Text(if (ai.configured) "✓ Key saved — you're ready to go." else "Optional: test and save your key now, or add it later in Settings.",
                    style = MaterialTheme.typography.bodySmall, color = if (ai.configured) cs.primary else cs.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
            }
            AiSettingsCard(ai, vm.client, vm::saveAi, onMessage = { m -> scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(m) } }, onDraftChange = { aiDraft = it })
            Text("Bring your own AI key (Claude or any OpenAI‑compatible endpoint). Nothing is sent anywhere except the requests you make, straight to your provider (and Android backup, if you use it — never your key).", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Button({
                // Preferences first: saving the profile marks onboarding done and swaps to Home immediately.
                val lang = onboardingLanguagePref(language)
                val prefs = vm.prefs.value
                if (lang != prefs.language) vm.savePrefs(prefs.copy(language = lang))
                aiDraft?.takeIf { it.configured && aiBaseUrlError(it.baseUrl) == null && it != vm.ai.value }?.let(vm::saveAi)
                vm.saveProfile(vm.profile.value + values) // keeps keys a newer/older spec or a backup added
            }, enabled = missing.isEmpty(), shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Get started", style = MaterialTheme.typography.titleMedium) }
            if (missing.isNotEmpty()) Text("Fill in ${missing.joinToString(", ") { "“${it.label}”" }} above to continue.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
            Spacer(Modifier.height(24.dp))
        }
    }
}
