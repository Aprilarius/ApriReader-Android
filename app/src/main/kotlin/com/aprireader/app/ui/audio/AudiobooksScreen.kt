package com.aprireader.app.ui.audio

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aprireader.app.R
import com.aprireader.app.data.audio.AudioPlayerState
import com.aprireader.app.domain.Book
import com.aprireader.app.ui.library.SUPPORTED_MIME_TYPES
import com.aprireader.app.ui.theme.SquircleLg
import com.aprireader.app.ui.theme.SquircleMd
import com.aprireader.app.ui.theme.SquircleSm
import com.aprireader.app.ui.theme.SquircleXs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobooksScreen(
    viewModel: AudiobooksViewModel,
    onOpenDrawer: () -> Unit,
    onOpenBook: (Book) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler {
        onOpenDrawer()
    }

    val filesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris -> viewModel.addFiles(uris) },
    )

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri -> uri?.let { viewModel.addFolder(it) } },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Аудиокниги",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            Icons.Rounded.Menu,
                            contentDescription = stringResource(R.string.drawer_open_hint),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { filesLauncher.launch(SUPPORTED_MIME_TYPES) }) {
                        Icon(
                            Icons.Rounded.UploadFile,
                            contentDescription = "Добавить файлы аудиокниг",
                        )
                    }
                    IconButton(onClick = { folderLauncher.launch(null) }) {
                        Icon(
                            Icons.Rounded.CreateNewFolder,
                            contentDescription = "Добавить папку с аудиокнигами",
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.audiobooks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                AudioEmptyState(
                    onAddFiles = { filesLauncher.launch(SUPPORTED_MIME_TYPES) },
                    onAddFolder = { folderLauncher.launch(null) },
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Hero player если выбрана или играет книга
                val activeBook = state.playerState.currentBook ?: state.audiobooks.firstOrNull { it.progress > 0f } ?: state.audiobooks.firstOrNull()
                if (activeBook != null) {
                    item(key = "hero_audio_card") {
                        HeroAudioPlayerCard(
                            book = activeBook,
                            playerState = state.playerState,
                            onTogglePlay = {
                                if (state.playerState.currentBook?.id == activeBook.id) {
                                    viewModel.togglePlayPause()
                                } else {
                                    viewModel.playAudiobook(activeBook)
                                }
                            },
                            onSeekBy = viewModel::seekBy,
                            onSpeedChange = viewModel::setSpeed,
                            onCardClick = {
                                if (state.playerState.currentBook?.id != activeBook.id) {
                                    viewModel.playAudiobook(activeBook)
                                }
                                viewModel.openFullPlayer()
                            },
                        )
                    }
                }

                item(key = "section_header_audio") {
                    Text(
                        text = "Все аудиокниги (${state.audiobooks.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }

                items(
                    items = state.audiobooks,
                    key = { it.id },
                ) { book ->
                    val isLoaded = state.playerState.currentBook?.id == book.id
                    val isCurrentlyPlaying = state.playerState.isPlaying && isLoaded

                    AudioBookItemCard(
                        book = book,
                        isPlaying = isCurrentlyPlaying,
                        isLoaded = isLoaded,
                        livePositionMs = if (isLoaded) state.playerState.currentPositionMs else null,
                        liveDurationMs = if (isLoaded) state.playerState.durationMs else null,
                        onPlayClick = {
                            if (isLoaded) {
                                viewModel.togglePlayPause()
                            } else {
                                viewModel.playAudiobook(book)
                            }
                        },
                        onOpenDetails = {
                            if (isLoaded) {
                                viewModel.openFullPlayer()
                            } else {
                                viewModel.playAudiobook(book)
                                viewModel.openFullPlayer()
                            }
                        },
                    )
                }

                item {
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }

    if (state.isFullPlayerOpen && state.playerState.currentBook != null) {
        AudioPlayerSheet(
            state = state.playerState,
            onDismiss = viewModel::closeFullPlayer,
            onPlay = viewModel::play,
            onPause = viewModel::pause,
            onToggle = viewModel::togglePlayPause,
            onSeekTo = viewModel::seekTo,
            onSeekBy = viewModel::seekBy,
            onSpeedChange = viewModel::setSpeed,
            onSleepTimerChange = viewModel::setSleepTimer,
        )
    }
}

@Composable
private fun HeroAudioPlayerCard(
    book: Book,
    playerState: AudioPlayerState,
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onCardClick: () -> Unit,
) {
    val isThisBookPlaying = playerState.isPlaying && playerState.currentBook?.id == book.id
    val isThisBookLoaded = playerState.currentBook?.id == book.id
    val currentMs = if (isThisBookLoaded) playerState.currentPositionMs else book.locatorOffset.toLong()
    val durationMs = if (isThisBookLoaded && playerState.durationMs > 0L) playerState.durationMs else (book.totalChars * 1000L)
    val progress = if (durationMs > 0L) (currentMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else book.progress

    Card(
        shape = SquircleLg,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(SquircleLg)
            .clickable(onClick = onCardClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            // Верх: Обложка и инфо
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(SquircleSm)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    if (book.coverPath != null) {
                        AsyncImage(
                            model = book.coverPath,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            Icons.Rounded.Headphones,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }

                Spacer(Modifier.width(14.dp))

                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                text = book.format.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }

                        if (isThisBookPlaying) {
                            Text(
                                text = "Воспроизведение",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Text(
                        text = book.authorLine.ifBlank { stringResource(R.string.group_no_author) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Прогресс бар
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatTime(currentMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (durationMs > 0) formatTime(durationMs) else "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Центрированные основные кнопки управления (-15s, Большой Play/Pause, +15s)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { onSeekBy(-15_000L) },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Rounded.FastRewind,
                        contentDescription = "-15 сек",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp),
                    )
                }

                Spacer(Modifier.width(20.dp))

                Surface(
                    onClick = onTogglePlay,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(56.dp),
                    shadowElevation = 4.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (playerState.isBuffering && isThisBookLoaded) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.5.dp,
                            )
                        } else {
                            Icon(
                                if (isThisBookPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isThisBookPlaying) "Пауза" else "Воспроизведение",
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.width(20.dp))

                IconButton(
                    onClick = { onSeekBy(15_000L) },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Rounded.FastForward,
                        contentDescription = "+15 сек",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Чипы регулировки скорости
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { s ->
                    FilterChip(
                        selected = playerState.speed == s,
                        onClick = { onSpeedChange(s) },
                        label = { Text("${s}x") },
                        shape = SquircleSm,
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioBookItemCard(
    book: Book,
    isPlaying: Boolean,
    isLoaded: Boolean,
    livePositionMs: Long?,
    liveDurationMs: Long?,
    onPlayClick: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    val durationMs = if (liveDurationMs != null && liveDurationMs > 0L) liveDurationMs else (book.totalChars * 1000L)
    val positionMs = if (livePositionMs != null && (livePositionMs > 0L || liveDurationMs != null)) livePositionMs else book.locatorOffset.toLong()
    val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else book.progress
    val percent = (progress * 100).toInt()

    Surface(
        onClick = onOpenDetails,
        shape = SquircleMd,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(SquircleSm)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (book.coverPath != null) {
                    AsyncImage(
                        model = book.coverPath,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Rounded.Headphones,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = book.authorLine.ifBlank { stringResource(R.string.group_no_author) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(3.dp),
                    ) {
                        Text(
                            text = book.format.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }

                    Text(
                        text = if (percent > 0) "${percent}%" else "Не начато",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (percent > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (durationMs > 0L) {
                        Text(
                            text = if (positionMs > 0L) "• ${formatTime(positionMs)} / ${formatTime(durationMs)}" else "• ${formatTime(durationMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            IconButton(
                onClick = onPlayClick,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isPlaying) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                    ),
            ) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Пауза" else "Слушать",
                    tint = if (isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun AudioEmptyState(
    onAddFiles: () -> Unit,
    onAddFolder: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(28.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(SquircleLg)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Headphones,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp),
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = "Аудиокниги не найдены",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Поддерживаются форматы M4B, MP3, M4A, FLAC, OGG, OPUS, AAC. Добавьте файлы или папку с аудиокнигами с устройства.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onAddFiles,
                    shape = SquircleMd,
                ) {
                    Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Выбрать файлы")
                }

                OutlinedButton(
                    onClick = onAddFolder,
                    shape = SquircleMd,
                ) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Выбрать папку")
                }
            }
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
