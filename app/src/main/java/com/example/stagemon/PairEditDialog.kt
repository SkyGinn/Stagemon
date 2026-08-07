/*
package com.example.stagemon

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import com.example.stagemon.R
import com.example.stagemon.Pair

object PairEditDialog {

    fun show(
        activity: FragmentActivity,
        position: Int,
        pair: Pair,
        onSave: (Int, String, String) -> Unit
    ) {
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

        // Начальные значения
        etName.setText(pair.name)
        etName.setSelection(etName.text.length)

        // Скрываем клавиатуру по Done
        etName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(etName.windowToken, 0)
                etName.clearFocus()
                true
            } else {
                false
            }
        }

        // Отслеживаем изменение имени
        etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                tempName = s.toString().trim()
            }
        })

        // Устанавливаем начальное поведение
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

        // Обработчик радиокнопок - сохраняем во временную переменную
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

        // Обработчик изменения поля Delay
        etDelay.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (rbDelay.isChecked) {
                    tempBehavior = s.toString()
                }
            }
        })

        // Валидация ввода - только цифры
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

        // Создаем основной диалог
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Song #${position + 1}")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
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
                    // Скрываем основной диалог
                    dialog.hide()

                    // Показываем диалог сохранения
                    val saveDialog = AlertDialog.Builder(activity)
                        .setTitle("Save changes?")
                        .setMessage("Song #${position + 1}: $finalName")
                        .setPositiveButton("Save") { _, _ ->
                            onSave(position, finalName, finalBehavior)
                            dialog.dismiss()
                        }
                        .setNegativeButton("Cancel") { _, _ ->
                            // Просто показываем основной диалог обратно
                            dialog.show()
                        }
                        .create()

                    saveDialog.window?.setBackgroundDrawable(
                        android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                            cornerRadius = 40f
                            setColor(0xFF1E1E1E.toInt())
                        }
                    )

                    saveDialog.show()
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                val currentName = etName.text.toString().trim()
                val currentBehavior = when {
                    rbGlobal.isChecked -> ""
                    rbInstant.isChecked -> "I"
                    rbPause.isChecked -> "P"
                    rbDelay.isChecked -> etDelay.text.toString()
                    else -> pair.behavior
                }

                if (currentName != pair.name || currentBehavior != pair.behavior) {
                    // Скрываем основной диалог
                    dialog.hide()

                    // Показываем диалог отмены
                    val discardDialog = AlertDialog.Builder(activity)
                        .setTitle("Discard changes?")
                        .setMessage("Song #${position + 1}: $currentName")
                        .setPositiveButton("Discard") { _, _ ->
                            dialog.dismiss()
                        }
                        .setNegativeButton("Keep editing") { _, _ ->
                            // Просто показываем основной диалог обратно
                            dialog.show()
                        }
                        .create()

                    discardDialog.window?.setBackgroundDrawable(
                        android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                            cornerRadius = 40f
                            setColor(0xFF1E1E1E.toInt())
                        }
                    )

                    discardDialog.show()
                } else {
                    dialog.dismiss()
                }
            }
            .create()

        dialog.show()

        // Стилизация основного диалога
        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 40f
                setColor(0xFF1E1E1E.toInt())
            }
        )
    }
}

 */