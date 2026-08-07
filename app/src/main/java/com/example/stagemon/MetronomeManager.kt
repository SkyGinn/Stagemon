package com.example.stagemon

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import android.util.Log

class MetronomeManager private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: MetronomeManager? = null

        fun getInstance(context: Context): MetronomeManager {
            return instance ?: synchronized(this) {
                instance ?: MetronomeManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private enum class ControlSource {
        NONE,        // метроном выключен
        SETTINGS,    // управление из окна настроек
        SONG         // управление из песни
    }

    private external fun setMetronomeBpm(enginePtr: Long, bpm: Int)
    private external fun setMetronomeVolume(enginePtr: Long, volume: Float)
    private external fun setMetronomeChannel(enginePtr: Long, mode: Int)
    private external fun resetMetronome(enginePtr: Long)

    private var settingsWaveform = 0
    private var settingsClickDuration = 20
    private var enginePtr: Long = 0L
    private var controlSource = ControlSource.NONE
    private val prefs: SharedPreferences = context.getSharedPreferences("metronome", Context.MODE_PRIVATE)

    private var settingsBpm = 120
    private var settingsStrongFreq = 1000
    private var settingsWeakFreq = 800
    private var settingsTimeSignature = "4/4"
    private var settingsChannel = "both"
    private var settingsVolume = 100
    private var settingsEnabled = false

    private var songBpm = 120
    private var songEnabled = false

    private val _isGloballyEnabled = MutableLiveData<Boolean>(false)
    val isGloballyEnabled: LiveData<Boolean> = _isGloballyEnabled

    private val _isPlaying = MutableLiveData<Boolean>(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _currentBpm = MutableLiveData<Int>(120)
    val currentBpm: LiveData<Int> = _currentBpm

    private val _timeSignature = MutableLiveData<String>("4/4")
    val timeSignature: LiveData<String> = _timeSignature

    private val _outputChannel = MutableLiveData<String>("both")
    val outputChannel: LiveData<String> = _outputChannel

    private val _volume = MutableLiveData<Int>(100)
    val volume: LiveData<Int> = _volume

    private val _strongFreq = MutableLiveData<Int>(1000)
    val strongFreq: LiveData<Int> = _strongFreq

    private val _weakFreq = MutableLiveData<Int>(800)
    val weakFreq: LiveData<Int> = _weakFreq

    init {
        loadFromPrefs()
    }

    fun setEnginePtr(ptr: Long) {
        enginePtr = ptr
        Log.d("METRONOME", "EnginePtr set to $ptr")
    }

    fun settingsSetWaveform(type: Int) {
        settingsWaveform = type
        prefs.edit().putInt("settings_waveform", type).apply()
        if (controlSource == ControlSource.SETTINGS) {
            applyToEngine()
        }
    }

    fun settingsSetClickDuration(ms: Int) {
        // Разреши 10, 15, 25
        val validMs = when (ms) {
            10, 15, 25 -> ms
            else -> 15
        }
        settingsClickDuration = validMs
        prefs.edit().putInt("settings_click_duration", validMs).apply()
        if (controlSource == ControlSource.SETTINGS) {
            applyToEngine()
        }
    }

    fun settingsOpened() {
        Log.d("METRONOME", "settingsOpened()")
        songEnabled = _isPlaying.value ?: false
        songBpm = _currentBpm.value ?: 120

        controlSource = ControlSource.SETTINGS

        settingsBpm = prefs.getInt("settings_bpm", 120)
        settingsStrongFreq = prefs.getInt("settings_strong_freq", 1000)
        settingsWeakFreq = prefs.getInt("settings_weak_freq", 800)
        settingsTimeSignature = prefs.getString("settings_time_signature", "4/4") ?: "4/4"
        settingsChannel = prefs.getString("settings_channel", "both") ?: "both"
        settingsVolume = prefs.getInt("settings_volume", 100)
        settingsWaveform = prefs.getInt("settings_waveform", 0)
        settingsClickDuration = prefs.getInt("settings_click_duration", 20)

        _currentBpm.value = settingsBpm
        _strongFreq.value = settingsStrongFreq
        _weakFreq.value = settingsWeakFreq
        _timeSignature.value = settingsTimeSignature
        _outputChannel.value = settingsChannel
        _volume.value = settingsVolume
        _isPlaying.value = true
        _isGloballyEnabled.value = true

        applyToEngine()
    }

    fun settingsSetBpm(bpm: Int) {
        settingsBpm = bpm
        prefs.edit().putInt("settings_bpm", bpm).apply()
        if (controlSource == ControlSource.SETTINGS) {
            _currentBpm.value = bpm
            applyToEngine()
        }
    }

    fun settingsSetStrongFreq(freq: Int) {
        settingsStrongFreq = freq
        prefs.edit().putInt("settings_strong_freq", freq).apply()
        if (controlSource == ControlSource.SETTINGS) {
            _strongFreq.value = freq
            applyToEngine()
        }
    }

    fun settingsSetWeakFreq(freq: Int) {
        settingsWeakFreq = freq
        prefs.edit().putInt("settings_weak_freq", freq).apply()
        if (controlSource == ControlSource.SETTINGS) {
            _weakFreq.value = freq
            applyToEngine()
        }
    }

    fun settingsSetTimeSignature(signature: String) {
        settingsTimeSignature = signature
        prefs.edit().putString("settings_time_signature", signature).apply()
        if (controlSource == ControlSource.SETTINGS) {
            _timeSignature.value = signature
        }
    }

    fun settingsSetChannel(channel: String) {
        settingsChannel = channel
        prefs.edit().putString("settings_channel", channel).apply()
        if (controlSource == ControlSource.SETTINGS) {
            _outputChannel.value = channel
            applyToEngine()
        }
    }

    fun settingsSetVolume(volume: Int) {
        settingsVolume = volume
        prefs.edit().putInt("settings_volume", volume).apply()
        if (controlSource == ControlSource.SETTINGS) {
            _volume.value = volume
            applyToEngine()
        }
    }

    fun settingsStop() {
        if (controlSource == ControlSource.SETTINGS) {
            MainActivity.setMetronomeEnabled(enginePtr, false)
            _isPlaying.value = false
        }
    }

    fun settingsHold() {
        if (enginePtr != 0L && controlSource == ControlSource.SETTINGS) {
            MainActivity.setMetronomeHolding(enginePtr, true)
            MainActivity.setMetronomeEnabled(enginePtr, true)
        }
    }

    fun settingsRelease() {
        if (enginePtr != 0L && controlSource == ControlSource.SETTINGS) {
            MainActivity.setMetronomeHolding(enginePtr, false)
            MainActivity.resetMetronome(enginePtr)
            MainActivity.setMetronomeEnabled(enginePtr, true)
        }
    }

    fun settingsClosed() {
        Log.d("METRONOME", "settingsClosed()")
        MainActivity.setMetronomeEnabled(enginePtr, false)
        controlSource = ControlSource.NONE
        _isPlaying.value = false
        _isGloballyEnabled.value = false
    }

    fun songUpdate(bpm: Int, enabled: Boolean) {
        Log.d("METRONOME", "songUpdate(bpm=$bpm, enabled=$enabled)")
        songBpm = bpm
        songEnabled = enabled

        if (controlSource != ControlSource.SETTINGS) {
            controlSource = if (enabled) ControlSource.SONG else ControlSource.NONE

            if (controlSource == ControlSource.SONG) {
                _currentBpm.value = songBpm
                _isPlaying.value = true
                _isGloballyEnabled.value = true
                MainActivity.setMetronomeEnabled(enginePtr, true)
                MainActivity.setMetronomeBpm(enginePtr, songBpm)
                MainActivity.setMetronomeVolume(enginePtr, 0.5f)
                MainActivity.setMetronomeChannel(enginePtr, 2)
                MainActivity.setMetronomeStrongFreq(enginePtr, 1000)
                MainActivity.setMetronomeWeakFreq(enginePtr, 800)
            } else {
                _isPlaying.value = false
                _isGloballyEnabled.value = false
                MainActivity.setMetronomeEnabled(enginePtr, false)
            }
        }
    }

    fun toggleGlobalEnabled() {
        val newState = !(_isGloballyEnabled.value ?: false)
        _isGloballyEnabled.value = newState

        if (enginePtr != 0L) {
            if (newState) {
                // ПРИНУДИТЕЛЬНО ВКЛЮЧАЕМ МЕТРОНОМ
                MainActivity.setMetronomeEnabled(enginePtr, true)
                MainActivity.setMetronomeBpm(enginePtr, 120)
                MainActivity.setMetronomeVolume(enginePtr, 0.5f)
                MainActivity.setMetronomeChannel(enginePtr, 2)
                controlSource = ControlSource.SONG  // ← ВАЖНО!
            } else {
                MainActivity.setMetronomeEnabled(enginePtr, false)
                controlSource = ControlSource.NONE
            }
        }
    }

    private fun applyToEngine() {
        if (enginePtr == 0L) return

        when (controlSource) {
            ControlSource.NONE -> {
                // Ничего не делаем
            }
            ControlSource.SETTINGS -> {
                MainActivity.setMetronomeEnabled(enginePtr, true)
                MainActivity.setMetronomeBpm(enginePtr, settingsBpm)
                MainActivity.setMetronomeVolume(enginePtr, settingsVolume / 100f)
                MainActivity.setMetronomeChannel(enginePtr, channelToInt(settingsChannel))
                MainActivity.setMetronomeStrongFreq(enginePtr, settingsStrongFreq)
                MainActivity.setMetronomeWeakFreq(enginePtr, settingsWeakFreq)
                MainActivity.setMetronomeWaveform(enginePtr, settingsWaveform)
                MainActivity.setMetronomeClickDuration(enginePtr, settingsClickDuration)
            }
            ControlSource.SONG -> {
                // Настройки уже применены в songUpdate
            }
        }
    }

    private fun loadFromPrefs() {
        settingsBpm = prefs.getInt("settings_bpm", 120)
        settingsStrongFreq = prefs.getInt("settings_strong_freq", 1000)
        settingsWeakFreq = prefs.getInt("settings_weak_freq", 800)
        settingsTimeSignature = prefs.getString("settings_time_signature", "4/4") ?: "4/4"
        settingsChannel = prefs.getString("settings_channel", "both") ?: "both"
        settingsVolume = prefs.getInt("settings_volume", 100)
        settingsWaveform = prefs.getInt("settings_waveform", 0)
        settingsClickDuration = prefs.getInt("settings_click_duration", 20)
    }

    private fun channelToInt(channel: String): Int {
        return when (channel) {
            "left" -> 0
            "right" -> 1
            else -> 2
        }
    }

    fun getChannelString(): String = _outputChannel.value ?: "both"
    fun getTimeSignatureString(): String = _timeSignature.value ?: "4/4"
    fun getWaveform(): Int = settingsWaveform
    fun getClickDuration(): Int = settingsClickDuration
}