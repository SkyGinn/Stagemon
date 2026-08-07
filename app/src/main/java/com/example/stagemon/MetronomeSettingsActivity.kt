package com.example.stagemon

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import com.aigestudio.wheelpicker.WheelPicker
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import android.widget.SeekBar
import com.google.android.material.button.MaterialButtonToggleGroup

class MetronomeSettingsActivity : AppCompatActivity() {

    private lateinit var spinnerWaveform: Spinner
    private lateinit var toggleDuration: MaterialButtonToggleGroup
    private lateinit var wheelPickerBpm: WheelPicker
    private lateinit var wheelPickerStrongFreq: WheelPicker
    private lateinit var wheelPickerWeakFreq: WheelPicker
    private lateinit var spinnerTimeSignature: Spinner
    private lateinit var radioLeft: RadioButton
    private lateinit var radioRight: RadioButton
    private lateinit var radioBoth: RadioButton
    private lateinit var btnPattern: Button
    private lateinit var btnPlay: Button
    private lateinit var btnStop: Button
    private lateinit var btnClose: Button

    private lateinit var metronomeManager: MetronomeManager
    private var isHolding = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setContentView(R.layout.activity_metronome_settings)

        metronomeManager = MetronomeManager.getInstance(this)
        metronomeManager.settingsOpened()

        initViews()
        loadCurrentSettings()
        setupListeners()
    }

    private fun initViews() {
        wheelPickerBpm = findViewById(R.id.wheelPickerBpm)
        spinnerTimeSignature = findViewById(R.id.spinnerTimeSignature)
        radioLeft = findViewById(R.id.radioLeft)
        radioRight = findViewById(R.id.radioRight)
        radioBoth = findViewById(R.id.radioBoth)
        wheelPickerStrongFreq = findViewById(R.id.wheelPickerStrongFreq)
        wheelPickerWeakFreq = findViewById(R.id.wheelPickerWeakFreq)
        btnPattern = findViewById(R.id.btnPattern)
        btnPlay = findViewById(R.id.btnPlayMetro)
        btnStop = findViewById(R.id.btnStopMetro)
        btnClose = findViewById(R.id.btnClose)
        spinnerWaveform = findViewById(R.id.spinnerWaveform)
        toggleDuration = findViewById(R.id.toggleDuration)

        val waveforms = arrayOf("Sine", "Triangle", "Sawtooth")
        val waveformAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, waveforms)
        spinnerWaveform.adapter = waveformAdapter

        val volumeSeekBar = findViewById<SeekBar>(R.id.volumeMetroSettings)
        val volumeText = findViewById<TextView>(R.id.volumeMetroSettingsText)

        volumeSeekBar.progress = metronomeManager.volume.value ?: 100
        volumeText.text = "${volumeSeekBar.progress}%"

        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                volumeText.text = "$progress%"
                metronomeManager.settingsSetVolume(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        metronomeManager.volume.observe(this) { volume ->
            volumeSeekBar.progress = volume
            volumeText.text = "$volume%"
        }

        val bpmData = (40..999).map { it.toString() }
        wheelPickerBpm.data = bpmData

        val strongFreqData = (200..3000 step 10).map { it.toString() }
        wheelPickerStrongFreq.data = strongFreqData

        val weakFreqData = (200..3000 step 10).map { it.toString() }
        wheelPickerWeakFreq.data = weakFreqData

        try {
            val myFont = ResourcesCompat.getFont(this, R.font.myfont)
            wheelPickerBpm.typeface = myFont
            wheelPickerStrongFreq.typeface = myFont
            wheelPickerWeakFreq.typeface = myFont
            Log.d("METRONOME", "Шрифт применен успешно")
        } catch (e: Exception) {
            Log.e("METRONOME", "Ошибка загрузки шрифта", e)
            wheelPickerBpm.typeface = Typeface.DEFAULT_BOLD
            wheelPickerStrongFreq.typeface = Typeface.DEFAULT_BOLD
            wheelPickerWeakFreq.typeface = Typeface.DEFAULT_BOLD
        }

        val signatures = arrayOf("2/4", "3/4", "4/4", "6/8")
        val signatureAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, signatures)
        spinnerTimeSignature.adapter = signatureAdapter
    }

    private fun loadCurrentSettings() {
        wheelPickerBpm.selectedItemPosition = (metronomeManager.currentBpm.value ?: 120) - 40

        when (metronomeManager.getTimeSignatureString()) {
            "2/4" -> spinnerTimeSignature.setSelection(0)
            "3/4" -> spinnerTimeSignature.setSelection(1)
            "4/4" -> spinnerTimeSignature.setSelection(2)
            "6/8" -> spinnerTimeSignature.setSelection(3)
        }

        when (metronomeManager.getChannelString()) {
            "left" -> radioLeft.isChecked = true
            "right" -> radioRight.isChecked = true
            else -> radioBoth.isChecked = true
        }
        spinnerWaveform.setSelection(metronomeManager.getWaveform())
        toggleDuration.check(
            when (metronomeManager.getClickDuration()) {
                10 -> R.id.btnDuration10
                15 -> R.id.btnDuration15
                25 -> R.id.btnDuration25
                else -> R.id.btnDuration15
            }
        )
        wheelPickerStrongFreq.selectedItemPosition = (1000 - 200) / 10
        wheelPickerWeakFreq.selectedItemPosition = (800 - 200) / 10
    }

    private fun setupListeners() {
        wheelPickerBpm.setOnItemSelectedListener(object : WheelPicker.OnItemSelectedListener {
            override fun onItemSelected(picker: WheelPicker, data: Any?, position: Int) {
                val selectedBpm = (data as String).toInt()
                metronomeManager.settingsSetBpm(selectedBpm)
            }
        })

        wheelPickerStrongFreq.setOnItemSelectedListener(object : WheelPicker.OnItemSelectedListener {
            override fun onItemSelected(picker: WheelPicker, data: Any?, position: Int) {
                val selectedFreq = (data as String).toInt()
                metronomeManager.settingsSetStrongFreq(selectedFreq)
            }
        })

        wheelPickerWeakFreq.setOnItemSelectedListener(object : WheelPicker.OnItemSelectedListener {
            override fun onItemSelected(picker: WheelPicker, data: Any?, position: Int) {
                val selectedFreq = (data as String).toInt()
                metronomeManager.settingsSetWeakFreq(selectedFreq)
            }
        })

        spinnerWaveform.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                metronomeManager.settingsSetWaveform(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        toggleDuration.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                val ms = when (checkedId) {
                    R.id.btnDuration10 -> 10
                    R.id.btnDuration15 -> 15
                    R.id.btnDuration25 -> 25
                    else -> 15
                }
                metronomeManager.settingsSetClickDuration(ms)
            }
        }

        btnStop.setOnClickListener {
            Log.d("METRONOME", "STOP clicked")
            metronomeManager.settingsStop()
            isHolding = false
        }

        btnClose.setOnClickListener {
            Log.d("METRONOME", "CLOSE clicked")
            metronomeManager.settingsClosed()
            finish()
        }

        spinnerTimeSignature.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val signature = when (position) {
                    0 -> "2/4"
                    1 -> "3/4"
                    2 -> "4/4"
                    3 -> "6/8"
                    else -> "4/4"
                }
                metronomeManager.settingsSetTimeSignature(signature)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        radioLeft.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) metronomeManager.settingsSetChannel("left")
        }
        radioRight.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) metronomeManager.settingsSetChannel("right")
        }
        radioBoth.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) metronomeManager.settingsSetChannel("both")
        }

        btnPlay.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isHolding = true
                    metronomeManager.settingsHold()
                    v.isPressed = true
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isHolding) {
                        metronomeManager.settingsRelease()
                        isHolding = false
                    }
                    v.isPressed = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (isHolding) {
                        metronomeManager.settingsRelease()
                        isHolding = false
                    }
                    v.isPressed = false
                    true
                }
                else -> false
            }
        }
    }
}