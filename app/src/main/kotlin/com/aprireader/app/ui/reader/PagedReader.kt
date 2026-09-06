package com.aprireader.app.ui.reader

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aprireader.app.R
import com.aprireader.app.data.prefs.ReadingScrollMode
import com.aprireader.app.ui.theme.ReaderPalette
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Состояние загрузки одной страницы PDF/комикса — отдельно от «ещё грузится»
 * держит «декодировать не удалось», чтобы вечный спиннер не выдавался за
 * загрузку, которая на самом деле уже завершилась ошибкой.
 */
private sealed interface PageLoadState {
    data object Loading : PageLoadState
    data class Loaded(val bitmap: ImageBitmap) : PageLoadState
    data object Failed : PageLoadState
}

/**
 * Постраничный и непрерывный режим для PDF и комиксов.
 * Поддерживает:
 * 1. Непрерывный вертикальный скролл (CONTINUOUS_VERTICAL).
 * 2. Горизонтальное листание свайпами (PAGED_HORIZONTAL).
 * 3. Вертикальное листание свайпами (PAGED_VERTICAL).
 * 4. Тап-зоны по краям экрана для перелистывания.
 * 5. Двойной тап и щипок для зума без блокировки перелистывания.
 */
@Composable
fun PagedReader(
    state: ReaderUiState,
    palette: ReaderPalette,
    pagerState: PagerState = rememberPagerState(initialPage = state.pageIndex) { state.pageCount.coerceAtLeast(1) },
    continuousListState: LazyListState = rememberLazyListState(initialFirstVisibleItemIndex = state.pageIndex),
    onPageChanged: (Int) -> Unit,
    onNextPage: () -> Unit = {},
    onPreviousPage: () -> Unit = {},
    onToggleChrome: () -> Unit,
    loadComicPage: suspend (Int) -> ByteArray?,
    renderPdfPage: suspend (Int, Int) -> android.graphics.Bitmap?,
    modifier: Modifier = Modifier,
) {
    val isContinuous = state.settings.reader.scrollMode == ReadingScrollMode.CONTINUOUS_VERTICAL

    LaunchedEffect(isContinuous, pagerState) {
        if (!isContinuous) {
            snapshotFlow { pagerState.currentPage }
                .distinctUntilChanged()
                .collect(onPageChanged)
        }
    }

    LaunchedEffect(isContinuous, continuousListState) {
        if (isContinuous) {
            snapshotFlow { continuousListState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collect(onPageChanged)
        }
    }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val targetWidthPx = remember(configuration.screenWidthDp, density) {
        val widthPx = with(density) { configuration.screenWidthDp.dp.toPx() }.toInt()
        widthPx.coerceIn(1080, 2048)
    }

    val estimatedPageHeightDp = remember(configuration.screenWidthDp) {
        (configuration.screenWidthDp * 1.414f).dp
    }

    val handleTap: (Offset, Float) -> Unit = { offset, width ->
        if (state.settings.reader.tapZonesPaging) {
            val fractionX = offset.x / width
            when {
                fractionX < 0.28f -> onPreviousPage()
                fractionX > 0.72f -> onNextPage()
                else -> onToggleChrome()
            }
        } else {
            onToggleChrome()
        }
    }

    val pageContent: @Composable (Int, Boolean) -> Unit = { page, forContinuous ->
        // Раньше здесь был ImageBitmap? — и «страница ещё грузится», и «страницу
        // не удалось декодировать вообще никогда» выглядели одинаково: вечный
        // спиннер без единого отличия. Пользователь видел «бесконечную
        // загрузку» именно там, где на самом деле загрузка уже завершилась
        // неудачей. PageLoadState даёт этим двум состояниям разный вид.
        val pageState by produceState<PageLoadState>(initialValue = PageLoadState.Loading, page, state.book?.id) {
            value = runCatching {
                if (state.isPdf || state.book?.format?.name == "PDF") {
                    renderPdfPage(page, targetWidthPx)?.asImageBitmap()
                } else {
                    loadComicPage(page)?.let { bytes ->
                        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        var sample = 1
                        val maxDim = targetWidthPx.coerceAtLeast(1080) * 2
                        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim * 2) {
                            sample *= 2
                        }
                        val opts = android.graphics.BitmapFactory.Options().apply {
                            inSampleSize = sample
                            inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                        }
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
                    }
                }
            }.getOrElse { error ->
                Log.w("PagedReader", "Failed to load page $page of \"${state.book?.fileName}\"", error)
                null
            }?.let { PageLoadState.Loaded(it) } ?: run {
                Log.w("PagedReader", "Page $page of \"${state.book?.fileName}\" decoded to null")
                PageLoadState.Failed
            }
        }

        ZoomablePage(
            onTap = handleTap,
            modifier = if (forContinuous) {
                Modifier
                    .fillMaxWidth()
                    .then(if (pageState is PageLoadState.Loaded) Modifier.wrapContentHeight() else Modifier.height(estimatedPageHeightDp))
            } else {
                Modifier.fillMaxSize()
            },
        ) {
            when (val current = pageState) {
                is PageLoadState.Loaded -> Image(
                    bitmap = current.bitmap,
                    contentDescription = stringResource(R.string.reader_page_of, page + 1, state.pageCount),
                    contentScale = if (forContinuous) ContentScale.FillWidth else ContentScale.Fit,
                    modifier = if (forContinuous) {
                        Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    } else {
                        Modifier.fillMaxSize()
                    },
                )
                PageLoadState.Loading -> Box(
                    modifier = if (forContinuous) {
                        Modifier
                            .fillMaxWidth()
                            .height(estimatedPageHeightDp)
                    } else {
                        Modifier.fillMaxSize()
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = palette.accent)
                }
                PageLoadState.Failed -> Box(
                    modifier = if (forContinuous) {
                        Modifier
                            .fillMaxWidth()
                            .height(estimatedPageHeightDp)
                    } else {
                        Modifier.fillMaxSize()
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.BrokenImage,
                            contentDescription = null,
                            tint = palette.accent.copy(alpha = 0.7f),
                        )
                        Text(
                            text = stringResource(R.string.reader_page_load_failed, page + 1),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.accent.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }

    when (state.settings.reader.scrollMode) {
        ReadingScrollMode.CONTINUOUS_VERTICAL -> {
            LazyColumn(
                state = continuousListState,
                modifier = modifier
                    .fillMaxSize()
                    .background(palette.background),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                items(state.pageCount, key = { it }) { page ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        pageContent(page, true)
                    }
                }
            }
        }

        ReadingScrollMode.PAGED_VERTICAL -> {
            VerticalPager(
                state = pagerState,
                modifier = modifier
                    .fillMaxSize()
                    .background(palette.background),
                beyondViewportPageCount = 1,
                key = { it },
                userScrollEnabled = true,
                pageContent = { page -> pageContent(page, false) },
            )
        }

        else -> {
            HorizontalPager(
                state = pagerState,
                modifier = modifier
                    .fillMaxSize()
                    .background(palette.background),
                beyondViewportPageCount = 1,
                key = { it },
                userScrollEnabled = true,
                pageContent = { page -> pageContent(page, false) },
            )
        }
    }
}

/**
 * Страница с поддержкой масштабирования (двойной тап и щипок).
 * При нормальном размере (scale <= 1.05f) жесты прокрутки и свайпы НЕ перехватываются,
 * что обеспечивает свободное и плавное перелистывание страниц пейджером или списком.
 */
@Composable
private fun ZoomablePage(
    onTap: (Offset, Float) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var scale by rememberSaveable { mutableFloatStateOf(1f) }
    var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable { mutableFloatStateOf(0f) }

    val isZoomed = scale > 1.05f

    Box(
        modifier
            .clipToBounds()
            .pointerInput(isZoomed) {
                detectTapGestures(
                    onTap = { offset -> onTap(offset, size.width.toFloat()) },
                    onDoubleTap = { offset ->
                        if (scale > 1.05f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2.4f
                            val maxOffsetX = (size.width * 1.4f) / 2f
                            val maxOffsetY = (size.height * 1.4f) / 2f
                            offsetX = ((size.width / 2f - offset.x) * 1.2f).coerceIn(-maxOffsetX, maxOffsetX)
                            offsetY = ((size.height / 2f - offset.y) * 1.2f).coerceIn(-maxOffsetY, maxOffsetY)
                        }
                    },
                )
            }
            .pointerInput(isZoomed) {
                if (isZoomed) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                        scale = newScale
                        if (newScale > 1.05f) {
                            val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                            val maxOffsetY = ((size.height * newScale) - size.height).coerceAtLeast(0f) / 2f
                            offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                            offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                        } else {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                } else {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.size >= 2) {
                                val zoomChange = event.calculateZoom()
                                if (zoomChange != 1f) {
                                    scale = (scale * zoomChange).coerceIn(1f, 5f)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
                translationY = offsetY
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
