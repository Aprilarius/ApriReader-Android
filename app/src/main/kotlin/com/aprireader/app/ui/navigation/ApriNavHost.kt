package com.aprireader.app.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aprireader.app.AppContainer
import com.aprireader.app.data.prefs.AppSettings
import com.aprireader.app.ui.about.AboutScreen
import com.aprireader.app.ui.about.AboutViewModel
import com.aprireader.app.ui.audio.AudiobooksScreen
import com.aprireader.app.ui.audio.AudiobooksViewModel
import com.aprireader.app.ui.common.LocalAnimatedVisibilityScope
import com.aprireader.app.ui.common.LocalSharedTransitionScope
import com.aprireader.app.ui.details.BookDetailsScreen
import com.aprireader.app.ui.details.BookDetailsViewModel
import com.aprireader.app.ui.library.LibraryScreen
import com.aprireader.app.ui.library.LibraryViewModel
import com.aprireader.app.ui.onboarding.OnboardingScreen
import com.aprireader.app.ui.reader.ReaderScreen
import com.aprireader.app.ui.reader.ReaderViewModel
import com.aprireader.app.ui.settings.SettingsScreen
import com.aprireader.app.ui.settings.SettingsViewModel
import com.aprireader.app.ui.stats.StatsScreen
import com.aprireader.app.ui.stats.StatsViewModel
import kotlinx.coroutines.launch

object Routes {
    const val ONBOARDING = "onboarding"
    const val LIBRARY = "library"
    const val AUDIOBOOKS = "audiobooks"
    const val READER = "reader/{bookId}"
    const val BOOK_DETAILS = "book/{bookId}"
    const val SETTINGS = "settings"
    const val STATS = "stats"
    const val ABOUT = "about"

    fun reader(bookId: String) = "reader/$bookId"
    fun bookDetails(bookId: String) = "book/$bookId"
}

/**
 * Навигация приложения с поддержкой боковой панели (Navigation Drawer)
 * и стабильными переходами без мерцания или зависания в белом экране при смене тем.
 */
@Composable
fun ApriNavHost(
    container: AppContainer,
    startDestination: String,
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: startDestination

    ApriDrawer(
        drawerState = drawerState,
        currentRoute = currentRoute,
        settings = settings,
        onNavigate = { targetRoute ->
            coroutineScope.launch { drawerState.close() }
            if (currentRoute != targetRoute) {
                if (targetRoute == Routes.LIBRARY) {
                    val popped = navController.popBackStack(Routes.LIBRARY, inclusive = false)
                    if (!popped) {
                        navController.navigate(Routes.LIBRARY) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                } else {
                    navController.navigate(targetRoute) {
                        popUpTo(Routes.LIBRARY) {
                            saveState = false
                        }
                        launchSingleTop = true
                    }
                }
            }
        },
    ) {
        SharedTransitionLayout(modifier = modifier) {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    enterTransition = {
                        if (initialState.destination.route != targetState.destination.route) {
                            slideInHorizontally(tween(250)) { it / 12 } + fadeIn(tween(200))
                        } else {
                            EnterTransition.None
                        }
                    },
                    exitTransition = {
                        if (initialState.destination.route != targetState.destination.route) {
                            fadeOut(tween(140))
                        } else {
                            ExitTransition.None
                        }
                    },
                    popEnterTransition = {
                        if (initialState.destination.route != targetState.destination.route) {
                            fadeIn(tween(200))
                        } else {
                            EnterTransition.None
                        }
                    },
                    popExitTransition = {
                        if (initialState.destination.route != targetState.destination.route) {
                            slideOutHorizontally(tween(250)) { it / 12 } + fadeOut(tween(180))
                        } else {
                            ExitTransition.None
                        }
                    },
                ) {
                    composable(Routes.ONBOARDING) {
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                            OnboardingScreen(
                                container = container,
                                onFinished = {
                                    navController.navigate(Routes.LIBRARY) {
                                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }
                    }

                    composable(Routes.LIBRARY) {
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                            val viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container))
                            LibraryScreen(
                                viewModel = viewModel,
                                onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                                onOpenBook = { book ->
                                    if (book.format.isAudio) {
                                        container.audioPlayer.loadAndPlay(book)
                                        navController.navigate(Routes.AUDIOBOOKS)
                                    } else {
                                        navController.navigate(Routes.reader(book.id))
                                    }
                                },
                                onOpenBookDetails = { navController.navigate(Routes.bookDetails(it.id)) },
                                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                                onOpenStats = { navController.navigate(Routes.STATS) },
                            )
                        }
                    }

                    composable(Routes.AUDIOBOOKS) {
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                            val viewModel: AudiobooksViewModel = viewModel(
                                factory = AudiobooksViewModel.factory(container),
                            )
                            AudiobooksScreen(
                                viewModel = viewModel,
                                onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                                onOpenBook = { book ->
                                    if (book.format.isAudio) {
                                        container.audioPlayer.loadAndPlay(book)
                                    } else {
                                        navController.navigate(Routes.reader(book.id))
                                    }
                                },
                            )
                        }
                    }

                    composable(Routes.READER) { entry ->
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                            val bookId = entry.arguments?.getString("bookId").orEmpty()
                            val viewModel: ReaderViewModel = viewModel(
                                factory = ReaderViewModel.factory(container, bookId),
                            )
                            ReaderScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                        }
                    }

                    composable(Routes.BOOK_DETAILS) { entry ->
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                            val bookId = entry.arguments?.getString("bookId").orEmpty()
                            val viewModel: BookDetailsViewModel = viewModel(
                                factory = BookDetailsViewModel.factory(container, bookId),
                            )
                            BookDetailsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onRead = { book ->
                                    if (book.format.isAudio) {
                                        container.audioPlayer.loadAndPlay(book)
                                        navController.navigate(Routes.AUDIOBOOKS) {
                                            popUpTo(Routes.BOOK_DETAILS) { inclusive = true }
                                        }
                                    } else {
                                        navController.navigate(Routes.reader(book.id)) {
                                            popUpTo(Routes.BOOK_DETAILS) { inclusive = true }
                                        }
                                    }
                                },
                            )
                        }
                    }

                    composable(Routes.SETTINGS) {
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                            val viewModel: SettingsViewModel = viewModel(
                                factory = SettingsViewModel.factory(container),
                            )
                            SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                        }
                    }

                    composable(Routes.STATS) {
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                            val viewModel: StatsViewModel = viewModel(factory = StatsViewModel.factory(container))
                            StatsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                        }
                    }

                    composable(Routes.ABOUT) {
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                            val viewModel: AboutViewModel = viewModel(factory = AboutViewModel.factory(container))
                            AboutScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                                onOpenBook = { bookId -> navController.navigate(Routes.reader(bookId)) },
                            )
                        }
                    }
                }
            }
        }
    }
}
