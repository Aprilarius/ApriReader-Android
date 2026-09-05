package com.aprireader.app.ui.audio

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aprireader.app.AppContainer
import com.aprireader.app.data.audio.AudioPlayerState
import com.aprireader.app.data.audio.AudiobookPlayerController
import com.aprireader.app.data.library.LibraryRepository
import com.aprireader.app.data.prefs.AppSettings
import com.aprireader.app.data.prefs.SettingsRepository
import com.aprireader.app.data.saf.StorageAccessManager
import com.aprireader.app.domain.Book
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AudiobooksUiState(
    val audiobooks: List<Book> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val playerState: AudioPlayerState = AudioPlayerState(),
    val isFullPlayerOpen: Boolean = false,
)

class AudiobooksViewModel(
    private val library: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val audioPlayer: AudiobookPlayerController,
    private val storageAccess: StorageAccessManager,
) : ViewModel() {

    private val _isFullPlayerOpen = MutableStateFlow(false)

    val state: StateFlow<AudiobooksUiState> = combine(
        library.books,
        settingsRepository.settings,
        audioPlayer.state,
        _isFullPlayerOpen,
    ) { books, settings, playerState, isFullPlayerOpen ->
        val audiobooks = books.filter { it.format.isAudio }
        AudiobooksUiState(
            audiobooks = audiobooks,
            settings = settings,
            playerState = playerState,
            isFullPlayerOpen = isFullPlayerOpen,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AudiobooksUiState(),
    )

    fun playAudiobook(book: Book) {
        audioPlayer.loadAndPlay(book)
    }

    fun togglePlayPause() {
        audioPlayer.togglePlayPause()
    }

    fun play() {
        audioPlayer.play()
    }

    fun pause() {
        audioPlayer.pause()
    }

    fun seekTo(positionMs: Long) {
        audioPlayer.seekTo(positionMs)
    }

    fun seekBy(deltaMs: Long) {
        audioPlayer.seekBy(deltaMs)
    }

    fun setSpeed(speed: Float) {
        audioPlayer.setSpeed(speed)
        viewModelScope.launch {
            settingsRepository.update { it.copy(ttsDefaultSpeed = speed) }
        }
    }

    fun setSleepTimer(minutes: Int?) {
        audioPlayer.setSleepTimer(minutes)
    }

    fun openFullPlayer() {
        _isFullPlayerOpen.value = true
    }

    fun closeFullPlayer() {
        _isFullPlayerOpen.value = false
    }

    fun addFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            for (uri in uris) {
                storageAccess.persist(uri)
            }
            library.addFiles(uris)
        }
    }

    fun addFolder(treeUri: Uri) {
        viewModelScope.launch {
            library.addFolder(treeUri)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AudiobooksViewModel(
                    library = container.library,
                    settingsRepository = container.settings,
                    audioPlayer = container.audioPlayer,
                    storageAccess = container.storageAccess,
                )
            }
        }
    }
}
