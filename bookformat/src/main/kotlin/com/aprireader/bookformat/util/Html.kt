package com.aprireader.bookformat.util

import com.aprireader.bookformat.model.ContentBlock
import com.aprireader.bookformat.model.ParagraphKind
import com.aprireader.bookformat.model.RichText
import com.aprireader.bookformat.model.SpanKind
import com.aprireader.bookformat.model.TextSpan

/**
 * Терпимый к «tag soup» парсер (X)HTML в типизированные блоки содержимого.
 *
 * Он намеренно не пытается быть полноценным движком вёрстки: задача — получить
 * поток абзацев со словами и инлайновой разметкой, пригодный для нативного
 * рендера, бионического шрифта и RSVP. Всё, что относится к вёрстке (CSS,
 * таблицы, флоаты), сознательно упрощается.
 */
object HtmlBlockParser {

    private val blockTags = setOf(
        "p", "div", "section", "article", "h1", "h2", "h3", "h4", "h5", "h6",
        "blockquote", "pre", "li", "ul", "ol", "tr", "td", "th", "table",
        "figure", "figcaption", "header", "footer", "aside", "main", "body", "dd", "dt", "dl",
    )
    private val skipTags = setOf("script", "style", "head", "title", "meta", "link", "svg")

    private val inlineKinds = mapOf(
        "b" to SpanKind.BOLD, "strong" to SpanKind.BOLD,
        "i" to SpanKind.ITALIC, "em" to SpanKind.ITALIC, "cite" to SpanKind.ITALIC,
        "u" to SpanKind.UNDERLINE, "ins" to SpanKind.UNDERLINE,
        "s" to SpanKind.STRIKE, "del" to SpanKind.STRIKE, "strike" to SpanKind.STRIKE,
        "code" to SpanKind.MONO, "kbd" to SpanKind.MONO, "samp" to SpanKind.MONO, "tt" to SpanKind.MONO,
        "sup" to SpanKind.SUPERSCRIPT, "sub" to SpanKind.SUBSCRIPT,
    )

    fun parse(html: String, resourceResolver: (String) -> String = { it }): List<ContentBlock> {
        val out = ArrayList<ContentBlock>()
        val builder = ParagraphBuilder()
        val openInline = ArrayList<OpenSpan>()
        var headingLevel = 0
        var kind = ParagraphKind.BODY
        var pendingAnchor: String? = null
        var listDepth = 0
        val orderedCounter = ArrayList<Int>()

        fun flush() {
            val rich = builder.build()
            if (!rich.isBlank) {
                out += if (headingLevel > 0) {
                    ContentBlock.Heading(rich, headingLevel, pendingAnchor)
                } else {
                    ContentBlock.Paragraph(rich, kind, pendingAnchor)
                }
            }
            builder.reset()
            openInline.clear()
            headingLevel = 0
            kind = ParagraphKind.BODY
            pendingAnchor = null
        }

        var i = 0
        val n = html.length
        while (i < n) {
            val c = html[i]
            if (c == '<') {
                val end = html.indexOf('>', i)
                if (end < 0) {
                    builder.appendText(html.substring(i))
                    break
                }
                if (html.startsWith("<!--", i)) {
                    val closeIdx = html.indexOf("-->", i + 4)
                    i = if (closeIdx < 0) n else closeIdx + 3
                    continue
                }

                val raw = html.substring(i + 1, end)
                i = end + 1

                if (raw.startsWith("!") || raw.startsWith("?")) continue

                val closing = raw.startsWith("/")
                val body = raw.removePrefix("/").trim()
                val name = body.takeWhile { !it.isWhitespace() && it != '/' }.lowercase()
                if (name.isEmpty()) continue

                if (name in skipTags) {
                    if (!closing) {
                        val closeIdx = indexOfCloseTag(html, name, i)
                        i = if (closeIdx < 0) n else closeIdx
                    }
                    continue
                }

                val attrs = if (closing) emptyMap() else parseAttributes(body)

                when {
                    name == "br" -> builder.appendText("\n")

                    name == "hr" -> {
                        flush()
                        out += ContentBlock.Separator
                    }

                    name == "img" || name == "image" -> {
                        val src = attrs["src"] ?: attrs["xlink:href"] ?: attrs["href"]
                        if (src != null) {
                            flush()
                            out += ContentBlock.Image(resourceResolver(src), attrs["alt"]?.takeIf { it.isNotBlank() })
                        }
                    }

                    name in inlineKinds -> {
                        val spanKind = inlineKinds.getValue(name)
                        if (closing) closeSpan(openInline, builder, spanKind)
                        else openInline += OpenSpan(spanKind, builder.length, null)
                    }

                    name == "a" -> {
                        if (closing) {
                            closeSpan(openInline, builder, SpanKind.LINK)
                        } else {
                            attrs["id"]?.let { if (builder.length == 0) pendingAnchor = it }
                            val href = attrs["href"]
                            if (href != null) openInline += OpenSpan(SpanKind.LINK, builder.length, href)
                        }
                    }

                    name in blockTags -> {
                        if (!closing) {
                            flush()
                            attrs["id"]?.let { pendingAnchor = it }
                            when (name) {
                                "h1", "h2", "h3", "h4", "h5", "h6" -> headingLevel = name[1].digitToInt()
                                "blockquote" -> kind = ParagraphKind.QUOTE
                                "pre" -> kind = ParagraphKind.CODE
                                "figcaption" -> kind = ParagraphKind.CAPTION
                                "ul" -> {
                                    listDepth++
                                    orderedCounter.add(-1)
                                }
                                "ol" -> {
                                    listDepth++
                                    orderedCounter.add(1)
                                }
                                "li" -> {
                                    val counter = orderedCounter.lastOrNull() ?: -1
                                    val marker = if (counter >= 0) {
                                        orderedCounter[orderedCounter.lastIndex] = counter + 1
                                        "$counter. "
                                    } else {
                                        "• "
                                    }
                                    builder.appendText("   ".repeat((listDepth - 1).coerceAtLeast(0)) + marker)
                                }
                            }
                            val cls = attrs["class"].orEmpty().lowercase()
                            if ("epigraph" in cls) kind = ParagraphKind.EPIGRAPH
                            if ("poem" in cls || "verse" in cls || "stanza" in cls) kind = ParagraphKind.POEM
                            if ("note" in cls || "footnote" in cls) kind = ParagraphKind.NOTE
                        } else {
                            flush()
                            if (name == "ul" || name == "ol") {
                                listDepth = (listDepth - 1).coerceAtLeast(0)
                                if (orderedCounter.isNotEmpty()) orderedCounter.removeAt(orderedCounter.lastIndex)
                            }
                        }
                    }
                }
            } else {
                val next = html.indexOf('<', i)
                val chunk = if (next < 0) html.substring(i) else html.substring(i, next)
                builder.appendText(decodeEntities(chunk))
                i = if (next < 0) n else next
            }
        }
        flush()
        return out
    }

    private fun closeSpan(open: MutableList<OpenSpan>, builder: ParagraphBuilder, kind: SpanKind) {
        val idx = open.indexOfLast { it.kind == kind }
        if (idx >= 0) {
            val span = open.removeAt(idx)
            if (builder.length > span.start) {
                builder.addSpan(TextSpan(span.start, builder.length, kind, span.target))
            }
        }
    }

    private fun indexOfCloseTag(html: String, tag: String, from: Int): Int {
        var idx = from
        while (idx < html.length) {
            val lt = html.indexOf("</", idx)
            if (lt < 0) return -1
            val gt = html.indexOf('>', lt)
            if (gt < 0) return -1
            val name = html.substring(lt + 2, gt).trim().lowercase()
            if (name == tag) return gt + 1
            idx = gt + 1
        }
        return -1
    }

    internal fun parseAttributes(body: String): Map<String, String> {
        val attrs = HashMap<String, String>()
        var i = body.indexOfFirst { it.isWhitespace() }
        if (i < 0) return attrs
        while (i < body.length) {
            while (i < body.length && (body[i].isWhitespace() || body[i] == '/')) i++
            if (i >= body.length) break
            val nameStart = i
            while (i < body.length && body[i] != '=' && !body[i].isWhitespace() && body[i] != '/') i++
            val name = body.substring(nameStart, i).lowercase()
            while (i < body.length && body[i].isWhitespace()) i++
            var value = ""
            if (i < body.length && body[i] == '=') {
                i++
                while (i < body.length && body[i].isWhitespace()) i++
                if (i < body.length && (body[i] == '"' || body[i] == '\'')) {
                    val quote = body[i]
                    i++
                    val start = i
                    while (i < body.length && body[i] != quote) i++
                    value = body.substring(start, i)
                    if (i < body.length) i++
                } else {
                    val start = i
                    while (i < body.length && !body[i].isWhitespace() && body[i] != '/') i++
                    value = body.substring(start, i)
                }
            }
            if (name.isNotEmpty()) attrs[name] = decodeEntities(value)
        }
        return attrs
    }
}

private class OpenSpan(val kind: SpanKind, val start: Int, val target: String?)

/** Собирает абзац, схлопывая избыточные пробелы, и переносит спаны с учётом схлопывания. */
internal class ParagraphBuilder {
    private val sb = StringBuilder()
    private val spans = ArrayList<TextSpan>()

    val length: Int get() = sb.length

    fun appendText(text: String) {
        for (ch in text) {
            when {
                ch == '\n' -> if (sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
                ch.isWhitespace() -> if (sb.isNotEmpty() && sb.last() != ' ' && sb.last() != '\n') sb.append(' ')
                else -> sb.append(ch)
            }
        }
    }

    fun addSpan(span: TextSpan) {
        spans += span
    }

    fun build(): RichText {
        val whole = sb.toString()
        val text = whole.trim()
        if (text.isEmpty()) return RichText("")
        val leading = whole.length - whole.trimStart().length
        val shifted = spans.mapNotNull {
            val s = (it.start - leading).coerceIn(0, text.length)
            val e = (it.end - leading).coerceIn(0, text.length)
            if (e > s) it.copy(start = s, end = e) else null
        }
        return RichText(text, shifted)
    }

    fun reset() {
        sb.setLength(0)
        spans.clear()
    }
}

private val namedEntities = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
    "nbsp" to " ", "mdash" to "—", "ndash" to "–", "hellip" to "…",
    "laquo" to "«", "raquo" to "»", "ldquo" to "“", "rdquo" to "”",
    "lsquo" to "‘", "rsquo" to "’", "bdquo" to "„", "sbquo" to "‚",
    "copy" to "©", "reg" to "®", "trade" to "™", "deg" to "°",
    "plusmn" to "±", "times" to "×", "divide" to "÷", "middot" to "·",
    "bull" to "•", "dagger" to "†", "sect" to "§", "para" to "¶",
    "euro" to "€", "pound" to "£", "yen" to "¥", "cent" to "¢",
    "shy" to "­", "ensp" to " ", "emsp" to " ", "thinsp" to " ",
    "prime" to "′", "Prime" to "″",
)

/** Декодирует HTML/XML-сущности, включая числовые. */
fun decodeEntities(input: String): String {
    if ('&' !in input) return input
    val sb = StringBuilder(input.length)
    var i = 0
    while (i < input.length) {
        val c = input[i]
        if (c != '&') {
            sb.append(c)
            i++
            continue
        }
        val semi = input.indexOf(';', i + 1)
        if (semi < 0 || semi - i > 12) {
            sb.append(c)
            i++
            continue
        }
        val body = input.substring(i + 1, semi)
        val decoded = when {
            body.startsWith("#x") || body.startsWith("#X") ->
                body.drop(2).toIntOrNull(16)?.let { codePointToString(it) }
            body.startsWith("#") -> body.drop(1).toIntOrNull()?.let { codePointToString(it) }
            else -> namedEntities[body]
        }
        if (decoded != null) {
            sb.append(decoded)
            i = semi + 1
        } else {
            sb.append(c)
            i++
        }
    }
    return sb.toString()
}

private fun codePointToString(cp: Int): String? =
    if (cp in 1..0x10FFFF) String(Character.toChars(cp)) else null
