package com.bobo.auralis.mobile.library.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bobo.auralis.mobile.library.db.entity.AlbumArtistCrossRef
import com.bobo.auralis.mobile.library.db.entity.AlbumEntity
import com.bobo.auralis.mobile.library.db.entity.ArtistEntity
import com.bobo.auralis.mobile.library.db.entity.GenreEntity
import com.bobo.auralis.mobile.library.db.entity.TrackArtistCrossRef
import com.bobo.auralis.mobile.library.db.entity.TrackGenreCrossRef
import com.bobo.auralis.mobile.library.model.AlbumId
import com.bobo.auralis.mobile.library.model.ArtistId
import com.bobo.auralis.mobile.library.model.GenreId
import com.bobo.auralis.mobile.library.model.TrackId

/**
 * Data access for the logical graph: albums, artists, genres and their relations.
 *
 * These are the tables a library browser queries, which is exactly why artist and
 * genre never live as serialized columns on `track` (§52). The serialized lists on
 * a source snapshot are a per-source cache and are never used to browse.
 *
 * The `clear*` plus `insert*` pairs are not atomic on their own. Callers must run
 * them inside one transaction, because §40 requires an active-source change to
 * update the pointer, the track metadata and every relation together — half an
 * update must never be visible.
 */
@Dao
interface GraphDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAlbum(album: AlbumEntity)

    @Update
    suspend fun updateAlbum(album: AlbumEntity)

    @Query("SELECT * FROM album WHERE albumId = :albumId")
    suspend fun findAlbumById(albumId: AlbumId): AlbumEntity?

    @Query("SELECT * FROM album WHERE albumKeyVersion = :version AND albumKeyHash = :hash")
    suspend fun findAlbumByKey(version: Int, hash: String): AlbumEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertArtist(artist: ArtistEntity)

    @Query("SELECT * FROM artist WHERE artistId = :artistId")
    suspend fun findArtistById(artistId: ArtistId): ArtistEntity?

    @Query("SELECT * FROM artist WHERE artistKeyVersion = :version AND artistKey = :key")
    suspend fun findArtistByKey(version: Int, key: String): ArtistEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGenre(genre: GenreEntity)

    @Query("SELECT * FROM genre WHERE genreId = :genreId")
    suspend fun findGenreById(genreId: GenreId): GenreEntity?

    @Query("SELECT * FROM genre WHERE genreKeyVersion = :version AND genreKey = :key")
    suspend fun findGenreByKey(version: Int, key: String): GenreEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTrackArtists(refs: List<TrackArtistCrossRef>)

    @Query("DELETE FROM track_artist WHERE trackId = :trackId")
    suspend fun clearTrackArtists(trackId: TrackId)

    @Query("SELECT * FROM track_artist WHERE trackId = :trackId ORDER BY position ASC")
    suspend fun trackArtists(trackId: TrackId): List<TrackArtistCrossRef>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAlbumArtists(refs: List<AlbumArtistCrossRef>)

    @Query("DELETE FROM album_artist WHERE albumId = :albumId")
    suspend fun clearAlbumArtists(albumId: AlbumId)

    @Query("SELECT * FROM album_artist WHERE albumId = :albumId ORDER BY position ASC")
    suspend fun albumArtists(albumId: AlbumId): List<AlbumArtistCrossRef>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTrackGenres(refs: List<TrackGenreCrossRef>)

    @Query("DELETE FROM track_genre WHERE trackId = :trackId")
    suspend fun clearTrackGenres(trackId: TrackId)

    @Query("SELECT * FROM track_genre WHERE trackId = :trackId ORDER BY position ASC")
    suspend fun trackGenres(trackId: TrackId): List<TrackGenreCrossRef>
}
