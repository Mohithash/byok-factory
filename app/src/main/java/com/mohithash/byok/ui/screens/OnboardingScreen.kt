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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.mohithash.byok.ui.AppViewModel
import com.mohithash.byok.ui.HeroCard
import com.mohithash.byok.ui.Label
import com.mohithash.byok.ui.StatCard
import com.mohithash.byok.ui.theme.LocalHeroDeep

@Composable
fun OnboardingScreen(vm: AppViewModel) {
    val spec = vm.spec
    val cs = MaterialTheme.colorScheme
    var values by remember { mutableStateOf(mapOf<String, String>()) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { LargeFlexibleTopAppBar(title = { Text(spec.onboarding.title) }, subtitle = { Text(spec.onboarding.subtitle) }, scrollBehavior = scroll, colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.surface, scrolledContainerColor = cs.surface)) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HeroCard(colors = listOf(cs.primary, LocalHeroDeep.current), blobShape = MaterialShapes.Cookie12Sided) {
                Label(spec.category, cs.onPrimary.copy(alpha = 0.8f)); Text(spec.name, style = MaterialTheme.typography.displaySmall, color = cs.onPrimary); Text(spec.tagline, color = cs.onPrimary.copy(alpha = 0.9f), style = MaterialTheme.typography.titleMedium)
                spec.onboarding.bullets.forEach { Text("✓  $it", color = cs.onPrimary.copy(alpha = 0.9f)) }
            }
            if (spec.profile.isNotEmpty()) StatCard { Label("About you"); Text("Used to personalise every answer. Change any time in Settings.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant); FieldsForm(spec.profile, emptyMap(), { values = it }) }
            Text("Bring your own AI key (Claude or any OpenAI‑compatible endpoint). Nothing leaves your phone except the requests you make, sent straight to your provider.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Button({ vm.saveProfile(values) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Get started", style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(24.dp))
        }
    }
}
