package com.aprireader.app.domain

import android.net.Uri
import com.aprireader.app.data.db.BookEntity
import com.aprireader.bookformat.model.BookFormat

/**
 * Поля, по которым полка сортирует книги.
 *
 * Выделены в отдельный интерфейс намеренно: так порядок книг проверяется
 * обычными JVM-тестами, без Android-типов вроде Uri, которых в юнит-тестах нет.
 */
interface ShelfSortable {
    val title: String
    val authorLine: String
    val addedAt: Long
    val lastOpenedAt: Long?
    val progress: Float
}

/** Книга в терминах предметной области — то, чем оперирует UI. */
data class Book(
    val id: String,
    val documentUri: Uri,
    val sourceUri: String?,
    val fileName: String,
    val fileSize: Long,
    val format: BookFormat,
    override val title: String,
    val authors: List<String>,
    val description: String?,
    val language: String?,
    val publisher: String?,
    val year: Int?,
    val series: String?,
    val seriesIndex: Int?,
    val coverPath: String?,
    override val addedAt: Long,
    override val lastOpenedAt: Long?,
    val available: Boolean,
    val favorite: Boolean,
    val finishedAt: Long?,
    override val progress: Float,
    val locatorUnit: Int,
    val locatorOffset: Int,
    val totalUnits: Int,
    val totalChars: Int,
    val autoAccent: Int?,
    val pinnedAccent: Int?,
    val metadataFetchedAt: Long?,
) : ShelfSortable {
    override val authorLine: String get() = authors.joinToString(", ")

    /** Ручной акцент всегда важнее автоматического — это обещание, данное пользователю. */
    val effectiveAccent: Int? get() = pinnedAccent ?: autoAccent

    val isStarted: Boolean get() = progress > 0.001f && finishedAt == null
    val isFinished: Boolean get() = finishedAt != null

    val seriesLine: String?
        get() = series?.let { name -> seriesIndex?.let { "$name · $it" } ?: name }
}

fun BookEntity.toDomain(): Book = Book(
    id = id,
    documentUri = Uri.parse(documentUri),
    sourceUri = sourceUri,
    fileName = fileName,
    fileSize = fileSize,
    format = runCatching { BookFormat.valueOf(format) }.getOrDefault(BookFormat.TXT),
    title = title,
    authors = authors.split(";").map { it.trim() }.filter { it.isNotEmpty() },
    description = description,
    language = language,
    publisher = publisher,
    year = year,
    series = series,
    seriesIndex = seriesIndex,
    coverPath = coverPath,
    addedAt = addedAt,
    lastOpenedAt = lastOpenedAt,
    available = available,
    favorite = favorite,
    finishedAt = finishedAt,
    progress = progress,
    locatorUnit = locatorUnit,
    locatorOffset = locatorOffset,
    totalUnits = totalUnits,
    totalChars = totalChars,
    autoAccent = autoAccent,
    pinnedAccent = pinnedAccent,
    metadataFetchedAt = metadataFetchedAt,
)

/** Папка-источник библиотеки. */
data class LibrarySource(
    val treeUri: Uri,
    val displayName: String,
    val addedAt: Long,
    val lastScanAt: Long,
    val available: Boolean,
    val bookCount: Int,
)
