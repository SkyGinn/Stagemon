package com.example.stagemon

import android.content.Context
import android.net.Uri
import android.system.Os
import android.util.Log
import java.io.FileDescriptor

data class WavInfo(
    val sampleRate: Int,
    val bitsPerSample: Int,
    val formatTag: Int,
    val channels: Int,
    val blockAlign: Int,
    val dataOffset: Long,
    val dataSize: Long
) {
    fun displaySampleRate(): String = when (sampleRate) {
        44100 -> "44.1k"
        48000 -> "48k"
        88200 -> "88.2k"
        96000 -> "96k"
        176400 -> "176.4k"
        192000 -> "192k"
        else -> "${sampleRate}Hz"
    }

    fun displayBitDepth(): String = when {
        formatTag == 3 && bitsPerSample == 32 -> "f32"
        formatTag == 3 && bitsPerSample == 64 -> "f64"
        else -> bitsPerSample.toString()
    }

    fun displayMeta(): String = "{${displaySampleRate()}/${displayBitDepth()}}"

    fun is48k(): Boolean = sampleRate == 48000
}

object WavHeaderParser {
    private const val TAG = "WavHeaderParser"

    fun parse(context: Context, uri: Uri): WavInfo? {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                parse(pfd.fileDescriptor, pfd.statSize)
            }
        } catch (e: Exception) {
            Log.e(TAG, "parse failed for $uri: ${e.message}", e)
            null
        }
    }

    fun parse(fd: FileDescriptor, fileSize: Long): WavInfo? {
        if (fileSize < 44) return null

        val riff = readAt(fd, 0, 12) ?: return null
        if (String(riff, 0, 4) != "RIFF" || String(riff, 8, 4) != "WAVE") {
            Log.e(TAG, "Not a WAVE file")
            return null
        }

        var pos = 12L
        var formatTag = 0
        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var blockAlign = 0
        var dataOffset = -1L
        var dataSize = -1L

        while (pos + 8 <= fileSize) {
            val chunkHeader = readAt(fd, pos, 8) ?: break
            val chunkId = String(chunkHeader, 0, 4)
            val chunkSize = readLe32(chunkHeader, 4).toLong() and 0xFFFFFFFFL
            val chunkDataStart = pos + 8

            if (chunkDataStart + chunkSize > fileSize) {
                Log.e(TAG, "Chunk $chunkId extends past EOF")
                break
            }

            when (chunkId) {
                "fmt " -> {
                    val fmtLen = minOf(chunkSize, 40L).toInt()
                    val fmt = readAt(fd, chunkDataStart, fmtLen) ?: return null
                    if (fmt.size >= 16) {
                        formatTag = readLe16(fmt, 0)
                        channels = readLe16(fmt, 2)
                        sampleRate = readLe32(fmt, 4)
                        blockAlign = readLe16(fmt, 12)
                        bitsPerSample = readLe16(fmt, 14)

                        // ✅ ОБРАБОТКА WAVEFORMATEXTENSIBLE (formatTag = 65534)
                        if (formatTag == 0xFFFE && fmt.size >= 40) {
                            val cbSize = readLe16(fmt, 16)
                            if (cbSize >= 22) {
                                val validBitsPerSample = readLe16(fmt, 18)
                                if (validBitsPerSample > 0 && validBitsPerSample <= 32) {
                                    bitsPerSample = validBitsPerSample  // ← используем реальную битность!
                                }
                            }
                        }

                    }
                }
                "data" -> {
                    dataOffset = chunkDataStart
                    dataSize = chunkSize
                }
            }

            pos = chunkDataStart + chunkSize
            if (chunkSize % 2L == 1L) pos++

            if (dataOffset >= 0 && sampleRate > 0 && bitsPerSample > 0) break
        }

        if (dataOffset < 0 || sampleRate <= 0 || bitsPerSample <= 0) {
            Log.e(TAG, "Incomplete WAV: dataOffset=$dataOffset sr=$sampleRate bps=$bitsPerSample")
            return null
        }

        if (blockAlign <= 0) {
            blockAlign = channels * ((bitsPerSample + 7) / 8)
        }

        val info = WavInfo(
            sampleRate = sampleRate,
            bitsPerSample = bitsPerSample,
            formatTag = formatTag,
            channels = channels,
            blockAlign = blockAlign,
            dataOffset = dataOffset,
            dataSize = dataSize
        )
        Log.d(TAG, "Parsed: $info")
        return info
    }

    private fun readAt(fd: FileDescriptor, position: Long, size: Int): ByteArray? {
        if (size <= 0) return ByteArray(0)
        val buf = ByteArray(size)
        return try {
            val read = Os.pread(fd, buf, 0, size, position)
            when {
                read <= 0 -> null
                read == size -> buf
                else -> buf.copyOf(read)
            }
        } catch (e: Exception) {
            Log.e(TAG, "pread at $position failed: ${e.message}")
            null
        }
    }

    private fun readLe16(buf: ByteArray, offset: Int): Int {
        return (buf[offset].toInt() and 0xFF) or
                ((buf[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readLe32(buf: ByteArray, offset: Int): Int {
        return (buf[offset].toInt() and 0xFF) or
                ((buf[offset + 1].toInt() and 0xFF) shl 8) or
                ((buf[offset + 2].toInt() and 0xFF) shl 16) or
                ((buf[offset + 3].toInt() and 0xFF) shl 24)
    }
}
