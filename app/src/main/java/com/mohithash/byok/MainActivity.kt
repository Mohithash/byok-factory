package com.mohithash.byok

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohithash.byok.ui.AppViewModel
import com.mohithash.byok.ui.Nav
import com.mohithash.byok.ui.theme.AppTheme

fun String.color(): Color = Color(("FF" + removePrefix("#")).toLong(16))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as App
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(app) as T
        }
        val c = app.spec.colors
        setContent { AppTheme(c[0].color(), c.getOrElse(1) { c[0] }.color(), c.getOrElse(2) { c[0] }.color()) { Nav(viewModel(factory = factory)) } }
    }
}
