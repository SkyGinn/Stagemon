package com.example.stagemon

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.lifecycle.ViewModelProvider
import android.content.pm.ActivityInfo
import android.view.View

class StageActivity : AppCompatActivity() {

    private lateinit var viewModel: PlaylistViewModel
    private lateinit var tvCurrentSong: TextView
    private lateinit var tvPrevSong: TextView
    private lateinit var tvNextSong: TextView
    private lateinit var tvBehavior: TextView
    private lateinit var tvTempo: TextView
    private lateinit var btnPlayStop: Button
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var seekFoh: SeekBar
    private lateinit var seekMon: SeekBar
    private lateinit var seekMetr: SeekBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setContentView(R.layout.activity_stage)

        viewModel = ViewModelProvider(this)[PlaylistViewModel::class.java]

        tvCurrentSong = findViewById(R.id.tvCurrentSong)
        tvPrevSong = findViewById(R.id.tvPrevSong)
        tvNextSong = findViewById(R.id.tvNextSong)
        tvBehavior = findViewById(R.id.tvBehavior)
        tvTempo = findViewById(R.id.tvTempo)
        btnPlayStop = findViewById(R.id.btnPlayStop)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        seekFoh = findViewById(R.id.seekFoh)
        seekMon = findViewById(R.id.seekMon)
        seekMetr = findViewById(R.id.seekMetr)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        // Наблюдаем за изменениями плейлиста
        viewModel.pairsLive.observe(this) { updateSongDisplays() }
        viewModel.currentPairIndex.observe(this) { updateSongDisplays() }

        // Кнопки управления
        btnPlayStop.setOnClickListener {
            val intent = Intent("LOCAL_TOGGLE_PLAYBACK")
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        }

        btnPrev.setOnClickListener {
            val intent = Intent("LOCAL_PREV_TRACK")
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        }

        btnNext.setOnClickListener {
            val intent = Intent("LOCAL_NEXT_TRACK")
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        }

        // Слайдеры громкости
        seekFoh.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val intent = Intent("LOCAL_SET_VOLUME")
                    intent.putExtra("foh_vol", progress / 100f)
                    LocalBroadcastManager.getInstance(this@StageActivity).sendBroadcast(intent)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekMon.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val intent = Intent("LOCAL_SET_VOLUME")
                    intent.putExtra("mon_vol", progress / 100f)
                    LocalBroadcastManager.getInstance(this@StageActivity).sendBroadcast(intent)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // seekMetr пока просто заглушка
        seekMetr.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // метроном будет позже
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        updateSongDisplays()
    }

    private fun updateSongDisplays() {
        val pairs = viewModel.pairs?: return
        val index = viewModel.currentPairIndex.value ?: 0

        // Предыдущая песня
        var prevIndex = index - 1
        while (prevIndex >= 0 && !pairs[prevIndex].isValid()) prevIndex--
        tvPrevSong.text = if (prevIndex >= 0) {
            pairs[prevIndex].name.ifBlank { pairs[prevIndex].fohFile.substringBeforeLast(".wav") }
        } else "..."

        // Текущая песня
        val current = pairs[index]
        tvCurrentSong.text = if (current.isValid()) {
            current.name.ifBlank { current.fohFile.substringBeforeLast(".wav") }
        } else "---"

        // Темп и поведение
        tvTempo.text = if (current.bpm.isNotEmpty()) "${current.bpm}" else ""
        tvBehavior.text = when {
            current.behavior.isEmpty() -> ""
            current.behavior == "I" -> "[I]"
            current.behavior == "P" -> "[P]"
            else -> "[${current.behavior}]"
        }

        // Следующая песня
        var nextIndex = index + 1
        while (nextIndex < pairs.size && !pairs[nextIndex].isValid()) nextIndex++
        tvNextSong.text = if (nextIndex < pairs.size) {
            pairs[nextIndex].name.ifBlank { pairs[nextIndex].fohFile.substringBeforeLast(".wav") }
        } else "..."
    }
}