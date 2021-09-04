package me.alexbakker.webdav.data

import androidx.room.*

@Dao
interface AccountDao {
    @Query("SELECT COUNT(id) FROM account")
    fun count(): Long

    @Query("SELECT * FROM account")
    fun getAll(): List<Account>

    @Query("SELECT * FROM account WHERE id=:id")
    fun getById(id: Long): Account?

    @Insert
    fun insert(account: Account): Long

    @Update
    fun update(vararg accounts: Account)

    @Delete
    fun delete(vararg accounts: Account)
}
