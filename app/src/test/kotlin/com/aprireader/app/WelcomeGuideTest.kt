package com.aprireader.app

import com.aprireader.bookformat.epub.EpubDocument
import com.aprireader.bookformat.util.FileRandomAccessSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WelcomeGuideTest {

    @Test
    fun testWelcomeGuideEpubParsesCleanly() {
        val assetFile = File("src/main/assets/welcome_guide.epub")
        assertTrue("Файл welcome_guide.epub должен существовать в assets", assetFile.exists())
        assertTrue("Размер файла должен быть больше 10 КБ", assetFile.length() > 10 * 1024)

        val doc = EpubDocument(FileRandomAccessSource(assetFile))
        try {
            assertEquals("Добро пожаловать в ApriReader", doc.metadata.title)
            assertTrue("Авторы должны содержать ApriReader", doc.metadata.authors.any { it.contains("ApriReader") })
            assertNotNull("Обложка должна присутствовать", doc.metadata.cover)
            assertTrue("Размер байтов обложки должен быть больше 1000", doc.metadata.cover!!.size > 1000)

            assertEquals("Должно быть 5 элементов spine", 5, doc.chapters.size)

            val ch1 = doc.loadChapter(1)
            assertTrue(ch1.blocks.isNotEmpty())

            val ch2 = doc.loadChapter(2)
            assertTrue(ch2.blocks.isNotEmpty())

            val ch3 = doc.loadChapter(3)
            assertTrue(ch3.blocks.isNotEmpty())

            val ch4 = doc.loadChapter(4)
            assertTrue(ch4.blocks.isNotEmpty())
        } finally {
            doc.close()
        }
    }
}
