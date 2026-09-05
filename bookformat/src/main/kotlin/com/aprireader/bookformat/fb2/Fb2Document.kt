package com.aprireader.bookformat.fb2

import com.aprireader.bookformat.model.BookFormat
import com.aprireader.bookformat.model.BookMetadata
import com.aprireader.bookformat.model.Chapter
import com.aprireader.bookformat.model.ChapterRef
import com.aprireader.bookformat.model.ContentBlock
import com.aprireader.bookformat.model.ParagraphKind
import com.aprireader.bookformat.model.SpanKind
import com.aprireader.bookformat.model.TextDocument
import com.aprireader.bookformat.model.TextSpan
import com.aprireader.bookformat.util.ParagraphBuilder
import com.aprireader.bookformat.util.XmlDom
import com.aprireader.bookformat.util.attr
import com.aprireader.bookformat.util.childElements
import com.aprireader.bookformat.util.decodeEntities
import com.aprireader.bookformat.util.descendants
import com.aprireader.bookformat.util.localNameOrTag
import com.aprireader.bookformat.util.text

/**
 * FictionBook 2. Документ читается в память целиком (FB2 — это один XML-файл,
 * типично 0.3–3 МБ), но разбирается лениво: сначала строится карта секций,
 * содержимое главы парсится в момент открытия.
 */
class Fb2Document(private val xml: String, override val format: BookFormat = BookFormat.FB2) : TextDocument {

    private val binaries: Map<String, IntRange> = indexBinaries(xml)
    private val sections: List<SectionSpan> = indexSections(xml)

    override val metadata: BookMetadata = parseDescription(xml, binaries)
    override val chapters: List<ChapterRef> = sections.mapIndexed { index, span ->
        ChapterRef(
            index = index,
            id = "sec$index",
            title = span.title,
            depth = span.depth,
            approxChars = span.range.last - span.range.first,
        )
    }

    override fun loadChapter(index: Int): Chapter {
        val ref = chapters[index]
        val span = sections[index]
        val blocks = Fb2BlockParser.parse(xml, span.range)
        return Chapter(ref, blocks)
    }

    override fun loadResource(href: String): ByteArray? {
        val id = href.removePrefix("#")
        val range = binaries[id] ?: return null
        val base64 = xml.substring(range.first, range.last).filterNot { it.isWhitespace() }
        return runCatching { java.util.Base64.getMimeDecoder().decode(base64) }.getOrNull()
    }

    override fun close() = Unit

    private data class SectionSpan(val title: String?, val depth: Int, val range: IntRange)

    private companion object {

        fun indexBinaries(xml: String): Map<String, IntRange> {
            val out = LinkedHashMap<String, IntRange>()
            val regex = Regex("""<binary\b([^>]*)>""", RegexOption.IGNORE_CASE)
            for (match in regex.findAll(xml)) {
                val id = Regex("""id\s*=\s*["']([^"']+)["']""").find(match.groupValues[1])?.groupValues?.get(1)
                    ?: continue
                val start = match.range.last + 1
                val end = xml.indexOf("</binary", start, ignoreCase = true)
                if (end > start) out[id] = start until end
            }
            return out
        }

        /** Карта секций: содержимое каждой секции — до первой вложенной секции. */
        fun indexSections(xml: String): List<SectionSpan> {
            val bodyStart = findBodyStart(xml)
            if (bodyStart < 0) return listOf(SectionSpan(null, 0, 0 until xml.length))
            val bodyEnd = xml.indexOf("</body", bodyStart, ignoreCase = true).let { if (it < 0) xml.length else it }

            val out = ArrayList<SectionSpan>()
            var depth = 0
            var i = bodyStart
            var pendingStart = -1
            var pendingDepth = 0
            val stack = ArrayList<Int>()

            fun closePending(end: Int) {
                if (pendingStart in 0 until end) {
                    out += SectionSpan(extractTitle(xml, pendingStart, end), pendingDepth, pendingStart until end)
                }
                pendingStart = -1
            }

            while (i < bodyEnd) {
                val lt = xml.indexOf('<', i)
                if (lt < 0 || lt >= bodyEnd) break
                val gt = xml.indexOf('>', lt)
                if (gt < 0) break
                val tag = xml.substring(lt + 1, gt)
                val name = tag.removePrefix("/").takeWhile { !it.isWhitespace() && it != '/' }.lowercase()
                if (name == "section") {
                    if (tag.startsWith("/")) {
                        closePending(lt)
                        depth = (depth - 1).coerceAtLeast(0)
                        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                    } else if (!tag.endsWith("/")) {
                        closePending(lt)
                        depth++
                        stack += depth
                        pendingStart = gt + 1
                        pendingDepth = depth - 1
                    }
                }
                i = gt + 1
            }
            closePending(bodyEnd)

            val meaningful = out.filter { it.range.last - it.range.first > 0 }
            return meaningful.ifEmpty { listOf(SectionSpan(null, 0, bodyStart until bodyEnd)) }
        }

        fun findBodyStart(xml: String): Int {
            var index = 0
            while (index < xml.length) {
                val at = xml.indexOf("<body", index, ignoreCase = true)
                if (at < 0) return -1
                val gt = xml.indexOf('>', at)
                if (gt < 0) return -1
                val tag = xml.substring(at, gt)
                // Сноски (name="notes") — не часть основного текста.
                if (!tag.contains("notes", ignoreCase = true) && !tag.contains("comments", ignoreCase = true)) {
                    return gt + 1
                }
                index = gt + 1
            }
            return -1
        }

        fun extractTitle(xml: String, start: Int, end: Int): String? {
            val titleStart = xml.indexOf("<title", start, ignoreCase = true)
            if (titleStart < 0 || titleStart > end) return null
            val open = xml.indexOf('>', titleStart)
            val close = xml.indexOf("</title", open, ignoreCase = true)
            if (open < 0 || close < 0 || close > end) return null
            return decodeEntities(xml.substring(open + 1, close).replace(Regex("<[^>]+>"), " "))
                .replace(Regex("\\s+"), " ").trim().takeIf { it.isNotBlank() }
        }

        fun parseDescription(xml: String, binaries: Map<String, IntRange>): BookMetadata {
            val start = xml.indexOf("<description", ignoreCase = true)
            val closeMatch = if (start >= 0) Regex("""</(?:[\w\-]+:)?description\s*>""", RegexOption.IGNORE_CASE).find(xml, start) else null
            val end = closeMatch?.range?.last?.plus(1) ?: xml.indexOf("</description>", ignoreCase = true).let { if (it >= 0) it + 14 else -1 }

            val root = if (start >= 0 && end > start) {
                XmlDom.parseString(xml.substring(start, end))
            } else {
                null
            }

            if (root == null) {
                return regexParseFb2Metadata(xml, binaries)
            }

            val titleInfo = root.childElements().firstOrNull { it.localNameOrTag().equals("title-info", true) }
                ?: root.descendants("title-info").firstOrNull()
            val publishInfo = root.childElements().firstOrNull { it.localNameOrTag().equals("publish-info", true) }
                ?: root.descendants("publish-info").firstOrNull()

            val authors = titleInfo?.childElements()
                ?.filter { it.localNameOrTag().equals("author", true) }
                ?.map { author ->
                    val parts = listOf("first-name", "middle-name", "last-name")
                        .mapNotNull { part ->
                            author.childElements().firstOrNull { it.localNameOrTag().equals(part, true) }?.text()?.let { decodeEntities(it) }
                        }
                        .filter { it.isNotBlank() }

                    if (parts.isNotEmpty()) {
                        parts.joinToString(" ")
                    } else {
                        val nick = author.childElements().firstOrNull { it.localNameOrTag().equals("nickname", true) }?.text()?.let { decodeEntities(it) }
                        nick?.takeIf { it.isNotBlank() } ?: decodeEntities(author.text())
                    }
                }
                ?.filter { it.isNotBlank() }
                .orEmpty()

            val sequence = titleInfo?.descendants("sequence")?.firstOrNull()
            val coverHref = titleInfo?.descendants("image")?.firstNotNullOfOrNull { it.attr("href") }
            val cover = findBinaryCover(coverHref, binaries, xml)

            val rawTitle = titleInfo?.childElements()
                ?.firstOrNull { it.localNameOrTag().equals("book-title", true) }?.text()
                ?.takeIf { it.isNotBlank() }
                ?: titleInfo?.descendants("book-title")?.firstOrNull()?.text()?.takeIf { it.isNotBlank() }

            val title = rawTitle?.let { decodeEntities(it) } ?: "Без названия"
            if (title == "Без названия" && authors.isEmpty()) {
                val fromRegex = regexParseFb2Metadata(xml, binaries)
                if (fromRegex.title != "Без названия" || fromRegex.authors.isNotEmpty()) {
                    return fromRegex.copy(cover = cover ?: fromRegex.cover)
                }
            }

            return BookMetadata(
                title = title,
                authors = authors,
                description = titleInfo?.descendants("annotation")?.firstOrNull()?.text()
                    ?.let { decodeEntities(it) }?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotBlank() },
                language = titleInfo?.childElements()?.firstOrNull { it.localNameOrTag().equals("lang", true) }?.text()?.trim(),
                publisher = publishInfo?.childElements()
                    ?.firstOrNull { it.localNameOrTag().equals("publisher", true) }?.text()?.let { decodeEntities(it) },
                year = publishInfo?.childElements()
                    ?.firstOrNull { it.localNameOrTag().equals("year", true) }?.text()?.trim()?.toIntOrNull(),
                series = sequence?.attr("name")?.let { decodeEntities(it) },
                seriesIndex = sequence?.attr("number")?.trim()?.toIntOrNull(),
                cover = cover,
            )
        }

        private fun findBinaryCover(coverHref: String?, binaries: Map<String, IntRange>, xml: String): ByteArray? {
            if (coverHref != null) {
                val id = coverHref.removePrefix("#").trim()
                val range = binaries[id]
                    ?: binaries.entries.firstOrNull { it.key.equals(id, ignoreCase = true) }?.value
                    ?: binaries.entries.firstOrNull { it.key.removePrefix("#").equals(id, ignoreCase = true) }?.value
                if (range != null) {
                    decodeBinaryRange(xml, range)?.let { return it }
                }
            }
            // Если явная ссылка не сработала, ищем binary с id="cover" или первое изображение
            val fallbackId = binaries.keys.firstOrNull { it.contains("cover", ignoreCase = true) }
                ?: binaries.keys.firstOrNull { it.endsWith(".jpg", true) || it.endsWith(".jpeg", true) || it.endsWith(".png", true) }
            return fallbackId?.let { binaries[it] }?.let { decodeBinaryRange(xml, it) }
        }

        private fun decodeBinaryRange(xml: String, range: IntRange): ByteArray? = runCatching {
            val base64 = xml.substring(range.first, range.last).filterNot { it.isWhitespace() }
            java.util.Base64.getMimeDecoder().decode(base64)
        }.getOrNull()

        private fun regexParseFb2Metadata(xml: String, binaries: Map<String, IntRange>): BookMetadata {
            fun tag(name: String): String? =
                Regex("""<$name\b[^>]*>([\s\S]*?)</$name>""", RegexOption.IGNORE_CASE)
                    .find(xml)?.groupValues?.get(1)?.let { decodeEntities(it.replace(Regex("<[^>]+>"), " ").trim()) }
                    ?.takeIf { it.isNotBlank() }

            val title = tag("book-title") ?: "Без названия"

            val authors = ArrayList<String>()
            val authorRegex = Regex("""<author\b[^>]*>([\s\S]*?)</author>""", RegexOption.IGNORE_CASE)
            for (match in authorRegex.findAll(xml)) {
                val content = match.groupValues[1]
                fun part(p: String): String? =
                    Regex("""<$p\b[^>]*>([\s\S]*?)</$p>""", RegexOption.IGNORE_CASE)
                        .find(content)?.groupValues?.get(1)?.let { decodeEntities(it.trim()) }
                        ?.takeIf { it.isNotBlank() }

                val fn = part("first-name")
                val mn = part("middle-name")
                val ln = part("last-name")
                val parts = listOfNotNull(fn, mn, ln)
                if (parts.isNotEmpty()) {
                    authors += parts.joinToString(" ")
                } else {
                    val nick = part("nickname")
                    if (nick != null) authors += nick
                }
            }

            val coverHref = Regex("""<coverpage\b[^>]*>[\s\S]*?<image\b[^>]+(?:href|xlink:href|l:href)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .find(xml)?.groupValues?.get(1)
            val cover = findBinaryCover(coverHref, binaries, xml)

            val seqMatch = Regex("""<sequence\b[^>]*name\s*=\s*["']([^"']+)["'](?:[^>]*number\s*=\s*["'](\d+)["'])?""", RegexOption.IGNORE_CASE)
                .find(xml)
            val series = seqMatch?.groupValues?.get(1)?.let { decodeEntities(it) }
            val seriesIndex = seqMatch?.groupValues?.getOrNull(2)?.toIntOrNull()

            return BookMetadata(
                title = title,
                authors = authors,
                description = tag("annotation"),
                language = tag("lang"),
                publisher = tag("publisher"),
                year = tag("year")?.toIntOrNull(),
                series = series,
                seriesIndex = seriesIndex,
                cover = cover,
            )
        }
    }
}

/** Разбор тела FB2-секции в типизированные блоки. */
internal object Fb2BlockParser {

    private val inlineKinds = mapOf(
        "strong" to SpanKind.BOLD,
        "emphasis" to SpanKind.ITALIC,
        "style" to SpanKind.ITALIC,
        "code" to SpanKind.MONO,
        "strikethrough" to SpanKind.STRIKE,
        "sup" to SpanKind.SUPERSCRIPT,
        "sub" to SpanKind.SUBSCRIPT,
    )

    private val paragraphTags = setOf("p", "v", "subtitle", "text-author", "th", "td")

    fun parse(xml: String, range: IntRange): List<ContentBlock> {
        val out = ArrayList<ContentBlock>()
        val builder = ParagraphBuilder()
        val open = ArrayList<Triple<SpanKind, Int, String?>>()
        var kind = ParagraphKind.BODY
        var headingLevel = 0
        var inTitle = false
        var titleEmitted = false
        var kindStack = ArrayList<ParagraphKind>()

        fun flush() {
            val rich = builder.build()
            if (!rich.isBlank) {
                out += if (headingLevel > 0) ContentBlock.Heading(rich, headingLevel) else ContentBlock.Paragraph(rich, kind)
            }
            builder.reset()
            open.clear()
            headingLevel = 0
            kind = kindStack.lastOrNull() ?: ParagraphKind.BODY
        }

        var i = range.first
        val end = minOf(range.last, xml.length)
        while (i < end) {
            val lt = xml.indexOf('<', i)
            if (lt < 0 || lt >= end) {
                builder.appendText(decodeEntities(xml.substring(i, end)))
                break
            }
            if (lt > i) builder.appendText(decodeEntities(xml.substring(i, lt)))
            val gt = xml.indexOf('>', lt)
            if (gt < 0) break
            val raw = xml.substring(lt + 1, gt)
            i = gt + 1

            if (raw.startsWith("!") || raw.startsWith("?")) continue
            val closing = raw.startsWith("/")
            val selfClosing = raw.endsWith("/")
            val body = raw.removePrefix("/").removeSuffix("/").trim()
            val name = body.takeWhile { !it.isWhitespace() }.lowercase()
            if (name.isEmpty()) continue

            // Вложенная секция — она станет отдельной главой.
            if (name == "section" && !closing) break

            when {
                name == "image" -> {
                    val href = Regex("""href\s*=\s*["']([^"']+)["']""").find(body)?.groupValues?.get(1)
                    if (href != null) {
                        flush()
                        out += ContentBlock.Image(href)
                    }
                }

                name == "empty-line" -> {
                    flush()
                    out += ContentBlock.Separator
                }

                name in inlineKinds -> {
                    val spanKind = inlineKinds.getValue(name)
                    if (closing) {
                        val idx = open.indexOfLast { it.first == spanKind }
                        if (idx >= 0) {
                            val (k, start, target) = open.removeAt(idx)
                            if (builder.length > start) builder.addSpan(TextSpan(start, builder.length, k, target))
                        }
                    } else if (!selfClosing) {
                        open += Triple(spanKind, builder.length, null)
                    }
                }

                name == "a" -> {
                    if (closing) {
                        val idx = open.indexOfLast { it.first == SpanKind.LINK }
                        if (idx >= 0) {
                            val (k, start, target) = open.removeAt(idx)
                            if (builder.length > start) builder.addSpan(TextSpan(start, builder.length, k, target))
                        }
                    } else {
                        val href = Regex("""href\s*=\s*["']([^"']+)["']""").find(body)?.groupValues?.get(1)
                        open += Triple(SpanKind.LINK, builder.length, href)
                    }
                }

                name == "title" -> {
                    flush()
                    inTitle = !closing
                    if (closing) titleEmitted = true
                }

                name in paragraphTags -> {
                    if (!closing) {
                        flush()
                        when {
                            inTitle -> headingLevel = if (titleEmitted) 3 else 2
                            name == "subtitle" -> headingLevel = 3
                            name == "v" -> kind = ParagraphKind.POEM
                            name == "text-author" -> kind = ParagraphKind.CAPTION
                            else -> kind = kindStack.lastOrNull() ?: ParagraphKind.BODY
                        }
                    } else {
                        flush()
                    }
                }

                name == "cite" || name == "epigraph" || name == "poem" || name == "stanza" -> {
                    flush()
                    if (!closing) {
                        kindStack.add(
                            when (name) {
                                "cite" -> ParagraphKind.QUOTE
                                "epigraph" -> ParagraphKind.EPIGRAPH
                                else -> ParagraphKind.POEM
                            }
                        )
                    } else if (kindStack.isNotEmpty()) {
                        kindStack.removeAt(kindStack.lastIndex)
                    }
                    kind = kindStack.lastOrNull() ?: ParagraphKind.BODY
                }
            }
        }
        flush()
        return out
    }
}
