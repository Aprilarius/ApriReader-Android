package com.aprireader.bookformat.util

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Потолок на то, сколько один файл внутри книги может занять в памяти при
 * распаковке целиком в `ByteArray`: 100 МБ с запасом покрывает необычно
 * большую главу, скан-страницу комикса или встроенную обложку.
 */
const val MAX_DECOMPRESSED_ENTRY_SIZE = 100L * 1024 * 1024

/**
 * Читает поток целиком, но не больше [limit] байт — иначе бросает
 * [IOException] вместо того, чтобы продолжать аллоцировать память.
 *
 * Книги в этом приложении приходят не только из папки пользователя: манифест
 * принимает файлы через системное «Поделиться» и `ACTION_VIEW` от любого
 * приложения — почтового клиента, мессенджера, файлового менеджера. Обычный
 * `InputStream.readBytes()` не имеет потолка: специально собранный архив
 * (zip bomb) с крошечным заявленным размером страницы, разжимающейся в
 * гигабайты, уронил бы процесс по `OutOfMemoryError`. Используется во всех
 * местах, где сжатая запись — из ZIP (EPUB, CBZ) или из RAR (CBR, через
 * junrar) — читается в память целиком.
 */
fun InputStream.readBytesUpTo(limit: Long = MAX_DECOMPRESSED_ENTRY_SIZE): ByteArray {
    val out = ByteArrayOutputStream(8 * 1024)
    val chunk = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val read = read(chunk)
        if (read < 0) break
        total += read
        if (total > limit) {
            throw IOException("Поток разжимается сверх допустимого размера ($limit байт)")
        }
        out.write(chunk, 0, read)
    }
    return out.toByteArray()
}
