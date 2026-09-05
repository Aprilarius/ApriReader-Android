package com.aprireader.app.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aprireader.app.R
import com.aprireader.app.data.metadata.MetadataCandidate
import com.aprireader.app.ui.theme.SquircleSheet
import com.aprireader.app.ui.theme.SquircleSm
import com.aprireader.app.ui.theme.SquircleXs

/**
 * Поиск данных книги в каталогах FantLab, Google Books и Open Library
 * с нечетким сопоставлением и выбором наилучшего варианта.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataSearchSheet(
    query: String,
    candidates: List<MetadataCandidate>,
    searching: Boolean,
    finished: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelect: (MetadataCandidate) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SquircleSheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = stringResource(R.string.meta_search_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.meta_search_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                label = { Text(stringResource(R.string.meta_search_label)) },
                supportingText = { Text(stringResource(R.string.meta_search_hint)) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Rounded.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                shape = SquircleSm,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onSearch, enabled = query.isNotBlank() && !searching) {
                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.action_find))
                }
                if (searching) {
                    Spacer(Modifier.width(14.dp))
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.meta_search_sending),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        when {
            candidates.isNotEmpty() -> LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(
                    count = candidates.size,
                    key = { index -> "${candidates[index].source}-${candidates[index].title}-$index" },
                ) { index ->
                    val candidate = candidates[index]
                    CandidateRow(candidate, onClick = { onSelect(candidate) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }

            finished && !searching -> EmptyResult()

            else -> Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CandidateRow(candidate: MetadataCandidate, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .width(48.dp)
                .height(72.dp)
                .clip(SquircleXs)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (candidate.coverUrl != null) {
                val context = androidx.compose.ui.platform.LocalContext.current
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(context)
                        .data(candidate.coverUrl)
                        .crossfade(true)
                        .setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36 ApriReader/1.0")
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Rounded.AutoStories,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = candidate.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                SourceBadge(source = candidate.source)
            }

            val authorLine = candidate.authors.joinToString(", ").takeIf { it.isNotBlank() }
            if (authorLine != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = authorLine,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val metaLine = listOfNotNull(
                candidate.year?.toString(),
                candidate.publisher?.takeIf { it.isNotBlank() && it != candidate.source },
                candidate.series?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")

            if (metaLine.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = metaLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (!candidate.description.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = candidate.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SourceBadge(source: String) {
    val (bgColor, textColor) = when (source.lowercase()) {
        "fantlab" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        "google books" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        "википедия", "wikipedia" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        "open library" -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = source,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun EmptyResult() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.SearchOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.meta_search_empty_title), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.meta_search_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
