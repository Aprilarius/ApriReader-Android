package com.aprireader.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Тональная палитра в перцептивном пространстве CIELCh.
 *
 * Material 3 строит палитры в HCT (CAM16). Публичного API для генерации схемы из
 * произвольного цвета-семени в androidx.compose.material3 нет, а тянуть внешнюю
 * библиотеку ради этого не хочется. LCh(ab) даёт близкий результат: тон меняется
 * равномерно по восприятию, оттенок и насыщенность сохраняются. Дальше поверх
 * этого работает явная проверка контраста — она и отвечает за читаемость.
 */
class TonalPalette private constructor(
    private val hue: Double,
    private val chroma: Double,
) {

    /** Цвет заданного тона, где тон — это L* от 0 (чёрный) до 100 (белый). */
    fun tone(value: Int): Color = lchToColor(value.toDouble().coerceIn(0.0, 100.0), chroma, hue)

    fun toneWithChroma(value: Int, chromaScale: Double): Color =
        lchToColor(value.toDouble().coerceIn(0.0, 100.0), chroma * chromaScale, hue)

    companion object {
        fun from(seed: Color): TonalPalette {
            val lch = colorToLch(seed)
            // Слишком блёклое семя даёт безжизненную схему, слишком яркое — ядовитую.
            val chroma = lch[1].coerceIn(12.0, 84.0)
            return TonalPalette(lch[2], chroma)
        }

        fun from(argb: Int): TonalPalette = from(Color(argb))

        /** Нейтральная палитра с лёгким подмешиванием оттенка семени. */
        fun neutralFrom(seed: Color, chroma: Double = 5.0): TonalPalette {
            val lch = colorToLch(seed)
            return TonalPalette(lch[2], chroma)
        }
    }
}

// --- преобразования цвета ---

private fun srgbToLinear(channel: Double): Double =
    if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)

private fun linearToSrgb(channel: Double): Double =
    if (channel <= 0.0031308) channel * 12.92 else 1.055 * channel.pow(1 / 2.4) - 0.055

private const val WHITE_X = 0.95047
private const val WHITE_Y = 1.0
private const val WHITE_Z = 1.08883

private fun colorToLch(color: Color): DoubleArray {
    val r = srgbToLinear(color.red.toDouble())
    val g = srgbToLinear(color.green.toDouble())
    val b = srgbToLinear(color.blue.toDouble())

    val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / WHITE_X
    val y = (0.2126729 * r + 0.7151522 * g + 0.0721750 * b) / WHITE_Y
    val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / WHITE_Z

    fun f(t: Double): Double = if (t > 0.008856) cbrt(t) else (7.787 * t) + 16.0 / 116.0

    val fx = f(x)
    val fy = f(y)
    val fz = f(z)

    val l = 116 * fy - 16
    val a = 500 * (fx - fy)
    val bb = 200 * (fy - fz)

    val chroma = kotlin.math.sqrt(a * a + bb * bb)
    var hue = Math.toDegrees(kotlin.math.atan2(bb, a))
    if (hue < 0) hue += 360.0
    return doubleArrayOf(l, chroma, hue)
}

private fun lchToColor(l: Double, chroma: Double, hue: Double): Color {
    var currentChroma = chroma
    // Не каждый LCh существует в sRGB: снижаем насыщенность, пока цвет не станет отображаемым.
    repeat(40) {
        val color = lchToColorUnclamped(l, currentChroma, hue)
        if (color != null) return color
        currentChroma *= 0.92
    }
    val gray = (l / 100.0).coerceIn(0.0, 1.0).let { linearToSrgb(labInverse(it)) }
    return Color(gray.toFloat(), gray.toFloat(), gray.toFloat())
}

private fun labInverse(y: Double): Double = y.pow(3)

private fun lchToColorUnclamped(l: Double, chroma: Double, hue: Double): Color? {
    val hRad = Math.toRadians(hue)
    val a = chroma * cos(hRad)
    val b = chroma * sin(hRad)

    val fy = (l + 16) / 116
    val fx = fy + a / 500
    val fz = fy - b / 200

    fun fInv(t: Double): Double = if (t.pow(3) > 0.008856) t.pow(3) else (t - 16.0 / 116.0) / 7.787

    val x = fInv(fx) * WHITE_X
    val y = fInv(fy) * WHITE_Y
    val z = fInv(fz) * WHITE_Z

    val rLinear = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z
    val gLinear = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z
    val bLinear = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z

    val r = linearToSrgb(rLinear)
    val g = linearToSrgb(gLinear)
    val bl = linearToSrgb(bLinear)

    val tolerance = 0.002
    if (r < -tolerance || r > 1 + tolerance) return null
    if (g < -tolerance || g > 1 + tolerance) return null
    if (bl < -tolerance || bl > 1 + tolerance) return null

    return Color(
        r.coerceIn(0.0, 1.0).toFloat(),
        g.coerceIn(0.0, 1.0).toFloat(),
        bl.coerceIn(0.0, 1.0).toFloat(),
    )
}

// --- контраст ---

/** Относительная яркость по WCAG 2.1. */
fun Color.relativeLuminance(): Double =
    0.2126 * srgbToLinear(red.toDouble()) +
        0.7152 * srgbToLinear(green.toDouble()) +
        0.0722 * srgbToLinear(blue.toDouble())

/** Коэффициент контраста по WCAG: от 1 (одинаковые) до 21 (чёрный на белом). */
fun contrastRatio(foreground: Color, background: Color): Double {
    val l1 = foreground.relativeLuminance()
    val l2 = background.relativeLuminance()
    val lighter = maxOf(l1, l2)
    val darker = minOf(l1, l2)
    return (lighter + 0.05) / (darker + 0.05)
}

/**
 * Подбирает тон переднего плана так, чтобы контраст с фоном был не ниже [minRatio].
 * Это то, что превращает «красивый цвет с обложки» в «читаемый текст».
 */
fun ensureContrast(foreground: Color, background: Color, minRatio: Double = 4.5): Color {
    if (contrastRatio(foreground, background) >= minRatio) return foreground

    val lch = colorToLch(foreground)
    val backgroundLuminance = background.relativeLuminance()
    val goDarker = backgroundLuminance > 0.5

    var best = foreground
    var bestRatio = contrastRatio(foreground, background)
    var lightness = lch[0]
    val step = if (goDarker) -2.0 else 2.0

    repeat(50) {
        lightness = (lightness + step).coerceIn(0.0, 100.0)
        val candidate = lchToColor(lightness, lch[1], lch[2])
        val ratio = contrastRatio(candidate, background)
        if (ratio > bestRatio) {
            bestRatio = ratio
            best = candidate
        }
        if (ratio >= minRatio) return candidate
    }
    // Крайний случай: даже чистый чёрный или белый лучше, чем нечитаемый текст.
    val fallback = if (goDarker) Color.Black else Color.White
    return if (contrastRatio(fallback, background) > bestRatio) fallback else best
}

/** Цвет текста поверх заливки — выбирается тот, что читается лучше. */
fun onColorFor(background: Color): Color =
    if (contrastRatio(Color.White, background) >= contrastRatio(Color.Black, background)) {
        Color.White
    } else {
        Color.Black
    }

internal fun Color.argb(): Int = toArgb()

internal fun Color.distanceTo(other: Color): Double {
    val a = colorToLch(this)
    val b = colorToLch(other)
    val dl = a[0] - b[0]
    val dc = a[1] - b[1]
    var dh = abs(a[2] - b[2])
    if (dh > 180) dh = 360 - dh
    return kotlin.math.sqrt(dl * dl + dc * dc + (dh / 3) * (dh / 3))
}
