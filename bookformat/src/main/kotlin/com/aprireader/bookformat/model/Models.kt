package com.aprireader.bookformat.model

import java.io.Closeable
import java.io.InputStream

/** Поддерживаемые форматы книг и аудиокниг. */
enum class BookFormat(val extensions: List<String>, val mime: String, val isPaged: Boolean, val isAudio: Boolean = false) {
    EPUB(listOf("epub"), "application/epub+zip", false),
    FB2(listOf("fb2"), "application/x-fictionbook+xml", false),
    FB2_ZIP(listOf("fb2.zip", "fbz"), "application/x-zip-compressed-fb2", false),
    PDF(listOf("pdf"), "application/pdf", true),
    TXT(listOf("txt", "text", "md"), "text/plain", false),
    HTML(listOf("html", "htm", "xhtml"), "text/html", false),
    CBZ(listOf("cbz"), "application/vnd.comicbook+zip", true),
    CBR(listOf("cbr"), "application/vnd.comicbook-rar", true),
    M4B(listOf("m4b"), "audio/x-m4b", false, true),
    MP3(listOf("mp3"), "audio/mpeg", false, true),
    M4A(listOf("m4a"), "audio/mp4", false, true),
    AAC(listOf("aac"), "audio/aac", false, true),
    FLAC(listOf("flac"), "audio/flac", false, true),
    OGG(listOf("ogg", "oga"), "audio/ogg", false, true),
    OPUS(listOf("opus"), "audio/opus", false, true);

    companion object {
        fun fromExtension(name: String): BookFormat? {
            val lower = name.lowercase()
            // fb2.zip проверяется первым: у него составное расширение
            if (lower.endsWith(".fb2.zip")) return FB2_ZIP
            val ext = lower.substringAfterLast('.', "")
            return entries.firstOrNull { ext in it.extensions }
        }
    }
}

/**
 * Источник байтов книги. Абстракция поверх SAF: модуль парсинга не знает про Uri
 * и ContentResolver, а получает уже готовый способ открыть поток.
 */
interface FileSource {
    /** Отображаемое имя файла вместе с расширением. */
    val displayName: String

    /** Размер в байтах, -1 если неизвестен. */
    val size: Long

    fun openStream(): InputStream

    /**
     * Локальный файл, если источник может быть представлен файлом на диске.
     * Нужен парсерам, которым требуется произвольный доступ (RAR, PDF).
     * null означает «только последовательное чтение».
     */
    fun asFile(): java.io.File? = null
}

/** Метаданные книги, извлечённые из самого файла (не из сети). */
data class BookMetadata(
    val title: String,
    val authors: List<String> = emptyList(),
    val description: String? = null,
    val language: String? = null,
    val publisher: String? = null,
    val year: Int? = null,
    val series: String? = null,
    val seriesIndex: Int? = null,
    val identifiers: Map<String, String> = emptyMap(),
    /** Сырые байты обложки, если она нашлась внутри файла. */
    val cover: ByteArray? = null,
) {
    val authorLine: String get() = authors.joinToString(", ").ifBlank { "" }

    override fun equals(other: Any?): Boolean =
        this === other || (other is BookMetadata && title == other.title && authors == other.authors)

    override fun hashCode(): Int = 31 * title.hashCode() + authors.hashCode()
}

/** Инлайновое оформление внутри абзаца. */
enum class SpanKind { BOLD, ITALIC, MONO, UNDERLINE, STRIKE, SUPERSCRIPT, SUBSCRIPT, LINK }

data class TextSpan(val start: Int, val end: Int, val kind: SpanKind, val target: String? = null)

/** Текст с инлайновой разметкой. */
data class RichText(val text: String, val spans: List<TextSpan> = emptyList()) {
    val isBlank: Boolean get() = text.isBlank()
}

enum class ParagraphKind { BODY, QUOTE, EPIGRAPH, POEM, CODE, CAPTION, NOTE }

/** Типизированный блок содержимого. Рендерится нативным Compose, не WebView. */
sealed interface ContentBlock {
    data class Heading(val text: RichText, val level: Int, val anchor: String? = null) : ContentBlock
    data class Paragraph(
        val text: RichText,
        val kind: ParagraphKind = ParagraphKind.BODY,
        val anchor: String? = null,
    ) : ContentBlock

    data class Image(val href: String, val caption: String? = null) : ContentBlock
    data object Separator : ContentBlock
}

/** Ссылка на главу без её содержимого — для оглавления и ленивой загрузки. */
data class ChapterRef(
    val index: Int,
    val id: String,
    val title: String?,
    /** Глубина вложенности в оглавлении, 0 — верхний уровень. */
    val depth: Int = 0,
    /** Приблизительный размер в символах, для оценки прогресса до загрузки. */
    val approxChars: Int = 0,
)

data class Chapter(
    val ref: ChapterRef,
    val blocks: List<ContentBlock>,
) {
    val charCount: Int by lazy {
        blocks.sumOf {
            when (it) {
                is ContentBlock.Heading -> it.text.text.length
                is ContentBlock.Paragraph -> it.text.text.length
                else -> 0
            }
        }
    }
}

/** Общий контракт открытого документа. */
sealed interface BookDocument : Closeable {
    val format: BookFormat
    val metadata: BookMetadata
}

/** Документ с текстовым содержимым: EPUB, FB2, TXT, HTML, PDF (через извлечение текста). */
interface TextDocument : BookDocument {
    val chapters: List<ChapterRef>
    fun loadChapter(index: Int): Chapter

    /** Возвращает байты внутреннего ресурса (иллюстрации) или null. */
    fun loadResource(href: String): ByteArray? = null
}

/** Документ-страницы: CBZ/CBR и PDF в режиме просмотра страниц. */
interface PagedDocument : BookDocument {
    val pageCount: Int

    /** Поток с изображением страницы. Для PDF не используется — там рендер через PdfRenderer. */
    fun openPage(index: Int): InputStream?
}

/** Аудио-документ: M4B, MP3, M4A, AAC, FLAC, OGG, OPUS. */
interface AudioDocument : BookDocument

class SimpleAudioDocument(
    override val format: BookFormat,
    override val metadata: BookMetadata,
) : AudioDocument {
    override fun close() {}
}

class BookParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
