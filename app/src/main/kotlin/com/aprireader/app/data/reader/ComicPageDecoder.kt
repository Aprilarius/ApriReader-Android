package com.aprireader.app.data.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log

/**
 * Декодирование одной страницы комикса (CBZ/CBR) в готовый для показа битмап.
 *
 * Раньше это делалось прямо в composable-функции ридера — там же, где решалось,
 * когда именно запускать декодирование. Из-за этого логика декодирования,
 * защита от параллельной перегрузки памяти и обработка нехватки памяти были
 * размазаны по UI-коду и дублировались бы при любом втором месте, которому
 * понадобится показать страницу комикса. Здесь — единственное место, которое
 * превращает байты страницы в битмап; UI (см. [BookSession.Comic]) отвечает
 * только за то, откуда эти байты взять и что показывать, пока идёт декодирование.
 */
internal object ComicPageDecoder {

    private const val TAG = "ComicPageDecoder"

    /**
     * Во сколько раз нужно уменьшить исходное изображение [rawWidth]×[rawHeight],
     * чтобы обе стороны уложились в [maxWidth]×[maxHeight].
     *
     * Чистая функция без обращения к Android SDK — можно проверить юнит-тестом
     * на JVM без эмулятора, в отличие от самого декодирования (там нужен
     * реальный `BitmapFactory`).
     */
    fun computeSampleSize(rawWidth: Int, rawHeight: Int, maxWidth: Int, maxHeight: Int): Int {
        if (rawWidth <= 0 || rawHeight <= 0 || maxWidth <= 0 || maxHeight <= 0) return 1
        var sample = 1
        while (rawWidth / sample > maxWidth || rawHeight / sample > maxHeight) {
            sample *= 2
        }
        return sample
    }

    /**
     * Декодирует страницу с даунсемплингом под ширину экрана [targetWidthPx].
     *
     * Возвращает `null` в ровно двух случаях, и оба логируются, чтобы разница
     * между «этот файл нечитаем» и «не хватило памяти» была видна в Logcat:
     * — [bytes] не являются декодируемым изображением (испорченная страница,
     *   неподдерживаемый формат картинки внутри архива);
     * — двух попыток декодирования всё равно не хватило памяти.
     *
     * При `OutOfMemoryError` декодирование повторяется один раз с вдвое более
     * грубым семплингом вместо немедленного провала: полученная страница всё
     * равно растягивается на ширину экрана, так что заметной потери качества
     * нет, а шанс уложиться в доступную память — особенно когда пейджер
     * декодирует несколько соседних страниц параллельно — заметно выше.
     */
    fun decode(bytes: ByteArray, targetWidthPx: Int): Bitmap? {
        if (bytes.isEmpty()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.w(TAG, "decode: invalid bounds (${bounds.outWidth}x${bounds.outHeight}) — not a decodable image")
            return null
        }

        // Портретные страницы комиксов обычно заметно выше, чем шире экрана,
        // поэтому по высоте допускается вдвое больше, чем по ширине.
        val maxWidth = targetWidthPx.coerceAtLeast(1080) * 2
        val maxHeight = maxWidth * 2
        val sample = computeSampleSize(bounds.outWidth, bounds.outHeight, maxWidth, maxHeight)

        repeat(2) { attempt ->
            val effectiveSample = if (attempt == 0) sample else sample * 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = effectiveSample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            } catch (oom: OutOfMemoryError) {
                if (attempt == 0) {
                    Log.w(TAG, "decode: OutOfMemoryError at sample=$effectiveSample, retrying coarser")
                    null
                } else {
                    Log.w(TAG, "decode: OutOfMemoryError again at sample=$effectiveSample, giving up")
                    return null
                }
            }
            if (bitmap != null) return bitmap
        }
        return null
    }
}
