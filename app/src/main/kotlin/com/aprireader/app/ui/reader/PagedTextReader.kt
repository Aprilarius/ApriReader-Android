package com.aprireader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aprireader.app.R
import com.aprireader.app.data.prefs.TypographySettings
import com.aprireader.app.ui.reader.text.BionicOptions
import com.aprireader.app.ui.reader.text.ReadingTextBuilder
import com.aprireader.app.ui.theme.ReaderPalette
import com.aprireader.app.ui.theme.SquircleXs
import com.aprireader.bookformat.model.ContentBlock
import com.aprireader.bookformat.model.ParagraphKind
import kotlinx.coroutines.launch
import kotlin.math.min

/** Элемент на странице ридера. */
internal data class PageBlockItem(
    val originalBlockIndex: Int,
    val block: ContentBlock,
)

/** Одна сформированная страница главы. */
internal data class TextPage(
    val items: List<PageBlockItem>,
) {
    /** Первый абзац/блок на странице — «смысловой» якорь страницы. */
    fun anchorBlock(): Int = items.firstOrNull()?.originalBlockIndex ?: 0
}

/**
 * Индекс страницы, на которой находится (или начинается) блок [blockIndex].
 *
 * Разбивка абзацев на части (см. [splitParagraph]) означает, что один и тот же
 * originalBlockIndex может встречаться на нескольких страницах подряд — берём
 * первую, где он появляется.
 */
private fun pageIndexForBlock(pages: List<TextPage>, blockIndex: Int): Int {
    if (pages.isEmpty()) return 0
    val found = pages.indexOfFirst { page -> page.items.any { it.originalBlockIndex >= blockIndex } }
    return (if (found >= 0) found else pages.lastIndex).coerceIn(0, pages.lastIndex)
}

/**
 * Постраничный режим чтения для текстовых книг (EPUB, FB2, TXT, MD).
 *
 * Архитектура строится вокруг «смысловой» позиции — индекса блока (абзаца), а
 * не индекса страницы: список страниц пересобирается заново при любом изменении
 * типографики, и индекс страницы сам по себе после этого ничего не значит.
 * Каждый пересчёт страниц заново находит страницу, содержащую тот же блок —
 * читатель не теряет место и прогресс не скачет.
 *
 * Поддерживает:
 * - Горизонтальное листание (слева направо) и вертикальное постраничное листание
 * - Восстановление точной позиции при открытии книги, переходе по оглавлению,
 *   результату поиска и при живой смене шрифта/кегля/бионики
 * - Слежение за озвучиваемым абзацем — страница переключается вместе с TTS
 * - Тап-зоны (края — листание, центр — вызов меню)
 * - Бесшовный переход между главами на последней/первой странице
 */
@Composable
fun PagedTextReader(
    state: ReaderUiState,
    palette: ReaderPalette,
    onScrollFraction: (Float) -> Unit,
    onNextChapter: () -> Unit,
    onPreviousChapter: () -> Unit,
    onToggleChrome: () -> Unit,
    loadResource: suspend (String) -> ByteArray?,
    highlightedBlocks: Set<Int> = emptySet(),
    onBlockLongPress: (Int) -> Unit = {},
    speakingBlock: Int? = null,
    isVerticalPaged: Boolean = false,
    scrollToBlock: Int? = null,
    onScrollTargetConsumed: () -> Unit = {},
    onBindPageControls: ((next: () -> Unit, prev: () -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val typography = state.typography
    val bionic = if (state.focus.bionicEnabled) {
        BionicOptions(state.focus.bionicIntensity, state.focus.bionicDimTail)
    } else {
        null
    }

    val baseStyle = rememberReadingStyle(typography, palette.text)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxSize().background(palette.background)) {
        val availableWidthPx = constraints.maxWidth
        val availableHeightPx = constraints.maxHeight
        val horizontalMargin = typography.horizontalMarginDp.dp
        val maxColumnWidth = (typography.maxLineWidthChars * typography.fontSizeSp * 0.52f).dp

        val usableWidthPx = min(
            (availableWidthPx - with(density) { (horizontalMargin * 2).toPx() }).toInt(),
            with(density) { maxColumnWidth.toPx() }.toInt(),
        ).coerceAtLeast(200)

        // 44.dp верхняя плашка + 44.dp нижняя плашка
        val headerFooterPaddingPx = with(density) { 88.dp.toPx() }
        val usableHeightPx = (availableHeightPx - headerFooterPaddingPx).coerceAtLeast(200f)

        val pages = remember(
            state.blocks,
            state.chapterIndex,
            state.chapterTitle,
            usableWidthPx,
            usableHeightPx,
            typography,
            bionic,
            baseStyle,
        ) {
            paginateChapter(
                blocks = state.blocks,
                chapterTitle = state.chapterTitle,
                typography = typography,
                baseStyle = baseStyle,
                bionic = bionic,
                palette = palette,
                textMeasurer = textMeasurer,
                density = density,
                usableWidthPx = usableWidthPx,
                usableHeightPx = usableHeightPx,
            )
        }

        // Вся живая позиция (PagerState читателя) пересоздаётся с нуля на
        // каждую новую главу — это надёжнее, чем вручную «перематывать» старый
        // PagerState: номер страницы прошлой главы не имеет смысла в новой.
        key(state.chapterIndex) {
            ChapterPager(
                state = state,
                palette = palette,
                pages = pages,
                horizontalMargin = horizontalMargin,
                maxColumnWidth = maxColumnWidth,
                isVerticalPaged = isVerticalPaged,
                initialBlock = scrollToBlock ?: 0,
                scrollToBlock = scrollToBlock,
                speakingBlock = speakingBlock,
                highlightedBlocks = highlightedBlocks,
                onScrollFraction = onScrollFraction,
                onNextChapter = onNextChapter,
                onPreviousChapter = onPreviousChapter,
                onToggleChrome = onToggleChrome,
                loadResource = loadResource,
                onBlockLongPress = onBlockLongPress,
                onScrollTargetConsumed = onScrollTargetConsumed,
                onBindPageControls = onBindPageControls,
            )
        }
    }
}

/**
 * Владеет [PagerState] одной главы: держит его синхронизированным со
 * «смысловой» позицией (индексом блока), а не с сырым индексом страницы.
 */
@Composable
private fun ChapterPager(
    state: ReaderUiState,
    palette: ReaderPalette,
    pages: List<TextPage>,
    horizontalMargin: androidx.compose.ui.unit.Dp,
    maxColumnWidth: androidx.compose.ui.unit.Dp,
    isVerticalPaged: Boolean,
    initialBlock: Int,
    scrollToBlock: Int?,
    speakingBlock: Int?,
    highlightedBlocks: Set<Int>,
    onScrollFraction: (Float) -> Unit,
    onNextChapter: () -> Unit,
    onPreviousChapter: () -> Unit,
    onToggleChrome: () -> Unit,
    loadResource: suspend (String) -> ByteArray?,
    onBlockLongPress: (Int) -> Unit,
    onScrollTargetConsumed: () -> Unit,
    onBindPageControls: ((next: () -> Unit, prev: () -> Unit) -> Unit)?,
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = pageIndexForBlock(pages, initialBlock),
    ) { pages.size.coerceAtLeast(1) }

    // Последний блок, на котором реально был читатель — источник истины для
    // восстановления позиции при пересборке страниц (смена шрифта, кегля,
    // бионики). Индекс страницы для этого непригоден: список страниц другой.
    var lastKnownBlock by remember { mutableIntStateOf(initialBlock) }

    // Пересборка страниц (пересчитанная типографика) и переключение
    // пользователем страниц — обрабатываются одним и тем же потоком, чтобы не
    // было гонки между «переехать на смысловую позицию» и «записать текущую».
    LaunchedEffect(pagerState, pages) {
        val target = pageIndexForBlock(pages, lastKnownBlock)
        if (pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
        snapshotFlow { pagerState.currentPage }
            .collect { pageIndex ->
                lastKnownBlock = pages.getOrNull(pageIndex)?.anchorBlock() ?: lastKnownBlock
                val fraction = if (pages.isEmpty()) {
                    0f
                } else {
                    (pageIndex.toFloat() / pages.size.toFloat()).coerceIn(0f, 1f)
                }
                onScrollFraction(fraction)
            }
    }

    // Явный переход: оглавление, результат поиска, восстановление позиции при
    // повторном открытии главы. Срабатывает и когда глава не менялась (поиск
    // внутри текущей главы), поэтому не завязан на смену главы.
    LaunchedEffect(scrollToBlock, pages) {
        val target = scrollToBlock ?: return@LaunchedEffect
        if (pages.isEmpty()) return@LaunchedEffect
        lastKnownBlock = target
        pagerState.scrollToPage(pageIndexForBlock(pages, target))
        onScrollTargetConsumed()
    }

    // Озвучиваемый абзац держим на экране: постраничный режим не должен
    // требовать от пользователя листать вручную вслед за голосом.
    LaunchedEffect(speakingBlock, pages) {
        val target = speakingBlock ?: return@LaunchedEffect
        if (pages.isEmpty()) return@LaunchedEffect
        val current = pages.getOrNull(pagerState.currentPage)
        val alreadyVisible = current?.items?.any { it.originalBlockIndex == target } == true
        if (!alreadyVisible) {
            lastKnownBlock = target
            pagerState.animateScrollToPage(pageIndexForBlock(pages, target))
        }
    }

    val pageForward: () -> Unit = {
        if (pagerState.currentPage < pages.size - 1) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
        } else if (state.settings.reader.continuousReading) {
            onNextChapter()
        }
    }

    val pageBackward: () -> Unit = {
        if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        } else if (state.chapterIndex > 0 && state.settings.reader.continuousReading) {
            onPreviousChapter()
        }
    }

    LaunchedEffect(onBindPageControls) {
        onBindPageControls?.invoke(pageForward, pageBackward)
    }

    val handleTapFraction: (Float) -> Unit = { fractionX ->
        when {
            fractionX < 0.28f -> pageBackward()
            fractionX > 0.72f -> pageForward()
            else -> onToggleChrome()
        }
    }

    val pageContent: @Composable androidx.compose.foundation.pager.PagerScope.(Int) -> Unit = { pageIndex ->
        TextPageContent(
            state = state,
            palette = palette,
            page = pages.getOrNull(pageIndex),
            pageIndex = pageIndex,
            pageCount = pages.size,
            horizontalMargin = horizontalMargin,
            maxColumnWidth = maxColumnWidth,
            speakingBlock = speakingBlock,
            highlightedBlocks = highlightedBlocks,
            loadResource = loadResource,
            onBlockLongPress = onBlockLongPress,
            onTapFraction = handleTapFraction,
        )
    }

    if (isVerticalPaged) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            pageContent = pageContent,
        )
    } else {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            pageContent = pageContent,
        )
    }
}

/** Визуальное содержимое одной страницы — колонтитулы, текст, номер страницы. */
@Composable
private fun TextPageContent(
    state: ReaderUiState,
    palette: ReaderPalette,
    page: TextPage?,
    pageIndex: Int,
    pageCount: Int,
    horizontalMargin: androidx.compose.ui.unit.Dp,
    maxColumnWidth: androidx.compose.ui.unit.Dp,
    speakingBlock: Int?,
    highlightedBlocks: Set<Int>,
    loadResource: suspend (String) -> ByteArray?,
    onBlockLongPress: (Int) -> Unit,
    onTapFraction: (Float) -> Unit,
) {
    val typography = state.typography
    val bionic = if (state.focus.bionicEnabled) {
        BionicOptions(state.focus.bionicIntensity, state.focus.bionicDimTail)
    } else {
        null
    }
    val baseStyle = rememberReadingStyle(typography, palette.text)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalMargin)
            .pointerInput(pageIndex) {
                detectTapGestures { offset ->
                    onTapFraction(offset.x / size.width.toFloat().coerceAtLeast(1f))
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Верхний колонтитул (название книги или главы)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.chapterTitle?.ifBlank { null } ?: state.book?.title.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = palette.secondaryText.copy(alpha = 0.70f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }

        // Тело страницы
        Column(
            modifier = Modifier
                .widthIn(max = maxColumnWidth)
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (pageIndex == 0 && !state.chapterTitle.isNullOrBlank()) {
                Text(
                    text = state.chapterTitle,
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
                Spacer(Modifier.height(14.dp))
            }

            page?.items?.forEach { item ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            when {
                                item.originalBlockIndex == speakingBlock -> Modifier
                                    .clip(SquircleXs)
                                    .background(palette.accent.copy(alpha = 0.16f))
                                item.originalBlockIndex in highlightedBlocks -> Modifier
                                    .clip(SquircleXs)
                                    .background(palette.selection)
                                else -> Modifier
                            }
                        )
                        .pointerInput(item.originalBlockIndex) {
                            detectTapGestures(
                                onLongPress = { onBlockLongPress(item.originalBlockIndex) },
                                onTap = { offset ->
                                    onTapFraction(offset.x / size.width.toFloat().coerceAtLeast(1f))
                                },
                            )
                        }
                ) {
                    BlockContent(
                        block = item.block,
                        typography = typography,
                        baseStyle = baseStyle,
                        palette = palette,
                        bionic = bionic,
                        loadResource = loadResource,
                    )
                }
            }
        }

        // Нижний колонтитул (прогресс и номер страницы)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${(state.progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = palette.secondaryText.copy(alpha = 0.70f),
            )
            Text(
                text = stringResource(R.string.reader_page_of, pageIndex + 1, pageCount),
                style = MaterialTheme.typography.labelSmall,
                color = palette.secondaryText.copy(alpha = 0.70f),
            )
        }
    }
}

/**
 * Точный алгоритм разбивки блоков главы на страницы.
 */
internal fun paginateChapter(
    blocks: List<ContentBlock>,
    chapterTitle: String?,
    typography: TypographySettings,
    baseStyle: TextStyle,
    bionic: BionicOptions?,
    palette: ReaderPalette,
    textMeasurer: TextMeasurer,
    density: Density,
    usableWidthPx: Int,
    usableHeightPx: Float,
): List<TextPage> {
    if (blocks.isEmpty()) return listOf(TextPage(emptyList()))

    val paragraphGapPx = with(density) { (typography.fontSizeSp * typography.paragraphSpacing).dp.toPx() }
    val pages = mutableListOf<TextPage>()
    var currentItems = mutableListOf<PageBlockItem>()
    var currentHeight = 0f

    val titleHeightPx = if (!chapterTitle.isNullOrBlank()) {
        with(density) { (typography.fontSizeSp * 1.5f * 1.3f + 24).dp.toPx() }
    } else {
        0f
    }

    var pageCapacity = (usableHeightPx - titleHeightPx).coerceAtLeast(100f)

    for (index in blocks.indices) {
        val block = blocks[index]
        val blockHeight = estimateOrMeasureBlockHeight(
            block = block,
            typography = typography,
            baseStyle = baseStyle,
            bionic = bionic,
            palette = palette,
            textMeasurer = textMeasurer,
            density = density,
            maxWidthPx = usableWidthPx,
            paragraphGapPx = paragraphGapPx,
        )

        if (currentHeight + blockHeight <= pageCapacity) {
            currentItems.add(PageBlockItem(index, block))
            currentHeight += blockHeight
        } else if (currentItems.isEmpty()) {
            // Блок больше всей доступной высоты страницы
            if (block is ContentBlock.Paragraph && block.text.text.length > 200) {
                val (part1, part2) = splitParagraph(
                    paragraph = block,
                    availableHeightPx = pageCapacity - currentHeight,
                    baseStyle = baseStyle,
                    bionic = bionic,
                    palette = palette,
                    textMeasurer = textMeasurer,
                    maxWidthPx = usableWidthPx,
                )
                if (part1 != null) {
                    currentItems.add(PageBlockItem(index, part1))
                    pages.add(TextPage(currentItems))
                    currentItems = mutableListOf()
                    currentHeight = 0f
                    pageCapacity = usableHeightPx

                    var rem: ContentBlock.Paragraph? = part2
                    while (rem != null) {
                        val remHeight = estimateOrMeasureBlockHeight(
                            block = rem,
                            typography = typography,
                            baseStyle = baseStyle,
                            bionic = bionic,
                            palette = palette,
                            textMeasurer = textMeasurer,
                            density = density,
                            maxWidthPx = usableWidthPx,
                            paragraphGapPx = paragraphGapPx,
                        )
                        if (remHeight <= pageCapacity) {
                            currentItems.add(PageBlockItem(index, rem))
                            currentHeight += remHeight
                            rem = null
                        } else {
                            val (sub1, sub2) = splitParagraph(
                                paragraph = rem,
                                availableHeightPx = pageCapacity,
                                baseStyle = baseStyle,
                                bionic = bionic,
                                palette = palette,
                                textMeasurer = textMeasurer,
                                maxWidthPx = usableWidthPx,
                            )
                            if (sub1 != null) {
                                pages.add(TextPage(listOf(PageBlockItem(index, sub1))))
                                rem = sub2
                            } else {
                                pages.add(TextPage(listOf(PageBlockItem(index, rem))))
                                rem = null
                            }
                        }
                    }
                } else {
                    currentItems.add(PageBlockItem(index, block))
                    pages.add(TextPage(currentItems))
                    currentItems = mutableListOf()
                    currentHeight = 0f
                    pageCapacity = usableHeightPx
                }
            } else {
                currentItems.add(PageBlockItem(index, block))
                pages.add(TextPage(currentItems))
                currentItems = mutableListOf()
                currentHeight = 0f
                pageCapacity = usableHeightPx
            }
        } else {
            // Завершаем текущую страницу и начинаем новую
            pages.add(TextPage(currentItems))
            currentItems = mutableListOf()
            currentHeight = 0f
            pageCapacity = usableHeightPx

            val newHeight = estimateOrMeasureBlockHeight(
                block = block,
                typography = typography,
                baseStyle = baseStyle,
                bionic = bionic,
                palette = palette,
                textMeasurer = textMeasurer,
                density = density,
                maxWidthPx = usableWidthPx,
                paragraphGapPx = paragraphGapPx,
            )

            if (newHeight <= pageCapacity) {
                currentItems.add(PageBlockItem(index, block))
                currentHeight = newHeight
            } else if (block is ContentBlock.Paragraph && block.text.text.length > 200) {
                val (part1, part2) = splitParagraph(
                    paragraph = block,
                    availableHeightPx = pageCapacity,
                    baseStyle = baseStyle,
                    bionic = bionic,
                    palette = palette,
                    textMeasurer = textMeasurer,
                    maxWidthPx = usableWidthPx,
                )
                if (part1 != null) {
                    pages.add(TextPage(listOf(PageBlockItem(index, part1))))
                    if (part2 != null) {
                        currentItems.add(PageBlockItem(index, part2))
                        currentHeight = estimateOrMeasureBlockHeight(
                            block = part2,
                            typography = typography,
                            baseStyle = baseStyle,
                            bionic = bionic,
                            palette = palette,
                            textMeasurer = textMeasurer,
                            density = density,
                            maxWidthPx = usableWidthPx,
                            paragraphGapPx = paragraphGapPx,
                        )
                    }
                } else {
                    currentItems.add(PageBlockItem(index, block))
                    currentHeight = newHeight
                }
            } else {
                currentItems.add(PageBlockItem(index, block))
                currentHeight = newHeight
            }
        }
    }

    if (currentItems.isNotEmpty()) {
        pages.add(TextPage(currentItems))
    }

    return if (pages.isEmpty()) listOf(TextPage(emptyList())) else pages
}

private fun estimateOrMeasureBlockHeight(
    block: ContentBlock,
    typography: TypographySettings,
    baseStyle: TextStyle,
    bionic: BionicOptions?,
    palette: ReaderPalette,
    textMeasurer: TextMeasurer,
    density: Density,
    maxWidthPx: Int,
    paragraphGapPx: Float,
): Float {
    return when (block) {
        is ContentBlock.Heading -> {
            val scale = when (block.level) {
                1 -> 1.6f
                2 -> 1.4f
                3 -> 1.22f
                else -> 1.1f
            }
            val headingText = ReadingTextBuilder.build(block.text, null, palette.text, palette.accent)
            val headingStyle = baseStyle.copy(
                fontSize = (typography.fontSizeSp * scale).sp,
                lineHeight = (typography.fontSizeSp * scale * 1.25f).sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Start,
                textIndent = TextIndent.None,
            )
            val layout = textMeasurer.measure(
                text = headingText,
                style = headingStyle,
                constraints = Constraints(maxWidth = maxWidthPx),
            )
            layout.size.height + (paragraphGapPx * 2.2f)
        }

        is ContentBlock.Paragraph -> {
            val paragraphText = ReadingTextBuilder.build(block.text, bionic, palette.text, palette.accent)
            val paragraphStyle = when (block.kind) {
                ParagraphKind.QUOTE, ParagraphKind.EPIGRAPH -> baseStyle.copy(fontStyle = FontStyle.Italic)
                ParagraphKind.CODE -> baseStyle.copy(fontFamily = FontFamily.Monospace)
                else -> baseStyle
            }
            val layout = textMeasurer.measure(
                text = paragraphText,
                style = paragraphStyle,
                constraints = Constraints(maxWidth = maxWidthPx),
            )
            layout.size.height + paragraphGapPx
        }

        is ContentBlock.Image -> {
            with(density) { 240.dp.toPx() }
        }

        ContentBlock.Separator -> {
            paragraphGapPx * 2.4f
        }
    }
}

private fun splitParagraph(
    paragraph: ContentBlock.Paragraph,
    availableHeightPx: Float,
    baseStyle: TextStyle,
    bionic: BionicOptions?,
    palette: ReaderPalette,
    textMeasurer: TextMeasurer,
    maxWidthPx: Int,
): Pair<ContentBlock.Paragraph?, ContentBlock.Paragraph?> {
    val rawText = paragraph.text.text
    if (rawText.length < 50 || availableHeightPx < 40f) return Pair(null, paragraph)

    val annotated = ReadingTextBuilder.build(paragraph.text, bionic, palette.text, palette.accent)
    val layout = textMeasurer.measure(
        text = annotated,
        style = baseStyle,
        constraints = Constraints(maxWidth = maxWidthPx),
    )

    if (layout.lineCount <= 1) return Pair(null, paragraph)

    val fitLine = layout.getLineForVerticalPosition(availableHeightPx)
    if (fitLine <= 0) return Pair(null, paragraph)

    val cutChar = layout.getLineEnd((fitLine - 1).coerceAtLeast(0)).coerceIn(0, rawText.length)
    if (cutChar <= 10 || cutChar >= rawText.length - 10) return Pair(null, paragraph)

    val part1 = paragraph.copy(text = paragraph.text.copy(text = rawText.substring(0, cutChar)))
    val part2 = paragraph.copy(text = paragraph.text.copy(text = rawText.substring(cutChar).trimStart()))
    return Pair(part1, part2)
}
