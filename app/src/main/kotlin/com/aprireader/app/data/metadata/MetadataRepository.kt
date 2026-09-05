package com.aprireader.app.data.metadata

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Найденный в сети вариант книги. */
data class MetadataCandidate(
    val title: String,
    val authors: List<String>,
    val year: Int?,
    val description: String?,
    val coverUrl: String?,
    val publisher: String? = null,
    val series: String? = null,
    val seriesIndex: Int? = null,
    val source: String = "Open Library",
    val score: Float = 0f,
)

/**
 * Сетевой репозиторий метаданных с поддержкой российских (FantLab) и международных
 * (Open Library, Google Books, Gutendex) каталогов, нечеткого поиска и надежной загрузки обложек.
 */
class MetadataRepository {

    private val _networkActive = MutableStateFlow(false)
    val networkActive: StateFlow<Boolean> = _networkActive.asStateFlow()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6000, TimeUnit.MILLISECONDS)
            .readTimeout(8000, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Ищет книгу по названию и автору с параллельным опросом всех провайдеров,
     * нечетким сопоставлением и ранжированием по релевантности.
     */
    suspend fun search(title: String, author: String?): Result<List<MetadataCandidate>> =
        withContext(Dispatchers.IO) {
            val cleanTitle = cleanQuery(title)
            val cleanAuthor = author?.let { cleanQuery(it) }?.takeIf { it.isNotBlank() }
            if (cleanTitle.isBlank() && cleanAuthor.isNullOrBlank()) {
                return@withContext Result.success(emptyList())
            }

            val query = listOfNotNull(cleanTitle.takeIf { it.isNotBlank() }, cleanAuthor).joinToString(" ")
            searchFreeform(query)
        }

    /** Свободный быстрый параллельный поиск по строке с нечетким сопоставлением и дедупликацией. */
    suspend fun searchFreeform(query: String): Result<List<MetadataCandidate>> = withContext(Dispatchers.IO) {
        val trimmed = cleanQuery(query)
        if (trimmed.isBlank()) return@withContext Result.success(emptyList())

        _networkActive.value = true
        try {
            val candidates = withTimeoutOrNull(15000L) {
                coroutineScope {
                    val deferredList = ArrayList<Deferred<List<MetadataCandidate>>>()
                    val transliterated = FuzzyMatcher.transliterate(trimmed)
                    val aliasQuery = FuzzyMatcher.findTitleAlias(trimmed)

                    // 1. Параллельный опрос всех основных источников
                    deferredList += async { requestFantLab(trimmed).getOrDefault(emptyList()) }
                    if (transliterated != trimmed) {
                        deferredList += async { requestFantLab(transliterated).getOrDefault(emptyList()) }
                    }
                    if (aliasQuery != null) {
                        deferredList += async { requestFantLab(aliasQuery).getOrDefault(emptyList()) }
                    }

                    deferredList += async { requestOpenLibrary(trimmed).getOrDefault(emptyList()) }
                    if (transliterated != trimmed) {
                        deferredList += async { requestOpenLibrary(transliterated).getOrDefault(emptyList()) }
                    }
                    if (aliasQuery != null) {
                        deferredList += async { requestOpenLibrary(aliasQuery).getOrDefault(emptyList()) }
                    }

                    deferredList += async { requestGoogleBooks(trimmed).getOrDefault(emptyList()) }
                    deferredList += async { requestGutendex(trimmed).getOrDefault(emptyList()) }
                    deferredList += async { requestWikipedia(trimmed, "ru").getOrDefault(emptyList()) }
                    if (transliterated != trimmed || aliasQuery != null) {
                        deferredList += async { requestWikipedia(aliasQuery ?: transliterated, "en").getOrDefault(emptyList()) }
                    }

                    val allCandidates = ArrayList<MetadataCandidate>()
                    for (d in deferredList) {
                        allCandidates += d.await()
                    }

                    // 2. Если результатов мало и в запросе несколько слов — ищем по подстрокам (Relaxed Query)
                    if (allCandidates.size < 4) {
                        val words = trimmed.split(" ").filter { it.length > 2 }
                        if (words.size > 1) {
                            val subQuery = words.take(2).joinToString(" ")
                            val flSub = async { requestFantLab(subQuery).getOrDefault(emptyList()) }
                            val olSub = async { requestOpenLibrary(subQuery).getOrDefault(emptyList()) }
                            allCandidates += flSub.await()
                            allCandidates += olSub.await()
                        }
                    }

                    allCandidates
                }
            } ?: emptyList()

            val ranked = rankAndDeduplicate(trimmed, candidates)
            Result.success(ranked)
        } catch (error: Throwable) {
            Result.failure(error)
        } finally {
            _networkActive.value = false
        }
    }

    /** Запрос к FantLab (крупнейшая российская база фантастики, классики и прозы). */
    private fun requestFantLab(query: String): Result<List<MetadataCandidate>> {
        val url = "https://api.fantlab.ru/search-works?q=${encode(query)}&page=1"
        return try {
            val call = client.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build()
            )
            call.execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Result.failure(IOException("FantLab ответил кодом ${response.code}"))
                } else {
                    Result.success(parseFantLab(body))
                }
            }
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    /** Запрос к Open Library API. */
    private fun requestOpenLibrary(query: String): Result<List<MetadataCandidate>> {
        val url = "https://openlibrary.org/search.json?q=${encode(query)}&limit=15"
        return try {
            val call = client.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build()
            )
            call.execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Result.failure(IOException("Open Library ответил кодом ${response.code}"))
                } else {
                    Result.success(parseSearch(body))
                }
            }
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    /** Запрос к Google Books API. */
    private fun requestGoogleBooks(query: String): Result<List<MetadataCandidate>> {
        val url = "https://www.googleapis.com/books/v1/volumes?q=${encode(query)}&maxResults=15"
        return try {
            val call = client.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build()
            )
            call.execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Result.failure(IOException("Google Books ответил кодом ${response.code}"))
                } else {
                    Result.success(parseGoogleBooks(body))
                }
            }
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    /** Запрос к Wikipedia API для быстрого поиска классических и мировых бестселлеров. */
    private fun requestWikipedia(query: String, lang: String): Result<List<MetadataCandidate>> {
        val url = "https://$lang.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=${encode(query)}&gsrlimit=6&prop=pageimages|extracts&pithumbsize=400&exintro=1&explaintext=1&exsentences=2&format=json"
        return try {
            val call = client.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", "ApriReader/1.0 (Mobile Android E-Reader; contact@aprireader.app)")
                    .header("Accept", "application/json")
                    .build()
            )
            call.execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Result.failure(IOException("Wikipedia ответил кодом ${response.code}"))
                } else {
                    Result.success(parseWikipedia(body, lang))
                }
            }
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    /** Запрос к Gutendex (Project Gutenberg). */
    private fun requestGutendex(query: String): Result<List<MetadataCandidate>> {
        val url = "https://gutendex.com/books/?search=${encode(query)}"
        return try {
            val call = client.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build()
            )
            call.execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Result.failure(IOException("Gutendex ответил кодом ${response.code}"))
                } else {
                    Result.success(parseGutendex(body))
                }
            }
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    /** Скачивает байты обложки с автоматическими резервными путями и валидацией размера. */
    suspend fun downloadCover(url: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        _networkActive.value = true
        try {
            val direct = fetchImageBytes(url)
            if (direct.isSuccess) return@withContext direct

            val fallbacks = mutableListOf<String>()
            if (url.contains("openlibrary.org/b/id/") && url.contains("-L.jpg")) {
                fallbacks += url.replace("-L.jpg", "-M.jpg")
            }
            if (url.contains("openlibrary.org/b/olid/") && url.contains("-L.jpg")) {
                fallbacks += url.replace("-L.jpg", "-M.jpg")
            }
            if (url.contains("data.fantlab.ru/images/editions/big/")) {
                fallbacks += url.replace("data.fantlab.ru/images/editions/big/", "fantlab.ru/images/editions/big/")
                fallbacks += url.replace("data.fantlab.ru/images/editions/big/", "data.fantlab.ru/images/editions/small/")
            }
            if (url.contains("books.google.com")) {
                fallbacks += url.replace("zoom=1", "zoom=0")
                fallbacks += url.replace("zoom=2", "zoom=1")
            }
            if (url.contains("upload.wikimedia.org") && url.contains("/thumb/")) {
                val mainUrl = url.substringBefore("/thumb/") + "/" + url.substringAfterLast("/")
                fallbacks += mainUrl
            }

            for (fb in fallbacks) {
                val r = fetchImageBytes(fb)
                if (r.isSuccess) return@withContext r
            }

            Result.failure(IOException("Обложку скачать не удалось"))
        } finally {
            _networkActive.value = false
        }
    }

    private fun fetchImageBytes(url: String): Result<ByteArray> {
        return try {
            val safeUrl = if (url.startsWith("http://", ignoreCase = true)) {
                url.replace("http://", "https://", ignoreCase = true)
            } else {
                url
            }
            val request = Request.Builder()
                .url(safeUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body
                val bytes = body?.bytes()
                if (!response.isSuccessful || bytes == null || bytes.size < 500 || bytes.size > 15 * 1024 * 1024) {
                    Result.failure(IOException("HTTP ${response.code} or invalid cover size"))
                } else {
                    Result.success(bytes)
                }
            }
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private fun rankAndDeduplicate(query: String, candidates: List<MetadataCandidate>): List<MetadataCandidate> {
        val scored = candidates.map { c ->
            val target = "${c.title} ${c.authors.joinToString(" ")}"
            var sim = FuzzyMatcher.tokenSimilarity(query, target)
            // Бонусы за совпадение названия
            val normQ = FuzzyMatcher.normalize(query)
            val normT = FuzzyMatcher.normalize(c.title)
            if (normQ == normT || normT.contains(normQ) || normQ.contains(normT)) {
                sim += 0.35f
            }
            // Бонусы за полноту карточки и наличие обложки
            if (c.coverUrl != null) sim += 0.25f
            if (!c.description.isNullOrBlank()) sim += 0.15f
            if (c.year != null) sim += 0.05f
            if (!c.publisher.isNullOrBlank()) sim += 0.03f
            if (!c.series.isNullOrBlank()) sim += 0.04f
            if (c.authors.isNotEmpty()) sim += 0.10f
            c.copy(score = sim)
        }

        // Дедупликация: группируем похожие книги по нормализованному названию и автору
        val deduped = ArrayList<MetadataCandidate>()
        val seen = HashSet<String>()

        for (c in scored.sortedByDescending { it.score }) {
            val key = FuzzyMatcher.normalize("${c.title} ${c.authors.firstOrNull().orEmpty()}")
            if (key.isBlank()) {
                deduped += c
                continue
            }
            if (seen.add(key)) {
                deduped += c
            } else {
                // Если дубликат имеет обложку или описание, а первый добавленный нет — дополняем
                val existingIndex = deduped.indexOfFirst { FuzzyMatcher.normalize("${it.title} ${it.authors.firstOrNull().orEmpty()}") == key }
                if (existingIndex >= 0) {
                    val existing = deduped[existingIndex]
                    if (existing.coverUrl == null && c.coverUrl != null) {
                        deduped[existingIndex] = existing.copy(coverUrl = c.coverUrl)
                    }
                    if (existing.description.isNullOrBlank() && !c.description.isNullOrBlank()) {
                        deduped[existingIndex] = deduped[existingIndex].copy(description = c.description)
                    }
                    if (existing.year == null && c.year != null) {
                        deduped[existingIndex] = deduped[existingIndex].copy(year = c.year)
                    }
                    if (existing.authors.isEmpty() && c.authors.isNotEmpty()) {
                        deduped[existingIndex] = deduped[existingIndex].copy(authors = c.authors)
                    }
                }
            }
        }

        return deduped
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value.trim(), "UTF-8")

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36 ApriReader/1.0"

        private val noiseTokens = Regex(
            "(?i)\\b(z-?lib(rary)?(\\.org)?|libgen|litres|литрес|flibusta|флибуста|royallib|epub|fb2|mobi|djvu|pdf|txt|azw3|ocr|scan|rus|eng|полная|версия|книга|том)\\b"
        )
        private val bracketed = Regex("[({\\[][^(){}\\[\\]]*[)}\\]]")
        private val separators = Regex("[_\\-.]+")
        private val longDigits = Regex("\\b\\d{4,}\\b")
        private val spaces = Regex("\\s{2,}")

        /** Очищает строку поиска от расширений и служебного шума. */
        fun cleanQuery(raw: String): String {
            var value = raw.trim()
            value = value.replace(Regex("\\.(epub|fb2(\\.zip)?|pdf|txt|html?|cbz|cbr|mobi|djvu|azw3)$", RegexOption.IGNORE_CASE), "")
            value = bracketed.replace(value, " ")
            value = separators.replace(value, " ")
            value = noiseTokens.replace(value, " ")

            val withoutDigits = spaces.replace(longDigits.replace(value, " "), " ").trim()
            value = if (withoutDigits.isNotBlank()) withoutDigits else spaces.replace(value, " ").trim()
            return value
        }

        /** Разбор ответа FantLab API с поддержкой реальных обложек изданий. */
        fun parseFantLab(body: String): List<MetadataCandidate> {
            val result = ArrayList<MetadataCandidate>()
            val root = runCatching {
                val trimmed = body.trimStart()
                if (trimmed.startsWith("{")) JSONObject(trimmed) else null
            }.getOrNull()

            val array = root?.optJSONArray("matches")
                ?: runCatching { if (body.trimStart().startsWith("[")) JSONArray(body) else null }.getOrNull()
                ?: return emptyList()

            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val workId = obj.optInt("work_id", 0)

                val rusTitle = obj.optString("rusname").ifBlank { obj.optString("work_name") }.trim()
                val origTitle = obj.optString("name").ifBlank { obj.optString("work_name_orig") }.trim()
                val title = if (rusTitle.isNotBlank()) rusTitle else origTitle
                if (title.isBlank()) continue

                val rusAuthor = obj.optString("all_autor_rusname")
                    .ifBlank { obj.optString("autor_rusname") }
                    .ifBlank { obj.optString("work_author") }
                    .trim()
                val origAuthor = obj.optString("all_autor_name")
                    .ifBlank { obj.optString("autor_name") }
                    .ifBlank { obj.optString("work_author_orig") }
                    .trim()
                val authors = when {
                    rusAuthor.isNotBlank() -> listOf(rusAuthor)
                    origAuthor.isNotBlank() -> listOf(origAuthor)
                    else -> emptyList()
                }

                val year = (obj.optInt("year").takeIf { it > 0 }
                    ?: obj.optInt("work_year").takeIf { it > 0 })

                val altname = obj.optString("altname").trim()
                val typeName = obj.optString("name_show_im").ifBlank { obj.optString("work_type_name") }.trim()
                val rawDescription = obj.optString("work_description").trim()

                val descParts = listOfNotNull(
                    rawDescription.takeIf { it.isNotBlank() },
                    if (rusTitle.isNotBlank() && origTitle.isNotBlank() && origTitle != rusTitle) origTitle else null,
                    typeName.takeIf { it.isNotBlank() },
                    altname.takeIf { it.isNotBlank() },
                )
                val description = if (descParts.isNotEmpty()) descParts.joinToString(" • ") else null

                val picEdition = obj.optInt("pic_edition_id_auto").takeIf { it > 0 }
                    ?: obj.optInt("pic_edition_id").takeIf { it > 0 }

                val coverUrl = when {
                    picEdition != null -> "https://data.fantlab.ru/images/editions/big/$picEdition"
                    workId > 0 -> "https://fantlab.ru/images/works/${workId}_1"
                    else -> null
                }

                result += MetadataCandidate(
                    title = title,
                    authors = authors,
                    year = year,
                    description = description,
                    coverUrl = coverUrl,
                    publisher = "FantLab",
                    source = "FantLab",
                )
            }
            return result
        }

        /** Разбор ответа Open Library. */
        fun parseSearch(body: String): List<MetadataCandidate> {
            val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
            val docs = root.optJSONArray("docs") ?: return emptyList()
            val result = ArrayList<MetadataCandidate>(docs.length())
            for (i in 0 until docs.length()) {
                val doc = docs.optJSONObject(i) ?: continue
                val title = doc.optString("title").trim()
                if (title.isBlank()) continue

                val authors = doc.optJSONArray("author_name")?.let { array ->
                    (0 until array.length()).mapNotNull { array.optString(it).trim().takeIf(String::isNotBlank) }
                }.orEmpty()

                val coverId = doc.opt("cover_i")?.toString()?.toIntOrNull()
                val coverEdition = doc.optString("cover_edition_key").takeIf(String::isNotBlank)
                val editionKey = doc.optJSONArray("edition_key")?.optString(0)?.takeIf(String::isNotBlank)
                val isbn = doc.optJSONArray("isbn")?.optString(0)?.takeIf(String::isNotBlank)

                val coverUrl = when {
                    coverId != null && coverId > 0 -> "https://covers.openlibrary.org/b/id/$coverId-M.jpg"
                    coverEdition != null -> "https://covers.openlibrary.org/b/olid/$coverEdition-M.jpg"
                    editionKey != null -> "https://covers.openlibrary.org/b/olid/$editionKey-M.jpg"
                    isbn != null -> "https://covers.openlibrary.org/b/isbn/$isbn-M.jpg"
                    else -> null
                }

                val sentence = doc.optJSONArray("first_sentence")
                    ?.let { array -> (0 until array.length()).firstNotNullOfOrNull { array.optString(it).takeIf(String::isNotBlank) } }
                    ?: doc.optJSONObject("first_sentence")?.optString("value")?.takeIf(String::isNotBlank)
                    ?: doc.optString("first_sentence").takeIf(String::isNotBlank)
                    ?: doc.optJSONObject("description")?.optString("value")?.takeIf(String::isNotBlank)
                    ?: doc.optString("description").takeIf(String::isNotBlank)
                    ?: doc.optString("subtitle").takeIf(String::isNotBlank)

                val publisher = doc.optJSONArray("publisher")?.optString(0)?.takeIf(String::isNotBlank)
                    ?: doc.optString("publisher").takeIf(String::isNotBlank)

                val year = doc.opt("first_publish_year")?.toString()?.toIntOrNull()

                result += MetadataCandidate(
                    title = title,
                    authors = authors,
                    year = year,
                    description = sentence,
                    coverUrl = coverUrl,
                    publisher = publisher,
                    source = "Open Library",
                )
            }
            return result
        }

        /** Разбор ответа Google Books API с оптимизацией URL обложки. */
        fun parseGoogleBooks(body: String): List<MetadataCandidate> {
            val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
            val items = root.optJSONArray("items") ?: return emptyList()
            val result = ArrayList<MetadataCandidate>(items.length())
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val volumeInfo = item.optJSONObject("volumeInfo") ?: continue
                val title = volumeInfo.optString("title").trim()
                if (title.isBlank()) continue

                val authors = volumeInfo.optJSONArray("authors")?.let { array ->
                    (0 until array.length()).mapNotNull { array.optString(it).trim().takeIf(String::isNotBlank) }
                }.orEmpty()

                val publishedDate = volumeInfo.optString("publishedDate").orEmpty()
                val year = Regex("\\b(\\d{4})\\b").find(publishedDate)?.groupValues?.get(1)?.toIntOrNull()

                val description = volumeInfo.optString("description").takeIf(String::isNotBlank)
                    ?: volumeInfo.optString("subtitle").takeIf(String::isNotBlank)

                val imageLinks = volumeInfo.optJSONObject("imageLinks")
                val rawThumbnail = imageLinks?.optString("thumbnail")?.takeIf(String::isNotBlank)
                    ?: imageLinks?.optString("smallThumbnail")?.takeIf(String::isNotBlank)
                    ?: imageLinks?.optString("medium")?.takeIf(String::isNotBlank)
                    ?: imageLinks?.optString("large")?.takeIf(String::isNotBlank)

                val coverUrl = rawThumbnail
                    ?.replace("http://", "https://")
                    ?.replace("&edge=curl", "")

                val publisher = volumeInfo.optString("publisher").takeIf(String::isNotBlank)

                result += MetadataCandidate(
                    title = title,
                    authors = authors,
                    year = year,
                    description = description,
                    coverUrl = coverUrl,
                    publisher = publisher,
                    source = "Google Books",
                )
            }
            return result
        }

        /** Разбор ответа Gutendex API. */
        fun parseGutendex(body: String): List<MetadataCandidate> {
            val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
            val results = root.optJSONArray("results") ?: return emptyList()
            val list = ArrayList<MetadataCandidate>(results.length())
            for (i in 0 until results.length()) {
                val obj = results.optJSONObject(i) ?: continue
                val title = obj.optString("title").trim()
                if (title.isBlank()) continue

                val authors = obj.optJSONArray("authors")?.let { arr ->
                    (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name")?.trim()?.takeIf(String::isNotBlank) }
                }.orEmpty()

                val formats = obj.optJSONObject("formats")
                val coverUrl = formats?.optString("image/jpeg")?.takeIf(String::isNotBlank)

                list += MetadataCandidate(
                    title = title,
                    authors = authors,
                    year = null,
                    description = obj.optJSONArray("subjects")?.optString(0),
                    coverUrl = coverUrl,
                    publisher = "Project Gutenberg",
                    source = "Gutenberg",
                )
            }
            return list
        }

        /** Разбор ответа Wikipedia API. */
        fun parseWikipedia(body: String, lang: String): List<MetadataCandidate> {
            val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
            val queryObj = root.optJSONObject("query") ?: return emptyList()
            val pages = queryObj.optJSONObject("pages") ?: return emptyList()
            val list = ArrayList<MetadataCandidate>()
            val keys = pages.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val page = pages.optJSONObject(key) ?: continue
                val rawTitle = page.optString("title").trim()
                if (rawTitle.isBlank()) continue
                val cleanTitle = rawTitle.replace(Regex("\\s*\\([^)]*\\)"), "").trim()
                val extract = page.optString("extract").trim()
                val thumb = page.optJSONObject("thumbnail")?.optString("source")?.takeIf(String::isNotBlank)
                val year = Regex("\\b(1[89]\\d{2}|20\\d{2})\\b").find(extract)?.groupValues?.get(1)?.toIntOrNull()

                list += MetadataCandidate(
                    title = cleanTitle.ifBlank { rawTitle },
                    authors = emptyList(),
                    year = year,
                    description = extract.takeIf { it.isNotBlank() },
                    coverUrl = thumb,
                    publisher = if (lang == "ru") "Википедия" else "Wikipedia",
                    source = if (lang == "ru") "Википедия" else "Wikipedia",
                )
            }
            return list
        }
    }
}

/** Нечеткое сопоставление строк, токенизация и транслитерация для устойчивого поиска. */
object FuzzyMatcher {

    fun normalize(s: String): String =
        s.lowercase()
            .replace(Regex("\\b(fb2|epub|pdf|cbz|cbr|txt|litres|литрес|книга|том|полное|собрание|издание)\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Сходство токенов запроса с целевым текстом от 0.0 до 1.0. */
    fun tokenSimilarity(query: String, target: String): Float {
        val qTokens = normalize(query).split(' ').filter { it.length > 1 }
        val tTokens = normalize(target).split(' ').filter { it.length > 1 }
        if (qTokens.isEmpty() || tTokens.isEmpty()) return 0f

        var scoreSum = 0f
        for (q in qTokens) {
            val best = tTokens.maxOfOrNull { t ->
                when {
                    q == t -> 1.0f
                    q in t || t in q -> 0.85f
                    levenshteinSimilarity(q, t) >= 0.60f -> levenshteinSimilarity(q, t)
                    else -> 0f
                }
            } ?: 0f
            scoreSum += best
        }
        return (scoreSum / qTokens.size).coerceIn(0f, 1f)
    }

    fun levenshteinSimilarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1f
        val dist = levenshteinDistance(s1, s2)
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 1f
        return (1f - dist.toFloat() / maxLen).coerceIn(0f, 1f)
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = IntArray(s2.length + 1) { it }
        for (i in 1..s1.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..s2.length) {
                val temp = dp[j]
                dp[j] = if (s1[i - 1] == s2[j - 1]) prev else minOf(prev, dp[j], dp[j - 1]) + 1
                prev = temp
            }
        }
        return dp[s2.length]
    }

    /** Транслитерация для поиска зарубежных авторов на русском и наоборот. */
    fun transliterate(text: String): String {
        val latToCyr = mapOf(
            "shch" to "щ", "yo" to "ё", "zh" to "ж", "kh" to "х", "ts" to "ц", "ch" to "ч",
            "sh" to "ш", "yu" to "ю", "ya" to "я", "a" to "а", "b" to "б", "v" to "в",
            "g" to "г", "d" to "д", "e" to "е", "z" to "з", "i" to "и", "j" to "й",
            "k" to "к", "l" to "л", "m" to "м", "n" to "н", "o" to "о", "p" to "п",
            "r" to "р", "s" to "с", "t" to "т", "u" to "у", "f" to "ф", "y" to "ы"
        )
        val cyrToLat = mapOf(
            'щ' to "shch", 'ё' to "yo", 'ж' to "zh", 'х' to "kh", 'ц' to "ts", 'ч' to "ch",
            'ш' to "sh", 'ю' to "yu", 'я' to "ya", 'а' to "a", 'б' to "b", 'в' to "v",
            'г' to "g", 'д' to "d", 'е' to "e", 'з' to "z", 'и' to "i", 'й' to "y",
            'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p",
            'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'ы' to "y",
            'э' to "e", 'ъ' to "", 'ь' to ""
        )

        val lower = text.lowercase()
        // Если текст латинский — транслитерируем в кириллицу
        if (lower.any { it in 'a'..'z' }) {
            var res = lower
            for ((lat, cyr) in latToCyr) {
                res = res.replace(lat, cyr)
            }
            return res
        }

        // Если текст кириллический — транслитерируем в латиницу
        if (lower.any { it in 'а'..'я' || it == 'ё' }) {
            val sb = StringBuilder()
            for (c in lower) {
                sb.append(cyrToLat[c] ?: c)
            }
            return sb.toString()
        }

        return text
    }

    /** Сопоставление популярных названий книг между русским и английским. */
    fun findTitleAlias(query: String): String? {
        val norm = normalize(query)
        val dict = mapOf(
            "братство кольца" to "The Fellowship of the Ring",
            "две крепости" to "The Two Towers",
            "две башни" to "The Two Towers",
            "возвращение короля" to "The Return of the King",
            "властелин колец" to "The Lord of the Rings",
            "хоббит" to "The Hobbit",
            "гарри поттер" to "Harry Potter",
            "философский камень" to "Philosopher's Stone",
            "тайная комната" to "Chamber of Secrets",
            "узник азкабана" to "Prisoner of Azkaban",
            "кубок огня" to "Goblet of Fire",
            "орден феникса" to "Order of the Phoenix",
            "принц полукровка" to "Half-Blood Prince",
            "дары смерти" to "Deathly Hallows",
            "игра престолов" to "A Game of Thrones",
            "песнь льда и пламени" to "A Song of Ice and Fire",
            "ведьмак" to "The Witcher",
            "последнее желание" to "The Last Wish",
            "меч предназначения" to "Sword of Destiny",
            "дюна" to "Dune",
            "о дивный новый мир" to "Brave New World",
            "убить пересмешника" to "To Kill a Mockingbird",
            "великий гэтсби" to "The Great Gatsby",
            "над пропастью во ржи" to "The Catcher in the Rye",
            "маленький принц" to "The Little Prince",
            "три товарища" to "Three Comrades",
            "триумфальная арка" to "Arch of Triumph",
            "война и мир" to "War and Peace",
            "преступление и наказание" to "Crime and Punishment",
            "мастер и маргарита" to "The Master and Margarita",
            "собачье сердце" to "Heart of a Dog",
            "отцы и дети" to "Fathers and Sons",
            "евгений онегин" to "Eugene Onegin",
            "герой нашего времени" to "A Hero of Our Time",
            "мертвые души" to "Dead Souls",
            "идиот" to "The Idiot",
            "братья карамазовы" to "The Brothers Karamazov",
            "анна каренина" to "Anna Karenina",
            "четыреста пятьдесят один градус по фаренгейту" to "Fahrenheit 451",
            "451 градус по фаренгейту" to "Fahrenheit 451",
            "цветы для элджернона" to "Flowers for Algernon",
            "атлант расправил плечи" to "Atlas Shrugged",
            "источник" to "The Fountainhead",
            "сияние" to "The Shining",
            "зеленая миля" to "The Green Mile",
            "побег из шоушенка" to "The Shawshank Redemption",
            "оно" to "It",
            "кладбище домашних животных" to "Pet Sematary",
            "автостопом по галактике" to "The Hitchhiker's Guide to the Galaxy",
        )
        for ((ru, en) in dict) {
            if (norm.contains(ru)) return en
        }
        return null
    }
}
