package com.aprireader.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

enum class DesignStyle {
    LIQUID_GLASS,   // Жидкое стекло: глубокая прозрачность, сияющий акцент, парящий лоск
    GLASSMORPHISM,  // Глассморфизм: утонченный матовый Frosted Glass, мягкие стеклянные контуры
    SOLID_CLEAN,    // Чистая классика: высокая контрастность, строгие матовые карточки без прозрачности
    NEUMORPHISM,    // Неоморфизм: объемные мягкие рельефы и плавные тени
    WOOD_LIBRARY,   // Деревянная полка: классический eReader с теплыми древесными фактурами
}

enum class ReadingScrollMode {
    CONTINUOUS_VERTICAL, // Непрерывный вертикальный свиток (чтение всей книги от начала до конца)
    PAGED_HORIZONTAL,    // Постраничное листание слева направо
    PAGED_VERTICAL,      // Постраничное листание сверху вниз
}

enum class ShelfLayout { SHELF, GRID, LIST, COMPACT }

enum class ShelfSort { RECENT, TITLE, AUTHOR, ADDED, PROGRESS }

enum class ShelfGrouping { NONE, AUTHOR, SERIES, FORMAT, PROGRESS }

enum class ReaderPageStyle { FOLLOW_THEME, PAPER, SEPIA, GRAPHITE, BLACK }

enum class ReadingFont(val key: String, val displayName: String) {
    LITERATA("literata", "Literata"),
    LORA("lora", "Lora"),
    MERRIWEATHER("merriweather", "Merriweather"),
    PT_SERIF("pt_serif", "PT Serif"),
    EB_GARAMOND("eb_garamond", "EB Garamond"),
    SPECTRAL("spectral", "Spectral"),
    SOURCE_SERIF("source_serif", "Source Serif 4"),
    BITTER("bitter", "Bitter"),
    ALEGREYA("alegreya", "Alegreya"),
    VOLLKORN("vollkorn", "Vollkorn"),
    NOTO_SERIF("noto_serif", "Noto Serif"),
    CORMORANT("cormorant", "Cormorant"),
    ATKINSON("atkinson", "Atkinson Hyperlegible"),
    LEXEND("lexend", "Lexend"),
    INTER("inter", "Inter"),
    FIRA_SANS("fira_sans", "Fira Sans"),
    SYSTEM_SERIF("system_serif", "Системный с засечками"),
    SYSTEM_SANS("system_sans", "Системный без засечек"),
    MONO("mono", "Моноширинный"),
    CUSTOM("custom", "Пользовательский");

    companion object {
        fun fromKey(key: String?): ReadingFont = entries.firstOrNull { it.key == key } ?: LITERATA
    }
}

/** Настройки типографики экрана чтения. Отделены от шрифта интерфейса намеренно. */
data class TypographySettings(
    val font: ReadingFont = ReadingFont.LITERATA,
    val customFontPath: String? = null,
    val customFontName: String? = null,
    val fontSizeSp: Float = 19f,
    val lineHeight: Float = 1.55f,
    val letterSpacing: Float = 0f,
    val paragraphSpacing: Float = 0.6f,
    val horizontalMarginDp: Float = 24f,
    val maxLineWidthChars: Int = 72,
    val justify: Boolean = false,
    val firstLineIndent: Boolean = false,
    val hyphenation: Boolean = true,
)

/** Настройки режимов для читателей с СДВГ. */
data class FocusSettings(
    val bionicEnabled: Boolean = false,
    /** Доля слова, выделяемая жирным: 0.3–0.7. */
    val bionicIntensity: Float = 0.45f,
    /** Приглушение «хвоста» слова — усиливает эффект фиксации. */
    val bionicDimTail: Boolean = true,
    val rsvpWpm: Int = 300,
    val rsvpChunkSize: Int = 1,
    val rsvpHighlightPivot: Boolean = true,
    val rsvpPauseOnPunctuation: Boolean = true,
    val focusLineHighlight: Boolean = false,
)

data class ReaderSettings(
    val typography: TypographySettings = TypographySettings(),
    val focus: FocusSettings = FocusSettings(),
    val pageStyle: ReaderPageStyle = ReaderPageStyle.FOLLOW_THEME,
    val scrollMode: ReadingScrollMode = ReadingScrollMode.PAGED_HORIZONTAL,
    val continuousReading: Boolean = true,
    val tapZonesPaging: Boolean = true,
    val keepScreenOn: Boolean = true,
    val fullscreen: Boolean = true,
    val volumeKeysPaging: Boolean = false,
    val showProgressBar: Boolean = true,
    val horizontalPaging: Boolean = true,
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val designStyle: DesignStyle = DesignStyle.LIQUID_GLASS,
    val pureBlackDark: Boolean = false,
    val dynamicCoverTheming: Boolean = true,
    val systemDynamicColor: Boolean = false,
    val globalAccent: Int? = null,
    val shelfLayout: ShelfLayout = ShelfLayout.SHELF,
    val shelfSort: ShelfSort = ShelfSort.RECENT,
    val shelfGrouping: ShelfGrouping = ShelfGrouping.NONE,
    val shelfAscending: Boolean = false,
    val reader: ReaderSettings = ReaderSettings(),
    val onboardingCompleted: Boolean = false,
    /** Как обращаться к читателю. Пустая строка — обращаться никак. */
    val userName: String = "",
    /** Локальный аватар: ID пресета ("m1_scholar", ...) или "custom". */
    val userAvatarId: String = "m1_scholar",
    /** Путь к локальному файлу аватарки пользователя (если выбран "custom"). */
    val customAvatarPath: String? = null,
    /** Литературный титул / архетип читателя. */
    val userTitleKey: String = "book_keeper",
    /** Короткая цитата или описание читателя. */
    val userBio: String = "",
    /** Язык интерфейса; null — язык системы. */
    val languageTag: String? = null,
    /**
     * Разрешение на сетевую подтяжку метаданных. По умолчанию false — до явного
     * согласия приложение не делает ни одного сетевого запроса.
     */
    val metadataNetworkAllowed: Boolean = false,
    val reduceMotion: Boolean = false,
    /** Количество колонок на книжной полке: 2 (крупный), 3 (стандарт), 4 (компакт), 5 (мини). */
    val shelfColumns: Int = 3,
    /** Отображать реалистичный 3D-корешок и светотеневой рельеф книги на полке. */
    val shelf3dCrease: Boolean = true,
    /** Скорость озвучки (TTS) по умолчанию: 0.75f - 2.5f. */
    val ttsDefaultSpeed: Float = 1.0f,
    /** Имя пакета выбранного TTS движка; null — системный движок по умолчанию. */
    val ttsEnginePackage: String? = null,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "apri_settings")

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun getInitialSettings(): AppSettings = context.dataStore.data.first().toSettings()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val current = prefs.toSettings()
            val updated = transform(current)
            if (updated.languageTag != current.languageTag) {
                LocaleStore.write(context, updated.languageTag)
            }
            prefs.write(updated)
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val rawTheme = this[Keys.themeMode]
        val pureBlack = this[Keys.pureBlack] ?: false
        val resolvedTheme = when {
            rawTheme == "AMOLED" -> ThemeMode.AMOLED
            rawTheme == "DARK" && pureBlack -> ThemeMode.AMOLED
            else -> enumOf(rawTheme, ThemeMode.SYSTEM)
        }
        return AppSettings(
            themeMode = resolvedTheme,
            designStyle = enumOf(this[Keys.designStyle], DesignStyle.LIQUID_GLASS),
            pureBlackDark = pureBlack || resolvedTheme == ThemeMode.AMOLED,
        dynamicCoverTheming = this[Keys.dynamicCover] ?: true,
        systemDynamicColor = this[Keys.systemDynamic] ?: false,
        globalAccent = this[Keys.globalAccent],
        shelfLayout = enumOf(this[Keys.shelfLayout], ShelfLayout.SHELF),
        shelfSort = enumOf(this[Keys.shelfSort], ShelfSort.RECENT),
        shelfGrouping = enumOf(this[Keys.shelfGrouping], ShelfGrouping.NONE),
        shelfAscending = this[Keys.shelfAscending] ?: false,
        onboardingCompleted = this[Keys.isOnboardingCompleted] ?: this[Keys.onboarding] ?: false,
        userName = this[Keys.userName].orEmpty(),
        userAvatarId = this[Keys.userAvatarId] ?: "m1_scholar",
        customAvatarPath = this[Keys.customAvatarPath],
        userTitleKey = this[Keys.userTitleKey] ?: "book_keeper",
        userBio = this[Keys.userBio].orEmpty(),
        languageTag = this[Keys.languageTag],
        metadataNetworkAllowed = this[Keys.metadataAllowed] ?: false,
        reduceMotion = this[Keys.reduceMotion] ?: false,
        shelfColumns = this[Keys.shelfColumns] ?: 3,
        shelf3dCrease = this[Keys.shelf3dCrease] ?: true,
        ttsDefaultSpeed = this[Keys.ttsDefaultSpeed] ?: 1.0f,
        ttsEnginePackage = this[Keys.ttsEnginePackage],
        reader = ReaderSettings(
            typography = TypographySettings(
                font = ReadingFont.fromKey(this[Keys.font]),
                customFontPath = this[Keys.customFontPath],
                customFontName = this[Keys.customFontName],
                fontSizeSp = this[Keys.fontSize] ?: 19f,
                lineHeight = this[Keys.lineHeight] ?: 1.55f,
                letterSpacing = this[Keys.letterSpacing] ?: 0f,
                paragraphSpacing = this[Keys.paragraphSpacing] ?: 0.6f,
                horizontalMarginDp = this[Keys.margin] ?: 24f,
                maxLineWidthChars = this[Keys.maxLineWidth] ?: 72,
                justify = this[Keys.justify] ?: false,
                firstLineIndent = this[Keys.indent] ?: false,
                hyphenation = this[Keys.hyphenation] ?: true,
            ),
            focus = FocusSettings(
                bionicEnabled = this[Keys.bionic] ?: false,
                bionicIntensity = this[Keys.bionicIntensity] ?: 0.45f,
                bionicDimTail = this[Keys.bionicDim] ?: true,
                rsvpWpm = this[Keys.rsvpWpm] ?: 300,
                rsvpChunkSize = this[Keys.rsvpChunk] ?: 1,
                rsvpHighlightPivot = this[Keys.rsvpPivot] ?: true,
                rsvpPauseOnPunctuation = this[Keys.rsvpPause] ?: true,
                focusLineHighlight = this[Keys.focusLine] ?: false,
            ),
            pageStyle = enumOf(this[Keys.pageStyle], ReaderPageStyle.FOLLOW_THEME),
            scrollMode = enumOf(this[Keys.scrollMode], ReadingScrollMode.PAGED_HORIZONTAL),
            continuousReading = this[Keys.continuousReading] ?: true,
            tapZonesPaging = this[Keys.tapZonesPaging] ?: true,
            keepScreenOn = this[Keys.keepScreenOn] ?: true,
            fullscreen = this[Keys.fullscreen] ?: true,
            volumeKeysPaging = this[Keys.volumeKeys] ?: false,
            showProgressBar = this[Keys.progressBar] ?: true,
            horizontalPaging = this[Keys.horizontalPaging] ?: false,
        ),
    )
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.write(value: AppSettings) {
        this[Keys.themeMode] = value.themeMode.name
        this[Keys.designStyle] = value.designStyle.name
        this[Keys.pureBlack] = value.pureBlackDark
        this[Keys.dynamicCover] = value.dynamicCoverTheming
        this[Keys.systemDynamic] = value.systemDynamicColor
        value.globalAccent?.let { this[Keys.globalAccent] = it } ?: remove(Keys.globalAccent)
        this[Keys.shelfLayout] = value.shelfLayout.name
        this[Keys.shelfSort] = value.shelfSort.name
        this[Keys.shelfGrouping] = value.shelfGrouping.name
        this[Keys.shelfAscending] = value.shelfAscending
        this[Keys.isOnboardingCompleted] = value.onboardingCompleted
        this[Keys.onboarding] = value.onboardingCompleted
        this[Keys.userName] = value.userName
        this[Keys.userAvatarId] = value.userAvatarId
        if (value.customAvatarPath != null) {
            this[Keys.customAvatarPath] = value.customAvatarPath
        } else {
            this.remove(Keys.customAvatarPath)
        }
        this[Keys.userTitleKey] = value.userTitleKey
        this[Keys.userBio] = value.userBio
        value.languageTag?.let { this[Keys.languageTag] = it } ?: remove(Keys.languageTag)
        this[Keys.metadataAllowed] = value.metadataNetworkAllowed
        this[Keys.reduceMotion] = value.reduceMotion
        this[Keys.shelfColumns] = value.shelfColumns
        this[Keys.shelf3dCrease] = value.shelf3dCrease
        this[Keys.ttsDefaultSpeed] = value.ttsDefaultSpeed
        value.ttsEnginePackage?.let { this[Keys.ttsEnginePackage] = it } ?: remove(Keys.ttsEnginePackage)

        val typography = value.reader.typography
        this[Keys.font] = typography.font.key
        typography.customFontPath?.let { this[Keys.customFontPath] = it } ?: remove(Keys.customFontPath)
        typography.customFontName?.let { this[Keys.customFontName] = it } ?: remove(Keys.customFontName)
        this[Keys.fontSize] = typography.fontSizeSp
        this[Keys.lineHeight] = typography.lineHeight
        this[Keys.letterSpacing] = typography.letterSpacing
        this[Keys.paragraphSpacing] = typography.paragraphSpacing
        this[Keys.margin] = typography.horizontalMarginDp
        this[Keys.maxLineWidth] = typography.maxLineWidthChars
        this[Keys.justify] = typography.justify
        this[Keys.indent] = typography.firstLineIndent
        this[Keys.hyphenation] = typography.hyphenation

        val focus = value.reader.focus
        this[Keys.bionic] = focus.bionicEnabled
        this[Keys.bionicIntensity] = focus.bionicIntensity
        this[Keys.bionicDim] = focus.bionicDimTail
        this[Keys.rsvpWpm] = focus.rsvpWpm
        this[Keys.rsvpChunk] = focus.rsvpChunkSize
        this[Keys.rsvpPivot] = focus.rsvpHighlightPivot
        this[Keys.rsvpPause] = focus.rsvpPauseOnPunctuation
        this[Keys.focusLine] = focus.focusLineHighlight

        this[Keys.pageStyle] = value.reader.pageStyle.name
        this[Keys.scrollMode] = value.reader.scrollMode.name
        this[Keys.continuousReading] = value.reader.continuousReading
        this[Keys.tapZonesPaging] = value.reader.tapZonesPaging
        this[Keys.keepScreenOn] = value.reader.keepScreenOn
        this[Keys.fullscreen] = value.reader.fullscreen
        this[Keys.volumeKeys] = value.reader.volumeKeysPaging
        this[Keys.progressBar] = value.reader.showProgressBar
        this[Keys.horizontalPaging] = value.reader.horizontalPaging
    }

    private inline fun <reified T : Enum<T>> enumOf(raw: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val designStyle = stringPreferencesKey("design_style")
        val pureBlack = booleanPreferencesKey("pure_black")
        val dynamicCover = booleanPreferencesKey("dynamic_cover")
        val systemDynamic = booleanPreferencesKey("system_dynamic")
        val globalAccent = intPreferencesKey("global_accent")
        val shelfLayout = stringPreferencesKey("shelf_layout")
        val shelfSort = stringPreferencesKey("shelf_sort")
        val shelfGrouping = stringPreferencesKey("shelf_grouping")
        val shelfAscending = booleanPreferencesKey("shelf_ascending")
        val isOnboardingCompleted = booleanPreferencesKey("is_onboarding_completed")
        val onboarding = booleanPreferencesKey("onboarding_completed")
        val userName = stringPreferencesKey("user_name")
        val userAvatarId = stringPreferencesKey("user_avatar_id")
        val customAvatarPath = stringPreferencesKey("custom_avatar_path")
        val userTitleKey = stringPreferencesKey("user_title_key")
        val userBio = stringPreferencesKey("user_bio")
        val languageTag = stringPreferencesKey("language_tag")
        val metadataAllowed = booleanPreferencesKey("metadata_network_allowed")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val shelfColumns = intPreferencesKey("shelf_columns")
        val shelf3dCrease = booleanPreferencesKey("shelf_3d_crease")
        val ttsDefaultSpeed = floatPreferencesKey("tts_default_speed")
        val ttsEnginePackage = stringPreferencesKey("tts_engine_package")

        val font = stringPreferencesKey("reading_font")
        val customFontPath = stringPreferencesKey("custom_font_path")
        val customFontName = stringPreferencesKey("custom_font_name")
        val fontSize = floatPreferencesKey("font_size")
        val lineHeight = floatPreferencesKey("line_height")
        val letterSpacing = floatPreferencesKey("letter_spacing")
        val paragraphSpacing = floatPreferencesKey("paragraph_spacing")
        val margin = floatPreferencesKey("horizontal_margin")
        val maxLineWidth = intPreferencesKey("max_line_width")
        val justify = booleanPreferencesKey("justify")
        val indent = booleanPreferencesKey("first_line_indent")
        val hyphenation = booleanPreferencesKey("hyphenation")

        val bionic = booleanPreferencesKey("bionic_enabled")
        val bionicIntensity = floatPreferencesKey("bionic_intensity")
        val bionicDim = booleanPreferencesKey("bionic_dim_tail")
        val rsvpWpm = intPreferencesKey("rsvp_wpm")
        val rsvpChunk = intPreferencesKey("rsvp_chunk")
        val rsvpPivot = booleanPreferencesKey("rsvp_pivot")
        val rsvpPause = booleanPreferencesKey("rsvp_pause")
        val focusLine = booleanPreferencesKey("focus_line")

        val pageStyle = stringPreferencesKey("page_style")
        val scrollMode = stringPreferencesKey("scroll_mode")
        val continuousReading = booleanPreferencesKey("continuous_reading")
        val tapZonesPaging = booleanPreferencesKey("tap_zones_paging")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val fullscreen = booleanPreferencesKey("fullscreen")
        val volumeKeys = booleanPreferencesKey("volume_keys_paging")
        val progressBar = booleanPreferencesKey("show_progress_bar")
        val horizontalPaging = booleanPreferencesKey("horizontal_paging")
    }
}
