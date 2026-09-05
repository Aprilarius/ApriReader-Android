package com.aprireader.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Папка или отдельный файл, к которым выдан постоянный доступ через SAF. */
@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey val treeUri: String,
    val displayName: String,
    val addedAt: Long,
    val lastScanAt: Long = 0,
    /** false — доступ отозван или папка недостижима. Книги при этом не удаляются. */
    val available: Boolean = true,
    val bookCount: Int = 0,
)

/**
 * Книга. Первичный ключ — контентный [id], а не URI: URI меняется при
 * переоформлении доступа, а прогресс чтения теряться не должен.
 */
@Entity(
    tableName = "books",
    indices = [Index("sourceUri"), Index("lastOpenedAt"), Index("title")],
)
data class BookEntity(
    @PrimaryKey val id: String,
    val documentUri: String,
    val sourceUri: String?,
    val fileName: String,
    val fileSize: Long,
    val format: String,
    val title: String,
    /** Авторы, склеенные через «; » — отдельная таблица здесь избыточна. */
    val authors: String = "",
    val description: String? = null,
    val language: String? = null,
    val publisher: String? = null,
    val year: Int? = null,
    val series: String? = null,
    val seriesIndex: Int? = null,
    val coverPath: String? = null,
    val addedAt: Long,
    val lastOpenedAt: Long? = null,
    val available: Boolean = true,
    val favorite: Boolean = false,
    val finishedAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val progress: Float = 0f,
    /** Позиция чтения: индекс главы/страницы и смещение внутри неё. */
    val locatorUnit: Int = 0,
    val locatorOffset: Int = 0,
    val totalUnits: Int = 0,
    val totalChars: Int = 0,
    /** Акцент, извлечённый из обложки автоматически. */
    val autoAccent: Int? = null,
    /** Акцент, закреплённый пользователем. Имеет приоритет и не перезаписывается. */
    val pinnedAccent: Int? = null,
    val metadataFetchedAt: Long? = null,
)

enum class MarkKind { BOOKMARK, HIGHLIGHT, NOTE }

@Entity(
    tableName = "marks",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bookId")],
)
data class MarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val kind: String,
    val unit: Int,
    val startOffset: Int,
    val endOffset: Int,
    val quotedText: String? = null,
    val note: String? = null,
    val colorIndex: Int = 0,
    val chapterTitle: String? = null,
    val createdAt: Long,
)

/** Сессия чтения — основа статистики и стриков. Хранится только на устройстве. */
@Entity(
    tableName = "reading_sessions",
    indices = [Index("bookId"), Index("startedAt")],
)
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationMs: Long,
    val charsRead: Int,
    /** Локальная дата в формате yyyy-MM-dd — по ней считаются дни и стрики. */
    val localDate: String,
)

/** Сохранённый набор типографики («Ночь», «Крупный», свои пресеты). */
@Entity(tableName = "typography_presets")
data class TypographyPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val fontFamily: String,
    val fontSizeSp: Float,
    val lineHeight: Float,
    val letterSpacing: Float,
    val paragraphSpacing: Float,
    val horizontalMargin: Float,
    val maxLineWidth: Int,
    val justify: Boolean,
    val firstLineIndent: Boolean,
    val createdAt: Long,
)

/** Разблокированное достижение. Прогресс достижений считается из сессий. */
@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val key: String,
    val unlockedAt: Long,
    val seen: Boolean = false,
)
