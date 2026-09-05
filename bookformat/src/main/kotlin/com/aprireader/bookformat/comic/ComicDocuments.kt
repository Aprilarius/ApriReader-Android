package com.aprireader.bookformat.comic

import com.aprireader.bookformat.model.BookFormat
import com.aprireader.bookformat.model.BookMetadata
import com.aprireader.bookformat.model.BookParseException
import com.aprireader.bookformat.model.PagedDocument
import com.aprireader.bookformat.util.RandomAccessSource
import com.aprireader.bookformat.util.XmlDom
import com.aprireader.bookformat.util.ZipArchive
import com.aprireader.bookformat.util.childElements
import com.aprireader.bookformat.util.localNameOrTag
import com.aprireader.bookformat.util.readBytesUpTo
import com.aprireader.bookformat.util.text
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif")

internal fun isImageEntry(name: String): Boolean {
    val clean = name.substringAfterLast('/')
    if (clean.startsWith(".") || clean.startsWith("__MACOSX")) return false
    return clean.substringAfterLast('.', "").lowercase() in imageExtensions
}

/**
 * «Естественная» сортировка: page2.jpg должна идти перед page10.jpg.
 * Лексикографический порядок ломает почти каждый комикс.
 */
internal object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var ni = i
                var nj = j
                while (ni < a.length && a[ni].isDigit()) ni++
                while (nj < b.length && b[nj].isDigit()) nj++
                val na = a.substring(i, ni).trimStart('0').ifEmpty { "0" }
                val nb = b.substring(j, nj).trimStart('0').ifEmpty { "0" }
                if (na.length != nb.length) return na.length - nb.length
                val cmp = na.compareTo(nb)
                if (cmp != 0) return cmp
                i = ni
                j = nj
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}

/** Комикс в ZIP-контейнере (CBZ). */
class CbzDocument(source: RandomAccessSource, fileName: String) : PagedDocument {

    private val zip = ZipArchive(source)
    private val pages: List<ZipArchive.Entry> = zip.entries
        .filter { isImageEntry(it.name) }
        .sortedWith(compareBy(NaturalOrder) { it.name })

    override val format = BookFormat.CBZ
    override val pageCount: Int = pages.size
    override val metadata: BookMetadata = run {
        val comicInfoBytes = zip.readAll("ComicInfo.xml")
            ?: zip.findIgnoreCase("ComicInfo.xml")?.let { zip.readAll(it) }
            ?: zip.names.firstOrNull { it.endsWith("ComicInfo.xml", true) }?.let { zip.readAll(it) }
        readComicInfo(comicInfoBytes, fileName).copy(cover = pages.firstOrNull()?.let { zip.readAll(it) })
    }

    init {
        if (pages.isEmpty()) throw BookParseException("CBZ: в архиве нет изображений")
    }

    override fun openPage(index: Int): InputStream? = pages.getOrNull(index)?.let { zip.open(it) }

    override fun close() = zip.close()
}

/**
 * Комикс в RAR-контейнере (CBR). Требует файла на диске, поэтому app-слой
 * материализует книгу в кэш. junrar не поддерживает RAR5 — такие архивы
 * распознаются и отдают понятную ошибку вместо падения.
 */
class CbrDocument(file: File, fileName: String) : PagedDocument {

    private val archive: Archive = runCatching { Archive(file) }.getOrElse {
        throw BookParseException("CBR: не удалось открыть архив (возможно, формат RAR5, который не поддерживается)", it)
    }

    private val pages: List<FileHeader> = archive.fileHeaders
        .filter { !it.isDirectory && isImageEntry(it.headerName()) }
        .sortedWith(compareBy(NaturalOrder) { it.headerName() })

    override val format = BookFormat.CBR
    override val pageCount: Int = pages.size
    override val metadata: BookMetadata

    init {
        if (pages.isEmpty()) throw BookParseException("CBR: в архиве нет изображений")
        val info = archive.fileHeaders.firstOrNull { it.headerName().endsWith("ComicInfo.xml", true) }
            ?.let { header -> runCatching { archive.getInputStream(header).use { it.readBytesUpTo() } }.getOrNull() }
        val cover = runCatching { archive.getInputStream(pages.first()).use { it.readBytesUpTo() } }.getOrNull()
        metadata = readComicInfo(info, fileName).copy(cover = cover)
    }

    override fun openPage(index: Int): InputStream? {
        val header = pages.getOrNull(index) ?: return null
        // junrar не даёт произвольного доступа к потоку, поэтому страница читается целиком
        // (readBytesUpTo — не только компактность, но и защита от RAR-бомбы: без
        // потолка размера страница-бомба уронила бы процесс по OutOfMemoryError).
        val bytes = runCatching { archive.getInputStream(header).use { it.readBytesUpTo() } }.getOrNull() ?: return null
        return ByteArrayInputStream(bytes)
    }

    override fun close() = archive.close()
}

private fun FileHeader.headerName(): String = fileName.replace('\\', '/')

/** ComicInfo.xml — де-факто стандарт метаданных комиксов. */
private fun readComicInfo(bytes: ByteArray?, fileName: String): BookMetadata {
    val fallbackTitle = fileName.substringBeforeLast('.').replace('_', ' ').trim()
        .ifBlank { "Без названия" }
    val root = bytes?.let { XmlDom.parse(it) } ?: return BookMetadata(title = fallbackTitle)

    fun value(name: String): String? = root.childElements()
        .firstOrNull { it.localNameOrTag().equals(name, true) }?.text()?.takeIf { it.isNotBlank() }

    val series = value("Series")
    val number = value("Number")
    val title = value("Title")
        ?: listOfNotNull(series, number?.let { "#$it" }).joinToString(" ").ifBlank { null }
        ?: fallbackTitle

    return BookMetadata(
        title = title,
        authors = listOfNotNull(value("Writer"), value("Penciller")).flatMap { it.split(",") }
            .map { it.trim() }.filter { it.isNotBlank() }.distinct(),
        description = value("Summary"),
        language = value("LanguageISO"),
        publisher = value("Publisher"),
        year = value("Year")?.toIntOrNull(),
        series = series,
        seriesIndex = number?.toIntOrNull(),
    )
}
