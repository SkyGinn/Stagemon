package com.example.stagemon

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.graphics.drawable.GradientDrawable
import androidx.core.content.res.ResourcesCompat
import com.example.stagemon.R
import android.text.TextUtils

class EditorAdapter(
    private val pairs: MutableList<Pair>,
    private val onItemMoved: (Int, Int) -> Unit,
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<EditorAdapter.ViewHolder>() {

    private lateinit var context: Context
    private var dragHandleListener: OnDragHandleListener? = null

    interface OnDragHandleListener {
        fun onStartDrag(viewHolder: RecyclerView.ViewHolder)
    }

    fun setDragHandleListener(listener: OnDragHandleListener) {
        this.dragHandleListener = listener
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val number: TextView
        val name: TextView
        val files: TextView
        val behavior: TextView
        val dragHandle: TextView

        init {
            number = TextView(view.context)
            name = TextView(view.context)
            files = TextView(view.context)
            behavior = TextView(view.context)
            dragHandle = TextView(view.context)

            // Настройка LayoutParams для files
            files.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            files.setTextSize(14f)
            files.setTextColor(0xFF888888.toInt())  // серый цвет

            // Для number используем обычный LayoutParams
            number.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            // ДЛЯ name НУЖЕН LinearLayout.LayoutParams, потому что weight работает только в LinearLayout!
            name.layoutParams = LinearLayout.LayoutParams(
                0,  // width = 0 (заполнит оставшееся место)
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f  // weight = 1
            )

            behavior.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            dragHandle.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            dragHandle.text = "⋮⋮⋮⋮⋮⋮"
            dragHandle.textSize = 20f
            dragHandle.gravity = Gravity.CENTER_VERTICAL
            dragHandle.setPadding(20, 0, 20, 0)

            (view as LinearLayout).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(number)
                addView(name)
                addView(files)
                addView(behavior)
                addView(dragHandle)
                dragHandle.isClickable = true
                dragHandle.isFocusable = true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        context = parent.context
        val view = LinearLayout(context)

        val textView = TextView(context)
        textView.textSize = 16f
        val textHeight = textView.lineHeight
        val verticalPadding = dpToPx(context, 12)

        view.layoutParams = ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            textHeight + verticalPadding
        )
        view.setPadding(16, 0, 16, 0)

        view.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20f
            setColor(0xFF333333.toInt())
        }

        val holder = ViewHolder(view)

        // Настройка dragHandle
        holder.dragHandle.isClickable = true
        holder.dragHandle.isFocusable = true

        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pair = pairs[position]

        if (position < itemCount - 1) {
            val layoutParams = holder.itemView.layoutParams as ViewGroup.MarginLayoutParams
            layoutParams.bottomMargin = dpToPx(context, 4)
            holder.itemView.layoutParams = layoutParams
        }

        val typeface = ResourcesCompat.getFont(context, R.font.myfont)  // Загружаем шрифт

        holder.number.text = "${position + 1}."
        holder.number.setTextSize(16f)
        holder.number.setPadding(0, 0, 16, 0)
        holder.number.typeface = typeface

        val hasFiles = pair.fohFile.isNotEmpty() || pair.monFile.isNotEmpty()

        // ========== НАЗВАНИЕ ПЕСНИ ==========
        val songName = if (pair.name.isNotEmpty()) {
            pair.name  // если есть имя - показываем его
        } else if (hasFiles) {
            "Song"     // если есть пары но нет имени - пишем Song
        } else {
            ""         // если нет пар - пусто
        }
        holder.name.text = songName
        holder.name.setTextSize(16f)
        holder.name.gravity = Gravity.CENTER_VERTICAL
        holder.name.setPadding(0, 0, 16, 0)
        holder.name.typeface = typeface
        holder.name.setSingleLine(true)              // ← одна строка
        holder.name.ellipsize = TextUtils.TruncateAt.END  // ← многоточие в конце

        // ========== ФАЙЛЫ ПОСЕРЕДИНЕ ==========
        if (hasFiles) {
            val fohDisplay = if (pair.fohFile.isNotEmpty()) {
                pair.fohFile.substringBeforeLast(".wav")
            } else {
                "—"
            }

            val monDisplay = if (pair.monFile.isNotEmpty()) {
                pair.monFile.substringBeforeLast(".wav")
            } else {
                "—"
            }

            holder.files.text = "$fohDisplay | $monDisplay"
            holder.files.setTextSize(14f)
            holder.files.gravity = Gravity.CENTER
            holder.files.visibility = View.VISIBLE
            holder.files.setPadding(16, 0, 16, 0)
            holder.files.typeface = typeface
            holder.files.setSingleLine(true)
            holder.files.ellipsize = TextUtils.TruncateAt.END
        } else {
            holder.files.text = ""
            holder.files.visibility = View.GONE
        }

        // ========== ПОВЕДЕНИЕ ==========
        val behaviorText = when {
            pair.behavior.isEmpty() -> ""
            pair.behavior == "P" -> "pause"
            pair.behavior == "I" -> "instant"
            else -> "delay ${pair.behavior} sec"
        }
        holder.behavior.text = behaviorText
        holder.behavior.setTextSize(14f)
        holder.behavior.gravity = Gravity.CENTER
        holder.behavior.visibility = if (behaviorText.isNotEmpty()) View.VISIBLE else View.GONE
        holder.behavior.setPadding(16, 0, 16, 0)
        holder.behavior.typeface = typeface
        holder.behavior.setSingleLine(true)
        holder.behavior.ellipsize = TextUtils.TruncateAt.END

        holder.dragHandle.setOnTouchListener { _, event ->
            dragHandleListener?.onStartDrag(holder)
            false
        }



        holder.dragHandle.typeface = typeface

        holder.itemView.setOnClickListener {
            onItemClick(position)
        }
    }


    override fun getItemCount() = pairs.size

    fun updateNumbers() {
        for (i in pairs.indices) {
            pairs[i] = pairs[i].copy(number = i + 1)
        }
        notifyDataSetChanged()
    }



    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}