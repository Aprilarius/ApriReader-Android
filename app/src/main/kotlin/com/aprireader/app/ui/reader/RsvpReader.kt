package com.aprireader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.aprireader.app.ui.common.GlassPanel
import com.aprireader.app.ui.theme.GlassLevel
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aprireader.app.ui.theme.SquircleMd
import com.aprireader.app.ui.theme.SquirclePill
import com.aprireader.app.ui.theme.ReaderPalette
import com.aprireader.app.ui.theme.resolveReadingFont
import androidx.compose.ui.res.stringResource
import com.aprireader.app.R

/**
 * Режим RSVP: слова показываются по одному в фиксированной точке экрана.
 *
 * Ключевая деталь — точка фиксации (ORP): слово выравнивается так, чтобы
 * «якорная» буква всегда оказывалась в одном и том же месте. Без этого глаз
 * вынужден искать слово заново на каждом такте, и режим быстро утомляет.
 */
@Composable
fun RsvpReader(
    state: ReaderUiState,
    palette: ReaderPalette,
    onTogglePlayback: () -> Unit,
    onSeek: (Int) -> Unit,
    onStepBack: () -> Unit,
    onSpeedChange: (Int) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val frame = state.rsvp.current

    Box(
        modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        IconButton(
            onClick = onExit,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        ) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.rsvp_close), tint = palette.secondaryText)
        }

        Column(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FocusGuides(palette)

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (frame != null) {
                    val style = TextStyle(
                        fontFamily = resolveReadingFont(state.typography),
                        fontSize = (state.typography.fontSizeSp * 2.1f).sp,
                        color = palette.text,
                    )
                    val measurer = rememberTextMeasurer()
                    // Слово сдвигается так, чтобы буква-якорь всегда стояла в центре:
                    // именно неподвижная точка фиксации и делает RSVP выносимым.
                    val pivotOffset = remember(frame.text, style) {
                        val prefix = frame.text.take(frame.pivotIndex)
                        val prefixWidth = if (prefix.isEmpty()) 0 else measurer.measure(prefix, style).size.width
                        val pivotChar = frame.text.getOrNull(frame.pivotIndex)?.toString().orEmpty()
                        val pivotWidth = if (pivotChar.isEmpty()) 0 else measurer.measure(pivotChar, style).size.width
                        val total = measurer.measure(frame.text, style).size.width
                        (total / 2f - (prefixWidth + pivotWidth / 2f)).toInt()
                    }
                    Text(
                        text = pivotAnnotated(frame.text, frame.pivotIndex, palette, state.focus.rsvpHighlightPivot),
                        style = style,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .offset { IntOffset(pivotOffset, 0) }
                            .semantics {
                                liveRegion = LiveRegionMode.Polite
                                contentDescription = frame.text
                            },
                    )
                }
            }

            FocusGuides(palette)

            Spacer(Modifier.height(40.dp))

            Text(
                text = stringResource(R.string.rsvp_progress_format, state.rsvp.index + 1, state.rsvp.frames.size),
                style = MaterialTheme.typography.labelMedium,
                color = palette.secondaryText,
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            shape = SquircleMd,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            val seekDescription = stringResource(R.string.rsvp_seek)
            val speedDescription = stringResource(R.string.rsvp_speed_wpm)
            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Slider(
                    value = state.rsvp.index.toFloat(),
                    onValueChange = { onSeek(it.toInt()) },
                    valueRange = 0f..(state.rsvp.frames.size - 1).coerceAtLeast(1).toFloat(),
                    modifier = Modifier.semantics { contentDescription = seekDescription },
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onStepBack) {
                        Icon(Icons.Rounded.Replay, contentDescription = stringResource(R.string.rsvp_rewind))
                    }
                    Surface(
                        shape = SquirclePill,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp),
                    ) {
                        IconButton(onClick = onTogglePlayback) {
                            Icon(
                                imageVector = if (state.rsvp.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (state.rsvp.running) stringResource(R.string.reader_action_pause) else stringResource(R.string.reader_action_play),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${state.focus.rsvpWpm}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.rsvp_wpm),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Slider(
                    value = state.focus.rsvpWpm.toFloat(),
                    onValueChange = { onSpeedChange(it.toInt()) },
                    valueRange = 100f..900f,
                    steps = 15,
                    modifier = Modifier.semantics { contentDescription = speedDescription },
                )
            }
        }
    }
}

/** Направляющие сверху и снизу — глаз держится за них между тактами. */
@Composable
private fun FocusGuides(palette: ReaderPalette) {
    Box(
        Modifier
            .width(2.dp)
            .height(14.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(palette.accent.copy(alpha = 0.65f)),
    )
}

/** Выделяет букву-якорь цветом акцента. */
private fun pivotAnnotated(
    text: String,
    pivotIndex: Int,
    palette: ReaderPalette,
    highlight: Boolean,
): AnnotatedString = buildAnnotatedString {
    append(text)
    if (highlight && pivotIndex in text.indices) {
        addStyle(
            SpanStyle(color = palette.accent, fontWeight = FontWeight.Bold),
            pivotIndex,
            pivotIndex + 1,
        )
    }
}
