package com.huanchengfly.tieba.post.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.annotation.RequiresApi
import com.huanchengfly.tieba.post.App

fun isNetworkConnected(context: Context = App.INSTANCE): Boolean {
    return context.connectivityManager().isConnected()
}

object NetworkUtil {
    fun isNetworkConnected(context: Context?): Boolean {
        return context?.connectivityManager()?.isConnected() ?: false
    }

    fun isWifiConnected(context: Context?): Boolean {
        return context?.connectivityManager()?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
    }

    fun isMobileConnected(context: Context?): Boolean {
        return context?.connectivityManager()?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ?: false
    }
}

private fun Context.connectivityManager(): ConnectivityManager {
    return getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
}

private fun ConnectivityManager.isConnected(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        return isConnectedOnApi23Plus()
    }
    @Suppress("DEPRECATION")
    return activeNetworkInfo?.isConnected == true
}

private fun ConnectivityManager.hasTransport(transportType: Int): Boolean {
    val capabilities = activeNetworkCapabilities()
    if (capabilities != null) {
        return capabilities.hasTransport(transportType)
    }
    @Suppress("DEPRECATION")
    val legacyType = when (transportType) {
        NetworkCapabilities.TRANSPORT_WIFI -> ConnectivityManager.TYPE_WIFI
        NetworkCapabilities.TRANSPORT_CELLULAR -> ConnectivityManager.TYPE_MOBILE
        else -> return false
    }
    @Suppress("DEPRECATION")
    return getNetworkInfo(legacyType)?.isConnected == true
}

private fun ConnectivityManager.activeNetworkCapabilities(): NetworkCapabilities? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        return null
    }
    val network = activeNetwork ?: return null
    return getNetworkCapabilities(network)
}

@RequiresApi(Build.VERSION_CODES.M)
private fun ConnectivityManager.isConnectedOnApi23Plus(): Boolean {
    val capabilities = activeNetworkCapabilities() ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
