package com.aprireader.app.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import com.aprireader.app.data.prefs.ReadingScrollMode
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import com.aprireader.app.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aprireader.app.data.prefs.AppSettings
import com.aprireader.app.ui.theme.SquircleSm
import com.aprireader.app.ui.common.GlassPanel
import com.aprireader.app.ui.theme.GlassLevel
import com.aprireader.app.ui.theme.SquircleLg
import com.aprireader.app.ui.theme.ApriTheme
import com.aprireader.app.ui.theme.readerPalette
import kotlinx.coroutines.launch

/**
 * Экран чтения.
 *
 * Здесь применяется Dynamic Cover Theming: тема на этом экране строится из
 * акцента книги, а не из глобального. Полка при этом остаётся нейтральной —
 * окрашивается именно контекст чтения конкретной книги.
 */
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tts by viewModel.ttsState.collectAsStateWithLifecycle()

    ApriTheme(
        settings = state.settings,
        bookAccent = state.accent,
        bookAccentPinned = state.accentPinned,
    ) {
        val darkTheme = MaterialTheme.colorScheme.surface.luminanceIsDark()
        val isAmoled = state.settings.themeMode == com.aprireader.app.data.prefs.ThemeMode.AMOLED || state.settings.pureBlackDark
        val palette = readerPalette(
            style = state.pageStyle,
            scheme = MaterialTheme.colorScheme,
            accent = MaterialTheme.colorScheme.primary,
            darkTheme = darkTheme,
            pureBlack = isAmoled,
        )

        var typographySheet by remember { mutableStateOf(false) }
        var tocSheet by remember { mutableStateOf(false) }
        var searchSheet by remember { mutableStateOf(false) }
        var blockMenuFor by remember { mutableStateOf<Int?>(null) }
        val listState = rememberLazyListState()
        val pagerState = rememberPagerState(initialPage = state.pageIndex) { state.pageCount.coerceAtLeast(1) }
        val continuousPdfListState = rememberLazyListState(initialFirstVisibleItemIndex = state.pageIndex)
        val scope = rememberCoroutineScopeCompat()
        var pagedTextNextPage by remember { mutableStateOf<(() -> Unit)?>(null) }
        var pagedTextPrevPage by remember { mutableStateOf<(() -> Unit)?>(null) }

        KeepScreenOn(enabled = state.settings.reader.keepScreenOn)
        ImmersiveReading(
            enabled = state.settings.reader.fullscreen && !state.loading,
            chromeVisible = state.chromeVisible,
        )

        // Перекрытие между «страницами» — примерно две строки текущего кегля.
        val pageOverlapPx = with(LocalDensity.current) {
            (state.typography.fontSizeSp * state.typography.lineHeight * 2f).dp.toPx()
        }

        val goToPage: (Int) -> Unit = { targetPage ->
            val bounded = targetPage.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
            scope.launch {
                if (state.settings.reader.scrollMode == ReadingScrollMode.CONTINUOUS_VERTICAL) {
                    continuousPdfListState.animateScrollToItem(bounded)
                } else {
                    pagerState.animateScrollToPage(bounded)
                }
            }
        }

        val onNextPage: () -> Unit = {
            val current = if (state.settings.reader.scrollMode == ReadingScrollMode.CONTINUOUS_VERTICAL) {
                continuousPdfListState.firstVisibleItemIndex
            } else {
                pagerState.currentPage
            }
            goToPage(current + 1)
        }

        val onPreviousPage: () -> Unit = {
            val current = if (state.settings.reader.scrollMode == ReadingScrollMode.CONTINUOUS_VERTICAL) {
                continuousPdfListState.firstVisibleItemIndex
            } else {
                pagerState.currentPage
            }
            goToPage(current - 1)
        }

        val isContinuousScroll = state.settings.reader.scrollMode == ReadingScrollMode.CONTINUOUS_VERTICAL
        VolumeKeyPaging(
            enabled = (state.settings.reader.volumeKeysPaging || state.isPaged || !isContinuousScroll) && state.mode != ReaderMode.RSVP,
            onPage = { forward ->
                when {
                    state.isPaged -> if (forward) onNextPage() else onPreviousPage()
                    isContinuousScroll -> scope.pageReader(listState, forward, pageOverlapPx)
                    forward -> pagedTextNextPage?.invoke()
                    else -> pagedTextPrevPage?.invoke()
                }
            },
        )

        // Пейджер и список создаются до того, как книга прочитана с диска, поэтому
        // сохранённую страницу нужно восстановить отдельно — и ровно один раз,
        // иначе листание будет отбрасывать читателя назад.
        var pageRestored by remember { mutableStateOf(false) }
        LaunchedEffect(state.loading, state.pageCount) {
            if (!pageRestored && !state.loading && state.pageCount > 0 && state.isPaged) {
                val target = state.pageIndex.coerceIn(0, state.pageCount - 1)
                pagerState.scrollToPage(target)
                continuousPdfListState.scrollToItem(target)
                pageRestored = true
            }
        }

        LaunchedEffect(state.settings.reader.scrollMode) {
            if (pageRestored && state.isPaged && state.pageCount > 0) {
                val target = state.pageIndex.coerceIn(0, state.pageCount - 1)
                if (state.settings.reader.scrollMode == ReadingScrollMode.CONTINUOUS_VERTICAL) {
                    continuousPdfListState.scrollToItem(target)
                } else {
                    pagerState.scrollToPage(target)
                }
            }
        }

        BackHandler(enabled = state.mode == ReaderMode.RSVP) { viewModel.exitRsvp() }

        // Ошибки, случившиеся при уже открытой книге (нет текстового слоя в PDF,
        // нечего озвучивать), нельзя показывать полноэкранным экраном ошибки —
        // читатель потерял бы страницу. Для них снекбар.
        val snackbarHostState = remember { SnackbarHostState() }
        LaunchedEffect(state.error) {
            val message = state.error ?: return@LaunchedEffect
            if (state.blocks.isNotEmpty() || state.pageCount > 0) {
                snackbarHostState.showSnackbar(message)
                viewModel.consumeError()
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(palette.background),
        ) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = palette.accent)
                }

                state.error != null && state.blocks.isEmpty() && state.pageCount == 0 -> ReaderError(
                    message = state.error!!,
                    onBack = onBack,
                )

                state.mode == ReaderMode.RSVP -> RsvpReader(
                    state = state,
                    palette = palette,
                    onTogglePlayback = viewModel::toggleRsvpPlayback,
                    onSeek = viewModel::rsvpSeek,
                    onStepBack = { viewModel.rsvpStepBack() },
                    onSpeedChange = viewModel::setRsvpSpeed,
                    onExit = viewModel::exitRsvp,
                )

                state.isPaged -> PagedReader(
                    state = state,
                    palette = palette,
                    pagerState = pagerState,
                    continuousListState = continuousPdfListState,
                    onPageChanged = { page -> if (pageRestored) viewModel.onPageChanged(page) },
                    onNextPage = onNextPage,
                    onPreviousPage = onPreviousPage,
                    onToggleChrome = viewModel::toggleChrome,
                    loadComicPage = viewModel::pageBytes,
                    renderPdfPage = { page, width -> viewModel.renderPdfPage(page, width) },
                )

                state.settings.reader.scrollMode == ReadingScrollMode.CONTINUOUS_VERTICAL -> Box(
                    Modifier
                        .fillMaxSize()
                        .readerTapZones(
                            pagingEnabled = state.settings.reader.tapZonesPaging || state.settings.reader.horizontalPaging,
                            onToggleChrome = viewModel::toggleChrome,
                            onPage = { forward ->
                                scope.pageReader(
                                    listState = listState,
                                    forward = forward,
                                    overlapPx = pageOverlapPx,
                                    continuousReading = state.settings.reader.continuousReading,
                                    onNextChapter = viewModel::nextChapter,
                                    onPreviousChapter = viewModel::previousChapter,
                                )
                            },
                        ),
                ) {
                    val handleTap: (Float) -> Unit = { fractionX ->
                        val pagingOn = state.settings.reader.tapZonesPaging || state.settings.reader.horizontalPaging
                        when {
                            !pagingOn -> viewModel.toggleChrome()
                            fractionX < 0.28f -> scope.pageReader(
                                listState = listState,
                                forward = false,
                                overlapPx = pageOverlapPx,
                                continuousReading = state.settings.reader.continuousReading,
                                onNextChapter = viewModel::nextChapter,
                                onPreviousChapter = viewModel::previousChapter,
                            )
                            fractionX > 0.72f -> scope.pageReader(
                                listState = listState,
                                forward = true,
                                overlapPx = pageOverlapPx,
                                continuousReading = state.settings.reader.continuousReading,
                                onNextChapter = viewModel::nextChapter,
                                onPreviousChapter = viewModel::previousChapter,
                            )
                            else -> viewModel.toggleChrome()
                        }
                    }
                    FlowReader(
                        state = state,
                        palette = palette,
                        listState = listState,
                        onScrollFraction = viewModel::onScrollFraction,
                        onNextChapter = viewModel::nextChapter,
                        onPreviousChapter = viewModel::previousChapter,
                        loadResource = viewModel::resource,
                        highlightedBlocks = state.marks
                            .filter { it.unit == state.chapterIndex && it.kind != "BOOKMARK" }
                            .map { it.startOffset }
                            .toSet(),
                        onBlockLongPress = { blockMenuFor = it },
                        onBlockTap = handleTap,
                        speakingBlock = state.speakingBlock,
                        scrollToBlock = state.scrollToBlock,
                        onScrollTargetConsumed = viewModel::consumeScrollTarget,
                    )
                }

                else -> PagedTextReader(
                    state = state,
                    palette = palette,
                    onScrollFraction = viewModel::onScrollFraction,
                    onNextChapter = viewModel::nextChapter,
                    onPreviousChapter = viewModel::previousChapter,
                    onToggleChrome = viewModel::toggleChrome,
                    loadResource = viewModel::resource,
                    highlightedBlocks = state.marks
                        .filter { it.unit == state.chapterIndex && it.kind != "BOOKMARK" }
                        .map { it.startOffset }
                        .toSet(),
                    onBlockLongPress = { blockMenuFor = it },
                    speakingBlock = state.speakingBlock,
                    isVerticalPaged = state.settings.reader.scrollMode == ReadingScrollMode.PAGED_VERTICAL,
                    scrollToBlock = state.scrollToBlock,
                    onScrollTargetConsumed = viewModel::consumeScrollTarget,
                    onBindPageControls = { next, prev ->
                        pagedTextNextPage = next
                        pagedTextPrevPage = prev
                    },
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp),
            )

            // Панель озвучивания живёт отдельно от остального управления:
            // выключить голос нужно уметь в любой момент.
            val context = androidx.compose.ui.platform.LocalContext.current
            if (state.speakingBlock != null || tts.speaking || tts.initializing) {
                SpeechBar(
                    tts = tts,
                    chapterTitle = state.chapterTitle,
                    onToggle = { viewModel.toggleSpeaking(listState.firstVisibleItemIndex.coerceAtLeast(0)) },
                    onSkip = viewModel::skipSpeaking,
                    onRate = viewModel::setSpeechRate,
                    onStop = viewModel::stopSpeaking,
                    onOpenSettings = {
                        val intent = android.content.Intent("com.android.settings.TTS_SETTINGS").apply {
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        val fallback = android.content.Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        val launched = runCatching {
                            context.startActivity(intent)
                            true
                        }.getOrDefault(false)
                        if (!launched) {
                            runCatching { context.startActivity(fallback) }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // Когда открыто управление, панель голоса встаёт над ним,
                        // а не поверх — иначе кнопки перекрывают друг друга.
                        .padding(bottom = if (state.chromeVisible) 168.dp else 0.dp),
                )
            }

            // Когда управление скрыто, о положении в книге напоминает только
            // тонкая полоса внизу — если пользователь её не выключил.
            if (state.settings.reader.showProgressBar && !state.chromeVisible &&
                state.mode != ReaderMode.RSVP && !state.loading
            ) {
                ReadingProgressLine(
                    progress = state.progress,
                    palette = palette,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            if (state.mode != ReaderMode.RSVP && !state.loading) {
                ReaderChrome(
                    visible = state.chromeVisible,
                    state = state,
                    onBack = {
                        viewModel.saveProgress()
                        onBack()
                    },
                    onOpenToc = { tocSheet = true },
                    onOpenSearch = { searchSheet = true },
                    onAddBookmark = {
                        viewModel.addBookmark(listState.firstVisibleItemIndex)
                    },
                    onOpenTypography = { typographySheet = true },
                    onToggleBionic = viewModel::toggleBionic,
                    onStartRsvp = viewModel::enterRsvp,
                    onToggleSpeech = { viewModel.toggleSpeaking(listState.firstVisibleItemIndex.coerceAtLeast(0)) },
                    onTogglePdfText = viewModel::togglePdfTextMode,
                    speaking = tts.speaking || state.speakingBlock != null,
                    ttsInitializing = tts.initializing,
                    onSeekChapter = { fraction ->
                        if (state.isPaged) {
                            val page = (fraction * (state.pageCount - 1)).toInt()
                            goToPage(page)
                        } else {
                            viewModel.openChapter((fraction * (state.chapters.size - 1)).toInt())
                        }
                    },
                    onNextPage = onNextPage,
                    onPreviousPage = onPreviousPage,
                    onToggleScrollMode = viewModel::toggleFlowPagingMode,
                )
            }
        }

        if (typographySheet) {
            val presets by viewModel.presets.collectAsStateWithLifecycle(emptyList())
            val customFonts by viewModel.customFonts.collectAsStateWithLifecycle(emptyList())
            val context = androidx.compose.ui.platform.LocalContext.current
            TypographySheet(
                presets = presets,
                customFonts = customFonts,
                onApplyPreset = viewModel::applyPreset,
                onSavePreset = viewModel::savePreset,
                onDeletePreset = viewModel::deletePreset,
                onImportFont = { uri, contentResolver ->
                    viewModel.importCustomFont(uri, contentResolver) { success, fontName ->
                        if (success) {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.msg_font_imported, fontName ?: ""),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.err_font_import_failed),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                },
                onDeleteCustomFont = viewModel::deleteCustomFont,
                state = state,
                onDismiss = { typographySheet = false },
                onTypographyChange = viewModel::updateTypography,
                onReaderChange = viewModel::updateReader,
                onPinAccent = viewModel::pinAccent,
                onUnpinAccent = viewModel::unpinAccent,
                onBionicIntensity = viewModel::setBionicIntensity,
                onToggleBionic = viewModel::toggleBionic,
            )
        }

        if (searchSheet) {
            SearchSheet(
                state = state,
                onQueryChange = viewModel::setSearchQuery,
                onSearch = viewModel::runSearch,
                onOpenHit = { hit ->
                    searchSheet = false
                    viewModel.openHit(hit)
                },
                onDismiss = {
                    searchSheet = false
                    viewModel.cancelSearch()
                },
            )
        }

        blockMenuFor?.let { blockIndex ->
            BlockActionsDialog(
                onDismiss = { blockMenuFor = null },
                onHighlight = {
                    blockMenuFor = null
                    viewModel.highlightBlock(blockIndex)
                },
                onBookmark = {
                    blockMenuFor = null
                    viewModel.addBookmark(blockIndex)
                },
            )
        }

        if (tocSheet) {
            TableOfContentsSheet(
                state = state,
                onDismiss = { tocSheet = false },
                onSelectChapter = {
                    tocSheet = false
                    viewModel.openChapter(it)
                },
                onSelectPage = { page ->
                    tocSheet = false
                    goToPage(page)
                },
            )
        }
    }
}

@Composable
private fun ReaderChrome(
    visible: Boolean,
    state: ReaderUiState,
    onBack: () -> Unit,
    onOpenToc: () -> Unit,
    onOpenSearch: () -> Unit,
    onAddBookmark: () -> Unit,
    onOpenTypography: () -> Unit,
    onToggleBionic: () -> Unit,
    onStartRsvp: () -> Unit,
    onToggleSpeech: () -> Unit,
    onTogglePdfText: () -> Unit,
    speaking: Boolean,
    ttsInitializing: Boolean = false,
    onSeekChapter: (Float) -> Unit,
    onNextPage: () -> Unit = {},
    onPreviousPage: () -> Unit = {},
    onToggleScrollMode: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            // Панель не приклеена к краю экрана, а плавает над страницей:
            // так видно, что под стеклом продолжается текст книги.
            GlassPanel(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                shape = SquircleLg,
                level = GlassLevel.Chrome,
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.reader_back_to_shelf))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = state.book?.title.orEmpty(),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        state.chapterTitle?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(onClick = onOpenToc) {
                        Icon(Icons.AutoMirrored.Rounded.List, contentDescription = stringResource(R.string.reader_toc_title))
                    }
                    if (state.supportsTextModes && state.chapters.isNotEmpty()) {
                        IconButton(onClick = onOpenSearch) {
                            Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.reader_search_title))
                        }
                        IconButton(onClick = onAddBookmark) {
                            Icon(Icons.Rounded.BookmarkAdd, contentDescription = stringResource(R.string.reader_action_bookmark))
                        }
                    }
                    IconButton(onClick = onOpenTypography) {
                        Icon(Icons.Rounded.FormatSize, contentDescription = stringResource(R.string.reader_presets_title))
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            GlassPanel(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                shape = SquircleLg,
                level = GlassLevel.Chrome,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = positionLabel(state),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${(state.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    val seekLabel = stringResource(R.string.rsvp_seek)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.isPaged) {
                            IconButton(
                                onClick = onPreviousPage,
                                enabled = state.pageIndex > 0,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.reader_prev_chapter),
                                )
                            }
                        }
                        Slider(
                            value = state.progress.coerceIn(0f, 1f),
                            onValueChange = onSeekChapter,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = seekLabel },
                        )
                        if (state.isPaged) {
                            IconButton(
                                onClick = onNextPage,
                                enabled = state.pageIndex < state.pageCount - 1,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = stringResource(R.string.reader_next_chapter),
                                )
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (state.supportsTextModes) {
                            ChromeAction(
                                icon = Icons.Rounded.Bolt,
                                label = stringResource(R.string.reader_bionic_reading),
                                active = state.focus.bionicEnabled,
                                onClick = onToggleBionic,
                                modifier = Modifier.weight(1f),
                            )
                            ChromeAction(
                                icon = Icons.Rounded.Speed,
                                label = "RSVP",
                                active = false,
                                onClick = onStartRsvp,
                                modifier = Modifier.weight(1f),
                            )
                            ChromeAction(
                                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                                label = when {
                                    ttsInitializing -> stringResource(R.string.tts_speaking_title) + "…"
                                    speaking -> stringResource(R.string.reader_action_pause)
                                    else -> stringResource(R.string.tts_title)
                                },
                                active = speaking,
                                onClick = onToggleSpeech,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (state.isPdf) {
                            ChromeAction(
                                icon = Icons.AutoMirrored.Rounded.Article,
                                label = if (state.isPaged) stringResource(R.string.reader_nav_scroll) else stringResource(R.string.reader_nav_page),
                                active = !state.isPaged,
                                onClick = onTogglePdfText,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            val isScroll = state.settings.reader.scrollMode == ReadingScrollMode.CONTINUOUS_VERTICAL
                            ChromeAction(
                                icon = if (isScroll) Icons.Rounded.SwapHoriz else Icons.Rounded.SwapVert,
                                label = if (isScroll) stringResource(R.string.reader_scroll_action_paged) else stringResource(R.string.reader_scroll_action_scroll),
                                active = !isScroll,
                                onClick = onToggleScrollMode,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        ChromeAction(
                            icon = Icons.Rounded.Palette,
                            label = stringResource(R.string.reader_presets_title),
                            active = false,
                            onClick = onOpenTypography,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChromeAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = SquircleSm,
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (active) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Действия над абзацем по долгому нажатию. */
@Composable
private fun BlockActionsDialog(
    onDismiss: () -> Unit,
    onHighlight: () -> Unit,
    onBookmark: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_block_actions_title)) },
        text = { Text(stringResource(R.string.reader_block_actions_text)) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onHighlight) { Text(stringResource(R.string.reader_action_highlight)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onBookmark) { Text(stringResource(R.string.reader_action_bookmark)) }
        },
    )
}

@Composable
private fun ReaderError(message: String, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.reader_error_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack) { Text(stringResource(R.string.reader_back_to_shelf)) }
    }
}

@Composable
private fun positionLabel(state: ReaderUiState): String = when {
    state.isPaged -> stringResource(R.string.reader_page_of, state.pageIndex + 1, state.pageCount)
    state.chapters.isNotEmpty() -> stringResource(R.string.reader_chapter_of, state.chapterIndex + 1, state.chapters.size)
    else -> ""
}

/** Не гасить экран во время чтения, если пользователь этого просил. */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun rememberCoroutineScopeCompat() = androidx.compose.runtime.rememberCoroutineScope()

private fun androidx.compose.ui.graphics.Color.luminanceIsDark(): Boolean =
    (0.299 * red + 0.587 * green + 0.114 * blue) < 0.5
