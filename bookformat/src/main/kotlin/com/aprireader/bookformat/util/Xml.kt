package com.aprireader.bookformat.util

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.Charset
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Разбор небольших служебных XML (container.xml, OPF, NCX, ComicInfo.xml) через DOM.
 *
 * DOM выбран сознательно: эти файлы малы, а код разбора получается втрое короче
 * и надёжнее, чем на pull-парсере. Крупные документы (FB2, XHTML-главы) DOM не
 * трогает — они идут через потоковый разбор.
 */
object XmlDom {

    private val factory: DocumentBuilderFactory by lazy {
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isValidating = false
            // Защита от XXE: книга — это недоверенный ввод.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            isExpandEntityReferences = false
        }
    }

    fun parse(bytes: ByteArray): Element? {
        val direct = parse(ByteArrayInputStream(bytes))
        if (direct != null) return direct

        // Если прямой разбор не удался (например, из-за HTML-сущностей или несовпадения declared encoding):
        val charset = detectCharset(bytes)
        val text = String(bytes, charset)
        return parseString(text)
    }

    fun parseString(xml: String): Element? {
        val sanitized = sanitizeXmlForDom(xml)
        val bytes = sanitized.toByteArray(Charsets.UTF_8)
        return parse(ByteArrayInputStream(bytes))
    }

    fun parse(stream: InputStream): Element? = runCatching {
        stream.use { factory.newDocumentBuilder().parse(it).documentElement }
    }.getOrNull()
}

/** Заменяет недекларированные HTML-сущности на прямые символы для XML 1.0 DOM парсера. */
fun sanitizeXmlForDom(xml: String): String {
    var result = xml.replace(Regex("""<\?xml[^>]*\?>""", RegexOption.IGNORE_CASE), "")
    if ('&' !in result) return result

    return Regex("""&([a-zA-Z0-9]+);""").replace(result) { match ->
        val name = match.groupValues[1]
        when (name.lowercase()) {
            "amp", "lt", "gt", "quot", "apos" -> match.value
            "nbsp" -> " "
            "mdash" -> "—"
            "ndash" -> "–"
            "laquo" -> "«"
            "raquo" -> "»"
            "hellip" -> "…"
            "ldquo" -> "“"
            "rdquo" -> "”"
            "lsquo" -> "‘"
            "rsquo" -> "’"
            "bdquo" -> "„"
            "sbquo" -> "‚"
            "copy" -> "©"
            "reg" -> "®"
            "trade" -> "™"
            "deg" -> "°"
            "plusmn" -> "±"
            "times" -> "×"
            "divide" -> "÷"
            "euro" -> "€"
            "pound" -> "£"
            "yen" -> "¥"
            "cent" -> "¢"
            "sect" -> "§"
            "bull" -> "•"
            else -> {
                val decoded = decodeEntities(match.value)
                if (decoded != match.value && !decoded.contains("&")) {
                    decoded
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                } else {
                    " "
                }
            }
        }
    }
}

fun Element.childElements(): List<Element> {
    val out = ArrayList<Element>()
    val nodes = childNodes
    for (i in 0 until nodes.length) {
        val node = nodes.item(i)
        if (node.nodeType == Node.ELEMENT_NODE) out += node as Element
    }
    return out
}

/** Ищет потомков по локальному имени, игнорируя префикс пространства имён. */
fun Element.descendants(localName: String): List<Element> {
    val out = ArrayList<Element>()
    fun walk(element: Element) {
        for (child in element.childElements()) {
            if (child.localNameOrTag().equals(localName, ignoreCase = true)) out += child
            walk(child)
        }
    }
    walk(this)
    return out
}

fun Element.firstDescendant(localName: String): Element? = descendants(localName).firstOrNull()

fun Element.localNameOrTag(): String = tagName.substringAfter(':')

fun Element.attr(name: String): String? {
    getAttribute(name).takeIf { it.isNotEmpty() }?.let { return it }
    val attributes = attributes
    for (i in 0 until attributes.length) {
        val item = attributes.item(i)
        if (item.nodeName.substringAfter(':').equals(name, ignoreCase = true)) {
            return item.nodeValue?.takeIf { it.isNotEmpty() }
        }
    }
    return null
}

fun Element.text(): String = textContent?.trim().orEmpty()

/**
 * Определяет кодировку текстового документа: BOM, затем XML-декларация или
 * HTML meta charset, иначе UTF-8. Кириллические книги часто приходят в
 * windows-1251, поэтому объявленную кодировку игнорировать нельзя.
 */
fun detectCharset(bytes: ByteArray, fallback: Charset = Charsets.UTF_8): Charset {
    if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
        return Charsets.UTF_8
    }
    if (bytes.size >= 2) {
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return Charsets.UTF_16LE
        if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return Charsets.UTF_16BE
    }
    val head = String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1)
    val declared = Regex("""(?:encoding|charset)\s*=\s*["']?([\w\-]+)""", RegexOption.IGNORE_CASE)
        .find(head)?.groupValues?.get(1)
    if (declared != null) {
        runCatching { return Charset.forName(declared) }
    }
    return fallback
}

private const val BOM = '\uFEFF'

/** Читает поток целиком и декодирует его с учётом объявленной кодировки. */
fun InputStream.readTextDetectingCharset(): String {
    val bytes = use { it.readBytes() }
    val charset = detectCharset(bytes)
    val text = String(bytes, charset)
    return if (text.isNotEmpty() && text[0] == BOM) text.substring(1) else text
}
