package com.aprireader.bookformat

import com.aprireader.bookformat.epub.EpubDocument
import com.aprireader.bookformat.fb2.Fb2Document
import com.aprireader.bookformat.model.BookFormat
import com.aprireader.bookformat.model.ContentBlock
import com.aprireader.bookformat.model.SpanKind
import com.aprireader.bookformat.text.HtmlDocument
import com.aprireader.bookformat.text.TxtDocument
import com.aprireader.bookformat.util.ByteArrayRandomAccessSource
import com.aprireader.bookformat.util.HtmlBlockParser
import com.aprireader.bookformat.util.ZipArchive
import com.aprireader.bookformat.util.decodeEntities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipArchiveTest {

    @Test
    fun `reads stored and deflated entries`() {
        val zip = buildZip(
            "a.txt" to "hello".toByteArray(),
            "dir/b.txt" to "x".repeat(5000).toByteArray(),
        )
        ZipArchive(ByteArrayRandomAccessSource(zip)).use { archive ->
            assertEquals(setOf("a.txt", "dir/b.txt"), archive.names)
            assertEquals("hello", String(archive.readAll("a.txt")!!))
            assertEquals(5000, archive.readAll("dir/b.txt")!!.size)
        }
    }
}

class HtmlParsingTest {

    @Test
    fun `extracts headings paragraphs and inline styles`() {
        val blocks = HtmlBlockParser.parse(
            """
            <html><body>
              <h1 id="c1">Заголовок</h1>
              <p>Обычный <b>жирный</b> и <i>курсив</i>.</p>
              <blockquote><p>Цитата</p></blockquote>
              <hr/>
              <img src="images/pic.png" alt="Рисунок"/>
            </body></html>
            """.trimIndent()
        )

        val heading = blocks.filterIsInstance<ContentBlock.Heading>().single()
        assertEquals("Заголовок", heading.text.text)
        assertEquals(1, heading.level)
        assertEquals("c1", heading.anchor)

        val paragraph = blocks.filterIsInstance<ContentBlock.Paragraph>().first()
        assertEquals("Обычный жирный и курсив.", paragraph.text.text)
        assertEquals(
            "жирный",
            paragraph.text.spans.first { it.kind == SpanKind.BOLD }
                .let { paragraph.text.text.substring(it.start, it.end) },
        )
        assertEquals(
            "курсив",
            paragraph.text.spans.first { it.kind == SpanKind.ITALIC }
                .let { paragraph.text.text.substring(it.start, it.end) },
        )

        assertTrue(blocks.any { it is ContentBlock.Separator })
        val image = blocks.filterIsInstance<ContentBlock.Image>().single()
        assertEquals("images/pic.png", image.href)
        assertEquals("Рисунок", image.caption)
    }

    @Test
    fun `skips scripts styles and comments`() {
        val blocks = HtmlBlockParser.parse(
            "<body><style>p{color:red}</style><!-- a > b --><script>var x = 1 < 2;</script><p>Текст</p></body>"
        )
        assertEquals(listOf("Текст"), blocks.filterIsInstance<ContentBlock.Paragraph>().map { it.text.text })
    }

    @Test
    fun `decodes entities`() {
        assertEquals("«А» — B&C…", decodeEntities("&laquo;А&raquo; &mdash; B&amp;C&hellip;"))
        assertEquals("A", decodeEntities("&#65;"))
        assertEquals("A", decodeEntities("&#x41;"))
    }
}

class EpubDocumentTest {

    @Test
    fun `parses metadata spine and toc`() {
        val document = EpubDocument(ByteArrayRandomAccessSource(sampleEpub()))
        assertEquals("Тестовая книга", document.metadata.title)
        assertEquals(listOf("Иван Автор"), document.metadata.authors)
        assertEquals("ru", document.metadata.language)
        assertNotNull(document.metadata.cover)

        assertEquals(2, document.chapters.size)
        assertEquals("Первая глава", document.chapters[0].title)
        assertEquals("Вторая глава", document.chapters[1].title)

        val chapter = document.loadChapter(0)
        assertTrue(chapter.blocks.any { it is ContentBlock.Paragraph && "Начало" in it.text.text })
        document.close()
    }

    @Test
    fun `resolves relative resource paths`() {
        val document = EpubDocument(ByteArrayRandomAccessSource(sampleEpub()))
        val image = document.loadChapter(1).blocks.filterIsInstance<ContentBlock.Image>().single()
        assertEquals("OEBPS/images/pic.png", image.href)
        assertNotNull(document.loadResource(image.href))
        document.close()
    }
}

class Fb2DocumentTest {

    private val fb2 = """
        <?xml version="1.0" encoding="UTF-8"?>
        <FictionBook>
          <description>
            <title-info>
              <author><first-name>Пётр</first-name><last-name>Писатель</last-name></author>
              <book-title>Книга FB2</book-title>
              <annotation><p>Аннотация книги.</p></annotation>
              <lang>ru</lang>
              <sequence name="Серия" number="2"/>
            </title-info>
            <publish-info><publisher>Издательство</publisher><year>2021</year></publish-info>
          </description>
          <body>
            <section>
              <title><p>Глава один</p></title>
              <p>Текст с <emphasis>курсивом</emphasis>.</p>
              <empty-line/>
              <cite><p>Цитата тут</p></cite>
            </section>
            <section>
              <title><p>Глава два</p></title>
              <p>Второй текст.</p>
            </section>
          </body>
        </FictionBook>
    """.trimIndent()

    @Test
    fun `parses description`() {
        val document = Fb2Document(fb2)
        assertEquals("Книга FB2", document.metadata.title)
        assertEquals(listOf("Пётр Писатель"), document.metadata.authors)
        assertEquals("Аннотация книги.", document.metadata.description)
        assertEquals("Серия", document.metadata.series)
        assertEquals(2, document.metadata.seriesIndex)
        assertEquals(2021, document.metadata.year)
    }

    @Test
    fun `parses fb2 with html entities and custom namespaces`() {
        val fb2Entities = """
            <?xml version="1.0" encoding="windows-1251"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" xmlns:l="http://www.w3.org/1999/xlink">
              <description>
                <title-info>
                  <author><first-name>Лев</first-name><last-name>Толстой</last-name></author>
                  <book-title>Война&nbsp;и&nbsp;мир</book-title>
                  <annotation><p>&laquo;Аннотация&raquo; &mdash; шедевр&hellip;</p></annotation>
                  <lang>ru</lang>
                  <coverpage><image l:href="#cover.jpg"/></coverpage>
                </title-info>
              </description>
              <binary id="cover.jpg" content-type="image/jpeg">aGVsbG8=</binary>
              <body>
                <section><p>Текст</p></section>
              </body>
            </FictionBook>
        """.trimIndent()
        val document = Fb2Document(fb2Entities)
        assertEquals("Война и мир", document.metadata.title)
        assertEquals(listOf("Лев Толстой"), document.metadata.authors)
        assertNotNull(document.metadata.cover)
    }

    @Test
    fun `splits sections into chapters`() {
        val document = Fb2Document(fb2)
        assertEquals(listOf("Глава один", "Глава два"), document.chapters.map { it.title })

        val first = document.loadChapter(0)
        val paragraph = first.blocks.filterIsInstance<ContentBlock.Paragraph>()
            .first { "курсив" in it.text.text }
        assertEquals("Текст с курсивом.", paragraph.text.text)
        assertTrue(paragraph.text.spans.any { it.kind == SpanKind.ITALIC })
        assertTrue(first.blocks.any { it is ContentBlock.Separator })
        assertTrue(first.blocks.any { it is ContentBlock.Heading && it.text.text == "Глава один" })
    }
}

class PlainTextTest {

    @Test
    fun `detects headings and paragraphs`() {
        val text = """
            Глава 1

            Первый абзац строка один
            продолжение абзаца.

            Второй абзац.

            Глава 2

            Третий абзац.
        """.trimIndent()

        val document = TxtDocument(text, "moya_kniga.txt")
        assertEquals("moya kniga", document.metadata.title)
        assertEquals(2, document.chapters.size)
        assertEquals("Глава 1", document.chapters[0].title)

        val blocks = document.loadChapter(0).blocks
        assertEquals(
            listOf("Первый абзац строка один продолжение абзаца.", "Второй абзац."),
            blocks.filterIsInstance<ContentBlock.Paragraph>().map { it.text.text },
        )
    }

    @Test
    fun `html document splits by top level headings`() {
        val document = HtmlDocument(
            "<html><head><title>Док</title></head><body><h2>Раз</h2><p>А</p><h2>Два</h2><p>Б</p></body></html>",
            "file.html",
        )
        assertEquals("Док", document.metadata.title)
        assertEquals(listOf("Раз", "Два"), document.chapters.map { it.title })
    }
}

class FormatDetectionTest {

    @Test
    fun `detects by extension mime and signature`() {
        assertEquals(BookFormat.EPUB, BookOpener.detectFormat("book.epub", null, null))
        assertEquals(BookFormat.FB2_ZIP, BookOpener.detectFormat("book.fb2.zip", null, null))
        assertEquals(BookFormat.CBZ, BookOpener.detectFormat("comic.cbz", null, null))
        assertEquals(BookFormat.PDF, BookOpener.detectFormat("noext", "application/pdf", null))
        assertEquals(BookFormat.PDF, BookOpener.detectFormat("noext", null, "%PDF-1.7".toByteArray()))
        assertEquals(BookFormat.CBR, BookOpener.detectFormat("noext", null, "Rar! ".toByteArray()))
    }
}

// --- вспомогательные построители ---

internal fun buildZip(vararg entries: Pair<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        for ((name, bytes) in entries) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

private fun sampleEpub(): ByteArray = buildZip(
    "mimetype" to "application/epub+zip".toByteArray(),
    "META-INF/container.xml" to """
        <?xml version="1.0"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
        </container>
    """.trimIndent().toByteArray(),
    "OEBPS/content.opf" to """
        <?xml version="1.0"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:title>Тестовая книга</dc:title>
            <dc:creator>Иван Автор</dc:creator>
            <dc:language>ru</dc:language>
            <dc:date>2019-05-01</dc:date>
            <meta name="cover" content="cover-img"/>
          </metadata>
          <manifest>
            <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
            <item id="c1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
            <item id="c2" href="ch2.xhtml" media-type="application/xhtml+xml"/>
            <item id="cover-img" href="images/cover.png" media-type="image/png"/>
            <item id="pic" href="images/pic.png" media-type="image/png"/>
          </manifest>
          <spine toc="ncx">
            <itemref idref="c1"/>
            <itemref idref="c2"/>
          </spine>
        </package>
    """.trimIndent().toByteArray(),
    "OEBPS/toc.ncx" to """
        <?xml version="1.0"?>
        <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
          <navMap>
            <navPoint id="n1" playOrder="1">
              <navLabel><text>Первая глава</text></navLabel>
              <content src="ch1.xhtml"/>
            </navPoint>
            <navPoint id="n2" playOrder="2">
              <navLabel><text>Вторая глава</text></navLabel>
              <content src="ch2.xhtml"/>
            </navPoint>
          </navMap>
        </ncx>
    """.trimIndent().toByteArray(),
    "OEBPS/ch1.xhtml" to """
        <?xml version="1.0"?>
        <html xmlns="http://www.w3.org/1999/xhtml"><body>
          <h1>Первая глава</h1>
          <p>Начало книги.</p>
        </body></html>
    """.trimIndent().toByteArray(),
    "OEBPS/ch2.xhtml" to """
        <?xml version="1.0"?>
        <html xmlns="http://www.w3.org/1999/xhtml"><body>
          <h1>Вторая глава</h1>
          <p>Продолжение.</p>
          <img src="images/pic.png"/>
        </body></html>
    """.trimIndent().toByteArray(),
    "OEBPS/images/cover.png" to ByteArray(64) { it.toByte() },
    "OEBPS/images/pic.png" to ByteArray(32) { it.toByte() },
)
