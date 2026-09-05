package com.aprireader.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aprireader.app.AppContainer
import com.aprireader.app.data.db.AchievementDao
import com.aprireader.app.data.db.AchievementEntity
import com.aprireader.app.data.db.ReadingSessionEntity
import com.aprireader.app.data.library.LibraryRepository
import com.aprireader.app.data.prefs.AppSettings
import com.aprireader.app.data.prefs.SettingsRepository
import com.aprireader.app.data.stats.StatsRepository
import com.aprireader.app.domain.Achievement
import com.aprireader.app.domain.AchievementProgress
import com.aprireader.app.domain.Achievements
import com.aprireader.app.domain.Book
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** День активности для графика. */
data class DayActivity(val date: LocalDate, val minutes: Int)

data class StatsDataBundle(
    val sessions: List<ReadingSessionEntity>,
    val streak: Int,
    val books: List<Book>,
    val unlocked: List<AchievementEntity>,
    val todayMillis: Long,
)

data class StatsUiState(
    val totalMinutes: Int = 0,
    val todayMinutes: Int = 0,
    val streakDays: Int = 0,
    val finishedBooks: Int = 0,
    val libraryBooks: Int = 0,
    val averageMinutesPerDay: Int = 0,
    val week: List<DayActivity> = emptyList(),
    val achievements: List<AchievementProgress> = emptyList(),
    val topBooks: List<Book> = emptyList(),
    val userAvatarId: String = "m1_scholar",
    val customAvatarPath: String? = null,
    val userName: String = "",
    val userTitleKey: String = "book_keeper",
    val userBio: String = "",
) {
    val unlockedCount: Int get() = achievements.count { it.unlocked }
}

class StatsViewModel(
    private val stats: StatsRepository,
    private val library: LibraryRepository,
    private val achievementDao: AchievementDao,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val statsBundleFlow = combine(
        stats.sessionsSince(365),
        stats.currentStreak,
        library.books,
        achievementDao.observeAll(),
        stats.todayMillis(),
    ) { sessions, streak, books, unlocked, todayMillis ->
        StatsDataBundle(sessions, streak, books, unlocked, todayMillis)
    }

    val state: StateFlow<StatsUiState> = combine(
        statsBundleFlow,
        settingsRepository.settings,
    ) { bundle, settings ->
        val sessions = bundle.sessions
        val streak = bundle.streak
        val books = bundle.books
        val unlocked = bundle.unlocked
        val todayMillis = bundle.todayMillis

        val totalMinutes = (sessions.sumOf { it.durationMs } / 60_000).toInt()
        val metrics = buildMetrics(sessions, streak, books, totalMinutes)
        val unlockedMap = unlocked.associateBy { it.key }

        persistNewlyUnlocked(metrics, unlockedMap)

        StatsUiState(
            totalMinutes = totalMinutes,
            todayMinutes = (todayMillis / 60_000).toInt(),
            streakDays = streak,
            finishedBooks = metrics.finishedBooks,
            libraryBooks = books.size,
            averageMinutesPerDay = averagePerActiveDay(sessions),
            week = weekActivity(sessions),
            achievements = Achievements.all.map { achievement ->
                AchievementProgress(
                    achievement = achievement,
                    current = Achievements.currentValue(achievement, metrics),
                    unlockedAt = unlockedMap[achievement.key]?.unlockedAt,
                )
            }.sortedWith(compareByDescending<AchievementProgress> { it.unlocked }.thenByDescending { it.fraction }),
            topBooks = books.filter { it.lastOpenedAt != null }
                .sortedByDescending { it.progress }
                .take(5),
            userAvatarId = settings.userAvatarId,
            customAvatarPath = settings.customAvatarPath,
            userName = settings.userName,
            userTitleKey = settings.userTitleKey,
            userBio = settings.userBio,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    private fun buildMetrics(
        sessions: List<ReadingSessionEntity>,
        streak: Int,
        books: List<Book>,
        totalMinutes: Int,
    ): Achievements.Metrics {
        val zone = ZoneId.systemDefault()
        val hours = sessions.map { Instant.ofEpochMilli(it.startedAt).atZone(zone).hour }
        return Achievements.Metrics(
            totalMinutes = totalMinutes,
            streakDays = streak,
            finishedBooks = books.count { it.isFinished },
            libraryBooks = books.size,
            longestSessionMinutes = ((sessions.maxOfOrNull { it.durationMs } ?: 0L) / 60_000).toInt(),
            readAfterMidnight = hours.any { it in 0..3 },
            readBeforeSix = hours.any { it in 4..5 },
            distinctFormats = books.filter { it.lastOpenedAt != null }.map { it.format }.distinct().size,
            openedAnyBook = books.any { it.lastOpenedAt != null },
        )
    }

    private fun persistNewlyUnlocked(
        metrics: Achievements.Metrics,
        known: Map<String, AchievementEntity>,
    ) {
        val newlyUnlocked = Achievements.unlockedKeys(metrics) - known.keys
        if (newlyUnlocked.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            newlyUnlocked.forEach { key ->
                achievementDao.upsert(AchievementEntity(key = key, unlockedAt = now))
            }
        }
    }

    private fun averagePerActiveDay(sessions: List<ReadingSessionEntity>): Int {
        val byDay = sessions.groupBy { it.localDate }
        if (byDay.isEmpty()) return 0
        return (byDay.values.sumOf { day -> day.sumOf { it.durationMs } } / byDay.size / 60_000).toInt()
    }

    private fun weekActivity(sessions: List<ReadingSessionEntity>): List<DayActivity> {
        val today = LocalDate.now()
        val byDate = sessions.groupBy { runCatching { LocalDate.parse(it.localDate) }.getOrNull() }
        return (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val minutes = (byDate[date]?.sumOf { it.durationMs } ?: 0L) / 60_000
            DayActivity(date, minutes.toInt())
        }
    }

    fun groupsOf(progress: List<AchievementProgress>): Map<Achievement.Group, List<AchievementProgress>> =
        progress.groupBy { it.achievement.group }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                StatsViewModel(
                    stats = container.stats,
                    library = container.library,
                    achievementDao = container.database.achievementDao(),
                    settingsRepository = container.settings,
                )
            }
        }
    }
}
