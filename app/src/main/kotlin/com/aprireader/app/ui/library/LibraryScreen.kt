package com.aprireader.app.ui.library

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.aprireader.app.ui.common.GlassPanel
import com.aprireader.app.ui.theme.GlassLevel
import com.aprireader.app.ui.theme.SquircleLg
import com.aprireader.app.ui.theme.SquircleSheet
import androidx.compose.ui.res.stringResource
import com.aprireader.app.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aprireader.app.data.prefs.ShelfGrouping
import com.aprireader.app.data.prefs.ShelfLayout
import com.aprireader.app.data.prefs.ShelfSort
import com.aprireader.app.domain.Book
import androidx.compose.ui.platform.LocalContext
import com.aprireader.app.data.prefs.AppLanguage
import com.aprireader.app.data.prefs.LocaleStore
import com.aprireader.app.ui.theme.SquirclePill
import com.aprireader.app.ui.common.BookCover
import com.aprireader.app.ui.common.COVER_ASPECT
import com.aprireader.app.ui.theme.CoverShape
import com.aprireader.app.ui.common.EmptyLibraryState
import com.aprireader.app.ui.theme.SquircleSm
import com.aprireader.app.ui.common.NothingFoundState

/**
 * Полка — первый экран приложения.
 *
 * Композиция строится вокруг одной мысли: главное здесь — книга, которую
 * пользователь читает прямо сейчас. Поэтому сверху карточка продолжения, а не
 * панель инструментов, а управление полкой убрано в меню.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenDrawer: () -> Unit,
    onOpenBook: (Book) -> Unit,
    onOpenBookDetails: (Book) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    var searchVisible by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var addMenuOpen by remember { mutableStateOf(false) }

    var selectedBookIds by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedBookIds.isNotEmpty()
    var contextMenuBook by remember { mutableStateOf<Book?>(null) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    var confirmSingleDeleteBook by remember { mutableStateOf<Book?>(null) }

    val allBooksOnShelf = remember(state.sections, state.continueReading) {
        val list = mutableListOf<Book>()
        state.continueReading?.let { list.add(it) }
        state.sections.forEach { list.addAll(it.books) }
        list.distinctBy { it.id }
    }
    val allSelected = allBooksOnShelf.isNotEmpty() && allBooksOnShelf.all { selectedBookIds.contains(it.id) }

    BackHandler(enabled = isSelectionMode) {
        selectedBookIds = emptySet()
    }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(viewModel::addFolder) }

    val filesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.addFiles(uris) }

    val messageText = state.message?.text()
    LaunchedEffect(messageText) {
        messageText?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.revalidateAccess()
        viewModel.autoRescan()
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 4.dp, bottom = 4.dp),
            ) {
                if (isSelectionMode) {
                    SelectionHeader(
                        selectedCount = selectedBookIds.size,
                        allSelected = allSelected,
                        onSelectAllToggle = {
                            selectedBookIds = if (allSelected) {
                                emptySet()
                            } else {
                                allBooksOnShelf.map { it.id }.toSet()
                            }
                        },
                        onDeleteSelected = { confirmBulkDelete = true },
                        onCloseSelection = { selectedBookIds = emptySet() },
                    )
                } else {
                    ShelfHeader(
                        shelfTitle = state.settings.userName.takeIf { it.isNotBlank() }
                            ?.let { stringResource(R.string.shelf_title_named, it) }
                            ?: stringResource(R.string.shelf_title),
                        bookCount = state.totalBooks,
                        searchVisible = searchVisible,
                        query = state.query,
                        onQueryChange = viewModel::setQuery,
                        onToggleSearch = {
                            searchVisible = !searchVisible
                            if (!searchVisible) {
                                viewModel.setQuery("")
                                focusManager.clearFocus()
                            }
                        },
                        layout = state.settings.shelfLayout,
                        onLayoutChange = viewModel::setLayout,
                        shelfColumns = state.settings.shelfColumns,
                        onColumnsChange = viewModel::setShelfColumns,
                        sortMenuOpen = sortMenuOpen,
                        onSortMenuOpenChange = { sortMenuOpen = it },
                        sort = state.settings.shelfSort,
                        grouping = state.settings.shelfGrouping,
                        ascending = state.settings.shelfAscending,
                        onSortChange = viewModel::setSort,
                        onGroupingChange = viewModel::setGrouping,
                        onToggleDirection = viewModel::toggleSortDirection,
                        onOpenDrawer = onOpenDrawer,
                        onOpenSettings = onOpenSettings,
                        onOpenStats = onOpenStats,
                    )
                }

                AnimatedVisibility(
                    visible = state.importProgress.running,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    ImportProgressRow(
                        scanned = state.importProgress.scanned,
                        imported = state.importProgress.imported,
                        total = state.importProgress.total,
                        currentName = state.importProgress.currentName,
                    )
                }

                AnimatedVisibility(visible = state.hasUnavailableSource) {
                    UnavailableSourceBanner(
                        count = state.sources.count { !it.available },
                        onReconnect = { folderLauncher.launch(null) },
                    )
                }

                if (state.totalBooks > 0) {
                    FilterRow(
                        selected = state.filter,
                        onSelect = viewModel::setFilter,
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!isSelectionMode) {
                Box(Modifier.navigationBarsPadding()) {
                    ExtendedFloatingActionButton(
                        onClick = { addMenuOpen = true },
                        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.shelf_add)) },
                    )
                    DropdownMenu(expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.shelf_add_folder)) },
                            leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, null) },
                            onClick = {
                                addMenuOpen = false
                                folderLauncher.launch(null)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.shelf_add_files)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.NoteAdd, null) },
                            onClick = {
                                addMenuOpen = false
                                filesLauncher.launch(SUPPORTED_MIME_TYPES)
                            },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.shelf_rescan)) },
                            leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                            onClick = {
                                addMenuOpen = false
                                viewModel.rescan()
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            com.aprireader.app.ui.theme.GlassmorphicBackdrop(Modifier.fillMaxSize())
            val isAmoled = MaterialTheme.colorScheme.surface == Color.Black || MaterialTheme.colorScheme.background == Color.Black
            if (!isAmoled) {
                // Мягкое свечение вверху экрана (только для светлой/Material тем, на AMOLED сохраняем чистый #000000)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    Color.Transparent,
                                )
                            )
                        )
                )
            }

            when {
                state.isEmpty -> EmptyLibraryState(
                    onPickFolder = { folderLauncher.launch(null) },
                    onPickFiles = { filesLauncher.launch(SUPPORTED_MIME_TYPES) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                )

                state.nothingFound -> NothingFoundState(
                    query = state.query,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                )

                else -> ShelfGrid(
                    state = state,
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 10.dp,
                        bottom = padding.calculateBottomPadding() + 84.dp,
                    ),
                    onOpenBook = { book ->
                        if (isSelectionMode) {
                            selectedBookIds = if (selectedBookIds.contains(book.id)) {
                                selectedBookIds - book.id
                            } else {
                                selectedBookIds + book.id
                            }
                        } else {
                            onOpenBook(book)
                        }
                    },
                    onOpenDetails = { book ->
                        if (isSelectionMode) {
                            selectedBookIds = if (selectedBookIds.contains(book.id)) {
                                selectedBookIds - book.id
                            } else {
                                selectedBookIds + book.id
                            }
                        } else {
                            contextMenuBook = book
                        }
                    },
                    onToggleFavorite = viewModel::toggleFavorite,
                    isSelectionMode = isSelectionMode,
                    selectedBookIds = selectedBookIds,
                )
            }
        }
    }

    if (contextMenuBook != null) {
        val book = contextMenuBook!!
        BookContextMenuSheet(
            book = book,
            onDismiss = { contextMenuBook = null },
            onSelectMode = {
                contextMenuBook = null
                selectedBookIds = setOf(book.id)
            },
            onOpenDetails = {
                contextMenuBook = null
                onOpenBookDetails(book)
            },
            onDelete = {
                contextMenuBook = null
                confirmSingleDeleteBook = book
            },
        )
    }

    if (confirmBulkDelete) {
        val selectedBooks = allBooksOnShelf.filter { selectedBookIds.contains(it.id) }
        AlertDialog(
            onDismissRequest = { confirmBulkDelete = false },
            title = { Text(stringResource(R.string.shelf_delete_selected_title)) },
            text = {
                Text(stringResource(R.string.shelf_delete_selected_confirm, selectedBooks.size))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmBulkDelete = false
                        viewModel.deleteBooks(selectedBooks)
                        selectedBookIds = emptySet()
                    }
                ) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmBulkDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (confirmSingleDeleteBook != null) {
        val book = confirmSingleDeleteBook!!
        AlertDialog(
            onDismissRequest = { confirmSingleDeleteBook = null },
            title = { Text(stringResource(R.string.details_remove_dialog_title)) },
            text = { Text(stringResource(R.string.details_remove_dialog_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmSingleDeleteBook = null
                        viewModel.deleteBook(book)
                    }
                ) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSingleDeleteBook = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ShelfGrid(
    state: LibraryUiState,
    contentPadding: PaddingValues,
    onOpenBook: (Book) -> Unit,
    onOpenDetails: (Book) -> Unit,
    onToggleFavorite: (Book) -> Unit,
    isSelectionMode: Boolean = false,
    selectedBookIds: Set<String> = emptySet(),
) {
    val gridState = rememberLazyGridState()

    when (state.settings.shelfLayout) {
        ShelfLayout.SHELF -> {
            LazyColumn(
                state = rememberLazyListState(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                val hero = state.continueReading
                if (hero != null && state.query.isBlank() && state.filter == ShelfFilter.ALL) {
                    item(key = "hero-${hero.id}") {
                        ContinueReadingCard(
                            book = hero,
                            onClick = { onOpenBook(hero) },
                            onLongClick = { onOpenDetails(hero) },
                            isSelectionMode = isSelectionMode,
                            isSelected = selectedBookIds.contains(hero.id),
                        )
                    }
                }

                state.sections.forEach { section ->
                    if (section.title != null) {
                        item(key = "section-${section.title}") {
                            SectionHeader(title = sectionTitle(section.title), count = section.books.size)
                        }
                    }
                    val columnsCount = state.settings.shelfColumns
                    val rows = section.books.chunked(columnsCount)
                    items(
                        items = rows,
                        key = { row -> "shelf-row-${row.firstOrNull()?.id.orEmpty()}" },
                    ) { rowBooks ->
                        BookshelfRow(
                            books = rowBooks,
                            columns = columnsCount,
                            onClick = onOpenBook,
                            onLongClick = onOpenDetails,
                            isSelectionMode = isSelectionMode,
                            selectedBookIds = selectedBookIds,
                        )
                    }
                }
            }
        }

        ShelfLayout.GRID, ShelfLayout.COMPACT, ShelfLayout.LIST -> {
            val columns = when (state.settings.shelfLayout) {
                ShelfLayout.GRID -> GridCells.Fixed(state.settings.shelfColumns)
                ShelfLayout.COMPACT -> GridCells.Fixed(state.settings.shelfColumns + 1)
                ShelfLayout.LIST -> GridCells.Fixed(1)
                else -> GridCells.Fixed(state.settings.shelfColumns)
            }

            LazyVerticalGrid(
                columns = columns,
                state = gridState,
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                val hero = state.continueReading
                if (hero != null && state.query.isBlank() && state.filter == ShelfFilter.ALL) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "hero-${hero.id}") {
                        ContinueReadingCard(
                            book = hero,
                            onClick = { onOpenBook(hero) },
                            onLongClick = { onOpenDetails(hero) },
                            isSelectionMode = isSelectionMode,
                            isSelected = selectedBookIds.contains(hero.id),
                        )
                    }
                }

                state.sections.forEach { section ->
                    if (section.title != null) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "section-${section.title}") {
                            SectionHeader(title = sectionTitle(section.title), count = section.books.size)
                        }
                    }
                    items(
                        items = section.books,
                        key = { "${section.title.orEmpty()}-${it.id}" },
                    ) { book ->
                        when (state.settings.shelfLayout) {
                            ShelfLayout.LIST -> BookListRow(
                                book = book,
                                onClick = { onOpenBook(book) },
                                onLongClick = { onOpenDetails(book) },
                                onToggleFavorite = { onToggleFavorite(book) },
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedBookIds.contains(book.id),
                            )
                            else -> BookGridCard(
                                book = book,
                                compact = state.settings.shelfLayout == ShelfLayout.COMPACT,
                                onClick = { onOpenBook(book) },
                                onLongClick = { onOpenDetails(book) },
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedBookIds.contains(book.id),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }
}

@Composable
private fun SelectionHeader(
    selectedCount: Int,
    allSelected: Boolean,
    onSelectAllToggle: () -> Unit,
    onDeleteSelected: () -> Unit,
    onCloseSelection: () -> Unit,
) {
    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        shape = SquircleLg,
        level = GlassLevel.Chrome,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCloseSelection) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_close),
                )
            }

            Text(
                text = stringResource(R.string.shelf_selection_title, selectedCount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp),
            )

            IconButton(onClick = onSelectAllToggle) {
                Icon(
                    imageVector = if (allSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                    contentDescription = stringResource(if (allSelected) R.string.shelf_deselect_all else R.string.shelf_select_all),
                )
            }

            IconButton(
                onClick = onDeleteSelected,
                enabled = selectedCount > 0,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.shelf_delete_selected),
                    tint = if (selectedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookContextMenuSheet(
    book: Book,
    onDismiss: () -> Unit,
    onSelectMode: () -> Unit,
    onOpenDetails: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = SquircleSheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            // Заголовок с обложкой и названием книги
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .aspectRatio(COVER_ASPECT)
                        .clip(CoverShape),
                ) {
                    BookCover(
                        book = book,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (book.authorLine.isNotBlank()) {
                        Text(
                            text = book.authorLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Text(
                        text = book.format.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

            // Пункт 1: Режим выбора
            Surface(
                onClick = onSelectMode,
                shape = SquircleSm,
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Checklist,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.shelf_context_select_mode),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.shelf_context_select_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Пункт 2: Данные о файле
            Surface(
                onClick = onOpenDetails,
                shape = SquircleSm,
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Article,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.shelf_context_details),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.shelf_context_details_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Пункт 3: Удалить из библиотеки
            Surface(
                onClick = onDelete,
                shape = SquircleSm,
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.shelf_context_delete),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShelfHeader(
    shelfTitle: String,
    bookCount: Int,
    searchVisible: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    layout: ShelfLayout,
    onLayoutChange: (ShelfLayout) -> Unit,
    shelfColumns: Int,
    onColumnsChange: (Int) -> Unit,
    sortMenuOpen: Boolean,
    onSortMenuOpenChange: (Boolean) -> Unit,
    sort: ShelfSort,
    grouping: ShelfGrouping,
    ascending: Boolean,
    onSortChange: (ShelfSort) -> Unit,
    onGroupingChange: (ShelfGrouping) -> Unit,
    onToggleDirection: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
) {
    var viewMenuOpen by remember { mutableStateOf(false) }

    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        shape = SquircleLg,
        level = GlassLevel.Chrome,
    ) {
      Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = stringResource(R.string.drawer_open_hint),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp, end = 4.dp),
            ) {
                Text(
                    text = shelfTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatBooksCount(bookCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            IconButton(onClick = onToggleSearch) {
                Icon(
                    imageVector = if (searchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                    contentDescription = stringResource(if (searchVisible) R.string.shelf_search_close else R.string.shelf_search),
                )
            }

            Box {
                IconButton(onClick = { viewMenuOpen = true }) {
                    Icon(
                        imageVector = when (layout) {
                            ShelfLayout.SHELF -> Icons.AutoMirrored.Rounded.LibraryBooks
                            ShelfLayout.LIST -> Icons.AutoMirrored.Rounded.ViewList
                            ShelfLayout.COMPACT -> Icons.Rounded.Apps
                            ShelfLayout.GRID -> Icons.Rounded.GridView
                        },
                        contentDescription = stringResource(R.string.shelf_layout_toggle),
                    )
                }
                DropdownMenu(expanded = viewMenuOpen, onDismissRequest = { viewMenuOpen = false }) {
                    MenuSectionLabel(stringResource(R.string.shelf_layout_toggle))
                    listOf(
                        ShelfLayout.SHELF to (Icons.AutoMirrored.Rounded.LibraryBooks to R.string.shelf_layout_shelf),
                        ShelfLayout.GRID to (Icons.Rounded.GridView to R.string.shelf_layout_grid),
                        ShelfLayout.COMPACT to (Icons.Rounded.Apps to R.string.shelf_layout_compact),
                        ShelfLayout.LIST to (Icons.AutoMirrored.Rounded.ViewList to R.string.shelf_layout_list),
                    ).forEach { (mode, pair) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(pair.second)) },
                            leadingIcon = { RadioButton(selected = layout == mode, onClick = null) },
                            trailingIcon = { Icon(pair.first, null, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                onLayoutChange(mode)
                                viewMenuOpen = false
                            },
                        )
                    }
                    if (layout != ShelfLayout.LIST) {
                        HorizontalDivider()
                        MenuSectionLabel(stringResource(R.string.shelf_scale_title))
                        listOf(
                            2 to R.string.shelf_scale_large,
                            3 to R.string.shelf_scale_standard,
                            4 to R.string.shelf_scale_compact,
                            5 to R.string.shelf_scale_mini,
                        ).forEach { (cols, labelRes) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(labelRes)) },
                                leadingIcon = { RadioButton(selected = shelfColumns == cols, onClick = null) },
                                onClick = {
                                    onColumnsChange(cols)
                                    viewMenuOpen = false
                                },
                            )
                        }
                    }
                }
            }

            Box {
                IconButton(onClick = { onSortMenuOpenChange(true) }) {
                    Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.shelf_sort_menu))
                }
                SortMenu(
                    expanded = sortMenuOpen,
                    onDismiss = { onSortMenuOpenChange(false) },
                    sort = sort,
                    grouping = grouping,
                    ascending = ascending,
                    onSortChange = onSortChange,
                    onGroupingChange = onGroupingChange,
                    onToggleDirection = onToggleDirection,
                    onOpenStats = onOpenStats,
                    onOpenSettings = onOpenSettings,
                )
            }
        }

        AnimatedVisibility(visible = searchVisible) {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.shelf_search_placeholder)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.shelf_search_clear))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                shape = SquircleSm,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
            )
        }
      }
    }
}

@Composable
private fun SortMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    sort: ShelfSort,
    grouping: ShelfGrouping,
    ascending: Boolean,
    onSortChange: (ShelfSort) -> Unit,
    onGroupingChange: (ShelfGrouping) -> Unit,
    onToggleDirection: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MenuSectionLabel(stringResource(R.string.sort_section))
        sortLabels.forEach { (value, label) ->
            DropdownMenuItem(
                text = { Text(stringResource(label)) },
                leadingIcon = { RadioButton(selected = sort == value, onClick = null) },
                onClick = { onSortChange(value) },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(if (ascending) R.string.sort_ascending else R.string.sort_descending)) },
            onClick = onToggleDirection,
        )
        HorizontalDivider()
        MenuSectionLabel(stringResource(R.string.group_section))
        groupingLabels.forEach { (value, label) ->
            DropdownMenuItem(
                text = { Text(stringResource(label)) },
                leadingIcon = { RadioButton(selected = grouping == value, onClick = null) },
                onClick = { onGroupingChange(value) },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_stats)) },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.MenuBook, null) },
            onClick = {
                onDismiss()
                onOpenStats()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.menu_settings)) },
            leadingIcon = { Icon(Icons.Rounded.Settings, null) },
            onClick = {
                onDismiss()
                onOpenSettings()
            },
        )
    }
}

@Composable
private fun MenuSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp),
    )
}

@Composable
private fun formatBooksCount(count: Int): String {
    val context = LocalContext.current
    val tag = LocaleStore.read(context)
    val lang = tag?.let { AppLanguage.fromTag(it) } ?: AppLanguage.suggested(context)
    return when (lang) {
        AppLanguage.RUSSIAN -> {
            val mod10 = count % 10
            val mod100 = count % 100
            when {
                mod10 == 1 && mod100 != 11 -> stringResource(R.string.shelf_books_count_one, count)
                mod10 in 2..4 && mod100 !in 12..14 -> stringResource(R.string.shelf_books_count_few, count)
                else -> stringResource(R.string.shelf_books_count_many, count)
            }
        }
        AppLanguage.ENGLISH, AppLanguage.ITALIAN, AppLanguage.GERMAN -> {
            if (count == 1) stringResource(R.string.shelf_books_count_one, count)
            else stringResource(R.string.shelf_books_count_few, count)
        }
        AppLanguage.AZERBAIJANI -> stringResource(R.string.shelf_books_count_few, count)
    }
}

@Composable
private fun FilterRow(
    selected: ShelfFilter,
    onSelect: (ShelfFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShelfFilter.entries.forEach { filter ->
            val isSelected = selected == filter
            val label = filter.label()
            Surface(
                onClick = { onSelect(filter) },
                shape = SquirclePill,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                border = if (isSelected) null else androidx.compose.foundation.BorderStroke(
                    0.75.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                ),
                shadowElevation = if (isSelected) 2.dp else 0.dp,
                modifier = Modifier.height(36.dp),
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportProgressRow(scanned: Int, imported: Int, total: Int, currentName: String?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (total > 0) stringResource(R.string.import_progress, imported, total) else stringResource(R.string.import_scanning, scanned),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        currentName?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(6.dp))
        if (total > 0) {
            LinearProgressIndicator(
                progress = { imported.toFloat() / total },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun UnavailableSourceBanner(count: Int, onReconnect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(MaterialTheme.colorScheme.errorContainer, SquircleSm)
            .clickable(onClick = onReconnect)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.FolderOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (count == 1) stringResource(R.string.source_lost_one) else stringResource(R.string.source_lost_many, count),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(R.string.source_lost_explain),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
            )
        }
        TextButton(onClick = onReconnect) { Text(stringResource(R.string.source_lost_action)) }
    }
}

private val sortLabels = listOf(
    ShelfSort.RECENT to R.string.sort_recent,
    ShelfSort.TITLE to R.string.sort_title,
    ShelfSort.AUTHOR to R.string.sort_author,
    ShelfSort.ADDED to R.string.sort_added,
    ShelfSort.PROGRESS to R.string.sort_progress,
)

private val groupingLabels = listOf(
    ShelfGrouping.NONE to R.string.group_none,
    ShelfGrouping.AUTHOR to R.string.group_author,
    ShelfGrouping.SERIES to R.string.group_series,
    ShelfGrouping.FORMAT to R.string.group_format,
    ShelfGrouping.PROGRESS to R.string.group_progress,
)

/** Переводимая подпись фильтра. */
@Composable
fun ShelfFilter.label(): String = stringResource(
    when (this) {
        ShelfFilter.ALL -> R.string.filter_all
        ShelfFilter.READING -> R.string.filter_reading
        ShelfFilter.FAVORITES -> R.string.filter_favorites
        ShelfFilter.UNREAD -> R.string.filter_unread
        ShelfFilter.FINISHED -> R.string.filter_finished
    }
)

/**
 * Заголовок группы. ViewModel кладёт сюда служебный ключ, когда группа —
 * это «без автора» или «прочитанные»: перевести такое можно только в UI.
 */
@Composable
fun sectionTitle(raw: String): String = when (raw) {
    NO_AUTHOR_KEY -> stringResource(R.string.group_no_author)
    NO_SERIES_KEY -> stringResource(R.string.group_no_series)
    PROGRESS_READING_KEY -> stringResource(R.string.filter_reading)
    PROGRESS_UNREAD_KEY -> stringResource(R.string.filter_unread)
    PROGRESS_FINISHED_KEY -> stringResource(R.string.filter_finished)
    else -> raw
}

/** Текст сообщения полки. */
@Composable
fun ShelfMessage.text(): String = when (this) {
    is ShelfMessage.BooksAdded -> stringResource(R.string.msg_books_added, count)
    ShelfMessage.NoNewBooks -> stringResource(R.string.msg_no_new_books)
    ShelfMessage.BooksAlreadyPresent -> stringResource(R.string.msg_books_already)
    is ShelfMessage.FilesUnsupported -> stringResource(R.string.msg_files_unsupported, count)
    ShelfMessage.LibraryUpToDate -> stringResource(R.string.msg_library_current)
    is ShelfMessage.NewBooksFound -> stringResource(R.string.msg_new_books_found, count)
    is ShelfMessage.BookRemoved -> stringResource(R.string.msg_book_removed, title)
    is ShelfMessage.BooksRemoved -> stringResource(R.string.msg_books_removed, count)
    is ShelfMessage.FolderFailed -> stringResource(R.string.msg_folder_failed, reason)
    is ShelfMessage.FilesFailed -> stringResource(R.string.msg_files_failed, reason)
}


/**
 * MIME-типы для системного выбора файла (`EXTRA_MIME_TYPES`).
 *
 * `"application/vnd.comicbook+zip"` и `"application/vnd.comicbook-rar"` тут
 * не потому, что их кто-то реально возвращает: это не зарегистрированные в
 * Android MIME-типы, и ни один провайдер файлов (включая системную «Папку
 * загрузок») никогда их не сообщит. Настоящий тип `.cbr`/`.cbz`, который
 * определит система, непредсказуем — зависит от прошивки и её реестра
 * MimeTypeMap — и почти наверняка не совпадёт ни с одной строкой в этом
 * списке. DocumentsUI в таком случае просто делает файл неактивным (серым) в
 * диалоге выбора — реального бага «формат не поддерживается» нет, файл
 * буквально нельзя было нажать.
 *
 * Универсальная маска (звёздочка на месте обеих частей MIME-типа) в списке
 * отключает эту фильтрацию полностью: все файлы становятся доступны для
 * выбора. Формат всё равно проверяется точно и без вариантов толкования —
 * [com.aprireader.bookformat.model.BookFormat.fromExtension] по расширению
 * файла — сразу после выбора, при импорте; пользователь просто увидит
 * понятное сообщение «формат не поддерживается», а не молчаливо недоступный
 * файл.
 */
val SUPPORTED_MIME_TYPES = arrayOf(
    "*/*",
    "application/epub+zip",
    "application/pdf",
    "application/x-fictionbook+xml",
    "text/plain",
    "text/html",
    "application/xhtml+xml",
    "application/vnd.comicbook+zip",
    "application/vnd.comicbook-rar",
    "application/zip",
    "audio/*",
    "audio/mpeg",
    "audio/mp3",
    "audio/mp4",
    "audio/x-m4a",
    "audio/x-m4b",
    "audio/flac",
    "audio/ogg",
    "audio/opus",
    "audio/aac",
    "application/octet-stream",
)
