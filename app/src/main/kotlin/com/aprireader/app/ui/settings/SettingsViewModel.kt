package com.aprireader.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aprireader.app.AppContainer
import com.aprireader.app.BuildConfig
import com.aprireader.app.data.fonts.CustomFont
import com.aprireader.app.data.fonts.CustomFontRepository
import com.aprireader.app.data.library.LibraryRepository
import com.aprireader.app.data.prefs.AppSettings
import com.aprireader.app.data.prefs.SettingsRepository
import com.aprireader.app.data.prefs.ThemeMode
import com.aprireader.app.data.profile.AvatarStore
import com.aprireader.app.data.tts.TtsController
import com.aprireader.app.data.tts.TtsEngineInfo
import com.aprireader.app.domain.LibrarySource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val sources: List<LibrarySource> = emptyList(),
    val ttsEngines: List<TtsEngineInfo> = emptyList(),
    val versionName: String = BuildConfig.VERSION_NAME,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val library: LibraryRepository,
    private val tts: TtsController,
    private val customFontsRepo: CustomFontRepository,
    private val avatarStore: AvatarStore,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        library.sources,
        tts.state,
    ) { settings, sources, ttsState ->
        SettingsUiState(
            settings = settings,
            sources = sources,
            ttsEngines = ttsState.availableEngines,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect {
                tts.setPreferredEngine(it.ttsEnginePackage)
            }
        }
    }

    val customFonts: StateFlow<List<CustomFont>> = customFontsRepo.fonts

    fun setReadingFont(font: com.aprireader.app.data.prefs.ReadingFont, customPath: String? = null, customName: String? = null) {
        update {
            it.copy(
                reader = it.reader.copy(
                    typography = it.reader.typography.copy(
                        font = font,
                        customFontPath = customPath,
                        customFontName = customName,
                    )
                )
            )
        }
    }

    fun importCustomFont(uri: Uri, contentResolver: android.content.ContentResolver, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = customFontsRepo.importFont(uri, contentResolver)
            result.onSuccess { font ->
                setReadingFont(com.aprireader.app.data.prefs.ReadingFont.CUSTOM, font.filePath, font.name)
                onResult(true, font.name)
            }.onFailure {
                onResult(false, it.message)
            }
        }
    }

    fun deleteCustomFont(fontId: String) {
        viewModelScope.launch {
            customFontsRepo.deleteFont(fontId)
        }
    }

    fun setUserAvatarId(avatarId: String) = update { it.copy(userAvatarId = avatarId) }

    fun setUserTitleKey(titleKey: String) = update { it.copy(userTitleKey = titleKey) }

    fun setUserBio(bio: String) = update { it.copy(userBio = bio.trim()) }

    fun saveCustomAvatar(uri: Uri, contentResolver: android.content.ContentResolver) {
        viewModelScope.launch {
            val savedPath = runCatching {
                avatarStore.saveCustomAvatar(uri, contentResolver)
            }.getOrNull()
            if (savedPath != null) {
                update { it.copy(userAvatarId = "custom", customAvatarPath = savedPath) }
            }
        }
    }

    fun clearCustomAvatar() {
        viewModelScope.launch {
            avatarStore.clearCustomAvatar()
            update { it.copy(userAvatarId = "m1_scholar", customAvatarPath = null) }
        }
    }

    fun setThemeMode(mode: ThemeMode) = update {
        it.copy(
            themeMode = mode,
            pureBlackDark = when (mode) {
                ThemeMode.AMOLED -> true
                ThemeMode.DARK -> false
                else -> it.pureBlackDark
            },
        )
    }

    fun setDesignStyle(style: com.aprireader.app.data.prefs.DesignStyle) = update { it.copy(designStyle = style) }

    fun setReadingScrollMode(mode: com.aprireader.app.data.prefs.ReadingScrollMode) =
        update { it.copy(reader = it.reader.copy(scrollMode = mode, horizontalPaging = mode == com.aprireader.app.data.prefs.ReadingScrollMode.PAGED_HORIZONTAL)) }

    fun setContinuousReading(value: Boolean) =
        update { it.copy(reader = it.reader.copy(continuousReading = value)) }

    fun setTapZonesPaging(value: Boolean) =
        update { it.copy(reader = it.reader.copy(tapZonesPaging = value)) }

    fun setPureBlack(value: Boolean) = update {
        val newMode = when {
            value && it.themeMode == ThemeMode.DARK -> ThemeMode.AMOLED
            !value && it.themeMode == ThemeMode.AMOLED -> ThemeMode.DARK
            else -> it.themeMode
        }
        it.copy(pureBlackDark = value, themeMode = newMode)
    }

    fun setDynamicCoverTheming(value: Boolean) = update { it.copy(dynamicCoverTheming = value) }

    fun setSystemDynamicColor(value: Boolean) = update { it.copy(systemDynamicColor = value) }

    fun setGlobalAccent(color: Int?) = update { it.copy(globalAccent = color) }

    fun setReduceMotion(value: Boolean) = update { it.copy(reduceMotion = value) }

    fun setMetadataAllowed(value: Boolean) = update { it.copy(metadataNetworkAllowed = value) }

    fun addFolder(uri: Uri) = viewModelScope.launch { library.addFolder(uri) }

    fun removeSource(source: LibrarySource, deleteBooks: Boolean) = viewModelScope.launch {
        library.removeSource(source.treeUri, deleteBooks)
    }

    fun setLanguage(tag: String) = update { it.copy(languageTag = tag) }

    fun setUserName(name: String) = update { it.copy(userName = name.trim()) }

    fun setShelfColumns(columns: Int) = update { it.copy(shelfColumns = columns.coerceIn(2, 5)) }

    fun setShelf3dCrease(value: Boolean) = update { it.copy(shelf3dCrease = value) }

    fun setTtsDefaultSpeed(speed: Float) = update { it.copy(ttsDefaultSpeed = speed) }

    fun setTtsEngine(enginePackage: String?) {
        tts.setPreferredEngine(enginePackage)
        update { it.copy(ttsEnginePackage = enginePackage) }
    }

    fun openSystemTtsSettings(context: Context) {
        tts.openSystemTtsSettings(context)
    }

    fun setKeepScreenOn(value: Boolean) = update { it.copy(reader = it.reader.copy(keepScreenOn = value)) }

    fun setFullscreen(value: Boolean) = update { it.copy(reader = it.reader.copy(fullscreen = value)) }

    fun setVolumeKeysPaging(value: Boolean) = update { it.copy(reader = it.reader.copy(volumeKeysPaging = value)) }

    fun setShowProgressBar(value: Boolean) = update { it.copy(reader = it.reader.copy(showProgressBar = value)) }

    fun clearCache() = viewModelScope.launch { library.clearBookCache() }

    private fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        settingsRepository.update(transform)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    settingsRepository = container.settings,
                    library = container.library,
                    tts = container.tts,
                    customFontsRepo = container.customFonts,
                    avatarStore = container.avatarStore,
                )
            }
        }
    }
}
