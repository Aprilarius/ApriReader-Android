package com.aprireader.app

import com.aprireader.app.data.prefs.ShelfSort
import com.aprireader.app.domain.ShelfSortable
import com.aprireader.app.ui.library.naturallyAscending
import com.aprireader.app.ui.library.shelfComparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Регрессия на порядок книг на полке.
 *
 * Баг, который здесь ловится: флаг «по возрастанию» трактовался по-разному для
 * разных сортировок, и при значении по умолчанию книги по названию выстраивались
 * от Я к А — противоположно тому, что показывал переключатель.
 */
class ShelfSortingTest {

    private data class Entry(
        override val title: String,
        override val authorLine: String = "",
        override val addedAt: Long = 0,
        override val lastOpenedAt: Long? = null,
        override val progress: Float = 0f,
    ) : ShelfSortable

    private val books = listOf(
        Entry("Война и мир", authorLine = "Толстой"),
        Entry("Анна Каренина", authorLine = "Толстой"),
        Entry("Мастер и Маргарита", authorLine = "Булгаков"),
    )

    @Test
    fun `title ascending goes from A to Z`() {
        val sorted = books.sortedWith(shelfComparator(ShelfSort.TITLE, ascending = true))
        assertEquals(
            listOf("Анна Каренина", "Война и мир", "Мастер и Маргарита"),
            sorted.map { it.title },
        )
    }

    @Test
    fun `title descending is exactly the reverse`() {
        val sorted = books.sortedWith(shelfComparator(ShelfSort.TITLE, ascending = false))
        assertEquals(
            listOf("Мастер и Маргарита", "Война и мир", "Анна Каренина"),
            sorted.map { it.title },
        )
    }

    @Test
    fun `default direction for a chosen sort matches its label`() {
        // Выбрали «название» — ждём А→Я; выбрали «недавние» — ждём свежие сверху.
        assertTrue(ShelfSort.TITLE.naturallyAscending())
        assertTrue(ShelfSort.AUTHOR.naturallyAscending())
        assertFalse(ShelfSort.RECENT.naturallyAscending())
        assertFalse(ShelfSort.ADDED.naturallyAscending())
        assertFalse(ShelfSort.PROGRESS.naturallyAscending())

        val byTitle = books.sortedWith(
            shelfComparator<Entry>(ShelfSort.TITLE, ShelfSort.TITLE.naturallyAscending()),
        )
        assertEquals("Анна Каренина", byTitle.first().title)
    }

    @Test
    fun `recent sort puts freshly opened books first`() {
        val list = listOf(
            Entry("Старая", addedAt = 1, lastOpenedAt = 100),
            Entry("Новая", addedAt = 2, lastOpenedAt = 300),
            Entry("Не открывалась", addedAt = 200),
        )
        val sorted = list.sortedWith(
            shelfComparator<Entry>(ShelfSort.RECENT, ShelfSort.RECENT.naturallyAscending()),
        )
        assertEquals(listOf("Новая", "Не открывалась", "Старая"), sorted.map { it.title })
    }

    @Test
    fun `books without an author go last in both directions`() {
        val list = listOf(
            Entry("Без автора"),
            Entry("С автором", authorLine = "Автор"),
        )
        assertEquals(
            "С автором",
            list.sortedWith(shelfComparator<Entry>(ShelfSort.AUTHOR, true)).first().title,
        )
        assertEquals(
            "Без автора",
            list.sortedWith(shelfComparator<Entry>(ShelfSort.AUTHOR, false)).first().title,
        )
    }

    @Test
    fun `progress sort is highest first by default`() {
        val list = listOf(
            Entry("Начата", progress = 0.1f),
            Entry("Почти дочитана", progress = 0.9f),
            Entry("Не начата", progress = 0f),
        )
        val sorted = list.sortedWith(
            shelfComparator<Entry>(ShelfSort.PROGRESS, ShelfSort.PROGRESS.naturallyAscending()),
        )
        assertEquals(listOf("Почти дочитана", "Начата", "Не начата"), sorted.map { it.title })
    }
}
