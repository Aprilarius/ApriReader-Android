package com.aprireader.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.aprireader.app.data.prefs.AppSettings
import com.aprireader.app.data.prefs.LocaleStore
import com.aprireader.app.data.saf.BookKey
import com.aprireader.app.ui.common.LocalizedContent
import com.aprireader.app.ui.navigation.ApriNavHost
import com.aprireader.app.ui.navigation.Routes
import com.aprireader.app.ui.theme.ApriTheme
import com.aprireader.app.widget.CurrentBookWidget
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleStore.wrap(newBase))
    }

    private var volumeKeyHandler: ((forward: Boolean) -> Boolean)? = null

    fun setVolumeKeyHandler(handler: ((forward: Boolean) -> Boolean)?) {
        volumeKeyHandler = handler
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val handler = volumeKeyHandler
        val forward = keyCode.asPagingDirection()
        if (handler != null && forward != null) {
            handler(forward)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (volumeKeyHandler != null && keyCode.asPagingDirection() != null) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun Int.asPagingDirection(): Boolean? = when (this) {
        KeyEvent.KEYCODE_VOLUME_DOWN -> true
        KeyEvent.KEYCODE_VOLUME_UP -> false
        else -> null
    }

    private val pendingExternalBookId = mutableStateOf<String?>(null)
    private val pendingAudiobook = mutableStateOf<com.aprireader.app.domain.Book?>(null)
    private var initialSettings by mutableStateOf<AppSettings?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val widgetBookId = intent.getStringExtra(CurrentBookWidget.EXTRA_BOOK_ID)
        if (widgetBookId != null) {
            pendingExternalBookId.value = widgetBookId
            return
        }

        val uri: Uri? = intent.data ?: androidx.core.content.IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        if (uri != null) {
            lifecycleScope.launch {
                runCatching {
                    appContainer.storageAccess.persist(uri)
                    appContainer.library.addFiles(listOf(uri))
                    val id = BookKey.compute(this@MainActivity, uri, 0L, uri.lastPathSegment ?: "book")
                    val book = appContainer.library.getBook(id) ?: appContainer.library.mostRecentBook()
                    if (book != null) {
                        if (book.format.isAudio) {
                            appContainer.audioPlayer.loadAndPlay(book)
                            pendingAudiobook.value = book
                        } else {
                            pendingExternalBookId.value = book.id
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = appContainer
        handleIntent(intent)

        splash.setKeepOnScreenCondition { initialSettings == null }

        lifecycleScope.launch {
            val loaded = container.settings.getInitialSettings()
            initialSettings = loaded
            container.library.installWelcomeGuide()
        }

        setContent {
            val initial = initialSettings ?: return@setContent
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = initial)

            val navController = rememberNavController()
            val startDestination = remember {
                when {
                    !initial.onboardingCompleted -> Routes.ONBOARDING
                    pendingAudiobook.value != null -> Routes.AUDIOBOOKS
                    pendingExternalBookId.value != null -> Routes.reader(pendingExternalBookId.value!!)
                    else -> Routes.LIBRARY
                }
            }

            val externalBookId by pendingExternalBookId
            LaunchedEffect(externalBookId) {
                externalBookId?.let { id ->
                    if (settings.onboardingCompleted) {
                        navController.navigate(Routes.reader(id)) {
                            launchSingleTop = true
                        }
                    }
                }
            }

            val externalAudio by pendingAudiobook
            LaunchedEffect(externalAudio) {
                externalAudio?.let {
                    if (settings.onboardingCompleted) {
                        navController.navigate(Routes.AUDIOBOOKS) {
                            launchSingleTop = true
                        }
                    }
                }
            }

            LocalizedContent(languageTag = settings.languageTag) {
                ApriTheme(settings = settings) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        ApriNavHost(
                            container = container,
                            startDestination = startDestination,
                            navController = navController,
                        )
                    }
                }
            }
        }
    }
}
