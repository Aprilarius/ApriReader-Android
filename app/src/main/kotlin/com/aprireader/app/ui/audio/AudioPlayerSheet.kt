package com.aprireader.app.ui.audio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Replay30
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aprireader.app.R
import com.aprireader.app.data.audio.AudioPlayerState
import com.aprireader.app.ui.theme.SquircleLg
import com.aprireader.app.ui.theme.SquircleMd
import com.aprireader.app.ui.theme.SquircleSm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerSheet(
    state: AudioPlayerState,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onToggle: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSleepTimerChange: (Int?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val book = state.currentBook ?: return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Верхняя панель: формат и кнопка закрыть
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = SquircleSm,
                ) {
                    Text(
                        text = book.format.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Закрыть")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Обложка книги с мягкой тенью
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .shadow(12.dp, shape = SquircleLg, spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    .clip(SquircleLg)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                if (book.coverPath != null) {
                    AsyncImage(
                        model = book.coverPath,
                        contentDescription = book.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(200.dp),
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Rounded.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Название и автор
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = book.authorLine.ifBlank { stringResource(R.string.group_no_author) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(20.dp))

            // Ползунок перемотки и время
            val durationMs = if (state.durationMs > 0L) state.durationMs else (book.totalChars * 1000L)
            val currentMs = if (state.durationMs > 0L || state.currentPositionMs > 0L) state.currentPositionMs else book.locatorOffset.toLong()
            val progress = if (durationMs > 0L) (currentMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else book.progress

            var isDragging by remember { mutableStateOf(false) }
            var dragProgress by remember { mutableFloatStateOf(0f) }

            val currentProgress = if (isDragging) dragProgress else progress
            val displayCurrentMs = if (isDragging) (dragProgress * durationMs).toLong() else currentMs

            Slider(
                value = currentProgress,
                onValueChange = {
                    isDragging = true
                    dragProgress = it
                },
                onValueChangeFinished = {
                    isDragging = false
                    val targetMs = (dragProgress * durationMs).toLong()
                    onSeekTo(targetMs)
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatTime(displayCurrentMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatTime(durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Кнопки управления (-15, Play/Pause, +15)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { onSeekBy(-15_000L) },
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        Icons.Rounded.FastRewind,
                        contentDescription = "-15 сек",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp),
                    )
                }

                Spacer(Modifier.width(20.dp))

                Surface(
                    onClick = onToggle,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(68.dp),
                    shadowElevation = 6.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (state.isBuffering) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp,
                            )
                        } else {
                            Icon(
                                if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (state.isPlaying) "Пауза" else "Воспроизведение",
                                modifier = Modifier.size(38.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.width(20.dp))

                IconButton(
                    onClick = { onSeekBy(15_000L) },
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        Icons.Rounded.FastForward,
                        contentDescription = "+15 сек",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Скорость воспроизведения
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { s ->
                    FilterChip(
                        selected = state.speed == s,
                        onClick = { onSpeedChange(s) },
                        label = { Text("${s}x") },
                        shape = SquircleSm,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Таймер сна
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Bedtime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                FilterChip(
                    selected = state.sleepTimerMinutes == null,
                    onClick = { onSleepTimerChange(null) },
                    label = { Text("Выкл") },
                    shape = SquircleSm,
                )
                listOf(5, 15, 30, 45, 60).forEach { mins ->
                    val isSelected = state.sleepTimerMinutes == mins
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSleepTimerChange(if (isSelected) null else mins) },
                        label = {
                            if (isSelected && state.sleepTimerRemainingSec != null) {
                                val remMin = state.sleepTimerRemainingSec / 60
                                val remSec = state.sleepTimerRemainingSec % 60
                                Text("%02d:%02d".format(remMin, remSec))
                            } else {
                                Text("$mins мин")
                            }
                        },
                        shape = SquircleSm,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms.coerceAtLeast(0L) / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
