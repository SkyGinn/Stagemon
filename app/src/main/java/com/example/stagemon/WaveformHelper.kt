package com.example.stagemon

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import android.view.View
import com.masoudss.lib.WaveformSeekBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WaveformHelper {

    private const val TAG = "WaveformHelper"

    suspend fun loadWaveform(
        contentResolver: ContentResolver,
        audioUri: Uri,
        waveformSeekBar: WaveformSeekBar
    ) {
        try {
            // ТЕСТОВЫЕ ДАННЫЕ
            val testAmplitudes = IntArray(100) { (Math.random() * 100).toInt() }

            withContext(Dispatchers.Main) {
                waveformSeekBar.visibility = View.VISIBLE
                waveformSeekBar.setSampleFrom(testAmplitudes)
                // ОТКЛЮЧАЕМ ВОЗМОЖНОСТЬ ПЕРЕТАСКИВАНИЯ
                waveformSeekBar.isEnabled = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading waveform", e)
            withContext(Dispatchers.Main) {
                waveformSeekBar.visibility = View.GONE
            }
        }
    }

    fun clearWaveform(waveformSeekBar: WaveformSeekBar) {
        waveformSeekBar.visibility = View.GONE
        waveformSeekBar.setSampleFrom(intArrayOf())
        waveformSeekBar.isEnabled = false
    }
}