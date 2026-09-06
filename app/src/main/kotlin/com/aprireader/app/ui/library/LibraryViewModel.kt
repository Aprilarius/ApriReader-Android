package com.aprireader.app.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aprireader.app.AppContainer
import com.aprireader.app.data.library.ImportProgress
import com.aprireader.app.data.library.LibraryRepository
import com.aprireader.app.data.prefs.AppSettings
import com.aprireader.app.data.prefs.SettingsRepository
import com.aprireader.app.data.prefs.ShelfGrouping
import com.aprireader.app.data.prefs.ShelfLayout
import com.aprireader.app.data.prefs.ShelfSort
import com.aprireader.app.domain.Book
import com.aprireader.app.domain.LibrarySource
import com.aprireader.app.domain.ShelfSortable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Сообщение полки.
 *
 * ViewModel не собирает текст сам: строки живут в ресурсах и переводятся, а
 * сюда попадает только тип события и его данные.
 */
sealed interface ShelfMessage {
    data class BooksAdded(val count: Int) : ShelfMessage
    data object NoNewBooks : ShelfMessage
    data object BooksAlreadyPresent : ShelfMessage
    data class FilesUnsupported(val count: Int) : ShelfMessage
    data object LibraryUpToDate : ShelfMessage
    data class NewBooksFound(val count: Int) : ShelfMessage
    data class BookRemoved(val title: String) : ShelfMessage
    data class BooksRemoved(val count: Int) : ShelfMessage
    data class FolderFailed(val reason: String) : ShelfMessage
    data class FilesFailed(val reason: String) : ShelfMessage
}

/** Секция полки: заголовок группы и её книги. */
data class ShelfSection(val title: String?, val books: List<Book>)

data class LibraryUiState(
    val loading: Boolean = true,
    val sections: List<ShelfSection> = emptyList(),
    val totalBooks: Int = 0,
    val continueReading: Book? = null,
    val settings: AppSettings = AppSettings(),
    val sources: List<LibrarySource> = emptyList(),
    val importProgress: ImportProgress = ImportProgress(),
    val query: String = "",
    val filter: ShelfFilter = ShelfFilter.ALL,
    val message: ShelfMessage? = null,
) {
    val isEmpty: Boolean get() = !loading && totalBooks == 0
    val hasUnavailableSource: Boolean get() = sources.any { !it.available }
    val nothingFound: Boolean get() = !loading && totalBooks > 0 && sections.all { it.books.isEmpty() }
}

/**
 * Служебные ключи заголовков групп. UI подменяет их переведённым текстом:
 * заголовок группы приходит из данных, а не из ресурсов, поэтому его нельзя
 * собрать в ViewModel.
 */
const val NO_AUTHOR_KEY = "__no_author__"
const val NO_SERIES_KEY = "__no_series__"
const val PROGRESS_READING_KEY = "__reading__"
const val PROGRESS_UNREAD_KEY = "__unread__"
const val PROGRESS_FINISHED_KEY = "__finished__"

/** Направление, которое читатель ожидает увидеть сразу после выбора сортировки. */
fun ShelfSort.naturallyAscending(): Boolean = when (this) {
    ShelfSort.TITLE, ShelfSort.AUTHOR -> true
    ShelfSort.RECENT, ShelfSort.ADDED, ShelfSort.PROGRESS -> false
}

enum class ShelfFilter {
    ALL,
    READING,
    FAVORITES,
    UNREAD,
    FINISHED,
}

class LibraryViewModel(
    private val library: LibraryRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(ShelfFilter.ALL)
    private val message = MutableStateFlow<ShelfMessage?>(null)

    init {
        viewModelScope.launch {
            library.installWelcomeGuide()
        }
    }

    val state: StateFlow<LibraryUiState> = combine(
        library.books,
        settingsRepository.settings,
        library.sources,
        library.importProgress,
        combine(query, filter, message) { q, f, m -> Triple(q, f, m) },
    ) { books, settings, sources, importProgress, (queryValue, filterValue, messageValue) ->
        val textBooks = books.filter { !it.format.isAudio }
        val visible = textBooks
            .filter { it.matches(queryValue) }
            .filter { filterValue.accepts(it) }

        LibraryUiState(
            loading = false,
            sections = groupBooks(visible, settings),
            totalBooks = textBooks.size,
            continueReading = textBooks
                .filter { it.lastOpenedAt != null && !it.isFinished }
                .maxByOrNull { it.lastOpenedAt ?: 0L },
            settings = settings,
            sources = sources,
            importProgress = importProgress,
            query = queryValue,
            filter = filterValue,
            message = messageValue,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun setFilter(value: ShelfFilter) {
        filter.value = value
    }

    fun consumeMessage() {
        message.value = null
    }

    fun setLayout(layout: ShelfLayout) = updateSettings { it.copy(shelfLayout = layout) }

    fun setShelfColumns(columns: Int) = updateSettings { it.copy(shelfColumns = columns.coerceIn(2, 5)) }

    /**
     * При смене вида сортировки направление сбрасывается на естественное для неё:
     * названия и авторы читаются от А к Я, а даты и прогресс — от новых и больших.
     */
    fun setSort(sort: ShelfSort) = updateSettings {
        it.copy(shelfSort = sort, shelfAscending = sort.naturallyAscending())
    }

    fun setGrouping(grouping: ShelfGrouping) = updateSettings { it.copy(shelfGrouping = grouping) }

    fun toggleSortDirection() = updateSettings { it.copy(shelfAscending = !it.shelfAscending) }

    fun toggleFavorite(book: Book) = viewModelScope.launch {
        library.setFavorite(book.id, !book.favorite)
    }

    fun deleteBook(book: Book) = viewModelScope.launch {
        library.deleteBook(book.id)
        message.value = ShelfMessage.BookRemoved(book.title)
    }

    fun deleteBooks(books: Collection<Book>) = viewModelScope.launch {
        if (books.isEmpty()) return@launch
        val count = books.size
        val firstTitle = books.first().title
        library.deleteBooks(books.map { it.id })
        message.value = if (count == 1) {
            ShelfMessage.BookRemoved(firstTitle)
        } else {
            ShelfMessage.BooksRemoved(count)
        }
    }

    fun addFolder(uri: Uri) = viewModelScope.launch {
        library.addFolder(uri)
            .onSuccess { imported ->
                message.value = if (imported == 0) ShelfMessage.NoNewBooks else ShelfMessage.BooksAdded(imported)
            }
            .onFailure { message.value = ShelfMessage.FolderFailed(it.message.orEmpty()) }
    }

    fun addFiles(uris: List<Uri>) = viewModelScope.launch {
        if (uris.isEmpty()) return@launch
        library.addFiles(uris)
            .onSuccess { summary ->
                // Три разных причины нулевого результата раньше сливались в одно
                // сообщение «Книги уже в библиотеке» — даже когда файл на самом
                // деле не поддерживается и вообще не был импортирован.
                message.value = when {
                    summary.added > 0 -> ShelfMessage.BooksAdded(summary.added)
                    summary.unsupported > 0 -> ShelfMessage.FilesUnsupported(summary.unsupported)
                    summary.alreadyPresent > 0 -> ShelfMessage.BooksAlreadyPresent
                    else -> ShelfMessage.NoNewBooks
                }
            }
            .onFailure { message.value = ShelfMessage.FilesFailed(it.message.orEmpty()) }
    }

    fun rescan() = viewModelScope.launch {
        val added = library.rescanAll()
        message.value = if (added > 0) ShelfMessage.NewBooksFound(added) else ShelfMessage.LibraryUpToDate
    }

    fun revalidateAccess() = viewModelScope.launch { library.revalidateAccess() }

    /**
     * Тихое автосканирование уже добавленных папок — на открытии полки, без
     * нажатия «Пересканировать» руками. Молчит, если новых книг не нашлось:
     * баннер «Библиотека актуальна» на каждый заход на полку был бы просто
     * шумом, а не полезной информацией.
     */
    fun autoRescan() = viewModelScope.launch {
        val added = library.rescanAll()
        if (added > 0) message.value = ShelfMessage.NewBooksFound(added)
    }

    fun removeSource(source: LibrarySource, deleteBooks: Boolean) = viewModelScope.launch {
        library.removeSource(source.treeUri, deleteBooks)
    }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        settingsRepository.update(transform)
    }

    private fun Book.matches(queryValue: String): Boolean {
        if (queryValue.isBlank()) return true
        val needle = queryValue.trim().lowercase()
        return title.lowercase().contains(needle) ||
            authorLine.lowercase().contains(needle) ||
            series?.lowercase()?.contains(needle) == true ||
            fileName.lowercase().contains(needle)
    }

    private fun ShelfFilter.accepts(book: Book): Boolean = when (this) {
        ShelfFilter.ALL -> true
        ShelfFilter.READING -> book.isStarted
        ShelfFilter.FAVORITES -> book.favorite
        ShelfFilter.UNREAD -> book.progress <= 0.001f
        ShelfFilter.FINISHED -> book.isFinished
    }

    private fun groupBooks(books: List<Book>, settings: AppSettings): List<ShelfSection> {
        val sorted = books.sortedWith(shelfComparator(settings.shelfSort, settings.shelfAscending))
        return when (settings.shelfGrouping) {
            ShelfGrouping.NONE -> listOf(ShelfSection(null, sorted))
            ShelfGrouping.AUTHOR -> sorted.groupBy { it.authorLine.ifBlank { NO_AUTHOR_KEY } }
                .toSortedMap()
                .map { (title, group) -> ShelfSection(title, group) }
            ShelfGrouping.SERIES -> sorted.groupBy { it.series ?: NO_SERIES_KEY }
                .toSortedMap()
                .map { (title, group) -> ShelfSection(title, group) }
            ShelfGrouping.FORMAT -> sorted.groupBy { it.format.name }
                .toSortedMap()
                .map { (title, group) -> ShelfSection(title, group) }
            ShelfGrouping.PROGRESS -> {
                val buckets = linkedMapOf(
                    PROGRESS_READING_KEY to sorted.filter { it.isStarted },
                    PROGRESS_UNREAD_KEY to sorted.filter { it.progress <= 0.001f },
                    PROGRESS_FINISHED_KEY to sorted.filter { it.isFinished },
                )
                buckets.filterValues { it.isNotEmpty() }.map { (title, group) -> ShelfSection(title, group) }
            }
        }
    }


    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { LibraryViewModel(container.library, container.settings) }
        }
    }
}

/**
 * Компаратор полки.
 *
 * [ascending] означает буквально «по возрастанию»: А→Я для названий и авторов,
 * от старых к новым для дат. Раньше флаг трактовался по-разному для разных
 * сортировок, и при значении по умолчанию книги по названию выстраивались
 * от Я к А — ровно наоборот тому, что показывает переключатель.
 */
fun <T : ShelfSortable> shelfComparator(sort: ShelfSort, ascending: Boolean): Comparator<T> {
    val ascendingBase: Comparator<T> = when (sort) {
        ShelfSort.RECENT -> compareBy { it.lastOpenedAt ?: it.addedAt }
        ShelfSort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        // Книги без автора уходят в конец при любом направлении.
        ShelfSort.AUTHOR -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.authorLine.ifBlank { "￿" } }
        ShelfSort.ADDED -> compareBy { it.addedAt }
        ShelfSort.PROGRESS -> compareBy { it.progress }
    }
    val directed = if (ascending) ascendingBase else ascendingBase.reversed()
    return directed.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
}
