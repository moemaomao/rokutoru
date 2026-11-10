package com.rokutoru

import androidx.compose.ui.window.application
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import com.rokutoru.ui.RokutoruApp
import com.rokutoru.model.AppState

fun main() = application {
    val windowState = rememberWindowState(width = 1400.dp, height = 900.dp)
    val appState = remember { AppState() }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Rokutoru — AI Manga Translator",
        state = windowState
    ) {
        RokutoruApp(appState)
    }
}