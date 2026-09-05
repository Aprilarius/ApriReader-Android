package com.aprireader.app.data.stats

import com.aprireader.app.data.db.AchievementDao
import com.aprireader.app.data.db.ReadingSessionEntity
import com.aprireader.app.data.db.StatsDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Статистика чтения. Всё считается локально и никуда не отправляется —
 * это принципиально: время чтения книг говорит о человеке очень много.
 */
class StatsRepository(
    private val statsDao: StatsDao,
    private val achievementDao: AchievementDao,
) {

    val totalMillis: Flow<Long> = statsDao.observeTotalMillis()
    val totalChars: Flow<Long> = statsDao.observeTotalChars()
    val readingDays: Flow<List<String>> = statsDao.observeReadingDays()
    val achievements = achievementDao.observeAll()

    fun todayMillis(): Flow<Long> = statsDao.observeMillisForDate(today())

    /** Сессии за последние [days] дней — для графика активности. */
    fun sessionsSince(days: Long): Flow<List<ReadingSessionEntity>> =
        statsDao.observeSince(System.currentTimeMillis() - days * DAY_MS)

    /** Текущий стрик: сколько дней подряд человек читал, включая сегодня или вчера. */
    val currentStreak: Flow<Int> = readingDays.map { days ->
        val parsed = days.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        computeStreak(parsed, LocalDate.now())
    }

    suspend fun recordSession(bookId: String, startedAt: Long, endedAt: Long, charsRead: Int) =
        withContext(Dispatchers.IO) {
            val duration = endedAt - startedAt
            // Слишком короткие интервалы — это не чтение, а случайное открытие.
            if (duration < MIN_SESSION_MS) return@withContext
            statsDao.insert(
                ReadingSessionEntity(
                    bookId = bookId,
                    startedAt = startedAt,
                    endedAt = endedAt,
                    durationMs = duration,
                    charsRead = charsRead,
                    localDate = dateOf(startedAt),
                )
            )
        }

    suspend fun millisForBook(bookId: String): Long = statsDao.totalMillisForBook(bookId)

    suspend fun markAchievementSeen(key: String) = achievementDao.markSeen(key)

    private fun today(): String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    private fun dateOf(timestamp: Long): String =
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)

    private companion object {
        const val MIN_SESSION_MS = 15_000L
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}

/**
 * Длина текущей серии дней чтения.
 *
 * Вынесено из потока отдельной функцией, потому что здесь легко ошибиться:
 * серия не должна рваться, если сегодня человек ещё не читал (вчерашний день
 * ещё «держит» её), но должна рваться при пропуске полного дня.
 *
 * Сортировка сделана `sortedDescending()`, а не `toSortedSet().reversed()`:
 * второй вариант уходит в `java.util.SequencedCollection#reversed` из API 35 и
 * падает на всех более ранних версиях Android.
 */
fun computeStreak(days: List<LocalDate>, today: LocalDate): Int {
    if (days.isEmpty()) return 0
    val sorted = days.distinct().sortedDescending()
    var expected = when {
        sorted.contains(today) -> today
        sorted.contains(today.minusDays(1)) -> today.minusDays(1)
        else -> return 0
    }
    var streak = 0
    for (date in sorted) {
        when {
            date == expected -> {
                streak++
                expected = expected.minusDays(1)
            }
            date.isAfter(expected) -> Unit // будущие или уже посчитанные даты пропускаем
            else -> break
        }
    }
    return streak
}
