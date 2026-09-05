package com.aprireader.bookformat

import com.aprireader.bookformat.comic.NaturalOrder
import com.aprireader.bookformat.comic.isImageEntry
import com.aprireader.bookformat.epub.resolve
import com.aprireader.bookformat.fb2.Fb2Document
import com.aprireader.bookformat.model.ContentBlock
import com.aprireader.bookformat.util.detectCharset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class CharsetDetectionTest {

    @Test
    fun `declared windows-1251 wins over utf-8 default`() {
        val xml = """<?xml version="1.0" encoding="windows-1251"?><FictionBook/>"""
        val bytes = xml.toByteArray(Charset.forName("windows-1251"))
        assertEquals(Charset.forName("windows-1251"), detectCharset(bytes))
    }

    @Test
    fun `html meta charset is respected`() {
        val html = """<html><head><meta charset="koi8-r"></head><body>текст</body></html>"""
        val bytes = html.toByteArray(Charsets.ISO_8859_1)
        assertEquals(Charset.forName("koi8-r"), detectCharset(bytes))
    }

    @Test
    fun `bom wins over declaration`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            """<?xml version="1.0" encoding="windows-1251"?>""".toByteArray()
        assertEquals(Charsets.UTF_8, detectCharset(bytes))
    }

    @Test
    fun `cyrillic fb2 in windows-1251 parses correctly`() {
        // Большая часть русскоязычных FB2 в природе именно в этой кодировке;
        // если её игнорировать, книга открывается набором вопросительных знаков.
        val xml = """<?xml version="1.0" encoding="windows-1251"?>
            <FictionBook>
              <description>
                <title-info><book-title>Тёплые буквы</book-title></title-info>
              </description>
              <body>
                <section><p>Ёжик шёл по лесу.</p></section>
              </body>
            </FictionBook>
        """.trimIndent()
        val bytes = xml.toByteArray(Charset.forName("windows-1251"))
        val decoded = String(bytes, detectCharset(bytes))

        val document = Fb2Document(decoded)
        assertEquals("Тёплые буквы", document.metadata.title)
        val paragraph = document.loadChapter(0).blocks
            .filterIsInstance<ContentBlock.Paragraph>()
            .first()
        assertEquals("Ёжик шёл по лесу.", paragraph.text.text)
    }
}

class EpubPathResolutionTest {

    @Test
    fun `relative paths are normalised against the base directory`() {
        assertEquals("OEBPS/images/pic.png", resolve("OEBPS", "images/pic.png"))
        assertEquals("images/pic.png", resolve("OEBPS/text", "../../images/pic.png"))
        assertEquals("OEBPS/pic.png", resolve("OEBPS/text", "../pic.png"))
        assertEquals("pic.png", resolve("", "pic.png"))
        assertEquals("root/pic.png", resolve("OEBPS", "/root/pic.png"))
    }

    @Test
    fun `percent encoded names are decoded to match zip entries`() {
        assertEquals("OEBPS/Глава 1.xhtml", resolve("OEBPS", "%D0%93%D0%BB%D0%B0%D0%B2%D0%B0%201.xhtml"))
        assertEquals("OEBPS/a b.xhtml", resolve("OEBPS", "a%20b.xhtml"))
    }
}

class ComicOrderingTest {

    @Test
    fun `pages sort naturally not lexicographically`() {
        val names = listOf("page10.jpg", "page2.jpg", "page1.jpg", "page20.jpg", "page3.jpg")
        assertEquals(
            listOf("page1.jpg", "page2.jpg", "page3.jpg", "page10.jpg", "page20.jpg"),
            names.sortedWith(NaturalOrder),
        )
    }

    @Test
    fun `leading zeros do not change the order`() {
        val names = listOf("007.png", "10.png", "8.png")
        assertEquals(listOf("007.png", "8.png", "10.png"), names.sortedWith(NaturalOrder))
    }

    @Test
    fun `service files are not treated as pages`() {
        assertTrue(isImageEntry("comic/001.jpg"))
        assertTrue(isImageEntry("002.WEBP"))
        assertFalse(isImageEntry("ComicInfo.xml"))
        assertFalse(isImageEntry("__MACOSX/._001.jpg"))
        assertFalse(isImageEntry("comic/.thumbnail.jpg"))
    }
}
