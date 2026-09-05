package com.aprireader.app

import androidx.compose.ui.graphics.Color
import com.aprireader.app.data.prefs.DesignStyle
import com.aprireader.app.ui.theme.GlassLevel
import com.aprireader.app.ui.theme.alphas
import com.aprireader.app.ui.theme.buildColorScheme
import com.aprireader.app.ui.theme.contrastRatio
import com.aprireader.app.ui.theme.glassComposite
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Прозрачность не должна стоить читаемости.
 *
 * Стеклянная панель лежит поверх произвольного содержимого: страницы книги
 * любого стиля, обложки, изображения. Тест берёт самые невыгодные подложки и
 * проверяет, что текст на композите панели всё ещё проходит WCAG AA. Если
 * захочется сделать стекло прозрачнее — сначала здесь.
 *
 * Проверяются все пять стилей дизайна, а не только Liquid Glass: у каждого
 * свои цвета `surfaceContainer*` (см. `buildColorScheme`), и до этой правки
 * тест молчаливо проверял только тот, что используется по умолчанию —
 * Glassmorphism и Neumorphism ни разу не прогонялись через WCAG AA.
 */
class GlassContrastTest {

    private val seeds = listOf(
        Color(0xFFC9A227), // фирменная латунь
        Color(0xFF00FF00),
        Color(0xFFFFFF00),
        Color(0xFF102A6B),
        Color(0xFF7A7A7A),
        Color(0xFFFF00FF),
    )

    /** Что может оказаться под панелью: белая страница, чёрная, бумага, сепия, яркая обложка. */
    private val backdrops = listOf(
        Color.White,
        Color.Black,
        Color(0xFFF8F3E9),
        Color(0xFFF1E2C8),
        Color(0xFF1C1D20),
        Color(0xFFFF3B30),
        Color(0xFF00E5FF),
    )

    /**
     * Реальная непрозрачность панели зависит от стиля, а не только от уровня:
     * Solid Clean и Neumorphism рисуют сплошную заливку (`background(base)`
     * без альфа-канала — см. `liquidGlass()`), подложка страницы под ними не
     * просвечивает вообще. Liquid Glass, Glassmorphism и Wood Library —
     * настоящее стекло, там альфа из [GlassLevel.alphas] и есть.
     */
    private fun bottomAlphaFor(style: DesignStyle, level: GlassLevel, dark: Boolean): Float =
        when (style) {
            DesignStyle.SOLID_CLEAN, DesignStyle.NEUMORPHISM -> 1f
            // Тёплый тон темнее нейтрального стекла — плотность поднята
            // относительно общего минимума, см. комментарий в Glass.kt.
            DesignStyle.WOOD_LIBRARY -> level.alphas(dark).second.coerceAtLeast(0.85f)
            else -> level.alphas(dark).second
        }

    @Test
    fun `text on glass stays readable over any backdrop`() {
        for (style in DesignStyle.entries) {
            for (seed in seeds) {
                for (dark in listOf(false, true)) {
                    val scheme = buildColorScheme(seed, dark, designStyle = style)
                    for (level in GlassLevel.entries) {
                        // Берём нижнюю, самую прозрачную часть градиента — худший случай.
                        val bottomAlpha = bottomAlphaFor(style, level, dark)
                        val base = when (level) {
                            GlassLevel.Chrome, GlassLevel.Sheet -> scheme.surfaceContainerHigh
                            GlassLevel.Card -> scheme.surfaceContainer
                        }
                        for (backdrop in backdrops) {
                            val composite = glassComposite(base, bottomAlpha, backdrop)
                            val ratio = contrastRatio(scheme.onSurface, composite)
                            assertTrue(
                                "Текст на стекле $style/$level (dark=$dark, seed=$seed) над $backdrop: %.2f".format(ratio),
                                ratio >= 4.5,
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `secondary text on glass keeps at least three to one`() {
        for (style in DesignStyle.entries) {
            for (seed in seeds) {
                for (dark in listOf(false, true)) {
                    val scheme = buildColorScheme(seed, dark, designStyle = style)
                    val bottomAlpha = bottomAlphaFor(style, GlassLevel.Card, dark)
                    for (backdrop in backdrops) {
                        val composite = glassComposite(scheme.surfaceContainer, bottomAlpha, backdrop)
                        val ratio = contrastRatio(scheme.onSurfaceVariant, composite)
                        assertTrue(
                            "Вторичный текст на стекле $style (dark=$dark, seed=$seed) над $backdrop: %.2f".format(ratio),
                            ratio >= 3.0,
                        )
                    }
                }
            }
        }
    }
}
