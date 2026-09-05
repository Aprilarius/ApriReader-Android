package com.aprireader.app

import com.aprireader.app.ui.reader.rsvp.RsvpEngine
import com.aprireader.app.ui.reader.text.anchorLength
import com.aprireader.app.ui.reader.text.bionicRanges
import com.aprireader.app.ui.reader.text.bionicTailRanges
import com.aprireader.bookformat.model.ContentBlock
import com.aprireader.bookformat.model.ParagraphKind
import com.aprireader.bookformat.model.RichText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BionicTextTest {

    @Test
    fun `anchor never covers the whole long word`() {
        assertEquals(1, anchorLength(1, 0.5f))
        assertEquals(1, anchorLength(3, 0.5f))
        assertEquals(2, anchorLength(4, 0.45f))
        // Интенсивность зажата в разумный диапазон, поэтому 0.95 работает как 0.8.
        assertEquals(8, anchorLength(10, 0.95f))
        assertTrue((4..20).all { length -> anchorLength(length, 0.5f) < length })
    }

    @Test
    fun `ranges cover word beginnings only`() {
        val text = "Мама мыла раму 42 раза"
        val ranges = bionicRanges(text, 0.5f)
        val anchors = ranges.map { text.substring(it.first, it.second) }
        assertEquals(listOf("Ма", "мы", "ра", "ра"), anchors)
    }

    @Test
    fun `tails complete the words`() {
        val text = "Привет мир"
        val tails = bionicTailRanges(text, 0.5f).map { text.substring(it.first, it.second) }
        assertEquals(listOf("вет", "ир"), tails)
    }
}

class RsvpEngineTest {

    private val blocks = listOf(
        ContentBlock.Heading(RichText("Глава первая"), 2),
        ContentBlock.Paragraph(RichText("Он шёл по длинной дороге, думая о доме."), ParagraphKind.BODY),
        ContentBlock.Separator,
    )

    @Test
    fun `frames follow the word order`() {
        val frames = RsvpEngine.buildFrames(blocks)
        assertEquals("Глава", frames.first().text)
        assertEquals(listOf("Глава", "первая", "Он", "шёл"), frames.take(4).map { it.text })
        assertEquals(frames.size - 1, frames.last().wordIndex)
    }

    @Test
    fun `chunking groups words`() {
        val frames = RsvpEngine.buildFrames(blocks, chunkSize = 2)
        assertEquals("Глава первая", frames.first().text)
        assertEquals(0, frames.first().wordIndex)
        assertEquals(2, frames[1].wordIndex)
    }

    @Test
    fun `pivot sits left of centre`() {
        assertEquals(0, RsvpEngine.pivotIndexOf("я"))
        assertEquals(1, RsvpEngine.pivotIndexOf("дом"))
        assertEquals(2, RsvpEngine.pivotIndexOf("дорога"))
        assertEquals(3, RsvpEngine.pivotIndexOf("путешествие"))
    }

    @Test
    fun `punctuation slows playback down`() {
        val frames = RsvpEngine.buildFrames(blocks)
        val afterComma = frames.first { it.text.endsWith(",") }
        val plain = frames.first { it.text == "Он" }
        assertTrue(afterComma.durationScale > plain.durationScale)

        val fast = RsvpEngine.frameDurationMs(plain, wordsPerMinute = 600, chunkSize = 1)
        val slow = RsvpEngine.frameDurationMs(plain, wordsPerMinute = 200, chunkSize = 1)
        assertTrue(slow > fast)
    }
}

class TtsLocaleResolutionTest {

    @Test
    fun `resolveLocale identifies explicit languages`() {
        val ru = com.aprireader.app.data.tts.TtsController.resolveLocale("ru")
        assertEquals("ru", ru.language)

        val en = com.aprireader.app.data.tts.TtsController.resolveLocale("en")
        assertEquals("en", en.language)

        val de = com.aprireader.app.data.tts.TtsController.resolveLocale("de")
        assertEquals("de", de.language)

        val it = com.aprireader.app.data.tts.TtsController.resolveLocale("it")
        assertEquals("it", it.language)

        val az = com.aprireader.app.data.tts.TtsController.resolveLocale("az")
        assertEquals("az", az.language)
    }

    @Test
    fun `resolveLocale detects Russian from sample Cyrillic text`() {
        val detected = com.aprireader.app.data.tts.TtsController.resolveLocale(null, "Это фрагмент русской книги")
        assertEquals("ru", detected.language)
    }
}

class ScrollAndPagingModeTest {

    @Test
    fun `default reading scroll mode is PAGED_HORIZONTAL for standard book experience`() {
        val settings = com.aprireader.app.data.prefs.ReaderSettings()
        assertEquals(com.aprireader.app.data.prefs.ReadingScrollMode.PAGED_HORIZONTAL, settings.scrollMode)
        assertTrue(settings.horizontalPaging)
    }

    @Test
    fun `reading scroll mode supports all 3 modes seamlessly`() {
        val modes = com.aprireader.app.data.prefs.ReadingScrollMode.entries
        assertEquals(3, modes.size)
        assertTrue(modes.contains(com.aprireader.app.data.prefs.ReadingScrollMode.CONTINUOUS_VERTICAL))
        assertTrue(modes.contains(com.aprireader.app.data.prefs.ReadingScrollMode.PAGED_HORIZONTAL))
        assertTrue(modes.contains(com.aprireader.app.data.prefs.ReadingScrollMode.PAGED_VERTICAL))
    }
}

