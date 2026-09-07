package com.aprireader.app.data.library

import android.content.Context
import android.net.Uri
import com.aprireader.app.data.db.BookDao
import com.aprireader.app.data.db.BookEntity
import com.aprireader.app.data.db.SourceDao
import com.aprireader.app.data.db.SourceEntity
import com.aprireader.app.data.saf.BookKey
import com.aprireader.app.data.saf.DocumentCache
import com.aprireader.app.data.saf.DocumentInfo
import com.aprireader.app.data.saf.LibraryScanner
import com.aprireader.app.data.saf.SafDocumentAccess
import com.aprireader.app.data.saf.StorageAccessManager
import com.aprireader.app.data.saf.queryDocument
import com.aprireader.app.data.saf.treeDisplayName
import java.io.File
import com.aprireader.app.domain.Book
import com.aprireader.app.domain.LibrarySource
import com.aprireader.app.domain.toDomain
import com.aprireader.bookformat.BookOpener
import com.aprireader.bookformat.model.BookFormat
import com.aprireader.bookformat.model.BookMetadata
import com.aprireader.bookformat.model.PagedDocument
import com.aprireader.bookformat.model.TextDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Итог попытки импортировать один документ.
 *
 * Раньше это был просто `Boolean`, и «формат не поддерживается» неотличимо
 * сливалось с «книга уже в библиотеке» — пользователь видел одно и то же
 * сообщение «Книги уже в библиотеке» для двух совершенно разных причин, и не
 * мог понять, что на самом деле произошло с его файлом.
 */
internal enum class ImportOutcome { ADDED, ALREADY_PRESENT, UNSUPPORTED_FORMAT }

/** Итог пакетного добавления отдельных файлов — по каждой причине отдельно, не одним числом. */
data class ImportSummary(
    val added: Int = 0,
    val alreadyPresent: Int = 0,
    val unsupported: Int = 0,
)

/** Ход импорта — показывается на полке, чтобы длинное сканирование не выглядело зависанием. */
data class ImportProgress(
    val running: Boolean = false,
    val scanned: Int = 0,
    val imported: Int = 0,
    val total: Int = 0,
    val currentName: String? = null,
) {
    val fraction: Float get() = if (total <= 0) 0f else (imported.toFloat() / total).coerceIn(0f, 1f)
}

class LibraryRepository(
    private val context: Context,
    private val bookDao: BookDao,
    private val sourceDao: SourceDao,
    private val coverStore: CoverStore,
    private val storageAccess: StorageAccessManager,
    private val scanner: LibraryScanner,
) {

    private val _importProgress = MutableStateFlow(ImportProgress())
    val importProgress: StateFlow<ImportProgress> = _importProgress.asStateFlow()

    val books: Flow<List<Book>> = bookDao.observeAll().map { list -> list.map { it.toDomain() } }

    val sources: Flow<List<LibrarySource>> = sourceDao.observeAll().map { list ->
        list.map {
            LibrarySource(
                treeUri = Uri.parse(it.treeUri),
                displayName = it.displayName,
                addedAt = it.addedAt,
                lastScanAt = it.lastScanAt,
                available = it.available,
                bookCount = it.bookCount,
            )
        }
    }

    fun observeBook(id: String): Flow<Book?> = bookDao.observeById(id).map { it?.toDomain() }

    fun observeRecent(limit: Int = 12): Flow<List<Book>> =
        bookDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    suspend fun getBook(id: String): Book? = bookDao.getById(id)?.toDomain()

    suspend fun mostRecentBook(): Book? = bookDao.getMostRecent()?.toDomain()

    /** Доступ к файлу книги. Единая точка — весь ввод-вывод книг идёт через SAF. */
    fun accessFor(book: Book): SafDocumentAccess = SafDocumentAccess(
        context = context,
        uri = book.documentUri,
        displayName = book.fileName,
        mimeType = book.format.mime,
    )

    // --- источники ---

    /**
     * Добавляет папку: сначала закрепляет разрешение, затем сканирует.
     * Без закрепления книги стали бы недоступны после перезапуска.
     */
    suspend fun addFolder(treeUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        if (!storageAccess.persist(treeUri)) {
            return@withContext Result.failure(IllegalStateException("Не удалось закрепить доступ к папке"))
        }
        sourceDao.upsert(
            SourceEntity(
                treeUri = treeUri.toString(),
                displayName = treeUri.treeDisplayName(),
                addedAt = System.currentTimeMillis(),
                available = true,
            )
        )
        runCatching { scanSource(treeUri) }
    }

    /** Добавляет отдельные файлы, выбранные через ACTION_OPEN_DOCUMENT. */
    suspend fun addFiles(uris: List<Uri>): Result<ImportSummary> = withContext(Dispatchers.IO) {
        _importProgress.value = ImportProgress(running = true, total = uris.size)
        var summary = ImportSummary()
        try {
            for ((index, uri) in uris.withIndex()) {
                storageAccess.persist(uri)
                val info = context.queryDocument(uri) ?: continue
                _importProgress.value = _importProgress.value.copy(
                    scanned = index + 1,
                    currentName = info.displayName,
                )
                summary = when (importDocument(info, sourceUri = null)) {
                    ImportOutcome.ADDED -> summary.copy(added = summary.added + 1)
                    ImportOutcome.ALREADY_PRESENT -> summary.copy(alreadyPresent = summary.alreadyPresent + 1)
                    ImportOutcome.UNSUPPORTED_FORMAT -> summary.copy(unsupported = summary.unsupported + 1)
                }
                _importProgress.value = _importProgress.value.copy(imported = summary.added)
            }
            Result.success(summary)
        } catch (error: Throwable) {
            Result.failure(error)
        } finally {
            _importProgress.value = ImportProgress()
        }
    }

    /** Пересканирует все доступные источники: находит новые книги и восстанавливает связи. */
    suspend fun rescanAll(): Int = withContext(Dispatchers.IO) {
        var total = 0
        for (source in sourceDao.getAll()) {
            val uri = Uri.parse(source.treeUri)
            if (!storageAccess.isTreeReachable(uri)) {
                markSourceUnavailable(source.treeUri)
                continue
            }
            total += runCatching { scanSource(uri) }.getOrDefault(0)
        }
        total
    }

    /**
     * Проверяет актуальность доступа ко всем источникам.
     * Вызывается при старте и возврате в приложение: пользователь мог отозвать
     * разрешение в системных настройках, пока приложение было в фоне.
     */
    suspend fun revalidateAccess() = withContext(Dispatchers.IO) {
        for (source in sourceDao.getAll()) {
            val uri = Uri.parse(source.treeUri)
            val reachable = storageAccess.isTreeReachable(uri)
            if (reachable != source.available) {
                sourceDao.setAvailable(source.treeUri, reachable)
                bookDao.setSourceAvailability(source.treeUri, reachable)
            }
        }
    }

    private suspend fun markSourceUnavailable(treeUri: String) {
        sourceDao.setAvailable(treeUri, false)
        bookDao.setSourceAvailability(treeUri, false)
    }

    /** Удаляет источник. Книги по умолчанию остаются — вместе с прогрессом чтения. */
    suspend fun removeSource(treeUri: Uri, deleteBooks: Boolean) = withContext(Dispatchers.IO) {
        val key = treeUri.toString()
        if (deleteBooks) {
            bookDao.getBySource(key).forEach { coverStore.delete(it.id) }
            bookDao.deleteBySource(key)
        } else {
            bookDao.setSourceAvailability(key, false)
        }
        sourceDao.delete(key)
        storageAccess.release(treeUri)
    }

    private suspend fun scanSource(treeUri: Uri): Int {
        _importProgress.value = ImportProgress(running = true)
        try {
            val documents = scanner.scanTree(treeUri) { count ->
                _importProgress.value = _importProgress.value.copy(scanned = count)
            }
            _importProgress.value = _importProgress.value.copy(total = documents.size)

            var imported = 0
            for (document in documents) {
                _importProgress.value = _importProgress.value.copy(currentName = document.displayName)
                if (importDocument(document, sourceUri = treeUri.toString()) == ImportOutcome.ADDED) imported++
                _importProgress.value = _importProgress.value.copy(imported = imported)
            }
            sourceDao.markScanned(treeUri.toString(), System.currentTimeMillis(), documents.size)
            sourceDao.setAvailable(treeUri.toString(), true)
            return imported
        } finally {
            _importProgress.value = ImportProgress()
        }
    }

    /**
     * Импортирует один документ. Если книга с таким контентным ключом уже есть,
     * обновляется только её адрес — прогресс, закладки и статистика сохраняются.
     */
    /**
     * Определяет формат добавляемого файла надёжнее, чем просто по расширению.
     *
     * `BookFormat.fromExtension(displayName)` — единственная проверка, которая
     * была здесь раньше — молча ломается, стоит SAF-провайдеру вернуть
     * DISPLAY_NAME без расширения. Это не гипотетический случай: часть
     * файловых менеджеров и провайдеров показывает вместо реального имени
     * файла заголовок из встроенных метаданных (характерно для FB2 — там
     * заголовок книги — это буквально первый читаемый текст в файле), из-за
     * чего пользователь видел «Формат не поддерживается» для совершенно
     * нормального файла.
     *
     * `BookOpener.detectFormat()` уже умеет надёжно определять формат по
     * расширению → MIME → сигнатуре первых байт файла (в этом порядке
     * доверия) — этим же путём формат определяется при самом открытии книги.
     * Раньше импорт был единственным местом, которое этот путь обходило.
     * Разбор сигнатуры требует прочитать начало файла, поэтому чтение
     * запускается только если расширение и MIME ничего не дали — на
     * подавляющем большинстве файлов, у которых имя нормальное, это как и
     * раньше ровно одна проверка расширения без единого обращения к диску.
     */
    private suspend fun detectImportFormat(info: DocumentInfo): BookFormat? =
        BookOpener.detectFormat(info.displayName, info.mimeType, header = null)
            ?: withContext(Dispatchers.IO) {
                val header = runCatching {
                    context.contentResolver.openInputStream(info.uri)?.use { it.readHeader() }
                }.getOrNull()
                header?.let { BookOpener.detectFormat(info.displayName, info.mimeType, it) }
            }

    private suspend fun importDocument(info: DocumentInfo, sourceUri: String?): ImportOutcome {
        val format = detectImportFormat(info) ?: return ImportOutcome.UNSUPPORTED_FORMAT
        val id = BookKey.compute(context, info.uri, info.size, info.displayName)

        val existing = bookDao.getById(id)
        if (existing != null) {
            // Диагностика на случай ложного срабатывания «уже в библиотеке»:
            // если пользователь уверен, что книги не было, здесь будет видно,
            // с какой именно записью совпал контентный ключ и почему.
            android.util.Log.d(
                "LibraryRepository",
                "importDocument: \"${info.displayName}\" (${info.size} B) matched existing book " +
                    "id=$id title=\"${existing.title}\" file=\"${existing.fileName}\" " +
                    "size=${existing.fileSize} uri=${existing.documentUri} available=${existing.available}",
            )
            if (existing.documentUri != info.uri.toString() || !existing.available) {
                bookDao.relink(id, info.uri.toString(), info.displayName)
            }
            // Если у ранее добавленной книги отсутствовали метаданные или обложка, обновляем их
            if (existing.authors.isBlank() || existing.coverPath == null || existing.totalUnits == 0) {
                val access = SafDocumentAccess(context, info.uri, info.displayName, info.mimeType)
                val parsed = when {
                    format == BookFormat.PDF -> readPdfPreview(info)
                    format.isAudio -> readAudioMetadata(info)
                    else -> readWithParser(access, format, info)
                }
                val (metadata, units, chars) = parsed
                val stored = if (existing.coverPath == null) metadata.cover?.let { coverStore.save(id, it) } else null

                val fallbackName = existing.fileName.substringBeforeLast('.')
                val newTitle = if (existing.title == fallbackName || existing.title == "Без названия") {
                    metadata.title.ifBlank { existing.title }
                } else {
                    existing.title
                }

                bookDao.upsert(
                    existing.copy(
                        title = newTitle,
                        authors = if (existing.authors.isBlank()) metadata.authors.joinToString("; ") else existing.authors,
                        description = existing.description ?: metadata.description,
                        language = existing.language ?: metadata.language,
                        publisher = existing.publisher ?: metadata.publisher,
                        year = existing.year ?: metadata.year,
                        series = existing.series ?: metadata.series,
                        seriesIndex = existing.seriesIndex ?: metadata.seriesIndex,
                        coverPath = existing.coverPath ?: stored?.path,
                        totalUnits = if (existing.totalUnits == 0) units else existing.totalUnits,
                        totalChars = if (existing.totalChars == 0) chars else existing.totalChars,
                        autoAccent = existing.autoAccent ?: stored?.accent,
                    )
                )
            }
            return ImportOutcome.ALREADY_PRESENT
        }

        val access = SafDocumentAccess(context, info.uri, info.displayName, info.mimeType)

        // PDF разбирается через PdfRenderer, аудиокниги через MediaMetadataRetriever, остальные через BookOpener
        val parsed = when {
            format == BookFormat.PDF -> readPdfPreview(info)
            format.isAudio -> readAudioMetadata(info)
            else -> readWithParser(access, format, info)
        }

        val (metadata, units, chars) = parsed
        val stored = metadata.cover?.let { coverStore.save(id, it) }

        val uriToStore = if (sourceUri == null && !storageAccess.hasAccess(info.uri)) {
            val localFile = DocumentCache.materialize(context, info.uri, info.displayName)
            if (localFile != null) Uri.fromFile(localFile).toString() else info.uri.toString()
        } else {
            info.uri.toString()
        }

        bookDao.upsert(
            BookEntity(
                id = id,
                documentUri = uriToStore,
                sourceUri = sourceUri,
                fileName = info.displayName,
                fileSize = info.size,
                format = format.name,
                title = metadata.title.ifBlank { info.displayName.substringBeforeLast('.') },
                authors = metadata.authors.joinToString("; "),
                description = metadata.description,
                language = metadata.language,
                publisher = metadata.publisher,
                year = metadata.year,
                series = metadata.series,
                seriesIndex = metadata.seriesIndex,
                coverPath = stored?.path,
                addedAt = System.currentTimeMillis(),
                available = true,
                totalUnits = units,
                totalChars = chars,
                autoAccent = stored?.accent,
            )
        )
        return ImportOutcome.ADDED
    }

    /** Метаданные, число единиц и объём текста, прочитанные парсером формата. */
    private fun readWithParser(
        access: SafDocumentAccess,
        format: BookFormat,
        info: DocumentInfo,
    ): Triple<BookMetadata, Int, Int> {
        val parsed = runCatching {
            BookOpener.open(access, format).use { document ->
                val units = when (document) {
                    is TextDocument -> document.chapters.size
                    is PagedDocument -> document.pageCount
                    else -> 1
                }
                val chars = (document as? TextDocument)?.chapters?.sumOf { it.approxChars } ?: 0
                Triple(document.metadata, units, chars)
            }
        }.getOrElse {
            // Битый или неподдерживаемый файл не должен ронять весь импорт.
            Triple(BookMetadata(title = info.displayName.substringBeforeLast('.')), 0, 0)
        }
        return parsed
    }

    /**
     * Извлечение метаданных, длительности и обложки из аудиокниги (M4B, MP3, M4A, FLAC, OGG, OPUS, AAC).
     */
    private fun readAudioMetadata(info: DocumentInfo): Triple<BookMetadata, Int, Int> {
        val fallbackTitle = info.displayName.substringBeforeLast('.').replace('_', ' ')
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            context.contentResolver.openFileDescriptor(info.uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
                val title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.takeIf { it.isNotBlank() }
                    ?: fallbackTitle
                val artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    ?: retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_AUTHOR)
                    ?: ""
                val album = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
                val year = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_YEAR)?.toIntOrNull()
                    ?: retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DATE)?.take(4)?.toIntOrNull()
                val durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val picture = retriever.embeddedPicture

                val authors = if (artist.isNotBlank()) listOf(artist) else emptyList()
                val metadata = BookMetadata(
                    title = title,
                    authors = authors,
                    description = album?.let { "Альбом: $it" },
                    year = year,
                    cover = picture,
                )
                Triple(metadata, 1, (durationMs / 1000).toInt())
            } ?: Triple(BookMetadata(title = fallbackTitle), 1, 0)
        } catch (e: Throwable) {
            Triple(BookMetadata(title = fallbackTitle), 1, 0)
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Быстрый предпросмотр PDF: число страниц и первая страница как обложка.
     *
     * Разбор текстового слоя здесь сознательно не делается — он нужен только
     * тому, кто включит RSVP или бионику, и стоит копии файла на диск плюс
     * полного разбора документа.
     */
    private fun readPdfPreview(info: DocumentInfo): Triple<BookMetadata, Int, Int> {
        val fallbackTitle = info.displayName.substringBeforeLast('.').replace('_', ' ')
        return runCatching {
            context.contentResolver.openFileDescriptor(info.uri, "r").use { descriptor ->
                if (descriptor == null) return@runCatching Triple(BookMetadata(fallbackTitle), 0, 0)
                val pdfMeta = runCatching {
                    context.contentResolver.openInputStream(info.uri)?.use { stream ->
                        com.aprireader.bookformat.pdf.PdfMetadataExtractor.extract(stream, fallbackTitle)
                    }
                }.getOrNull() ?: BookMetadata(fallbackTitle)

                android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
                    val cover = runCatching { renderFirstPage(renderer) }.getOrNull()
                    Triple(
                        pdfMeta.copy(cover = cover),
                        renderer.pageCount,
                        0,
                    )
                }
            }
        }.getOrElse { Triple(BookMetadata(fallbackTitle), 0, 0) }
    }

    private fun renderFirstPage(renderer: android.graphics.pdf.PdfRenderer): ByteArray? {
        if (renderer.pageCount == 0) return null
        renderer.openPage(0).use { page ->
            val width = COVER_RENDER_WIDTH
            val height = (page.height.toFloat() / page.width * width).toInt().coerceAtLeast(1)
            val bitmap = android.graphics.Bitmap.createBitmap(
                width,
                height,
                android.graphics.Bitmap.Config.ARGB_8888,
            )
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            val stream = java.io.ByteArrayOutputStream()
            @Suppress("DEPRECATION")
            bitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP, 85, stream)
            bitmap.recycle()
            return stream.toByteArray()
        }
    }

    // --- операции над книгой ---

    suspend fun setFavorite(bookId: String, favorite: Boolean) = bookDao.setFavorite(bookId, favorite)

    suspend fun setPinnedAccent(bookId: String, color: Int?) = bookDao.setPinnedAccent(bookId, color)

    suspend fun touch(bookId: String) = bookDao.touch(bookId, System.currentTimeMillis())

    suspend fun updateProgress(bookId: String, unit: Int, offset: Int, progress: Float, totalUnits: Int) {
        bookDao.updateProgress(bookId, unit, offset, progress, totalUnits, System.currentTimeMillis())
        // Виджет на домашнем экране показывает именно эту книгу — обновляем сразу,
        // иначе он живёт вчерашним прогрессом до следующего системного апдейта.
        com.aprireader.app.widget.CurrentBookWidget.refresh(context)
    }

    suspend fun deleteBook(bookId: String) = withContext(Dispatchers.IO) {
        coverStore.delete(bookId)
        bookDao.delete(bookId)
    }

    suspend fun deleteBooks(bookIds: Collection<String>) = withContext(Dispatchers.IO) {
        if (bookIds.isEmpty()) return@withContext
        bookIds.forEach { coverStore.delete(it) }
        bookDao.delete(bookIds.toList())
    }

    suspend fun updateMetadata(
        bookId: String,
        title: String? = null,
        authors: List<String>? = null,
        description: String? = null,
        coverBytes: ByteArray? = null,
        year: Int? = null,
        publisher: String? = null,
        series: String? = null,
        seriesIndex: Int? = null,
        fromNetwork: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        val existing = bookDao.getById(bookId) ?: return@withContext
        val stored = coverBytes?.let { coverStore.save(bookId, it) }
        bookDao.upsert(
            existing.copy(
                title = title?.takeIf { it.isNotBlank() } ?: existing.title,
                authors = authors?.joinToString("; ") ?: existing.authors,
                description = description ?: existing.description,
                year = year ?: existing.year,
                publisher = publisher ?: existing.publisher,
                series = series ?: existing.series,
                seriesIndex = seriesIndex ?: existing.seriesIndex,
                coverPath = stored?.path ?: existing.coverPath,
                autoAccent = stored?.accent ?: existing.autoAccent,
                metadataFetchedAt = if (fromNetwork) System.currentTimeMillis() else existing.metadataFetchedAt,
            )
        )
    }

    /** Удаляет локальные копии книг, которые делались для RAR и PDF. */
    suspend fun clearBookCache() = withContext(Dispatchers.IO) {
        com.aprireader.app.data.saf.DocumentCache.clear(context)
    }

    /** Проверяет доступность конкретной книги перед открытием. */
    suspend fun ensureAvailable(book: Book): Boolean {
        val reachable = storageAccess.isDocumentReachable(book.documentUri)
        if (reachable != book.available) bookDao.setAvailable(book.id, reachable)
        return reachable
    }

    /**
     * Устанавливает стартовую приветственную книгу-руководство «Добро пожаловать в ApriReader» из assets.
     * При первом запуске (когда база пуста и руководство ещё не устанавливалось) распаковывает
     * книгу во внутреннее хранилище и добавляет на полку, чтобы пользователь мог сразу оценить ридер.
     */
    suspend fun installWelcomeGuide(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("apri_installation", Context.MODE_PRIVATE)
        val alreadyInstalled = prefs.getBoolean("welcome_guide_installed", false)
        if (!force && alreadyInstalled) {
            return@withContext false
        }

        // Если не принудительно, и у пользователя уже есть книги, не навязываем
        if (!force && bookDao.count() > 0) {
            prefs.edit().putBoolean("welcome_guide_installed", true).apply()
            return@withContext false
        }

        val targetDir = File(context.filesDir, "books").apply { mkdirs() }
        val targetFile = File(targetDir, "welcome_guide.epub")

        val copied = runCatching {
            context.assets.open("welcome_guide.epub").use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        }.getOrDefault(false)

        if (!copied || !targetFile.exists()) {
            return@withContext false
        }

        val uri = Uri.fromFile(targetFile)
        val info = DocumentInfo(
            uri = uri,
            displayName = "welcome_guide.epub",
            size = targetFile.length(),
            mimeType = BookFormat.EPUB.mime,
            lastModified = targetFile.lastModified(),
        )

        val imported = importDocument(info, sourceUri = null) == ImportOutcome.ADDED
        if (imported) {
            prefs.edit().putBoolean("welcome_guide_installed", true).apply()
        }
        imported
    }

    /** Находит ID книги-руководства, если она установлена и присутствует в базе. */
    suspend fun findWelcomeGuideBookId(): String? = withContext(Dispatchers.IO) {
        val targetFile = File(context.filesDir, "books/welcome_guide.epub")
        if (targetFile.exists()) {
            val uri = Uri.fromFile(targetFile)
            val id = BookKey.compute(context, uri, targetFile.length(), "welcome_guide.epub")
            if (bookDao.getById(id) != null) return@withContext id
        }
        null
    }

    private companion object {
        /** Ширина рендера первой страницы PDF под обложку. */
        const val COVER_RENDER_WIDTH = 600
    }
}

/** Столько же байт, сколько BookOpener читает для собственного определения формата по сигнатуре. */
private const val FORMAT_SNIFF_BYTES = 1024

/** Читает начало потока для определения формата по сигнатуре — столько, сколько наберётся, но не больше [n]. */
private fun java.io.InputStream.readHeader(n: Int = FORMAT_SNIFF_BYTES): ByteArray {
    val buffer = ByteArray(n)
    var read = 0
    while (read < n) {
        val count = this.read(buffer, read, n - read)
        if (count <= 0) break
        read += count
    }
    return if (read == n) buffer else buffer.copyOf(read)
}
