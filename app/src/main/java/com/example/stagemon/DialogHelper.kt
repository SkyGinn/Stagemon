package com.example.stagemon

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import com.example.stagemon.R
import com.example.stagemon.Pair
import android.util.Log

object DialogHelper {

    // ========== ДИАЛОГ СОХРАНЕНИЯ ==========
    fun showSaveDialog(
        activity: AppCompatActivity,
        playlistName: String,
        onConfirm: () -> Unit
    ) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Save changes to:")
            .setMessage("$playlistName ?")
            .setPositiveButton("Yes") { _, _ ->
                onConfirm()
            }
            .setNegativeButton("No", null)
            .create()

        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 40f
                setColor(0xFF1E1E1E.toInt())
            }
        )

        dialog.show()
    }

    // ========== ДИАЛОГ ЗАГРУЗКИ ==========
    // ========== ДИАЛОГ ЗАГРУЗКИ ==========
    fun showListDialog(
        activity: AppCompatActivity,
        title: String,
        items: Array<String>,
        onItemClick: (String) -> Unit
    ) {
        // Создаем кастомный адаптер с отступами
        val adapter = object : ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.gravity = Gravity.CENTER_VERTICAL
                textView.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                textView.setTextColor(Color.WHITE)
                textView.textSize = 18f
                textView.setPadding(50, 20, 50, 20)  // ← увеличил отступы
                return view
            }
        }

        // Создаем LinearLayout для заголовка с разделителем
        val headerLayout = LinearLayout(activity)
        headerLayout.orientation = LinearLayout.VERTICAL
        headerLayout.setPadding(50, 30, 50, 0)

        // Заголовок
        val titleView = TextView(activity)
        titleView.text = title
        titleView.setTextColor(Color.WHITE)
        titleView.textSize = 20f
        titleView.gravity = Gravity.CENTER
        titleView.setPadding(0, 0, 0, 20)
        headerLayout.addView(titleView)

        // Линия-разделитель
        val divider = View(activity)
        divider.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            2  // толщина линии
        )
        divider.setBackgroundColor(0xFF444444.toInt())  // серый цвет
        divider.setPadding(0, 0, 0, 10)
        headerLayout.addView(divider)

        // Создаем ListView
        val listView = ListView(activity)
        listView.adapter = adapter
        listView.divider = null  // убираем стандартный разделитель
        listView.setPadding(0, 10, 0, 10)

        // Создаем главный layout
        val mainLayout = LinearLayout(activity)
        mainLayout.orientation = LinearLayout.VERTICAL
        mainLayout.addView(headerLayout)
        mainLayout.addView(listView)

        val dialog = AlertDialog.Builder(activity)
            .setView(mainLayout)  // ← используем кастомный layout вместо стандартного
            .setNegativeButton("Back", null)
            .create()

        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 40f
                setColor(0xFF1E1E1E.toInt())
            }
        )

        dialog.show()

        // Обработка кликов на ListView
        listView.setOnItemClickListener { _, _, position, _ ->
            onItemClick(items[position])
            dialog.dismiss()
        }
    }

    // ========== ДИАЛОГ ОЧИСТКИ ==========
    fun showClearDialog(
        activity: AppCompatActivity,
        onConfirm: () -> Unit
    ) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Clear all songs?")
            .setMessage("")
            .setPositiveButton("Yes") { _, _ ->
                onConfirm()
            }
            .setNegativeButton("No", null)
            .create()

        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 40f
                setColor(0xFF1E1E1E.toInt())
            }
        )

        dialog.show()
    }

    // ========== ДИАЛОГ DISCARD ==========
    fun showDiscardDialog(
        activity: AppCompatActivity,
        onConfirm: () -> Unit
    ) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Discard changes?")
            .setMessage("")
            .setPositiveButton("Yes") { _, _ ->
                onConfirm()
            }
            .setNegativeButton("No", null)
            .create()

        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 40f
                setColor(0xFF1E1E1E.toInt())
            }
        )

        dialog.show()
    }

    // ========== ДИАЛОГ ВЫХОДА ==========
    fun showExitDialog(
        activity: AppCompatActivity,
        playlistName: String,
        fromMain: Boolean,
        hasChanges: Boolean,
        onSave: () -> Unit,
        onDiscard: () -> Unit,
        onCancel: () -> Unit
    ) {
        if (!fromMain) {
            onDiscard()
            return
        }

        if (!hasChanges) {
            onDiscard()
            return
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Save changes to \"$playlistName\"?")
            .setMessage("")
            .setPositiveButton("Save") { _, _ ->
                onSave()
            }
            .setNegativeButton("Discard") { _, _ ->
                onDiscard()
            }
            //.setNeutralButton("Cancel") { _, _ ->
            //    onCancel()
            // }
            .create()

        dialog.window?.setBackgroundDrawable(
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 40f
                setColor(0xFF1E1E1E.toInt())
            }
        )

        dialog.show()
    }

    // ========== ДИАЛОГ РЕДАКТИРОВАНИЯ ПЕСНИ ==========
    fun showEditSongDialog(
        activity: FragmentActivity,
        position: Int,
        pair: Pair,
        fromMain: Boolean,
        onSave: (Int, String, String) -> Unit
    ) {
        Log.d("DEBUG_DIALOG", "showEditSongDialog fromMain = $fromMain, position = $position")
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_edit_pair, null)

        val etName = dialogView.findViewById<EditText>(R.id.etPairName)
        val rgBehavior = dialogView.findViewById<RadioGroup>(R.id.rgPairBehavior)
        val rbGlobal = dialogView.findViewById<RadioButton>(R.id.rbPairGlobal)
        val rbInstant = dialogView.findViewById<RadioButton>(R.id.rbPairInstant)
        val rbPause = dialogView.findViewById<RadioButton>(R.id.rbPairPause)
        val rbDelay = dialogView.findViewById<RadioButton>(R.id.rbPairDelay)
        val etDelay = dialogView.findViewById<EditText>(R.id.etPairDelay)
        val delayContainer = dialogView.findViewById<LinearLayout>(R.id.delayContainer)

        // ВРЕМЕННЫЕ ПЕРЕМЕННЫЕ
        var tempName = pair.name
        var tempBehavior = pair.behavior

        etName.setText(pair.name)
        etName.setSelection(etName.text.length)

        etName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val imm =
                    activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(etName.windowToken, 0)
                etName.clearFocus()
                true
            } else {
                false
            }
        }

        etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                tempName = s.toString().trim()
            }
        })

        when (pair.behavior) {
            "" -> rbGlobal.isChecked = true
            "P" -> rbPause.isChecked = true
            "I" -> rbInstant.isChecked = true
            else -> {
                rbDelay.isChecked = true
                delayContainer.visibility = View.VISIBLE
                etDelay.setText(pair.behavior)
                tempBehavior = pair.behavior
            }
        }

        rgBehavior.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbPairGlobal -> {
                    delayContainer.visibility = View.GONE
                    tempBehavior = ""
                }

                R.id.rbPairInstant -> {
                    delayContainer.visibility = View.GONE
                    tempBehavior = "I"
                }

                R.id.rbPairPause -> {
                    delayContainer.visibility = View.GONE
                    tempBehavior = "P"
                }

                R.id.rbPairDelay -> {
                    delayContainer.visibility = View.VISIBLE
                    etDelay.requestFocus()
                    tempBehavior = etDelay.text.toString()
                }
            }
        }

        etDelay.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (rbDelay.isChecked) {
                    tempBehavior = s.toString()
                }
            }
        })

        etDelay.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val original = s.toString()
                val digits = original.filter { it.isDigit() }
                if (original != digits) {
                    etDelay.setText(digits)
                    etDelay.setSelection(digits.length)
                }
            }
        })

        // ОБЪЯВЛЯЕМ ДИАЛОГ ДО БИЛДЕРА
        lateinit var dialog: AlertDialog

        val builder = AlertDialog.Builder(activity)
            .setTitle("Song #${position + 1}")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val editDialog = dialog
                val finalName = tempName
                val finalBehavior = when {
                    rbGlobal.isChecked -> ""
                    rbInstant.isChecked -> "I"
                    rbPause.isChecked -> "P"
                    rbDelay.isChecked -> {
                        val delayText = tempBehavior.filter { it.isDigit() }
                        if (delayText.isEmpty()) "5" else delayText
                    }
                    else -> pair.behavior
                }

                if (finalName != pair.name || finalBehavior != pair.behavior) {
                    if (fromMain) {
                        editDialog.hide()

                        // СНАЧАЛА СПРАШИВАЕМ
                        showExitDialog(
                            activity as AppCompatActivity,
                            "Song #${position + 1}: $finalName",
                            true,
                            true,
                            onSave = {
                                onSave(position, finalName, finalBehavior)  // ← ТОЛЬКО ТУТ!
                                editDialog.dismiss()
                            },
                            onDiscard = {
                                editDialog.dismiss()  // ← НЕ вызываем onSave!
                            },
                            onCancel = {
                                editDialog?.let {
                                    if (!it.isShowing) {
                                        it.show()
                                    }
                                }
                        }
                    )
                    }else {
                        // для редактора плейлиста - сразу сохраняем в память
                        onSave(position, finalName, finalBehavior)
                        editDialog.dismiss()
                    }
                } else {
                    editDialog.dismiss()
                }

            }
          /*  .setNegativeButton("Cancel") { _, _ ->
                val currentName = etName.text.toString().trim()
                val currentBehavior = when {
                    rbGlobal.isChecked -> ""
                    rbInstant.isChecked -> "I"
                    rbPause.isChecked -> "P"
                    rbDelay.isChecked -> etDelay.text.toString()
                    else -> pair.behavior
                }

                if (currentName != pair.name || currentBehavior != pair.behavior) {
                    dialog.hide()

                    if (fromMain) {  // ← ТОЛЬКО ДЛЯ ГЛАВНОГО ЭКРАНА
                        showDiscardDialog(activity as AppCompatActivity) {
                            dialog.dismiss()
                        }
                    } else {
                        dialog.dismiss()  // ← ДЛЯ РЕДАКТОРА ПРОСТО ЗАКРЫТЬ
                    }
                } else {
                    dialog.dismiss()
                }
         */

        dialog = builder.create()
        dialog.show()

        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 40f
                setColor(0xFF1E1E1E.toInt())
            }
        )
    }
}