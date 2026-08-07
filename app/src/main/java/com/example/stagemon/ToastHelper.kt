package com.example.stagemon

import android.content.Context
import android.graphics.Color
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast

object ToastHelper {
    private var currentToast: Toast? = null  // ← ДОБАВИ: для отмены предыдущего

    fun showCustomToast(
        context: Context,
        message: String,
        type: String = "success",
        duration: Int = Toast.LENGTH_SHORT
    ) {
        currentToast?.cancel()  // ← ОТМЕНИ ПРЕДЫДУЩИЙ, ЧТОБЫ НЕ ДУБЛИРОВАЛО!!!

        val inflater = LayoutInflater.from(context)
        val layout = inflater.inflate(R.layout.custom_toast, null)

        val textView = layout.findViewById<TextView>(R.id.toastText)
        val iconView = layout.findViewById<TextView>(R.id.toastIcon)

        textView.text = message

        when (type) {
            "plain" -> {
                iconView.visibility = android.view.View.GONE
            }
            "success" -> {
                iconView.text = "✓"
                iconView.setTextColor(Color.GREEN)
                layout.backgroundTintList = ColorStateList.valueOf(0xFF1E1E1E.toInt())
            }
            "error" -> {
                iconView.text = "✗"
                iconView.setTextColor(Color.RED)
                layout.backgroundTintList = ColorStateList.valueOf(0xFF2A1E1E.toInt())
            }
            "warning" -> {
                iconView.text = "⚠"
                iconView.setTextColor(Color.YELLOW)
                layout.backgroundTintList = ColorStateList.valueOf(0xFF2A2A1E.toInt())
            }
            "info" -> {
                iconView.text = "ℹ"
                iconView.setTextColor(Color.CYAN)
                layout.backgroundTintList = ColorStateList.valueOf(0xFF1E2A2A.toInt())
            }
        }

        val toast = Toast(context)
        toast.duration = duration
        toast.view = layout
        toast.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 12)
        toast.show()

        currentToast = toast  // ← СОХРАНИ ТЕКУЩИЙ ДЛЯ ОТМЕНЫ СЛЕДУЮЩИМ
    }
}