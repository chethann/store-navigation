package com.pincode.storenav

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.pincode.storenav.ui.StoreMapEditor

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Store Navigation Editor",
        state = rememberWindowState()
    ) {
        StoreMapEditor()
    }
}