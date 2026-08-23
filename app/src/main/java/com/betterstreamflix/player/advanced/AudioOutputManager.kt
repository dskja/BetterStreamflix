package com.betterstreamflix.player.advanced

import android.content.Context
import androidx.core.content.edit

/**
 * Audio output manager — manages audio output device selection
 * and routing (speaker, Bluetooth, HDMI).
 */
object AudioOutputManager {

    /**
     * Get the current audio output type.
     */
    fun getCurrentOutputType(context: Context): AudioOutputType {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            ?: return AudioOutputType.SPEAKER

        return when (audioManager.getDevicesForStream(android.media.AudioManager.STREAM_MUSIC)) {
            // Check for Bluetooth
            else -> {
                val devices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                when {
                    devices.any { it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP } -> AudioOutputType.BLUETOOTH
                    devices.any { it.type == android.media.AudioDeviceInfo.TYPE_HDMI } -> AudioOutputType.HDMI
                    devices.any { it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET } -> AudioOutputType.HEADPHONES
                    else -> AudioOutputType.SPEAKER
                }
            }
        }
    }

    /**
     * Check if Bluetooth audio is connected.
     */
    fun isBluetoothConnected(context: Context): Boolean {
        return getCurrentOutputType(context) == AudioOutputType.BLUETOOTH
    }

    /**
     * Check if headphones are connected.
     */
    fun areHeadphonesConnected(context: Context): Boolean {
        return getCurrentOutputType(context) == AudioOutputType.HEADPHONES
    }

    enum class AudioOutputType {
        SPEAKER,
        HEADPHONES,
        BLUETOOTH,
        HDMI,
        OTHER,
    }
}
