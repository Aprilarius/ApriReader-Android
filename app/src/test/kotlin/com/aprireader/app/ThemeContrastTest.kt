package com.aprireader.app

import androidx.compose.ui.graphics.Color
import com.aprireader.app.data.prefs.DesignStyle
import com.aprireader.app.data.prefs.ReaderPageStyle
import com.aprireader.app.ui.theme.buildColorScheme
import com.aprireader.app.ui.theme.contrastRatio
import com.aprireader.app.ui.theme.ensureContrast
import com.aprireader.app.ui.theme.readerPalette
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверка того самого обещания, ради которого затевалась темизация по обложке:
 * какой бы цвет ни пришёл с картинки, текст остаётся читаемым.
 *
 * Первые три теста проверяют все пять стилей дизайна ([DesignStyle]), а не
 * только тот, что подставляется по умолчанию (Liquid Glass) — у Solid Clean,
 * Wood Library, Glassmorphism и Neumorphism свои цвета фона и поверхностей
 * (см. `buildColorScheme`), и раньше они ни разу не проверялись на WCAG AA.
 * Тест на AMOLED стиль не учитывает: `pureBlack=true` строит схему одинаково
 * для всех стилей (см. `buildColorScheme`).
 */
class ThemeContrastTest {

    /** Набор «трудных» семян: кислотные, почти чёрные, почти белые, блёклые. */
    private val seeds = listOf(
        Color(0xFFC9A227), // фирменная латунь
        Color(0xFF00FF00), // ядовитый зелёный
        Color(0xFFFFFF00), // жёлтый, худший случай для белого текста
        Color(0xFF000010), // почти чёрный
        Color(0xFFFFFEF8), // почти белый
        Color(0xFF7A7A7A), // серый без насыщенности
        Color(0xFFFF00FF), // пурпур
        Color(0xFF102A6B), // тёмно-синий
    )

    @Test
    fun `text on surface passes WCAG AA in both themes`() {
        for (designStyle in DesignStyle.entries) {
            for (seed in seeds) {
                for (dark in listOf(false, true)) {
                    val scheme = buildColorScheme(seed, dark, designStyle = designStyle)
                    val ratio = contrastRatio(scheme.onSurface, scheme.surface)
                    assertTrue(
                        "onSurface/surface = %.2f для $designStyle, семени $seed (dark=$dark)".format(ratio),
                        ratio >= 4.5,
                    )
                    val variantRatio = contrastRatio(scheme.onSurfaceVariant, scheme.surface)
                    assertTrue(
                        "onSurfaceVariant/surface = %.2f для $designStyle, семени $seed (dark=$dark)".format(variantRatio),
                        variantRatio >= 3.0,
                    )
                }
            }
        }
    }

    @Test
    fun `text on primary and containers stays readable`() {
        for (designStyle in DesignStyle.entries) {
            for (seed in seeds) {
                for (dark in listOf(false, true)) {
                    val scheme = buildColorScheme(seed, dark, designStyle = designStyle)
                    val onPrimary = contrastRatio(scheme.onPrimary, scheme.primary)
                    assertTrue(
                        "onPrimary/primary = %.2f для $designStyle, семени $seed (dark=$dark)".format(onPrimary),
                        onPrimary >= 4.5,
                    )
                    val onContainer = contrastRatio(scheme.onPrimaryContainer, scheme.primaryContainer)
                    assertTrue(
                        "onPrimaryContainer/primaryContainer = %.2f для $designStyle, семени $seed (dark=$dark)".format(onContainer),
                        onContainer >= 4.5,
                    )
                }
            }
        }
    }

    @Test
    fun `reading page keeps AAA contrast for body text`() {
        for (designStyle in DesignStyle.entries) {
            for (seed in seeds) {
                for (dark in listOf(false, true)) {
                    val scheme = buildColorScheme(seed, dark, designStyle = designStyle)
                    for (style in ReaderPageStyle.entries) {
                        val palette = readerPalette(
                            style = style,
                            scheme = scheme,
                            accent = scheme.primary,
                            darkTheme = dark,
                            pureBlack = false,
                        )
                        val body = contrastRatio(palette.text, palette.background)
                        assertTrue(
                            "Основной текст $designStyle/$style: %.2f (seed=$seed, dark=$dark)".format(body),
                            body >= 7.0,
                        )
                        val secondary = contrastRatio(palette.secondaryText, palette.background)
                        assertTrue(
                            "Вторичный текст $designStyle/$style: %.2f (seed=$seed, dark=$dark)".format(secondary),
                            secondary >= 4.5,
                        )
                        val accent = contrastRatio(palette.accent, palette.background)
                        assertTrue(
                            "Акцент $designStyle/$style: %.2f (seed=$seed, dark=$dark)".format(accent),
                            accent >= 4.5,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `reading page and UI surfaces pass contrast in AMOLED theme`() {
        for (seed in seeds) {
            val scheme = buildColorScheme(seed, dark = true, pureBlack = true)
            // Surface contrast
            val ratio = contrastRatio(scheme.onSurface, scheme.surface)
            assertTrue("onSurface/surface AMOLED = %.2f (seed=$seed)".format(ratio), ratio >= 4.5)

            // Reader page in AMOLED mode
            for (style in ReaderPageStyle.entries) {
                val palette = readerPalette(
                    style = style,
                    scheme = scheme,
                    accent = scheme.primary,
                    darkTheme = true,
                    pureBlack = true,
                )
                val body = contrastRatio(palette.text, palette.background)
                assertTrue("Body text AMOLED $style: %.2f (seed=$seed)".format(body), body >= 7.0)
                val secondary = contrastRatio(palette.secondaryText, palette.background)
                assertTrue("Secondary text AMOLED $style: %.2f (seed=$seed)".format(secondary), secondary >= 4.5)
                val accent = contrastRatio(palette.accent, palette.background)
                assertTrue("Accent AMOLED $style: %.2f (seed=$seed)".format(accent), accent >= 4.5)
            }
        }
    }

    @Test
    fun `ensureContrast lifts even the worst pairing`() {
        val yellowOnWhite = ensureContrast(Color(0xFFFFFF00), Color.White, minRatio = 4.5)
        assertTrue(contrastRatio(yellowOnWhite, Color.White) >= 4.5)

        val darkBlueOnBlack = ensureContrast(Color(0xFF000033), Color.Black, minRatio = 4.5)
        assertTrue(contrastRatio(darkBlueOnBlack, Color.Black) >= 4.5)
    }
}
