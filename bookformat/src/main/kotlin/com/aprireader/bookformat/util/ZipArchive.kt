package com.aprireader.bookformat.util

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * Минимальный читатель ZIP с произвольным доступом.
 *
 * Зачем свой, а не java.util.zip.ZipFile: ZipFile требует File, а книги приходят
 * из SAF в виде дескриптора. ZipInputStream дал бы только последовательный доступ,
 * что не годится для EPUB (нужно читать OPF, затем произвольные главы) и для
 * CBZ (страницы открываются в произвольном порядке).
 */
class ZipArchive(private val source: RandomAccessSource) : Closeable {

    data class Entry(
        val name: String,
        val method: Int,
        val compressedSize: Long,
        val uncompressedSize: Long,
        val headerOffset: Long,
    )

    private val entriesByName = LinkedHashMap<String, Entry>()

    val entries: Collection<Entry> get() = entriesByName.values
    val names: Set<String> get() = entriesByName.keys

    init {
        parseCentralDirectory()
    }

    operator fun contains(name: String): Boolean = !name.contains("..") && entriesByName.containsKey(sanitizeName(name))

    fun entry(name: String): Entry? = if (name.contains("..")) null else entriesByName[sanitizeName(name)]

    /** Регистронезависимый поиск — некоторые упаковщики меняют регистр путей. */
    fun findIgnoreCase(name: String): Entry? {
        if (name.contains("..")) return null
        val clean = sanitizeName(name)
        return entriesByName[clean] ?: entriesByName.entries.firstOrNull { it.key.equals(clean, true) }?.value
    }

    fun open(name: String): InputStream? = entry(name)?.let { open(it) }

    fun open(entry: Entry): InputStream {
        val dataOffset = dataOffsetOf(entry)
        val raw = source.streamAt(dataOffset, entry.compressedSize)
        return when (entry.method) {
            METHOD_STORED -> raw
            METHOD_DEFLATED -> InflaterInputStream(raw, Inflater(true), 8 * 1024)
            else -> throw UnsupportedOperationException("Unsupported ZIP method ${entry.method} for ${entry.name}")
        }
    }

    fun readAll(name: String): ByteArray? = entry(name)?.let { readAll(it) }

    /**
     * Читает запись целиком в память — с потолком размера.
     *
     * Без него любой файл, открытый через SAF или системное «Поделиться»
     * (а манифест принимает такие файлы от любого приложения — почтового
     * клиента, мессенджера, файлового менеджера), мог бы объявить в
     * центральном каталоге крошечный размер и раздуться при распаковке в
     * гигабайты (zip bomb) — приложение падало бы по OutOfMemoryError.
     * Поэтому размер проверяется дважды: по заявленному `uncompressedSize` —
     * дёшево и сразу отсекает переполнение unsigned-поля и откровенно
     * завышенные заявки; и во время самого чтения — на случай, если
     * заголовок соврал в меньшую сторону, а поток на деле разжимается сильнее
     * заявленного.
     */
    fun readAll(entry: Entry): ByteArray {
        if (entry.uncompressedSize < 0 || entry.uncompressedSize > MAX_DECOMPRESSED_ENTRY_SIZE) {
            throw IOException(
                "ZIP-запись слишком большая: ${entry.name} " +
                    "(${entry.uncompressedSize} байт, лимит $MAX_DECOMPRESSED_ENTRY_SIZE)"
            )
        }
        return open(entry).use { it.readBytesUpTo() }
    }

    private fun dataOffsetOf(entry: Entry): Long {
        val header = readBytesAt(entry.headerOffset, 30)
        require(le32(header, 0) == LOCAL_HEADER_SIG) { "Bad local header for ${entry.name}" }
        val nameLen = le16(header, 26)
        val extraLen = le16(header, 28)
        return entry.headerOffset + 30 + nameLen + extraLen
    }

    private fun readBytesAt(position: Long, length: Int): ByteArray = source.readBytes(position, length)

    private fun parseCentralDirectory() {
        val eocd = findEndOfCentralDirectory() ?: throw IllegalArgumentException("Not a ZIP archive: EOCD not found")
        var entryCount = le16(eocd.bytes, 10).toLong()
        var cdOffset = le32u(eocd.bytes, 16)
        var cdSize = le32u(eocd.bytes, 12)

        if (cdOffset == 0xFFFFFFFFL || entryCount == 0xFFFFL) {
            val z64 = findZip64Eocd(eocd.position)
            if (z64 != null) {
                entryCount = le64(z64, 32)
                cdSize = le64(z64, 40)
                cdOffset = le64(z64, 48)
            }
        }

        val cd = readBytesAt(cdOffset, cdSize.toInt())
        var p = 0
        var i = 0L
        while (i < entryCount && p + 46 <= cd.size) {
            if (le32(cd, p) != CENTRAL_HEADER_SIG) break
            val flags = le16(cd, p + 8)
            val method = le16(cd, p + 10)
            var compressed = le32u(cd, p + 20)
            var uncompressed = le32u(cd, p + 24)
            val nameLen = le16(cd, p + 28)
            val extraLen = le16(cd, p + 30)
            val commentLen = le16(cd, p + 32)
            var localOffset = le32u(cd, p + 42)
            val isUtf8 = (flags and (1 shl 11)) != 0
            val nameBytes = cd.copyOfRange(p + 46, p + 46 + nameLen)
            val rawName = if (isUtf8) {
                String(nameBytes, Charsets.UTF_8)
            } else {
                runCatching {
                    // Проверяем UTF-8
                    val s = String(nameBytes, Charsets.UTF_8)
                    if ('\uFFFD' in s && nameBytes.any { it < 0 }) {
                        String(nameBytes, java.nio.charset.Charset.forName("CP866"))
                    } else s
                }.getOrElse { String(nameBytes, Charsets.UTF_8) }
            }

            if (compressed == 0xFFFFFFFFL || uncompressed == 0xFFFFFFFFL || localOffset == 0xFFFFFFFFL) {
                val extraStart = p + 46 + nameLen
                var e = extraStart
                while (e + 4 <= extraStart + extraLen) {
                    val id = le16(cd, e)
                    val size = le16(cd, e + 2)
                    if (id == 0x0001) {
                        var f = e + 4
                        if (uncompressed == 0xFFFFFFFFL) { uncompressed = le64(cd, f); f += 8 }
                        if (compressed == 0xFFFFFFFFL) { compressed = le64(cd, f); f += 8 }
                        if (localOffset == 0xFFFFFFFFL) { localOffset = le64(cd, f) }
                        break
                    }
                    e += 4 + size
                }
            }

            val safeName = sanitizeName(rawName)

            if (safeName.isNotBlank() && !safeName.endsWith("/")) {
                val entry = Entry(safeName, method, compressed, uncompressed, localOffset)
                entriesByName[safeName] = entry
            }
            p += 46 + nameLen + extraLen + commentLen
            i++
        }
    }

    private class Eocd(val bytes: ByteArray, val position: Long)

    private fun findEndOfCentralDirectory(): Eocd? {
        val maxTail = minOf(source.size, 64L * 1024 + 22)
        if (maxTail < 22) return null
        val start = source.size - maxTail
        val tail = readBytesAt(start, maxTail.toInt())
        for (i in tail.size - 22 downTo 0) {
            if (le32(tail, i) == EOCD_SIG) {
                return Eocd(tail.copyOfRange(i, tail.size), start + i)
            }
        }
        return null
    }

    private fun findZip64Eocd(eocdPosition: Long): ByteArray? {
        val locatorPos = eocdPosition - 20
        if (locatorPos < 0) return null
        val locator = readBytesAt(locatorPos, 20)
        if (le32(locator, 0) != ZIP64_LOCATOR_SIG) return null
        val z64Pos = le64(locator, 8)
        if (z64Pos < 0 || z64Pos + 56 > source.size) return null
        val z64 = readBytesAt(z64Pos, 56)
        return if (le32(z64, 0) == ZIP64_EOCD_SIG) z64 else null
    }

    override fun close() = source.close()

    private companion object {
        fun sanitizeName(name: String): String {
            val normalized = name.replace('\\', '/').trimStart('/')
            return normalized.split('/').filter { it.isNotEmpty() && it != ".." && it != "." }.joinToString("/")
        }

        const val LOCAL_HEADER_SIG = 0x04034b50
        const val CENTRAL_HEADER_SIG = 0x02014b50
        const val EOCD_SIG = 0x06054b50
        const val ZIP64_EOCD_SIG = 0x06064b50
        const val ZIP64_LOCATOR_SIG = 0x07064b50
        const val METHOD_STORED = 0
        const val METHOD_DEFLATED = 8
    }
}

internal fun le16(b: ByteArray, o: Int): Int = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

internal fun le32(b: ByteArray, o: Int): Int =
    (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
        ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)

internal fun le32u(b: ByteArray, o: Int): Long = le32(b, o).toLong() and 0xFFFFFFFFL

internal fun le64(b: ByteArray, o: Int): Long {
    var v = 0L
    for (i in 7 downTo 0) v = (v shl 8) or (b[o + i].toLong() and 0xFF)
    return v
}
