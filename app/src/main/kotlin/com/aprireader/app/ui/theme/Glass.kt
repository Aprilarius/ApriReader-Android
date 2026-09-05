package com.aprireader.app.ui.theme

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.core.graphics.withTranslation
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aprireader.app.data.prefs.DesignStyle

/**
 * Плотность стекла. Панель поверх текста должна быть плотнее карточки на
 * нейтральном фоне: под ней проходит контент, а буквы на ней всё равно обязаны
 * читаться.
 */
enum class GlassLevel {
    /** Панели поверх страницы книги: верхняя, нижняя, плеер озвучивания. */
    Chrome,

    /** Карточки на полке и в настройках. */
    Card,

    /** Листы и диалоги — самый плотный слой, под ним затемнение. */
    Sheet,
}

/**
 * Толщина стекла подобрана с учетом WCAG AA контрастности.
 */
fun GlassLevel.alphas(dark: Boolean): Pair<Float, Float> = when (this) {
    GlassLevel.Chrome -> if (dark) 0.90f to 0.84f else 0.92f to 0.86f
    GlassLevel.Card -> if (dark) 0.82f to 0.72f else 0.86f to 0.78f
    GlassLevel.Sheet -> if (dark) 0.94f to 0.90f else 0.95f to 0.92f
}

/**
 * Универсальный стилизованный модификатор поверхности (Liquid Glass, Glassmorphism, Solid, Neumorphism, Wood Library).
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape,
    level: GlassLevel = GlassLevel.Card,
    tint: Color = LocalAccent.current.color,
    elevation: Dp = when (level) {
        GlassLevel.Chrome -> 12.dp
        GlassLevel.Card -> 6.dp
        GlassLevel.Sheet -> 20.dp
    },
    border: Boolean = true,
): Modifier {
    val style = LocalDesignStyle.current
    val dark = isSystemInDarkTheme() || MaterialTheme.colorScheme.surface.isDarkSurface()
    val scheme = MaterialTheme.colorScheme
    val isPureBlack = scheme.surface == Color.Black || scheme.background == Color.Black
    val (topAlpha, bottomAlpha) = level.alphas(dark)

    val base = when (level) {
        GlassLevel.Chrome -> scheme.surfaceContainerHigh
        GlassLevel.Card -> scheme.surfaceContainer
        GlassLevel.Sheet -> scheme.surfaceContainerHigh
    }

    return when (style) {
        DesignStyle.SOLID_CLEAN -> {
            val solidBorder = if (isPureBlack) scheme.outlineVariant.copy(alpha = 0.85f) else scheme.outlineVariant.copy(alpha = 0.5f)
            this
                .shadow(elevation = elevation / 2, shape = shape, clip = false)
                .clip(shape)
                .background(base)
                .then(
                    if (border) Modifier.border(1.dp, solidBorder, shape)
                    else Modifier
                )
        }
        DesignStyle.GLASSMORPHISM -> {
            // Огранённая, прохладная пластина стекла — резче и мельче Liquid
            // Glass (форма приходит из designShapes), с бликом света в верхнем
            // левом углу поверх ровной изморози: это и есть отличительный жест
            // жанра, а не просто другой оттенок той же плашки.
            val frostTop = (if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.50f)).compositeOver(base).copy(alpha = if (dark) 0.78f else 0.86f)
            val frostBottom = base.copy(alpha = if (dark) 0.66f else 0.72f)
            val sheen = Brush.linearGradient(
                0f to (if (dark) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.60f)),
                0.45f to Color.Transparent,
                start = Offset.Zero,
                end = Offset.Infinite,
            )
            val rim = if (isPureBlack) Color.White.copy(alpha = 0.35f) else if (dark) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.85f)
            this
                .shadow(elevation = elevation, shape = shape, clip = false, ambientColor = tint.copy(alpha = 0.18f), spotColor = tint.copy(alpha = 0.14f))
                .clip(shape)
                .background(Brush.verticalGradient(listOf(frostTop, frostBottom)))
                .background(sheen)
                .then(
                    if (border) {
                        Modifier.border(
                            width = 1.dp,
                            brush = Brush.linearGradient(0f to rim, 0.6f to rim.copy(alpha = 0.04f), start = Offset.Zero, end = Offset.Infinite),
                            shape = shape,
                        )
                    } else {
                        Modifier
                    }
                )
        }
        DesignStyle.NEUMORPHISM -> {
            // Настоящий неоморфизм: одна плотная поверхность почти в цвет фона,
            // без бордюра, с глубиной только за счёт пары размытых теней —
            // светлой со стороны источника света и тёмной с противоположной.
            // Modifier.shadow умеет ровно одну тень с одной стороны, поэтому
            // раньше стиль ничем не отличался по факту, кроме оттенка: тут
            // рисуются оба силуэта вручную через BlurMaskFilter.
            val neuBase = if (dark) scheme.surfaceContainerLow else scheme.surfaceContainerHighest
            val highlight = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.95f)
            val shadowColor = if (dark) Color.Black.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.16f)
            this
                .neumorphicDualShadow(
                    shape = shape,
                    lightColor = highlight,
                    darkColor = shadowColor,
                    distance = elevation * 0.4f,
                    blur = elevation * 1.4f,
                )
                .clip(shape)
                .background(neuBase)
        }
        DesignStyle.WOOD_LIBRARY -> {
            // Как и у остального стеклянного семейства, оба стопа градиента
            // должны нести альфа-канал: без него `compositeOver` возвращает
            // непрозрачный цвет, и панель перестаёт быть стеклом — превращается
            // в сплошную заливку, хотя по названию и соседству с Liquid Glass
            // и Glassmorphism предполагается лёгкая прозрачность.
            val warmTint = Color(0xFF8D6E63)
            // Тёплый тон темнее нейтрального стекла, поэтому на тех же
            // альфах, что у Liquid Glass, светлый текст в тёмной теме на
            // Card-уровне у самой яркой подложки (белая страница) едва не
            // проваливает WCAG AA (см. GlassContrastTest) — плотность здесь
            // поднята чуть выше общего минимума стеклянного семейства.
            val woodTopAlpha = topAlpha.coerceAtLeast(0.90f)
            val woodBottomAlpha = bottomAlpha.coerceAtLeast(0.85f)
            val woodTop = warmTint.copy(alpha = if (dark) 0.16f else 0.09f).compositeOver(base).copy(alpha = woodTopAlpha)
            val woodBottom = base.copy(alpha = woodBottomAlpha)
            this
                .shadow(elevation = elevation, shape = shape, clip = false, ambientColor = warmTint.copy(alpha = 0.35f), spotColor = warmTint.copy(alpha = 0.25f))
                .clip(shape)
                .background(Brush.verticalGradient(listOf(woodTop, woodBottom)))
                .then(
                    if (border) Modifier.border(1.dp, Brush.verticalGradient(listOf(warmTint.copy(alpha = 0.4f), Color.Transparent)), shape)
                    else Modifier
                )
        }
        DesignStyle.LIQUID_GLASS -> {
            val tintStrength = if (isPureBlack) 0.12f else if (dark) 0.16f else 0.12f
            val glassTop = tint.copy(alpha = tintStrength).compositeOver(base).copy(alpha = if (isPureBlack) 0.92f else topAlpha)
            val glassBottom = base.copy(alpha = if (isPureBlack) 0.88f else bottomAlpha)

            val halo = if (isPureBlack) tint.copy(alpha = 0.35f).compositeOver(Color.White.copy(alpha = 0.22f))
                       else if (dark) Color.White.copy(alpha = 0.16f)
                       else Color.White.copy(alpha = 0.55f)
            val haloFade = if (isPureBlack) scheme.outlineVariant.copy(alpha = 0.6f)
                           else if (dark) Color.White.copy(alpha = 0.02f)
                           else Color.White.copy(alpha = 0.10f)

            this
                .shadow(
                    elevation = elevation,
                    shape = shape,
                    clip = false,
                    ambientColor = tint.copy(alpha = if (isPureBlack) 0.20f else 0.30f),
                    spotColor = tint.copy(alpha = if (isPureBlack) 0.15f else 0.24f),
                )
                .clip(shape)
                .background(Brush.verticalGradient(listOf(glassTop, glassBottom)))
                .then(
                    if (border) {
                        Modifier.border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(listOf(halo, haloFade)),
                            shape = shape,
                        )
                    } else {
                        Modifier
                    }
                )
        }
    }
}

/**
 * Двойная размытая тень «продавленного пластилина»: светлый силуэт формы,
 * сдвинутый к источнику света (вверх-влево), и тёмный — в противоположную
 * сторону. `Modifier.shadow` рисует только одну тень с одним источником, и
 * этого достаточно для стекла или обычной приподнятой карточки, но не для
 * неоморфизма — там глубина держится ровно на паре противоположных теней на
 * одноцветной поверхности.
 *
 * `BlurMaskFilter` не читается на аппаратно ускоренном слое рисования, поэтому
 * ровно над этим `drawBehind` слой принудительно переводится в офскрин-режим
 * композитинга ([CompositingStrategy.Offscreen]) — это стандартный обходной
 * путь Compose именно для такого случая.
 */
private fun Modifier.neumorphicDualShadow(
    shape: Shape,
    lightColor: Color,
    darkColor: Color,
    distance: Dp,
    blur: Dp,
): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply {
            when (outline) {
                is Outline.Rectangle -> addRect(outline.rect)
                is Outline.Rounded -> addRoundRect(outline.roundRect)
                is Outline.Generic -> addPath(outline.path)
            }
        }.asAndroidPath()

        val distancePx = distance.toPx()
        val blurPx = blur.toPx().coerceAtLeast(0.1f)

        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
            }
            val nativeCanvas = canvas.nativeCanvas

            paint.color = darkColor.toArgb()
            nativeCanvas.withTranslation(distancePx, distancePx) {
                drawPath(path, paint)
            }

            paint.color = lightColor.toArgb()
            nativeCanvas.withTranslation(-distancePx, -distancePx) {
                drawPath(path, paint)
            }
        }
    }

/**
 * Фон-«скайлайт» для Glassmorphism: два цветных пятна за пределами обычной
 * зоны контента, размытые там, где это поддерживает система. Это тот самый
 * узнаваемый жест жанра — цветной свет, будто просвечивающий сквозь
 * матированное стекло панелей поверх него — а не только оттенок панелей.
 * Для любого другого стиля не рисует ничего: место вызова остаётся тем же.
 */
@Composable
fun GlassmorphicBackdrop(modifier: Modifier = Modifier) {
    if (LocalDesignStyle.current != DesignStyle.GLASSMORPHISM) return

    val accent = LocalAccent.current.color
    val dark = isSystemInDarkTheme() || MaterialTheme.colorScheme.surface.isDarkSurface()
    val supportsBlur = remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
    val alpha = if (dark) 0.30f else 0.40f
    val cool = Color(0xFF5B8DEF)

    Box(modifier.clipToBounds()) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 70.dp, y = (-90).dp)
                .size(280.dp)
                .then(if (supportsBlur) Modifier.blur(80.dp) else Modifier)
                .background(Brush.radialGradient(listOf(accent.copy(alpha = alpha), Color.Transparent))),
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-80).dp, y = 110.dp)
                .size(320.dp)
                .then(if (supportsBlur) Modifier.blur(90.dp) else Modifier)
                .background(Brush.radialGradient(listOf(cool.copy(alpha = alpha * 0.9f), Color.Transparent))),
        )
    }
}

/**
 * Цветной ореол обложки — единственное место, где применяется настоящее
 * размытие. На Android 12 и новее это `Modifier.blur`; ниже он не поддержан
 * системой, и вместо него остаётся мягкий радиальный градиент акцента.
 */
@Composable
fun CoverBloom(
    coverPath: String?,
    accent: Color,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 48.dp,
) {
    val supportsBlur = remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
    val file = remember(coverPath) { coverPath?.let { java.io.File(it) }?.takeIf { it.exists() } }

    Box(modifier) {
        if (supportsBlur && file != null) {
            coil.compose.AsyncImage(
                model = file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(blurRadius),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = if (supportsBlur && file != null) 0.28f else 0.42f), Color.Transparent),
                    )
                )
        )
    }
}

/** Тёмная ли поверхность — по воспринимаемой яркости, а не по флагу темы. */
fun Color.isDarkSurface(): Boolean = relativeLuminance() < 0.5

/**
 * Композит полупрозрачного стекла с подложкой.
 */
fun glassComposite(base: Color, alpha: Float, backdrop: Color): Color =
    base.copy(alpha = alpha).compositeOver(backdrop)
