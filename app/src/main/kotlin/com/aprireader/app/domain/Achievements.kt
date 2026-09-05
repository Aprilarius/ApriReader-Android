package com.aprireader.app.domain

import com.aprireader.app.R

/**
 * Достижения.
 *
 * Правило подбора: достижение отмечает то, что человек действительно сделал
 * (прочитал, вернулся, дочитал), и никогда не подталкивает к «фарму» ради
 * значка. Никаких ежедневных заданий и обратного отсчёта — это ридер, а не
 * игра на удержание.
 */
data class Achievement(
    val key: String,
    val titleRes: Int,
    val descriptionRes: Int,
    val group: Group,
    /** Порог, по которому считается прогресс. */
    val threshold: Int,
) {
    enum class Group { TIME, STREAK, BOOKS, HABITS }
}

data class AchievementProgress(
    val achievement: Achievement,
    val current: Int,
    val unlockedAt: Long?,
) {
    val unlocked: Boolean get() = unlockedAt != null
    val fraction: Float
        get() = if (achievement.threshold <= 0) 1f else (current.toFloat() / achievement.threshold).coerceIn(0f, 1f)
}

object Achievements {

    val all: List<Achievement> = listOf(
        Achievement("first_page", R.string.ach_first_page_title, R.string.ach_first_page_desc, Achievement.Group.HABITS, 1),
        Achievement("hours_1", R.string.ach_hours_1_title, R.string.ach_hours_1_desc, Achievement.Group.TIME, 60),
        Achievement("hours_10", R.string.ach_hours_10_title, R.string.ach_hours_10_desc, Achievement.Group.TIME, 600),
        Achievement("hours_50", R.string.ach_hours_50_title, R.string.ach_hours_50_desc, Achievement.Group.TIME, 3_000),
        Achievement("hours_100", R.string.ach_hours_100_title, R.string.ach_hours_100_desc, Achievement.Group.TIME, 6_000),
        Achievement("streak_3", R.string.ach_streak_3_title, R.string.ach_streak_3_desc, Achievement.Group.STREAK, 3),
        Achievement("streak_7", R.string.ach_streak_7_title, R.string.ach_streak_7_desc, Achievement.Group.STREAK, 7),
        Achievement("streak_30", R.string.ach_streak_30_title, R.string.ach_streak_30_desc, Achievement.Group.STREAK, 30),
        Achievement("finished_1", R.string.ach_finished_1_title, R.string.ach_finished_1_desc, Achievement.Group.BOOKS, 1),
        Achievement("finished_5", R.string.ach_finished_5_title, R.string.ach_finished_5_desc, Achievement.Group.BOOKS, 5),
        Achievement("finished_25", R.string.ach_finished_25_title, R.string.ach_finished_25_desc, Achievement.Group.BOOKS, 25),
        Achievement("library_50", R.string.ach_library_50_title, R.string.ach_library_50_desc, Achievement.Group.BOOKS, 50),
        Achievement("marathon", R.string.ach_marathon_title, R.string.ach_marathon_desc, Achievement.Group.HABITS, 60),
        Achievement("night_owl", R.string.ach_night_owl_title, R.string.ach_night_owl_desc, Achievement.Group.HABITS, 1),
        Achievement("early_bird", R.string.ach_early_bird_title, R.string.ach_early_bird_desc, Achievement.Group.HABITS, 1),
        Achievement("polyglot_formats", R.string.ach_polyglot_formats_title, R.string.ach_polyglot_formats_desc, Achievement.Group.HABITS, 3),
    )

    fun byKey(key: String): Achievement? = all.firstOrNull { it.key == key }

    /** Текущие значения метрик, по которым считаются достижения. */
    data class Metrics(
        val totalMinutes: Int,
        val streakDays: Int,
        val finishedBooks: Int,
        val libraryBooks: Int,
        val longestSessionMinutes: Int,
        val readAfterMidnight: Boolean,
        val readBeforeSix: Boolean,
        val distinctFormats: Int,
        val openedAnyBook: Boolean,
    )

    fun currentValue(achievement: Achievement, metrics: Metrics): Int = when (achievement.key) {
        "first_page" -> if (metrics.openedAnyBook) 1 else 0
        "hours_1", "hours_10", "hours_50", "hours_100" -> metrics.totalMinutes
        "streak_3", "streak_7", "streak_30" -> metrics.streakDays
        "finished_1", "finished_5", "finished_25" -> metrics.finishedBooks
        "library_50" -> metrics.libraryBooks
        "marathon" -> metrics.longestSessionMinutes
        "night_owl" -> if (metrics.readAfterMidnight) 1 else 0
        "early_bird" -> if (metrics.readBeforeSix) 1 else 0
        "polyglot_formats" -> metrics.distinctFormats
        else -> 0
    }

    fun unlockedKeys(metrics: Metrics): Set<String> =
        all.filter { currentValue(it, metrics) >= it.threshold }.map { it.key }.toSet()
}
