package com.aprireader.app

import android.content.Context
import com.aprireader.app.data.db.ApriDatabase
import com.aprireader.app.data.library.CoverStore
import com.aprireader.app.data.library.LibraryRepository
import com.aprireader.app.data.fonts.CustomFontRepository
import com.aprireader.app.data.marks.MarksRepository
import com.aprireader.app.data.metadata.MetadataRepository
import com.aprireader.app.data.prefs.SettingsRepository
import com.aprireader.app.data.reader.BookSessionFactory
import com.aprireader.app.data.saf.LibraryScanner
import com.aprireader.app.data.saf.StorageAccessManager
import com.aprireader.app.data.stats.StatsRepository
import com.aprireader.app.data.tts.TtsController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Ручной граф зависимостей.
 *
 * Здесь нет Hilt намеренно: граф неглубокий и почти статический, а отсутствие
 * второго процессора аннотаций заметно ускоряет сборку. Всё создаётся лениво,
 * поэтому старт приложения не платит за то, что пользователь ещё не открыл.
 */
class AppContainer(private val context: Context) {

    val database: ApriDatabase by lazy { ApriDatabase.create(context) }

    val settings: SettingsRepository by lazy { SettingsRepository(context) }

    val customFonts: CustomFontRepository by lazy { CustomFontRepository(context) }

    val avatarStore: com.aprireader.app.data.profile.AvatarStore by lazy { com.aprireader.app.data.profile.AvatarStore(context) }

    val storageAccess: StorageAccessManager by lazy { StorageAccessManager(context) }

    private val scanner: LibraryScanner by lazy { LibraryScanner(context) }

    private val coverStore: CoverStore by lazy { CoverStore(context) }

    val library: LibraryRepository by lazy {
        LibraryRepository(
            context = context,
            bookDao = database.bookDao(),
            sourceDao = database.sourceDao(),
            coverStore = coverStore,
            storageAccess = storageAccess,
            scanner = scanner,
        )
    }

    val sessionFactory: BookSessionFactory by lazy { BookSessionFactory(context, library) }

    val stats: StatsRepository by lazy {
        StatsRepository(database.statsDao(), database.achievementDao())
    }

    val marks: MarksRepository by lazy { MarksRepository(database.markDao()) }

    /**
     * Сетевой компонент создаётся лениво: пока пользователь не нажал «найти
     * данные книги», HTTP-клиента в процессе не существует вовсе.
     */
    val metadata: MetadataRepository by lazy { MetadataRepository() }

    /** Движок озвучивания создаётся при первом обращении к нему из экрана чтения. */
    val tts: TtsController by lazy { TtsController(context) }

    /** Контроллер воспроизведения аудиокниг. */
    val audioPlayer: com.aprireader.app.data.audio.AudiobookPlayerController by lazy {
        com.aprireader.app.data.audio.AudiobookPlayerController(context, library, appScope)
    }

    /**
     * Область корутин уровня приложения. Нужна для операций, которые обязаны
     * завершиться, даже если экран уже закрыт: сохранение позиции чтения и
     * запись сессии в статистику.
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

val Context.appContainer: AppContainer
    get() = (applicationContext as ApriApplication).container
