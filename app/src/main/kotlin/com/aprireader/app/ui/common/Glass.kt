package com.aprireader.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.aprireader.app.ui.theme.GlassLevel
import com.aprireader.app.ui.theme.LocalAccent
import com.aprireader.app.ui.theme.SquircleLg
import com.aprireader.app.ui.theme.isDarkSurface
import com.aprireader.app.ui.theme.liquidGlass

/**
 * Стеклянная панель — базовый строительный блок интерфейса.
 *
 * Всё, что лежит поверх содержимого (панели чтения, карточки полки, плеер
 * озвучивания), собрано из неё, поэтому плотность стекла и блик по кромке
 * настраиваются в одном месте.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = SquircleLg,
    level: GlassLevel = GlassLevel.Card,
    tint: Color = LocalAccent.current.color,
    border: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.liquidGlass(shape = shape, level = level, tint = tint, border = border),
        content = content,
    )
}

/**
 * Тонкая светящаяся линия — тот же блик, что и на кромке стекла, но как
 * разделитель. Заменяет привычную серую черту: на прозрачных поверхностях
 * сплошная линия выглядит грязно.
 */
@Composable
fun SpecularDivider(modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.surface.isDarkSurface()
    val highlight = if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, highlight, highlight, Color.Transparent),
                )
            ),
    )
}

/**
 * Градиент, под которым контент уходит за стеклянную панель.
 *
 * Нужен там, где панель полупрозрачная: без него строка текста упирается в
 * кромку стекла и выглядит обрезанной.
 */
@Composable
fun ScrimEdge(modifier: Modifier = Modifier, fromTop: Boolean = true, height: androidx.compose.ui.unit.Dp = 48.dp) {
    val surface = MaterialTheme.colorScheme.surface
    val colors = if (fromTop) {
        listOf(surface, surface.copy(alpha = 0f))
    } else {
        listOf(surface.copy(alpha = 0f), surface)
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(Brush.verticalGradient(colors)),
    )
}

/**
 * Ореол обложки под карточкой. Обёртка над [com.aprireader.app.ui.theme.CoverBloom]
 * с именем, по которому видно, что это фон, а не изображение книги.
 */
@Composable
fun CoverBloomHost(
    coverPath: String?,
    accent: Color,
    modifier: Modifier = Modifier,
) = com.aprireader.app.ui.theme.CoverBloom(coverPath = coverPath, accent = accent, modifier = modifier)
