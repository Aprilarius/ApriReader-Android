package com.aprireader.app.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aprireader.app.AppContainer
import com.aprireader.app.data.fonts.CustomFontRepository
import com.aprireader.app.data.library.LibraryRepository
import com.aprireader.app.data.prefs.AppSettings
import com.aprireader.app.data.prefs.FocusSettings
import com.aprireader.app.data.prefs.ReaderPageStyle
import com.aprireader.app.data.prefs.SettingsRepository
import com.aprireader.app.data.prefs.TypographySettings
import com.aprireader.app.data.reader.BookSession
import com.aprireader.app.data.reader.BookSessionFactory
import com.aprireader.app.data.stats.StatsRepository
import com.aprireader.app.domain.Book
import com.aprireader.app.ui.reader.rsvp.RsvpEngine
import com.aprireader.app.ui.reader.rsvp.RsvpFrame
import com.aprireader.bookformat.model.ChapterRef
import com.aprireader.bookformat.model.ContentBlock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReaderMode { FLOW, PAGES, RSVP }

data class RsvpState(
    val running: Boolean = false,
    val frames: List<RsvpFrame> = emptyList(),
    val index: Int = 0,
) {
    val current: RsvpFrame? get() = frames.getOrNull(index)
    val progress: Float get() = if (frames.isEmpty()) 0f else index.toFloat() / frames.size
}

/** Найденный фрагмент при поиске по тексту книги. */
data class SearchHit(
    val chapterIndex: Int,
    val chapterTitle: String?,
    val blockIndex: Int,
    val snippet: String,
    val matchStart: Int,
)

data class SearchState(
    val query: String = "",
    val running: Boolean = false,
    val hits: List<SearchHit> = emptyList(),
    val searchedChapters: Int = 0,
    val totalChapters: Int = 0,
    val finished: Boolean = false,
)

data class ReaderUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val book: Book? = null,
    val settings: AppSettings = AppSettings(),
    val mode: ReaderMode = ReaderMode.FLOW,
    val chapters: List<ChapterRef> = emptyList(),
    val chapterIndex: Int = 0,
    val blocks: List<ContentBlock> = emptyList(),
    val chapterTitle: String? = null,
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
    val chromeVisible: Boolean = false,
    val progress: Float = 0f,
    val rsvp: RsvpState = RsvpState(),
    val supportsTextModes: Boolean = true,
    val accent: Int? = null,
    val accentPinned: Boolean = false,
    val search: SearchState = SearchState(),
    val marks: List<com.aprireader.app.data.db.MarkEntity> = emptyList(),
    val scrollToBlock: Int? = null,
    /** Абзац, который сейчас озвучивается, — подсвечивается в тексте. */
    val speakingBlock: Int? = null,
    /** У PDF есть два режима: страницы и текстовый слой. */
    val isPdf: Boolean = false,
) {
    val typography: TypographySettings get() = settings.reader.typography
    val focus: FocusSettings get() = settings.reader.focus
    val pageStyle: ReaderPageStyle get() = settings.reader.pageStyle
    val isPaged: Boolean get() = mode == ReaderMode.PAGES
}

/**
 * Экран чтения.
 *
 * Прогресс сохраняется не по таймеру, а в моменты, когда он реально меняется:
 * смена главы, уход с экрана, пауза RSVP. Так база не молотит на каждый пиксель
 * прокрутки, но позиция не теряется даже при убийстве процесса системой.
 */
class ReaderViewModel(
    private val bookId: String,
    private val library: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionFactory: BookSessionFactory,
    private val stats: StatsRepository,
    private val marks: com.aprireader.app.data.marks.MarksRepository,
    private val presetDao: com.aprireader.app.data.db.PresetDao,
    private val tts: com.aprireader.app.data.tts.TtsController,
    private val customFontsRepo: CustomFontRepository,
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    val presets = presetDao.observeAll()
    val customFonts = customFontsRepo.fonts

    fun importCustomFont(uri: android.net.Uri, contentResolver: android.content.ContentResolver, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = customFontsRepo.importFont(uri, contentResolver)
            result.onSuccess { font ->
                updateTypography {
                    it.copy(
                        font = com.aprireader.app.data.prefs.ReadingFont.CUSTOM,
                        customFontPath = font.filePath,
                        customFontName = font.name,
                    )
                }
                onResult(true, font.name)
            }.onFailure {
                onResult(false, it.message)
            }
        }
    }

    fun deleteCustomFont(fontId: String) {
        viewModelScope.launch {
            customFontsRepo.deleteFont(fontId)
            if (_state.value.typography.font == com.aprireader.app.data.prefs.ReadingFont.CUSTOM) {
                updateTypography {
                    it.copy(
                        font = com.aprireader.app.data.prefs.ReadingFont.LITERATA,
                        customFontPath = null,
                        customFontName = null,
                    )
                }
            }
        }
    }

    private var session: BookSession? = null

    /**
     * Текстовый слой PDF, когда пользователь переключился в него из постраничного
     * режима. Живёт рядом с основной сессией: страницы всё ещё нужны, чтобы
     * вернуться обратно без повторного открытия файла.
     */
    private var pdfTextSession: BookSession.Text? = null

    /** Текстовая сессия, с которой сейчас работают главы, поиск, RSVP и озвучивание. */
    private fun textSession(): BookSession.Text? = session as? BookSession.Text ?: pdfTextSession
    private var rsvpJob: Job? = null
    private var searchJob: Job? = null
    /** Режим, из которого пользователь ушёл в RSVP, — чтобы вернуть его обратно. */
    private var modeBeforeRsvp: ReaderMode = ReaderMode.FLOW
    private var sessionStartedAt = 0L
    private var scrollFraction = 0f

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            marks.forBook(bookId).collect { list -> _state.update { it.copy(marks = list) } }
        }
        viewModelScope.launch { load() }
    }

    private object Search {
        const val MAX_SEARCH_HITS = 300

        fun snippetAround(text: String, at: Int, length: Int): String {
            val start = (at - 48).coerceAtLeast(0)
            val end = (at + length + 64).coerceAtMost(text.length)
            val prefix = if (start > 0) "…" else ""
            val suffix = if (end < text.length) "…" else ""
            return prefix + text.substring(start, end).replace('\n', ' ') + suffix
        }
    }

    private suspend fun load() {
        val book = library.getBook(bookId)
        if (book == null) {
            _state.update { it.copy(loading = false, error = "Книга не найдена") }
            return
        }
        if (!library.ensureAvailable(book)) {
            _state.update {
                it.copy(
                    loading = false,
                    book = book,
                    error = "Файл книги недоступен. Возможно, отозван доступ к папке или файл перемещён.",
                )
            }
            return
        }

        sessionFactory.open(book)
            .onSuccess { opened ->
                session = opened
                sessionStartedAt = System.currentTimeMillis()
                library.touch(book.id)
                when (opened) {
                    is BookSession.Text -> {
                        _state.update {
                            it.copy(
                                loading = false,
                                book = book,
                                mode = ReaderMode.FLOW,
                                chapters = opened.chapters,
                                pageCount = opened.unitCount,
                                supportsTextModes = true,
                                accent = book.effectiveAccent,
                                accentPinned = book.pinnedAccent != null,
                                progress = book.progress,
                            )
                        }
                        openChapter(
                            index = book.locatorUnit.coerceIn(0, (opened.chapters.size - 1).coerceAtLeast(0)),
                            restoreFraction = (book.locatorOffset / 10_000f).coerceIn(0f, 1f),
                        )
                    }

                    is BookSession.Comic -> _state.update {
                        it.copy(
                            loading = false,
                            book = book,
                            mode = ReaderMode.PAGES,
                            pageCount = opened.unitCount,
                            pageIndex = book.locatorUnit.coerceIn(0, (opened.unitCount - 1).coerceAtLeast(0)),
                            supportsTextModes = false,
                            accent = book.effectiveAccent,
                            accentPinned = book.pinnedAccent != null,
                            progress = book.progress,
                        )
                    }

                    is BookSession.Pdf -> _state.update {
                        it.copy(
                            isPdf = true,
                            loading = false,
                            book = book,
                            mode = ReaderMode.PAGES,
                            pageCount = opened.unitCount,
                            pageIndex = book.locatorUnit.coerceIn(0, (opened.unitCount - 1).coerceAtLeast(0)),
                            supportsTextModes = true,
                            accent = book.effectiveAccent,
                            accentPinned = book.pinnedAccent != null,
                            progress = book.progress,
                        )
                    }
                }
            }
            .onFailure { error ->
                _state.update {
                    it.copy(loading = false, book = book, error = "Не удалось открыть книгу: ${error.message}")
                }
            }
    }

    /**
     * Открывает главу. [restoreFraction] используется при возврате в книгу:
     * позиция внутри главы восстанавливается приблизительно, по доле абзацев —
     * точный пиксельный офсет бессмыслен, потому что типографика могла измениться.
     */
    fun openChapter(index: Int, restoreFraction: Float = 0f, targetBlock: Int? = null) {
        val text = textSession() ?: return
        viewModelScope.launch {
            val bounded = index.coerceIn(0, (text.chapters.size - 1).coerceAtLeast(0))
            val chapter = runCatching { text.chapter(bounded) }.getOrNull()
            val blocks = chapter?.blocks.orEmpty()
            // Цель прокрутки вычисляется здесь, а не снаружи: главу ещё нужно
            // загрузить, и любое значение, выставленное до этого, было бы
            // затёрто этим же обновлением состояния.
            val target = when {
                targetBlock != null && blocks.isNotEmpty() ->
                    targetBlock.coerceIn(0, blocks.size - 1)
                restoreFraction > 0.001f && blocks.isNotEmpty() ->
                    (blocks.size * restoreFraction).toInt().coerceIn(0, blocks.size - 1)
                else -> null
            }
            _state.update {
                it.copy(
                    chapterIndex = bounded,
                    blocks = blocks,
                    chapterTitle = text.chapters.getOrNull(bounded)?.title,
                    rsvp = RsvpState(),
                    progress = text.progressOf(bounded, restoreFraction),
                    scrollToBlock = target,
                )
            }
            scrollFraction = restoreFraction
            saveProgress()
        }
    }

    fun nextChapter() = openChapter(_state.value.chapterIndex + 1)

    fun previousChapter() = openChapter(_state.value.chapterIndex - 1)

    /** Позиция прокрутки внутри главы — влияет на прогресс, но не пишется в базу немедленно. */
    fun onScrollFraction(fraction: Float) {
        val text = textSession() ?: return
        scrollFraction = fraction.coerceIn(0f, 1f)
        _state.update { it.copy(progress = text.progressOf(it.chapterIndex, scrollFraction)) }
    }

    fun onPageChanged(index: Int) {
        val total = _state.value.pageCount
        if (total <= 0) return
        val bounded = index.coerceIn(0, total - 1)
        _state.update {
            it.copy(pageIndex = bounded, progress = (bounded + 1).toFloat() / total)
        }
    }

    fun toggleChrome() = _state.update { it.copy(chromeVisible = !it.chromeVisible) }

    fun setChromeVisible(visible: Boolean) = _state.update { it.copy(chromeVisible = visible) }

    // --- режимы фокуса ---

    fun toggleBionic() = updateSettings { settings ->
        settings.copy(
            reader = settings.reader.copy(
                focus = settings.reader.focus.copy(bionicEnabled = !settings.reader.focus.bionicEnabled),
            )
        )
    }

    fun setBionicIntensity(value: Float) = updateSettings { settings ->
        settings.copy(
            reader = settings.reader.copy(focus = settings.reader.focus.copy(bionicIntensity = value)),
        )
    }

    fun setRsvpSpeed(wpm: Int) = updateSettings { settings ->
        settings.copy(reader = settings.reader.copy(focus = settings.reader.focus.copy(rsvpWpm = wpm)))
    }

    /**
     * RSVP собирается из уже разобранных блоков текущей главы. Для PDF при этом
     * подгружается текстовый слой — постранично отрисованный PDF словами не оперирует.
     */
    fun enterRsvp() {
        viewModelScope.launch {
            val blocks = _state.value.blocks.ifEmpty { loadTextBlocksForCurrentUnit() }
            if (blocks.isEmpty()) {
                _state.update { it.copy(error = "В этом фрагменте нет текста для режима RSVP") }
                return@launch
            }
            val frames = RsvpEngine.buildFrames(
                blocks = blocks,
                chunkSize = _state.value.focus.rsvpChunkSize,
                pauseOnPunctuation = _state.value.focus.rsvpPauseOnPunctuation,
            )
            modeBeforeRsvp = _state.value.mode
            val startIndex = (frames.size * scrollFraction).toInt().coerceIn(0, (frames.size - 1).coerceAtLeast(0))
            _state.update {
                it.copy(
                    mode = ReaderMode.RSVP,
                    blocks = blocks,
                    chromeVisible = false,
                    rsvp = RsvpState(running = false, frames = frames, index = startIndex),
                )
            }
        }
    }

    fun exitRsvp() {
        rsvpJob?.cancel()
        val text = textSession()
        _state.update {
            val fraction = it.rsvp.progress
            it.copy(
                // Возвращаемся ровно в тот режим, из которого зашли: у PDF это
                // почти всегда страницы, и подмена их текстовым слоем выглядела
                // бы так, будто приложение потеряло книгу.
                mode = modeBeforeRsvp,
                rsvp = RsvpState(),
                progress = if (modeBeforeRsvp == ReaderMode.FLOW && text != null) {
                    text.progressOf(it.chapterIndex, fraction)
                } else {
                    it.progress
                },
            )
        }
        saveProgress()
    }

    fun toggleRsvpPlayback() {
        if (_state.value.rsvp.running) pauseRsvp() else startRsvp()
    }

    private fun startRsvp() {
        rsvpJob?.cancel()
        _state.update { it.copy(rsvp = it.rsvp.copy(running = true)) }
        rsvpJob = viewModelScope.launch {
            while (true) {
                val current = _state.value
                val frame = current.rsvp.current ?: break
                delay(
                    RsvpEngine.frameDurationMs(
                        frame = frame,
                        wordsPerMinute = current.focus.rsvpWpm,
                        chunkSize = current.focus.rsvpChunkSize,
                    )
                )
                val next = _state.value.rsvp.index + 1
                if (next >= _state.value.rsvp.frames.size) {
                    _state.update { it.copy(rsvp = it.rsvp.copy(running = false)) }
                    break
                }
                _state.update { it.copy(rsvp = it.rsvp.copy(index = next)) }
            }
        }
    }

    private fun pauseRsvp() {
        rsvpJob?.cancel()
        _state.update { it.copy(rsvp = it.rsvp.copy(running = false)) }
        saveProgress()
    }

    fun rsvpSeek(index: Int) {
        _state.update {
            it.copy(rsvp = it.rsvp.copy(index = index.coerceIn(0, (it.rsvp.frames.size - 1).coerceAtLeast(0))))
        }
    }

    fun rsvpStepBack(words: Int = 12) = rsvpSeek(_state.value.rsvp.index - words)

    /**
     * Переключение PDF между постраничным просмотром и текстовым слоем.
     *
     * Именно это делает бионический шрифт и RSVP доступными в PDF: отрисованная
     * страница — картинка, слов в ней нет. Текст извлекается лениво, при первом
     * переключении, и дальше переиспользуется.
     */
    fun togglePdfTextMode() {
        val pdf = session as? BookSession.Pdf ?: return

        if (_state.value.mode == ReaderMode.FLOW) {
            val page = pdfTextSession?.let { firstPageOf(it.chapters, _state.value.chapterIndex) } ?: 1
            _state.update {
                it.copy(mode = ReaderMode.PAGES, blocks = emptyList(), pageIndex = (page - 1).coerceAtLeast(0))
            }
            saveProgress()
            return
        }

        viewModelScope.launch {
            val book = _state.value.book ?: return@launch
            val document = pdf.text()
            if (document == null || document.chapters.isEmpty()) {
                _state.update {
                    it.copy(error = "В этом PDF нет текстового слоя — похоже, страницы отсканированы картинками")
                }
                return@launch
            }
            val wrapped = pdfTextSession ?: BookSession.Text(book, document).also { pdfTextSession = it }
            val chapterIndex = chapterForPage(wrapped.chapters, _state.value.pageIndex)
            _state.update { it.copy(mode = ReaderMode.FLOW, chapters = wrapped.chapters) }
            openChapter(chapterIndex)
        }
    }

    /** Текст текущего разворота PDF — нужен RSVP, когда пользователь остался на страницах. */
    private suspend fun loadTextBlocksForCurrentUnit(): List<ContentBlock> {
        val pdf = session as? BookSession.Pdf ?: return emptyList()
        val book = _state.value.book ?: return emptyList()
        val document = pdf.text() ?: return emptyList()
        val wrapped = pdfTextSession ?: BookSession.Text(book, document).also { pdfTextSession = it }
        val chapterIndex = chapterForPage(wrapped.chapters, _state.value.pageIndex)
        return runCatching { wrapped.chapter(chapterIndex).blocks }.getOrDefault(emptyList())
    }

    /**
     * Текстовый слой режется на куски страниц, а идентификатор куска хранит его
     * первую страницу («p13-24»). Нужен последний кусок, начинающийся не позже
     * текущей страницы, — indexOfFirst здесь всегда возвращал бы нулевой.
     */
    private fun chapterForPage(chapters: List<ChapterRef>, pageIndex: Int): Int =
        chapters.indexOfLast { ref -> pageStartOf(ref) <= pageIndex + 1 }.coerceAtLeast(0)

    private fun pageStartOf(ref: ChapterRef): Int =
        ref.id.removePrefix("p").substringBefore('-').toIntOrNull() ?: 1

    private fun firstPageOf(chapters: List<ChapterRef>, index: Int): Int =
        chapters.getOrNull(index)?.let { pageStartOf(it) } ?: 1

    // --- акцент книги ---

    /** Закрепляет цвет за книгой. Ровно это обещание: автоматика его больше не тронет. */
    fun pinAccent(color: Int) = viewModelScope.launch {
        library.setPinnedAccent(bookId, color)
        _state.update { it.copy(accent = color, accentPinned = true) }
    }

    fun unpinAccent() = viewModelScope.launch {
        library.setPinnedAccent(bookId, null)
        val book = library.getBook(bookId)
        _state.update { it.copy(accent = book?.autoAccent, accentPinned = false) }
    }

    // --- типографика ---

    // --- пресеты типографики ---

    /** Сохраняет текущий набор настроек под именем, чтобы к нему можно было вернуться. */
    fun savePreset(name: String) = viewModelScope.launch {
        val typography = _state.value.typography
        presetDao.upsert(
            com.aprireader.app.data.db.TypographyPresetEntity(
                name = name.ifBlank { "Мой пресет" },
                fontFamily = typography.font.key,
                fontSizeSp = typography.fontSizeSp,
                lineHeight = typography.lineHeight,
                letterSpacing = typography.letterSpacing,
                paragraphSpacing = typography.paragraphSpacing,
                horizontalMargin = typography.horizontalMarginDp,
                maxLineWidth = typography.maxLineWidthChars,
                justify = typography.justify,
                firstLineIndent = typography.firstLineIndent,
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    fun applyPreset(preset: com.aprireader.app.data.db.TypographyPresetEntity) = updateTypography { current ->
        current.copy(
            font = com.aprireader.app.data.prefs.ReadingFont.fromKey(preset.fontFamily),
            fontSizeSp = preset.fontSizeSp,
            lineHeight = preset.lineHeight,
            letterSpacing = preset.letterSpacing,
            paragraphSpacing = preset.paragraphSpacing,
            horizontalMarginDp = preset.horizontalMargin,
            maxLineWidthChars = preset.maxLineWidth,
            justify = preset.justify,
            firstLineIndent = preset.firstLineIndent,
        )
    }

    fun deletePreset(id: Long) = viewModelScope.launch { presetDao.delete(id) }

    fun updateTypography(transform: (TypographySettings) -> TypographySettings) = updateSettings { settings ->
        settings.copy(reader = settings.reader.copy(typography = transform(settings.reader.typography)))
    }

    fun updateReader(transform: (com.aprireader.app.data.prefs.ReaderSettings) -> com.aprireader.app.data.prefs.ReaderSettings) =
        updateSettings { it.copy(reader = transform(it.reader)) }

    fun setScrollMode(mode: com.aprireader.app.data.prefs.ReadingScrollMode) =
        updateReader { it.copy(scrollMode = mode, horizontalPaging = mode == com.aprireader.app.data.prefs.ReadingScrollMode.PAGED_HORIZONTAL) }

    /**
     * Переключает «Скролл ⇄ Листание» кнопкой из шторки чтения — не просто
     * меняет настройку, а переносит текущую позицию в главе из одного движка
     * в другой.
     *
     * `FlowReader` и `PagedTextReader` — это два разных Composable в одном
     * `when` в `ReaderScreen`: при смене `scrollMode` старый уничтожается, а
     * новый создаётся с нуля и знает о положении в главе только через
     * [ReaderUiState.scrollToBlock]. Если просто дёрнуть [setScrollMode], этот
     * параметр придёт с опозданием — настройка идёт через DataStore и долетает
     * до состояния асинхронно, а старый ридер к этому моменту уже мог сам
     * среагировать на `scrollToBlock` (тем же механизмом, что и переход по
     * оглавлению) и обнулить его самостоятельно, до того как вообще
     * появился новый ридер. Поэтому режим и целевой блок выставляются одним
     * атомарным обновлением состояния экрана — старый ридер снимается с
     * композиции в том же кадре, где меняется режим, и не успевает
     * отреагировать на промежуточное значение.
     */
    fun toggleFlowPagingMode() {
        val current = _state.value
        val blocks = current.blocks
        val target = if (blocks.isNotEmpty()) {
            (blocks.size * scrollFraction).toInt().coerceIn(0, blocks.size - 1)
        } else {
            null
        }
        val nextMode = if (current.settings.reader.scrollMode == com.aprireader.app.data.prefs.ReadingScrollMode.CONTINUOUS_VERTICAL) {
            com.aprireader.app.data.prefs.ReadingScrollMode.PAGED_HORIZONTAL
        } else {
            com.aprireader.app.data.prefs.ReadingScrollMode.CONTINUOUS_VERTICAL
        }
        _state.update {
            it.copy(
                scrollToBlock = target,
                settings = it.settings.copy(
                    reader = it.settings.reader.copy(
                        scrollMode = nextMode,
                        horizontalPaging = nextMode == com.aprireader.app.data.prefs.ReadingScrollMode.PAGED_HORIZONTAL,
                    ),
                ),
            )
        }
        // Персистентная настройка обновляется тем же вызовом — асинхронно,
        // через DataStore. К моменту, когда её значение долетит обратно сюда
        // через settingsRepository.settings.collect, экран уже переключён
        // локальным обновлением выше, так что повторное присвоение того же
        // scrollMode ничего не сломает.
        setScrollMode(nextMode)
    }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        settingsRepository.update(transform)
    }

    fun consumeError() = _state.update { it.copy(error = null) }

    // --- озвучивание ---

    val ttsState = tts.state

    /**
     * Озвучивает текущую главу, начиная с указанного абзаца. Язык определяется из
     * метаданных книги или автоматически по тексту главы с надёжными фоллбэками.
     */
    fun startSpeaking(fromBlock: Int = 0) {
        val texts = _state.value.blocks.map { block ->
            when (block) {
                is ContentBlock.Heading -> block.text.text
                is ContentBlock.Paragraph -> block.text.text
                else -> ""
            }
        }
        if (texts.all { it.isBlank() }) {
            _state.update { it.copy(error = "В этой главе нечего озвучивать") }
            return
        }

        val firstSample = texts.firstOrNull { it.isNotBlank() }
        val locale = com.aprireader.app.data.tts.TtsController.resolveLocale(
            languageCode = _state.value.book?.language,
            sampleText = firstSample,
        )

        val startIndex = fromBlock.coerceIn(0, (texts.size - 1).coerceAtLeast(0))
        _state.update { it.copy(speakingBlock = startIndex) }

        tts.speak(
            blocks = texts,
            fromIndex = startIndex,
            language = locale,
            onBlock = { index -> _state.update { it.copy(speakingBlock = index) } },
            onComplete = {
                _state.update { it.copy(speakingBlock = null) }
                // Дочитали главу вслух — продолжаем со следующей.
                if (_state.value.chapterIndex < _state.value.chapters.lastIndex) {
                    openChapter(_state.value.chapterIndex + 1)
                }
            },
        )
    }

    fun toggleSpeaking(fromVisibleBlock: Int = 0) {
        when {
            tts.state.value.speaking -> tts.pause()
            _state.value.speakingBlock != null -> tts.resume()
            else -> startSpeaking(fromVisibleBlock)
        }
    }

    fun stopSpeaking() {
        tts.stop()
        _state.update { it.copy(speakingBlock = null) }
    }

    fun skipSpeaking(delta: Int) = tts.skip(delta)

    fun setSpeechRate(rate: Float) = tts.setRate(rate)

    // --- закладки и выделения ---

    /** Закладка ставится на текущую позицию: главу и видимый абзац. */
    fun addBookmark(blockIndex: Int) = viewModelScope.launch {
        val current = _state.value
        val quote = (current.blocks.getOrNull(blockIndex) as? ContentBlock.Paragraph)?.text?.text?.take(160)
        marks.addBookmark(
            bookId = bookId,
            unit = if (current.isPaged) current.pageIndex else current.chapterIndex,
            offset = blockIndex,
            chapterTitle = current.chapterTitle,
            quote = quote,
        )
    }

    /**
     * Выделение делается на уровне абзаца, а не произвольного диапазона.
     * Компромисс сознательный: посимвольное выделение поверх собственного
     * рендера текста потребовало бы своей реализации хэндлов и не дало бы
     * заметного выигрыша для читателя.
     */
    fun highlightBlock(blockIndex: Int, note: String? = null, colorIndex: Int = 0) = viewModelScope.launch {
        val current = _state.value
        val block = current.blocks.getOrNull(blockIndex)
        val text = when (block) {
            is ContentBlock.Paragraph -> block.text.text
            is ContentBlock.Heading -> block.text.text
            else -> return@launch
        }
        marks.addHighlight(
            bookId = bookId,
            unit = current.chapterIndex,
            startOffset = blockIndex,
            endOffset = blockIndex,
            quote = text,
            note = note,
            colorIndex = colorIndex,
            chapterTitle = current.chapterTitle,
        )
    }

    fun removeMark(id: Long) = viewModelScope.launch { marks.delete(id) }

    // --- поиск по книге ---

    fun setSearchQuery(query: String) = _state.update { it.copy(search = it.search.copy(query = query)) }

    /**
     * Поиск идёт последовательно по главам с отдачей промежуточных результатов:
     * в книге на 900 страниц ждать полного прохода, глядя на пустой экран, нельзя.
     */
    fun runSearch() {
        val text = textSession() ?: return
        val query = _state.value.search.query.trim()
        if (query.length < 2) return
        searchJob?.cancel()
        _state.update {
            it.copy(
                search = it.search.copy(
                    running = true,
                    hits = emptyList(),
                    searchedChapters = 0,
                    totalChapters = text.chapters.size,
                    finished = false,
                ),
            )
        }
        searchJob = viewModelScope.launch {
            val hits = ArrayList<SearchHit>()
            for (index in text.chapters.indices) {
                val chapter = runCatching { text.chapter(index) }.getOrNull()
                chapter?.blocks?.forEachIndexed { blockIndex, block ->
                    val content = when (block) {
                        is ContentBlock.Paragraph -> block.text.text
                        is ContentBlock.Heading -> block.text.text
                        else -> null
                    } ?: return@forEachIndexed
                    var from = 0
                    while (from < content.length) {
                        val at = content.indexOf(query, from, ignoreCase = true)
                        if (at < 0) break
                        hits += SearchHit(
                            chapterIndex = index,
                            chapterTitle = chapter.ref.title,
                            blockIndex = blockIndex,
                            snippet = Search.snippetAround(content, at, query.length),
                            matchStart = at,
                        )
                        from = maxOf(from + 1, at + query.length)
                        if (hits.size >= Search.MAX_SEARCH_HITS) break
                    }
                }
                _state.update {
                    it.copy(search = it.search.copy(hits = hits.toList(), searchedChapters = index + 1))
                }
                if (hits.size >= Search.MAX_SEARCH_HITS) break
            }
            _state.update { it.copy(search = it.search.copy(running = false, finished = true)) }
        }
    }

    fun cancelSearch() {
        searchJob?.cancel()
        _state.update { it.copy(search = SearchState()) }
    }

    fun openHit(hit: SearchHit) {
        openChapter(hit.chapterIndex, targetBlock = hit.blockIndex)
    }

    fun consumeScrollTarget() = _state.update { it.copy(scrollToBlock = null) }

    suspend fun pageBytes(index: Int): ByteArray? = (session as? BookSession.Comic)?.page(index)

    suspend fun renderPdfPage(index: Int, widthPx: Int) =
        (session as? BookSession.Pdf)?.renderPage(index, widthPx)

    suspend fun resource(href: String): ByteArray? = textSession()?.resource(href)

    fun saveProgress() {
        val current = _state.value
        val book = current.book ?: return
        // Пишем через область приложения: сохранение позиции обязано завершиться,
        // даже если экран закрывается прямо сейчас.
        appScope.launch {
            // Позиция в PDF всегда хранится в страницах, даже когда читатель
            // сейчас в текстовом слое: иначе при следующем открытии номер главы
            // был бы понят как номер страницы.
            val unit = when {
                current.mode == ReaderMode.PAGES -> current.pageIndex
                pdfTextSession != null && session is BookSession.Pdf ->
                    (firstPageOf(current.chapters, current.chapterIndex) - 1).coerceAtLeast(0)
                else -> current.chapterIndex
            }
            library.updateProgress(
                bookId = book.id,
                unit = unit,
                offset = (scrollFraction * 10_000).toInt(),
                progress = current.progress,
                totalUnits = current.pageCount.coerceAtLeast(current.chapters.size),
            )
        }
    }

    override fun onCleared() {
        rsvpJob?.cancel()
        searchJob?.cancel()
        tts.release()
        saveProgress()
        val book = _state.value.book
        val started = sessionStartedAt
        if (book != null && started > 0) {
            val charsRead = ((_state.value.progress - book.progress).coerceAtLeast(0f) * book.totalChars).toInt()
            appScope.launch {
                stats.recordSession(book.id, started, System.currentTimeMillis(), charsRead)
            }
        }
        session?.close()
        session = null
        pdfTextSession?.close()
        pdfTextSession = null
        super.onCleared()
    }

    companion object {
        fun factory(container: AppContainer, bookId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ReaderViewModel(
                    bookId = bookId,
                    library = container.library,
                    settingsRepository = container.settings,
                    sessionFactory = container.sessionFactory,
                    stats = container.stats,
                    marks = container.marks,
                    presetDao = container.database.presetDao(),
                    tts = container.tts,
                    customFontsRepo = container.customFonts,
                    appScope = container.appScope,
                )
            }
        }
    }
}
