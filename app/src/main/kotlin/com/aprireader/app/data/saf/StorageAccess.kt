package com.aprireader.app.data.saf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.aprireader.bookformat.BookOpener
import com.aprireader.bookformat.model.BookFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Управление постоянными разрешениями SAF.
 *
 * Отзыв доступа — штатный сценарий: пользователь может убрать разрешение в
 * системных настройках, вынуть карту памяти или удалить папку. Поэтому все
 * операции проверяют актуальность разрешения, а не полагаются на то, что оно
 * когда-то было выдано.
 */
class StorageAccessManager(private val context: Context) {

    private val resolver get() = context.contentResolver

    /** Забирает постоянное разрешение на чтение. Вызывается сразу после выбора в системном диалоге. */
    fun persist(uri: Uri): Boolean = runCatching {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    }.getOrElse { false }

    fun release(uri: Uri) {
        runCatching { resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    /** Есть ли у приложения действующее разрешение на этот URI. */
    fun hasAccess(uri: Uri): Boolean {
        val target = uri.toString()
        return resolver.persistedUriPermissions.any { it.isReadPermission && it.uri.toString() == target }
    }

    fun persistedUris(): List<Uri> =
        resolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri }

    /** Проверяет, что папка не только разрешена, но и физически доступна. */
    suspend fun isTreeReachable(treeUri: Uri): Boolean = withContext(Dispatchers.IO) {
        if (!hasAccess(treeUri)) return@withContext false
        runCatching {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
            resolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
                ?.use { true } ?: false
        }.getOrElse { false }
    }

    /** Доступен ли конкретный документ прямо сейчас. */
    suspend fun isDocumentReachable(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        if (uri.scheme == "file") {
            val file = java.io.File(uri.path ?: uri.schemeSpecificPart)
            return@withContext file.exists()
        }
        runCatching {
            resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
                ?.use { it.count > 0 } ?: false
        }.getOrElse { false }
    }
}

/**
 * Обход дерева папок SAF курсором.
 *
 * Намеренно не используется DocumentFile.listFiles(): он делает отдельный запрос
 * на каждый файл и на библиотеке в тысячу книг работает недопустимо медленно.
 */
class LibraryScanner(private val context: Context) {

    /** Находит все файлы поддерживаемых форматов внутри дерева. */
    suspend fun scanTree(treeUri: Uri, onProgress: (Int) -> Unit = {}): List<DocumentInfo> =
        withContext(Dispatchers.IO) {
            val found = ArrayList<DocumentInfo>()
            val queue = ArrayDeque<String>()
            queue += DocumentsContract.getTreeDocumentId(treeUri)
            val visited = HashSet<String>()

            while (queue.isNotEmpty()) {
                val documentId = queue.removeFirst()
                if (!visited.add(documentId)) continue
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
                val cursor = runCatching {
                    context.contentResolver.query(
                        childrenUri,
                        arrayOf(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE,
                            DocumentsContract.Document.COLUMN_SIZE,
                            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                        ),
                        null, null, null,
                    )
                }.getOrNull() ?: continue

                cursor.use {
                    while (it.moveToNext()) {
                        val childId = it.getString(0) ?: continue
                        val name = it.getString(1) ?: continue
                        val mime = it.getString(2)
                        val size = if (it.isNull(3)) 0L else it.getLong(3)
                        val modified = if (it.isNull(4)) 0L else it.getLong(4)

                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            queue += childId
                            continue
                        }
                        // fromExtension в первую очередь — точнее и не требует
                        // разбора MIME. Но у части SAF-провайдеров (особенно
                        // видно на FB2 — они реже встречаются, чем EPUB/PDF, и
                        // некоторые файловые менеджеры показывают вместо имени
                        // файла заголовок книги из метаданных без расширения)
                        // DISPLAY_NAME приходит вообще без расширения — тогда
                        // единственная зацепка, которая тут доступна бесплатно
                        // (без открытия потока на каждый файл в папке — это
                        // сделало бы полное сканирование на тысячах файлов
                        // болезненно медленным), это MIME-тип, который тот же
                        // запрос курсора уже вернул бесплатно.
                        val recognized = BookFormat.fromExtension(name) != null ||
                            BookOpener.detectFormat(name, mime, header = null) != null
                        if (!recognized) continue
                        found += DocumentInfo(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId),
                            displayName = name,
                            size = size,
                            mimeType = mime,
                            lastModified = modified,
                        )
                        onProgress(found.size)
                    }
                }
            }
            found
        }
}

/** Читаемое имя папки для показа в UI. */
fun Uri.treeDisplayName(): String {
    val documentId = runCatching { DocumentsContract.getTreeDocumentId(this) }.getOrNull() ?: return toString()
    return documentId.substringAfterLast(':').substringAfterLast('/').ifBlank { documentId }
}
