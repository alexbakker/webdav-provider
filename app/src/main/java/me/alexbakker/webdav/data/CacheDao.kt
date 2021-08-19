package me.alexbakker.webdav.data

import androidx.room.*

@Dao
interface CacheDao {
    @Query("SELECT * FROM cache_entry WHERE id=:id")
    fun getById(id: Long): CacheEntry?

    @Query("SELECT * FROM cache_entry WHERE account_id=:accountId")
    fun getByAccountId(accountId: Long): List<CacheEntry>

    @Query("SELECT * FROM cache_entry WHERE account_id=:accountId AND path=:path")
    fun getByPath(accountId: Long, path: String): CacheEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entry: CacheEntry): Long

    @Update
    fun update(vararg entries: CacheEntry)

    @Delete
    fun delete(vararg entry: CacheEntry)
}
