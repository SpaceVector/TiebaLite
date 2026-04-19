package com.huanchengfly.tieba.post.ui.widgets.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun FullScreen(
    onBack: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = {
            onBack?.invoke()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = onBack != null,
            dismissOnClickOutside = false
        )
    ) {
        if (onBack != null) {
            BackHandler(onBack = onBack)
        }
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            content()
        }
    }
}
