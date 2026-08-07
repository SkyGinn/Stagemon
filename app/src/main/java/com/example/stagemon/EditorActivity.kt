package com.example.stagemon

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import android.graphics.drawable.GradientDrawable
import android.view.inputmethod.InputMethodManager
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import android.view.inputmethod.EditorInfo
import android.content.pm.ActivityInfo
import android.text.TextWatcher
import android.text.Editable

private var isSaving = false

class EditorActivity : AppCompatActivity(), EditorAdapter.OnDragHandleListener {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: EditorAdapter
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var defaultPause: RadioButton
    private lateinit var defaultInstant: RadioButton
    private lateinit var defaultDelay: RadioButton
    private lateinit var defaultDelayValue: EditText
    private lateinit var viewModel: PlaylistViewModel
    private lateinit var tvPlaylistTitle: TextView

    private var fromMain = false
    private val pairs = mutableListOf<Pair>()
    private var defaultBehavior = ""
    private val gson = Gson()
    private var hasChanges = false
    private lateinit var itemTouchHelper: ItemTouchHelper
    private var playlistName: String = "NonameList"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE  // ← ЛЭНДШАФТ
        setContentView(R.layout.activity_editor)

        viewModel = ViewModelProvider(this)[PlaylistViewModel::class.java]

        // Загружаем данные в ViewModel
        viewModel.loadFromIntent(
            pairsJson = intent.getStringExtra("pairs"),
            behavior = intent.getStringExtra("default_behavior"),
            name = intent.getStringExtra("playlist_name")
        )

        // Наблюдаем за изменениями ViewModel
        viewModel.pairsLive.observe(this) { newPairs ->
            // Запоминаем текущий порядок из pairs (то, что пользователь натаскал)
            val currentNames = pairs.map { it.name }
            val currentFoh = pairs.map { it.fohFile }
            val currentMon = pairs.map { it.monFile }
            val currentBehavior = pairs.map { it.behavior }

            // Обновляем список из ViewModel
            pairs.clear()
            pairs.addAll(newPairs)

            // Восстанавливаем порядок, если он был изменён
            for (i in pairs.indices) {
                if (i < currentNames.size) {
                    pairs[i] = pairs[i].copy(
                        name = currentNames[i],
                        fohFile = currentFoh[i],
                        monFile = currentMon[i],
                        behavior = currentBehavior[i]
                    )
                }
            }
            adapter.notifyDataSetChanged()
        }

        defaultBehavior = intent.getStringExtra("default_behavior") ?: ""
        playlistName = intent.getStringExtra("playlist_name") ?: "NoNameList"

        initViews()
        setupRecyclerView()
        setupDefaultBehavior()
        setupButtons()

        fromMain = intent.getBooleanExtra("from_main", false)  // ← А ЭТО УЖЕ ЕСТЬ
        Log.d("DEBUG", "fromMain = $fromMain")

        tvPlaylistTitle.text = playlistName
        tvPlaylistTitle.setOnClickListener { editPlaylistName() }

        // ========== ЕСЛИ ЭТО НОВЫЙ ПУСТОЙ ПЛЕЙЛИСТ - СТАВИМ INSTANT ==========
        if (playlistName == "NoNameList" && pairs.none { it.fohFile.isNotEmpty() || it.monFile.isNotEmpty() }) {
            defaultBehavior = "I"
            defaultInstant.isChecked = true
            viewModel.updateDefaultBehavior("I")
        }
        // ====================================================================

        hasChanges = false
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.editorRecyclerView)
        btnSave = findViewById(R.id.btnSaveEditor)
        btnCancel = findViewById(R.id.btnCancelEditor)
        defaultPause = findViewById(R.id.defaultPause)
        defaultInstant = findViewById(R.id.defaultInstant)
        defaultDelay = findViewById(R.id.defaultDelay)
        defaultDelayValue = findViewById(R.id.defaultDelayValue)
        tvPlaylistTitle = findViewById(R.id.tvPlaylistTitle)

        val defaultDelayContainer = findViewById<LinearLayout>(R.id.defaultDelayContainer)

        defaultPause.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                defaultDelayContainer.visibility = View.GONE
                hasChanges = true
            }
        }

        defaultInstant.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                defaultDelayContainer.visibility = View.GONE
                hasChanges = true
            }
        }

        defaultDelay.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                defaultDelayContainer.visibility = View.VISIBLE
                hasChanges = true
            } else {
                defaultDelayContainer.visibility = View.GONE
            }
        }
    }

    private fun editPlaylistName() {
        // Делаем поле редактируемым
        tvPlaylistTitle.isFocusableInTouchMode = true
        tvPlaylistTitle.isFocusable = true
        tvPlaylistTitle.requestFocus()

        // Показываем клавиатуру
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(tvPlaylistTitle, InputMethodManager.SHOW_IMPLICIT)

        // Добавляем слушатель изменений текста
        tvPlaylistTitle.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val newName = s.toString().trim()
                if (newName != playlistName) {
                    playlistName = newName
                    viewModel.updatePlaylistName(newName)
                    hasChanges = true
                }
            }
        })

        // Обработчик нажатия "Готово"
        tvPlaylistTitle.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val newName = tvPlaylistTitle.text.toString().trim()
                if (newName.isNotEmpty() && newName != playlistName) {
                    playlistName = newName
                    viewModel.updatePlaylistName(newName)
                    hasChanges = true
                }
                tvPlaylistTitle.clearFocus()
                imm.hideSoftInputFromWindow(tvPlaylistTitle.windowToken, 0)
                true
            } else {
                false
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = EditorAdapter(pairs, { from, to ->
            hasChanges = true
        }, { position ->
            val pair = pairs[position]
            DialogHelper.showEditSongDialog(
                this,
                position,
                pair,
                false  // ← fromMain = false (из редактора плейлиста)
            ) { pos, newName, newBehavior ->
                Log.d("DEBUG_SAVE", "onSave called for position $pos: name=$newName, behavior=$newBehavior")
                val updatedPair = pair.copy(name = newName, behavior = newBehavior)
                pairs[pos] = updatedPair
                viewModel.updatePairName(pos, newName)
                viewModel.updatePairBehavior(pos, newBehavior)
                hasChanges = true
                adapter.notifyItemChanged(pos)
            }
        })
        adapter.setDragHandleListener(this)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,  // ← ТОЛЬКО UP и DOWN!
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition

                // ПРЯМОЕ ПЕРЕМЕЩЕНИЕ КАК В РАБОЧЕЙ ВЕРСИИ (НЕ ТРОГАЕМ!)
                val temp = pairs[from]
                if (from < to) {
                    for (i in from until to) {
                        pairs[i] = pairs[i + 1]
                    }
                } else {
                    for (i in from downTo to + 1) {
                        pairs[i] = pairs[i - 1]
                    }
                }
                pairs[to] = temp

                // Обновляем номера
                for (i in pairs.indices) {
                    pairs[i] = pairs[i].copy(number = i + 1)
                }
                // for (i in pairs.indices) {
                //     val p = pairs[i]
                //     viewModel.updatePairName(i, p.name)
                //     viewModel.updatePairFiles(i, p.fohFile, p.monFile)
                //     viewModel.updatePairBehavior(i, p.behavior)
                // }


                adapter.notifyItemMoved(from, to)
                hasChanges = true


                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)


                // ========== ОБНОВЛЯЕМ НОМЕРА ==========
                for (i in pairs.indices) {
                    pairs[i] = pairs[i].copy(number = i + 1)
                }

                // ========== СОХРАНЯЕМ ВЕСЬ НОВЫЙ ПОРЯДОК В VIEWMODEL ==========
                for (i in pairs.indices) {
                    val p = pairs[i]  // ← БЕРЁМ ИЗ pairs!
                    viewModel.updatePairName(i, p.name)
                    viewModel.updatePairFiles(i, p.fohFile, p.monFile)
                    viewModel.updatePairBehavior(i, p.behavior)
                }

            }
        }

        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun setupDefaultBehavior() {
        when (defaultBehavior) {
            "P" -> defaultPause.isChecked = true
            "I" -> defaultInstant.isChecked = true
            else -> {
                defaultDelay.isChecked = true
                defaultDelayValue.setText(defaultBehavior)
            }
        }
        hasChanges = false
    }

    private fun setupButtons() {
        btnSave.setOnClickListener { saveAndReturn() }
        btnCancel.setOnClickListener { cancelAndReturn() }
    }


    private fun cancelAndReturn() {
        val currentName = tvPlaylistTitle.text.toString().trim()
        if (currentName != viewModel.playlistName.value) {
            hasChanges = true
        }

        val defaultName = if (currentName.isEmpty()) "NoNameList" else currentName

        if (hasChanges) {
            DialogHelper.showExitDialog(
                this,
                defaultName,
                fromMain,
                hasChanges,
                onSave = {
                    saveAndReturn()  // ← просто сохраняем
                },
                onDiscard = {
                    setResult(RESULT_CANCELED)
                    finish()
                },
                onCancel = {
                    // остаемся
                }
            )
        } else {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun saveAndReturn() {
        defaultBehavior = when {
            defaultPause.isChecked -> "P"
            defaultInstant.isChecked -> "I"
            defaultDelay.isChecked -> defaultDelayValue.text.toString()
            else -> ""
        }

        val currentName = tvPlaylistTitle.text.toString().trim()
        if (currentName.isEmpty()) {
            ToastHelper.showCustomToast(this, "Name cannot be empty", "error")
            return
        }

        // Сохраняем в ViewModel
        for (i in pairs.indices) {
            val p = pairs[i]
            viewModel.updatePairName(i, p.name)
            viewModel.updatePairFiles(i, p.fohFile, p.monFile)
            viewModel.updatePairBehavior(i, p.behavior)
        }

        viewModel.updateDefaultBehavior(defaultBehavior)
        viewModel.updatePlaylistName(currentName)

        if (isFinishing) return

        // ← ДОБАВЬ ДИАЛОГ С ТРЕМЯ КНОПКАМИ!
        DialogHelper.showExitDialog(
            this,
            currentName,
            fromMain,
            true,  // hasChanges = true
            onSave = {
                PlaylistSaver.saveNamedList(this, currentName, viewModel, defaultBehavior)

                // ========== ВОТ СЮДА ДОБАВЛЯЕМ ==========
                val prefs = getSharedPreferences("stagemon", Context.MODE_PRIVATE)
                prefs.edit().putString("last_loaded_playlist", currentName).apply()
                Log.d("SAVE_DEBUG", "Сохранено в last_loaded_playlist: $currentName")
                // ========================================

                hasChanges = false
                val intent = intent
                intent.putExtra("pairs", gson.toJson(viewModel.pairs))
                intent.putExtra("default_behavior", defaultBehavior)
                intent.putExtra("playlist_name", currentName)
                setResult(RESULT_OK, intent)
                finish()
            },
            onDiscard = {
                setResult(RESULT_CANCELED)
                finish()
            },
            onCancel = {
                // остаемся
            }
        )
    }


    private fun saveNamedList(name: String) {
        Log.d("SAVE_DEBUG", "=== НАЧАЛО СОХРАНЕНИЯ ===")
        Log.d("SAVE_DEBUG", "Имя файла: $name.txt")
        Log.d("SAVE_DEBUG", "Default behavior: $defaultBehavior")
        Log.d("SAVE_DEBUG", "Количество пар: ${viewModel.pairs?.size ?: 0}")

        try {
            val prefs = getSharedPreferences("stagemon", MODE_PRIVATE)
            val playlistsUri = prefs.getString("playlists_folder_uri", null)
            Log.d("SAVE_DEBUG", "playlists_folder_uri: $playlistsUri")

            if (playlistsUri == null) {
                Log.e("SAVE_DEBUG", "ОШИБКА: playlists_folder_uri = null")
                ToastHelper.showCustomToast(this, "Folder not selected", "error")
                return
            }

            val playlistsFolder = DocumentFile.fromTreeUri(this, Uri.parse(playlistsUri))
            Log.d("SAVE_DEBUG", "playlistsFolder exists: ${playlistsFolder?.exists()}")
            Log.d("SAVE_DEBUG", "playlistsFolder URI: ${playlistsFolder?.uri}")

            if (playlistsFolder == null || !playlistsFolder.exists()) {
                Log.e("SAVE_DEBUG", "ОШИБКА: Папка не существует!")
                return
            }

            // Проверяем содержимое папки ДО сохранения
            val filesBefore = playlistsFolder.listFiles()
            Log.d("SAVE_DEBUG", "Файлов в папке ДО: ${filesBefore.size}")
            filesBefore.forEachIndexed { index, file ->
                Log.d("SAVE_DEBUG", "  Файл $index: ${file.name}")
            }

            // Удаляем старый файл
            val oldFile = playlistsFolder.findFile("$name.txt")
            Log.d("SAVE_DEBUG", "Старый файл существует: ${oldFile?.exists()}")
            oldFile?.delete()

            // Создаём новый
            val txtFile = playlistsFolder.createFile("text/plain", "$name.txt")
            Log.d("SAVE_DEBUG", "Новый файл создан: ${txtFile != null}")
            Log.d("SAVE_DEBUG", "Новый файл URI: ${txtFile?.uri}")

            if (txtFile == null) {
                Log.e("SAVE_DEBUG", "ОШИБКА: Не удалось создать файл!")
                return
            }

            val sb = StringBuilder()
            sb.append("root\\\n")
            sb.append(if (defaultBehavior.isEmpty()) "[]\n" else "[$defaultBehavior]\n")

            var pairCount = 0
            for (i in 0 until 99) {
                val pair = viewModel.pairs?.getOrNull(i) ?: continue
                if (pair.fohFile.isNotEmpty() || pair.monFile.isNotEmpty()) {
                    pairCount++
                    val behaviorStr = when {
                        pair.behavior.isEmpty() -> "[]"
                        pair.behavior == "P" -> "[P]"
                        pair.behavior == "I" -> "[I]"
                        else -> "[${pair.behavior}]"
                    }

                    val displayName = pair.name.ifEmpty {
                        val fileName = if (pair.fohFile.isNotEmpty()) pair.fohFile else pair.monFile
                        fileName.substringBeforeLast(".wav")
                    }

                    sb.append("${pair.number} | $displayName | ${pair.fohFile} | ${pair.monFile} | $behaviorStr\n")
                }
            }
            Log.d("SAVE_DEBUG", "Найдено непустых пар: $pairCount")
            Log.d("SAVE_DEBUG", "Содержимое файла:\n$sb")

            contentResolver.openOutputStream(txtFile.uri)?.use { outputStream ->
                outputStream.write(sb.toString().toByteArray())
                Log.d("SAVE_DEBUG", "Файл успешно записан")
                hasChanges = false
            }

            // Проверяем содержимое папки ПОСЛЕ сохранения
            val filesAfter = playlistsFolder.listFiles()
            Log.d("SAVE_DEBUG", "Файлов в папке ПОСЛЕ: ${filesAfter.size}")
            filesAfter.forEachIndexed { index, file ->
                Log.d("SAVE_DEBUG", "  Файл $index: ${file.name} (${file.uri})")
            }

            ToastHelper.showCustomToast(this, "Saved: $name", "success")
            Log.d("SAVE_DEBUG", "=== СОХРАНЕНИЕ УСПЕШНО ===")

        } catch (e: Exception) {
            Log.e("SAVE_DEBUG", "ОШИБКА СОХРАНЕНИЯ", e)
            ToastHelper.showCustomToast(this, "Error: ${e.message}", "error")
        }
    }

    override fun onStartDrag(viewHolder: RecyclerView.ViewHolder) {
        itemTouchHelper.startDrag(viewHolder)
    }

    override fun onBackPressed() {
        val currentName = tvPlaylistTitle.text.toString().trim()
        val defaultName = if (currentName.isEmpty()) "NoNameList" else currentName

        if (hasChanges) {
            DialogHelper.showExitDialog(
                this,
                defaultName,
                fromMain,
                hasChanges,
                onSave = {
                    saveAndReturn()
                },
                onDiscard = {
                    setResult(RESULT_CANCELED)
                    finish()
                },
                onCancel = {
                    // остаемся
                }
            )
        } else {
            super.onBackPressed()
        }
    }

}