package com.aprireader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import com.aprireader.app.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.aprireader.app.data.prefs.TypographySettings
import com.aprireader.app.ui.reader.text.BionicOptions
import com.aprireader.app.ui.reader.text.ReadingTextBuilder
import com.aprireader.app.ui.theme.SquircleXs
import com.aprireader.app.ui.theme.ReaderPalette
import com.aprireader.app.ui.theme.resolveReadingFont
import com.aprireader.bookformat.model.ContentBlock
import com.aprireader.bookformat.model.ParagraphKind
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Потоковый режим чтения — основной для EPUB, FB2, TXT и HTML.
 *
 * Текст рисуется нативным Compose, а не WebView: только так бионический режим и
 * RSVP получают доступ к словам, а не к готовой вёрстке.
 */
@Composable
fun FlowReader(
    state: ReaderUiState,
    palette: ReaderPalette,
    listState: LazyListState = rememberLazyListState(),
    onScrollFraction: (Float) -> Unit,
    onNextChapter: () -> Unit,
    onPreviousChapter: () -> Unit,
    loadResource: suspend (String) -> ByteArray?,
    highlightedBlocks: Set<Int> = emptySet(),
    onBlockLongPress: (Int) -> Unit = {},
    /** Доля ширины по горизонтали, куда пришёлся тап: 0 — левый край, 1 — правый. */
    onBlockTap: (Float) -> Unit = {},
    /** Абзац, который сейчас читается вслух. */
    speakingBlock: Int? = null,
    scrollToBlock: Int? = null,
    onScrollTargetConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val typography = state.typography
    val bionic = if (state.focus.bionicEnabled) {
        BionicOptions(state.focus.bionicIntensity, state.focus.bionicDimTail)
    } else {
        null
    }

    val baseStyle = rememberReadingStyle(typography, palette.text)

    LaunchedEffect(listState, state.chapterIndex) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val total = layout.totalItemsCount
            if (total == 0) {
                0f
            } else {
                val last = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
                ((last + 1).toFloat() / total).coerceIn(0f, 1f)
            }
        }
            .distinctUntilChanged()
            .collect(onScrollFraction)
    }

    // Новая глава начинается сверху — кроме случая, когда позицию просят восстановить.
    LaunchedEffect(state.chapterIndex) {
        if (scrollToBlock == null) listState.scrollToItem(0)
    }

    // Озвучиваемый абзац держим в поле зрения: читать вслух и смотреть в другое
    // место страницы одинаково неудобно и зрячим, и незрячим пользователям.
    LaunchedEffect(speakingBlock) {
        val target = speakingBlock ?: return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        val inView = visible.any { it.index == target + 1 }
        if (!inView) listState.animateScrollToItem((target + 1).coerceAtLeast(0))
    }

    // Переход из поиска: список прокручивается к найденному абзацу
    // (+1 — заголовок главы занимает первый элемент списка).
    LaunchedEffect(scrollToBlock, state.blocks.size) {
        val target = scrollToBlock ?: return@LaunchedEffect
        if (state.blocks.isEmpty()) return@LaunchedEffect
        listState.scrollToItem((target + 1).coerceIn(0, state.blocks.size))
        onScrollTargetConsumed()
    }

    Box(modifier.fillMaxSize().background(palette.background)) {
        // Ширина колонки ограничивается числом символов в строке: длинная строка
        // читается хуже вне зависимости от того, сколько места есть на экране.
        val maxColumnWidth = (typography.maxLineWidthChars * typography.fontSizeSp * 0.52f).dp
        val horizontal = typography.horizontalMarginDp.dp
        val blockActionsLabel = stringResource(R.string.reader_block_actions_title)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = horizontal,
                end = horizontal,
                top = 96.dp,
                bottom = 140.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "chapter-title") {
                state.chapterTitle?.takeIf { it.isNotBlank() }?.let { title ->
                    Column(Modifier.widthIn(max = maxColumnWidth).fillMaxWidth()) {
                        Text(
                            text = title,
                            style = baseStyle.copy(
                                fontSize = (typography.fontSizeSp * 1.5f).sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = (typography.fontSizeSp * 1.5f * 1.25f).sp,
                            ),
                            color = palette.text,
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier
                                .width(48.dp)
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(palette.accent),
                        )
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }

            items(count = state.blocks.size, key = { "block-$it" }) { index ->
                Box(
                    Modifier
                        .widthIn(max = maxColumnWidth)
                        .fillMaxWidth()
                        .then(
                            when {
                                index == speakingBlock -> Modifier
                                    .clip(SquircleXs)
                                    .background(palette.accent.copy(alpha = 0.16f))
                                index in highlightedBlocks -> Modifier
                                    .clip(SquircleXs)
                                    .background(palette.selection)
                                else -> Modifier
                            }
                        )
                        // Абзац сам обрабатывает и долгое нажатие, и обычный тап:
                        // иначе он проглатывал бы касания, и зоны листания
                        // переставали бы работать поверх текста.
                        .pointerInput(index) {
                            detectTapGestures(
                                onLongPress = { onBlockLongPress(index) },
                                onTap = { offset ->
                                    onBlockTap(offset.x / size.width.toFloat().coerceAtLeast(1f))
                                },
                            )
                        }
                        .semantics {
                            onLongClick(label = blockActionsLabel) {
                                onBlockLongPress(index)
                                true
                            }
                        },
                ) {
                    BlockContent(
                        block = state.blocks[index],
                        typography = typography,
                        baseStyle = baseStyle,
                        palette = palette,
                        bionic = bionic,
                        loadResource = loadResource,
                    )
                }
            }

            item(key = "chapter-nav") {
                ChapterNavigation(
                    hasPrevious = state.chapterIndex > 0,
                    hasNext = state.chapterIndex < state.chapters.size - 1,
                    nextTitle = state.chapters.getOrNull(state.chapterIndex + 1)?.title,
                    palette = palette,
                    onPrevious = onPreviousChapter,
                    onNext = onNextChapter,
                    modifier = Modifier.widthIn(max = maxColumnWidth).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun BlockContent(
    block: ContentBlock,
    typography: TypographySettings,
    baseStyle: TextStyle,
    palette: ReaderPalette,
    bionic: BionicOptions?,
    loadResource: suspend (String) -> ByteArray?,
) {
    val paragraphGap = (typography.fontSizeSp * typography.paragraphSpacing).dp

    when (block) {
        is ContentBlock.Heading -> {
            val scale = when (block.level) {
                1 -> 1.6f
                2 -> 1.4f
                3 -> 1.22f
                else -> 1.1f
            }
            val headingText = remember(block.text, palette.text, palette.accent) {
                ReadingTextBuilder.build(block.text, null, palette.text, palette.accent)
            }
            Column {
                Spacer(Modifier.height(paragraphGap * 1.6f))
                Text(
                    text = headingText,
                    style = baseStyle.copy(
                        fontSize = (typography.fontSizeSp * scale).sp,
                        lineHeight = (typography.fontSizeSp * scale * 1.25f).sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Start,
                        textIndent = TextIndent.None,
                    ),
                    color = palette.text,
                )
                Spacer(Modifier.height(paragraphGap * 0.6f))
            }
        }

        is ContentBlock.Paragraph -> {
            val style = when (block.kind) {
                ParagraphKind.QUOTE, ParagraphKind.EPIGRAPH -> baseStyle.copy(
                    fontStyle = FontStyle.Italic,
                    fontSize = (typography.fontSizeSp * 0.96f).sp,
                )
                ParagraphKind.POEM -> baseStyle.copy(textAlign = TextAlign.Start, textIndent = TextIndent.None)
                ParagraphKind.CODE -> baseStyle.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = (typography.fontSizeSp * 0.86f).sp,
                    textAlign = TextAlign.Start,
                )
                ParagraphKind.CAPTION, ParagraphKind.NOTE -> baseStyle.copy(
                    fontSize = (typography.fontSizeSp * 0.88f).sp,
                )
                ParagraphKind.BODY -> baseStyle
            }
            val color = when (block.kind) {
                ParagraphKind.CAPTION, ParagraphKind.NOTE -> palette.secondaryText
                else -> palette.text
            }

            val bionicOptions = bionic.takeIf { block.kind != ParagraphKind.CODE }
            val text = remember(block.text, bionicOptions, color, palette.accent) {
                ReadingTextBuilder.build(
                    rich = block.text,
                    bionic = bionicOptions,
                    baseColor = color,
                    linkColor = palette.accent,
                )
            }

            Column {
                Spacer(Modifier.height(paragraphGap))
                if (block.kind == ParagraphKind.QUOTE || block.kind == ParagraphKind.EPIGRAPH) {
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(palette.accent.copy(alpha = 0.55f), RoundedCornerShape(2.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(text = text, style = style, color = color)
                    }
                } else {
                    Text(text = text, style = style, color = color)
                }
            }
        }

        is ContentBlock.Image -> {
            val bitmap by produceState<ImageBitmap?>(initialValue = null, block.href) {
                value = loadResource(block.href)?.let { bytes ->
                    runCatching {
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }.getOrNull()
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(paragraphGap * 1.4f))
                bitmap?.let {
                    androidx.compose.foundation.Image(
                        bitmap = it,
                        contentDescription = block.caption,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SquircleXs),
                    )
                }
                block.caption?.let { caption ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = caption,
                        style = baseStyle.copy(fontSize = (typography.fontSizeSp * 0.85f).sp),
                        color = palette.secondaryText,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(paragraphGap * 1.4f))
            }
        }

        ContentBlock.Separator -> {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = paragraphGap * 1.2f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "* * *",
                    style = baseStyle.copy(textAlign = TextAlign.Center, textIndent = TextIndent.None),
                    color = palette.secondaryText,
                )
            }
        }
    }
}

@Composable
private fun ChapterNavigation(
    hasPrevious: Boolean,
    hasNext: Boolean,
    nextTitle: String?,
    palette: ReaderPalette,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .width(64.dp)
                .height(1.dp)
                .background(palette.secondaryText.copy(alpha = 0.4f)),
        )
        Spacer(Modifier.height(24.dp))
        if (hasNext) {
            nextTitle?.let {
                Text(
                    text = stringResource(R.string.reader_next_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.secondaryText,
                )
                Spacer(Modifier.height(4.dp))
                Text(text = it, style = MaterialTheme.typography.titleMedium, color = palette.text)
                Spacer(Modifier.height(14.dp))
            }
            Button(onClick = onNext) { Text(stringResource(R.string.reader_next_chapter)) }
        } else {
            Text(
                text = stringResource(R.string.reader_end_of_book),
                style = MaterialTheme.typography.titleMedium,
                color = palette.text,
            )
        }
        if (hasPrevious) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onPrevious) { Text(stringResource(R.string.reader_prev_chapter)) }
        }
        Spacer(Modifier.height(40.dp))
    }
}

/** Единый стиль абзаца, собранный из пользовательских настроек типографики. */
@Composable
fun rememberReadingStyle(typography: TypographySettings, color: Color): TextStyle {
    return remember(typography, color) {
        TextStyle(
            fontFamily = resolveReadingFont(typography),
            fontSize = typography.fontSizeSp.sp,
            lineHeight = (typography.fontSizeSp * typography.lineHeight).sp,
            letterSpacing = typography.letterSpacing.em,
            color = color,
            textAlign = if (typography.justify) TextAlign.Justify else TextAlign.Start,
            hyphens = if (typography.hyphenation) Hyphens.Auto else Hyphens.None,
            lineBreak = LineBreak.Paragraph,
            textIndent = if (typography.firstLineIndent) TextIndent(firstLine = (typography.fontSizeSp * 1.4f).sp) else TextIndent.None,
        )
    }
}

