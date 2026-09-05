package com.aprireader.bookformat.util

import java.io.Closeable
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile

/**
 * Произвольный доступ к байтам документа. Реализуется в app-слое поверх
 * ParcelFileDescriptor из SAF: дескрипторы локальных провайдеров позиционируемы,
 * поэтому копировать книгу в кэш не требуется.
 */
interface RandomAccessSource : Closeable {
    val size: Long

    /** Читает до [length] байт с позиции [position]. Возвращает число прочитанных байт или -1. */
    fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int
}

fun RandomAccessSource.readFully(position: Long, buffer: ByteArray, offset: Int = 0, length: Int = buffer.size) {
    var read = 0
    while (read < length) {
        val n = readAt(position + read, buffer, offset + read, length - read)
        if (n <= 0) throw EOFException("Unexpected end of source at ${position + read}")
        read += n
    }
}

fun RandomAccessSource.readBytes(position: Long, length: Int): ByteArray =
    ByteArray(length).also { readFully(position, it) }

/** Последовательный поток поверх произвольного доступа. */
fun RandomAccessSource.streamAt(position: Long, length: Long): InputStream = object : InputStream() {
    private var pos = position
    private val end = position + length

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (pos >= end) return -1
        val toRead = minOf(len.toLong(), end - pos).toInt()
        val n = readAt(pos, b, off, toRead)
        if (n > 0) pos += n
        return n
    }

    override fun available(): Int = (end - pos).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

/** Реализация поверх обычного файла — используется в тестах и для кэшированных копий. */
class FileRandomAccessSource(file: File) : RandomAccessSource {
    private val raf = RandomAccessFile(file, "r")
    override val size: Long = raf.length()

    @Synchronized
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (position >= size) return -1
        raf.seek(position)
        return raf.read(buffer, offset, length)
    }

    override fun close() = raf.close()
}

/** Реализация поверх массива в памяти — для вложенных архивов (fb2.zip). */
class ByteArrayRandomAccessSource(private val data: ByteArray) : RandomAccessSource {
    override val size: Long = data.size.toLong()

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (position >= size) return -1
        val n = minOf(length.toLong(), size - position).toInt()
        System.arraycopy(data, position.toInt(), buffer, offset, n)
        return n
    }

    override fun close() = Unit
}
