package com.bobo.auralis.mobile.library.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bobo.auralis.mobile.library.db.entity.SourceMetadataEntity
import com.bobo.auralis.mobile.library.db.entity.TrackActiveSourceEntity
import com.bobo.auralis.mobile.library.db.entity.TrackSourceEntity
import com.bobo.auralis.mobile.library.model.TrackId
import com.bobo.auralis.mobile.library.model.TrackSourceId

/**
 * Data access for physical sources, their metadata snapshots and the active
 * source pointer.
 *
 * [findByDocumentKey] is the physical continuity lookup and always runs before any
 * semantic matching: a file that only moved, and whose provider still reports the
 * same document id, keeps both its `TrackSourceId` and its `TrackId` (§28).
 *
 * `source_metadata` cascades from `track_source`, so replacing a source's snapshot
 * is a delete-and-insert of the child row, never an orphan.
 */
@Dao
interface SourceDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(source: TrackSourceEntity)

    @Update
    suspend fun update(source: TrackSourceEntity)

    @Query("SELECT * FROM track_source WHERE sourceId = :sourceId")
    suspend fun findById(sourceId: TrackSourceId): TrackSourceEntity?

    @Query("SELECT * FROM track_source WHERE provider = :provider AND documentId = :documentId")
    suspend fun findByDocumentKey(provider: String, documentId: String): TrackSourceEntity?

    @Query("SELECT * FROM track_source WHERE trackId = :trackId")
    suspend fun findByTrack(trackId: TrackId): List<TrackSourceEntity>

    /**
     * Sources not observed by the given scan session.
     *
     * Whether any of them may actually be declared `MISSING` is a reconciliation
     * decision (§22, §37): a source whose scope was unreachable during this scan
     * must stay `UNREACHABLE` instead.
     */
    @Query("SELECT * FROM track_source WHERE lastSeenScanId IS NULL OR lastSeenScanId != :scanId")
    suspend fun findNotSeenInScan(scanId: Long): List<TrackSourceEntity>

    @Query("DELETE FROM track_source WHERE sourceId = :sourceId")
    suspend fun deleteById(sourceId: TrackSourceId)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: SourceMetadataEntity)

    @Query("SELECT * FROM source_metadata WHERE sourceId = :sourceId")
    suspend fun findMetadata(sourceId: TrackSourceId): SourceMetadataEntity?

    @Query("DELETE FROM source_metadata WHERE sourceId = :sourceId")
    suspend fun deleteMetadata(sourceId: TrackSourceId)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setActiveSource(active: TrackActiveSourceEntity)

    @Query("SELECT * FROM track_active_source WHERE trackId = :trackId")
    suspend fun findActiveSource(trackId: TrackId): TrackActiveSourceEntity?

    @Query("DELETE FROM track_active_source WHERE trackId = :trackId")
    suspend fun clearActiveSource(trackId: TrackId)
}
