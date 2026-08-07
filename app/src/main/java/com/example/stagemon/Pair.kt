package com.example.stagemon

data class Pair(
    val number: Int,
    var name: String,
    var fohFile: String,
    var monFile: String,
    var pattern: String = "",
    var bpm: String = "",
    var behavior: String = "",
    var fohSampleRate: Int = 0,
    var fohBitDepth: Int = 0,
    var fohFormatTag: Int = 1,    // ← ДОБАВИТЬ (1=PCM int, 3=IEEE float)
    var monSampleRate: Int = 0,
    var monBitDepth: Int = 0,
    var monFormatTag: Int = 1     // ← ДОБАВИТЬ
)
{
    // Вспомогательная функция для отображения в левой деке (на главном экране)
    fun getFohDisplayName(showBehavior: Boolean = false): String {
        val nameWithoutExt = fohFile.substringBeforeLast(".wav")
        val base = if (fohFile.isNotEmpty()) nameWithoutExt else "—"
        return if (showBehavior) {
            when {
                behavior.isEmpty() -> "$base []"      // использует DEFAULT
                behavior == "P" -> "$base [P]"
                behavior == "I" -> "$base [I]"
                else -> "$base [$behavior]"           // число
            }
        } else {
            base
        }
    }

    // Вспомогательная функция для отображения в правой деке (на главном экране)
    fun getMonDisplayName(showBehavior: Boolean = false): String {
        val nameWithoutExt = monFile.substringBeforeLast(".wav")
        val base = if (monFile.isNotEmpty()) nameWithoutExt else "—"
        return if (showBehavior) {
            when {
                behavior.isEmpty() -> "$base []"
                behavior == "P" -> "$base [P]"
                behavior == "I" -> "$base [I]"
                else -> "$base [$behavior]"
            }
        } else {
            base
        }
    }

    // Вспомогательная функция для отображения имени пары сверху (на главном экране)
    fun getPairDisplayName(showBehavior: Boolean = false): String {
        val base = if (name.isNotEmpty()) "$number. $name" else "$number. Pair"
        return if (showBehavior) {
            when {
                behavior.isEmpty() -> "$base []"
                behavior == "P" -> "$base [P]"
                behavior == "I" -> "$base [I]"
                else -> "$base [$behavior]"
            }
        } else {
            base
        }
    }

    // Проверка, что пара не пустая (есть оба файла)
    fun isValid(): Boolean = fohFile.isNotBlank() && monFile.isNotBlank()

    // Получить поведение для отображения в скобках
    fun getBehaviorDisplay(): String {
        return when {
            behavior.isEmpty() -> "[]"
            behavior == "P" -> "[P]"
            behavior == "I" -> "[I]"
            else -> "[$behavior]"
        }
    }

    // Получить BPM для отображения
    fun getBpmDisplay(): String {
        return if (bpm.isNotEmpty()) "$bpm bpm" else "-- bpm"
    }

    fun fohMetaDisplay(): String? {
        if (fohSampleRate <= 0 || fohBitDepth <= 0) return null
        return WavInfo(fohSampleRate, fohBitDepth, fohFormatTag, 2, 0, 0, 0).displayMeta()
    }

    fun monMetaDisplay(): String? {
        if (monSampleRate <= 0 || monBitDepth <= 0) return null
        return WavInfo(monSampleRate, monBitDepth, monFormatTag, 2, 0, 0, 0).displayMeta()
    }
}