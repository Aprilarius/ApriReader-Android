package com.aprireader.bookformat.pdf

import com.aprireader.bookformat.model.BookFormat
import com.aprireader.bookformat.model.BookMetadata
import com.aprireader.bookformat.model.BookParseException
import com.aprireader.bookformat.model.Chapter
import com.aprireader.bookformat.model.ChapterRef
import com.aprireader.bookformat.model.ContentBlock
import com.aprireader.bookformat.model.ParagraphKind
import com.aprireader.bookformat.model.RichText
import com.aprireader.bookformat.model.TextDocument
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.InputStream

/**
 * Текстовый слой PDF.
 *
 * Основной режим чтения PDF — постраничный рендер (он живёт в app-слое поверх
 * системного PdfRenderer). Этот класс нужен там, где требуется именно поток слов:
 * RSVP, бионический шрифт, поиск по тексту, TTS. Разбор ленивый — текст
 * извлекается группами страниц по мере чтения.
 */
class PdfTextDocument(file: File) : TextDocument {

    private val document: PDDocument = runCatching { PDDocument.load(file) }
        .getOrElse { throw BookParseException("PDF: не удалось открыть документ", it) }

    override val format = BookFormat.PDF

    override val metadata: BookMetadata = run {
        val info = document.documentInformation
        BookMetadata(
            title = info?.title?.takeIf { it.isNotBlank() }
                ?: file.name.substringBeforeLast('.').replace('_', ' '),
            authors = info?.author?.split(",", ";")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty(),
            description = info?.subject?.takeIf { it.isNotBlank() },
            publisher = info?.producer?.takeIf { it.isNotBlank() },
            year = info?.creationDate?.get(java.util.Calendar.YEAR),
        )
    }

    val pageCount: Int = document.numberOfPages

    override val chapters: List<ChapterRef> = buildList {
        var page = 1
        var index = 0
        while (page <= pageCount) {
            val last = minOf(page + PAGES_PER_CHUNK - 1, pageCount)
            add(
                ChapterRef(
                    index = index,
                    id = "p$page-$last",
                    title = if (pageCount <= PAGES_PER_CHUNK) null else "Страницы $page–$last",
                    approxChars = (last - page + 1) * APPROX_CHARS_PER_PAGE,
                )
            )
            page = last + 1
            index++
        }
    }.ifEmpty { listOf(ChapterRef(0, "p0", null)) }

    /** Текст, извлечённый из PDF, не имеет структуры — он разбивается на абзацы эвристически. */
    override fun loadChapter(index: Int): Chapter {
        val ref = chapters[index]
        val first = index * PAGES_PER_CHUNK + 1
        val last = minOf(first + PAGES_PER_CHUNK - 1, pageCount)
        if (first > pageCount) return Chapter(ref, emptyList())

        val stripper = PDFTextStripper().apply {
            startPage = first
            endPage = last
            paragraphStart = PARAGRAPH_MARKER
            sortByPosition = true
        }
        val raw = runCatching { stripper.getText(document) }.getOrElse { "" }
        return Chapter(ref, toBlocks(raw))
    }

    override fun close() = document.close()

    private companion object {
        const val PAGES_PER_CHUNK = 12
        const val APPROX_CHARS_PER_PAGE = 1800
        const val PARAGRAPH_MARKER = ""

        fun toBlocks(raw: String): List<ContentBlock> {
            if (raw.isBlank()) return emptyList()
            return raw.split(PARAGRAPH_MARKER)
                .flatMap { it.split("\n\n") }
                .map { paragraph ->
                    paragraph.lines().joinToString(" ") { line ->
                        // Перенос по слогам в конце строки — типичный артефакт PDF.
                        line.trim()
                    }.replace(Regex("(\\p{L})-\\s+(\\p{Ll})"), "$1$2")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                }
                .filter { it.isNotBlank() }
                .map { ContentBlock.Paragraph(RichText(it), ParagraphKind.BODY) }
        }
    }
}

/**
 * Быстрое потоковое извлечение метаданных из заголовков/хвоста PDF и XMP пакетов без тяжелого рендера.
 */
object PdfMetadataExtractor {

    fun extract(stream: InputStream, fallbackTitle: String): BookMetadata {
        return runCatching {
            val bytes = stream.use { it.readBytes() }
            if (bytes.size < 32) return BookMetadata(fallbackTitle)

            // 1. Поиск XMP метаданных
            val xmp = extractXmp(bytes)
            if (xmp != null && (xmp.title != fallbackTitle && xmp.title.isNotBlank() || xmp.authors.isNotEmpty())) {
                return xmp.copy(title = xmp.title.ifBlank { fallbackTitle })
            }

            // 2. Поиск классического /Info словаря PDF
            extractInfoDict(bytes, fallbackTitle)
        }.getOrDefault(BookMetadata(fallbackTitle))
    }

    private fun extractXmp(bytes: ByteArray): BookMetadata? {
        val startMarker = "<x:xmpmeta".toByteArray()
        val endMarker = "</x:xmpmeta>".toByteArray()
        val start = indexOf(bytes, startMarker)
        if (start < 0) return null
        val end = indexOf(bytes, endMarker, start)
        if (end < 0) return null

        val xmpText = String(bytes, start, end + endMarker.size - start, Charsets.UTF_8)

        fun tag(name: String): String? =
            Regex("""<dc:$name\b[^>]*>([\s\S]*?)</dc:$name>""", RegexOption.IGNORE_CASE)
                .find(xmpText)?.groupValues?.get(1)?.let { stripXml(it) }?.takeIf { it.isNotBlank() }

        val title = tag("title")
        val authors = ArrayList<String>()
        val creatorBlock = Regex("""<dc:creator\b[^>]*>([\s\S]*?)</dc:creator>""", RegexOption.IGNORE_CASE).find(xmpText)?.groupValues?.get(1)
        if (creatorBlock != null) {
            val items = Regex("""<rdf:li\b[^>]*>([\s\S]*?)</rdf:li>""", RegexOption.IGNORE_CASE).findAll(creatorBlock)
            for (item in items) {
                val a = stripXml(item.groupValues[1]).trim()
                if (a.isNotBlank()) authors += a
            }
        }
        val description = tag("description")
        val date = tag("date")
        val year = date?.let { Regex("""\d{4}""").find(it)?.value?.toIntOrNull() }

        if (title == null && authors.isEmpty()) return null
        return BookMetadata(
            title = title ?: "",
            authors = authors,
            description = description,
            year = year,
        )
    }

    private fun extractInfoDict(bytes: ByteArray, fallbackTitle: String): BookMetadata {
        val scanStart = maxOf(0, bytes.size - 64 * 1024)
        val tail = String(bytes, scanStart, bytes.size - scanStart, Charsets.ISO_8859_1)

        fun pdfString(key: String): String? {
            val match = Regex("""/$key\s*\(([^)]+)\)""").find(tail) ?: Regex("""/$key\s*<([0-9A-Fa-f]+)>""").find(tail)
            if (match != null) {
                val raw = match.groupValues[1]
                return decodePdfString(raw)
            }
            return null
        }

        val title = pdfString("Title")?.takeIf { it.isNotBlank() } ?: fallbackTitle
        val author = pdfString("Author")?.takeIf { it.isNotBlank() }
        val subject = pdfString("Subject")?.takeIf { it.isNotBlank() }
        val date = pdfString("CreationDate")
        val year = date?.let { Regex("""\d{4}""").find(it)?.value?.toIntOrNull() }

        return BookMetadata(
            title = title,
            authors = author?.split(",", ";")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty(),
            description = subject,
            year = year,
        )
    }

    private fun decodePdfString(raw: String): String {
        if (raw.startsWith("\u00fe\u00ff") || raw.startsWith("\\376\\377")) {
            val clean = raw.removePrefix("\u00fe\u00ff").removePrefix("\\376\\377")
            return clean
        }
        return raw.replace("\\(", "(").replace("\\)", ")").replace("\\\\", "\\").trim()
    }

    private fun stripXml(raw: String): String =
        raw.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()

    private fun indexOf(source: ByteArray, target: ByteArray, fromIndex: Int = 0): Int {
        if (target.isEmpty()) return 0
        val max = source.size - target.size
        for (i in fromIndex..max) {
            var found = true
            for (j in target.indices) {
                if (source[i + j] != target[j]) {
                    found = false
                    break
                }
            }
            if (found) return i
        }
        return -1
    }
}
