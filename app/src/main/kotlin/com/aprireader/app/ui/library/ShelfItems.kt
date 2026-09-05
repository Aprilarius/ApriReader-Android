package com.aprireader.app.ui.library

import com.aprireader.app.ui.theme.isDarkSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.aprireader.app.R
import com.aprireader.app.domain.Book
import com.aprireader.app.ui.common.BookCover
import com.aprireader.app.ui.common.COVER_ASPECT
import com.aprireader.app.ui.common.CoverBloomHost
import com.aprireader.app.ui.common.GlassPanel
import com.aprireader.app.ui.common.sharedCover
import com.aprireader.app.ui.common.sharedKey
import com.aprireader.app.ui.theme.CoverShape
import com.aprireader.app.ui.theme.SquirclePill
import com.aprireader.app.ui.theme.SquircleSm
import com.aprireader.app.ui.theme.SquircleXl
import com.aprireader.app.ui.theme.SquircleXs

@Composable
private fun SelectionBadge(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.55f),
        border = if (isSelected) null else BorderStroke(1.5.dp, Color.White.copy(alpha = 0.85f)),
        shadowElevation = 4.dp,
        modifier = modifier.size(24.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * Ряд книжной полки в стиле eReader Prestigio, адаптированный под современный дизайн.
 * Настраиваемое количество книг (2-5) на трехмерной деревянной полке с тенями и фактурой.
 */
@Composable
fun BookshelfRow(
    books: List<Book>,
    columns: Int = 3,
    onClick: (Book) -> Unit,
    onLongClick: (Book) -> Unit,
    isSelectionMode: Boolean = false,
    selectedBookIds: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
) {
    val hPadding = (6 - columns).coerceIn(3, 8).dp
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            for (i in 0 until columns) {
                val book = books.getOrNull(i)
                if (book != null) {
                    BookshelfBookItem(
                        book = book,
                        onClick = { onClick(book) },
                        onLongClick = { onLongClick(book) },
                        isSelectionMode = isSelectionMode,
                        isSelected = selectedBookIds.contains(book.id),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = hPadding),
                    )
                } else {
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = hPadding),
                    )
                }
            }
        }

        // 3D-деревянная планка полки
        BookshelfLedge()
    }
}

/** Трехмерная деревянная полка с верхней гранью, торцом и глубокой тенью. */
@Composable
fun BookshelfLedge(modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme() || MaterialTheme.colorScheme.surface.isDarkSurface()
    val style = com.aprireader.app.ui.theme.LocalDesignStyle.current
    val accent = com.aprireader.app.ui.theme.LocalAccent.current.color

    when (style) {
        com.aprireader.app.data.prefs.DesignStyle.WOOD_LIBRARY -> {
            val shelfTopColor = if (isDark) Color(0xFF4A3425) else Color(0xFFD9B489)
            val shelfFaceColor = if (isDark) Color(0xFF2C1E15) else Color(0xFFAA8252)
            val shelfBottomEdge = if (isDark) Color(0xFF19100B) else Color(0xFF724F2A)

            Column(modifier = modifier.fillMaxWidth().padding(top = 1.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(Brush.verticalGradient(listOf(shelfTopColor.copy(alpha = 0.95f), shelfTopColor.copy(alpha = 0.65f))))
                        .border(width = 0.5.dp, color = Color.White.copy(alpha = if (isDark) 0.15f else 0.40f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(11.dp)
                        .background(Brush.verticalGradient(listOf(shelfFaceColor, shelfBottomEdge)))
                ) {
                    Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(0.8.dp).background(Color.Black.copy(alpha = 0.45f)))
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = if (isDark) 0.55f else 0.28f), Color.Transparent)))
                )
            }
        }
        com.aprireader.app.data.prefs.DesignStyle.GLASSMORPHISM -> {
            val glassRim = if (isDark) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.85f)
            val glassBodyTop = if (isDark) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.60f)
            val glassBodyBottom = if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.25f)

            Column(modifier = modifier.fillMaxWidth().padding(top = 2.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(Brush.verticalGradient(listOf(glassBodyTop, glassBodyBottom)))
                        .border(width = 0.75.dp, brush = Brush.verticalGradient(listOf(glassRim, Color.Transparent)), shape = androidx.compose.ui.graphics.RectangleShape)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = if (isDark) 0.35f else 0.10f), Color.Transparent)))
                )
            }
        }
        com.aprireader.app.data.prefs.DesignStyle.LIQUID_GLASS -> {
            val liquidRim = accent.copy(alpha = if (isDark) 0.65f else 0.45f)
            val liquidBodyTop = accent.copy(alpha = if (isDark) 0.25f else 0.18f)
            val liquidBodyBottom = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)

            Column(modifier = modifier.fillMaxWidth().padding(top = 2.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .background(Brush.verticalGradient(listOf(liquidBodyTop, liquidBodyBottom)))
                        .border(width = 1.dp, brush = Brush.verticalGradient(listOf(liquidRim, Color.White.copy(alpha = 0.15f))), shape = androidx.compose.ui.graphics.RectangleShape)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.25f), Color.Transparent)))
                )
            }
        }
        com.aprireader.app.data.prefs.DesignStyle.NEUMORPHISM -> {
            val neuHighlight = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.90f)
            val neuShadow = if (isDark) Color.Black.copy(alpha = 0.60f) else Color.Black.copy(alpha = 0.15f)
            val neuFace = MaterialTheme.colorScheme.surfaceContainerHigh

            Column(modifier = modifier.fillMaxWidth().padding(top = 1.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(neuHighlight)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(neuFace)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .background(Brush.verticalGradient(listOf(neuShadow, Color.Transparent)))
                )
            }
        }
        com.aprireader.app.data.prefs.DesignStyle.SOLID_CLEAN -> {
            val barColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            Column(modifier = modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(barColor)
                )
            }
        }
    }
}

/** Книга, стоящая на полке: 3D обложка, контактная тень и аккуратная подпись. */
@Composable
fun BookshelfBookItem(
    book: Book,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val openLabel = stringResource(R.string.shelf_open_book)
    val detailsLabel = stringResource(R.string.shelf_book_details)
    val a11yLabel = bookAccessibilityLabel(book)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SquircleSm)
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (isSelectionMode) onClick else onLongClick,
                onClickLabel = openLabel,
                onLongClickLabel = detailsLabel,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = a11yLabel
            }
            .alpha(if (book.available) 1f else 0.45f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 1.dp),
        ) {
            BookCover(
                book = book,
                modifier = Modifier
                    .fillMaxWidth()
                    .sharedCover(book.id, CoverShape),
            )

            if (isSelectionMode && isSelected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(2.5.dp, MaterialTheme.colorScheme.primary, CoverShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CoverShape)
                )
            }

            if (isSelectionMode) {
                SelectionBadge(
                    isSelected = isSelected,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                )
            }

            // Прогресс чтения (бейдж)
            if (book.progress > 0.001f && !isSelectionMode) {
                Surface(
                    shape = SquirclePill,
                    color = Color.Black.copy(alpha = 0.75f),
                    border = BorderStroke(0.75.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 5.dp),
                ) {
                    Text(
                        text = "${(book.progress * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                    )
                }
            }

            // Избранное
            if (book.favorite && !isSelectionMode) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(16.dp),
                )
            }

            // Недоступный файл
            if (!book.available) {
                Icon(
                    imageVector = Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(15.dp),
                )
            }
        }

        // Контактная тень под книгой на полке (гладкий линейный градиент без артефактов)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .height(3.dp)
                .background(
                    Brush.horizontalGradient(
                        0.0f to Color.Transparent,
                        0.15f to Color.Black.copy(alpha = 0.20f),
                        0.5f to Color.Black.copy(alpha = 0.38f),
                        0.85f to Color.Black.copy(alpha = 0.20f),
                        1.0f to Color.Transparent,
                    )
                ),
        )

        // Компактное название книги с фиксированной высотой слота (34.dp) для идеального горизонтального выравнивания
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .padding(top = 4.dp, bottom = 1.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.labelMedium.copy(lineHeight = 14.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }

        // Автор книги со стабильной высотой (16.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .padding(bottom = 2.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (book.authorLine.isNotBlank()) {
                Text(
                    text = book.authorLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Карточка книги в сетке (Grid/Compact view). */
@Composable
fun BookGridCard(
    book: Book,
    compact: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val openLabel = stringResource(R.string.shelf_open_book)
    val detailsLabel = stringResource(R.string.shelf_book_details)
    val a11yLabel = bookAccessibilityLabel(book)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SquircleSm)
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (isSelectionMode) onClick else onLongClick,
                onClickLabel = openLabel,
                onLongClickLabel = detailsLabel,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = a11yLabel
            }
            .alpha(if (book.available) 1f else 0.45f),
    ) {
        Box {
            BookCover(
                book = book,
                modifier = Modifier
                    .fillMaxWidth()
                    .sharedCover(book.id, CoverShape),
            )
            if (isSelectionMode && isSelected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(2.5.dp, MaterialTheme.colorScheme.primary, CoverShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CoverShape)
                )
            }
            if (isSelectionMode) {
                SelectionBadge(
                    isSelected = isSelected,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                )
            }
            if (book.progress > 0.001f && !isSelectionMode) {
                ProgressStripe(
                    progress = book.progress,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 5.dp, vertical = 5.dp),
                )
            }
            if (book.favorite && !isSelectionMode) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(15.dp),
                )
            }
            if (!book.available) {
                Icon(
                    imageVector = Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .size(15.dp),
                )
            }
        }

        if (!compact) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .padding(top = 6.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.labelLarge.copy(lineHeight = 15.sp),
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .padding(top = 1.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                if (book.authorLine.isNotBlank()) {
                    Text(
                        text = book.authorLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Строка книги в списочном виде. */
@Composable
fun BookListRow(
    book: Book,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(SquircleSm)
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (isSelectionMode) onClick else onLongClick,
            )
            .padding(vertical = 5.dp)
            .alpha(if (book.available) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSelectionMode) {
            SelectionBadge(
                isSelected = isSelected,
                modifier = Modifier.padding(end = 10.dp),
            )
        }
        Box(
            modifier = Modifier
                .width(48.dp)
                .aspectRatio(COVER_ASPECT),
        ) {
            BookCover(
                book = book,
                modifier = Modifier
                    .fillMaxSize()
                    .sharedCover(book.id, CoverShape),
            )
            if (isSelectionMode && isSelected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(2.dp, MaterialTheme.colorScheme.primary, CoverShape)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.authorLine.isNotBlank()) {
                Text(
                    text = book.authorLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = book.format.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(SquircleXs)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 5.dp, vertical = 1.5.dp),
                )
                if (book.progress > 0.001f) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${(book.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (book.favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                contentDescription = if (book.favorite) stringResource(R.string.shelf_favorite_remove) else stringResource(R.string.shelf_favorite_add),
                tint = if (book.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Карточка «Продолжить чтение» — компактная, элегантная, с фиксированной обложкой. */
@Composable
fun ContinueReadingCard(
    book: Book,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val accent = book.effectiveAccent?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val continueLabel = stringResource(R.string.shelf_continue)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    ) {
        CoverBloomHost(
            coverPath = book.coverPath,
            accent = accent,
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clip(SquircleXl),
        )

        GlassPanel(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isSelectionMode && isSelected) {
                        Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, SquircleXl)
                    } else Modifier
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (isSelectionMode) onClick else onLongClick,
                    onClickLabel = continueLabel,
                ),
            shape = SquircleXl,
            tint = accent,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSelectionMode) {
                    SelectionBadge(
                        isSelected = isSelected,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .aspectRatio(COVER_ASPECT),
                ) {
                    BookCover(
                        book = book,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = continueLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (book.authorLine.isNotBlank()) {
                        Text(
                            text = book.authorLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressStripe(progress = book.progress, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${(book.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Surface(
                    shape = SquirclePill,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = continueLabel,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Полоса прогресса на обложке или в карточке. */
@Composable
fun ProgressStripe(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(3.5.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(2.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        )
                    )
                ),
        )
    }
}

@Composable
internal fun bookAccessibilityLabel(book: Book): String = buildString {
    append(book.title)
    if (book.authorLine.isNotBlank()) append(", ${book.authorLine}")
    if (book.progress > 0.001f) append(", ${(book.progress * 100).toInt()}%")
    if (book.favorite) append(", ${stringResource(R.string.filter_favorites)}")
    if (!book.available) append(", ${stringResource(R.string.source_lost_one)}")
}

