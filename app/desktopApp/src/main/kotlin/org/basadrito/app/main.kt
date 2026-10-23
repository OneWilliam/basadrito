package org.basadrito.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

fun main() = application {
    val dependencies = remember { DesktopAppDependencies() }
    DisposableEffect(dependencies) { onDispose { dependencies.close() } }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Basadrito",
    ) {
        App(dependencies.container)
    }
}
