package com.example.stagemon

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import android.content.pm.ActivityInfo
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.os.Build
import android.content.Context
import android.content.res.ColorStateList
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbDevice
import android.view.Gravity
import android.view.ViewGroup
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
    private lateinit var viewModel: PlaylistViewModel
    private var maxChannels = 0
    private lateinit var audioManager: AudioManager

    // ========== СПИННЕРЫ ==========
    private lateinit var spinnerFohL: Spinner
    private lateinit var spinnerFohR: Spinner
    private lateinit var spinnerMonL: Spinner
    private lateinit var spinnerMonR: Spinner
    private lateinit var spinnerBitDepth: Spinner

    // ========== КНОПКИ PLAY ==========
    private lateinit var containerTestFohL: FrameLayout
    private lateinit var containerTestFohR: FrameLayout
    private lateinit var containerTestMonL: FrameLayout
    private lateinit var containerTestMonR: FrameLayout
    private lateinit var containerTestMelody: FrameLayout
    private lateinit var btnTestFohL: ImageButton
    private lateinit var btnTestFohR: ImageButton
    private lateinit var btnTestMonL: ImageButton
    private lateinit var btnTestMonR: ImageButton
    private lateinit var btnTestMelody: ImageButton

    // Для toggle-логики: какая кнопка сейчас играет
    private var currentPlayingButton: ImageButton? = null

    // Флаг, чтобы избежать рекурсии при сбросе спиннеров
    private var isUpdatingSpinners = false

    private var buttonPulseAnimators = mutableMapOf<FrameLayout, android.animation.ValueAnimator>()

    // ========== КЭШ ПРОВЕРКИ ФОРМАТОВ ==========
    private val gson = Gson()
    private val prefs by lazy { getSharedPreferences("stagemon_cache", MODE_PRIVATE) }

    private fun startButtonPulse(container: FrameLayout) {
        if (buttonPulseAnimators[container] == null) {
            buttonPulseAnimators[container] = android.animation.ValueAnimator.ofArgb(
                0xFF444444.toInt(), // Исходный тёмный
                0xFF008800.toInt()  // Зелёный
            ).apply {
                duration = 800
                repeatCount = android.animation.ValueAnimator.INFINITE
                repeatMode = android.animation.ValueAnimator.REVERSE
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()

                addUpdateListener { animation ->
                    val drawable = container.background as? android.graphics.drawable.GradientDrawable
                    if (drawable != null) {
                        drawable.setColor(animation.animatedValue as Int)
                    }
                }
            }
        }
        buttonPulseAnimators[container]?.start()
    }

    private fun stopButtonPulse(container: FrameLayout) {
        buttonPulseAnimators[container]?.cancel()
        val drawable = container.background as? android.graphics.drawable.GradientDrawable
        drawable?.setColor(0xFF444444.toInt())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setContentView(R.layout.activity_settings)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        viewModel = ViewModelProvider(this)[PlaylistViewModel::class.java]

        // ========== ИНИЦИАЛИЗАЦИЯ UI ==========
        val tvStatus = findViewById<TextView>(R.id.tvDeviceStatus)
        val tvInfo = findViewById<TextView>(R.id.tvDeviceInfo)
        val btnDeviceInfo = findViewById<Button>(R.id.btnDeviceInfo)

        containerTestFohL = findViewById(R.id.containerTestFohL)
        containerTestFohR = findViewById(R.id.containerTestFohR)
        containerTestMonL = findViewById(R.id.containerTestMonL)
        containerTestMonR = findViewById(R.id.containerTestMonR)
        containerTestMelody = findViewById(R.id.containerTestMelody)

        spinnerFohL = findViewById(R.id.spinnerFohL)
        spinnerFohR = findViewById(R.id.spinnerFohR)
        spinnerMonL = findViewById(R.id.spinnerMonL)
        spinnerMonR = findViewById(R.id.spinnerMonR)
        spinnerBitDepth = findViewById(R.id.spinnerBitDepth)

        btnTestFohL = findViewById(R.id.btnTestFohL)
        btnTestFohR = findViewById(R.id.btnTestFohR)
        btnTestMonL = findViewById(R.id.btnTestMonL)
        btnTestMonR = findViewById(R.id.btnTestMonR)
        btnTestMelody = findViewById(R.id.btnTestMelody)

        // ========== КНОПКА INFO ==========
        btnDeviceInfo.setOnClickListener {
            showDeviceInfoDialog()
        }

        // ========== СТОП ОСНОВНОГО ТРЕКА ПРИ ВХОДЕ В МЕНЮ ==========
        val stopIntent = Intent("LOCAL_STOP_PLAYBACK")
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(stopIntent)
        Log.d("SETTINGS", "Отправлен запрос на остановку основного трека")

        // ========== ПОЛУЧАЕМ ИНФУ О USB УСТРОЙСТВЕ ==========
        updateUsbInfo(tvStatus, tvInfo)

        // ========== НАСТРОЙКА СПИННЕРА БИТНОСТИ ==========
        setupBitDepthSpinner()

        // ========== НАСТРОЙКА КНОПОК PLAY ==========
        setupTestButtons()
        // Кнопка BACK (размеры в dp через LayoutParams)
        val backButton = Button(this).apply {
            text = "BACK"
            setTextColor(Color.WHITE)
            textSize = 14f
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 40f
                setColor(Color.parseColor("#777777"))
            }
            setOnClickListener { finish() }
        }

        val widthPx = (96 * resources.displayMetrics.density).toInt()   // 96dp
        val heightPx = (35 * resources.displayMetrics.density).toInt()  // 35dp

        val params = FrameLayout.LayoutParams(widthPx, heightPx).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            setMargins(0, 0, (20 * resources.displayMetrics.density).toInt(), (20 * resources.displayMetrics.density).toInt())
        }

        val rootView = findViewById<FrameLayout>(android.R.id.content)
        rootView.addView(backButton, params)
    }

    // ========== ОБНОВЛЕНИЕ ИНФОРМАЦИИ О USB ==========
    private fun updateUsbInfo(tvStatus: TextView, tvInfo: TextView) {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val usbDevice = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }

        if (usbDevice != null) {
            tvStatus.text = "ONLINE"
            tvStatus.setTextColor(Color.GREEN)
            tvInfo.text = "${usbDevice.productName} | ${usbDevice.channelCounts.maxOrNull() ?: 2}ch"
            maxChannels = usbDevice.channelCounts.maxOrNull() ?: 2
            setupChannelSpinners(maxChannels)
        } else {
            tvStatus.text = "OFFLINE"
            tvStatus.setTextColor(Color.parseColor("#888888"))
            tvInfo.text = "No device"
        }
    }

    // ========== НАСТРОЙКА 4 СПИННЕРОВ КАНАЛОВ ==========
    private fun setupChannelSpinners(maxChannels: Int) {
        // UI показывает: "n/a", "1 channel", "2 channel", ...
        val channels = mutableListOf("not assigned")
        for (i in 1..maxChannels) {
            channels.add("$i channel")
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, channels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        spinnerFohL.adapter = adapter
        spinnerFohR.adapter = adapter
        spinnerMonL.adapter = adapter
        spinnerMonR.adapter = adapter

        val prefs = getSharedPreferences("stagemon", MODE_PRIVATE)

        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val usbDevice = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        val deviceName = usbDevice?.productName?.toString() ?: ""
        val keyPrefix = if (deviceName.isNotEmpty()) "routing_${deviceName.replace(" ", "_")}" else ""

        // Читаем позицию спиннера из prefs (0, 1, 2, 3, 4)
        val fohL = if (keyPrefix.isNotEmpty()) prefs.getInt("${keyPrefix}_foh_l", 0) else 0
        val fohR = if (keyPrefix.isNotEmpty()) prefs.getInt("${keyPrefix}_foh_r", 0) else 0
        val monL = if (keyPrefix.isNotEmpty()) prefs.getInt("${keyPrefix}_mon_l", 0) else 0
        val monR = if (keyPrefix.isNotEmpty()) prefs.getInt("${keyPrefix}_mon_r", 0) else 0

        spinnerFohL.setSelection(fohL.coerceIn(0, channels.size - 1))
        spinnerFohR.setSelection(fohR.coerceIn(0, channels.size - 1))
        spinnerMonL.setSelection(monL.coerceIn(0, channels.size - 1))
        spinnerMonR.setSelection(monR.coerceIn(0, channels.size - 1))

        // Слушатели для всех 4 спиннеров
        val spinners = listOf(spinnerFohL, spinnerFohR, spinnerMonL, spinnerMonR)
        val keys = listOf("foh_l", "foh_r", "mon_l", "mon_r")

        for (i in spinners.indices) {
            spinners[i].onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (isUpdatingSpinners) return

                    val fullKey = "${keyPrefix}_${keys[i]}"
                    Log.d("SETTINGS", "$fullKey selected position: $position")

                    // Сохраняем позицию спиннера (0, 1, 2, 3, 4)
                    prefs.edit().putInt(fullKey, position).apply()

                    // Проверяем взаимное исключение
                    enforceExclusiveChannel(spinners[i], position, spinners, keys, keyPrefix, prefs)

                    // Отправляем broadcast в MainActivity
                    sendRoutingBroadcast(keyPrefix, prefs)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
    }

    // ========== ВЗАИМНОЕ ИСКЛЮЧЕНИЕ КАНАЛОВ ==========
    private fun enforceExclusiveChannel(
        changedSpinner: Spinner,
        selectedPosition: Int,
        allSpinners: List<Spinner>,
        shortKeys: List<String>,
        keyPrefix: String,
        prefs: android.content.SharedPreferences
    ) {
        if (selectedPosition == 0) return

        isUpdatingSpinners = true

        for (i in allSpinners.indices) {
            val spinner = allSpinners[i]
            if (spinner === changedSpinner) continue

            if (spinner.selectedItemPosition == selectedPosition) {
                spinner.setSelection(0)
                prefs.edit().putInt("${keyPrefix}_${shortKeys[i]}", -1).apply()
            }
        }

        isUpdatingSpinners = false
    }

    // ========== ОТПРАВКА BROADCAST С МАРШРУТИЗАЦИЕЙ ==========
    private fun sendRoutingBroadcast(keyPrefix: String, prefs: android.content.SharedPreferences) {
        val localIntent = Intent("LOCAL_UPDATE_CHANNEL_ROUTING")
        localIntent.putExtra("foh_l_channel", prefs.getInt("${keyPrefix}_foh_l", 0))
        localIntent.putExtra("foh_r_channel", prefs.getInt("${keyPrefix}_foh_r", 0))
        localIntent.putExtra("mon_l_channel", prefs.getInt("${keyPrefix}_mon_l", 0))
        localIntent.putExtra("mon_r_channel", prefs.getInt("${keyPrefix}_mon_r", 0))
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(localIntent)
        Log.d("SETTINGS", "Отправлен broadcast маршрутизации для $keyPrefix")
    }

    // ========== НАСТРОЙКА СПИННЕРА БИТНОСТИ ==========
    private fun setupBitDepthSpinner() {
        val prefs = getSharedPreferences("stagemon", MODE_PRIVATE)

        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val usbDevice = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }

        if (usbDevice == null) {
            ToastHelper.showCustomToast(this, "No USB device", "warning")
            return
        }

        // ✅ ИСПОЛЬЗУЕМ AudioDeviceInfo.encodings — данные напрямую от USB-драйвера
        val encodings = usbDevice.encodings
        Log.d("SETTINGS", "Device encodings: ${encodings.toList()}")

        val supportedFormats = mutableListOf<kotlin.Pair<Int, String>>()

        for (enc in encodings) {
            when (enc) {
                AudioFormat.ENCODING_PCM_16BIT -> {
                    supportedFormats.add(kotlin.Pair(1, "16-bit PCM"))
                    Log.d("SETTINGS", "✅ 16-bit PCM supported")
                }
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                    supportedFormats.add(kotlin.Pair(2, "24-bit PCM"))
                    Log.d("SETTINGS", "✅ 24-bit PCM supported")
                }
                AudioFormat.ENCODING_PCM_32BIT -> {
                    supportedFormats.add(kotlin.Pair(3, "32-bit PCM"))
                    Log.d("SETTINGS", "✅ 32-bit PCM supported")
                }
                AudioFormat.ENCODING_PCM_FLOAT -> {
                    supportedFormats.add(kotlin.Pair(4, "32-bit Float"))
                    Log.d("SETTINGS", "✅ 32-bit Float supported")
                }
            }
        }

        Log.d("SETTINGS", "Supported formats count: ${supportedFormats.size}")

        if (supportedFormats.isEmpty()) {
            ToastHelper.showCustomToast(this, "No supported formats found!", "error")
            return
        }

        val formatNames = supportedFormats.map { it.second }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, formatNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerBitDepth.adapter = adapter

        val savedFormatCode = prefs.getInt("output_format_code", -1)
        val selectedIndex = if (savedFormatCode > 0) {
            val index = supportedFormats.indexOfFirst { it.first == savedFormatCode }
            if (index >= 0) index else supportedFormats.size - 1
        } else {
            supportedFormats.size - 1
        }
        spinnerBitDepth.setSelection(selectedIndex.coerceAtLeast(0))

        spinnerBitDepth.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedCode = supportedFormats[position].first
                val selectedName = supportedFormats[position].second
                Log.d("SETTINGS", "BitDepth selected: $selectedName (code=$selectedCode)")
                prefs.edit().putInt("output_format_code", selectedCode).apply()

                if (currentPlayingButton === btnTestMelody) {
                    stopAllTestSounds()
                    ToastHelper.showCustomToast(this@SettingsActivity, "Мелодия остановлена", "info")
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // ========== НАСТРОЙКА КНОПОК PLAY (TOGGLE-ЛОГИКА) ==========
    private fun setupTestButtons() {
        val buttons = listOf(btnTestFohL, btnTestFohR, btnTestMonL, btnTestMonR, btnTestMelody)
        val containers = listOf(containerTestFohL, containerTestFohR, containerTestMonL, containerTestMonR, containerTestMelody)
        val labels = listOf("FOH L", "FOH R", "MON L", "MON R", "Melody")

        for (i in buttons.indices) {
            buttons[i].setOnClickListener {
                val clickedButton = buttons[i]
                val clickedContainer = containers[i]

                if (currentPlayingButton === clickedButton) {
                    // Тап по той же кнопке — стоп
                    stopAllTestSounds()
                    Log.d("SETTINGS", "Stopped test: ${labels[i]}")
                } else {
                    // Тап по другой кнопке — стопить текущую, запустить новую
                    stopAllTestSounds()
                    currentPlayingButton = clickedButton
                    playTestSound(labels[i], clickedButton, clickedContainer)
                    Log.d("SETTINGS", "Started test: ${labels[i]}")
                }
            }
        }
    }

    // ========== ВОСПРОИЗВЕДЕНИЕ ТЕСТОВОГО ЗВУКА ==========
    private fun playTestSound(label: String, button: ImageButton, container: FrameLayout) {
        // Проверка 1: есть ли USB-карта
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val usbDevice = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }

        if (usbDevice == null) {
            ToastHelper.showCustomToast(this, "No USB device connected", "warning")
            return
        }

        // Проверка 2: назначен ли хотя бы один канал
        val deviceName = usbDevice.productName?.toString() ?: ""
        val keyPrefix = if (deviceName.isNotEmpty()) "routing_${deviceName.replace(" ", "_")}" else ""
        val prefs = getSharedPreferences("stagemon", MODE_PRIVATE)

        val fohL = prefs.getInt("${keyPrefix}_foh_l", -1)
        val fohR = prefs.getInt("${keyPrefix}_foh_r", -1)
        val monL = prefs.getInt("${keyPrefix}_mon_l", -1)
        val monR = prefs.getInt("${keyPrefix}_mon_r", -1)

        if (fohL == 0 && fohR == 0 && monL == 0 && monR == 0) {
            ToastHelper.showCustomToast(this, "Please assign channels first", "info")
            return
        }

        // Меняем иконку на Stop (красный)
        button.setImageResource(R.drawable.ic_stop)

        // Запускаем зелёную пульсацию
        startButtonPulse(container)

        // TODO: Здесь будет код для воспроизведения тестового WAV через движок
        Log.d("SETTINGS", "Playing test: $label")
    }

    // ========== ОСТАНОВКА ВСЕХ ТЕСТОВЫХ ЗВУКОВ ==========
    private fun stopAllTestSounds() {
        // Сбрасываем все кнопки в Play
        btnTestFohL.setImageResource(R.drawable.ic_play)
        btnTestFohR.setImageResource(R.drawable.ic_play)
        btnTestMonL.setImageResource(R.drawable.ic_play)
        btnTestMonR.setImageResource(R.drawable.ic_play)
        btnTestMelody.setImageResource(R.drawable.ic_play)

        // Останавливаем все пульсации
        stopButtonPulse(containerTestFohL)
        stopButtonPulse(containerTestFohR)
        stopButtonPulse(containerTestMonL)
        stopButtonPulse(containerTestMonR)
        stopButtonPulse(containerTestMelody)

        currentPlayingButton = null

        // TODO: Здесь будет код для остановки тестового звука в движке
        Log.d("SETTINGS", "All test sounds stopped")
    }

    // ========== ДИАЛОГ С ИНФОРМАЦИЕЙ ОБ УСТРОЙСТВЕ ==========


    private fun showDeviceInfoDialog() {
        prefs.edit().clear().apply()
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val usbDevice = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }

        if (usbDevice == null) {
            ToastHelper.showCustomToast(this, "No USB device connected", "warning")
            return
        }

        // ========== СОБИРАЕМ БАЗОВУЮ ИНФУ ==========
        val deviceName = usbDevice.productName ?: "Unknown"
        val deviceId = usbDevice.id

        // ========== Узнаём Vid/Pid ==========
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList: HashMap<String, UsbDevice> = usbManager.deviceList
        var vid = -1
        var pid = -1
        val audioProduct = usbDevice.productName?.toString() ?: ""
        for (dev in deviceList.values) {
            val usbProduct = dev.productName ?: ""
            if (usbProduct.isNotEmpty() && audioProduct.contains(usbProduct)) {
                vid = dev.vendorId
                pid = dev.productId
                break
            }
        }
        val vidPidText = if (vid >= 0 && pid >= 0) {
            "VID: 0x${vid.toString(16).uppercase().toString().padStart(4, '0')} | PID: 0x${pid.toString(16).uppercase().toString().padStart(4, '0')}"
        } else {
            "VID/PID: N/A"
        }

        // ========== КОНТЕЙНЕРЫ ДЛЯ РЕЗУЛЬТАТОВ ==========
        val channelCounts = usbDevice.channelCounts
        val channelsText = if (channelCounts.isNotEmpty()) {
            channelCounts.sorted().joinToString(", ") { "${it}ch" }
        } else {
            "Not reported"
        }

        // ========== СОЗДАЁМ ДИАЛОГ ==========
        val scrollView = ScrollView(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_device_info, scrollView, false)
        scrollView.addView(dialogView)

        val tvName = dialogView.findViewById<TextView>(R.id.tvInfoDeviceName)
        val tvId = dialogView.findViewById<TextView>(R.id.tvInfoDeviceId)
        val tvChannels = dialogView.findViewById<TextView>(R.id.tvInfoChannels)
        val tvEncodings = dialogView.findViewById<TextView>(R.id.tvInfoEncodings)
        val tvSampleRates = dialogView.findViewById<TextView>(R.id.tvInfoSampleRates)

        tvName.text = deviceName
        tvId.text = "ID: $deviceId | $vidPidText"
        tvChannels.text = channelsText
        tvEncodings.text = "⏳ Проверяем форматы..."
        tvSampleRates.text = "⏳ Проверяем частоты..."

// ========== КНОПКА "ПЕРЕПРОВЕРИТЬ" ==========
        val btnReprobe = Button(this).apply {
            text = "REPROBE"
            setTextColor(Color.WHITE)
            textSize = 12f
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f
                setColor(Color.parseColor("#555555"))
            }
            setPadding(32, 8, 32, 8)
        }

        val btnContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 16)
            addView(btnReprobe)
        }
        (dialogView as ViewGroup).addView(btnContainer)

        val dialog = AlertDialog.Builder(this)
            .setView(scrollView)
            .setPositiveButton("CLOSE", null)
            .create()

        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 40f
                setColor(0xFF1E1E1E.toInt())
            }
        )

        dialog.show()

        // ========== ФУНКЦИЯ ОБНОВЛЕНИЯ UI ==========
        fun updateResults(formats: List<Int>, rates: List<Int>) {
            // Форматы
            val formatNames = formats.map { code ->
                when (code) {
                    1 -> "• 16-bit PCM"
                    2 -> "• 24-bit PCM (packed)"
                    3 -> "• 32-bit PCM"
                    4 -> "• 32-bit Float"
                    else -> "• Unknown ($code)"
                }
            }
            tvEncodings.text = if (formatNames.isNotEmpty()) {
                formatNames.joinToString("\n")
            } else {
                "No formats supported!"
            }

            // Частоты
            tvSampleRates.text = if (rates.isNotEmpty()) {
                rates.sorted().joinToString(", ") { "${it} Hz" }
            } else {
                "No rates supported!"
            }
        }

        // ========== ЗАГРУЗКА ИЗ КЭША ИЛИ ПРОВЕРКА ==========
        if (vid >= 0 && pid >= 0) {
            getDeviceInfoWithCache(deviceId, deviceName.toString(), vid, pid, forceProbe = false) { formats, rates ->
                updateResults(formats, rates)
            }
        } else {
            tvEncodings.text = "VID/PID unknown — cannot cache"
            tvSampleRates.text = "VID/PID unknown — cannot cache"
        }

        // ========== КНОПКА ПЕРЕПРОВЕРКИ ==========
        btnReprobe.setOnClickListener {
            tvEncodings.text = "⏳ Перепроверяем форматы..."
            tvSampleRates.text = "⏳ Перепроверяем частоты..."

            if (vid >= 0 && pid >= 0) {
                getDeviceInfoWithCache(deviceId, deviceName.toString(), vid, pid, forceProbe = true) { formats, rates ->
                    updateResults(formats, rates)
                    ToastHelper.showCustomToast(this@SettingsActivity, "Reprobe complete", "success")
                }
            }
        }
    }

    // ========== КЭШ РЕЗУЛЬТАТОВ ПРОВЕРКИ ==========

    data class DeviceCache(
        val name: String,
        val formats: List<Int>,   // коды форматов: 1=I16, 2=I24, 3=I32, 4=Float
        val rates: List<Int>      // частоты в Гц
    )

    private fun getCacheKey(vid: Int, pid: Int): String {
        return "${vid.toString(16)}_${pid.toString(16)}"
    }

    private fun loadDeviceCache(vid: Int, pid: Int): DeviceCache? {
        val key = getCacheKey(vid, pid)
        val json = prefs.getString("device_$key", null) ?: return null
        return try {
            gson.fromJson(json, DeviceCache::class.java)
        } catch (e: Exception) {
            null
        }
    }

    private fun saveDeviceCache(vid: Int, pid: Int, name: String, formats: List<Int>, rates: List<Int>) {
        val key = getCacheKey(vid, pid)
        val cache = DeviceCache(name, formats, rates)
        val json = gson.toJson(cache)
        prefs.edit().putString("device_$key", json).apply()
        Log.d("DEVICE_CACHE", "Saved cache for $key: $json")
    }

// ========== ПРОВЕРКА ПОДДЕРЖКИ ФОРМАТОВ ==========

    private fun probeDeviceFormats(deviceId: Int, onResult: (List<Int>, List<Int>) -> Unit) {
        Log.d("FORMAT_PROBE", "=== Using AudioDeviceInfo (reliable method) ===")

        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val usbDevice = devices.firstOrNull {
            it.id == deviceId &&
                    (it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                            it.type == AudioDeviceInfo.TYPE_USB_HEADSET)
        }

        if (usbDevice == null) {
            Log.e("FORMAT_PROBE", "USB device not found!")
            onResult(emptyList(), emptyList())
            return
        }

        // ========== РЕАЛЬНЫЕ КОДИРОВКИ ОТ УСТРОЙСТВА ==========
        val encodings = usbDevice.encodings
        val supportedFormats = mutableListOf<Int>()

        Log.d("FORMAT_PROBE", "Raw encodings from device: ${encodings.toList()}")

        // AudioFormat.ENCODING_DEFAULT = 1
        // AudioFormat.ENCODING_PCM_16BIT = 2
        // AudioFormat.ENCODING_PCM_8BIT = 3
        // AudioFormat.ENCODING_PCM_FLOAT = 4
        // AudioFormat.ENCODING_PCM_24BIT_PACKED = 6  ← 24-bit в 3-байтном формате
        // AudioFormat.ENCODING_PCM_32BIT = 7 (API 31+)
        // AudioFormat.ENCODING_PCM_24BIT = 23 (API 34+, 24-bit в 4-байтном контейнере)

        for (enc in encodings) {
            when (enc) {
                AudioFormat.ENCODING_PCM_16BIT -> {
                    if (!supportedFormats.contains(1)) supportedFormats.add(1)
                    Log.d("FORMAT_PROBE", "✅ 16-bit PCM supported")
                }
                AudioFormat.ENCODING_PCM_FLOAT -> {
                    if (!supportedFormats.contains(2)) supportedFormats.add(2)
                    Log.d("FORMAT_PROBE", "✅ 32-bit Float supported")
                }
                6 -> { // ENCODING_PCM_24BIT_PACKED
                    if (!supportedFormats.contains(3)) supportedFormats.add(3)
                    Log.d("FORMAT_PROBE", "✅ 24-bit packed supported")
                }
                23 -> { // ENCODING_PCM_24BIT (в 4-байтном контейнере, API 34+)
                    if (!supportedFormats.contains(3)) supportedFormats.add(3)
                    Log.d("FORMAT_PROBE", "✅ 24-bit in 32-bit container supported")
                }
                7 -> { // ENCODING_PCM_32BIT (API 31+)
                    if (!supportedFormats.contains(4)) supportedFormats.add(4)
                    Log.d("FORMAT_PROBE", "✅ 32-bit Integer supported")
                }
                else -> {
                    Log.d("FORMAT_PROBE", "Unknown encoding: $enc")
                }
            }
        }

        // ========== РЕАЛЬНЫЕ ЧАСТОТЫ ОТ УСТРОЙСТВА ==========
        val sampleRates = usbDevice.sampleRates
        val supportedRates = sampleRates.toList().sorted()

        Log.d("FORMAT_PROBE", "Supported sample rates: $supportedRates")

        Log.d("FORMAT_PROBE", "=== Probe complete: formats=$supportedFormats, rates=$supportedRates ===")

        onResult(supportedFormats, supportedRates)
    }

// ========== ПОЛУЧЕНИЕ ИНФЫ О КАРТЕ (С КЭШЕМ) ==========

    private fun getDeviceInfoWithCache(
        deviceId: Int,
        deviceName: String,
        vid: Int,
        pid: Int,
        forceProbe: Boolean,
        onResult: (List<Int>, List<Int>) -> Unit
    ) {
        // ✅ ВСЕГДА проверяем форматы, игнорируем кэш!
        Log.d("DEVICE_CACHE", "Probing device formats (cache disabled)...")

        probeDeviceFormats(deviceId) { formats, rates ->
            // Сохраняем в кэш для совместимости (но не используем)
            saveDeviceCache(vid, pid, deviceName, formats, rates)

            Log.d("DEVICE_CACHE", "Probed formats: $formats, rates: $rates")
            onResult(formats, rates)
        }
    }


    override fun onPause() {
        super.onPause()
        // Останавливаем тестовые звуки при выходе
        stopAllTestSounds()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAllTestSounds()
    }
}