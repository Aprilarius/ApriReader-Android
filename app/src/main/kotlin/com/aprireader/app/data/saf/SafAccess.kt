package com.aprireader.app.data.saf

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.aprireader.bookformat.DocumentAccess
import com.aprireader.bookformat.util.RandomAccessSource
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest

/**
 * Доступ к книге, лежащей за SAF-URI.
 *
 * Ключевая деталь: дескриптор, который отдаёт локальный DocumentsProvider,
 * позиционируем, поэтому произвольный доступ (ZIP-архивы EPUB и CBZ) работает
 * без копирования книги в кэш. Копия делается только там, где библиотека
 * физически требует файла (RAR, PDFBox), и складывается в кэш с вытеснением.
 */
class SafDocumentAccess(
    private val context: Context,
    private val uri: Uri,
    override val displayName: String,
    override val mimeType: String?,
) : DocumentAccess {

    override fun openStream(): InputStream {
        if (uri.scheme == "file") {
            val file = File(uri.path ?: uri.schemeSpecificPart)
            if (file.exists()) return FileInputStream(file)
        }
        return context.contentResolver.openInputStream(uri)
            ?: throw java.io.FileNotFoundException("Не удалось открыть поток: $uri")
    }

    override fun openRandomAccess(): RandomAccessSource {
        if (uri.scheme == "file") {
            val file = File(uri.path ?: uri.schemeSpecificPart)
            if (file.exists()) return com.aprireader.bookformat.util.FileRandomAccessSource(file)
        }
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw java.io.FileNotFoundException("Не удалось открыть дескриптор: $uri")
        return runCatching { DescriptorRandomAccessSource(descriptor) }
            .getOrElse {
                descriptor.close()
                // Провайдер не поддерживает позиционирование — работаем через кэш-копию.
                com.aprireader.bookformat.util.FileRandomAccessSource(
                    materializeFile() ?: throw it
                )
            }
    }

    override fun materializeFile(): File? {
        if (uri.scheme == "file") {
            val file = File(uri.path ?: uri.schemeSpecificPart)
            if (file.exists()) return file
        }
        return DocumentCache.materialize(context, uri, displayName)
    }
}

/** Произвольный доступ поверх ParcelFileDescriptor через позиционное чтение канала. */
private class DescriptorRandomAccessSource(
    private val descriptor: ParcelFileDescriptor,
) : RandomAccessSource {

    private val stream = FileInputStream(descriptor.fileDescriptor)
    private val channel: FileChannel = stream.channel

    override val size: Long = channel.size()

    @Synchronized
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (position >= size) return -1
        return channel.read(ByteBuffer.wrap(buffer, offset, length), position)
    }

    override fun close() {
        runCatching { channel.close() }
        runCatching { stream.close() }
        runCatching { descriptor.close() }
    }
}

/**
 * Кэш локальных копий книг. Нужен только форматам без потокового доступа.
 * Вытесняется по «давно не использовалось», чтобы не расти бесконечно.
 */
object DocumentCache {

    private const val MAX_CACHE_BYTES = 512L * 1024 * 1024

    fun materialize(context: Context, uri: Uri, displayName: String): File? {
        val tag = "DocumentCache"
        val startedAt = System.currentTimeMillis()
        val dir = File(context.cacheDir, "books").apply { mkdirs() }
        val target = File(dir, "${uri.toString().sha1()}_${displayName.takeLast(40).sanitized()}")
        val remoteSize = querySize(context, uri)
        if (target.exists() && (remoteSize == null || target.length() == remoteSize)) {
            target.setLastModified(System.currentTimeMillis())
            android.util.Log.d(tag, "materialize(\"$displayName\"): cache hit, ${target.length()} bytes")
            return target
        }
        android.util.Log.d(tag, "materialize(\"$displayName\"): copying, remoteSize=$remoteSize")
        val result = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output, 256 * 1024) }
            } ?: return null
            trim(dir)
            target
        }.getOrNull()
        android.util.Log.d(
            tag,
            "materialize(\"$displayName\"): ${if (result != null) "done, ${result.length()} bytes" else "FAILED"} " +
                "in ${System.currentTimeMillis() - startedAt}ms",
        )
        return result
    }

    fun clear(context: Context) {
        File(context.cacheDir, "books").listFiles()?.forEach { it.delete() }
    }

    private fun trim(dir: File) {
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= MAX_CACHE_BYTES) break
            total -= file.length()
            file.delete()
        }
    }

    private fun querySize(context: Context, uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }.getOrNull()
}

/**
 * Стабильный ключ книги: размер + хэш первых 64 КБ + нормализованное имя.
 *
 * Именно он, а не URI, связывает прогресс чтения, закладки и статистику с книгой:
 * URI меняется при переоформлении доступа к папке или переносе файла, а книга — нет.
 */
object BookKey {

    private const val SAMPLE_BYTES = 64 * 1024

    fun compute(size: Long, name: String, headBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(headBytes)
        digest.update(name.normalizedName().toByteArray())
        digest.update(size.toString().toByteArray())
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun compute(context: Context, uri: Uri, size: Long, name: String): String {
        val head = runCatching {
            val stream = if (uri.scheme == "file") {
                val file = File(uri.path ?: uri.schemeSpecificPart)
                if (file.exists()) FileInputStream(file) else null
            } else {
                context.contentResolver.openInputStream(uri)
            }
            stream?.use { s ->
                val buffer = ByteArray(SAMPLE_BYTES)
                var read = 0
                while (read < SAMPLE_BYTES) {
                    val n = s.read(buffer, read, SAMPLE_BYTES - read)
                    if (n <= 0) break
                    read += n
                }
                buffer.copyOf(read)
            }
        }.getOrNull() ?: ByteArray(0)
        return compute(size, name, head)
    }

    private fun String.normalizedName(): String = lowercase().replace(Regex("[^\\p{L}\\p{N}.]"), "")
}

internal fun String.sha1(): String =
    MessageDigest.getInstance("SHA-1").digest(toByteArray()).joinToString("") { "%02x".format(it) }

internal fun String.sanitized(): String = replace(Regex("[^A-Za-z0-9._-]"), "_")

/** Читает отображаемое имя и размер документа одним запросом. */
fun Context.queryDocument(uri: Uri): DocumentInfo? = runCatching {
    contentResolver.query(
        uri,
        arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        ),
        null, null, null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        DocumentInfo(
            uri = uri,
            displayName = cursor.getString(0) ?: uri.lastPathSegment.orEmpty(),
            size = if (cursor.isNull(1)) 0L else cursor.getLong(1),
            mimeType = cursor.getString(2),
            lastModified = if (cursor.isNull(3)) 0L else cursor.getLong(3),
        )
    }
}.getOrNull()

data class DocumentInfo(
    val uri: Uri,
    val displayName: String,
    val size: Long,
    val mimeType: String?,
    val lastModified: Long,
)
