@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.mohithash.byok.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    val pendingTool by vm.pendingToolId.collectAsState()
    val incoming by vm.incoming.collectAsState()
    // A launcher shortcut opens its tool directly.
    LaunchedEffect(pendingTool) {
        val id = pendingTool ?: return@LaunchedEffect
        vm.pendingToolId.value = null
        vm.toolById(id)?.let { vm.openTool(it); nav.navigate("tool") { popUpTo("home") } }
    }
    // Shared content waits on Home, where the user picks a tool for it.
    LaunchedEffect(incoming) {
        if (incoming != null && nav.currentDestination?.route != "home") nav.popBackStack("home", inclusive = false)
    }
    NavHost(nav, "home") {
        composable("home") { HomeScreen(vm, onTool = { vm.openTool(it); nav.navigate("tool") }, onResult = { vm.openResult(it); nav.navigate("tool") }, onHistory = { nav.navigate("history") }, onSettings = { nav.navigate("settings") }, onToolOpened = { nav.navigate("tool") }) }
        composable("tool") { ToolScreen(vm, onBack = { nav.popBackStack() }, onSettings = { nav.navigate("settings") }) }
        composable("history") { HistoryScreen(vm, onBack = { nav.popBackStack() }, onOpen = { vm.openResult(it); nav.navigate("tool") }) }
        composable("settings") { SettingsScreen(vm, onBack = { nav.popBackStack() }) }
    }
}
