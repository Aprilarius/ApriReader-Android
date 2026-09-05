package com.aprireader.app

import com.aprireader.app.data.metadata.MetadataRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Регрессия на подтяжку данных книги.
 *
 * Функция выглядела неработающей по двум причинам, и обе воспроизводятся здесь:
 * в каталог уходило имя файла со всем мусором, и разбор ответа не проверялся
 * ничем, кроме запуска на устройстве.
 */
class MetadataQueryTest {

    @Test
    fun `file name noise is stripped from the query`() {
        assertEquals(
            "voyna i mir tolstoy",
            MetadataRepository.cleanQuery("voyna_i_mir_tolstoy_1869_(z-lib.org).fb2"),
        )
        assertEquals("Мастер и Маргарита", MetadataRepository.cleanQuery("Мастер и Маргарита.epub"))
        assertEquals("Dune", MetadataRepository.cleanQuery("Dune [litres] .mobi"))
        assertEquals("Тихий Дон", MetadataRepository.cleanQuery("Тихий_Дон__flibusta.fb2"))
    }

    @Test
    fun `short meaningful numbers survive cleaning`() {
        // «1984» — это название, а не мусор; вычищаются только длинные числа.
        assertEquals("1984", MetadataRepository.cleanQuery("1984.epub"))
        assertEquals("Catch 22", MetadataRepository.cleanQuery("Catch_22_(epub).epub"))
    }

    @Test
    fun `cleaning never returns leading or trailing spaces`() {
        val samples = listOf("  book  ", "book_.pdf", "(rus) book (2019)", "___")
        for (sample in samples) {
            val cleaned = MetadataRepository.cleanQuery(sample)
            assertEquals(cleaned.trim(), cleaned)
        }
    }
}

class MetadataParsingTest {

    /** Сокращённый, но настоящий ответ Open Library. */
    private val response = """
        {
          "numFound": 3765,
          "docs": [
            {
              "author_name": ["Лев Толстой"],
              "cover_i": 12621906,
              "first_publish_year": 1864,
              "title": "War and Peace",
              "first_sentence": ["Well, Prince, so Genoa and Lucca are now just family estates."]
            },
            {
              "author_name": ["Leo Tolstoy", "Louise Maude"],
              "first_publish_year": 1869,
              "title": "Война и мир"
            },
            {
              "title": ""
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parses titles authors year and cover url`() {
        val candidates = MetadataRepository.parseSearch(response)

        // Запись с пустым названием отбрасывается — подставлять её некуда.
        assertEquals(2, candidates.size)

        val first = candidates[0]
        assertEquals("War and Peace", first.title)
        assertEquals(listOf("Лев Толстой"), first.authors)
        assertEquals(1864, first.year)
        assertEquals("https://covers.openlibrary.org/b/id/12621906-M.jpg", first.coverUrl)
        assertTrue(first.description!!.startsWith("Well, Prince"))

        val second = candidates[1]
        assertEquals(listOf("Leo Tolstoy", "Louise Maude"), second.authors)
        assertNull(second.coverUrl)
        assertNull(second.description)
    }

    @Test
    fun `broken payloads do not crash the search`() {
        assertTrue(MetadataRepository.parseSearch("").isEmpty())
        assertTrue(MetadataRepository.parseSearch("не json").isEmpty())
        assertTrue(MetadataRepository.parseSearch("""{"numFound":0,"docs":[]}""").isEmpty())
        assertTrue(MetadataRepository.parseSearch("""{"unexpected":true}""").isEmpty())
        assertTrue(MetadataRepository.parseGoogleBooks("").isEmpty())
        assertTrue(MetadataRepository.parseGoogleBooks("invalid json").isEmpty())
    }

    @Test
    fun `parses open library alternate covers and object descriptions`() {
        val json = """
            {
              "docs": [
                {
                  "title": "Преступление и наказание",
                  "author_name": ["Фёдор Достоевский"],
                  "cover_edition_key": "OL12345M",
                  "first_sentence": {"type": "/type/text", "value": "В начале июля..."},
                  "publisher": ["Эксмо"],
                  "first_publish_year": 1866
                },
                {
                  "title": "1984",
                  "author_name": ["George Orwell"],
                  "isbn": ["9780451524935"],
                  "description": "Dystopian masterpiece"
                }
              ]
            }
        """.trimIndent()
        val candidates = MetadataRepository.parseSearch(json)
        assertEquals(2, candidates.size)
        assertEquals("https://covers.openlibrary.org/b/olid/OL12345M-M.jpg", candidates[0].coverUrl)
        assertEquals("В начале июля...", candidates[0].description)
        assertEquals("Эксмо", candidates[0].publisher)
        assertEquals("https://covers.openlibrary.org/b/isbn/9780451524935-M.jpg", candidates[1].coverUrl)
        assertEquals("Dystopian masterpiece", candidates[1].description)
    }

    @Test
    fun `parses google books volume info`() {
        val json = """
            {
              "items": [
                {
                  "volumeInfo": {
                    "title": "Война и мир",
                    "authors": ["Лев Николаевич Толстой"],
                    "publishedDate": "1869-01-01",
                    "description": "Роман-эпопея Льва Толстого",
                    "publisher": "Русский вестник",
                    "imageLinks": {
                      "thumbnail": "http://books.google.com/books/content?id=xyz&printsec=frontcover&img=1"
                    }
                  }
                }
              ]
            }
        """.trimIndent()
        val candidates = MetadataRepository.parseGoogleBooks(json)
        assertEquals(1, candidates.size)
        val book = candidates[0]
        assertEquals("Война и мир", book.title)
        assertEquals(listOf("Лев Николаевич Толстой"), book.authors)
        assertEquals(1869, book.year)
        assertEquals("Роман-эпопея Льва Толстого", book.description)
        assertEquals("Русский вестник", book.publisher)
        assertEquals("https://books.google.com/books/content?id=xyz&printsec=frontcover&img=1", book.coverUrl)
        assertEquals("Google Books", book.source)
    }

    @Test
    fun `parses fantlab works info`() {
        val json = """
            [
              {
                "work_id": 12345,
                "work_name": "Пикник на обочине",
                "work_name_orig": "Roadside Picnic",
                "work_author": "Аркадий и Борис Стругацкие",
                "work_year": 1972,
                "work_description": "Фантастическая повесть братьев Стругацких о Зоне Посещения.",
                "work_type_name": "повесть"
              }
            ]
        """.trimIndent()
        val candidates = MetadataRepository.parseFantLab(json)
        assertEquals(1, candidates.size)
        val book = candidates[0]
        assertEquals("Пикник на обочине", book.title)
        assertEquals(listOf("Аркадий и Борис Стругацкие"), book.authors)
        assertEquals(1972, book.year)
        assertEquals("https://fantlab.ru/images/works/12345_1", book.coverUrl)
        assertEquals("FantLab", book.source)
        assertTrue(book.description!!.contains("Стругацких"))
    }

    @Test
    fun `parses wikipedia search results`() {
        val json = """
            {
              "query": {
                "pages": {
                  "123": {
                    "title": "1984 (роман)",
                    "extract": "«1984» — роман-антиутопия Джорджа Оруэлла, изданный в 1949 году.",
                    "thumbnail": {
                      "source": "https://upload.wikimedia.org/wikipedia/commons/5/51/1984_cover.jpg"
                    }
                  }
                }
              }
            }
        """.trimIndent()
        val candidates = MetadataRepository.parseWikipedia(json, "ru")
        assertEquals(1, candidates.size)
        val book = candidates[0]
        assertEquals("1984", book.title)
        assertEquals(1984, book.year)
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/5/51/1984_cover.jpg", book.coverUrl)
        assertEquals("Википедия", book.source)
    }
}

class FuzzyMatcherTest {

    @Test
    fun `calculates high similarity for typos and inverted words`() {
        // Опечатки в авторе и названии
        val sim1 = com.aprireader.app.data.metadata.FuzzyMatcher.tokenSimilarity(
            query = "гарри потер и филосовский камень",
            target = "Гарри Поттер и философский камень Джоан Роулинг",
        )
        assertTrue("Expected sim > 0.8f, got $sim1", sim1 >= 0.8f)

        // Перестановка слов
        val sim2 = com.aprireader.app.data.metadata.FuzzyMatcher.tokenSimilarity(
            query = "булгаков мастер и маргарита",
            target = "Мастер и Маргарита Михаил Булгаков",
        )
        assertTrue("Expected sim > 0.85f, got $sim2", sim2 >= 0.85f)
    }

    @Test
    fun `transliterates latin to cyrillic and cyrillic to latin`() {
        val cyr = com.aprireader.app.data.metadata.FuzzyMatcher.transliterate("dostoevsky")
        assertTrue(cyr.contains("достоевск"))

        val lat = com.aprireader.app.data.metadata.FuzzyMatcher.transliterate("толстой")
        assertTrue(lat.contains("tolstoy") || lat.contains("tolstoy"))
    }

    @Test
    fun `finds famous title aliases between languages`() {
        assertEquals("The Fellowship of the Ring", com.aprireader.app.data.metadata.FuzzyMatcher.findTitleAlias("Братство кольца"))
        assertEquals("The Lord of the Rings", com.aprireader.app.data.metadata.FuzzyMatcher.findTitleAlias("Властелин колец"))
        assertEquals("Harry Potter", com.aprireader.app.data.metadata.FuzzyMatcher.findTitleAlias("Гарри Поттер"))
    }
}
