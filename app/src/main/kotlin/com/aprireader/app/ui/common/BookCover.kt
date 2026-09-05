package com.aprireader.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.aprireader.app.domain.Book
import com.aprireader.app.ui.theme.CoverShape
import com.aprireader.app.ui.theme.LiterataFamily
import com.aprireader.app.ui.theme.TonalPalette
import com.aprireader.app.ui.theme.onColorFor
import java.io.File

/**
 * Обложка книги.
 *
 * Если файл обложки есть — показывается он. Если нет, рисуется типографическая
 * обложка на основе акцента книги: это заметно лучше серого прямоугольника с
 * иконкой и сохраняет полку визуально цельной.
 */
@Composable
fun BookCover(
    book: Book,
    modifier: Modifier = Modifier,
    shape: Shape = CoverShape,
    showTitleFallback: Boolean = true,
) {
    val context = LocalContext.current
    val hasCover = remember(book.coverPath) { book.coverPath?.let { File(it).exists() } == true }

    Box(
        modifier = modifier
            .aspectRatio(COVER_ASPECT)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), shape)
            .semantics { contentDescription = book.title },
    ) {
        if (hasCover && !book.coverPath.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(book.coverPath))

                    .crossfade(true)
                .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (showTitleFallback) {
            GeneratedCover(
                title = book.title,
                author = book.authorLine,
                seedColor = book.effectiveAccent,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh))
        }

        // Реалистичная тень корешка книги слева (3D Spine Crease)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(8.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.10f),
                            Color.Transparent,
                        )
                    )
                ),
        )

        // Тонкий срез страниц справа
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(1.5.dp)
                .background(Color.Black.copy(alpha = 0.18f)),
        )

        if (book.format.isAudio) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color.Black.copy(alpha = 0.70f)),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Rounded.Headphones,
                    contentDescription = "Аудиокнига",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

/** Типографическая обложка для книг без картинки. */
@Composable
fun GeneratedCover(
    title: String,
    author: String,
    seedColor: Int?,
    modifier: Modifier = Modifier,
) {
    val seed = remember(seedColor, title) {
        seedColor?.let { Color(it) } ?: stableColorFor(title)
    }
    val palette = remember(seed) { TonalPalette.from(seed) }
    val top = palette.tone(34)
    val bottom = palette.tone(18)
    val ink = onColorFor(bottom)

    Box(
        modifier = modifier.background(Brush.linearGradient(listOf(top, bottom))),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 9.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                Modifier
                    .width(22.dp)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(ink.copy(alpha = 0.7f)),
            )
            Column {
                Text(
                    text = title,
                    color = ink,
                    fontFamily = LiterataFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                if (author.isNotBlank()) {
                    Text(
                        text = author,
                        color = ink.copy(alpha = 0.72f),
                        fontFamily = LiterataFamily,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

const val COVER_ASPECT = 2f / 3f

/** Устойчивый цвет из названия: одна и та же книга всегда выглядит одинаково. */
fun stableColorFor(text: String): Color {
    var hash = 7L
    for (char in text) hash = hash * 31 + char.code
    val hue = ((hash % 360) + 360) % 360
    return Color.hsl(hue.toFloat(), 0.45f, 0.42f)
}
