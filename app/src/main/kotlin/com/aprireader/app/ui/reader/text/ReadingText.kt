package com.aprireader.app.ui.reader.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import com.aprireader.bookformat.model.RichText
import com.aprireader.bookformat.model.SpanKind
import kotlin.math.ceil

/**
 * Превращение текста книги в размеченную строку.
 *
 * Здесь же живёт бионический режим: он не переписывает текст, а накладывает
 * дополнительный вес на «якорную» часть каждого слова. Реализация намеренно
 * работает на уровне спанов, а не HTML — поэтому одинаково применима к EPUB,
 * FB2, TXT и извлечённому тексту PDF.
 */
object ReadingTextBuilder {

    fun build(
        rich: RichText,
        bionic: BionicOptions?,
        baseColor: Color,
        linkColor: Color,
        monoFamily: FontFamily = FontFamily.Monospace,
    ): AnnotatedString = buildAnnotatedString {
        append(rich.text)

        for (span in rich.spans) {
            val start = span.start.coerceIn(0, rich.text.length)
            val end = span.end.coerceIn(start, rich.text.length)
            if (end <= start) continue
            val style = when (span.kind) {
                SpanKind.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                SpanKind.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                SpanKind.MONO -> SpanStyle(fontFamily = monoFamily)
                SpanKind.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
                SpanKind.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                SpanKind.SUPERSCRIPT -> SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = SMALL_EM)
                SpanKind.SUBSCRIPT -> SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = SMALL_EM)
                SpanKind.LINK -> SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
            }
            addStyle(style, start, end)
        }

        if (bionic != null) {
            val (anchors, tails) = bionicTokenRanges(rich.text, bionic.intensity, bionic.dimTail)
            val boldStyle = SpanStyle(
                fontWeight = FontWeight.Bold,
                fontSynthesis = FontSynthesis.Weight,
            )
            for (range in anchors) {
                addStyle(boldStyle, range.first, range.second)
            }
            if (bionic.dimTail) {
                val dimStyle = SpanStyle(color = baseColor.copy(alpha = 0.72f))
                for (range in tails) {
                    addStyle(dimStyle, range.first, range.second)
                }
            }
        }
    }

    private val SMALL_EM = androidx.compose.ui.unit.TextUnit(
        0.75f,
        androidx.compose.ui.unit.TextUnitType.Em,
    )
}

data class BionicOptions(val intensity: Float, val dimTail: Boolean)

/**
 * Однопроходное вычисление диапазонов «якорей» и «хвостов» слов для бионического чтения.
 */
fun bionicTokenRanges(
    text: String,
    intensity: Float,
    extractTails: Boolean,
): Pair<List<Pair<Int, Int>>, List<Pair<Int, Int>>> {
    val anchors = ArrayList<Pair<Int, Int>>()
    val tails = if (extractTails) ArrayList<Pair<Int, Int>>() else emptyList<Pair<Int, Int>>()

    var index = 0
    val length = text.length
    while (index < length) {
        if (!text[index].isLetter()) {
            index++
            continue
        }
        val start = index
        while (index < length && (text[index].isLetter() || text[index] == '-' || text[index] == '\'')) {
            index++
        }
        val wordLen = index - start
        val anchor = anchorLength(wordLen, intensity)
        if (anchor > 0) {
            val anchorEnd = start + anchor
            anchors += start to anchorEnd
            if (extractTails && index > anchorEnd) {
                tails as ArrayList
                tails += anchorEnd to index
            }
        }
    }
    return anchors to tails
}

fun bionicRanges(text: String, intensity: Float): List<Pair<Int, Int>> =
    bionicTokenRanges(text, intensity, extractTails = false).first

fun bionicTailRanges(text: String, intensity: Float): List<Pair<Int, Int>> =
    bionicTokenRanges(text, intensity, extractTails = true).second

internal fun anchorLength(wordLength: Int, intensity: Float): Int = when {
    wordLength <= 1 -> wordLength
    wordLength <= 3 -> 1
    else -> ceil(wordLength * intensity.coerceIn(0.2f, 0.8f)).toInt().coerceIn(1, wordLength - 1)
}
