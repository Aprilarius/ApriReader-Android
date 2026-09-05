package com.aprireader.app.ui.reader

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.aprireader.app.MainActivity
import com.aprireader.app.ui.theme.ReaderPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.aprireader.app.data.prefs.findActivity

/**
 * Погружение: системные панели прячутся во время чтения и возвращаются вместе с
 * управлением. Свайп от края временно показывает их.
 */
@Composable
fun ImmersiveReading(enabled: Boolean, chromeVisible: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current

    DisposableEffect(enabled, chromeVisible) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (enabled && !chromeVisible) {
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            window?.let { WindowCompat.getInsetsController(it, view) }
                ?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/**
 * Кнопки громкости как перелистывание.
 */
@Composable
fun VolumeKeyPaging(enabled: Boolean, onPage: (forward: Boolean) -> Unit) {
    val context = LocalContext.current

    DisposableEffect(enabled) {
        val activity = context.findActivity() as? MainActivity
        if (enabled && activity != null) {
            activity.setVolumeKeyHandler { forward ->
                onPage(forward)
                true
            }
        }
        onDispose { activity?.setVolumeKeyHandler(null) }
    }
}

/**
 * Зоны нажатия: края листают вперед/назад, центр показывает управление.
 */
fun Modifier.readerTapZones(
    pagingEnabled: Boolean,
    onToggleChrome: () -> Unit,
    onPage: (forward: Boolean) -> Unit,
): Modifier = pointerInput(pagingEnabled) {
    detectTapGestures { offset ->
        if (!pagingEnabled) {
            onToggleChrome()
            return@detectTapGestures
        }
        val width = size.width
        when {
            offset.x < width * 0.28f -> onPage(false)
            offset.x > width * 0.72f -> onPage(true)
            else -> onToggleChrome()
        }
    }
}

/**
 * Перелистывание в потоковом режиме с поддержкой непрерывного чтения от начала до конца книги.
 */
suspend fun LazyListState.pageBy(forward: Boolean, overlapPx: Float = 0f) {
    val viewport = layoutInfo.viewportSize.height.toFloat()
    if (viewport <= 0f) return
    val distance = (viewport - overlapPx).coerceAtLeast(viewport * 0.5f)
    animateScrollBy(if (forward) distance else -distance)
}

fun CoroutineScope.pageReader(
    listState: LazyListState,
    forward: Boolean,
    overlapPx: Float,
    continuousReading: Boolean = false,
    onNextChapter: (() -> Unit)? = null,
    onPreviousChapter: (() -> Unit)? = null,
) {
    launch {
        val layout = listState.layoutInfo
        val total = layout.totalItemsCount
        val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
        val firstVisible = layout.visibleItemsInfo.firstOrNull()?.index ?: 0

        if (forward) {
            if (total > 0 && lastVisible >= total - 1 && continuousReading && onNextChapter != null) {
                onNextChapter()
            } else {
                listState.pageBy(true, overlapPx)
            }
        } else {
            if (firstVisible == 0 && listState.firstVisibleItemScrollOffset == 0 && continuousReading && onPreviousChapter != null) {
                onPreviousChapter()
            } else {
                listState.pageBy(false, overlapPx)
            }
        }
    }
}

/** Тонкая полоса прогресса, видимая когда управление скрыто. */
@Composable
fun ReadingProgressLine(progress: Float, palette: ReaderPalette, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(palette.secondaryText.copy(alpha = 0.15f)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(palette.accent.copy(alpha = 0.75f)),
        )
    }
}
