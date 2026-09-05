package com.aprireader.app.data.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.aprireader.app.data.library.LibraryRepository
import com.aprireader.app.data.saf.SafDocumentAccess
import com.aprireader.app.domain.Book
import com.aprireader.bookformat.BookOpener
import com.aprireader.bookformat.model.BookFormat
import com.aprireader.bookformat.model.Chapter
import com.aprireader.bookformat.model.ChapterRef
import com.aprireader.bookformat.model.PagedDocument
import com.aprireader.bookformat.model.TextDocument
import com.aprireader.bookformat.pdf.PdfTextDocument
import com.aprireader.bookformat.util.readBytesUpTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable

/**
 * Открытая книга: держит документ, кэширует главы и умеет отдавать страницы.
 *
 * Живёт ровно столько, сколько открыт экран чтения. Всё чтение с диска
 * происходит на IO-диспетчере — открытие книги на 50 МБ не должно морозить UI.
 */
sealed class BookSession(val book: Book) : Closeable {

    /** Сколько «единиц» в книге: глав для текста, страниц для PDF и комиксов. */
    abstract val unitCount: Int

    /** Текстовый режим доступен не всегда: у комиксов текста нет вовсе. */
    open val supportsTextModes: Boolean get() = true

    class Text(
        book: Book,
        private val document: TextDocument,
    ) : BookSession(book) {

        val chapters: List<ChapterRef> = document.chapters
        override val unitCount: Int = chapters.size

        private val cache = object : LinkedHashMap<Int, Chapter>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Chapter>?): Boolean = size > 4
        }

        /** Доля книги, приходящаяся на каждую главу, — основа честного прогресса. */
        private val weights: List<Float> = run {
            val total = chapters.sumOf { it.approxChars.coerceAtLeast(1) }.toFloat()
            chapters.map { it.approxChars.coerceAtLeast(1) / total }
        }

        /**
         * Загрузка главы сериализована мьютексом.
         *
         * Поиск по книге и чтение работают с одним и тем же документом
         * параллельно, а PDFBox (текстовый слой PDF) не потокобезопасен: два
         * одновременных прохода по PDDocument роняют приложение. Кэш при этом
         * остаётся под своим замком, чтобы попадание в него не ждало чужой
         * загрузки.
         */
        private val loadMutex = Mutex()

        suspend fun chapter(index: Int): Chapter = withContext(Dispatchers.IO) {
            synchronized(cache) { cache[index] }?.let { return@withContext it }
            loadMutex.withLock {
                synchronized(cache) { cache[index] }
                    ?: document.loadChapter(index).also { synchronized(cache) { cache[index] = it } }
            }
        }

        suspend fun resource(href: String): ByteArray? = withContext(Dispatchers.IO) {
            runCatching { document.loadResource(href) }.getOrNull()
        }

        /** Прогресс по книге с учётом позиции внутри главы. */
        fun progressOf(chapterIndex: Int, fractionInChapter: Float): Float {
            if (weights.isEmpty()) return 0f
            val before = weights.take(chapterIndex.coerceIn(0, weights.size)).sum()
            val current = weights.getOrElse(chapterIndex) { 0f } * fractionInChapter.coerceIn(0f, 1f)
            return (before + current).coerceIn(0f, 1f)
        }

        override fun close() = document.close()
    }

    class Comic(
        book: Book,
        private val document: PagedDocument,
    ) : BookSession(book) {

        override val unitCount: Int = document.pageCount
        override val supportsTextModes: Boolean get() = false

        suspend fun page(index: Int): ByteArray? = withContext(Dispatchers.IO) {
            // readBytesUpTo, а не readBytes — CBZ/CBR-страница не должна
            // иметь возможность распаковаться в память без ограничения
            // размера (zip/rar-бомба), см. readBytesUpTo.
            runCatching { document.openPage(index)?.use { it.readBytesUpTo() } }.getOrNull()
        }

        override fun close() = document.close()
    }

    /**
     * PDF: страницы рисуются системным PdfRenderer, а текстовый слой достаётся
     * лениво — он нужен только для RSVP, бионического режима и поиска.
     */
    class Pdf(
        book: Book,
        private val descriptor: ParcelFileDescriptor,
        private val renderer: PdfRenderer,
        private val textLoader: suspend () -> TextDocument?,
    ) : BookSession(book) {

        override val unitCount: Int = renderer.pageCount

        private var textDocument: TextDocument? = null
        private val pageCache = object : android.util.LruCache<String, Bitmap>(24 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
        }

        suspend fun renderPage(index: Int, targetWidthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
            if (index < 0 || index >= unitCount) return@withContext null
            val key = "$index-$targetWidthPx"
            pageCache.get(key)?.takeIf { !it.isRecycled }?.let { return@withContext it }

            runCatching {
                synchronized(renderer) {
                    renderer.openPage(index).use { page ->
                        val scale = targetWidthPx.toFloat() / page.width
                        val width = targetWidthPx.coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(AndroidColor.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        pageCache.put(key, bitmap)
                        bitmap
                    }
                }
            }.getOrNull()
        }

        /** Текстовый слой загружается по первому требованию и дальше переиспользуется. */
        suspend fun text(): TextDocument? {
            textDocument?.let { return it }
            return textLoader()?.also { textDocument = it }
        }

        override fun close() {
            pageCache.evictAll()
            runCatching { textDocument?.close() }
            runCatching { renderer.close() }
            runCatching { descriptor.close() }
        }
    }
}

class BookSessionFactory(
    private val context: Context,
    private val library: LibraryRepository,
) {

    /** Открывает книгу в том режиме, который соответствует её формату. */
    suspend fun open(book: Book): Result<BookSession> = withContext(Dispatchers.IO) {
        runCatching {
            val access = library.accessFor(book)
            when (book.format) {
                BookFormat.PDF -> openPdf(book, access)
                BookFormat.CBZ, BookFormat.CBR -> BookSession.Comic(
                    book,
                    BookOpener.open(access, book.format) as PagedDocument,
                )
                else -> when (val document = BookOpener.open(access, book.format)) {
                    is TextDocument -> BookSession.Text(book, document)
                    is PagedDocument -> BookSession.Comic(book, document)
                    is com.aprireader.bookformat.model.AudioDocument -> throw IllegalStateException("Аудиокниги воспроизводятся через встроенный аудиоплеер")
                }
            }
        }
    }

    private fun openPdf(book: Book, access: SafDocumentAccess): BookSession.Pdf {
        val descriptor = context.contentResolver.openFileDescriptor(book.documentUri, "r")
            ?: throw java.io.FileNotFoundException("PDF недоступен")
        val renderer = PdfRenderer(descriptor)
        return BookSession.Pdf(
            book = book,
            descriptor = descriptor,
            renderer = renderer,
            textLoader = {
                withContext(Dispatchers.IO) {
                    runCatching { access.materializeFile()?.let { PdfTextDocument(it) } }.getOrNull()
                }
            },
        )
    }
}
