package com.aprireader.app.ui.details

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aprireader.app.R
import com.aprireader.app.domain.Book
import com.aprireader.app.ui.common.BookCover
import com.aprireader.app.ui.common.COVER_ASPECT
import com.aprireader.app.ui.common.CoverBloomHost
import com.aprireader.app.ui.common.GlassPanel
import com.aprireader.app.ui.common.InfoRow
import com.aprireader.app.ui.common.sharedCover
import com.aprireader.app.ui.theme.AccentPresets
import com.aprireader.app.ui.theme.CoverShape
import com.aprireader.app.ui.theme.GlassLevel
import com.aprireader.app.ui.theme.SquircleLg
import com.aprireader.app.ui.theme.SquirclePill
import com.aprireader.app.ui.theme.SquircleSm
import com.aprireader.app.ui.theme.SquircleXl

/**
 * Сведения о книге: гармоничная выровненная карточка с обложкой,
 * прогрессом, выбором акцентной палитры и метаданными.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailsScreen(
    viewModel: BookDetailsViewModel,
    onBack: () -> Unit,
    onRead: (Book) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val book = state.book

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.details_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.onboarding_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(
                            imageVector = if (book?.favorite == true) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            contentDescription = if (book?.favorite == true) stringResource(R.string.shelf_favorite_remove) else stringResource(R.string.shelf_favorite_add),
                            tint = if (book?.favorite == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (book == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.details_not_found), style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        val accent = book.effectiveAccent?.let { Color(it) } ?: MaterialTheme.colorScheme.primary

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            // Индикатор сетевой активности
            AnimatedVisibility(visible = state.networkActive) {
                NetworkIndicator()
            }

            // Главная карточка книги
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            ) {
                CoverBloomHost(
                    coverPath = book.coverPath,
                    accent = accent,
                    modifier = Modifier
                        .matchParentSize()
                        .padding(4.dp)
                        .clip(SquircleXl),
                )

                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = SquircleXl,
                    tint = accent,
                    level = GlassLevel.Card,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        // Обложка фиксированного гармоничного размера
                        Box(
                            modifier = Modifier
                                .width(112.dp)
                                .aspectRatio(COVER_ASPECT),
                        ) {
                            BookCover(
                                book = book,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .sharedCover(book.id, CoverShape),
                            )
                        }

                        Spacer(Modifier.width(16.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .align(Alignment.CenterVertically),
                        ) {
                            Text(
                                text = book.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (book.authorLine.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = book.authorLine,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            book.seriesLine?.let {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Spacer(Modifier.height(14.dp))

                            Button(
                                onClick = { onRead(book) },
                                enabled = book.available,
                                shape = SquirclePill,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp),
                            ) {
                                Icon(
                                    if (book.format.isAudio) androidx.compose.material.icons.Icons.Rounded.Headphones
                                    else Icons.AutoMirrored.Rounded.MenuBook,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (book.format.isAudio) {
                                        if (book.progress > 0.001f) "Продолжить слушать" else "Слушать"
                                    } else {
                                        if (book.progress > 0.001f) stringResource(R.string.shelf_continue) else stringResource(R.string.shelf_read)
                                    },
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }

            if (!book.available) {
                Card(
                    shape = SquircleLg,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.details_file_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            // Секция Прогресс чтения
            GlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = SquircleLg,
                tint = accent,
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionTitle(stringResource(R.string.sort_progress))
                        Text(
                            text = "${(book.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { book.progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.details_reading_time, formatDuration(state.readingMillis)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Секция Цветовой акцент
            GlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = SquircleLg,
                tint = accent,
            ) {
                Column(Modifier.padding(16.dp)) {
                    SectionTitle(stringResource(R.string.details_accent_section))
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (book.pinnedAccent != null) {
                            stringResource(R.string.details_accent_pinned)
                        } else {
                            stringResource(R.string.details_accent_auto)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        for (preset in AccentPresets) {
                            val isSelected = book.pinnedAccent == preset.color.toArgb()
                            val name = stringResource(preset.nameRes)
                            Box(
                                Modifier
                                    .size(42.dp)
                                    .clip(SquirclePill)
                                    .background(preset.color)
                                    .clickable(
                                        onClickLabel = name,
                                        onClick = { viewModel.pinAccent(preset.color.toArgb()) },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }

                    if (book.pinnedAccent != null) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = viewModel::unpinAccent) {
                            Text(stringResource(R.string.details_accent_reset))
                        }
                    }
                }
            }

            // Секция Метаданные и Сведения
            GlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = SquircleLg,
                tint = accent,
            ) {
                Column(Modifier.padding(16.dp)) {
                    SectionTitle(stringResource(R.string.details_info_section))
                    Spacer(Modifier.height(8.dp))

                    InfoRow(stringResource(R.string.details_format), book.format.name)
                    InfoRow(stringResource(R.string.details_file), book.fileName)
                    InfoRow(stringResource(R.string.details_size), formatSize(book.fileSize))
                    book.publisher?.let { InfoRow(stringResource(R.string.details_publisher), it) }
                    book.year?.let { InfoRow(stringResource(R.string.details_year), it.toString()) }
                    book.language?.let { InfoRow(stringResource(R.string.details_language), it) }

                    book.description?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    FilledTonalButton(
                        onClick = viewModel::requestMetadataFetch,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.details_fetch_metadata))
                    }

                    Text(
                        text = stringResource(R.string.details_fetch_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            // Секция Цитаты и Заметки
            if (state.marks.isNotEmpty()) {
                GlassPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = SquircleLg,
                    tint = accent,
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            SectionTitle(stringResource(R.string.details_marks_section, state.marks.size))
                            Spacer(Modifier.weight(1f))
                            val context = LocalContext.current
                            val notesTitle = stringResource(R.string.details_export_title, book.title)
                            val exportLabel = stringResource(R.string.details_export)
                            TextButton(
                                onClick = {
                                    val markdown = viewModel.exportMarks()
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TITLE, notesTitle)
                                        putExtra(Intent.EXTRA_TEXT, markdown)
                                    }
                                    context.startActivity(Intent.createChooser(intent, exportLabel))
                                },
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.details_export))
                            }
                        }

                        state.marks.take(20).forEachIndexed { idx, mark ->
                            if (idx > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            Column(Modifier.padding(vertical = 4.dp)) {
                                mark.chapterTitle?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                mark.quotedText?.let {
                                    Text(text = it, style = MaterialTheme.typography.bodyMedium, maxLines = 4)
                                }
                                mark.note?.takeIf { it.isNotBlank() }?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Удаление книги
            var confirmDelete by remember { mutableStateOf(false) }
            TextButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.details_remove_book), color = MaterialTheme.colorScheme.error)
            }
            Text(
                text = stringResource(R.string.details_remove_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )

            Spacer(Modifier.height(32.dp))

            if (confirmDelete) {
                AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    title = { Text(stringResource(R.string.details_remove_dialog_title)) },
                    text = { Text(stringResource(R.string.details_remove_dialog_text)) },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmDelete = false
                            viewModel.deleteBook()
                        }) {
                            Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDelete = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    },
                )
            }
        }

        if (state.consentRequested) {
            MetadataConsentDialog(
                book = book,
                onDismiss = viewModel::cancelMetadataFetch,
                onConfirm = viewModel::confirmMetadataFetch,
            )
        }

        if (state.searchOpen) {
            MetadataSearchSheet(
                query = state.searchQuery,
                candidates = state.candidates,
                searching = state.networkActive,
                finished = state.searchDone,
                onQueryChange = viewModel::setSearchQuery,
                onSearch = viewModel::runSearch,
                onSelect = viewModel::applyCandidate,
                onDismiss = viewModel::closeSearch,
            )
        }
    }
}

@Composable
private fun MetadataConsentDialog(
    book: Book,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit,
) {
    var rememberChoice by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.meta_consent_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.meta_consent_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                Text("• ${stringResource(R.string.sort_title)}: «${book.title}»", style = MaterialTheme.typography.bodySmall)
                if (book.authors.isNotEmpty()) {
                    Text("• ${stringResource(R.string.sort_author)}: ${book.authors.first()}", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.meta_consent_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = rememberChoice,
                        onCheckedChange = { rememberChoice = it },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.meta_consent_remember),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(rememberChoice) }) {
                Text(stringResource(R.string.action_find))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun NetworkIndicator() {
    Surface(
        shape = SquirclePill,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.CloudDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.details_fetch_metadata),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalMinutes = millis / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours} ч ${minutes} мин"
        minutes > 0 -> "${minutes} мин"
        else -> "< 1 мин"
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f МБ".format(bytes.toFloat() / (1024 * 1024))
    bytes >= 1024 -> "%d КБ".format(bytes / 1024)
    else -> "$bytes Б"
}
