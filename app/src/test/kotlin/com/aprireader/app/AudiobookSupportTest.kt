package com.aprireader.app

import com.aprireader.app.data.audio.AudioPlayerState
import com.aprireader.app.domain.Book
import com.aprireader.app.ui.library.SUPPORTED_MIME_TYPES
import com.aprireader.bookformat.BookOpener
import com.aprireader.bookformat.model.BookFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudiobookSupportTest {

    @Test
    fun testAudiobookFormatRecognition() {
        assertEquals(BookFormat.M4B, BookFormat.fromExtension("audiobook.m4b"))
        assertEquals(BookFormat.MP3, BookFormat.fromExtension("chapter1.mp3"))
        assertEquals(BookFormat.M4A, BookFormat.fromExtension("voice.m4a"))
        assertEquals(BookFormat.AAC, BookFormat.fromExtension("track.aac"))
        assertEquals(BookFormat.FLAC, BookFormat.fromExtension("lossless.flac"))
        assertEquals(BookFormat.OGG, BookFormat.fromExtension("audio.ogg"))
        assertEquals(BookFormat.OPUS, BookFormat.fromExtension("voice.opus"))

        assertTrue(BookFormat.M4B.isAudio)
        assertTrue(BookFormat.MP3.isAudio)
        assertTrue(BookFormat.M4A.isAudio)
        assertTrue(BookFormat.AAC.isAudio)
        assertTrue(BookFormat.FLAC.isAudio)
        assertTrue(BookFormat.OGG.isAudio)
        assertTrue(BookFormat.OPUS.isAudio)

        assertFalse(BookFormat.EPUB.isAudio)
        assertFalse(BookFormat.PDF.isAudio)
        assertFalse(BookFormat.FB2.isAudio)
    }

    @Test
    fun testMimeTypesSupportedInFilePicker() {
        val list = SUPPORTED_MIME_TYPES.toList()
        assertTrue(list.contains("audio/*"))
        assertTrue(list.contains("audio/mpeg"))
        assertTrue(list.contains("audio/mp4"))
        assertTrue(list.contains("audio/x-m4b"))
        assertTrue(list.contains("audio/flac"))
        assertTrue(list.contains("audio/ogg"))
    }

    @Test
    fun testBookOpenerDetectsAudioFormats() {
        assertEquals(BookFormat.M4B, BookOpener.detectFormat("test.m4b", null, null))
        assertEquals(BookFormat.MP3, BookOpener.detectFormat("test", "audio/mpeg", null))
        assertEquals(BookFormat.FLAC, BookOpener.detectFormat("test", "audio/flac", null))
        assertEquals(BookFormat.OGG, BookOpener.detectFormat("test", "audio/ogg", null))

        // Magic headers
        val mp3Header = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 0x03, 0x00)
        assertEquals(BookFormat.MP3, BookOpener.detectFormat("unknown", null, mp3Header))

        val flacHeader = byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte())
        assertEquals(BookFormat.FLAC, BookOpener.detectFormat("unknown", null, flacHeader))

        val oggHeader = byteArrayOf('O'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 'S'.code.toByte())
        assertEquals(BookFormat.OGG, BookOpener.detectFormat("unknown", null, oggHeader))
    }

    @Test
    fun testAudioPlayerStateProgress() {
        val stateZero = AudioPlayerState(durationMs = 0L, currentPositionMs = 0L)
        assertEquals(0f, stateZero.progress, 0.001f)

        val stateHalf = AudioPlayerState(durationMs = 100_000L, currentPositionMs = 50_000L)
        assertEquals(0.5f, stateHalf.progress, 0.001f)

        val stateOver = AudioPlayerState(durationMs = 100_000L, currentPositionMs = 150_000L)
        assertEquals(1.0f, stateOver.progress, 0.001f)
    }
}
