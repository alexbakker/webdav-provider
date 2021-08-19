package me.alexbakker.webdav.data

import androidx.room.*

@Entity(
    tableName = "cache_entry",
    foreignKeys = [ForeignKey(
        entity = Account::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("account_id"),
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["account_id", "path"], unique = true)
    ]
)
data class CacheEntry(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,

    @ColumnInfo(name = "account_id")
    var accountId: Long,

    @ColumnInfo(name = "status")
    var status: Status,

    @ColumnInfo(name = "path")
    var path: String,

    @ColumnInfo(name = "etag")
    var etag: String?,

    @ColumnInfo(name = "content_length")
    var contentLength: Long?,

    @ColumnInfo(name = "last_modified")
    var lastModified: Long? = null
) {
    enum class Status {
        DONE, PENDING
    }
}
