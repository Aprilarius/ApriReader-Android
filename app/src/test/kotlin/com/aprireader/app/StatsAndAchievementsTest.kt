package com.aprireader.app

import com.aprireader.app.data.stats.computeStreak
import com.aprireader.app.domain.Achievements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StreakTest {

    private val today = LocalDate.of(2026, 3, 15)

    @Test
    fun `no days means no streak`() {
        assertEquals(0, computeStreak(emptyList(), today))
    }

    @Test
    fun `today plus two previous days`() {
        val days = listOf(today, today.minusDays(1), today.minusDays(2))
        assertEquals(3, computeStreak(days, today))
    }

    @Test
    fun `streak survives a day that has not started yet`() {
        // Человек читал вчера и позавчера, а сегодня ещё не открывал книгу —
        // серия должна держаться, а не обнуляться до полуночи.
        val days = listOf(today.minusDays(1), today.minusDays(2))
        assertEquals(2, computeStreak(days, today))
    }

    @Test
    fun `gap of a full day breaks the streak`() {
        val days = listOf(today, today.minusDays(1), today.minusDays(3), today.minusDays(4))
        assertEquals(2, computeStreak(days, today))
    }

    @Test
    fun `stale history gives zero`() {
        val days = listOf(today.minusDays(5), today.minusDays(6))
        assertEquals(0, computeStreak(days, today))
    }

    @Test
    fun `duplicates do not inflate the streak`() {
        val days = listOf(today, today, today.minusDays(1), today.minusDays(1))
        assertEquals(2, computeStreak(days, today))
    }
}

class AchievementsTest {

    private val empty = Achievements.Metrics(
        totalMinutes = 0,
        streakDays = 0,
        finishedBooks = 0,
        libraryBooks = 0,
        longestSessionMinutes = 0,
        readAfterMidnight = false,
        readBeforeSix = false,
        distinctFormats = 0,
        openedAnyBook = false,
    )

    @Test
    fun `fresh install unlocks nothing`() {
        assertTrue(Achievements.unlockedKeys(empty).isEmpty())
    }

    @Test
    fun `thresholds unlock cumulatively`() {
        val metrics = empty.copy(totalMinutes = 700, openedAnyBook = true)
        val unlocked = Achievements.unlockedKeys(metrics)
        assertTrue("hours_1" in unlocked)
        assertTrue("hours_10" in unlocked)
        assertFalse("hours_50" in unlocked)
        assertTrue("first_page" in unlocked)
    }

    @Test
    fun `progress value never exceeds what user did`() {
        val metrics = empty.copy(streakDays = 5)
        val week = Achievements.byKey("streak_7")!!
        assertEquals(5, Achievements.currentValue(week, metrics))
        assertFalse("streak_7" in Achievements.unlockedKeys(metrics))
        assertTrue("streak_3" in Achievements.unlockedKeys(metrics))
    }

    @Test
    fun `every achievement has a matching metric`() {
        // Защита от опечатки в ключе: достижение без метрики навсегда останется
        // недостижимым и будет выглядеть как обман.
        val full = Achievements.Metrics(
            totalMinutes = 100_000,
            streakDays = 1_000,
            finishedBooks = 1_000,
            libraryBooks = 1_000,
            longestSessionMinutes = 1_000,
            readAfterMidnight = true,
            readBeforeSix = true,
            distinctFormats = 10,
            openedAnyBook = true,
        )
        assertEquals(Achievements.all.size, Achievements.unlockedKeys(full).size)
    }
}
