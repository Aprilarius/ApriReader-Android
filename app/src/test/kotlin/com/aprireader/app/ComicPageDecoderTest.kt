package com.aprireader.app

import com.aprireader.app.data.reader.ComicPageDecoder
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `ComicPageDecoder.decode()` требует настоящий `BitmapFactory` (недоступен в
 * юнит-тестах), но подбор коэффициента даунсемплинга — чистая функция,
 * которую стоит проверить отдельно: это ровно та арифметика, которая раньше
 * была вручную скопирована внутри composable-функции ридера без единого теста.
 */
class ComicPageDecoderTest {

    @Test
    fun `image already within bounds is not downsampled`() {
        assertEquals(1, ComicPageDecoder.computeSampleSize(1080, 1920, 2160, 4320))
    }

    @Test
    fun `wide image is downsampled to the next power of two`() {
        // 4320 / 2 = 2160 <= 2160 — одного шага достаточно.
        assertEquals(2, ComicPageDecoder.computeSampleSize(4320, 3000, 2160, 4320))
    }

    @Test
    fun `tall portrait page respects the taller height budget`() {
        // Типичная страница комикса: намного выше, чем шире.
        // 3000/1=3000 > 2160 -> sample=2; 3000/2=1500 <= 2160, 9000/2=4500 <= 8640 -> ок.
        assertEquals(2, ComicPageDecoder.computeSampleSize(3000, 9000, 2160, 8640))
    }

    @Test
    fun `extreme resolution keeps doubling until it fits`() {
        // 20000 needs four halvings to drop under 2160 (2500 at sample=8 still over).
        assertEquals(16, ComicPageDecoder.computeSampleSize(20000, 30000, 2160, 4320))
    }

    @Test
    fun `invalid dimensions fall back to no downsampling`() {
        assertEquals(1, ComicPageDecoder.computeSampleSize(0, 100, 2160, 4320))
        assertEquals(1, ComicPageDecoder.computeSampleSize(100, -1, 2160, 4320))
        assertEquals(1, ComicPageDecoder.computeSampleSize(100, 100, 0, 4320))
    }
}
