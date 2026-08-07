package com.example.stagemon

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo // <--- Этот импорт жизненно необходим
import android.media.AudioManager
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

class UsbManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _usbStatus = MutableLiveData<UsbStatus>()
    val usbStatus: LiveData<UsbStatus> = _usbStatus

    data class UsbStatus(
        val deviceId: Int,
        val deviceName: String,
        val maxChannels: Int,
        val isConnected: Boolean
    )

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) { // <--- Используем out
            checkUsb()
        }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) { // <--- Используем out
            checkUsb()
        }
    }

    init {
        audioManager.registerAudioDeviceCallback(callback, null)
        checkUsb()
    }

    fun checkUsb() {
        val device = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }

        _usbStatus.value = if (device != null) {
            UsbStatus(
                deviceId = device.id,
                deviceName = device.productName.toString(),
                maxChannels = device.channelCounts.maxOrNull() ?: 2,
                isConnected = true
            )
        } else {
            UsbStatus(-1, "", 0, false)
        }
    }

    fun getUsbDevices(): List<AudioDeviceInfo> {
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }

    fun cleanup() {
        audioManager.unregisterAudioDeviceCallback(callback)
    }
}