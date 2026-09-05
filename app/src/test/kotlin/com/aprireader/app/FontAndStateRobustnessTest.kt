package com.aprireader.app

import com.aprireader.app.data.prefs.FocusSettings
import com.aprireader.app.data.prefs.ReadingFont
import com.aprireader.app.data.prefs.TypographySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FontAndStateRobustnessTest {

    @Test
    fun `font magic validation accepts valid ttf and otf binaries`() {
        // TrueType magic: 0x00010000
        val ttfHeader = byteArrayOf(0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0A.toByte(), 0x00.toByte(), 0x80.toByte())
        val ttfFile = File.createTempFile("test_ttf", ".ttf").apply {
            writeBytes(ttfHeader + ByteArray(200))
            deleteOnExit()
        }

        // OpenType magic: OTTO (0x4F54544F)
        val otfHeader = byteArrayOf(0x4F.toByte(), 0x54.toByte(), 0x54.toByte(), 0x4F.toByte(), 0x00.toByte(), 0x08.toByte(), 0x00.toByte(), 0x80.toByte())
        val otfFile = File.createTempFile("test_otf", ".otf").apply {
            writeBytes(otfHeader + ByteArray(200))
            deleteOnExit()
        }

        // Corrupt executable or random file
        val exeHeader = byteArrayOf(0x4D.toByte(), 0x5A.toByte(), 0x90.toByte(), 0x00.toByte()) // MZ
        val exeFile = File.createTempFile("test_exe", ".exe").apply {
            writeBytes(exeHeader + ByteArray(200))
            deleteOnExit()
        }

        fun checkMagic(file: File): Boolean = runCatching {
            val header = ByteArray(4)
            file.inputStream().use { it.read(header) }
            val magic = (header[0].toInt() and 0xFF shl 24) or
                (header[1].toInt() and 0xFF shl 16) or
                (header[2].toInt() and 0xFF shl 8) or
                (header[3].toInt() and 0xFF)
            magic == 0x00010000 || magic == 0x4F54544F || magic == 0x74746366 || magic == 0x774F4646 || magic == 0x774F4632 || magic == 0x74727565
        }.getOrDefault(false)

        assertTrue(checkMagic(ttfFile))
        assertTrue(checkMagic(otfFile))
        assertTrue(!checkMagic(exeFile))
    }

    @Test
    fun `typography and focus settings immutability and defaults`() {
        val defaultTypography = TypographySettings()
        assertEquals(ReadingFont.LITERATA, defaultTypography.font)
        assertEquals(19f, defaultTypography.fontSizeSp, 0.01f)
        assertEquals(1.55f, defaultTypography.lineHeight, 0.01f)

        val modified = defaultTypography.copy(
            font = ReadingFont.INTER,
            fontSizeSp = 22f,
            hyphenation = false,
        )
        assertNotEquals(defaultTypography.font, modified.font)
        assertEquals(22f, modified.fontSizeSp, 0.01f)
        assertEquals(false, modified.hyphenation)

        val defaultFocus = FocusSettings()
        assertEquals(300, defaultFocus.rsvpWpm)
        assertEquals(1, defaultFocus.rsvpChunkSize)
        assertEquals(false, defaultFocus.bionicEnabled)

        val modifiedFocus = defaultFocus.copy(bionicEnabled = true, rsvpWpm = 450)
        assertTrue(modifiedFocus.bionicEnabled)
        assertEquals(450, modifiedFocus.rsvpWpm)
    }
}
