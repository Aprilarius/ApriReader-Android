package com.aprireader.app.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import com.aprireader.app.ui.theme.SquircleSheet
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import com.aprireader.app.ui.theme.SquircleSm
import androidx.compose.ui.unit.dp

import androidx.compose.ui.res.stringResource
import com.aprireader.app.R

/** Поиск по тексту открытой книги. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSheet(
    state: ReaderUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenHit: (SearchHit) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }
    val search = state.search

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SquircleSheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            OutlinedTextField(
                value = search.query,
                onValueChange = onQueryChange,
                singleLine = true,
                label = { Text(stringResource(R.string.reader_search_title)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                shape = SquircleSm,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
            Spacer(Modifier.height(10.dp))

            if (search.running) {
                LinearProgressIndicator(
                    progress = {
                        if (search.totalChapters == 0) 0f
                        else search.searchedChapters.toFloat() / search.totalChapters
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
            }

            Text(
                text = when {
                    search.running -> stringResource(R.string.reader_search_running, search.hits.size)
                    search.finished -> stringResource(R.string.reader_search_matches, search.hits.size)
                    else -> stringResource(R.string.reader_search_prompt)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(Modifier.heightIn(max = 460.dp).padding(top = 10.dp)) {
            itemsIndexed(search.hits) { index, hit ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenHit(hit) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    hit.chapterTitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        text = highlighted(hit.snippet, search.query),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                    )
                }
                HorizontalDivider()
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

private fun highlighted(text: String, query: String): AnnotatedString = buildAnnotatedString {
    append(text)
    if (query.isBlank()) return@buildAnnotatedString
    var from = 0
    while (from < text.length) {
        val at = text.indexOf(query, from, ignoreCase = true)
        if (at < 0) break
        addStyle(SpanStyle(fontWeight = FontWeight.Bold), at, at + query.length)
        from = maxOf(from + 1, at + query.length)
    }
}
