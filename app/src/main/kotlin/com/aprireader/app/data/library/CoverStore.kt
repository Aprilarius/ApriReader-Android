package com.aprireader.app.data.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.palette.graphics.Palette
import java.io.File

/**
 * Хранилище обложек: приводит найденную в книге картинку к разумному размеру,
 * кладёт в приватную директорию приложения и сразу извлекает акцентный цвет.
 *
 * Извлечение делается один раз при импорте, а не при каждом открытии книги:
 * Palette — заметная работа, а обложка не меняется.
 */
class CoverStore(private val context: Context) {

    private val dir: File by lazy { File(context.filesDir, "covers").apply { mkdirs() } }

    data class Stored(val path: String, val accent: Int?)

    fun save(bookId: String, bytes: ByteArray): Stored? {
        val bitmap = decodeScaled(bytes, MAX_DIMENSION) ?: return null
        val file = File(dir, "$bookId.webp")
        val ok = runCatching {
            file.outputStream().use { out ->
                val format = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                bitmap.compress(format, 88, out)
            }
        }.isSuccess
        if (!ok) return null
        val accent = extractAccent(bitmap)
        bitmap.recycle()
        return Stored(file.absolutePath, accent)
    }

    fun delete(bookId: String) {
        File(dir, "$bookId.webp").delete()
    }

    fun accentOf(path: String): Int? {
        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
        val bitmap = BitmapFactory.decodeFile(path, options) ?: return null
        return extractAccent(bitmap).also { bitmap.recycle() }
    }

    /**
     * Отправная точка — Palette API. Выбор идёт по «живости» свотча: самый
     * заметный цвет обложки не всегда пригоден как акцент, поэтому предпочтение
     * отдаётся насыщенным вариантам, а окончательная коррекция контраста
     * происходит уже при построении цветовой схемы.
     */
    private fun extractAccent(bitmap: Bitmap): Int? {
        val palette = runCatching {
            Palette.from(bitmap).clearFilters().maximumColorCount(24).generate()
        }.getOrNull() ?: return null

        val candidates = listOfNotNull(
            palette.vibrantSwatch,
            palette.lightVibrantSwatch,
            palette.darkVibrantSwatch,
            palette.mutedSwatch,
            palette.darkMutedSwatch,
            palette.lightMutedSwatch,
        )
        if (candidates.isEmpty()) return palette.dominantSwatch?.rgb

        return candidates.maxByOrNull { swatch ->
            val hsl = swatch.hsl
            val saturation = hsl[1]
            val lightness = hsl[2]
            // Штрафуем крайние светлоты: из них не получится читаемого акцента.
            val lightnessScore = 1f - kotlin.math.abs(lightness - 0.5f) * 1.4f
            saturation * 2f + lightnessScore + swatch.population / 100_000f
        }?.rgb
    }

    private fun decodeScaled(bytes: ByteArray, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) }.getOrNull()
    }

    private companion object {
        const val MAX_DIMENSION = 900
    }
}
