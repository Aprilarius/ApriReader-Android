package com.aprireader.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {

    @Query("SELECT * FROM sources ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources")
    suspend fun getAll(): List<SourceEntity>

    @Upsert
    suspend fun upsert(source: SourceEntity)

    @Query("UPDATE sources SET available = :available WHERE treeUri = :treeUri")
    suspend fun setAvailable(treeUri: String, available: Boolean)

    @Query("UPDATE sources SET lastScanAt = :timestamp, bookCount = :count WHERE treeUri = :treeUri")
    suspend fun markScanned(treeUri: String, timestamp: Long, count: Int)

    @Query("DELETE FROM sources WHERE treeUri = :treeUri")
    suspend fun delete(treeUri: String)
}

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeById(id: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE lastOpenedAt IS NOT NULL ORDER BY lastOpenedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE lastOpenedAt IS NOT NULL ORDER BY lastOpenedAt DESC LIMIT 1")
    suspend fun getMostRecent(): BookEntity?

    @Query("SELECT id FROM books")
    suspend fun allIds(): List<String>

    @Query("SELECT COUNT(*) FROM books")
    suspend fun count(): Int

    @Query("SELECT * FROM books WHERE sourceUri = :sourceUri")
    suspend fun getBySource(sourceUri: String): List<BookEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoring(books: List<BookEntity>): List<Long>

    @Upsert
    suspend fun upsert(book: BookEntity)

    @Update
    suspend fun update(book: BookEntity)

    @Query("UPDATE books SET documentUri = :uri, available = 1, fileName = :fileName WHERE id = :id")
    suspend fun relink(id: String, uri: String, fileName: String)

    @Query("UPDATE books SET available = :available WHERE sourceUri = :sourceUri")
    suspend fun setSourceAvailability(sourceUri: String, available: Boolean)

    @Query("UPDATE books SET available = :available WHERE id = :id")
    suspend fun setAvailable(id: String, available: Boolean)

    @Query("UPDATE books SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE books SET pinnedAccent = :color WHERE id = :id")
    suspend fun setPinnedAccent(id: String, color: Int?)

    @Query("UPDATE books SET autoAccent = :color WHERE id = :id")
    suspend fun setAutoAccent(id: String, color: Int?)

    @Query(
        """
        UPDATE books
        SET locatorUnit = :unit, locatorOffset = :offset, progress = :progress,
            lastOpenedAt = :timestamp, totalUnits = :totalUnits,
            finishedAt = CASE WHEN :progress >= 0.995 AND finishedAt IS NULL THEN :timestamp ELSE finishedAt END
        WHERE id = :id
        """
    )
    suspend fun updateProgress(
        id: String,
        unit: Int,
        offset: Int,
        progress: Float,
        totalUnits: Int,
        timestamp: Long,
    )

    @Query("UPDATE books SET lastOpenedAt = :timestamp WHERE id = :id")
    suspend fun touch(id: String, timestamp: Long)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM books WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)

    @Query("DELETE FROM books WHERE sourceUri = :sourceUri")
    suspend fun deleteBySource(sourceUri: String)

    @Query("SELECT COUNT(*) FROM books")
    fun observeCount(): Flow<Int>
}

@Dao
interface MarkDao {

    @Query("SELECT * FROM marks WHERE bookId = :bookId ORDER BY unit, startOffset")
    fun observeForBook(bookId: String): Flow<List<MarkEntity>>

    @Query("SELECT * FROM marks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MarkEntity>>

    @Upsert
    suspend fun upsert(mark: MarkEntity): Long

    @Query("DELETE FROM marks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM marks")
    suspend fun count(): Int
}

@Dao
interface StatsDao {

    @Insert
    suspend fun insert(session: ReadingSessionEntity): Long

    @Query("SELECT * FROM reading_sessions WHERE startedAt >= :since ORDER BY startedAt DESC")
    fun observeSince(since: Long): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<ReadingSessionEntity>>

    @Query("SELECT DISTINCT localDate FROM reading_sessions ORDER BY localDate DESC")
    fun observeReadingDays(): Flow<List<String>>

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM reading_sessions")
    fun observeTotalMillis(): Flow<Long>

    @Query("SELECT COALESCE(SUM(charsRead), 0) FROM reading_sessions")
    fun observeTotalChars(): Flow<Long>

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM reading_sessions WHERE localDate = :localDate")
    fun observeMillisForDate(localDate: String): Flow<Long>

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM reading_sessions WHERE bookId = :bookId")
    suspend fun totalMillisForBook(bookId: String): Long
}

@Dao
interface PresetDao {

    @Query("SELECT * FROM typography_presets ORDER BY createdAt")
    fun observeAll(): Flow<List<TypographyPresetEntity>>

    @Upsert
    suspend fun upsert(preset: TypographyPresetEntity): Long

    @Query("DELETE FROM typography_presets WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AchievementDao {

    @Query("SELECT * FROM achievements")
    fun observeAll(): Flow<List<AchievementEntity>>

    @Upsert
    suspend fun upsert(achievement: AchievementEntity)

    @Query("UPDATE achievements SET seen = 1 WHERE key = :key")
    suspend fun markSeen(key: String)

    @Transaction
    suspend fun unlockIfNeeded(key: String, timestamp: Long, known: Set<String>) {
        if (key !in known) upsert(AchievementEntity(key = key, unlockedAt = timestamp))
    }
}
