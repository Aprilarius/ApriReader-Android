package com.aprireader.bookformat.epub

import com.aprireader.bookformat.model.BookFormat
import com.aprireader.bookformat.model.BookMetadata
import com.aprireader.bookformat.model.BookParseException
import com.aprireader.bookformat.model.Chapter
import com.aprireader.bookformat.model.ChapterRef
import com.aprireader.bookformat.model.TextDocument
import com.aprireader.bookformat.util.HtmlBlockParser
import com.aprireader.bookformat.util.RandomAccessSource
import com.aprireader.bookformat.util.ZipArchive
import com.aprireader.bookformat.util.XmlDom
import com.aprireader.bookformat.util.attr
import com.aprireader.bookformat.util.childElements
import com.aprireader.bookformat.util.decodeEntities
import com.aprireader.bookformat.util.descendants
import com.aprireader.bookformat.util.detectCharset
import com.aprireader.bookformat.util.localNameOrTag
import com.aprireader.bookformat.util.text
import org.w3c.dom.Element

/**
 * EPUB 2 и EPUB 3. Читает OPF-пакет, разворачивает spine в список глав и
 * отдаёт содержимое главы лениво — открытие книги не должно зависеть от её размера.
 */
class EpubDocument(source: RandomAccessSource) : TextDocument {

    private val zip = ZipArchive(source)
    private val opfPath: String
    private val opfDir: String
    private val manifest: Map<String, ManifestItem>
    private val spine: List<ManifestItem>

    override val format = BookFormat.EPUB
    override val metadata: BookMetadata
    override val chapters: List<ChapterRef>

    private data class ManifestItem(val id: String, val href: String, val mediaType: String, val properties: String)

    init {
        opfPath = findOpfPath() ?: throw BookParseException("EPUB: не найден OPF-пакет")
        opfDir = opfPath.substringBeforeLast('/', "")
        val opf = zip.readAll(opfPath)?.let { XmlDom.parse(it) }
            ?: throw BookParseException("EPUB: не удалось разобрать $opfPath")

        manifest = opf.firstOrNull("manifest")?.childElements()
            ?.filter { it.localNameOrTag().equals("item", true) }
            ?.mapNotNull { element ->
                val id = element.attr("id") ?: return@mapNotNull null
                val href = element.attr("href") ?: return@mapNotNull null
                id to ManifestItem(
                    id = id,
                    href = resolve(opfDir, href),
                    mediaType = element.attr("media-type").orEmpty(),
                    properties = element.attr("properties").orEmpty(),
                )
            }?.toMap().orEmpty()

        spine = opf.firstOrNull("spine")?.childElements()
            ?.filter { it.localNameOrTag().equals("itemref", true) }
            ?.filter { it.attr("linear")?.equals("no", true) != true }
            ?.mapNotNull { manifest[it.attr("idref")] }
            ?.filter { it.href in zip }
            .orEmpty()
            .ifEmpty {
                // Битый или отсутствующий spine — не повод отказываться от книги.
                manifest.values.filter { it.mediaType.contains("html") && it.href in zip }
            }

        metadata = parseMetadata(opf)
        chapters = buildChapters(opf)
    }

    override fun loadChapter(index: Int): Chapter {
        val ref = chapters.getOrNull(index) ?: throw BookParseException("EPUB: нет главы $index")
        val item = spine.getOrNull(index) ?: throw BookParseException("EPUB: нет spine-элемента $index")
        val bytes = zip.readAll(item.href) ?: return Chapter(ref, emptyList())
        val html = String(bytes, detectCharset(bytes))
        val baseDir = item.href.substringBeforeLast('/', "")
        val blocks = HtmlBlockParser.parse(html) { src -> resolve(baseDir, src.substringBefore('#')) }
        return Chapter(ref, blocks)
    }

    override fun loadResource(href: String): ByteArray? = zip.readAll(href) ?: zip.findIgnoreCase(href)?.let { zip.readAll(it) }

    override fun close() = zip.close()

    // --- разбор ---

    private fun findOpfPath(): String? {
        val containerBytes = zip.readAll("META-INF/container.xml")
            ?: zip.findIgnoreCase("META-INF/container.xml")?.let { zip.readAll(it) }
        val container = containerBytes?.let { XmlDom.parse(it) }
        val fromContainer = container?.descendants("rootfile")
            ?.firstNotNullOfOrNull { it.attr("full-path") }
        if (fromContainer != null) {
            val resolved = decodeUriPath(fromContainer)
            if (resolved in zip) return resolved
            if (fromContainer in zip) return fromContainer
            zip.findIgnoreCase(resolved)?.name?.let { return it }
            zip.findIgnoreCase(fromContainer)?.name?.let { return it }
        }
        // Некоторые генераторы кладут container.xml с неверным путём.
        return zip.names.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
    }

    private fun parseMetadata(opf: Element): BookMetadata {
        val meta = opf.firstOrNull("metadata")
        fun dc(name: String): List<String> =
            meta?.childElements()?.filter { it.localNameOrTag().equals(name, true) }?.map { decodeEntities(it.text()) }
                ?.filter { it.isNotBlank() }.orEmpty()

        val metaTags = meta?.childElements()?.filter { it.localNameOrTag().equals("meta", true) }.orEmpty()
        fun metaContent(name: String): String? = metaTags.firstOrNull {
            it.attr("name").equals(name, true) || it.attr("property").equals(name, true)
        }?.let { it.attr("content") ?: it.text() }?.let { decodeEntities(it) }?.takeIf { it.isNotBlank() }

        val authors = dc("creator").ifEmpty {
            metaTags.filter { it.attr("property")?.contains("creator", true) == true }
                .map { decodeEntities(it.text().ifBlank { it.attr("content").orEmpty() }) }
                .filter { it.isNotBlank() }
        }

        val rawTitle = dc("title").firstOrNull()
            ?: metaTags.firstOrNull { it.attr("property")?.contains("title", true) == true }
                ?.let { decodeEntities(it.text().ifBlank { it.attr("content").orEmpty() }) }

        val identifiers = meta?.childElements()
            ?.filter { it.localNameOrTag().equals("identifier", true) }
            ?.associate { (it.attr("scheme") ?: it.attr("id") ?: "id") to it.text() }
            .orEmpty()

        val series = metaContent("calibre:series")
            ?: metaContent("belongs-to-collection")
            ?: metaContent("collection-title")

        val seriesIndex = metaContent("calibre:series_index")?.substringBefore('.')?.toIntOrNull()
            ?: metaTags.firstOrNull { it.attr("property")?.contains("group-position", true) == true }
                ?.let { it.attr("content") ?: it.text() }?.trim()?.toIntOrNull()

        return BookMetadata(
            title = rawTitle?.takeIf { it.isNotBlank() } ?: "Без названия",
            authors = authors,
            description = dc("description").firstOrNull()?.let { stripTags(decodeEntities(it)) },
            language = dc("language").firstOrNull(),
            publisher = dc("publisher").firstOrNull(),
            year = dc("date").firstNotNullOfOrNull { yearOf(it) },
            series = series,
            seriesIndex = seriesIndex,
            identifiers = identifiers,
            cover = findCover(opf, metaTags),
        )
    }

    private fun findCover(opf: Element, metaTags: List<Element>): ByteArray? {
        val byProperties = manifest.values.firstOrNull { "cover-image" in it.properties.lowercase() }
        val byMeta = metaTags.firstOrNull { it.attr("name").equals("cover", true) }
            ?.attr("content")?.let { manifest[it] }
        val byGuide = opf.firstOrNull("guide")?.childElements()
            ?.firstOrNull { it.attr("type")?.contains("cover", true) == true }
            ?.attr("href")?.let { href -> resolve(opfDir, href.substringBefore('#')) }
            ?.let { path -> manifest.values.firstOrNull { it.href.equals(path, true) } }
        val byId = manifest.values.firstOrNull {
            it.id.equals("cover", true) || it.id.equals("cover-image", true) || it.id.equals("coverimage", true)
        }
        val byName = manifest.values.firstOrNull {
            it.mediaType.startsWith("image/") && ("cover" in it.href.lowercase() || "titlepage" in it.href.lowercase())
        }
        val item = byProperties ?: byMeta ?: byGuide ?: byId ?: byName
        val bytes = item?.let { readManifestBytes(it.href) }
        // Если «обложка» оказалась XHTML-страницей, вытаскиваем из неё первую картинку.
        if (bytes != null && item?.mediaType?.contains("html") == true) {
            val html = String(bytes, detectCharset(bytes))
            val src = Regex("""<(?:img|image)[^>]+(?:src|xlink:href|href)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.get(1) ?: return null
            val imageHref = resolve(item.href.substringBeforeLast('/', ""), src)
            return readManifestBytes(imageHref)
        }
        if (bytes != null) return bytes

        // Если обложка всё ещё не найдена, ищем первое изображение в манифесте
        val firstImage = manifest.values.firstOrNull { it.mediaType.startsWith("image/") }
        return firstImage?.let { readManifestBytes(it.href) }
    }

    private fun readManifestBytes(href: String): ByteArray? {
        return zip.readAll(href)
            ?: zip.findIgnoreCase(href)?.let { zip.readAll(it) }
            ?: zip.findIgnoreCase(decodeUriPath(href))?.let { zip.readAll(it) }
    }

    private fun buildChapters(opf: Element): List<ChapterRef> {
        val titles = readTocTitles(opf)
        return spine.mapIndexed { index, item ->
            val entry = titles[item.href]
            ChapterRef(
                index = index,
                id = item.id,
                title = entry?.title ?: fallbackTitle(item),
                depth = entry?.depth ?: 0,
                approxChars = (zip.entry(item.href)?.uncompressedSize ?: 0L).toInt(),
            )
        }
    }

    private fun fallbackTitle(item: ManifestItem): String? =
        item.href.substringAfterLast('/').substringBeforeLast('.')
            .replace('_', ' ').replace('-', ' ')
            .takeIf { it.isNotBlank() }

    private data class TocEntry(val title: String, val depth: Int)

    private fun readTocTitles(opf: Element): Map<String, TocEntry> {
        val result = LinkedHashMap<String, TocEntry>()

        // EPUB 3: навигационный документ.
        manifest.values.firstOrNull { "nav" in it.properties.split(' ') }?.let { nav ->
            val bytes = zip.readAll(nav.href)
            val root = bytes?.let { XmlDom.parse(it) }
            val baseDir = nav.href.substringBeforeLast('/', "")
            root?.descendants("nav")
                ?.firstOrNull { it.attr("type")?.contains("toc") == true || it.attr("epub:type")?.contains("toc") == true }
                ?.let { collectNavList(it, baseDir, 0, result) }
                ?: root?.descendants("a")?.forEach { anchor ->
                    val href = anchor.attr("href") ?: return@forEach
                    result.putIfAbsent(resolve(baseDir, href.substringBefore('#')), TocEntry(anchor.text(), 0))
                }
        }
        if (result.isNotEmpty()) return result

        // EPUB 2: NCX.
        val ncx = opf.firstOrNull("spine")?.attr("toc")?.let { manifest[it] }
            ?: manifest.values.firstOrNull { it.mediaType.contains("ncx") || it.href.endsWith(".ncx", true) }
        val root = ncx?.href?.let { zip.readAll(it) }?.let { XmlDom.parse(it) } ?: return result
        val baseDir = ncx.href.substringBeforeLast('/', "")
        root.firstOrNull("navMap")?.let { collectNavPoints(it, baseDir, 0, result) }
        return result
    }

    private fun collectNavPoints(parent: Element, baseDir: String, depth: Int, out: MutableMap<String, TocEntry>) {
        for (point in parent.childElements()) {
            if (!point.localNameOrTag().equals("navPoint", true)) continue
            val label = point.firstOrNull("navLabel")?.firstOrNull("text")?.text().orEmpty()
            val href = point.firstOrNull("content")?.attr("src")
            if (href != null && label.isNotBlank()) {
                out.putIfAbsent(resolve(baseDir, href.substringBefore('#')), TocEntry(label, depth))
            }
            collectNavPoints(point, baseDir, depth + 1, out)
        }
    }

    private fun collectNavList(nav: Element, baseDir: String, depth: Int, out: MutableMap<String, TocEntry>) {
        for (list in nav.childElements()) {
            if (!list.localNameOrTag().equals("ol", true) && !list.localNameOrTag().equals("ul", true)) {
                collectNavList(list, baseDir, depth, out)
                continue
            }
            for (item in list.childElements()) {
                val anchor = item.childElements().firstOrNull { it.localNameOrTag().equals("a", true) }
                val href = anchor?.attr("href")
                if (href != null) {
                    out.putIfAbsent(resolve(baseDir, href.substringBefore('#')), TocEntry(anchor.text(), depth))
                }
                collectNavList(item, baseDir, depth + 1, out)
            }
        }
    }

    private fun Element.firstOrNull(localName: String): Element? =
        childElements().firstOrNull { it.localNameOrTag().equals(localName, true) }
            ?: descendants(localName).firstOrNull()

    private companion object {
        fun yearOf(raw: String): Int? = Regex("""\d{4}""").find(raw)?.value?.toIntOrNull()

        fun stripTags(raw: String): String =
            raw.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
    }
}

/** Нормализует относительный путь внутри архива: `a/b` + `../c.xhtml` -> `c.xhtml`. */
internal fun resolve(baseDir: String, href: String): String {
    val decoded = decodeUriPath(href)
    if (decoded.startsWith("/")) return decoded.trimStart('/')
    val parts = ArrayList<String>()
    if (baseDir.isNotEmpty()) parts += baseDir.split('/').filter { it.isNotEmpty() }
    for (segment in decoded.split('/')) {
        when (segment) {
            "", "." -> Unit
            ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
            else -> parts += segment
        }
    }
    return parts.joinToString("/")
}

/** Пути внутри EPUB процентно-кодированы, а имена записей в ZIP — нет. */
internal fun decodeUriPath(value: String): String {
    if ('%' !in value) return value
    return runCatching { java.net.URLDecoder.decode(value.replace("+", "%2B"), "UTF-8") }.getOrDefault(value)
}
