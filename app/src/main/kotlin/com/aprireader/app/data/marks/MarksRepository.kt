package com.aprireader.app.data.marks

import com.aprireader.app.data.db.MarkDao
import com.aprireader.app.data.db.MarkEntity
import com.aprireader.app.data.db.MarkKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Закладки, выделения и заметки. Всё локально, экспорт — по явному действию пользователя. */
class MarksRepository(private val dao: MarkDao) {

    fun forBook(bookId: String): Flow<List<MarkEntity>> = dao.observeForBook(bookId)

    fun bookmarks(bookId: String): Flow<List<MarkEntity>> =
        forBook(bookId).map { marks -> marks.filter { it.kind == MarkKind.BOOKMARK.name } }

    fun highlights(bookId: String): Flow<List<MarkEntity>> =
        forBook(bookId).map { marks -> marks.filter { it.kind != MarkKind.BOOKMARK.name } }

    val all: Flow<List<MarkEntity>> = dao.observeAll()

    suspend fun addBookmark(bookId: String, unit: Int, offset: Int, chapterTitle: String?, quote: String?): Long =
        dao.upsert(
            MarkEntity(
                bookId = bookId,
                kind = MarkKind.BOOKMARK.name,
                unit = unit,
                startOffset = offset,
                endOffset = offset,
                quotedText = quote,
                chapterTitle = chapterTitle,
                createdAt = System.currentTimeMillis(),
            )
        )

    suspend fun addHighlight(
        bookId: String,
        unit: Int,
        startOffset: Int,
        endOffset: Int,
        quote: String,
        note: String? = null,
        colorIndex: Int = 0,
        chapterTitle: String? = null,
    ): Long = dao.upsert(
        MarkEntity(
            bookId = bookId,
            kind = if (note.isNullOrBlank()) MarkKind.HIGHLIGHT.name else MarkKind.NOTE.name,
            unit = unit,
            startOffset = startOffset,
            endOffset = endOffset,
            quotedText = quote,
            note = note,
            colorIndex = colorIndex,
            chapterTitle = chapterTitle,
            createdAt = System.currentTimeMillis(),
        )
    )

    suspend fun update(mark: MarkEntity) = dao.upsert(mark)

    suspend fun delete(id: Long) = dao.delete(id)

    /** Экспорт выделений в Markdown — файл создаётся локально, никуда не отправляется. */
    fun exportMarkdown(bookTitle: String, author: String, marks: List<MarkEntity>): String = buildString {
        appendLine("# $bookTitle")
        if (author.isNotBlank()) appendLine("_" + author + "_")
        appendLine()
        marks.sortedWith(compareBy({ it.unit }, { it.startOffset })).forEach { mark ->
            mark.chapterTitle?.let { appendLine("## $it") }
            mark.quotedText?.let { quote ->
                appendLine("> " + quote.trim().replace("\n", "\n> "))
            }
            mark.note?.takeIf { it.isNotBlank() }?.let { note ->
                appendLine()
                appendLine(note.trim())
            }
            appendLine()
        }
    }
}
