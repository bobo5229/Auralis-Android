package com.bobo.auralis.mobile.library.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.bobo.auralis.mobile.library.db.entity.AlbumEntity
import com.bobo.auralis.mobile.library.db.entity.TrackEntity
import com.bobo.auralis.mobile.library.model.AlbumId
import com.bobo.auralis.mobile.library.model.ArtistId
import com.bobo.auralis.mobile.library.model.ParseState
import com.bobo.auralis.mobile.library.model.PlayabilityState
import com.bobo.auralis.mobile.library.model.SourceAvailabilityState
import com.bobo.auralis.mobile.library.model.TrackId
import com.bobo.auralis.mobile.library.model.TrackSourceId
import kotlinx.coroutines.flow.Flow

data class AlbumArtistNameRow(
    val albumId: AlbumId,
    val artistId: ArtistId,
    val position: Int,
    val displayName: String,
)

data class TrackArtistNameRow(
    val trackId: TrackId,
    val artistId: ArtistId,
    val position: Int,
    val displayName: String,
)

data class ActiveTrackSourceRow(
    val trackId: TrackId,
    val sourceId: TrackSourceId,
    val fileName: String,
    val durationMs: Long?,
    val availabilityState: SourceAvailabilityState,
    val parseState: ParseState,
    val playabilityState: PlayabilityState,
    val uri: String,
    val relativePath: String,
    val modifiedMs: Long,
    val sizeBytes: Long,
    val treeUri: String?,
    val rootLastSuccessfulScanAt: Long?,
    val hasEmbeddedArtwork: Boolean?,
)

/** Read-only reactive projections for product library pages. */
@Dao
interface LibraryBrowserDao {
    @Query("SELECT * FROM album ORDER BY title COLLATE NOCASE ASC, date ASC, albumId ASC")
    fun observeAlbums(): Flow<List<AlbumEntity>>

    @Query(
        """
        SELECT * FROM track
        ORDER BY albumId ASC, discNumber ASC, trackNumber ASC, title COLLATE NOCASE ASC, trackId ASC
        """,
    )
    fun observeTracks(): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT aa.albumId, aa.artistId, aa.position, artist.displayName
        FROM album_artist AS aa
        INNER JOIN artist ON artist.artistId = aa.artistId
        ORDER BY aa.albumId ASC, aa.position ASC
        """,
    )
    fun observeAlbumArtists(): Flow<List<AlbumArtistNameRow>>

    @Query(
        """
        SELECT ta.trackId, ta.artistId, ta.position, artist.displayName
        FROM track_artist AS ta
        INNER JOIN artist ON artist.artistId = ta.artistId
        ORDER BY ta.trackId ASC, ta.position ASC
        """,
    )
    fun observeTrackArtists(): Flow<List<TrackArtistNameRow>>

    @Query(
        """
        SELECT active.trackId, source.sourceId, source.fileName, source.durationMs,
               source.availabilityState, source.parseState, source.playabilityState,
               source.uri, source.relativePath, source.modifiedMs, source.sizeBytes,
               root.treeUri, root.lastSuccessfulScanAt AS rootLastSuccessfulScanAt,
               metadata.hasEmbeddedArtwork
        FROM track_active_source AS active
        INNER JOIN track_source AS source ON source.sourceId = active.sourceId
        LEFT JOIN library_root AS root ON root.rootId = source.rootId
        LEFT JOIN source_metadata AS metadata ON metadata.sourceId = source.sourceId
        """,
    )
    fun observeActiveSources(): Flow<List<ActiveTrackSourceRow>>
}
