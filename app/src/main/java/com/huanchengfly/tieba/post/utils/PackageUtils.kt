package com.huanchengfly.tieba.post.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

private fun Context.getPackageInfoCompat(packageName: String): PackageInfo {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
    }
}

fun Context.isPackageInstalled(packageName: String): Boolean {
    return try {
        getPackageInfoCompat(packageName)
        true
    } catch (e: Exception) {
        false
    }
}

val Context.packageInfo: PackageInfo
    get() = getPackageInfoCompat(packageName)

fun Context.isAnyPackageInstalled(packages: Array<String>): Boolean {
    return packages.any {
        isPackageInstalled(it)
    }
}
