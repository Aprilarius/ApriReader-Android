package com.aprireader.bookformat

import com.aprireader.bookformat.comic.CbrDocument
import com.aprireader.bookformat.comic.CbzDocument
import com.aprireader.bookformat.epub.EpubDocument
import com.aprireader.bookformat.fb2.Fb2Document
import com.aprireader.bookformat.model.BookDocument
import com.aprireader.bookformat.model.BookFormat
import com.aprireader.bookformat.model.BookMetadata
import com.aprireader.bookformat.model.BookParseException
import com.aprireader.bookformat.model.PagedDocument
import com.aprireader.bookformat.model.TextDocument
import com.aprireader.bookformat.pdf.PdfTextDocument
import com.aprireader.bookformat.text.HtmlDocument
import com.aprireader.bookformat.text.TxtDocument
import com.aprireader.bookformat.util.ByteArrayRandomAccessSource
import com.aprireader.bookformat.util.RandomAccessSource
import com.aprireader.bookformat.util.ZipArchive
import com.aprireader.bookformat.util.detectCharset
import com.aprireader.bookformat.util.readTextDetectingCharset
import java.io.File
import java.io.InputStream

/**
 * Всё, что нужно парсерам для доступа к книге. Реализуется в app-слое поверх SAF:
 * модуль форматов не знает ни про Uri, ни про ContentResolver.
 */
interface DocumentAccess {
    val displayName: String
    val mimeType: String?

    /** Последовательный поток с начала файла. */
    fun openStream(): InputStream

    /** Произвольный доступ. Для локальных SAF-провайдеров дескриптор позиционируем. */
    fun openRandomAccess(): RandomAccessSource

    /**
     * Копия книги в виде файла на диске. Требуется только там, где библиотека
     * физически не умеет иначе (RAR, PDFBox). Может вернуть null.
     */
    fun materializeFile(): File? = null
}

/**
 * Точка входа модуля форматов: определение формата и открытие документа.
 */
object BookOpener {

    /** Определяет формат по расширению, MIME и сигнатуре файла — в таком порядке доверия. */
    fun detectFormat(displayName: String, mimeType: String?, header: ByteArray?): BookFormat? {
        BookFormat.fromExtension(displayName)?.let { return it }

        mimeType?.lowercase()?.let { mime ->
            BookFormat.entries.firstOrNull { it.mime == mime }?.let { return it }
            when {
                "epub" in mime -> return BookFormat.EPUB
                "pdf" in mime -> return BookFormat.PDF
                "fictionbook" in mime -> return BookFormat.FB2
                "m4b" in mime -> return BookFormat.M4B
                "audio/mpeg" in mime || "mp3" in mime -> return BookFormat.MP3
                "audio/mp4" in mime || "m4a" in mime -> return BookFormat.M4A
                "flac" in mime -> return BookFormat.FLAC
                "ogg" in mime -> return BookFormat.OGG
                "opus" in mime -> return BookFormat.OPUS
                mime.startsWith("text/htm") || "xhtml" in mime -> return BookFormat.HTML
                mime.startsWith("text/") -> return BookFormat.TXT
                mime.startsWith("audio/") -> return BookFormat.MP3
            }
        }

        val head = header ?: return null
        return when {
            head.startsWith("%PDF") -> BookFormat.PDF
            head.startsWith("Rar!") -> BookFormat.CBR
            head.startsWith("ID3") -> BookFormat.MP3
            head.startsWith("fLaC") -> BookFormat.FLAC
            head.startsWith("OggS") -> BookFormat.OGG
            head.size > 8 && head[4] == 'f'.code.toByte() && head[5] == 't'.code.toByte() && head[6] == 'y'.code.toByte() && head[7] == 'p'.code.toByte() -> BookFormat.M4B
            head.size > 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() -> BookFormat.EPUB
            String(head, 0, minOf(head.size, 512), detectCharset(head)).contains("FictionBook", true) -> BookFormat.FB2
            String(head, 0, minOf(head.size, 512), detectCharset(head)).contains("<html", true) -> BookFormat.HTML
            else -> null
        }
    }

    /** Открывает документ и отдаёт его в том виде, который соответствует формату. */
    fun open(access: DocumentAccess, format: BookFormat): BookDocument = when (format) {
        BookFormat.EPUB -> openZipBased(access)
        BookFormat.FB2 -> Fb2Document(access.openStream().readTextDetectingCharset())
        BookFormat.FB2_ZIP -> Fb2Document(readFb2FromZip(access.openRandomAccess()), BookFormat.FB2_ZIP)
        BookFormat.TXT -> TxtDocument(access.openStream().readTextDetectingCharset(), access.displayName)
        BookFormat.HTML -> HtmlDocument(access.openStream().readTextDetectingCharset(), access.displayName)
        BookFormat.CBZ -> CbzDocument(access.openRandomAccess(), access.displayName)
        BookFormat.CBR -> CbrDocument(
            access.materializeFile() ?: throw BookParseException("CBR: требуется локальная копия файла"),
            access.displayName,
        )
        BookFormat.PDF -> PdfTextDocument(
            access.materializeFile() ?: throw BookParseException("PDF: требуется локальная копия файла"),
        )
        BookFormat.M4B, BookFormat.MP3, BookFormat.M4A, BookFormat.AAC,
        BookFormat.FLAC, BookFormat.OGG, BookFormat.OPUS -> {
            com.aprireader.bookformat.model.SimpleAudioDocument(
                format = format,
                metadata = BookMetadata(title = access.displayName.substringBeforeLast('.')),
            )
        }
    }

    /** Открывает документ, определив формат самостоятельно. */
    fun open(access: DocumentAccess): BookDocument {
        val header = runCatching { access.openStream().use { stream -> readHeader(stream) } }.getOrNull()
        val format = detectFormat(access.displayName, access.mimeType, header)
            ?: throw BookParseException("Формат файла не распознан: ${access.displayName}")
        return open(access, format)
    }

    /**
     * Быстрое чтение только метаданных при импорте — без разбора содержимого.
     * Документ закрывается сразу, наружу уходят лишь метаданные и обложка.
     */
    fun readMetadata(access: DocumentAccess, format: BookFormat): BookMetadata =
        open(access, format).use { document ->
            val chapterCount = when (document) {
                is TextDocument -> document.chapters.size
                is PagedDocument -> document.pageCount
                is com.aprireader.bookformat.model.AudioDocument -> 1
            }
            document.metadata.also { require(chapterCount >= 0) }
        }

    /**
     * ZIP-контейнер может оказаться EPUB, CBZ или запакованным FB2 — расширение
     * иногда врёт, поэтому решение принимается по содержимому архива.
     */
    private fun openZipBased(access: DocumentAccess): BookDocument {
        val source = access.openRandomAccess()
        val zip = ZipArchive(source)
        val names = zip.names
        return when {
            names.any { it.equals("META-INF/container.xml", true) } || names.any { it.endsWith(".opf", true) } -> {
                zip.close()
                EpubDocument(access.openRandomAccess())
            }
            names.any { it.endsWith(".fb2", true) } -> {
                val entry = zip.entries.first { it.name.endsWith(".fb2", true) }
                val bytes = zip.readAll(entry)
                zip.close()
                Fb2Document(String(bytes, detectCharset(bytes)), BookFormat.FB2_ZIP)
            }
            else -> {
                zip.close()
                CbzDocument(access.openRandomAccess(), access.displayName)
            }
        }
    }

    private fun readFb2FromZip(source: RandomAccessSource): String = ZipArchive(source).use { zip ->
        val entry = zip.entries.firstOrNull { it.name.endsWith(".fb2", true) }
            ?: zip.entries.firstOrNull()
            ?: throw BookParseException("fb2.zip: архив пуст")
        val bytes = zip.readAll(entry)
        String(bytes, detectCharset(bytes))
    }
}

internal fun ByteArray.startsWith(prefix: String): Boolean {
    if (size < prefix.length) return false
    for (i in prefix.indices) if (this[i] != prefix[i].code.toByte()) return false
    return true
}

private fun readHeader(stream: InputStream, length: Int = 1024): ByteArray {
    val buffer = ByteArray(length)
    var read = 0
    while (read < length) {
        val n = stream.read(buffer, read, length - read)
        if (n <= 0) break
        read += n
    }
    return if (read == length) buffer else buffer.copyOf(read)
}

/** Оборачивает готовые байты — используется для вложенных архивов и тестов. */
fun bytesAsRandomAccess(bytes: ByteArray): RandomAccessSource = ByteArrayRandomAccessSource(bytes)
