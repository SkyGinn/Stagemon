package com.example.stagemon

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.view.MotionEvent
import androidx.lifecycle.ViewModelProvider
import android.text.TextUtils
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.widget.ImageButton
import android.text.Html
import java.io.FileInputStream


class MainActivity : AppCompatActivity() {
    private val OPEN_TREE_REQUEST_CODE = 1001

    private val scope = MainScope()
    private val gson = Gson()
    private val handler = Handler(Looper.getMainLooper())
    private var splashShown = false
    private val usbDevice: AudioDeviceInfo?
        get() {
            return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_DEVICE }
        }

    // ========== НОВЫЕ ЭЛЕМЕНТЫ HELP ==========
    private lateinit var addLeft: Button                // +WAV (левый)
    private lateinit var addRight: Button               // +WAV (правый)
    private lateinit var btnMetrSet: Button             // кнопка "Metr Setings" (ранее btnCustomR)
    private lateinit var btnTimeScreen: MaterialButton  // кнопка "TS"
    private lateinit var btnHelp: MaterialButton        // кнопка "HELP"
    private lateinit var btnMetr: Button                // кнопка "Metronom"
    private lateinit var volumeMetr: SeekBar            // слайдер метронома
    private lateinit var volumeMetrText: TextView       // текст процента метронома
    private lateinit var helpOverlay: View              // оверлей с подсказками
    private var isMetronomeGlobalEnabled = false

    // ========== ДЕКИ, ПАРЫ, СПИСКИ (СТАРЫЕ) ==========

    private var currentPairIndex = 0
    private var defaultBehavior: String = ""

    private var isPlaying = false
    private var isFlowMode = false
    private lateinit var backgroundAnimation: LottieAnimationView
    private lateinit var viewModel: PlaylistViewModel
   // private lateinit var waveformSeekBar: com.masoudss.lib.WaveformSeekBar

    private var playbackMode = "single"
    private lateinit var btnPlaybackMode: Button
    private lateinit var tvNowPlaying: TextView

    // ========== АУДИОДВИЖОК OBOE (ИЗ НОВОГО) ==========
    private var enginePtr: Long = 0L
    private var usbDeviceId = -1
    private var usbDeviceName = ""
    private var maxChannels = 0
    private lateinit var audioManager: AudioManager
    private lateinit var prefs: SharedPreferences
    private var usbDeviceCallback: AudioDeviceCallback? = null
    private var leftPfd: android.os.ParcelFileDescriptor? = null
    private var rightPfd: android.os.ParcelFileDescriptor? = null

    // ========== UI ЭЛЕМЕНТЫ ==========
    private lateinit var tvPairName: TextView
    private lateinit var tvPlaylistName: TextView
    private lateinit var tvUsbStatus: TextView
    private lateinit var btnFlow: Button
    private lateinit var btnMenu: Button
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPlayStop: Button
    private lateinit var btnFohAdd: Button
    private lateinit var btnMonAdd: Button
    private lateinit var seekFoh: SeekBar
    private lateinit var seekMon: SeekBar
    private lateinit var tvVolFoh: TextView
    private lateinit var tvVolMon: TextView
    private lateinit var tvCountdown: TextView
    private val countdownHandler = Handler(Looper.getMainLooper())
    private var isCountdownRunning = false
    private val autoScrollHandler = Handler(Looper.getMainLooper())
    private var autoScrollRunnable: Runnable? = null
    private var autoScrollDirection = 0
    private val autoScrollDelay = 100L
    private lateinit var tvPairNumber: TextView
    private var backPressedTime = 0L
    private lateinit var btnAddWav: Button

    private var isSyncing = false
    private var isLeftScrolling = false
    private var isRightScrolling = false
    private lateinit var btnStageView: Button
    // ========== МЕТРОНОМ ==========
    private lateinit var metronomeManager: MetronomeManager
    private lateinit var tvSongTempo: TextView

    // ========== VU-МЕТРЫ FOH ==========
    private lateinit var vuFohLeftContainer: LinearLayout
    private lateinit var vuFohRightContainer: LinearLayout
    private var smoothFohLeftDb = -60f
    private var smoothFohRightDb = -60f

    // ========== VU-МЕТРЫ MON ==========
    private lateinit var vuMonLeftContainer: LinearLayout
    private lateinit var vuMonRightContainer: LinearLayout
    private var smoothMonLeftDb = -60f
    private var smoothMonRightDb = -60f
    private lateinit var leftRecyclerView: RecyclerView
    private lateinit var rightRecyclerView: RecyclerView
    private lateinit var leftAdapter: TrackAdapter
    private lateinit var rightAdapter: TrackAdapter
    private var isStopping = false
    private var stopFadeLeft = 0f
    private var stopFadeRight = 0f
    private var stopFadeLeftMon = 0f
    private var stopFadeRightMon = 0f
    private var stopRequested = false
    private var isUsbDisconnected = false
    private var pendingResume = false
    private var savedPosition = 0L


    private val DB_LEVELS = floatArrayOf(
        6f, 5f, 4f, 3f, 2f, 1f, 0f, -1f,
        -2f, -3f, -4f, -5f, -6f,
        -7f, -8f, -9f, -10f, -11f, -12f, -13f, -14f, -15f,
        -16f, -17f, -18f, -19f, -20f, -22f, -25f, -30f
    )

    private var isMetronomeEnabled = false

    private fun startMetronome() {
      //  metronomeManager.songUpdate(metronomeManager.currentBpm.value ?: 120, true)
    }

    private fun stopMetronome() {
       // metronomeManager.songUpdate(120, false)
    }


    private fun syncWithSong() {
        // В MetronomeManager нет метода reset!
        // Просто синхронизируем через songUpdate или ничего не делаем
        val currentPair = viewModel.pairs?.getOrNull(currentPairIndex)
        currentPair?.bpm?.toIntOrNull()?.let { bpm ->
         //   metronomeManager.songUpdate(bpm, metronomeManager.isGloballyEnabled.value == true)
        }
    }
    data class FileEntry(val fileName: String, val uri: Uri)
    private fun updateVuFoh(leftDb: Float, rightDb: Float) {
        if (!::vuFohLeftContainer.isInitialized || !::vuFohRightContainer.isInitialized) return

        smoothFohLeftDb = smoothFohLeftDb * 0.8f + leftDb * 0.2f
        smoothFohRightDb = smoothFohRightDb * 0.8f + rightDb * 0.2f

        for (i in 0 until vuFohLeftContainer.childCount) {
            val segment = vuFohLeftContainer.getChildAt(i)
            val segmentDb = DB_LEVELS[i]

            if (smoothFohLeftDb >= segmentDb) {
                when {
                    segmentDb >= -1f -> segment.setBackgroundColor(Color.RED)
                    segmentDb >= -6f -> segment.setBackgroundColor(Color.YELLOW)
                    else -> segment.setBackgroundColor(Color.GREEN)
                }
            } else {
                segment.setBackgroundColor(0xFF222222.toInt())
            }
        }

        for (i in 0 until vuFohRightContainer.childCount) {
            val segment = vuFohRightContainer.getChildAt(i)
            val segmentDb = DB_LEVELS[i]

            if (smoothFohRightDb >= segmentDb) {
                when {
                    segmentDb >= -1f -> segment.setBackgroundColor(Color.RED)
                    segmentDb >= -6f -> segment.setBackgroundColor(Color.YELLOW)
                    else -> segment.setBackgroundColor(Color.GREEN)
                }
            } else {
                segment.setBackgroundColor(0xFF222222.toInt())
            }
        }
    }

    private fun startCountdownWithDelay(seconds: Int, onFinish: () -> Unit) {
        if (isCountdownRunning) return

        isCountdownRunning = true
        var remaining = seconds

        tvCountdown.text = remaining.toString()
        tvCountdown.visibility = View.VISIBLE

        val countdownRunnable = object : Runnable {
            override fun run() {
                remaining--
                if (remaining > 0) {
                    tvCountdown.text = remaining.toString()
                    countdownHandler.postDelayed(this, 1000)
                } else {
                    tvCountdown.visibility = View.GONE
                    isCountdownRunning = false
                    onFinish.invoke()
                }
            }
        }

        countdownHandler.postDelayed(countdownRunnable, 1000)
    }

    private fun playNextInFlow() {
        Log.d("FLOW", "playNextInFlow() called, current index: $currentPairIndex")

        var next = currentPairIndex + 1
        while (next < 99) {
            if (viewModel.pairs!![next].isValid()) {
                currentPairIndex = next
                refreshAll()
                isStopping = false
                stopRequested = false
                playCurrentPair()
                return
            }
            next++
        }
        Log.d("FLOW", "End of playlist reached")
        showEndOfPlaylist()
        isFlowMode = false
        btnFlow.backgroundTintList = ContextCompat.getColorStateList(this, R.color.white)
    }

    private fun updateVuMon(leftDb: Float, rightDb: Float) {
        if (!::vuMonLeftContainer.isInitialized || !::vuMonRightContainer.isInitialized) return

        smoothMonLeftDb = smoothMonLeftDb * 0.8f + leftDb * 0.2f
        smoothMonRightDb = smoothMonRightDb * 0.8f + rightDb * 0.2f

        for (i in 0 until vuMonLeftContainer.childCount) {
            val segment = vuMonLeftContainer.getChildAt(i)
            val segmentDb = DB_LEVELS[i]

            if (smoothMonLeftDb >= segmentDb) {
                when {
                    segmentDb >= -1f -> segment.setBackgroundColor(Color.RED)
                    segmentDb >= -6f -> segment.setBackgroundColor(Color.YELLOW)
                    else -> segment.setBackgroundColor(Color.GREEN)
                }
            } else {
                segment.setBackgroundColor(0xFF222222.toInt())
            }
        }

        for (i in 0 until vuMonRightContainer.childCount) {
            val segment = vuMonRightContainer.getChildAt(i)
            val segmentDb = DB_LEVELS[i]

            if (smoothMonRightDb >= segmentDb) {
                when {
                    segmentDb >= -1f -> segment.setBackgroundColor(Color.RED)
                    segmentDb >= -6f -> segment.setBackgroundColor(Color.YELLOW)
                    else -> segment.setBackgroundColor(Color.GREEN)
                }
            } else {
                segment.setBackgroundColor(0xFF222222.toInt())
            }
        }
    }

    init {
        System.loadLibrary("stagemon")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private lateinit var focusRequest: AudioFocusRequest

    @SuppressLint("SourceLockedOrientationActivity")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        prefs = getSharedPreferences("stagemon", MODE_PRIVATE)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        enginePtr = createEngine()
        Log.d("MAIN", "enginePtr = $enginePtr")

        // ========== ЭТА СТРОКА ДОЛЖНА БЫТЬ! ==========
        metronomeManager = MetronomeManager.getInstance(this)
        metronomeManager.setEnginePtr(enginePtr)
        Log.d("MAIN", "enginePtr set to MetronomeManager")
        // =============================================

        viewModel = ViewModelProvider(this)[PlaylistViewModel::class.java]

        // Наблюдай за изменениями:
        viewModel.pairsLive.observe(this) { newPairs ->
            leftAdapter.updatePairs(newPairs)
            rightAdapter.updatePairs(newPairs)
            refreshAll()
        }

        viewModel.currentPairIndex.observe(this) { index ->
            currentPairIndex = index
            refreshAll()
        }

        viewModel.playlistName.observe(this) { name ->
            tvPlaylistName.text = name
        }

        // При загрузке данных передай их в ViewModel:
        viewModel.loadFromIntent(
            pairsJson = intent.getStringExtra("pairs"),
            behavior = intent.getStringExtra("default_behavior"),
            name = intent.getStringExtra("playlist_name")
        )

        ensureFoldersExist()
        showMainScreen()

        lastLoadedPlaylist = prefs.getString("last_loaded_playlist", "") ?: ""
        Log.d("START_DEBUG", "lastLoadedPlaylist из prefs: '$lastLoadedPlaylist'")

        if (lastLoadedPlaylist.isNotEmpty()) {
            Log.d("START_DEBUG", "Пытаемся загрузить: $lastLoadedPlaylist")
            loadNamedList(lastLoadedPlaylist)
        } else {
            Log.d("START_DEBUG", "Нет последнего плейлиста, ставим NoNameList")
            defaultBehavior = "I"
            viewModel.updateDefaultBehavior("I")
            updatePlaylistName("NoNameList")
        }

        val splashView = View(this).apply {
            setBackgroundResource(R.drawable.splash)
            isClickable = true
        }

        val rootView = window.decorView as FrameLayout
        rootView.addView(splashView)

        handler.postDelayed({
            splashView.animate()
                .alpha(0f)
                .setDuration(1000)
                .withEndAction {
                    rootView.removeView(splashView)
                    splashShown = true
                }
                .start()
        }, 1100)
        val filter = IntentFilter().apply {
            addAction("LOCAL_UPDATE_CHANNEL_ROUTING")
            addAction("LOCAL_STOP_PLAYBACK")
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(localReceiver, filter)
        Log.d("MainActivity", "✅ LocalReceiver registered in onCreate")

    }


    private fun startAutoScroll(direction: Int) {
        stopAutoScroll()
        autoScrollDirection = direction

        autoScrollRunnable = object : Runnable {
            override fun run() {
                if (direction == -1 && currentPairIndex > 0) {
                    currentPairIndex--
                } else if (direction == 1 && currentPairIndex < 98) {
                    currentPairIndex++
                } else {
                    stopAutoScroll()
                    return
                }

                if (isPlaying) {
                    stopPlayback()
                }
                refreshAll()
                viewModel.updateCurrentPairIndex(currentPairIndex)

                autoScrollHandler.postDelayed(this, autoScrollDelay)
            }
        }

        autoScrollHandler.postDelayed(autoScrollRunnable!!, autoScrollDelay)
    }

    private fun stopAutoScroll() {
        autoScrollRunnable?.let {
            autoScrollHandler.removeCallbacks(it)
        }
        autoScrollRunnable = null
        autoScrollDirection = 0
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1003 && resultCode == RESULT_OK) {  // 1003 - код для SettingsActivity
            updateRoutingFromSettings()
        }

        if (requestCode == OPEN_TREE_REQUEST_CODE && resultCode == RESULT_OK) {
            data?.data?.let { treeUri ->
                contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                prefs.edit().putString("root_tree_uri", treeUri.toString()).apply()

                val rootDoc = DocumentFile.fromTreeUri(this, treeUri)
                var playlistsFolder = rootDoc?.findFile("_PlayLists") as? DocumentFile
                if (playlistsFolder == null || !playlistsFolder.exists()) {
                    playlistsFolder = rootDoc?.createDirectory("_PlayLists")
                }
                if (playlistsFolder != null) {
                    prefs.edit().putString("playlists_folder_uri", playlistsFolder.uri.toString())
                        .apply()
                }
                ToastHelper.showCustomToast(this, "Folder selected", "success")
            }
        }

        if (requestCode == 1002 && resultCode == RESULT_OK) {
            val pairsJson = data?.getStringExtra("pairs")
            val behavior = data?.getStringExtra("default_behavior") ?: ""
            val playlistName = data?.getStringExtra("playlist_name") ?: ""

            if (isPlaying) stopPlayback()

            viewModel.loadFromIntent(pairsJson, behavior, playlistName)

            defaultBehavior = behavior
            if (playlistName.isNotEmpty()) {
                updatePlaylistName(playlistName)
                lastLoadedPlaylist = playlistName
            }
            refreshAll()
        }
    }

    private fun showEndOfPlaylist() {
        tvCountdown.text = "END OF PLAYLIST"
        tvCountdown.visibility = View.VISIBLE

        // Убираем автоматическое скрытие через 3 секунды
        // countdownHandler.postDelayed({
        //     tvCountdown.visibility = View.GONE
        // }, 3000)

        // Вешаем слушатель на тап
        tvCountdown.setOnClickListener {
            tvCountdown.visibility = View.GONE
            tvCountdown.setOnClickListener(null)  // убираем слушатель после скрытия
        }

        // if (isFlowMode) {
        //     isFlowMode = false
        //     btnFlow.backgroundTintList = ContextCompat.getColorStateList(this, R.color.white)
        // }
    }

    private fun ensureFoldersExist() {
        val treeUriString = prefs.getString("root_tree_uri", null)
        if (treeUriString == null) {
            handler.postDelayed({ openFolderPicker() }, 1000)
            return
        }

        val treeUri = Uri.parse(treeUriString)
        val rootDoc = DocumentFile.fromTreeUri(this, treeUri)

        if (rootDoc == null || !rootDoc.exists()) {
            prefs.edit().clear().apply()
            handler.postDelayed({ openFolderPicker() }, 1000)
            return
        }

        var playlistsFolder = rootDoc.findFile("_PlayLists") as? DocumentFile
        if (playlistsFolder == null || !playlistsFolder.exists()) {
            playlistsFolder = rootDoc.createDirectory("_PlayLists")
        }

        if (playlistsFolder != null) {
            prefs.edit().putString("root_tree_uri", treeUri.toString()).apply()
            prefs.edit().putString("playlists_folder_uri", playlistsFolder.uri.toString()).apply()
        }
    }

    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        startActivityForResult(intent, OPEN_TREE_REQUEST_CODE)
    }

    private fun updateChannelRouting() {
        if (enginePtr == 0L || usbDeviceName.isEmpty()) return

        val prefs = getSharedPreferences("stagemon", MODE_PRIVATE)
        val keyPrefix = "routing_${usbDeviceName.replace(" ", "_")}"

        // Читаем позицию спиннера (0, 1, 2, 3, 4)
        val fohL_pos = prefs.getInt("${keyPrefix}_foh_l", 0)
        val fohR_pos = prefs.getInt("${keyPrefix}_foh_r", 0)
        val monL_pos = prefs.getInt("${keyPrefix}_mon_l", 0)
        val monR_pos = prefs.getInt("${keyPrefix}_mon_r", 0)

        // Преобразуем позицию спиннера в индекс массива
        // Позиция 0 = "n/a" → индекс -1 (не используется)
        // Позиция 1 = "1 channel" → индекс 0
        // Позиция 2 = "2 channel" → индекс 1
        val fohL = if (fohL_pos == 0) -1 else fohL_pos - 1
        val fohR = if (fohR_pos == 0) -1 else fohR_pos - 1
        val monL = if (monL_pos == 0) -1 else monL_pos - 1
        val monR = if (monR_pos == 0) -1 else monR_pos - 1

        Log.d("MAIN", "Routing: FOH L=$fohL, R=$fohR, MON L=$monL, R=$monR")

        setChannelRouting(enginePtr, fohL, fohR, monL, monR)
    }

    private fun showMainScreen() {
        if (splashShown) return
        splashShown = true

        setContentView(R.layout.activity_main)

        val prevPair = findViewById<ImageButton>(R.id.prevPair)
        val nextPair = findViewById<ImageButton>(R.id.nextPair)


        initViews()
        backgroundAnimation = findViewById(R.id.backgroundAnimation)
        applyRoundedCorners()


        setupDecks()
        setupVolumeControls()
        setupUsbCallback()
        setupListeners()
        refreshAll()
        checkUSB()
    }

    private fun hasNextTrack(): Boolean {
        var next = currentPairIndex + 1
        while (next < 99) {
            if (viewModel.pairs!![next].isValid()) {
                return true
            }
            next++
        }
        return false
    }

    private fun initViews() {
        leftRecyclerView = findViewById(R.id.leftDeckList)
        rightRecyclerView = findViewById(R.id.rightDeckList)
        tvPairName = findViewById(R.id.currentPairName)
        tvNowPlaying = findViewById(R.id.tvNowPlaying)
        tvUsbStatus = findViewById(R.id.usbStatus)
        btnFlow = findViewById(R.id.flowButton)
        btnMenu = findViewById(R.id.menuButton)
        btnPrev = findViewById(R.id.prevPair)
        btnNext = findViewById(R.id.nextPair)
        btnPlayStop = findViewById(R.id.playStop)
        seekFoh = findViewById(R.id.volumeFoh)
        seekMon = findViewById(R.id.volumeMon)
        tvVolFoh = findViewById(R.id.volumeFohText)
        tvVolMon = findViewById(R.id.volumeMonText)
        tvCountdown = findViewById(R.id.tvCountdown)
        tvPlaylistName = findViewById(R.id.currentPlaylistName)
        tvPairNumber = findViewById(R.id.currentPairNumber)
        tvPairName = findViewById(R.id.currentPairName)

        vuFohLeftContainer = findViewById(R.id.vuFohLeftContainer)
        vuFohRightContainer = findViewById(R.id.vuFohRightContainer)
        vuMonLeftContainer = findViewById(R.id.vuMonLeftContainer)
        vuMonRightContainer = findViewById(R.id.vuMonRightContainer)
        //waveformSeekBar = findViewById(R.id.waveformSeekBar)
        btnPlaybackMode = findViewById(R.id.btnPlaybackMode)
        btnPlaybackMode.setOnClickListener { togglePlaybackMode() }
        btnPlaybackMode = findViewById(R.id.btnPlaybackMode)
        btnPlaybackMode.setOnClickListener { togglePlaybackMode() }
        updatePlaybackModeButton()  // БУДЕТ SINGLE (БЕЛЫЙ)
        tvPairName.setOnClickListener {
        }
        tvPairName.setOnLongClickListener {
            val pair = viewModel.pairs?.getOrNull(currentPairIndex)
                ?: return@setOnLongClickListener true
            DialogHelper.showEditSongDialog(
                this,
                currentPairIndex,
                pair,
                true
            ) { pos, newName, newBehavior ->
                viewModel.updatePairName(pos, newName)
                viewModel.updatePairBehavior(pos, newBehavior)
                refreshAll()

                // Сохраняем весь плейлист
                val currentName = tvPlaylistName.text.toString()
                if (currentName.isNotEmpty()) {
                    PlaylistSaver.saveNamedList(this, currentName, viewModel, defaultBehavior)
                }
            }
            true
        }

        val dualWaveform = findViewById<DualWaveformView>(R.id.dualWaveform)

        dualWaveform.onScratchActive = { active ->
            if (enginePtr != 0L) {
                setScratchActive(enginePtr, active)
            }
        }

        dualWaveform.onSpeedChange = { speed ->
            Log.d("VARISPEED", "speed=$speed")
            if (enginePtr != 0L) {
                setPlaybackSpeed(enginePtr, speed)
            }
        }

        dualWaveform.onSeek = { progress ->
            Log.d("SCRATCH", "onSeek progress=$progress")
            if (enginePtr != 0L) {
                val fohLength = getFohLength(enginePtr)
                Log.d("SCRATCH", "fohLength=$fohLength")
                val newPosBytes = (progress * fohLength).toLong()
                Log.d("SCRATCH", "newPosBytes=$newPosBytes")
                setPosition(enginePtr, newPosBytes)
            }
        }

        tvPlaylistName.setOnClickListener {
            loadListDialog()  // ← Это твоя существующая функция для списка плейлистов
        }

        tvPlaylistName.setOnLongClickListener {
            openEditorActivity()  // открываем редактор с текущим плейлистом
            true
        }

        tvSongTempo = findViewById(R.id.tvSongTempo)
        btnStageView = findViewById(R.id.btnStageView)
        btnStageView.setOnLongClickListener {
            startActivity(Intent(this, StageActivity::class.java))
            true
        }

        // ===== НОВЫЕ ЭЛЕМЕНТЫ =====
        btnAddWav = findViewById(R.id.btnAddWav)
        btnMetrSet = findViewById(R.id.btnMetrSet)
        btnTimeScreen = findViewById(R.id.btnTimeScreen)         // кнопка "TS"
        btnHelp = findViewById(R.id.btnHelp)                     // кнопка "HELP"
        btnMetr = findViewById(R.id.btnMetr)                     // кнопка "Metronom"
        volumeMetr = findViewById(R.id.volumeMetr)               // слайдер метронома
        volumeMetrText = findViewById(R.id.volumeMetrText)       // текст слайдера
        helpOverlay = findViewById(R.id.helpOverlay)             // оверлей

// Обработчики для новых кнопок


        btnHelp.setOnClickListener {
            if (helpOverlay.visibility == View.VISIBLE) {
                helpOverlay.visibility = View.GONE
            } else {
                helpOverlay.visibility = View.VISIBLE
            }
        }

        helpOverlay.setOnClickListener {
            helpOverlay.visibility = View.GONE
        }

        volumeMetr.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                volumeMetrText.text = "$progress%"
                // здесь позже можно управлять громкостью метронома
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnMetr.setOnClickListener {

        }

        btnMetrSet.setOnClickListener {
            startActivity(Intent(this, MetronomeSettingsActivity::class.java))
        }

        btnTimeScreen.setOnClickListener {

        }

        // ========== ИНИЦИАЛИЗАЦИЯ МЕТРОНОМА ==========
        metronomeManager = MetronomeManager.getInstance(this)

        // Наблюдаем за изменениями метронома
        metronomeManager.isGloballyEnabled.observe(this) { enabled ->
            leftAdapter.updateConfig(
                isFlowMode,
                enabled,
                defaultBehavior
            )  // ← ДОБАВИЛИ defaultBehavior
            rightAdapter.updateConfig(
                isFlowMode,
                enabled,
                defaultBehavior
            ) // ← ДОБАВИЛИ defaultBehavior
            if (enabled) {
                btnMetr.backgroundTintList = ColorStateList.valueOf(Color.GREEN)
            } else {
                btnMetr.backgroundTintList = ColorStateList.valueOf(Color.GRAY)
            }
        }

        metronomeManager.isPlaying.observe(this) {
            updateMetronomeDisplay()
        }

        metronomeManager.currentBpm.observe(this) {
            updateMetronomeDisplay()
        }

        metronomeManager.timeSignature.observe(this) {
            updateMetronomeDisplay()
        }

        // Долгий тап на кнопке Metronom - вкл/выкл
        btnMetr.setOnLongClickListener {
            metronomeManager.toggleGlobalEnabled()
            true
        }

        // Короткий тап ничего не делает (ты просил)
        // btnMetr.setOnClickListener { } - не нужен

        // Обновляем отображение
        updateMetronomeDisplay()

    }
    private fun updateMetronomeDisplay() {
        // Левое поле - BPM
        val currentPair = viewModel.pairs?.getOrNull(currentPairIndex)
        val songBpm = currentPair?.bpm
        val bpmValue = songBpm?.trim()
        tvSongTempo.text = if (!bpmValue.isNullOrEmpty()) {
            Html.fromHtml("<b>$bpmValue</b><br>bpm", Html.FROM_HTML_MODE_LEGACY)
        } else {
            Html.fromHtml("<b>---</b><br>bpm", Html.FROM_HTML_MODE_LEGACY)
        }
    }

    private fun setupDecks() {
        leftAdapter = TrackAdapter(
            viewModel.pairs!!, true, currentPairIndex,
            { position ->
                // === ФИКС: Если играет, сначала останавливаем! ===
                if (isPlaying) stopPlayback()
                // =================================================
                currentPairIndex = position
                rightAdapter.updateCurrentPair(position)
                viewModel.updateCurrentPairIndex(position)
                refreshAll()
            },
            isFlowMode,
            defaultBehavior,
            metronomeManager.isGloballyEnabled.value == true,
            onItemLongClick = { position -> // <--- ЛОГИКА ТОЛЬКО ДЛЯ ЛЕВОЙ ДЕКИ
                val pair = viewModel.pairs?.getOrNull(position) ?: return@TrackAdapter
                DialogHelper.showEditSongDialog(
                    this@MainActivity,
                    position,
                    pair,
                    true
                ) { pos, newName, newBehavior ->
                    viewModel.updatePairName(pos, newName)
                    viewModel.updatePairBehavior(pos, newBehavior)
                    refreshAll()

                    val currentName = tvPlaylistName.text.toString()
                    if (currentName.isNotEmpty()) {
                        PlaylistSaver.saveNamedList(
                            this@MainActivity,
                            currentName,
                            viewModel,
                            defaultBehavior
                        )
                    }
                }
            }
        )

        rightAdapter = TrackAdapter(
            viewModel.pairs!!, false, currentPairIndex,
            { position ->
                // === ФИКС: Если играет, сначала останавливаем! ===
                if (isPlaying) stopPlayback()
                // =================================================
                currentPairIndex = position
                leftAdapter.updateCurrentPair(position)
                viewModel.updateCurrentPairIndex(position)
                refreshAll()
            },
            isFlowMode,
            defaultBehavior
        )

        leftRecyclerView.layoutManager = LinearLayoutManager(this)
        rightRecyclerView.layoutManager = LinearLayoutManager(this)

        leftRecyclerView.adapter = leftAdapter
        rightRecyclerView.adapter = rightAdapter

        leftRecyclerView.setHasFixedSize(true)
        rightRecyclerView.setHasFixedSize(true)

        leftRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                isLeftScrolling = newState != RecyclerView.SCROLL_STATE_IDLE
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (isLeftScrolling && !isSyncing) {
                    isSyncing = true
                    rightRecyclerView.scrollBy(0, dy)
                    isSyncing = false
                }
            }
        })

        rightRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                isRightScrolling = newState != RecyclerView.SCROLL_STATE_IDLE
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (isRightScrolling && !isSyncing) {
                    isSyncing = true
                    leftRecyclerView.scrollBy(0, dy)
                    isSyncing = false
                }
            }
        })
    }

    private var lastLoadedPlaylist: String = ""

    private fun updatePlaylistName(name: String) {
        Log.d("LOAD_DEBUG", "updatePlaylistName: $name")
        tvPlaylistName.text = if (name.isEmpty()) "NoNameList" else name
        if (name.isNotEmpty()) {
            lastLoadedPlaylist = name
        }
    }

    private fun setupVolumeControls() {
        seekFoh.max = 100
        seekFoh.progress = prefs.getInt("vol_foh", 100)
        tvVolFoh.text = "${seekFoh.progress}%"

        seekFoh.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvVolFoh.text = "$progress%"
                prefs.edit().putInt("vol_foh", progress).apply()
                if (enginePtr != 0L && isPlaying(enginePtr)) {
                    setVolumes(enginePtr, progress / 100f, seekMon.progress / 100f)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekMon.max = 100
        seekMon.progress = prefs.getInt("vol_mon", 100)
        tvVolMon.text = "${seekMon.progress}%"

        seekMon.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvVolMon.text = "$progress%"
                prefs.edit().putInt("vol_mon", progress).apply()
                if (enginePtr != 0L && isPlaying(enginePtr)) {
                    setVolumes(enginePtr, seekFoh.progress / 100f, progress / 100f)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    // Один picker на всё
    private val pickWavLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) { // ← ДОБАВЛЕНО result.data != null
                val added = handleFiles(result.data)
                if (added > 0) ToastHelper.showCustomToast(this, "Added: $added files", "success")
            }
        }

    private fun setupUsbCallback() {
        usbDeviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) = checkUSB()
            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) = checkUSB()
        }
        audioManager.registerAudioDeviceCallback(usbDeviceCallback, null)
    }

    // ========== АВТОНАЗНАЧЕНИЕ КАНАЛОВ ПРИ ПЕРВОМ ПОДКЛЮЧЕНИИ КАРТЫ ==========
    private fun autoAssignChannelsIfNeeded() {
        if (usbDeviceName.isEmpty() || usbDeviceName == "No device") return

        val prefs = getSharedPreferences("stagemon", MODE_PRIVATE)
        val keyPrefix = "routing_${usbDeviceName.replace(" ", "_")}"

        val hasSettings = prefs.contains("${keyPrefix}_foh_l")

        if (!hasSettings) {
            // Автоназначение: сохраняем позиции спиннера (0, 1, 2, 3, 4)
            // Позиция 0 = "n/a", позиция 1 = "1 channel", позиция 2 = "2 channel" и т.д.
            val fohL = 1  // "1 channel" (позиция 1 в спиннере)
            val fohR = if (maxChannels >= 2) 2 else 0  // "2 channel" или "n/a"
            val monL = if (maxChannels >= 3) 3 else 0  // "3 channel" или "n/a"
            val monR = if (maxChannels >= 4) 4 else 0  // "4 channel" или "n/a"

            prefs.edit()
                .putInt("${keyPrefix}_foh_l", fohL)
                .putInt("${keyPrefix}_foh_r", fohR)
                .putInt("${keyPrefix}_mon_l", monL)
                .putInt("${keyPrefix}_mon_r", monR)
                .apply()

            Log.d("MAIN", "Auto-assign for $usbDeviceName: FOH L=$fohL, R=$fohR, MON L=$monL, R=$monR")
        }
    }


    private fun setupListeners() {
        btnAddWav.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                addCategory(Intent.CATEGORY_OPENABLE)

                // ========== КОРНЕВАЯ ПАПКА ==========
                try {
                    val rootUriString = prefs.getString("root_tree_uri", null)
                    if (rootUriString != null) {
                        val rootUri = Uri.parse(rootUriString)
                        val rootDoc = DocumentFile.fromTreeUri(this@MainActivity, rootUri)
                        if (rootDoc != null && rootDoc.exists()) {
                            putExtra(DocumentsContract.EXTRA_INITIAL_URI, rootUri)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("FILE_PICKER", "Ошибка", e)
                }
                // ====================================
            }
            pickWavLauncher.launch(intent)
        }

        btnFlow.setOnClickListener { toggleFlowMode() }

        btnMenu.setOnClickListener {
            showPlaylistDialog()
        }


        btnMenu.setOnLongClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivityForResult(intent, 1003)  // ← вместо startActivity
            true
        }

        btnPrev.setOnClickListener {
        }

        btnPrev.setOnLongClickListener {
            startAutoScroll(-1)
            true
        }

        btnNext.setOnClickListener {
        }

        btnNext.setOnLongClickListener {
            startAutoScroll(1)
            true
        }

        btnPrev.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                stopAutoScroll()
                if (autoScrollRunnable == null && currentPairIndex > 0) {
                    currentPairIndex--
                    if (isPlaying) stopPlayback()
                    refreshAll()
                    viewModel.updateCurrentPairIndex(currentPairIndex)
                }
            }
            false
        }

        btnNext.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                stopAutoScroll()
                if (autoScrollRunnable == null && currentPairIndex < 98) {
                    currentPairIndex++
                    if (isPlaying) stopPlayback()
                    refreshAll()
                    viewModel.updateCurrentPairIndex(currentPairIndex)
                }
            }
            false
        }

        btnPlayStop.setOnClickListener {
            if (isPlaying) stopPlayback() else playCurrentPair()
        }
    }

    fun updateRoutingFromSettings() {
        if (usbDeviceName.isEmpty()) return

        val keyPrefix = "routing_${usbDeviceName.replace(" ", "_")}"
        val fohL_ui = prefs.getInt("${keyPrefix}_foh_l", 1)
        val fohR_ui = prefs.getInt("${keyPrefix}_foh_r", 2)
        val monL_ui = prefs.getInt("${keyPrefix}_mon_l", 3)
        val monR_ui = prefs.getInt("${keyPrefix}_mon_r", 4)

        val fohL = if (fohL_ui == 0) -1 else fohL_ui - 1
        val fohR = if (fohR_ui == 0) -1 else fohR_ui - 1
        val monL = if (monL_ui == 0) -1 else monL_ui - 1
        val monR = if (monR_ui == 0) -1 else monR_ui - 1

        setChannelRouting(enginePtr, fohL, fohR, monL, monR)

        if (enginePtr != 0L) {
            setChannelRouting(enginePtr, fohL, fohR, monL, monR)
            Log.d(
                "MainActivity",
                "Routing updated from Settings: FOH L=$fohL, FOH R=$fohR, MON L=$monL, MON R=$monR"
            )
        }
    }

    private fun playCurrentPair(resume: Boolean = false) {
        Log.d("PLAY_DEBUG", "playCurrentPair called, usbDeviceId=$usbDeviceId, isPlaying=$isPlaying")

        // ========== ДОБАВИТЬ ДЛЯ ОТЛАДКИ ==========
        Log.d("PLAY_DEBUG", "currentPairIndex=$currentPairIndex, pairs.size=${viewModel.pairs?.size}")
        // ==========================================

        if (usbDeviceId == -1) {
            ToastHelper.showCustomToast(this, "No USB device", "warning")
            return
        }

        var pairToPlay = viewModel.pairs?.get(currentPairIndex)

        // ========== ДОБАВИТЬ ДЛЯ ОТЛАДКИ ==========
        Log.d("PLAY_DEBUG", "pairToPlay after get: foh='${pairToPlay?.fohFile}', mon='${pairToPlay?.monFile}', valid=${pairToPlay?.isValid()}")
        // ==========================================

        if (pairToPlay == null || !pairToPlay.isValid()) {
            Log.d("PLAY_DEBUG", "pairToPlay null or invalid, searching...")
            var foundValid = false
            for (i in 0 until 99) {
                val p = viewModel.pairs?.get(i)
                if (p != null && p.isValid()) {
                    currentPairIndex = i
                    pairToPlay = p
                    foundValid = true
                    Log.d("PLAY_DEBUG", "Found valid track at index $i")
                    break
                }
            }
            if (!foundValid) {
                Log.e("PLAY_DEBUG", "NO VALID TRACKS FOUND!")
                ToastHelper.showCustomToast(this, "No valid tracks in playlist", "error")
                return
            }
            viewModel.updateCurrentPairIndex(currentPairIndex)
            refreshAll()
        }

        val currentPair = pairToPlay!!
        Log.d("PLAY_DEBUG", "Pair files: foh='${currentPair.fohFile}', mon='${currentPair.monFile}'")
        Log.d("PLAY_DEBUG", "Pair isValid=${currentPair.isValid()}")

        Log.d("PLAY_DEBUG", "Checking pair emptiness...")
        if (currentPair.fohFile.isEmpty() || currentPair.monFile.isEmpty()) {
            ToastHelper.showCustomToast(this, "No tracks paired", "error")
            return
        }

        // Проверяем только если метаданные были прочитаны (не равны 0)
        if ((currentPair.fohSampleRate > 0 && currentPair.fohSampleRate != 48000) ||
            (currentPair.monSampleRate > 0 && currentPair.monSampleRate != 48000)) {
            ToastHelper.showCustomToast(this, "Track must be 48kHz! Check playlist.", "error")
            return
        }

        isUsbDisconnected = false
        pendingResume = false
        tvCountdown.visibility = View.GONE
        tvCountdown.setOnClickListener(null)

        if (isPlaying) stopPlayback()
        requestAudioFocus()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)

        isStopping = false

        tvNowPlaying?.text = "Now playing:"
        tvNowPlaying?.setTextColor(Color.parseColor("#888888"))
        stopRequested = false
        backgroundAnimation.playAnimation()
        refreshAll()

        val startOffset = if (resume) savedPosition else 0L
        Log.d("PLAY", "Starting at offset: $startOffset, resume: $resume")

        scope.launch(Dispatchers.IO) {
            try {
                val rootUriString = prefs.getString("root_tree_uri", null)
                if (rootUriString == null) {
                    Log.e("PLAY", "root_tree_uri is null! No folder selected!")
                    withContext(Dispatchers.Main) {
                        ToastHelper.showCustomToast(this@MainActivity, "Select folder first!", "error")
                        isPlaying = false
                        backgroundAnimation.pauseAnimation()
                        refreshAll()
                    }
                    return@launch
                }

                val rootUri = Uri.parse(rootUriString)
                val rootDoc = DocumentFile.fromTreeUri(this@MainActivity, rootUri)
                if (rootDoc == null) {
                    Log.e("PLAY", "DocumentFile.fromTreeUri returned null! URI=$rootUriString")
                    withContext(Dispatchers.Main) {
                        ToastHelper.showCustomToast(this@MainActivity, "Folder not accessible!", "error")
                        isPlaying = false
                        backgroundAnimation.pauseAnimation()
                        refreshAll()
                    }
                    return@launch
                }

                val fohFile = findFileByName(rootDoc, currentPair.fohFile)
                val monFile = findFileByName(rootDoc, currentPair.monFile)

                if (fohFile == null || monFile == null) {
                    Log.e("PLAY", "Files not found! foh='${currentPair.fohFile}', mon='${currentPair.monFile}'")
                    withContext(Dispatchers.Main) {
                        ToastHelper.showCustomToast(this@MainActivity, "Files not found", "error")
                        isPlaying = false
                        tvNowPlaying?.text = "Standby:"
                        tvNowPlaying?.setTextColor(Color.parseColor("#888888"))
                        backgroundAnimation.pauseAnimation()
                        refreshAll()
                    }
                    return@launch
                }


                // ========== ЧИТАЕМ WAV-ЗАГОЛОВКИ ==========
                val fohInfo = WavHeaderParser.parse(this@MainActivity, fohFile.uri)
                val monInfo = WavHeaderParser.parse(this@MainActivity, monFile.uri)

                if (fohInfo == null || monInfo == null) {
                    Log.e("PLAY", "Failed to read WAV header!")
                    withContext(Dispatchers.Main) {
                        ToastHelper.showCustomToast(this@MainActivity, "Invalid WAV header", "error")
                        isPlaying = false
                        backgroundAnimation.pauseAnimation()
                        refreshAll()
                    }
                    return@launch
                }

                Log.d("PLAY", "FOH=$fohInfo, MON=$monInfo")
                // ========== ИЗВЛЕЧЕНИЕ ПИКОВ (БЕЗ КЭША, НА ЛЕТУ) ==========
                val numPeaks = 50 // Количество столбиков на каждую волну
                var fohPeaks = floatArrayOf()
                var monPeaks = floatArrayOf()

// ОТКРЫВАЕМ ВТОРЫЕ FD! Это спасет Oboe от рассинхрона позиции чтения
                val fohExtractPfd = contentResolver.openFileDescriptor(fohFile.uri, "r")
                val monExtractPfd = contentResolver.openFileDescriptor(monFile.uri, "r")

                if (fohExtractPfd != null && monExtractPfd != null) {
                    fohPeaks = extractPeaks(
                        fohExtractPfd.fd, fohInfo.dataOffset, fohInfo.dataSize,
                        fohInfo.bitsPerSample, fohInfo.formatTag, numPeaks
                    )
                    monPeaks = extractPeaks(
                        monExtractPfd.fd, monInfo.dataOffset, monInfo.dataSize,
                        monInfo.bitsPerSample, monInfo.formatTag, numPeaks
                    )
                }

                fohExtractPfd?.close()
                monExtractPfd?.close()

                withContext(Dispatchers.Main) {
                    findViewById<DualWaveformView>(R.id.dualWaveform)?.setPeaks(fohPeaks, monPeaks)
                }




                if (!fohInfo.is48k() || !monInfo.is48k()) {
                    withContext(Dispatchers.Main) {
                        ToastHelper.showCustomToast(this@MainActivity, "Track must be 48kHz!", "error")
                        isPlaying = false
                        backgroundAnimation.pauseAnimation()
                        refreshAll()
                    }
                    return@launch
                }

                setTrackParams(
                    enginePtr,
                    fohInfo.sampleRate, fohInfo.bitsPerSample, fohInfo.formatTag,
                    monInfo.sampleRate, monInfo.bitsPerSample, monInfo.formatTag
                )
                setTrackDataInfo(
                    enginePtr,
                    fohInfo.dataOffset, fohInfo.blockAlign,
                    monInfo.dataOffset, monInfo.blockAlign
                )
                // ======================================================================

                Log.d("PLAY", "FOH ${fohInfo.displayMeta()}, MON ${monInfo.displayMeta()}")

                leftPfd = contentResolver.openFileDescriptor(fohFile.uri, "r")
                rightPfd = contentResolver.openFileDescriptor(monFile.uri, "r")
                if (leftPfd == null || rightPfd == null) {
                    withContext(Dispatchers.Main) {
                        ToastHelper.showCustomToast(this@MainActivity, "Cannot open files", "error")
                        isPlaying = false
                        backgroundAnimation.pauseAnimation()
                        refreshAll()
                    }
                    return@launch
                }

                seekFd(leftPfd!!.fd, fohInfo.dataOffset + startOffset)
                seekFd(rightPfd!!.fd, monInfo.dataOffset + startOffset)

                val leftFd = leftPfd!!.fd
                val rightFd = rightPfd!!.fd
                val leftDataLen = fohInfo.dataSize
                val rightDataLen = monInfo.dataSize

                if (leftDataLen <= 0 || rightDataLen <= 0) {
                    withContext(Dispatchers.Main) {
                        ToastHelper.showCustomToast(this@MainActivity, "File too small", "error")
                        isPlaying = false
                        backgroundAnimation.pauseAnimation()
                        refreshAll()
                    }
                    return@launch
                }

                setDeviceId(enginePtr, usbDeviceId)
                setVolumes(enginePtr, seekFoh.progress / 100f, seekMon.progress / 100f)

                val keyPrefix = "routing_${usbDeviceName.replace(" ", "_")}"

// Читаем позицию спиннера (0, 1, 2, 3, 4)
                val fohL_pos = prefs.getInt("${keyPrefix}_foh_l", 0)
                val fohR_pos = prefs.getInt("${keyPrefix}_foh_r", 0)
                val monL_pos = prefs.getInt("${keyPrefix}_mon_l", 0)
                val monR_pos = prefs.getInt("${keyPrefix}_mon_r", 0)

// Преобразуем позицию спиннера в индекс массива
                val fohL = if (fohL_pos == 0) -1 else fohL_pos - 1
                val fohR = if (fohR_pos == 0) -1 else fohR_pos - 1
                val monL = if (monL_pos == 0) -1 else monL_pos - 1
                val monR = if (monR_pos == 0) -1 else monR_pos - 1

                setChannelRouting(enginePtr, fohL, fohR, monL, monR)

                if (!isStreamOpen(enginePtr)) {
                    val outputFormatIndex = prefs.getInt("output_bit_depth_index", 1)
                    val opened = openStream(enginePtr, usbDeviceId, outputFormatIndex)
                    if (!opened) {
                        Log.e("PLAY", "Failed to open stream!")
                        withContext(Dispatchers.Main) {
                            ToastHelper.showCustomToast(this@MainActivity, "Audio stream error", "error")
                            isPlaying = false
                            backgroundAnimation.pauseAnimation()
                            refreshAll()
                        }
                        return@launch
                    }
                }
                Log.d("PLAY", "leftDataLen=$leftDataLen, rightDataLen=$rightDataLen")
                setTrackFdsSeek(enginePtr, leftFd, leftDataLen, rightFd, rightDataLen)

                withContext(Dispatchers.Main) {
                }

                val started = startEngine(enginePtr)

                withContext(Dispatchers.Main) {
                    if (started) {
                        isPlaying = true
                        tvUsbStatus.text = "USB\nON"
                        tvUsbStatus.setTextColor(Color.GREEN)
                        isStopping = false
                        if (!resume) savedPosition = 0L
                        startVuMeterUpdates()
                        // WaveformHelper.loadWaveform(contentResolver, fohFile.uri, waveformSeekBar)
                        refreshAll()
                    } else {
                        ToastHelper.showCustomToast(this@MainActivity, "Startup error", "error")
                        isPlaying = false; backgroundAnimation.pauseAnimation(); refreshAll()
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Playback error", e)
                withContext(Dispatchers.Main) {
                    ToastHelper.showCustomToast(this@MainActivity, "Error: ${e.message}", "error")
                    isPlaying = false; backgroundAnimation.pauseAnimation(); refreshAll()
                }
            }
        }
    }

    private fun findFileByName(folder: DocumentFile, fileName: String): DocumentFile? {
        folder.listFiles().forEach { file ->
            if (file.isFile && file.name == fileName) {
                return file
            }
        }

        folder.listFiles().forEach { file ->
            if (file.isDirectory) {
                val found = findFileByName(file, fileName)
                if (found != null) return found
            }
        }

        return null
    }

    private fun stopPlayback() {
        if (enginePtr != 0L) {
            if (isUsbDisconnected && isPlaying) {
                savedPosition = getCurrentPosition(enginePtr)
            }

            // СНАЧАЛА останавливаем движок
            stopEngine(enginePtr)

            // ПОТОМ закрываем файловые дескрипторы
            try {
                leftPfd?.close()
            } catch (e: Exception) {
                Log.e("STOP", "Error closing leftPfd", e)
            }
            leftPfd = null

            try {
                rightPfd?.close()
            } catch (e: Exception) {
                Log.e("STOP", "Error closing rightPfd", e)
            }
            rightPfd = null
        }

        if (::focusRequest.isInitialized) {
            try {
                audioManager.abandonAudioFocusRequest(focusRequest)
            } catch (e: Exception) {
                Log.e("STOP", "Error abandoning audio focus", e)
            }
        }

        isPlaying = false
        if (!isUsbDisconnected) {
            isStopping = true
            stopFadeLeft = smoothFohLeftDb
            stopFadeRight = smoothFohRightDb
            stopFadeLeftMon = smoothMonLeftDb
            stopFadeRightMon = smoothMonRightDb
        }

        findViewById<DualWaveformView>(R.id.dualWaveform)?.setPeaks(floatArrayOf(), floatArrayOf())
        backgroundAnimation.pauseAnimation()
        refreshAll()

        if (!vuHandler.hasCallbacks(vuRunnable)) {
            vuHandler.post(vuRunnable)
        }
    }

    private fun requestAudioFocus() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        runOnUiThread { stopPlayback() }
                    }

                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        Log.d("AudioFocus", "Temporary loss - ignoring")
                    }

                    AudioManager.AUDIOFOCUS_GAIN -> {
                        Log.d("AudioFocus", "Focus gained")
                    }
                }
            }
            .build()

        val result = audioManager.requestAudioFocus(focusRequest)
        Log.d("AudioFocus", "Request result: $result")
    }

    private fun checkUSB() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        val wasDisconnected = (usbDeviceId == -1)
        var newDeviceId = -1
        var newDeviceName = ""
        var newMaxChannels = 0

        for (device in devices) {
            if (device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                device.type == AudioDeviceInfo.TYPE_USB_HEADSET
            ) {
                newDeviceId = device.id
                newDeviceName = device.productName.toString()
                newMaxChannels = device.channelCounts.maxOrNull() ?: 2
                break
            }
        }

        runOnUiThread {
            usbDeviceId = newDeviceId
            usbDeviceName = newDeviceName
            maxChannels = newMaxChannels
            autoAssignChannelsIfNeeded()

            if (usbDeviceId != -1) {
                tvUsbStatus.text = "USB\nON"
                tvUsbStatus.setTextColor(Color.GREEN)
            } else {
                tvUsbStatus.text = "USB\nOFF"
                tvUsbStatus.setTextColor(Color.parseColor("#888888"))

                if (isPlaying) {
                    isUsbDisconnected = true
                    pendingResume = true
                    stopPlayback()

                    tvCountdown.text = "PAUSE"
                    tvCountdown.visibility = View.VISIBLE

                    tvCountdown.setOnClickListener {
                        if (usbDeviceId != -1 && pendingResume) {
                            tvCountdown.visibility = View.GONE
                            tvCountdown.setOnClickListener(null)
                            pendingResume = false
                            isUsbDisconnected = false
                            playCurrentPair(true)
                        }
                    }
                }
            }
        }
    }

    private fun showPlaylistDialog() {
        val items = arrayOf("Edit list", "Load list", "Clear list")

        val adapter =
            object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent)
                    val textView = view.findViewById<TextView>(android.R.id.text1)
                    textView.gravity = Gravity.CENTER
                    textView.textAlignment = View.TEXT_ALIGNMENT_CENTER
                    return view
                }
            }

        AlertDialog.Builder(this)
            .setTitle("PLAYLISTS")
            .setAdapter(adapter) { dialog, which ->
                when (which) {
                    0 -> openEditorActivity()
                    1 -> loadListDialog()
                    2 -> clearListDialog()
                }
            }
            .setNegativeButton("Back", null)
            .show()
            .window?.setBackgroundDrawable(
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 40f
                    setColor(0xFF1E1E1E.toInt())
                }
            )
    }

    private fun openEditorActivity() {
        val intent = Intent(this, EditorActivity::class.java)
        intent.putExtra("pairs", gson.toJson(viewModel.pairs!!))
        intent.putExtra("default_behavior", defaultBehavior)
        intent.putExtra("playlist_name", tvPlaylistName.text.toString())
        intent.putExtra("from_main", true)
        startActivityForResult(intent, 1002)
    }


    private val vuHandler = Handler(Looper.getMainLooper())

    private val vuRunnable = object : Runnable {
        override fun run() {
            if (isPlaying && enginePtr != 0L) {
                val engineState = isPlaying(enginePtr)
                if (engineState) {
                    stopRequested = false

                    val currentPosBytes = getCurrentPosition(enginePtr)
                    val fohLength = getFohLength(enginePtr)

                    if (fohLength > 0) {
                        val progress = (currentPosBytes.toFloat() / fohLength.toFloat()).coerceIn(0f, 1f)
                        findViewById<DualWaveformView>(R.id.dualWaveform)?.setProgress(progress)
                    }

                    val leftVal = getFohLeft(enginePtr)
                    val rightVal = getFohRight(enginePtr)
                    val leftMon = getMonLeft(enginePtr)
                    val rightMon = getMonRight(enginePtr)

                    val leftFohDb = floatToDb(leftVal)
                    val rightFohDb = floatToDb(rightVal)
                    val leftMonDb = floatToDb(leftMon)
                    val rightMonDb = floatToDb(rightMon)

                    updateVuFoh(leftFohDb, rightFohDb)
                    updateVuMon(leftMonDb, rightMonDb)

                    vuHandler.postDelayed(this, 16)
                } else if (!stopRequested) {
                    stopRequested = true
                    vuHandler.postDelayed(this, 300)
                } else {
                    runOnUiThread {
                        stopPlayback()

                        // ========== РЕЖИМЫ SINGLE/REPEAT_ONE ==========
                        if (!isFlowMode) {
                            when (playbackMode) {
                                "repeat_one" -> {
                                    playCurrentPair(resume = false)  // заново эту же
                                }

                                "instant" -> {
                                    // INSTANT MODE - играем следующий трек сразу
                                    var next = currentPairIndex + 1
                                    var found = false

                                    while (next < 99) {
                                        if (viewModel.pairs!![next].isValid()) {
                                            currentPairIndex = next
                                            refreshAll()
                                            playCurrentPair(resume = false)
                                            found = true
                                            break  // ← ВЫХОДИМ ИЗ ЦИКЛА!
                                        }
                                        next++
                                    }

                                    if (!found) {
                                        showEndOfPlaylist()  // ← НЕТ СЛЕДУЮЩЕГО ТРЕКА
                                    }
                                }

                                "single" -> {
                                    // Ничего не делаем - просто останавливаемся
                                }
                            }
                        } else if (isFlowMode) {
                            // === НОВАЯ ПРОВЕРКА: ЕСЛИ ЭТО ПОСЛЕДНИЙ ТРЕК, СРАЗУ КОНЕЦ ===
                            if (!hasNextTrack()) {
                                showEndOfPlaylist()
                                isFlowMode = false
                                btnFlow.backgroundTintList = ContextCompat.getColorStateList(
                                    this@MainActivity,
                                    R.color.white
                                )
                            } else {
                                // =================================================================
                                val currentPair = viewModel.pairs?.getOrNull(currentPairIndex)
                                val behavior = if (currentPair?.behavior?.isNotEmpty() == true)
                                    currentPair.behavior
                                else
                                    defaultBehavior

                                Log.d("FLOW", "Current track behavior: $behavior")

                                when {
                                    behavior == "I" -> {
                                        playNextInFlow()
                                    }

                                    behavior == "P" -> {
                                        tvCountdown.text = "PAUSE"
                                        tvCountdown.visibility = View.VISIBLE
                                        tvCountdown.setOnClickListener {
                                            tvCountdown.visibility = View.GONE
                                            tvCountdown.setOnClickListener(null)
                                            playNextInFlow()
                                        }
                                    }

                                    behavior.matches(Regex("\\d+")) -> {
                                        val delaySeconds = behavior.toInt()
                                        startCountdownWithDelay(delaySeconds) {
                                            playNextInFlow()
                                        }
                                    }

                                    else -> {
                                        when (defaultBehavior) {
                                            "I" -> {
                                                playNextInFlow()
                                            }

                                            "P" -> {
                                                tvCountdown.text = "PAUSE"
                                                tvCountdown.visibility = View.VISIBLE
                                                tvCountdown.setOnClickListener {
                                                    tvCountdown.visibility = View.GONE
                                                    tvCountdown.setOnClickListener(null)
                                                    playNextInFlow()
                                                }
                                            }

                                            else -> {
                                                val defaultDelay =
                                                    defaultBehavior.toIntOrNull() ?: 5
                                                startCountdownWithDelay(defaultDelay) {
                                                    playNextInFlow()
                                                }
                                            }
                                        }
                                    }
                                }
                            } // <--- ЗАКРЫВАЮЩАЯ СКОБКА ДЛЯ if (!hasNextTrack())
                        } else {
                        }
                    }
                    return
                }
            } else if (isStopping) {
                stopFadeLeft = stopFadeLeft * 0.95f - 2f
                stopFadeRight = stopFadeRight * 0.95f - 2f
                stopFadeLeftMon = stopFadeLeftMon * 0.95f - 2f
                stopFadeRightMon = stopFadeRightMon * 0.95f - 2f

                updateVuFoh(stopFadeLeft, stopFadeRight)
                updateVuMon(stopFadeLeftMon, stopFadeRightMon)

                if (stopFadeLeft < -60f && stopFadeRight < -60f &&
                    stopFadeLeftMon < -60f && stopFadeRightMon < -60f
                ) {
                    isStopping = false
                    return
                }

                vuHandler.postDelayed(this, 16)
            }
        }
    }

    private fun updatePlaybackModeButton() {
        // Определяем какой drawable грузить
        val drawableRes = when (playbackMode) {
            "single" -> R.drawable.ic_stop_one
            "repeat_one" -> R.drawable.ic_repeat_one
            "instant" -> R.drawable.ic_instant
            else -> R.drawable.ic_stop_one
        }

        // Грузим drawable
        val drawable = ContextCompat.getDrawable(this, drawableRes)

        // УБИРАЕМ setBounds НАХУЙ! ОН НЕ НУЖЕН!
        // drawable?.setBounds(0, 0, 70, 50)  // ← УДАЛИ ЭТО!

        // ИСПОЛЬЗУЕМ ПРАВИЛЬНЫЙ МЕТОД!
        btnPlaybackMode.text = ""
        btnPlaybackMode.setCompoundDrawablesWithIntrinsicBounds(null, drawable, null, null)
        btnPlaybackMode.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
    }

    private fun togglePlaybackMode() {
        playbackMode = when (playbackMode) {
            "single" -> {
                ToastHelper.showCustomToast(this, "MODE: Instant", "info")
                "instant"
            }

            "instant" -> {
                ToastHelper.showCustomToast(this, "MODE: Repeat One", "info")
                "repeat_one"
            }

            "repeat_one" -> {
                ToastHelper.showCustomToast(this, "MODE: Single", "info")
                "single"
            }

            else -> "single"
        }
        updatePlaybackModeButton()
    }

    private fun floatToDb(value: Float): Float {
        if (value <= 0.00001f) return -60f
        val calibrated = value * 6.0f
        return 20 * Math.log10(calibrated.toDouble()).toFloat()
    }

    private fun startVuMeterUpdates() {
        if (!vuHandler.hasCallbacks(vuRunnable)) {
            vuHandler.post(vuRunnable)
        }
    }


    private fun applyRoundedCorners() {
        val radius = 20f

        // Создаём ОТДЕЛЬНЫЕ GradientDrawable для каждого контейнера!
        val songPairBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(0xFF333333.toInt())
        }

        val playlistPairBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(0xFF333333.toInt())
        }

        val deckBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(0xFF111111.toInt())
        }

        // Теперь у каждого контейнера СВОЙ собственный background
        findViewById<FrameLayout>(R.id.pairNameContainer).apply {
            background = songPairBg  // Свой объект для окна песни
            clipToOutline = true
        }
        findViewById<FrameLayout>(R.id.playlistNameContainer).apply {
            background = playlistPairBg  // Свой объект для окна плейлиста
            clipToOutline = true
        }
        findViewById<FrameLayout>(R.id.leftDeckContainer).apply {
            background = deckBg
            clipToOutline = true
        }
        findViewById<FrameLayout>(R.id.rightDeckContainer).apply {
            background = deckBg
            clipToOutline = true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                createAppFolders()
                ToastHelper.showCustomToast(this, "Permission received", "success")
            } else {
                ToastHelper.showCustomToast(this, "No write permission", "error")
            }
        }
    }

    private fun handleFiles(data: Intent?): Int {
        var added = 0

        val files = mutableListOf<FileEntry>()


        if (data?.clipData != null) {
            val clip = data.clipData!!
            for (i in 0 until clip.itemCount) {
                val uri = clip.getItemAt(i).uri
                val fileName = getFileName(uri.toString())
                files.add(FileEntry(fileName, uri))
            }
        } else if (data?.data != null) {
            val uri = data.data!!
            val fileName = getFileName(uri.toString())
            files.add(FileEntry(fileName, uri))
        }

        for ((fileName, uri) in files) {
            Log.d("HANDLE_FILES", "Обработка файла: $fileName")
            val isFohFile = fileName.endsWith(".foh.wav")
            val isMonFile = fileName.endsWith(".mon.wav")

            if (!isFohFile && !isMonFile) {
                Log.d("HANDLE_FILES", "Файл без суффикса .foh/.mon - игнорируем")
                ToastHelper.showCustomToast(this, "File must end with .foh.wav or .mon.wav", "warning")
                continue
            }

            val baseName = when {
                isFohFile -> fileName.substringBefore(".foh.wav")
                isMonFile -> fileName.substringBefore(".mon.wav")
                else -> ""
            }

            val wavInfo = WavHeaderParser.parse(this, uri)
            if (wavInfo == null) {
                ToastHelper.showCustomToast(this, "Can't read WAV header ($fileName)", "error")
                continue
            }
            val info = wavInfo!!
            val sr = info.sampleRate
            val bd = info.bitsPerSample
            val fmt = info.formatTag

            if (!info.is48k()) {
                ToastHelper.showCustomToast(this, "Track must be 48kHz! $fileName is ${wavInfo.displaySampleRate()}", "error")
                continue
            }

            if (isFohFile) {
                viewModel.addOrUpdatePair(baseName, fileName, true)
                added++
                val pairs = viewModel.pairs
                if (pairs != null) {
                    for (i in pairs.indices) {
                        if (pairs[i].fohFile == fileName) {
                            viewModel.updatePairMetadata(
                                i, sr, bd, fmt, pairs[i].monSampleRate, pairs[i].monBitDepth, pairs[i].monFormatTag
                            )
                            break
                        }
                    }
                }
            } else if (isMonFile) {
                viewModel.addOrUpdatePair(baseName, fileName, false)
                added++
                val pairs = viewModel.pairs
                if (pairs != null) {
                    for (i in pairs.indices) {
                        if (pairs[i].monFile == fileName) {
                            viewModel.updatePairMetadata(
                                i, pairs[i].fohSampleRate, pairs[i].fohBitDepth, pairs[i].fohFormatTag, sr, bd, fmt
                            )
                            break
                        }
                    }
                }
            }
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
        }
        return added
    }

    @SuppressLint("Range")
    private fun getFileName(uriString: String): String {
        if (uriString.isEmpty()) return "—"
        val uri = Uri.parse(uriString)
        return try {
            contentResolver.query(uri, null, null, null, null)?.use {
                if (it.moveToFirst()) {
                    it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME)) ?: "Unknown"
                } else "Unknown"
            } ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun toggleFlowMode() {
        isFlowMode = !isFlowMode

        // Делаем кнопку неактивной во Flow
        btnPlaybackMode.isEnabled = !isFlowMode

        if (isFlowMode) {
            btnPlaybackMode.backgroundTintList = ColorStateList.valueOf(Color.GRAY)
            btnPlaybackMode.setTextColor(Color.DKGRAY)
        } else {
            // Возвращаем нормальный вид в зависимости от режима
            updatePlaybackModeButton()
        }

        btnFlow.backgroundTintList = if (isFlowMode) {
            ColorStateList.valueOf(Color.GREEN)
        } else {
            ContextCompat.getColorStateList(this, R.color.white)
        }

        // ========== ДОБАВЬ ЭТО ==========
// Обновляем оба адаптера с новыми настройками
        leftAdapter.updateConfig(
            isFlowMode,
            metronomeManager.isGloballyEnabled.value == true,
            defaultBehavior
        )
        rightAdapter.updateConfig(
            isFlowMode,
            metronomeManager.isGloballyEnabled.value == true,
            defaultBehavior
        )
// ================================
    }


    private fun loadListDialog() {
        val playlistsUri = prefs.getString("playlists_folder_uri", null)
        if (playlistsUri == null) {
            ToastHelper.showCustomToast(
                this,
                "First, select a folder to store playlists",
                "info"
            )
            openFolderPicker()
            return
        }

        val playlistsFolder = DocumentFile.fromTreeUri(this, Uri.parse(playlistsUri)) ?: return

        val files = playlistsFolder.listFiles()
        val txtFiles = files.filter {
            it.name?.endsWith(".txt") == true && it.isFile
        }

        if (txtFiles.isEmpty()) {
            ToastHelper.showCustomToast(this, "No saved playlists", "warning")
            return
        }

        val names = txtFiles.mapNotNull { it.name?.replace(".txt", "") }.toTypedArray()

        // ========== СОРТИРОВКА: СНАЧАЛА АНГЛИЙСКИЕ, ПОТОМ РУССКИЕ ==========
        val sortedNames = names.sortedWith { a, b ->
            val aIsEnglish = a.firstOrNull()?.let { it in 'A'..'Z' || it in 'a'..'z' } ?: false
            val bIsEnglish = b.firstOrNull()?.let { it in 'A'..'Z' || it in 'a'..'z' } ?: false

            when {
                aIsEnglish && !bIsEnglish -> -1  // английские вперед
                !aIsEnglish && bIsEnglish -> 1   // русские назад
                else -> a.compareTo(b, ignoreCase = true)  // внутри группы по алфавиту
            }
        }.toTypedArray()
        // =================================================================

        // ========== ИСПОЛЬЗУЕМ НОВЫЙ МЕТОД ИЗ DialogHelper ==========
        DialogHelper.showListDialog(this, "Load playlist", names) { name ->
            loadNamedList(name)
        }
    }

    private fun clearListDialog() {
        DialogHelper.showClearDialog(this) {
            clearAllLists()
        }
    }

    private fun createAppFolders() {
        try {
            val stagemonDir = File(Environment.getExternalStorageDirectory(), "Stagemon")
            val playlistsDir = File(stagemonDir, "_PlayLists")

            Log.d(
                "MainActivity",
                "Path: ${Environment.getExternalStorageDirectory().absolutePath}"
            )
            Log.d("MainActivity", "StagemonDir: ${stagemonDir.absolutePath}")
            Log.d("MainActivity", "PlaylistsDir: ${playlistsDir.absolutePath}")
            Log.d(
                "MainActivity",
                "ExternalStorageState: ${Environment.getExternalStorageState()}"
            )

            if (!stagemonDir.exists()) {
                val created = stagemonDir.mkdirs()
                Log.d("MainActivity", "Stagemon created: $created")
                if (!created) {
                    Log.e("MainActivity", "Failed to create Stagemon")
                    ToastHelper.showCustomToast(
                        this,
                        "Failed to create Stagemon folder",
                        "error"
                    )
                }
            }

            if (!playlistsDir.exists()) {
                val created = playlistsDir.mkdirs()
                Log.d("MainActivity", "_PlayLists created: $created")
            }

            if (stagemonDir.exists()) {
                Log.d(
                    "MainActivity",
                    "Stagemon exists, you can write: ${stagemonDir.canWrite()}"
                )
                ToastHelper.showCustomToast(
                    this,
                    "Folder Stagemon: ${stagemonDir.absolutePath}",
                    "success"
                )
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error creating folders", e)
            ToastHelper.showCustomToast(this, "Error: ${e.message}", "error")
        }
    }


    private fun loadNamedList(name: String) {
        Log.d("LOAD_DEBUG", "Пытаемся загрузить: $name")
        val playlistsUri = prefs.getString("playlists_folder_uri", null)
        Log.d("LOAD_DEBUG", "playlists_folder_uri: $playlistsUri")

        if (playlistsUri == null) {
            Log.e("LOAD_DEBUG", "playlists_folder_uri = null!")
            ToastHelper.showCustomToast(this, "Folder not selected", "error")
            return
        }
        if (isPlaying) stopPlayback()

        try {
            Log.d("LOAD_DEBUG", "Создаем playlistsFolder из URI")
            val playlistsFolder = DocumentFile.fromTreeUri(this, Uri.parse(playlistsUri))
            Log.d("LOAD_DEBUG", "playlistsFolder exists: ${playlistsFolder?.exists()}")

            if (playlistsFolder == null || !playlistsFolder.exists()) {
                Log.e("LOAD_DEBUG", "playlistsFolder не существует!")
                return
            }

            Log.d("LOAD_DEBUG", "Ищем файл: $name.txt")
            val txtFile = playlistsFolder.findFile("$name.txt")
            Log.d("LOAD_DEBUG", "txtFile найден: ${txtFile != null}")

            if (txtFile == null) {
                Log.e("LOAD_DEBUG", "Файл не найден!")
                ToastHelper.showCustomToast(this, "File not found", "error")
                return
            }

            Log.d("LOAD_DEBUG", "Открываем InputStream")
            contentResolver.openInputStream(txtFile.uri)?.use { inputStream ->
                Log.d("LOAD_DEBUG", "InputStream открыт, читаем lines")
                val lines = inputStream.bufferedReader().readLines()
                Log.d("LOAD_DEBUG", "Прочитано строк: ${lines.size}")

                if (lines.isEmpty()) {
                    Log.e("LOAD_DEBUG", "Файл пустой")
                    return
                }

                val rootPath = lines[0].trim()
                Log.d("LOAD_DEBUG", "rootPath: $rootPath")

                val defaultLine = if (lines.size > 1) lines[1].trim() else "[]"
                Log.d("LOAD_DEBUG", "defaultLine: $defaultLine")

                defaultBehavior = when {
                    defaultLine == "[]" -> ""
                    defaultLine.startsWith("[") && defaultLine.endsWith("]") -> {
                        val content = defaultLine.substring(1, defaultLine.length - 1)
                        if (content == "P" || content == "I" || content.toIntOrNull() != null) content else ""
                    }

                    else -> ""
                }
                Log.d("LOAD_DEBUG", "defaultBehavior: $defaultBehavior")

                for (i in 0 until 99) {
                    viewModel.clearPair(i)
                }

                var loadedPairs = 0
                for (i in 2 until lines.size) {
                    val line = lines[i].trim()
                    if (line.isEmpty()) continue

                    val parts = line.split("|").map { it.trim() }


                    // Теперь ожидаем 7 частей: номер | имя | foh | mon | {pattern} | [bpm] | [behavior]
                    if (parts.size >= 7) {
                        val number = parts[0].toIntOrNull() ?: continue
                        if (number in 1..99) {
                            val pairName = parts[1]
                            val fohFile = parts[2]
                            val monFile = parts[3]
                            val patternPart = parts[4]  // {pattern}
                            val bpmPart = parts[5]      // [bpm]
                            val behaviorPart = parts[6]  // [behavior]

                            // Извлекаем pattern из {}
                            val pattern =
                                if (patternPart.startsWith("{") && patternPart.endsWith("}")) {
                                    patternPart.substring(1, patternPart.length - 1)
                                } else ""

                            // Извлекаем bpm из []
                            val bpm = if (bpmPart.startsWith("[") && bpmPart.endsWith("]")) {
                                bpmPart.substring(1, bpmPart.length - 1)
                            } else ""

                            // Извлекаем behavior из []
                            val behavior =
                                if (behaviorPart.startsWith("[") && behaviorPart.endsWith("]")) {
                                    behaviorPart.substring(1, behaviorPart.length - 1)
                                } else ""

                            val fileNameForName = if (fohFile.isNotEmpty()) fohFile else monFile
                            val autoName = if (fileNameForName.isNotEmpty()) {
                                fileNameForName.substringBeforeLast(".wav")
                            } else ""

                            val finalName = when {
                                pairName.isNotEmpty() && pairName != "Song" -> pairName
                                autoName.isNotEmpty() -> autoName
                                else -> ""
                            }

                            // Сохраняем всё в ViewModel
                            viewModel.updatePairName(number - 1, finalName)
                            viewModel.updatePairFiles(number - 1, fohFile, monFile)
                            viewModel.updatePairPattern(number - 1, pattern)
                            viewModel.updatePairBpm(number - 1, bpm)
                            viewModel.updatePairBehavior(number - 1, behavior)

                            loadedPairs++
                        }
                    }

                }
                scope.launch(Dispatchers.IO) {
                    val rootUriString = prefs.getString("root_tree_uri", null) ?: return@launch
                    val rootUri = Uri.parse(rootUriString)
                    val rootDoc = DocumentFile.fromTreeUri(this@MainActivity, rootUri) ?: return@launch

                    Log.d("METADATA", "Starting WAV parsing for 99 tracks...")

                    for (i in 0 until 99) {
                        val pair = viewModel.pairs!![i]
                        if (pair.fohFile.isNotBlank() || pair.monFile.isNotBlank()) {
                            Log.d("METADATA", "Processing pair $i: foh='${pair.fohFile}', mon='${pair.monFile}'")

                            var currentFohSr = pair.fohSampleRate
                            var currentFohBd = pair.fohBitDepth
                            var currentFohFmt = pair.fohFormatTag
                            var currentMonSr = pair.monSampleRate
                            var currentMonBd = pair.monBitDepth
                            var currentMonFmt = pair.monFormatTag

                            // Парсим FOH
                            if (pair.fohFile.isNotBlank()) {
                                val fohDoc = findFileByName(rootDoc, pair.fohFile)
                                if (fohDoc != null) {
                                    val fohInfo = WavHeaderParser.parse(this@MainActivity, fohDoc.uri)
                                    if (fohInfo != null) {
                                        Log.d("METADATA", "FOH parsed: ${fohInfo.sampleRate}/${fohInfo.bitsPerSample}")
                                        currentFohSr = fohInfo.sampleRate
                                        currentFohBd = fohInfo.bitsPerSample
                                        currentFohFmt = fohInfo.formatTag

                                        withContext(Dispatchers.Main) {
                                            viewModel.updatePairMetadata(
                                                i,
                                                currentFohSr, currentFohBd, currentFohFmt,
                                                currentMonSr, currentMonBd, currentMonFmt
                                            )
                                        }
                                    }
                                }
                            }

                            // Парсим MON
                            if (pair.monFile.isNotBlank()) {
                                val monDoc = findFileByName(rootDoc, pair.monFile)
                                if (monDoc != null) {
                                    val monInfo = WavHeaderParser.parse(this@MainActivity, monDoc.uri)
                                    if (monInfo != null) {
                                        Log.d("METADATA", "MON parsed: ${monInfo.sampleRate}/${monInfo.bitsPerSample}")
                                        currentMonSr = monInfo.sampleRate
                                        currentMonBd = monInfo.bitsPerSample
                                        currentMonFmt = monInfo.formatTag

                                        withContext(Dispatchers.Main) {
                                            viewModel.updatePairMetadata(
                                                i,
                                                currentFohSr, currentFohBd, currentFohFmt,  // ← используем ОБНОВЛЁННЫЕ значения!
                                                currentMonSr, currentMonBd, currentMonFmt
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Log.d("METADATA", "WAV parsing completed, calling refreshAll()")
                    withContext(Dispatchers.Main) {
                        refreshAll()
                    }
                }


                Log.d("LOAD_DEBUG", "Загружено пар: $loadedPairs")

                currentPairIndex = 0
                for (i in 0 until 99) {
                    if (viewModel.pairs!![i].isValid()) {
                        currentPairIndex = i
                        break
                    }
                }
                Log.d("LOAD_DEBUG", "currentPairIndex: $currentPairIndex")

                lastLoadedPlaylist = name
                prefs.edit().putString("last_loaded_playlist", name).apply()
                viewModel.updatePlaylistName(name)
                viewModel.updateCurrentPairIndex(currentPairIndex)
                refreshAll()

                leftAdapter.updateConfig(
                    isFlowMode,
                    metronomeManager.isGloballyEnabled.value == true,
                    defaultBehavior
                )
                rightAdapter.updateConfig(
                    isFlowMode,
                    metronomeManager.isGloballyEnabled.value == true,
                    defaultBehavior
                )

                ToastHelper.showCustomToast(this, "Loaded: $name", "success")
                Log.d("LOAD_DEBUG", "=== ЗАГРУЗКА УСПЕШНО ЗАВЕРШЕНА ===")
            }
        } catch (e: Exception) {
            Log.e("LOAD_DEBUG", "ОШИБКА: ${e.message}", e)
            ToastHelper.showCustomToast(this, "Error: ${e.message}", "error")
        }
    }

    private fun clearAllLists() {
        for (i in 0 until 99) {
            viewModel.clearPair(i)
        }
        currentPairIndex = 0
        defaultBehavior = "I"
        lastLoadedPlaylist = "NoNameList"
        prefs.edit().putString("last_loaded_playlist", "").apply()
        refreshAll()
        updatePlaylistName("")
        ToastHelper.showCustomToast(this, "Cleared", "success")
    }

    private fun refreshAdapters() {
        (leftRecyclerView.adapter as? TrackAdapter)?.let { adapter ->
            adapter.updateCurrentPair(currentPairIndex)
        }
        (rightRecyclerView.adapter as? TrackAdapter)?.let { adapter ->
            adapter.updateCurrentPair(currentPairIndex)
        }
    }

    // ========== RECYCLERVIEW ADAPTER ==========
// ========== RECYCLERVIEW ADAPTER ==========
    class TrackAdapter(
        private var pairs: List<Pair>,
        private val isLeftDeck: Boolean,
        private var currentPair: Int,
        private val onItemClick: (Int) -> Unit,
        private var isFlowMode: Boolean,
        private var defaultBehavior: String = "I",
        private var isMetronomeEnabled: Boolean = false,
        private val onItemLongClick: ((Int) -> Unit)? = null
    ) : RecyclerView.Adapter<TrackAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val number: TextView = view.findViewById(R.id.tvTrackNumber)
            val name: TextView = view.findViewById(R.id.tvTrackName)
            val behavior: TextView = view.findViewById(R.id.tvTrackBehavior)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view =
                LayoutInflater.from(parent.context).inflate(R.layout.item_track, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val pair = pairs[position]
            val isCurrent = (position == currentPair)

            holder.number.text = "${position + 1}."

            if (isLeftDeck) {
                // ========== ЛЕВАЯ ДЕКА (FOH) ==========
                val songName = if (pair.name.isNotEmpty()) {
                    pair.name
                } else if (pair.fohFile.isNotEmpty()) {
                    pair.fohFile.substringBeforeLast(".wav")
                } else {
                    "..."
                }
                holder.name.text = songName

                val isTrackLoaded =
                    pair.fohFile.isNotEmpty() || pair.monFile.isNotEmpty() || pair.name.isNotEmpty()

                val behaviorDisplay = if (isFlowMode && isTrackLoaded) {
                    val localBehavior = pair.behavior.trim()
                    when {
                        localBehavior == "I" -> "[I]"
                        localBehavior == "P" -> "[P]"
                        localBehavior.matches(Regex("\\d+")) -> "[D${localBehavior}]"
                        localBehavior.isBlank() -> {
                            val globalBehavior = defaultBehavior.trim()
                            when (globalBehavior) {
                                "I" -> "[gI]"
                                "P" -> "[gP]"
                                else -> if (globalBehavior.matches(Regex("\\d+"))) "[gD$globalBehavior]" else ""
                            }
                        }

                        else -> ""
                    }
                } else {
                    ""
                }
                holder.behavior.text = behaviorDisplay

                // Бегущая строка
                if (isCurrent) {
                    holder.name.isSelected = true
                    holder.name.ellipsize = TextUtils.TruncateAt.MARQUEE
                } else {
                    holder.name.isSelected = false
                    holder.name.ellipsize = TextUtils.TruncateAt.END
                }

                // Долгий тап только для левой деки
                holder.itemView.setOnLongClickListener {
                    onItemLongClick?.invoke(position)
                    true
                }
                // =====================================

            } else {
                // ========== ПРАВАЯ ДЕКА (MON) ==========
                val isTrackLoaded = pair.fohFile.isNotEmpty() || pair.monFile.isNotEmpty()

                if (!isTrackLoaded) {
                    holder.name.text = "..."
                } else {
                    val fohName = pair.fohFile.substringBeforeLast(".wav").ifEmpty { "FOH" }
                    val monName = pair.monFile.substringBeforeLast(".wav").ifEmpty { "MON" }

                    val spannable = SpannableStringBuilder()

                    // FOH часть
                    spannable.append(fohName)
                    if (pair.fohMetaDisplay() != null) {
                        val metaText = " ${pair.fohMetaDisplay()}"
                        val startIndex = spannable.length
                        spannable.append(metaText)

                        if (pair.fohSampleRate != 48000) {
                            spannable.setSpan(
                                ForegroundColorSpan(Color.RED),
                                startIndex,
                                startIndex + metaText.length,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                    }

                    spannable.append(" | ")

                    // MON часть
                    spannable.append(monName)
                    if (pair.monMetaDisplay() != null) {
                        val metaText = " ${pair.monMetaDisplay()}"
                        val startIndex = spannable.length
                        spannable.append(metaText)

                        if (pair.monSampleRate != 48000) {
                            spannable.setSpan(
                                ForegroundColorSpan(Color.RED),
                                startIndex,
                                startIndex + metaText.length,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                    }

                    holder.name.text = spannable
                }
                holder.behavior.visibility = View.GONE
                // =====================================
            }

            // Бегущая строка для активного трека (для обеих дек)
            if (position == currentPair) {
                holder.name.isSelected = true
                holder.name.ellipsize = TextUtils.TruncateAt.MARQUEE
                holder.name.setMarqueeRepeatLimit(-1)
                holder.name.setSingleLine(true)
            } else {
                holder.name.isSelected = false
                holder.name.ellipsize = TextUtils.TruncateAt.END
            }

            // Раскраска
            if (isCurrent) {
                holder.itemView.setBackgroundColor(0xFF555555.toInt())
                holder.number.setTextColor(0xFFFFFFFF.toInt())
                holder.name.setTextColor(0xFFFFFFFF.toInt())
                holder.name.setTypeface(null, android.graphics.Typeface.BOLD)
                holder.behavior.setTextColor(0xFFFFFFFF.toInt())
            } else {
                holder.itemView.setBackgroundColor(0xFF222222.toInt())
                holder.number.setTextColor(0xFF888888.toInt())
                holder.name.setTextColor(0xFFCCCCCC.toInt())
                holder.name.setTypeface(null, android.graphics.Typeface.NORMAL)
                holder.behavior.setTextColor(0xFF888888.toInt())
            }

            // Короткий тап (для обеих дек)
            holder.itemView.setOnClickListener {
                onItemClick(position)
            }
        }

        override fun getItemCount() = 99

        fun updateCurrentPair(position: Int) {
            currentPair = position
            notifyDataSetChanged()
        }

        fun updateConfig(
            newIsFlowMode: Boolean,
            newIsMetronomeEnabled: Boolean,
            newDefaultBehavior: String = "I"
        ) {
            this.isFlowMode = newIsFlowMode
            this.isMetronomeEnabled = newIsMetronomeEnabled
            this.defaultBehavior = newDefaultBehavior
            notifyDataSetChanged()
        }

        fun updatePairs(newPairs: List<Pair>) {
            pairs = newPairs
            notifyDataSetChanged()
        }
    }

    // ========== АДАПТЕР ДЛЯ СПИСКА ПЛЕЙЛИСТОВ ==========
    class PlaylistAdapter(
        private val names: List<String>,
        private val onClick: (String) -> Unit,
        private val context: Context
    ) : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textView: TextView = view as TextView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val textView = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dpToPx(48)
                )
                setTextColor(Color.WHITE)
                textSize = 16f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpToPx(16), 0, dpToPx(16), 0)
                setBackgroundResource(R.drawable.item_selector)
            }
            return ViewHolder(textView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.textView.text = names[position]
            holder.itemView.setOnClickListener { onClick(names[position]) }
        }

        override fun getItemCount() = names.size

        private fun dpToPx(dp: Int): Int {
            return (dp * context.resources.displayMetrics.density).toInt()
        }
    }

    private fun loadAll() {
        // Если в ViewModel уже есть данные (не пустые) - не загружаем из SharedPreferences
        val currentPairs = viewModel.pairs
        if (currentPairs != null && currentPairs.any { it.fohFile.isNotEmpty() || it.monFile.isNotEmpty() }) {
            return  // уже есть данные, выходим
        }

        val p = getSharedPreferences("dj", MODE_PRIVATE)

        // ========== ПРОВЕРЯЕМ, ЕСТЬ ЛИ ВООБЩЕ СОХРАНЕННЫЕ ДАННЫЕ ==========
        if (!p.contains("left") && !p.contains("right")) {
            // НЕТ СОХРАНЕННЫХ ДАННЫХ - СТАВИМ ДЕФОЛТЫ
            defaultBehavior = "I"
            lastLoadedPlaylist = ""
            viewModel.updateDefaultBehavior("I")
            viewModel.updatePlaylistName("NoNameList")
            updatePlaylistName("NoNameList")
            return
        }
        // ====================================================================

        val type = object : TypeToken<List<String>>() {}.type

        val leftList = gson.fromJson<List<String>>(p.getString("left", "[]"), type) ?: listOf()
        val rightList =
            gson.fromJson<List<String>>(p.getString("right", "[]"), type) ?: listOf()
        val namesList =
            gson.fromJson<List<String>>(p.getString("names", "[]"), type) ?: listOf()
        val behaviorList =
            gson.fromJson<List<String>>(p.getString("behavior", "[]"), type) ?: listOf()

        defaultBehavior = p.getString("default_behavior", "I") ?: "I"  // ← INSTANT ПО УМОЛЧАНИЮ
        lastLoadedPlaylist = p.getString("last_playlist", "") ?: ""

        for (i in 0 until 99) {
            val fohFile = if (i < leftList.size) leftList[i] else ""
            val monFile = if (i < rightList.size) rightList[i] else ""
            val name = if (i < namesList.size) namesList[i] else ""
            val behavior = if (i < behaviorList.size) behaviorList[i] else ""

            viewModel.updatePairFiles(i, fohFile, monFile)
            viewModel.updatePairName(i, name)
            viewModel.updatePairBehavior(i, behavior)
        }

        viewModel.updatePlaylistName(if (lastLoadedPlaylist.isNotEmpty()) lastLoadedPlaylist else "NoNameList")
        viewModel.updateDefaultBehavior(defaultBehavior)
    }

    @SuppressLint("SetTextI18n")
    private fun refreshAll() {
        Log.d("LOAD_DEBUG", "refreshAll() START")
        refreshAdapters()

        // 1. АВТО-ИСПРАВЛЕНИЕ: Если текущий трек пустой, ищем первый заполненный
        var pair = viewModel.pairs?.get(currentPairIndex)
        if (pair != null && !pair.isValid()) {
            for (i in 0 until 99) {
                val p = viewModel.pairs?.get(i)
                if (p != null && p.isValid()) {
                    currentPairIndex = i
                    viewModel.updateCurrentPairIndex(currentPairIndex) // Обновляем ViewModel
                    pair = p // Берем найденный трек
                    break
                }
            }
        }

        // Если всё равно ничего не нашли (совсем пустой плейлист)
        if (pair == null || !pair.isValid()) {
            pair = Pair(1, " ", " ", " ")
        }

        // 2. Номер текущей пары
        tvPairNumber.text = "${pair.number}."

        // 3. ВЕРХНЯЯ ПАНЕЛЬ: ИМЯ ПОКАЗЫВАЕМ ВСЕГДА, НЕЗАВИСИМО ОТ isPlaying!
        val displayName = if (pair.name.isNotBlank()) {  // ← isNotBlank() вместо isNotEmpty()
            pair.name
        } else if (pair.fohFile.isNotBlank()) {  // ← isNotBlank() вместо isNotEmpty()
            pair.fohFile.substringBeforeLast(".wav")
        } else {
            "--"
        }

        tvPairName.text = displayName
        tvPairName.visibility = View.VISIBLE

        // Текст ВСЕГДА белый
        tvPairName.setTextColor(Color.WHITE)
        tvPairNumber.setTextColor(Color.WHITE)

        val songContainer = findViewById<android.widget.FrameLayout>(R.id.pairNameContainer)

        if (isPlaying) {
            // ТРЕК ИГРАЕТ: бегущая строка + ПУЛЬСАЦИЯ ФОНА
            tvPairName.isSelected = true
            tvPairName.ellipsize = TextUtils.TruncateAt.MARQUEE
            startCardPulse(songContainer)
        } else {
            // ТРЕК СТОИТ: троеточие, СТОП ПУЛЬСАЦИЯ
            tvPairName.isSelected = false
            tvPairName.ellipsize = TextUtils.TruncateAt.END
            stopCardPulse(songContainer)
        }
        if (isPlaying) {
            tvNowPlaying.text = "Now playing:"
            tvNowPlaying.setTextColor(Color.parseColor("#BBBBBB"))
        } else {
            tvNowPlaying.text = "Standby:"
            tvNowPlaying.setTextColor(Color.parseColor("#888888"))
        }
        // 4. Кнопки и прочее
        btnPlayStop.text = if (isPlaying) "STOP" else "PLAY"
        btnPlayStop.backgroundTintList = ContextCompat.getColorStateList(
            this,
            if (isPlaying) android.R.color.holo_red_dark else R.color.white
        )

        btnFlow.backgroundTintList = if (isFlowMode) {
            ColorStateList.valueOf(Color.GREEN)
        } else {
            ColorStateList.valueOf(Color.WHITE)
        }

        leftRecyclerView.scrollToPosition(currentPairIndex)
        rightRecyclerView.scrollToPosition(currentPairIndex)

       // metronomeManager.songUpdate(pair.bpm.toIntOrNull() ?: 120, metronomeManager.isGloballyEnabled.value == true)

        updateMetronomeDisplay()
    }


    override fun onBackPressed() {
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            // Второе нажатие - ВЫРУБАЕМ НАХРЕН
            finishAffinity()

            // Для Android 10+ убиваем процесс
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                finishAndRemoveTask()
            }

            // Полное уничтожение процесса
            android.os.Process.killProcess(android.os.Process.myPid())

        } else {
            backPressedTime = System.currentTimeMillis()
            ToastHelper.showCustomToast(
                this,
                "Press BACK again to exit",
                "info",
                Toast.LENGTH_SHORT
            )
        }
    }


    private val localReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "LOCAL_UPDATE_CHANNEL_ROUTING" -> {
                    if (usbDeviceName.isNotEmpty()) {
                        val keyPrefix = "routing_${usbDeviceName.replace(" ", "_")}"

                        // Читаем позицию спиннера (0, 1, 2, 3, 4)
                        val fohL_pos = intent.getIntExtra("foh_l_channel", prefs.getInt("${keyPrefix}_foh_l", 0))
                        val fohR_pos = intent.getIntExtra("foh_r_channel", prefs.getInt("${keyPrefix}_foh_r", 0))
                        val monL_pos = intent.getIntExtra("mon_l_channel", prefs.getInt("${keyPrefix}_mon_l", 0))
                        val monR_pos = intent.getIntExtra("mon_r_channel", prefs.getInt("${keyPrefix}_mon_r", 0))

                        // Преобразуем позицию спиннера в индекс массива
                        val fohL = if (fohL_pos == 0) -1 else fohL_pos - 1
                        val fohR = if (fohR_pos == 0) -1 else fohR_pos - 1
                        val monL = if (monL_pos == 0) -1 else monL_pos - 1
                        val monR = if (monR_pos == 0) -1 else monR_pos - 1

                        if (enginePtr != 0L) {
                            setChannelRouting(enginePtr, fohL, fohR, monL, monR)
                            Log.d("MainActivity", "✅ Routing applied: FOH L=$fohL R=$fohR, MON L=$monL R=$monR")
                        }
                    }
                }

                "LOCAL_STOP_PLAYBACK" -> {
                    Log.d("MainActivity", "🛑 Stop playback requested from SettingsActivity")
                    runOnUiThread {
                        if (isPlaying) stopPlayback()
                    }
                }
                "LOCAL_TOGGLE_PLAYBACK" -> {
                    if (isPlaying) stopPlayback() else playCurrentPair()
                }
                "LOCAL_NEXT_TRACK" -> {
                    if (currentPairIndex < 98) {
                        currentPairIndex++
                        if (isPlaying) stopPlayback()
                        refreshAll()
                        viewModel.updateCurrentPairIndex(currentPairIndex)
                    }
                }
                "LOCAL_PREV_TRACK" -> {
                    if (currentPairIndex > 0) {
                        currentPairIndex--
                        if (isPlaying) stopPlayback()
                        refreshAll()
                        viewModel.updateCurrentPairIndex(currentPairIndex)
                    }
                }
                "LOCAL_SET_VOLUME" -> {
                    val fohVol = intent.getFloatExtra("foh_vol", seekFoh.progress / 100f)
                    val monVol = intent.getFloatExtra("mon_vol", seekMon.progress / 100f)
                    val metrVol = intent.getFloatExtra("metr_vol", 1f)  // пока не используется
                    seekFoh.progress = (fohVol * 100).toInt()
                    seekMon.progress = (monVol * 100).toInt()
                    if (enginePtr != 0L && isPlaying(enginePtr)) {
                        setVolumes(enginePtr, fohVol, monVol)
                    }
                    // для метронома аналогично, когда будет готово
                }
            }
        }
    }


    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(localReceiver)
        Log.d("MainActivity", "✅ LocalReceiver unregistered in onDestroy")
        scope.cancel()
        stopPlayback()
        audioManager.unregisterAudioDeviceCallback(usbDeviceCallback)
        tvCountdown.setOnClickListener(null)
        if (enginePtr != 0L) {
            destroyEngine(enginePtr)
            enginePtr = 0L
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(localReceiver)
        metronomeManager.setEnginePtr(0L)
        super.onDestroy()
    }

    // ========== ПЕРЕМЕННЫЕ ДЛЯ АНИМАЦИИ ПУЛЬСАЦИИ ==========
    private var pulseColorAnimator: android.animation.ValueAnimator? = null
    private var pulseScaleAnimator: android.animation.ObjectAnimator? = null

    private fun startCardPulse(container: android.widget.FrameLayout) {
        // 1. Анимация ЦВЕТА (от тёмного к ЯРКО-ЗЕЛЁНОМУ)
        if (pulseColorAnimator == null) {
            pulseColorAnimator = android.animation.ValueAnimator.ofArgb(
                0xFF333333.toInt(), // Исходный тёмный (из applyRoundedCorners)
                0xFF008800.toInt()  // Явный зелёный цвет подсветки
            ).apply {
                duration = 800
                repeatCount = android.animation.ValueAnimator.INFINITE
                repeatMode = android.animation.ValueAnimator.REVERSE
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()

                addUpdateListener { animation ->
                    val drawable =
                        container.background as? android.graphics.drawable.GradientDrawable
                    if (drawable != null) {
                        drawable.setColor(animation.animatedValue as Int)
                    }
                }
            }
        }
        if (pulseColorAnimator?.isRunning == false) {
            pulseColorAnimator?.start()
        }

        // 2. Анимация МАСШТАБА (настоящая пульсация туда-сюда)
        if (pulseScaleAnimator == null) {
            pulseScaleAnimator = android.animation.ObjectAnimator.ofPropertyValuesHolder(
                container,
                android.animation.PropertyValuesHolder.ofFloat("scaleX", 1f, 1.04f),
                android.animation.PropertyValuesHolder.ofFloat("scaleY", 1f, 1.04f)
            ).apply {
                duration = 800
                repeatCount = android.animation.ObjectAnimator.INFINITE
                repeatMode = android.animation.ObjectAnimator.REVERSE
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            }
        }
        if (pulseScaleAnimator?.isRunning == false) {
            pulseScaleAnimator?.start()
        }
    }

    private fun stopCardPulse(container: android.widget.FrameLayout) {
        // 1. Останавливаем обе анимации
        pulseColorAnimator?.cancel()
        pulseScaleAnimator?.cancel()

        // 2. Жёстко возвращаем исходный тёмный цвет
        val drawable = container.background as? android.graphics.drawable.GradientDrawable
        drawable?.setColor(0xFF333333.toInt())

        // 3. Жёстко возвращаем исходный размер
        container.scaleX = 1f
        container.scaleY = 1f
    }

    companion object {
        // ========== JNI ДЛЯ МЕТРОНОМА ==========

        external fun setMetronomeEnabled(enginePtr: Long, enabled: Boolean)
        external fun setMetronomeBpm(enginePtr: Long, bpm: Int)
        external fun setMetronomeVolume(enginePtr: Long, volume: Float)
        external fun setMetronomeChannel(
            enginePtr: Long,
            channelMode: Int
        ) // 0-left, 1-right, 2-both

        external fun setMetronomeHolding(enginePtr: Long, holding: Boolean)
        external fun resetMetronome(enginePtr: Long)
        external fun setMetronomeStrongFreq(enginePtr: Long, freq: Int)
        external fun setMetronomeWeakFreq(enginePtr: Long, freq: Int)
        external fun setMetronomeWaveform(enginePtr: Long, type: Int)
        external fun setMetronomeClickDuration(enginePtr: Long, ms: Int)
        external fun checkFormatSupport(deviceId: Int, sampleRate: Int, formatCode: Int): Boolean


    }
        external fun setPlaybackSpeed(enginePtr: Long, speed: Float)
        external fun extractPeaks(fd: Int, offset: Long, length: Long, bitsPerSample: Int, formatTag: Int, numPeaks: Int): FloatArray
        external fun createEngine(): Long
        external fun destroyEngine(enginePtr: Long)
        external fun startEngine(enginePtr: Long): Boolean
        external fun stopEngine(enginePtr: Long)
        external fun isPlaying(enginePtr: Long): Boolean
        external fun setVolumes(enginePtr: Long, vol1: Float, vol2: Float)
        external fun getAudioDeviceInfo(enginePtr: Long): String
        external fun setDeviceId(enginePtr: Long, deviceId: Int)
        external fun setPairsSwap(enginePtr: Long, swap: Boolean)
        private external fun setTrackDataInfo(
            enginePtr: Long,
            fohDataOffset: Long, fohBlockAlign: Int,
            monDataOffset: Long, monBlockAlign: Int
        )
        private external fun setTrackFdsSeek(enginePtr: Long, fohFd: Int, fohDataLength: Long, monFd: Int, monDataLength: Long)
        private external fun setTrackParams(enginePtr: Long, fohSr: Int, fohBps: Int, fohFmt: Int, monSr: Int, monBps: Int, monFmt: Int)
        private external fun seekFd(fd: Int, position: Long)

        external fun setChannelRouting(enginePtr: Long, fohL: Int, fohR: Int, monL: Int, monR: Int)
        external fun getFohSampleRate(enginePtr: Long): Int
        external fun getFohBitDepth(enginePtr: Long): Int
        external fun getMonSampleRate(enginePtr: Long): Int
        external fun getMonBitDepth(enginePtr: Long): Int

        external fun getFohLeft(enginePtr: Long): Float
        external fun getFohRight(enginePtr: Long): Float
        external fun getMonLeft(enginePtr: Long): Float
        external fun getMonRight(enginePtr: Long): Float
        external fun getCurrentPosition(enginePtr: Long): Long
        external fun resetVuLevels(enginePtr: Long)
        external fun setPosition(enginePtr: Long, position: Long)
        external fun setScratchActive(enginePtr: Long, active: Boolean)

        external fun getFohLength(enginePtr: Long): Long
        external fun isStreamOpen(enginePtr: Long): Boolean
        external fun openStream(enginePtr: Long, deviceId: Int, formatIndex: Int): Boolean

}


