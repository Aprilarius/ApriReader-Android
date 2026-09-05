package com.aprireader.app.ui.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aprireader.app.AppContainer
import com.aprireader.app.BuildConfig
import com.aprireader.app.data.library.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AboutTab {
    INFO,
    TOS,
    PRIVACY,
    LICENSES,
}

data class AboutUiState(
    val version: String = BuildConfig.VERSION_NAME,
    val selectedTab: AboutTab = AboutTab.INFO,
    val cacheCleared: Boolean = false,
)

class AboutViewModel(
    private val library: LibraryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AboutUiState())
    val state: StateFlow<AboutUiState> = _state.asStateFlow()

    fun selectTab(tab: AboutTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    fun clearCache() = viewModelScope.launch {
        library.clearBookCache()
        _state.update { it.copy(cacheCleared = true) }
    }

    fun openOrCreateWelcomeGuide(onReady: (String) -> Unit) = viewModelScope.launch {
        var bookId = library.findWelcomeGuideBookId()
        if (bookId == null) {
            library.installWelcomeGuide(force = true)
            bookId = library.findWelcomeGuideBookId()
        }
        if (bookId != null) {
            onReady(bookId)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AboutViewModel(library = container.library)
            }
        }
    }
}
