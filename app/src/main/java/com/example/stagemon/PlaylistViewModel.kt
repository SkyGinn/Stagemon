package com.example.stagemon

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.collections.get
import kotlin.text.set

class PlaylistViewModel : ViewModel() {
/*
    private val _pairs = MutableLiveData<MutableList<Pair>>()
    val pairs: LiveData<MutableList<Pair>> = _pairs
*/
    var pairs = MutableList(99) { Pair(it + 1, "", "", "") }
    // Это твои 99 песен, где Pair — это ТВОЙ data class!
    private val _pairs = MutableLiveData<List<Pair>>(pairs)
    val pairsLive: LiveData<List<Pair>> get() = _pairs

    private val _defaultBehavior = MutableLiveData<String>()
    val defaultBehavior: LiveData<String> = _defaultBehavior

    private val _playlistName = MutableLiveData<String>()
    val playlistName: LiveData<String> = _playlistName

    private val _currentPairIndex = MutableLiveData<Int>()
    val currentPairIndex: LiveData<Int> = _currentPairIndex

    // ========== НОВЫЕ ПОЛЯ ДЛЯ МЕТРОНОМА ==========
    private val _metronomeEnabled = MutableLiveData<Boolean>(false)
    val metronomeEnabled: LiveData<Boolean> = _metronomeEnabled

    private val _metronomeBpm = MutableLiveData<Int>(120)
    val metronomeBpm: LiveData<Int> = _metronomeBpm

    private val _metronomeTimeSignature = MutableLiveData<String>("4/4")
    val metronomeTimeSignature: LiveData<String> = _metronomeTimeSignature

    private val _metronomeChannel = MutableLiveData<String>("both") // left, right, both
    val metronomeChannel: LiveData<String> = _metronomeChannel

    private val _metronomeVolume = MutableLiveData<Int>(100) // 0-100
    val metronomeVolume: LiveData<Int> = _metronomeVolume



    init {
        // Инициализация с 99 пустыми парами
        _pairs.value = MutableList(99) { index -> Pair(index + 1, "", "", "") }
        _defaultBehavior.value = "I"
        _playlistName.value = "NonameList"
        _currentPairIndex.value = 0
    }

    // Загрузка данных из Intent
    fun loadFromIntent(pairsJson: String?, behavior: String?, name: String?) {
        pairsJson?.let {
            val type = object : TypeToken<List<Pair>>() {}.type
            val loadedPairs = Gson().fromJson<List<Pair>>(it, type)
            if (loadedPairs != null) {
                _pairs.value = loadedPairs.toMutableList()
                pairs = loadedPairs.toMutableList()
            }
        }
        _defaultBehavior.value = behavior ?: ""
        _playlistName.value = name ?: "NonameList"
    }

    // ========== НОВЫЙ МЕТОД ДЛЯ ОЧИСТКИ ПАРЫ ==========
    fun clearPair(index: Int) {
        _pairs.value?.let { currentPairs ->
            val updated = currentPairs.toMutableList()
            if (index in updated.indices) {
                updated[index] = Pair(index + 1, "", "", "", "")
                _pairs.value = updated
                pairs = updated
            }
        }
    }
    // =================================================

    // ========== МЕТОД ДЛЯ ДОБАВЛЕНИЯ/ОБНОВЛЕНИЯ ПАРЫ ==========
    fun addOrUpdatePair(baseName: String, fileName: String, isLeft: Boolean) {
        val currentPairs = _pairs.value?.toMutableList() ?: return

        // 1. Ищем существующую пару по базовому имени
        var foundIndex = -1
        for (i in currentPairs.indices) {
            val pair = currentPairs[i]

            val fohBase = if (pair.fohFile.endsWith(".foh.wav"))
                pair.fohFile.substringBefore(".foh.wav") else ""
            val monBase = if (pair.monFile.endsWith(".mon.wav"))
                pair.monFile.substringBefore(".mon.wav") else ""

            if (fohBase == baseName || monBase == baseName) {
                foundIndex = i
                break
            }
        }

        if (foundIndex != -1) {
            // 2. Нашли пару - обновляем нужную деку
            val pair = currentPairs[foundIndex]

            if (isLeft && pair.fohFile.isEmpty()) {
                currentPairs[foundIndex] = pair.copy(fohFile = fileName)
            } else if (!isLeft && pair.monFile.isEmpty()) {
                currentPairs[foundIndex] = pair.copy(monFile = fileName)
            }

            // 3. Если имя пустое ИЛИ равно "Song" - заполняем из baseName
            val updatedPair = currentPairs[foundIndex]
            if (updatedPair.name.isEmpty() || updatedPair.name == "Song") {
                currentPairs[foundIndex] = updatedPair.copy(name = baseName)
            }
            // Если имя уже есть - НЕ ТРОГАЕМ!

        } else {
            // 4. Нет пары - создаём новую
            for (i in currentPairs.indices) {
                if (currentPairs[i].fohFile.isEmpty() && currentPairs[i].monFile.isEmpty()) {
                    if (isLeft) {
                        currentPairs[i] = Pair(i + 1, baseName, fileName, "")
                    } else {
                        currentPairs[i] = Pair(i + 1, baseName, "", fileName)
                    }
                    break
                }
            }
        }

        _pairs.value = currentPairs
        pairs = currentPairs
    }

    // Обновление поведения конкретной пары
    fun updatePairBehavior(index: Int, behavior: String) {
        _pairs.value?.let { currentPairs ->
            val updated = currentPairs.toMutableList()
            if (index in updated.indices) {
                val pair = updated[index]
                updated[index] = pair.copy(behavior = behavior)
                _pairs.value = updated
            }
        }
    }

    // Обновление имени пары
    fun updatePairName(index: Int, name: String) {
        _pairs.value?.let { currentPairs ->
            val updated = currentPairs.toMutableList()
            if (index in updated.indices) {
                val pair = updated[index]
                updated[index] = pair.copy(name = name)
                _pairs.value = updated
            }
        }
    }

    // Обновление файлов пары
    fun updatePairFiles(index: Int, fohFile: String, monFile: String) {
        _pairs.value?.let { currentPairs ->
            val updated = currentPairs.toMutableList()
            if (index in updated.indices) {
                val pair = updated[index]
                updated[index] = pair.copy(fohFile = fohFile, monFile = monFile)
                _pairs.value = updated
                pairs = updated
            }
        }
    }

    // Перемещение пары
    fun movePair(from: Int, to: Int) {
        _pairs.value?.let { currentPairs ->
            val updated = currentPairs.toMutableList()
            val temp = updated[from]
            if (from < to) {
                for (i in from until to) {
                    updated[i] = updated[i + 1]
                }
            } else {
                for (i in from downTo to + 1) {
                    updated[i] = updated[i - 1]
                }
            }
            updated[to] = temp

            // Обновляем номера
            for (i in updated.indices) {
                updated[i] = updated[i].copy(number = i + 1)
            }

            _pairs.value = updated
            pairs = updated
        }
    }

    // Обновление паттерна пары
    fun updatePairPattern(index: Int, pattern: String) {
        _pairs.value?.let { currentPairs ->
            val updated = currentPairs.toMutableList()
            if (index in updated.indices) {
                val pair = updated[index]
                updated[index] = pair.copy(pattern = pattern)
                _pairs.value = updated
                pairs = updated
            }
        }
    }

    // Обновление BPM пары
    fun updatePairBpm(index: Int, bpm: String) {
        _pairs.value?.let { currentPairs ->
            val updated = currentPairs.toMutableList()
            if (index in updated.indices) {
                val pair = updated[index]
                updated[index] = pair.copy(bpm = bpm)
                _pairs.value = updated
                pairs = updated
            }
        }
    }



    // Обновление дефолтного поведения
    fun updateDefaultBehavior(behavior: String) {
        _defaultBehavior.value = behavior
    }

    // Обновление названия плейлиста
    fun updatePlaylistName(name: String) {
        _playlistName.value = name
    }

    // Обновление текущего индекса
    fun updateCurrentPairIndex(index: Int) {
        _currentPairIndex.value = index
    }

    // Очистка всех данных
    fun clearAll() {
        _pairs.value = MutableList(99) { index -> Pair(index + 1, "", "", "") }
        pairs = _pairs.value!!.toMutableList()
        _defaultBehavior.value = "I"
        _playlistName.value = "NonameList"
        _currentPairIndex.value = 0
    }

    // Получить копию текущих пар для сохранения
    fun getPairsForSave(): List<Pair> {
        return _pairs.value?.toList() ?: emptyList()
    }

    fun updatePairMetadata(
        index: Int,
        fohSampleRate: Int,
        fohBitDepth: Int,
        fohFormatTag: Int,
        monSampleRate: Int,
        monBitDepth: Int,
        monFormatTag: Int
    ) {
        Log.d("METADATA", "updatePairMetadata called for index=$index, FOH=$fohSampleRate/$fohBitDepth, MON=$monSampleRate/$monBitDepth")

        val current = _pairs.value?.toMutableList() ?: return
        if (index in current.indices) {
            val old = current[index]
            current[index] = old.copy(
                fohSampleRate = fohSampleRate,
                fohBitDepth = fohBitDepth,
                fohFormatTag = fohFormatTag,
                monSampleRate = monSampleRate,
                monBitDepth = monBitDepth,
                monFormatTag = monFormatTag
            )
            _pairs.value = current
            pairs = current

            Log.d("METADATA", "✅ Metadata updated for index=$index, new FOH=${current[index].fohSampleRate}/${current[index].fohBitDepth}")
        }
    }

    // ========== МЕТОДЫ ДЛЯ МЕТРОНОМА ==========
    fun setMetronomeEnabled(enabled: Boolean) {
        _metronomeEnabled.value = enabled
    }

    fun setMetronomeBpm(bpm: Int) {
        _metronomeBpm.value = bpm
    }

    fun setMetronomeTimeSignature(signature: String) {
        _metronomeTimeSignature.value = signature
    }

    fun setMetronomeChannel(channel: String) {
        _metronomeChannel.value = channel
    }

    fun setMetronomeVolume(volume: Int) {
        _metronomeVolume.value = volume
    }
}