package com.aprireader.bookformat

import com.aprireader.bookformat.comic.CbzDocument
import com.aprireader.bookformat.epub.EpubDocument
import com.aprireader.bookformat.fb2.Fb2Document
import com.aprireader.bookformat.model.BookParseException
import com.aprireader.bookformat.text.TxtDocument
import com.aprireader.bookformat.util.ByteArrayRandomAccessSource
import com.aprireader.bookformat.util.MAX_DECOMPRESSED_ENTRY_SIZE
import com.aprireader.bookformat.util.ZipArchive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MaliciousAndCorruptedFileTest {

    @Test
    fun `zip slip path traversal is prevented in zip archive`() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            val entry = ZipEntry("../../etc/passwd")
            zos.putNextEntry(entry)
            zos.write("root:x:0:0:root:/root:/bin/bash".toByteArray())
            zos.closeEntry()

            val normalEntry = ZipEntry("OEBPS/content.opf")
            zos.putNextEntry(normalEntry)
            zos.write("<package></package>".toByteArray())
            zos.closeEntry()
        }

        val source = ByteArrayRandomAccessSource(baos.toByteArray())
        val archive = ZipArchive(source)

        // Path traversal string should not exist in sanitized keys
        assertNull(archive.entry("../../etc/passwd"))
        assertNotNull(archive.entry("etc/passwd"))
        assertNotNull(archive.entry("OEBPS/content.opf"))
    }

    @Test
    fun `zip entry claiming a huge uncompressed size is rejected, not read into memory`() {
        // Файл, который приходит через SAF или системное «Поделиться», может
        // быть собран так, что центральный каталог ZIP заявляет крошечный
        // архив, но одна запись объявляет гигантский исходный размер —
        // классический zip bomb. readAll обязан отказать сразу, а не начать
        // аллоцировать байты под заявленный размер.
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            val entry = ZipEntry("bomb.txt")
            zos.putNextEntry(entry)
            zos.write("small payload".toByteArray())
            zos.closeEntry()
        }
        val bytes = baos.toByteArray()

        // Central directory header signature (little-endian PK\x01\x02):
        // uncompressedSize живёт по смещению +24 от начала этой сигнатуры.
        val centralSig = byteArrayOf(0x50, 0x4B, 0x01, 0x02)
        val sigIndex = bytes.indices.first { i ->
            i + 4 <= bytes.size && (0 until 4).all { bytes[i + it] == centralSig[it] }
        }
        val hugeSize = (MAX_DECOMPRESSED_ENTRY_SIZE * 4).toInt()
        val sizeOffset = sigIndex + 24
        bytes[sizeOffset] = (hugeSize and 0xFF).toByte()
        bytes[sizeOffset + 1] = ((hugeSize shr 8) and 0xFF).toByte()
        bytes[sizeOffset + 2] = ((hugeSize shr 16) and 0xFF).toByte()
        bytes[sizeOffset + 3] = ((hugeSize shr 24) and 0xFF).toByte()

        val archive = ZipArchive(ByteArrayRandomAccessSource(bytes))
        val entry = archive.entry("bomb.txt")
        assertNotNull(entry)
        try {
            archive.readAll(entry!!)
            fail("Ожидалось IOException для записи с заявленным размером больше лимита")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("слишком большая"))
        }
    }

    @Test
    fun `empty and corrupted plain text documents parse gracefully`() {
        val emptyDoc = TxtDocument("", "empty.txt")
        assertEquals("empty", emptyDoc.metadata.title)
        assertTrue(emptyDoc.chapters.isNotEmpty())

        val whitespaceDoc = TxtDocument("   \n\n\r\n   ", "space.txt")
        assertEquals("space", whitespaceDoc.metadata.title)
        val ch = whitespaceDoc.loadChapter(0)
        assertTrue(ch.blocks.isEmpty())
    }

    @Test
    fun `fb2 handles corrupted XML and malformed bodies without crashing`() {
        val corruptXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                <description>
                    <title-info>
                        <book-title>Повреждённая книга</book-title>
                    </title-info>
                </description>
                <body>
                    <section>
                        <title><p>Глава 1</p>
                        <p>Незакрытый тег параграфа
                    </section>
        """.trimIndent()

        val doc = Fb2Document(corruptXml)
        assertEquals("Повреждённая книга", doc.metadata.title)
        assertTrue(doc.chapters.isNotEmpty())
        val chapter = doc.loadChapter(0)
        assertTrue(chapter.blocks.isNotEmpty())
    }

    @Test
    fun `cbz with no images throws BookParseException cleanly`() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            val entry = ZipEntry("readme.txt")
            zos.putNextEntry(entry)
            zos.write("Just text, no images".toByteArray())
            zos.closeEntry()
        }

        val source = ByteArrayRandomAccessSource(baos.toByteArray())
        try {
            CbzDocument(source, "empty_comic.cbz")
            fail("Expected BookParseException for empty CBZ")
        } catch (e: BookParseException) {
            assertTrue(e.message?.contains("нет изображений") == true)
        }
    }

    @Test
    fun `epub with corrupt container throws BookParseException cleanly`() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            val entry = ZipEntry("META-INF/container.xml")
            zos.putNextEntry(entry)
            zos.write("Corrupted unparseable container".toByteArray())
            zos.closeEntry()
        }

        val source = ByteArrayRandomAccessSource(baos.toByteArray())
        try {
            EpubDocument(source)
            fail("Expected BookParseException for corrupt EPUB container")
        } catch (e: BookParseException) {
            assertTrue(e.message?.contains("EPUB") == true)
        }
    }

    @Test
    fun `unicode, spaces, and emoji in filenames are handled safely`() {
        val trickyName = "📚 Приключения 🚀 & Co. (v1.0) [2026] #123.txt"
        val doc = TxtDocument("Привет, мир! Это тестовая книга.", trickyName)
        assertEquals("📚 Приключения 🚀 & Co. (v1.0) [2026] #123", doc.metadata.title)
        val ch = doc.loadChapter(0)
        assertEquals("Привет, мир! Это тестовая книга.", (ch.blocks.first() as com.aprireader.bookformat.model.ContentBlock.Paragraph).text.text)
    }
}
