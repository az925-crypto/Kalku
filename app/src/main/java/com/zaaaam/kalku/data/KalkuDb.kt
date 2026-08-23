package com.zaaaam.kalku.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** Metadata/index record for one vault item (file or folder). Content never lives here. */
@Entity(
    tableName = "files",
    indices = [Index(value = ["relPath"], unique = true)],
)
data class FileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Path relative to vault root using '/' separators. Root folder itself has relPath "". */
    val relPath: String,
    val name: String,
    /** relPath of containing folder ("" for items directly under root). */
    val parent: String,
    val isFolder: Boolean,
    val category: String,
    val mime: String,
    val size: Long,
    val createdAt: Long,
    val modifiedAt: Long,
    val favorite: Boolean = false,
    /** Comma-separated user tags. */
    val tags: String = "",
    /** True while the item sits in the recycle bin (.Trash); metadata is kept for restore. */
    val deleted: Boolean = false,
)

@Entity(tableName = "trash")
data class TrashEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Physical file name inside .Trash (uuid-based, collision free). */
    val trashName: String,
    val name: String,
    /** Full original relPath of the top-level item (descendants share its prefix). */
    val originalRelPath: String,
    val originalParent: String,
    val isFolder: Boolean,
    val category: String,
    val size: Long,
    val deletedAt: Long,
)

@Entity(tableName = "recent")
data class RecentEntity(
    @PrimaryKey val fileId: Long,
    val name: String,
    val relPath: String,
    val category: String,
    val openedAt: Long,
)

@Entity(tableName = "calc_history")
data class CalcHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expression: String,
    val result: String,
    val timestamp: Long,
)

@Dao
interface FileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: FileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<FileEntity>)

    @Query("SELECT * FROM files WHERE relPath = :relPath LIMIT 1")
    suspend fun byPath(relPath: String): FileEntity?

    @Query("SELECT * FROM files WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): FileEntity?

    @Query("SELECT * FROM files WHERE parent = :parent AND deleted = 0 ORDER BY isFolder DESC, name COLLATE NOCASE ASC")
    suspend fun children(parent: String): List<FileEntity>

    @Query("SELECT * FROM files WHERE deleted = 0 ORDER BY isFolder DESC, name COLLATE NOCASE ASC")
    suspend fun all(): List<FileEntity>

    @Query("SELECT * FROM files WHERE deleted = 0 ORDER BY isFolder DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<FileEntity>>

    @Query("DELETE FROM files")
    suspend fun clear()

    @Query("UPDATE files SET favorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("UPDATE files SET tags = :tags WHERE id = :id")
    suspend fun setTags(id: Long, tags: String)

    @androidx.room.Query("UPDATE files SET relPath = :newPath, parent = :newParent, name = :newName WHERE id = :id")
    suspend fun repathEntry(id: Long, newPath: String, newParent: String, newName: String)

    @androidx.room.Query("UPDATE files SET relPath = :newPath WHERE relPath = :oldPath")
    suspend fun repath(oldPath: String, newPath: String)

    @Query("UPDATE files SET size = :size, modifiedAt = :modifiedAt WHERE relPath = :relPath")
    suspend fun updateStat(relPath: String, size: Long, modifiedAt: Long)

    @Query("DELETE FROM files WHERE relPath IN (:paths)")
    suspend fun deleteByPaths(paths: List<String>)

    @Query("SELECT * FROM files WHERE deleted = 1")
    suspend fun allDeleted(): List<FileEntity>

    @Query("SELECT * FROM files WHERE deleted = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observeDeleted(): Flow<List<FileEntity>>

    @Query("UPDATE files SET deleted = 1 WHERE relPath = :relPath OR relPath LIKE :prefix || '/%' ESCAPE '\\'")
    suspend fun markDeleted(relPath: String, prefix: String)

    @Query("UPDATE files SET deleted = 0 WHERE relPath = :relPath OR relPath LIKE :prefix || '/%' ESCAPE '\\'")
    suspend fun markAlive(relPath: String, prefix: String)

    @Query("DELETE FROM files WHERE relPath = :relPath OR relPath LIKE :prefix || '/%' ESCAPE '\\'")
    suspend fun deleteTree(relPath: String, prefix: String)

    @Query("SELECT * FROM files WHERE deleted = 0 AND favorite = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observeFavorites(): Flow<List<FileEntity>>

    @Query("SELECT COUNT(*) FROM files WHERE deleted = 0 AND category = :cat")
    suspend fun countInCategory(cat: String): Int

    @Query("SELECT COALESCE(SUM(size), 0) FROM files WHERE deleted = 0 AND category = :cat")
    suspend fun sizeOfCategory(cat: String): Long
}

@Dao
interface TrashDao {
    @Insert
    suspend fun insert(entry: TrashEntity): Long

    @Query("SELECT * FROM trash ORDER BY deletedAt DESC")
    fun observeAll(): Flow<List<TrashEntity>>

    @Query("SELECT * FROM trash WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): TrashEntity?

    @Query("DELETE FROM trash WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM trash WHERE deletedAt < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long): Int

    @Query("DELETE FROM trash")
    suspend fun clear()
}

@Dao
interface RecentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: RecentEntity)

    @Query("SELECT * FROM recent ORDER BY openedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RecentEntity>>

    @Query("DELETE FROM recent WHERE fileId NOT IN (SELECT id FROM files)")
    suspend fun pruneOrphans()
}

@Dao
interface CalcHistoryDao {
    @Insert
    suspend fun insert(entry: CalcHistoryEntity)

    @Query("SELECT * FROM calc_history ORDER BY timestamp DESC LIMIT :limit")
    fun observeHistory(limit: Int): Flow<List<CalcHistoryEntity>>

    @Query("DELETE FROM calc_history")
    suspend fun clear()
}

@Database(
    entities = [FileEntity::class, TrashEntity::class, RecentEntity::class, CalcHistoryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class KalkuDatabase : RoomDatabase() {
    abstract fun fileDao(): FileDao
    abstract fun trashDao(): TrashDao
    abstract fun recentDao(): RecentDao
    abstract fun calcHistoryDao(): CalcHistoryDao
}
