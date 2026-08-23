package com.betterstreamflix.activities.main

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betterstreamflix.BuildConfig
import com.betterstreamflix.utils.FileLogger
import com.betterstreamflix.utils.GitHub
import com.betterstreamflix.utils.InAppUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import com.betterstreamflix.utils.UserPreferences
import java.io.File

class MainViewModel : ViewModel() {

    private val _state = MutableStateFlow<State>(State.CheckingUpdate)
    val state: Flow<State> = _state

    sealed class State {
        data object CheckingUpdate : State()
        data class SuccessCheckingUpdate(val newReleases: List<GitHub.Release>, val asset: GitHub.Release.Asset) : State()

        data object DownloadingUpdate : State()
        data class SuccessDownloadingUpdate(val apk: File) : State()

        data object InstallingUpdate : State()

        data class FailedUpdate(val error: Exception) : State()
    }


    fun checkUpdate() = viewModelScope.launch(Dispatchers.IO) {
        FileLogger.i("MainViewModel", "checkUpdate() called. updateCheckEnabled=${UserPreferences.updateCheckEnabled}")
        if (!UserPreferences.updateCheckEnabled) return@launch
        _state.emit(State.CheckingUpdate)
        FileLogger.i("MainViewModel", "checkUpdate: emitting CheckingUpdate state")

        try {
            val newReleases = InAppUpdater.getNewReleases()
            FileLogger.i("MainViewModel", "checkUpdate: found ${newReleases.size} releases")
            if (newReleases.isEmpty()) return@launch

            val asset = (newReleases.firstOrNull()?.assets ?: emptyList())
                .filter { it.contentType == "application/vnd.android.package-archive" }
                .find {
                    when (BuildConfig.APP_LAYOUT) {
                        "mobile" -> it.name.endsWith("-mobile.apk")
                        "tv" -> it.name.endsWith("-tv.apk")
                        else -> !it.name.endsWith("-mobile.apk") && !it.name.endsWith("-tv.apk")
                    }
                }
                ?: throw Exception("Can't find update APK")

            FileLogger.i("MainViewModel", "checkUpdate: found asset ${asset.name} (${asset.size} bytes)")
            _state.emit(State.SuccessCheckingUpdate(newReleases, asset))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            FileLogger.e("MainViewModel", "checkUpdate FAILED: ${e.message}", e)
            Log.e("MainViewModel", "checkUpdate: ", e)
        }
    }

    fun downloadUpdate(
        context: Context,
        asset: GitHub.Release.Asset,
    ) = viewModelScope.launch(Dispatchers.IO) {
        FileLogger.i("MainViewModel", "downloadUpdate: ${asset.name}")
        _state.emit(State.DownloadingUpdate)

        try {
            val apk = InAppUpdater.downloadApk(context, asset)
            FileLogger.i("MainViewModel", "downloadUpdate: success, apk=${apk.absolutePath}")
            _state.emit(State.SuccessDownloadingUpdate(apk))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            FileLogger.e("MainViewModel", "downloadUpdate FAILED: ${e.message}", e)
            Log.e("MainViewModel", "downloadUpdate: ", e)
            _state.emit(State.FailedUpdate(e))
        }
    }

    fun installUpdate(
        context: Context,
        apk: File,
    ) = viewModelScope.launch(Dispatchers.IO) {
        FileLogger.i("MainViewModel", "installUpdate: ${apk.absolutePath}")
        _state.emit(State.InstallingUpdate)

        try {
            InAppUpdater.installApk(context, Uri.fromFile(apk))
            FileLogger.i("MainViewModel", "installUpdate: installApk called")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            FileLogger.e("MainViewModel", "installUpdate FAILED: ${e.message}", e)
            Log.e("MainViewModel", "installUpdate: ", e)
            _state.emit(State.FailedUpdate(e))
        }
    }
}
