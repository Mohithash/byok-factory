@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.mohithash.byok.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mohithash.byok.ui.screens.HistoryScreen
import com.mohithash.byok.ui.screens.HomeScreen
import com.mohithash.byok.ui.screens.OnboardingScreen
import com.mohithash.byok.ui.screens.SettingsScreen
import com.mohithash.byok.ui.screens.ToolScreen

@Composable
fun Nav(vm: AppViewModel) {
    val onboarded by vm.onboarded.collectAsState()
    if (!onboarded) { OnboardingScreen(vm); return }
    val nav = rememberNavController()
    NavHost(nav, "home") {
        composable("home") { HomeScreen(vm, onTool = { vm.openTool(it); nav.navigate("tool") }, onResult = { vm.openResult(it); nav.navigate("tool") }, onHistory = { nav.navigate("history") }, onSettings = { nav.navigate("settings") }) }
        composable("tool") { ToolScreen(vm, onBack = { nav.popBackStack() }, onSettings = { nav.navigate("settings") }) }
        composable("history") { HistoryScreen(vm, onBack = { nav.popBackStack() }, onOpen = { vm.openResult(it); nav.navigate("tool") }) }
        composable("settings") { SettingsScreen(vm, onBack = { nav.popBackStack() }) }
    }
}
