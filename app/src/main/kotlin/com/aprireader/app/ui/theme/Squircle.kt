package com.aprireader.app.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.aprireader.app.data.prefs.DesignStyle

/**
 * Скруглённый прямоугольник с непрерывной кривизной — «squircle».
 *
 * Обычное скругление стыкует прямую грань с дугой окружности: кривизна в точке
 * стыка меняется скачком, и глаз читает это как уголок с фаской. Здесь каждый
 * угол — кубическая кривая Безье, у которой касательные на концах лежат ровно
 * вдоль граней, поэтому переход грань → угол происходит плавно.
 *
 * Наследование от [CornerBasedShape] сделано ради Material 3: тема принимает
 * только такие формы, и благодаря этому кнопки, чипы, диалоги и меню получают
 * сглаженные углы без правки каждого места вызова.
 *
 * [smoothing] задаёт, насколько далеко угол «съедает» грань. 0 почти неотличим
 * от обычного скругления; у Liquid Glass это 0.6; у Neumorphism поднято до
 * 0.85 — мягче, «пластилиновее»; у Glassmorphism опущено до 0.3 — резче,
 * читается как огранённая грань стекла, а не органичная капля.
 */
class SquircleShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
    val smoothing: Float = DEFAULT_SMOOTHING,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        if (topStart + topEnd + bottomEnd + bottomStart == 0f) {
            return Outline.Rectangle(size.toRect())
        }
        val ltr = layoutDirection == LayoutDirection.Ltr
        return Outline.Generic(
            squirclePath(
                size = size,
                topLeft = if (ltr) topStart else topEnd,
                topRight = if (ltr) topEnd else topStart,
                bottomRight = if (ltr) bottomEnd else bottomStart,
                bottomLeft = if (ltr) bottomStart else bottomEnd,
                smoothing = smoothing,
            )
        )
    }

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): CornerBasedShape = SquircleShape(topStart, topEnd, bottomEnd, bottomStart, smoothing)

    override fun toString(): String = "SquircleShape(smoothing=$smoothing)"

    companion object {
        const val DEFAULT_SMOOTHING = 0.6f
    }
}

fun SquircleShape(radius: Dp, smoothing: Float = SquircleShape.DEFAULT_SMOOTHING): SquircleShape =
    SquircleShape(
        topStart = CornerSize(radius),
        topEnd = CornerSize(radius),
        bottomEnd = CornerSize(radius),
        bottomStart = CornerSize(radius),
        smoothing = smoothing,
    )

/** Сглаженные углы только сверху — для листов, выезжающих снизу. */
fun SquircleTopShape(radius: Dp, smoothing: Float = SquircleShape.DEFAULT_SMOOTHING): SquircleShape =
    SquircleShape(
        topStart = CornerSize(radius),
        topEnd = CornerSize(radius),
        bottomEnd = CornerSize(0.dp),
        bottomStart = CornerSize(0.dp),
        smoothing = smoothing,
    )

/** Сглаженные углы только со стороны, противоположной корешку — для шторки навигации. */
fun SquircleTrailingShape(radius: Dp, smoothing: Float = SquircleShape.DEFAULT_SMOOTHING): SquircleShape =
    SquircleShape(
        topStart = CornerSize(0.dp),
        topEnd = CornerSize(radius),
        bottomEnd = CornerSize(radius),
        bottomStart = CornerSize(0.dp),
        smoothing = smoothing,
    )

/**
 * Контур со сглаженными углами.
 *
 * Для каждого угла берутся две точки на гранях на расстоянии «касательной» от
 * вершины, а управляющие точки кубической кривой ставятся на отрезках
 * «точка → вершина». Такое расположение гарантирует, что кривая входит в грань
 * по касательной — ради этой непрерывности всё и затевалось.
 */
private fun squirclePath(
    size: Size,
    topLeft: Float,
    topRight: Float,
    bottomRight: Float,
    bottomLeft: Float,
    smoothing: Float,
): Path {
    val width = size.width
    val height = size.height
    val path = Path()
    if (width <= 0f || height <= 0f) return path

    val limit = minOf(width, height) / 2f
    fun tangent(radius: Float) = (radius * (1f + smoothing)).coerceIn(0f, limit)

    val tl = tangent(topLeft)
    val tr = tangent(topRight)
    val br = tangent(bottomRight)
    val bl = tangent(bottomLeft)

    // Доля отрезка «точка на грани → вершина», на которой стоит управляющая точка.
    val grip = 1f - 0.32f / (1f + smoothing)

    fun corner(
        fromX: Float, fromY: Float,
        cornerX: Float, cornerY: Float,
        toX: Float, toY: Float,
    ) {
        path.cubicTo(
            x1 = fromX + (cornerX - fromX) * grip,
            y1 = fromY + (cornerY - fromY) * grip,
            x2 = cornerX + (toX - cornerX) * (1f - grip),
            y2 = cornerY + (toY - cornerY) * (1f - grip),
            x3 = toX,
            y3 = toY,
        )
    }

    path.moveTo(tl, 0f)
    path.lineTo(width - tr, 0f)
    corner(width - tr, 0f, width, 0f, width, tr)

    path.lineTo(width, height - br)
    corner(width, height - br, width, height, width - br, height)

    path.lineTo(bl, height)
    corner(bl, height, 0f, height, 0f, height - bl)

    path.lineTo(0f, tl)
    corner(0f, tl, 0f, 0f, tl, 0f)

    path.close()
    return path
}

/**
 * Геометрия одного стиля дизайна: радиус и сглаживание для пяти размеров формы.
 *
 * Радиус и сглаживание — не менее значимая часть личности стиля, чем цвет.
 * Neumorphism крупнее и мягче обычного (пластилин), Glassmorphism — меньше и
 * жёстче (огранённая пластина стекла); Liquid Glass, Solid Clean и Wood
 * Library держат исходную геометрию проекта без изменений.
 */
private data class ShapeScale(val xs: Dp, val sm: Dp, val md: Dp, val lg: Dp, val xl: Dp, val sheet: Dp, val smoothing: Float)

private val defaultScale = ShapeScale(10.dp, 14.dp, 20.dp, 28.dp, 36.dp, 32.dp, SquircleShape.DEFAULT_SMOOTHING)
private val neumorphismScale = ShapeScale(16.dp, 22.dp, 30.dp, 38.dp, 46.dp, 40.dp, 0.85f)
private val glassmorphismScale = ShapeScale(8.dp, 12.dp, 16.dp, 22.dp, 28.dp, 26.dp, 0.30f)

private fun ShapeScale.forStyle(style: DesignStyle): ShapeScale = when (style) {
    DesignStyle.NEUMORPHISM -> neumorphismScale
    DesignStyle.GLASSMORPHISM -> glassmorphismScale
    else -> this
}

/**
 * Формы приложения — единый набор, но зависящий от выбранного стиля дизайна.
 *
 * Раньше это были статические константы: смена стиля в настройках красила
 * фон, но ни одна кнопка, чип, диалог или карточка не меняли геометрию — вся
 * разница сводилась к оттенку. Теперь это вычисляемые свойства, читающие
 * [LocalDesignStyle]: то же самое обращение `SquircleSm` в месте вызова, но
 * форма реально меняется вместе со стилем — без правки полутора сотен мест,
 * где эти константы уже используются.
 */
val SquircleXs: SquircleShape
    @Composable get() = defaultScale.forStyle(LocalDesignStyle.current).let { SquircleShape(it.xs, it.smoothing) }

val SquircleSm: SquircleShape
    @Composable get() = defaultScale.forStyle(LocalDesignStyle.current).let { SquircleShape(it.sm, it.smoothing) }

val SquircleMd: SquircleShape
    @Composable get() = defaultScale.forStyle(LocalDesignStyle.current).let { SquircleShape(it.md, it.smoothing) }

val SquircleLg: SquircleShape
    @Composable get() = defaultScale.forStyle(LocalDesignStyle.current).let { SquircleShape(it.lg, it.smoothing) }

val SquircleXl: SquircleShape
    @Composable get() = defaultScale.forStyle(LocalDesignStyle.current).let { SquircleShape(it.xl, it.smoothing) }

val SquircleSheet: SquircleShape
    @Composable get() = defaultScale.forStyle(LocalDesignStyle.current).let { SquircleTopShape(it.sheet, it.smoothing) }

/** Форма шторки навигации — тот же XL-радиус, но скруглён только край, противоположный корешку. */
val SquircleDrawer: SquircleShape
    @Composable get() = defaultScale.forStyle(LocalDesignStyle.current).let { SquircleTrailingShape(it.xl, it.smoothing) }

/** Пилюля: сглаживание здесь не нужно — при полукруглых торцах его не видно. */
val SquirclePill = SquircleShape(999.dp, smoothing = 0f)
