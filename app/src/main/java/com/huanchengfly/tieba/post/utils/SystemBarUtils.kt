@file:Suppress("DEPRECATION")

package com.huanchengfly.tieba.post.utils

import android.app.Activity
import android.graphics.Color
import android.os.Build
import androidx.core.view.WindowCompat

internal fun isHyperOs(
    manufacturer: String = Build.MANUFACTURER,
    incrementalVersion: String = Build.VERSION.INCREMENTAL,
): Boolean = manufacturer.equals("Xiaomi", ignoreCase = true) &&
        incrementalVersion.startsWith("OS", ignoreCase = true)

fun Activity.configureTransparentSystemBars(
    statusBarDarkIcons: Boolean,
    navigationBarDarkIcons: Boolean,
) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    }
    controller.isAppearanceLightStatusBars = statusBarDarkIcons
    controller.isAppearanceLightNavigationBars = navigationBarDarkIcons
}
