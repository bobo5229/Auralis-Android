package com.bobo.auralis.mobile.library.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bobo.auralis.mobile.library.db.entity.TrackEntity
import com.bobo.auralis.mobile.library.model.AlbumId
import com.bobo.auralis.mobile.library.model.TrackId

/**
 * Data access for logical tracks.
 *
 * The lookup by `(trackKeyVersion, trackKeyHash)` is what reconciliation uses to
 * find an existing track for a new source. Note that finding several rows for one
 * key is a legitimate outcome, not an error: §33 requires an ambiguous match to
 * create a new track instead of guessing, so this returns a list.
 *
 * [deleteById] exists for explicit maintenance only. Normal reconcile never
 * deletes a track — a track whose last source disappears keeps its row as the
 * tombstone so playlists referencing `TrackId` can recover later (§37, §38).
 */
@Dao
interface TrackDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(track: TrackEntity)

    @Update
    suspend fun update(track: TrackEntity)

    @Query("SELECT * FROM track WHERE trackId = :trackId")
    suspend fun findById(trackId: TrackId): TrackEntity?

    @Query(
        "SELECT * FROM track WHERE trackKeyVersion = :version AND trackKeyHash = :hash",
    )
    suspend fun findByKey(version: Int, hash: String): List<TrackEntity>

    @Query("SELECT * FROM track WHERE albumId = :albumId")
    suspend fun findByAlbum(albumId: AlbumId): List<TrackEntity>

    @Query("DELETE FROM track WHERE trackId = :trackId")
    suspend fun deleteById(trackId: TrackId)

    @Query("SELECT * FROM track ORDER BY createdAt ASC")
    suspend fun getAll(): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM track")
    suspend fun count(): Int
}
