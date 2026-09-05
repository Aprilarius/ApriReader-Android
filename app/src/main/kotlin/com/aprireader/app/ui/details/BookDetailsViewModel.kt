package com.aprireader.app.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aprireader.app.AppContainer
import com.aprireader.app.data.db.MarkEntity
import com.aprireader.app.data.library.LibraryRepository
import com.aprireader.app.data.marks.MarksRepository
import com.aprireader.app.data.metadata.MetadataCandidate
import com.aprireader.app.data.metadata.MetadataRepository
import com.aprireader.app.data.prefs.SettingsRepository
import com.aprireader.app.data.stats.StatsRepository
import com.aprireader.app.domain.Book
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BookDetailsUiState(
    val book: Book? = null,
    val marks: List<MarkEntity> = emptyList(),
    val readingMillis: Long = 0,
    /** Пользователь ещё не давал согласия на сетевой запрос — покажем объяснение. */
    val consentRequested: Boolean = false,
    val networkActive: Boolean = false,
    val candidates: List<MetadataCandidate> = emptyList(),
    /** Открыт лист поиска метаданных. */
    val searchOpen: Boolean = false,
    /** Запрос, который человек может поправить перед отправкой. */
    val searchQuery: String = "",
    val searchDone: Boolean = false,
    val message: String? = null,
    val deleted: Boolean = false,
)

/**
 * Сведения о книге и единственное место, откуда приложение может выйти в сеть.
 * Запрос всегда инициирует человек, и всегда после явного согласия.
 */
class BookDetailsViewModel(
    private val bookId: String,
    private val library: LibraryRepository,
    private val marksRepository: MarksRepository,
    private val metadata: MetadataRepository,
    private val settings: SettingsRepository,
    private val stats: StatsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BookDetailsUiState())
    val state: StateFlow<BookDetailsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            library.observeBook(bookId).collect { book ->
                _state.update { it.copy(book = book) }
            }
        }
        viewModelScope.launch {
            marksRepository.forBook(bookId).collect { marks -> _state.update { it.copy(marks = marks) } }
        }
        viewModelScope.launch {
            metadata.networkActive.collect { active -> _state.update { it.copy(networkActive = active) } }
        }
        viewModelScope.launch {
            _state.update { it.copy(readingMillis = stats.millisForBook(bookId)) }
        }
    }

    fun toggleFavorite() = viewModelScope.launch {
        val book = _state.value.book ?: return@launch
        library.setFavorite(book.id, !book.favorite)
    }

    fun pinAccent(color: Int) = viewModelScope.launch { library.setPinnedAccent(bookId, color) }

    fun unpinAccent() = viewModelScope.launch { library.setPinnedAccent(bookId, null) }

    fun deleteBook() = viewModelScope.launch {
        library.deleteBook(bookId)
        _state.update { it.copy(deleted = true) }
    }

    /**
     * Первый шаг подтяжки метаданных: показать, что именно будет отправлено.
     * Никакого запроса на этом шаге не происходит.
     */
    /**
     * Первый шаг. Если согласие уже дано раньше, диалог не показывается — раньше
     * галочка «больше не спрашивать» сохранялась, но не проверялась, и диалог
     * появлялся каждый раз.
     */
    fun requestMetadataFetch() = viewModelScope.launch {
        val allowed = settings.settings.first().metadataNetworkAllowed
        val book = _state.value.book
        val suggested = listOfNotNull(
            book?.title?.let { MetadataRepository.cleanQuery(it) }?.takeIf { it.isNotBlank() },
            book?.authors?.firstOrNull()?.let { MetadataRepository.cleanQuery(it) }?.takeIf { it.isNotBlank() },
        ).joinToString(" ")

        _state.update { it.copy(searchQuery = suggested, searchDone = false, candidates = emptyList()) }
        if (allowed) {
            _state.update { it.copy(searchOpen = true) }
            runSearch()
        } else {
            _state.update { it.copy(consentRequested = true) }
        }
    }

    fun cancelMetadataFetch() = _state.update { it.copy(consentRequested = false) }

    fun setSearchQuery(value: String) = _state.update { it.copy(searchQuery = value) }

    fun closeSearch() = _state.update {
        it.copy(searchOpen = false, candidates = emptyList(), searchDone = false)
    }

    /**
     * Поиск по строке, которую человек видит и может поправить.
     *
     * Пустой результат здесь — не ошибка, а обычный исход: названия из имён
     * файлов часто не находятся, и пользователю нужно дать шанс переписать
     * запрос, а не тупик со словами «ничего не найдено».
     */
    fun runSearch() = viewModelScope.launch {
        val query = _state.value.searchQuery.trim()
        if (query.isBlank()) return@launch
        _state.update { it.copy(searchDone = false, candidates = emptyList()) }
        metadata.searchFreeform(query)
            .onSuccess { candidates ->
                _state.update { it.copy(candidates = candidates, searchDone = true) }
            }
            .onFailure { error ->
                _state.update {
                    it.copy(
                        searchDone = true,
                        message = "Не удалось найти данные: ${error.message ?: "проверьте подключение к сети"}",
                    )
                }
            }
    }

    /** Второй шаг: пользователь согласился — только теперь идёт сетевой вызов. */
    fun confirmMetadataFetch(rememberChoice: Boolean) = viewModelScope.launch {
        val book = _state.value.book ?: return@launch
        _state.update { it.copy(consentRequested = false, searchOpen = true) }
        if (rememberChoice) {
            settings.update { it.copy(metadataNetworkAllowed = true) }
        }
        metadata.search(book.title, book.authors.firstOrNull())
            .onSuccess { candidates -> _state.update { it.copy(candidates = candidates, searchDone = true) } }
            .onFailure { error ->
                _state.update {
                    it.copy(
                        searchDone = true,
                        message = "Не удалось загрузить данные: ${error.message ?: "проверьте подключение к сети"}",
                    )
                }
            }
    }

    fun applyCandidate(candidate: MetadataCandidate) = viewModelScope.launch {
        val cover = candidate.coverUrl?.let { metadata.downloadCover(it).getOrNull() }
        library.updateMetadata(
            bookId = bookId,
            title = candidate.title,
            authors = candidate.authors.takeIf { it.isNotEmpty() },
            description = candidate.description,
            coverBytes = cover,
            year = candidate.year,
            publisher = candidate.publisher,
            series = candidate.series,
            seriesIndex = candidate.seriesIndex,
            fromNetwork = true,
        )
        _state.update {
            it.copy(candidates = emptyList(), searchOpen = false, searchDone = false, message = "Данные книги обновлены")
        }
    }

    fun dismissCandidates() = _state.update { it.copy(candidates = emptyList()) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun deleteMark(id: Long) = viewModelScope.launch { marksRepository.delete(id) }

    fun exportMarks(): String {
        val book = _state.value.book ?: return ""
        return marksRepository.exportMarkdown(book.title, book.authorLine, _state.value.marks)
    }

    companion object {
        fun factory(container: AppContainer, bookId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                BookDetailsViewModel(
                    bookId = bookId,
                    library = container.library,
                    marksRepository = container.marks,
                    metadata = container.metadata,
                    settings = container.settings,
                    stats = container.stats,
                )
            }
        }
    }
}
