package com.animehub.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import com.animehub.domain.model.Anime
import com.animehub.domain.model.EntryRef
import com.animehub.domain.model.SiteInfo
import com.animehub.domain.model.Work
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sites")
data class SiteEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    val searchUrl: String?,
    val itemSelector: String?,
    val episodeSelector: String?,
    val enabled: Boolean,
    val builtIn: Boolean,
    val sortOrder: Int,
)

/** Favorito por obra (chave = título normalizado); guarda as referências de todas as fontes. */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val workKey: String,
    val title: String,
    val coverUrl: String?,
    val refs: String,
)

@Entity(tableName = "progress", primaryKeys = ["workKey", "episodeKey"])
data class ProgressEntity(
    val workKey: String,
    val episodeKey: String,
    val title: String,
    val coverUrl: String?,
    val refs: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

@Dao
interface SiteDao {
    @Query("SELECT * FROM sites ORDER BY builtIn DESC, sortOrder, name")
    fun observeAll(): Flow<List<SiteEntity>>

    @Query("SELECT * FROM sites")
    suspend fun all(): List<SiteEntity>

    @Query("SELECT * FROM sites WHERE id = :id")
    suspend fun get(id: String): SiteEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(site: SiteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(site: SiteEntity)

    @Query("UPDATE sites SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM sites WHERE id = :id AND builtIn = 0")
    suspend fun delete(id: String)
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM favorites ORDER BY title")
    fun favorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT COUNT(*) FROM favorites WHERE workKey = :key")
    fun isFavoriteFlow(key: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM favorites WHERE workKey = :key")
    suspend fun favoriteCount(key: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(f: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE workKey = :key")
    suspend fun deleteFavorite(key: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(p: ProgressEntity)

    @Query("SELECT positionMs FROM progress WHERE workKey = :key AND episodeKey = :episode")
    suspend fun progress(key: String, episode: String): Long?

    @Query(
        "SELECT * FROM progress WHERE updatedAt IN " +
            "(SELECT MAX(updatedAt) FROM progress GROUP BY workKey) " +
            "ORDER BY updatedAt DESC LIMIT 10"
    )
    fun recent(): Flow<List<ProgressEntity>>
}

@Database(
    entities = [SiteEntity::class, FavoriteEntity::class, ProgressEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun siteDao(): SiteDao
    abstract fun libraryDao(): LibraryDao
}

fun SiteEntity.toInfo() = SiteInfo(
    id = id, name = name, baseUrl = baseUrl, enabled = enabled, builtIn = builtIn,
    searchUrl = searchUrl, itemSelector = itemSelector, episodeSelector = episodeSelector,
)

fun FavoriteEntity.toWork(): Work = Work(
    key = workKey,
    title = title,
    coverUrl = coverUrl,
    entries = EntryRef.decodeList(refs).map { Anime(it.sourceId, it.animeId, title, coverUrl) },
)
