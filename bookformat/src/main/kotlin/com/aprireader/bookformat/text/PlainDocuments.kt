package com.aprireader.bookformat.text

import com.aprireader.bookformat.model.BookFormat
import com.aprireader.bookformat.model.BookMetadata
import com.aprireader.bookformat.model.Chapter
import com.aprireader.bookformat.model.ChapterRef
import com.aprireader.bookformat.model.ContentBlock
import com.aprireader.bookformat.model.ParagraphKind
import com.aprireader.bookformat.model.RichText
import com.aprireader.bookformat.model.TextDocument
import com.aprireader.bookformat.util.HtmlBlockParser

/**
 * Простой текст. Заголовки распознаются эвристически, потому что в TXT нет
 * структуры: строки вида «Глава 7», «CHAPTER IV», «* * *» и короткие строки
 * капсом — это почти всегда заголовки, и книга без них читается заметно хуже.
 */
class TxtDocument(
    text: String,
    private val fileName: String,
    override val format: BookFormat = BookFormat.TXT,
) : TextDocument {

    private val parts: List<List<ContentBlock>>

    override val metadata: BookMetadata
    override val chapters: List<ChapterRef>

    init {
        val blocks = buildBlocks(text)
        parts = splitIntoChapters(blocks)
        metadata = BookMetadata(
            title = guessTitle(blocks, fileName),
            authors = emptyList(),
        )
        chapters = parts.mapIndexed { index, part ->
            ChapterRef(
                index = index,
                id = "part$index",
                title = (part.firstOrNull() as? ContentBlock.Heading)?.text?.text
                    ?: if (parts.size > 1) "Часть ${index + 1}" else null,
                approxChars = part.sumOf { block ->
                    when (block) {
                        is ContentBlock.Heading -> block.text.text.length
                        is ContentBlock.Paragraph -> block.text.text.length
                        else -> 0
                    }
                },
            )
        }
    }

    override fun loadChapter(index: Int): Chapter = Chapter(chapters[index], parts[index])

    override fun close() = Unit

    private companion object {
        /** Порог разбиения: держим главу в пределах, комфортных для пагинации и прогресса. */
        const val TARGET_CHARS_PER_PART = 60_000

        val headingPatterns = listOf(
            Regex("""^\s*(глава|часть|книга|том)\s+[\dIVXLC]+.*""", RegexOption.IGNORE_CASE),
            Regex("""^\s*(chapter|part|book)\s+[\dIVXLC]+.*""", RegexOption.IGNORE_CASE),
            Regex("""^\s*[*#=~-]{3,}\s*$"""),
        )

        fun buildBlocks(text: String): List<ContentBlock> {
            val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
            val out = ArrayList<ContentBlock>()
            val paragraph = StringBuilder()

            fun flush() {
                val value = paragraph.toString().trim()
                paragraph.setLength(0)
                if (value.isEmpty()) return
                out += if (isHeading(value)) {
                    ContentBlock.Heading(RichText(value), 2)
                } else {
                    ContentBlock.Paragraph(RichText(value), ParagraphKind.BODY)
                }
            }

            for (line in normalized.split('\n')) {
                val trimmed = line.trim()
                when {
                    trimmed.isEmpty() -> flush()
                    isHeading(trimmed) -> {
                        flush()
                        out += if (trimmed.matches(headingPatterns[2])) {
                            ContentBlock.Separator
                        } else {
                            ContentBlock.Heading(RichText(trimmed), 2)
                        }
                    }
                    else -> {
                        if (paragraph.isNotEmpty()) paragraph.append(' ')
                        paragraph.append(trimmed)
                    }
                }
            }
            flush()
            return out
        }

        fun isHeading(line: String): Boolean {
            if (line.length > 90) return false
            if (headingPatterns.any { line.matches(it) }) return true
            val letters = line.filter { it.isLetter() }
            return letters.length in 3..60 && letters.all { it.isUpperCase() } && !line.endsWith('.')
        }

        fun splitIntoChapters(blocks: List<ContentBlock>): List<List<ContentBlock>> {
            if (blocks.isEmpty()) return listOf(emptyList())
            val headingCount = blocks.count { it is ContentBlock.Heading }
            val parts = ArrayList<List<ContentBlock>>()
            var current = ArrayList<ContentBlock>()
            var chars = 0

            for (block in blocks) {
                val isHeading = block is ContentBlock.Heading
                val tooLong = chars >= TARGET_CHARS_PER_PART
                val shouldSplit = current.isNotEmpty() && ((isHeading && headingCount > 1) || (tooLong && isHeading) || chars >= TARGET_CHARS_PER_PART * 2)
                if (shouldSplit) {
                    parts += current
                    current = ArrayList()
                    chars = 0
                }
                current += block
                chars += when (block) {
                    is ContentBlock.Heading -> block.text.text.length
                    is ContentBlock.Paragraph -> block.text.text.length
                    else -> 0
                }
            }
            if (current.isNotEmpty()) parts += current
            return parts.ifEmpty { listOf(blocks) }
        }

        fun guessTitle(blocks: List<ContentBlock>, fileName: String): String {
            val fromName = fileName.substringBeforeLast('.').replace('_', ' ').trim()
            if (fromName.isNotBlank()) return fromName
            val first = blocks.firstOrNull()
            return when (first) {
                is ContentBlock.Heading -> first.text.text
                is ContentBlock.Paragraph -> first.text.text.take(60)
                else -> "Без названия"
            }
        }
    }
}

/** Одиночный (X)HTML-документ. Разбивается на главы по заголовкам первого уровня. */
class HtmlDocument(
    html: String,
    fileName: String,
    override val format: BookFormat = BookFormat.HTML,
) : TextDocument {

    private val parts: List<List<ContentBlock>>
    override val metadata: BookMetadata
    override val chapters: List<ChapterRef>

    init {
        val blocks = HtmlBlockParser.parse(html)
        val docTitle = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        val author = Regex("""<meta[^>]+name\s*=\s*["']author["'][^>]+content\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)

        metadata = BookMetadata(
            title = docTitle ?: fileName.substringBeforeLast('.').replace('_', ' '),
            authors = listOfNotNull(author),
        )

        val splitLevel = blocks.filterIsInstance<ContentBlock.Heading>().minOfOrNull { it.level } ?: 1
        val result = ArrayList<List<ContentBlock>>()
        var current = ArrayList<ContentBlock>()
        for (block in blocks) {
            if (block is ContentBlock.Heading && block.level <= splitLevel && current.isNotEmpty()) {
                result += current
                current = ArrayList()
            }
            current += block
        }
        if (current.isNotEmpty()) result += current
        parts = result.ifEmpty { listOf(blocks) }

        chapters = parts.mapIndexed { index, part ->
            ChapterRef(
                index = index,
                id = "part$index",
                title = (part.firstOrNull() as? ContentBlock.Heading)?.text?.text,
                approxChars = part.sumOf { block ->
                    when (block) {
                        is ContentBlock.Heading -> block.text.text.length
                        is ContentBlock.Paragraph -> block.text.text.length
                        else -> 0
                    }
                },
            )
        }
    }

    override fun loadChapter(index: Int): Chapter = Chapter(chapters[index], parts[index])

    override fun close() = Unit
}
