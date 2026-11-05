package com.manuscript.core.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "objects", primaryKeys = ["accountId", "id"])
data class ObjectEntity(
    val accountId: String,
    val id: String,
    val kind: String,
    val json: String,
    val title: String,
    val parentId: String?,
    val lastEdited: String?,
    val updatedAt: Long,
)

@Entity(tableName = "children", primaryKeys = ["accountId", "parentId"])
data class ChildrenEntity(
    val accountId: String,
    val parentId: String,
    val json: String,
    val updatedAt: Long,
)

@Entity(tableName = "recents", primaryKeys = ["accountId", "id"])
data class RecentEntity(
    val accountId: String,
    val id: String,
    val kind: String,
    val title: String,
    val iconJson: String?,
    val coverUrl: String?,
    val visitedAt: Long,
    val visits: Int,
)

@Entity(tableName = "favorites", primaryKeys = ["accountId", "id"])
data class FavoriteEntity(
    val accountId: String,
    val id: String,
    val kind: String,
    val title: String,
    val iconJson: String?,
    val position: Int,
    val addedAt: Long,
)

@Entity(tableName = "searches", primaryKeys = ["accountId", "query"])
data class SearchEntity(val accountId: String, val query: String, val at: Long)

@Entity(tableName = "kv", primaryKeys = ["accountId", "key"])
data class KvEntity(val accountId: String, val key: String, val value: String)

@Dao
interface CacheDao {
    @Upsert
    suspend fun putObject(entity: ObjectEntity)

    @Upsert
    suspend fun putObjects(entities: List<ObjectEntity>)

    @Query("SELECT * FROM objects WHERE accountId = :account AND id = :id")
    suspend fun getObject(account: String, id: String): ObjectEntity?

    @Query("SELECT * FROM objects WHERE accountId = :account AND kind = :kind ORDER BY lastEdited DESC LIMIT :limit")
    suspend fun objectsOfKind(account: String, kind: String, limit: Int): List<ObjectEntity>

    @Query("SELECT * FROM objects WHERE accountId = :account AND title LIKE '%' || :query || '%' ORDER BY lastEdited DESC LIMIT 50")
    suspend fun searchTitles(account: String, query: String): List<ObjectEntity>

    @Query("DELETE FROM objects WHERE accountId = :account AND id = :id")
    suspend fun deleteObject(account: String, id: String)

    @Upsert
    suspend fun putChildren(entity: ChildrenEntity)

    @Query("SELECT * FROM children WHERE accountId = :account AND parentId = :parentId")
    suspend fun getChildren(account: String, parentId: String): ChildrenEntity?

    @Query("SELECT COUNT(*) FROM children WHERE accountId = :account")
    suspend fun cachedPageCount(account: String): Int

    @Query("SELECT COALESCE(SUM(LENGTH(json)), 0) FROM children WHERE accountId = :account")
    suspend fun cachedBytes(account: String): Long

    @Upsert
    suspend fun putKv(entity: KvEntity)

    @Query("SELECT value FROM kv WHERE accountId = :account AND `key` = :key")
    suspend fun getKv(account: String, key: String): String?

    @Query("SELECT value FROM kv WHERE accountId = :account AND `key` = :key")
    fun observeKv(account: String, key: String): Flow<String?>

    @Query("DELETE FROM objects WHERE accountId = :account")
    suspend fun clearObjects(account: String)

    @Query("DELETE FROM children WHERE accountId = :account")
    suspend fun clearChildren(account: String)

    @Query("DELETE FROM kv WHERE accountId = :account")
    suspend fun clearKv(account: String)
}

@Dao
interface RecentDao {
    @Query("SELECT * FROM recents WHERE accountId = :account ORDER BY visitedAt DESC LIMIT :limit")
    fun observe(account: String, limit: Int = 30): Flow<List<RecentEntity>>

    @Query("SELECT * FROM recents WHERE accountId = :account AND id = :id")
    suspend fun get(account: String, id: String): RecentEntity?

    @Upsert
    suspend fun put(entity: RecentEntity)

    @Query("DELETE FROM recents WHERE accountId = :account AND id = :id")
    suspend fun delete(account: String, id: String)

    @Query("DELETE FROM recents WHERE accountId = :account")
    suspend fun clear(account: String)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites WHERE accountId = :account ORDER BY position ASC, addedAt ASC")
    fun observe(account: String): Flow<List<FavoriteEntity>>

    @Query("SELECT COUNT(*) FROM favorites WHERE accountId = :account AND id = :id")
    fun observeIsFavorite(account: String, id: String): Flow<Int>

    @Query("SELECT COALESCE(MAX(position), 0) FROM favorites WHERE accountId = :account")
    suspend fun maxPosition(account: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: FavoriteEntity)

    @Upsert
    suspend fun putAll(entities: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE accountId = :account AND id = :id")
    suspend fun delete(account: String, id: String)

    @Query("UPDATE favorites SET title = :title, iconJson = :iconJson WHERE accountId = :account AND id = :id")
    suspend fun refresh(account: String, id: String, title: String, iconJson: String?)
}

@Dao
interface SearchDao {
    @Query("SELECT * FROM searches WHERE accountId = :account ORDER BY at DESC LIMIT 12")
    fun observe(account: String): Flow<List<SearchEntity>>

    @Upsert
    suspend fun put(entity: SearchEntity)

    @Query("DELETE FROM searches WHERE accountId = :account AND `query` = :query")
    suspend fun delete(account: String, query: String)

    @Query("DELETE FROM searches WHERE accountId = :account")
    suspend fun clear(account: String)
}

@Database(
    entities = [ObjectEntity::class, ChildrenEntity::class, RecentEntity::class, FavoriteEntity::class, SearchEntity::class, KvEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cache(): CacheDao
    abstract fun recents(): RecentDao
    abstract fun favorites(): FavoriteDao
    abstract fun searches(): SearchDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "manuscript.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
