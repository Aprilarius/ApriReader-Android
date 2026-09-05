package com.aprireader.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.aprireader.app.R
import com.aprireader.app.data.prefs.DesignStyle
import com.aprireader.app.data.prefs.ReaderPageStyle

/**
 * Фирменное семя ApriReader — тёплая латунь.
 */
val ApriBrassSeed = Color(0xFFC9A227)

data class AccentPreset(val nameRes: Int, val color: Color)

/** Готовые варианты акцента для ручного выбора. */
val AccentPresets: List<AccentPreset> = listOf(
    AccentPreset(R.string.accent_brass, Color(0xFFC9A227)),
    AccentPreset(R.string.accent_terracotta, Color(0xFFB4552D)),
    AccentPreset(R.string.accent_garnet, Color(0xFF9E2B37)),
    AccentPreset(R.string.accent_plum, Color(0xFF7A3E8F)),
    AccentPreset(R.string.accent_indigo, Color(0xFF3A4E9B)),
    AccentPreset(R.string.accent_teal, Color(0xFF17726B)),
    AccentPreset(R.string.accent_moss, Color(0xFF4B6B2A)),
    AccentPreset(R.string.accent_graphite, Color(0xFF5A5F66)),
)

/**
 * Палитра цветов для экрана чтения книги.
 */
data class ReaderPalette(
    val background: Color,
    val text: Color,
    val secondaryText: Color,
    val accent: Color,
    val dark: Boolean,
    val selection: Color = accent.copy(alpha = 0.28f),
)

fun readerPalette(
    style: ReaderPageStyle,
    scheme: ColorScheme,
    accent: Color,
    darkTheme: Boolean,
    pureBlack: Boolean = false,
): ReaderPalette = when (style) {
    ReaderPageStyle.FOLLOW_THEME -> {
        val bg = if (pureBlack) Color.Black else scheme.surface
        val text = if (pureBlack) Color(0xFFE2E5EC) else scheme.onSurface
        val secondary = if (pureBlack) Color(0xFF8E95A2) else scheme.onSurfaceVariant
        val safeAccent = ensureContrast(accent, bg, minRatio = 4.5)
        ReaderPalette(
            background = bg,
            text = text,
            secondaryText = secondary,
            accent = safeAccent,
            dark = darkTheme,
            selection = safeAccent.copy(alpha = 0.28f),
        )
    }
    ReaderPageStyle.PAPER -> {
        val bg = Color(0xFFF6EFE2)
        val safeAccent = ensureContrast(accent, bg, minRatio = 4.5)
        ReaderPalette(
            background = bg,
            text = Color(0xFF2C241B),
            secondaryText = Color(0xFF6B5C4B),
            accent = safeAccent,
            dark = false,
            selection = safeAccent.copy(alpha = 0.28f),
        )
    }
    ReaderPageStyle.SEPIA -> {
        val bg = Color(0xFFEDE0CC)
        val safeAccent = ensureContrast(accent, bg, minRatio = 4.5)
        ReaderPalette(
            background = bg,
            text = Color(0xFF382A1B),
            secondaryText = Color(0xFF735C45),
            accent = safeAccent,
            dark = false,
            selection = safeAccent.copy(alpha = 0.28f),
        )
    }
    ReaderPageStyle.GRAPHITE -> {
        val bg = Color(0xFF24272D)
        val safeAccent = ensureContrast(accent, bg, minRatio = 4.5)
        ReaderPalette(
            background = bg,
            text = Color(0xFFDCE0E8),
            secondaryText = Color(0xFF8B92A0),
            accent = safeAccent,
            dark = true,
            selection = safeAccent.copy(alpha = 0.28f),
        )
    }
    ReaderPageStyle.BLACK -> {
        val bg = Color.Black
        val safeAccent = ensureContrast(accent, bg, minRatio = 4.5)
        ReaderPalette(
            background = bg,
            text = Color(0xFFD4D7DE),
            secondaryText = Color(0xFF808590),
            accent = safeAccent,
            dark = true,
            selection = safeAccent.copy(alpha = 0.28f),
        )
    }
}

/**
 * Собирает полную схему Material 3 с учетом выбранного стиля дизайна
 * (Liquid Glass, Glassmorphism, Solid Clean, Neumorphism, Wood Library)
 * и режима глубокого AMOLED или Material Dark.
 */
fun buildColorScheme(
    seed: Color,
    dark: Boolean,
    pureBlack: Boolean = false,
    designStyle: DesignStyle = DesignStyle.LIQUID_GLASS,
): ColorScheme {
    val primary = TonalPalette.from(seed)
    val secondary = TonalPalette.from(seed)
    val neutral = when (designStyle) {
        DesignStyle.WOOD_LIBRARY -> TonalPalette.from(Color(0xFFC67D3B))
        DesignStyle.GLASSMORPHISM -> TonalPalette.from(Color(0xFF3B5E8C))
        DesignStyle.NEUMORPHISM -> TonalPalette.from(Color(0xFF5C6B73))
        DesignStyle.SOLID_CLEAN -> TonalPalette.neutralFrom(Color.Gray, chroma = 0.0)
        DesignStyle.LIQUID_GLASS -> TonalPalette.neutralFrom(seed, chroma = 4.0)
    }
    val neutralVariant = TonalPalette.neutralFrom(seed, chroma = 9.0)
    val error = TonalPalette.from(Color(0xFFB3261E))

    return if (dark) {
        if (pureBlack) {
            // Тёмная AMOLED тема: 100% чистый чёрный фон, элегантные обсидиановые контейнеры
            darkColorScheme(
                primary = primary.tone(82),
                onPrimary = primary.tone(15),
                primaryContainer = primary.toneWithChroma(26, 0.55),
                onPrimaryContainer = primary.tone(92),
                inversePrimary = primary.tone(40),

                secondary = secondary.toneWithChroma(82, 0.45),
                onSecondary = secondary.toneWithChroma(15, 0.45),
                secondaryContainer = secondary.toneWithChroma(26, 0.45),
                onSecondaryContainer = secondary.toneWithChroma(92, 0.45),

                tertiary = primary.toneWithChroma(82, 0.7),
                onTertiary = primary.toneWithChroma(15, 0.7),
                tertiaryContainer = primary.toneWithChroma(26, 0.7),
                onTertiaryContainer = primary.toneWithChroma(92, 0.7),

                background = Color.Black,
                onBackground = Color(0xFFF1F3F6),
                surface = Color.Black,
                onSurface = Color(0xFFF1F3F6),
                surfaceVariant = Color(0xFF14161E),
                onSurfaceVariant = Color(0xFFA2A7B5),
                surfaceTint = primary.tone(82),
                inverseSurface = Color(0xFFF1F3F6),
                inverseOnSurface = Color(0xFF121316),

                surfaceDim = Color.Black,
                surfaceBright = Color(0xFF1E212A),
                surfaceContainerLowest = Color.Black,
                surfaceContainerLow = Color(0xFF090A0E),
                surfaceContainer = Color(0xFF111319),
                surfaceContainerHigh = Color(0xFF181B23),
                surfaceContainerHighest = Color(0xFF222530),

                outline = Color(0xFF4B5060),
                outlineVariant = Color(0xFF2C303C),
                error = error.tone(80),
                onError = error.tone(20),
                errorContainer = error.tone(30),
                onErrorContainer = error.tone(90),
                scrim = Color.Black,
            )
        } else {
            // Тёмная Material тема: мягкие графитовые и угольные тона с выразительной глубиной
            val bg = when (designStyle) {
                DesignStyle.WOOD_LIBRARY -> Color(0xFF1D1511)
                DesignStyle.GLASSMORPHISM -> Color(0xFF0D121B)
                DesignStyle.NEUMORPHISM -> Color(0xFF1C2028)
                DesignStyle.SOLID_CLEAN -> Color(0xFF121212)
                else -> neutral.tone(6)
            }

            val surface = when (designStyle) {
                DesignStyle.WOOD_LIBRARY -> Color(0xFF261C16)
                DesignStyle.GLASSMORPHISM -> Color(0xFF131B27)
                DesignStyle.NEUMORPHISM -> Color(0xFF222731)
                DesignStyle.SOLID_CLEAN -> Color(0xFF181818)
                else -> neutral.tone(6)
            }

            val container = when (designStyle) {
                DesignStyle.WOOD_LIBRARY -> Color(0xFF382921)
                DesignStyle.GLASSMORPHISM -> Color(0xFF1C2839)
                DesignStyle.NEUMORPHISM -> Color(0xFF2B323F)
                DesignStyle.SOLID_CLEAN -> Color(0xFF222222)
                else -> neutral.tone(12)
            }

            darkColorScheme(
                primary = primary.tone(80),
                onPrimary = primary.tone(20),
                primaryContainer = primary.tone(30),
                onPrimaryContainer = primary.tone(90),
                inversePrimary = primary.tone(40),

                secondary = secondary.toneWithChroma(80, 0.45),
                onSecondary = secondary.toneWithChroma(20, 0.45),
                secondaryContainer = secondary.toneWithChroma(30, 0.45),
                onSecondaryContainer = secondary.toneWithChroma(90, 0.45),

                tertiary = primary.toneWithChroma(80, 0.7),
                onTertiary = primary.toneWithChroma(20, 0.7),
                tertiaryContainer = primary.toneWithChroma(30, 0.7),
                onTertiaryContainer = primary.toneWithChroma(90, 0.7),

                background = bg,
                onBackground = neutral.tone(90),
                surface = surface,
                onSurface = neutral.tone(90),
                surfaceVariant = neutralVariant.tone(30),
                onSurfaceVariant = neutralVariant.tone(80),
                surfaceTint = primary.tone(80),
                inverseSurface = neutral.tone(90),
                inverseOnSurface = neutral.tone(20),

                surfaceDim = bg,
                surfaceBright = neutral.tone(24),
                surfaceContainerLowest = neutral.tone(4),
                surfaceContainerLow = neutral.tone(10),
                surfaceContainer = container,
                surfaceContainerHigh = when (designStyle) {
                    DesignStyle.WOOD_LIBRARY -> Color(0xFF45332A)
                    DesignStyle.GLASSMORPHISM -> Color(0xFF24344A)
                    DesignStyle.NEUMORPHISM -> Color(0xFF343C4C)
                    DesignStyle.SOLID_CLEAN -> Color(0xFF2C2C2C)
                    else -> neutral.tone(17)
                },
                surfaceContainerHighest = neutral.tone(22),

                outline = neutralVariant.tone(60),
                outlineVariant = neutralVariant.tone(30),
                error = error.tone(80),
                onError = error.tone(20),
                errorContainer = error.tone(30),
                onErrorContainer = error.tone(90),
                scrim = Color.Black,
            )
        }
    } else {
        val bg = when (designStyle) {
            DesignStyle.WOOD_LIBRARY -> Color(0xFFF7F1EB)
            DesignStyle.GLASSMORPHISM -> Color(0xFFEEF4FA)
            DesignStyle.NEUMORPHISM -> Color(0xFFE2E8F0)
            DesignStyle.SOLID_CLEAN -> Color(0xFFFFFFFF)
            DesignStyle.LIQUID_GLASS -> Color(0xFFF8FAFD)
        }

        val surface = when (designStyle) {
            DesignStyle.WOOD_LIBRARY -> Color(0xFFF2ECE4)
            DesignStyle.GLASSMORPHISM -> Color(0xFFE6EFF7)
            DesignStyle.NEUMORPHISM -> Color(0xFFD9E2ED)
            DesignStyle.SOLID_CLEAN -> Color(0xFFFFFFFF)
            DesignStyle.LIQUID_GLASS -> Color(0xFFF4F7FB)
        }

        val container = when (designStyle) {
            DesignStyle.WOOD_LIBRARY -> Color(0xFFEADBCE)
            DesignStyle.GLASSMORPHISM -> Color(0xFFD6E4F0)
            DesignStyle.NEUMORPHISM -> Color(0xFFCCD6E3)
            DesignStyle.SOLID_CLEAN -> Color(0xFFF2F2F2)
            DesignStyle.LIQUID_GLASS -> neutral.tone(94)
        }

        lightColorScheme(
            primary = primary.tone(40),
            onPrimary = primary.tone(100),
            primaryContainer = primary.tone(90),
            onPrimaryContainer = primary.tone(10),
            inversePrimary = primary.tone(80),

            secondary = secondary.toneWithChroma(40, 0.45),
            onSecondary = secondary.toneWithChroma(100, 0.45),
            secondaryContainer = secondary.toneWithChroma(90, 0.45),
            onSecondaryContainer = secondary.toneWithChroma(10, 0.45),

            tertiary = primary.toneWithChroma(40, 0.7),
            onTertiary = primary.toneWithChroma(100, 0.7),
            tertiaryContainer = primary.toneWithChroma(90, 0.7),
            onTertiaryContainer = primary.toneWithChroma(10, 0.7),

            background = bg,
            onBackground = neutral.tone(10),
            surface = surface,
            onSurface = neutral.tone(10),
            surfaceVariant = neutralVariant.tone(92),
            onSurfaceVariant = neutralVariant.tone(30),
            surfaceTint = primary.tone(40),
            inverseSurface = neutral.tone(20),
            inverseOnSurface = neutral.tone(95),

            surfaceDim = neutral.tone(87),
            surfaceBright = neutral.tone(99),
            surfaceContainerLowest = neutral.tone(100),
            surfaceContainerLow = neutral.tone(96),
            surfaceContainer = container,
            surfaceContainerHigh = when (designStyle) {
                DesignStyle.WOOD_LIBRARY -> Color(0xFFE0CEBE)
                DesignStyle.GLASSMORPHISM -> Color(0xFFC7DAE9)
                DesignStyle.NEUMORPHISM -> Color(0xFFBFCBD9)
                DesignStyle.SOLID_CLEAN -> Color(0xFFE8E8E8)
                DesignStyle.LIQUID_GLASS -> neutral.tone(92)
            },
            surfaceContainerHighest = neutral.tone(90),

            outline = neutralVariant.tone(50),
            outlineVariant = neutralVariant.tone(80),
            error = error.tone(40),
            onError = error.tone(100),
            errorContainer = error.tone(90),
            onErrorContainer = error.tone(10),
            scrim = Color.Black,
        )
    }
}
