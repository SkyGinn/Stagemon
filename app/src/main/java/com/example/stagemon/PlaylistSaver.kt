package com.example.stagemon

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson

object PlaylistSaver {

    private val gson = Gson()

    fun saveNamedList(
        context: Context,
        name: String,
        viewModel: PlaylistViewModel,
        defaultBehavior: String
    ) {
        Log.d("SAVE_DEBUG", "=== НАЧАЛО СОХРАНЕНИЯ ===")
        Log.d("SAVE_DEBUG", "Имя файла: $name.txt")
        Log.d("SAVE_DEBUG", "Default behavior: $defaultBehavior")
        Log.d("SAVE_DEBUG", "Количество пар: ${viewModel.pairs?.size ?: 0}")

        try {
            val prefs = context.getSharedPreferences("stagemon", Context.MODE_PRIVATE)
            val playlistsUri = prefs.getString("playlists_folder_uri", null)
            Log.d("SAVE_DEBUG", "playlists_folder_uri: $playlistsUri")

            if (playlistsUri == null) {
                Log.e("SAVE_DEBUG", "ОШИБКА: playlists_folder_uri = null")
                ToastHelper.showCustomToast(context, "Folder not selected", "error")
                return
            }

            val playlistsFolder = DocumentFile.fromTreeUri(context, Uri.parse(playlistsUri))
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

                    // ========== НОВЫЙ ФОРМАТ С pattern И bpm ==========
                    // Паттерн: если пустой то {}, если есть то {значение}
                    val patternStr = if (pair.pattern.isEmpty()) "{}" else "{${pair.pattern}}"

                    // BPM: если пустой то [], если есть то [значение]
                    val bpmStr = if (pair.bpm.isEmpty()) "[]" else "[${pair.bpm}]"

                    // Поведение: как было
                    val behaviorStr = when {
                        pair.behavior.isEmpty() -> "[]"
                        pair.behavior == "P" -> "[P]"
                        pair.behavior == "I" -> "[I]"
                        else -> "[${pair.behavior}]"
                    }

                    // Отображаемое имя (если пустое - берём из имени файла)
                    val displayName = pair.name.ifEmpty {
                        val fileName = if (pair.fohFile.isNotEmpty()) pair.fohFile else pair.monFile
                        fileName.substringBeforeLast(".wav")
                    }

                    // Формируем строку: номер | имя | foh | mon | {pattern} | [bpm] | [behavior]
                    sb.append("${pair.number} | $displayName | ${pair.fohFile} | ${pair.monFile} | $patternStr | $bpmStr | $behaviorStr\n")
                }
            }
            Log.d("SAVE_DEBUG", "Найдено непустых пар: $pairCount")
            Log.d("SAVE_DEBUG", "Содержимое файла:\n$sb")

            context.contentResolver.openOutputStream(txtFile.uri)?.use { outputStream ->
                outputStream.write(sb.toString().toByteArray())
                Log.d("SAVE_DEBUG", "Файл успешно записан")
            }

            // Проверяем содержимое папки ПОСЛЕ сохранения
            val filesAfter = playlistsFolder.listFiles()
            Log.d("SAVE_DEBUG", "Файлов в папке ПОСЛЕ: ${filesAfter.size}")
            filesAfter.forEachIndexed { index, file ->
                Log.d("SAVE_DEBUG", "  Файл $index: ${file.name} (${file.uri})")
            }

            ToastHelper.showCustomToast(context, "Saved: $name", "success")
            Log.d("SAVE_DEBUG", "=== СОХРАНЕНИЕ УСПЕШНО ===")

        } catch (e: Exception) {
            Log.e("SAVE_DEBUG", "ОШИБКА СОХРАНЕНИЯ", e)
            ToastHelper.showCustomToast(context, "Error: ${e.message}", "error")
        }
    }
}