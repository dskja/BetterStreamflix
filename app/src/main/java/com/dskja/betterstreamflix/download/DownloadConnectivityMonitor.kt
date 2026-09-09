package com.dskja.betterstreamflix.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DownloadNetworkType {
    NONE,
    WIFI,
    CELLULAR,
    OTHER,
}

object DownloadConnectivityMonitor {
    data class Status(
        val type: DownloadNetworkType,
        val validated: Boolean,
    )

    private val _status = MutableStateFlow(Status(DownloadNetworkType.NONE, false))
    val status: StateFlow<Status> = _status.asStateFlow()

    @Volatile
    private var registered = false

    fun start(context: Context) {
        if (registered) return
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        refresh(cm)
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(
            request,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = refresh(cm)
                override fun onLost(network: Network) = refresh(cm)
                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) = refresh(cm)
            },
        )
        registered = true
    }

    fun current(context: Context): Status {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        refresh(cm)
        return _status.value
    }

    fun isWifi(context: Context): Boolean = current(context).type == DownloadNetworkType.WIFI

    fun isMetered(context: Context): Boolean {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cm.isActiveNetworkMetered
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.type == ConnectivityManager.TYPE_MOBILE
        }
    }

    private fun refresh(cm: ConnectivityManager) {
        val network = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cm.activeNetwork
        } else {
            null
        }
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            || caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val type = when {
            caps == null -> DownloadNetworkType.NONE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> DownloadNetworkType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> DownloadNetworkType.CELLULAR
            else -> DownloadNetworkType.OTHER
        }
        _status.value = Status(type = type, validated = validated)
    }
}
