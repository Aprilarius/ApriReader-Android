package com.aprireader.app.ui.reader.rsvp

import com.aprireader.bookformat.model.ContentBlock

/**
 * Одна «вспышка» RSVP: то, что показывается на экране за один такт.
 *
 * [pivotIndex] — точка оптимального распознавания (ORP): буква, на которой глаз
 * фиксируется. Ради неё слово и центрируется, иначе режим быстро утомляет.
 */
data class RsvpFrame(
    val text: String,
    val pivotIndex: Int,
    /** Множитель длительности такта: длинные слова и знаки препинания требуют паузы. */
    val durationScale: Float,
    /** Индекс первого слова кадра в потоке главы — по нему считается прогресс и возврат в текст. */
    val wordIndex: Int,
    val isParagraphEnd: Boolean,
)

/**
 * Подготовка потока слов главы для режима RSVP.
 *
 * Разбор идёт по уже разобранным блокам, а не по сырому тексту, поэтому
 * одинаково работает для всех форматов, включая текстовый слой PDF.
 */
object RsvpEngine {

    fun buildFrames(
        blocks: List<ContentBlock>,
        chunkSize: Int = 1,
        pauseOnPunctuation: Boolean = true,
    ): List<RsvpFrame> {
        val frames = ArrayList<RsvpFrame>()
        var wordIndex = 0

        for (block in blocks) {
            val text = when (block) {
                is ContentBlock.Heading -> block.text.text
                is ContentBlock.Paragraph -> block.text.text
                else -> null
            } ?: continue

            val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
            var i = 0
            while (i < words.size) {
                val chunk = words.subList(i, minOf(i + chunkSize.coerceAtLeast(1), words.size))
                val joined = chunk.joinToString(" ")
                val isLast = i + chunk.size >= words.size
                frames += RsvpFrame(
                    text = joined,
                    pivotIndex = pivotIndexOf(joined),
                    durationScale = durationScale(joined, isLast, pauseOnPunctuation),
                    wordIndex = wordIndex,
                    isParagraphEnd = isLast,
                )
                wordIndex += chunk.size
                i += chunk.size
            }
        }
        return frames
    }

    /**
     * Точка фиксации взгляда. Значения подобраны по классической таблице ORP:
     * она смещена влево от центра, потому что распознавание слова опирается на
     * его начало.
     */
    fun pivotIndexOf(word: String): Int {
        val letters = word.trimStart { !it.isLetterOrDigit() }
        val offset = word.length - letters.length
        val length = letters.trimEnd { !it.isLetterOrDigit() }.length.coerceAtLeast(1)
        val pivot = when {
            length <= 1 -> 0
            length <= 5 -> 1
            length <= 9 -> 2
            length <= 13 -> 3
            else -> 4
        }
        return (offset + pivot).coerceIn(0, (word.length - 1).coerceAtLeast(0))
    }

    private fun durationScale(text: String, isParagraphEnd: Boolean, pauseOnPunctuation: Boolean): Float {
        var scale = 1f
        val length = text.length
        if (length > 8) scale += (length - 8) * 0.045f
        if (pauseOnPunctuation) {
            val last = text.trimEnd().lastOrNull()
            scale *= when (last) {
                '.', '!', '?', '…' -> 2.0f
                ',', ';', ':', '—', '–' -> 1.45f
                else -> 1f
            }
        }
        if (isParagraphEnd) scale *= 1.35f
        return scale.coerceIn(0.5f, 4f)
    }

    /** Длительность такта в миллисекундах для заданной скорости. */
    fun frameDurationMs(frame: RsvpFrame, wordsPerMinute: Int, chunkSize: Int): Long {
        val base = 60_000.0 / wordsPerMinute.coerceIn(60, 1200) * chunkSize.coerceAtLeast(1)
        return (base * frame.durationScale).toLong().coerceAtLeast(40L)
    }
}
