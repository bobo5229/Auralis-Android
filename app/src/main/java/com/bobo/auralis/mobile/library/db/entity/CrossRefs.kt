package com.bobo.auralis.mobile.library.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.bobo.auralis.mobile.library.model.AlbumId
import com.bobo.auralis.mobile.library.model.ArtistId
import com.bobo.auralis.mobile.library.model.GenreId
import com.bobo.auralis.mobile.library.model.TrackId

/**
 * Which artists are credited on a track, in credit order.
 *
 * Frozen by `docs/phase3a/03_ROOM_SCHEMA.md` §15. The primary key is
 * `(trackId, position)` rather than `(trackId, artistId)` precisely so the credit
 * order survives; `artistId` carries its own index for the reverse lookup.
 */
@Entity(
    tableName = "track_artist",
    primaryKeys = ["trackId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["trackId"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ArtistEntity::class,
            parentColumns = ["artistId"],
            childColumns = ["artistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["artistId"])],
)
data class TrackArtistCrossRef(
    val trackId: TrackId,
    val artistId: ArtistId,
    val position: Int,
)

/**
 * Which artists an album belongs to, in credit order.
 *
 * Frozen by `docs/phase3a/03_ROOM_SCHEMA.md` §16. These relations come from album
 * identity and source metadata only; they are never derived from track artists.
 * When sources under one album disagree about the album artist they already
 * produce different `AlbumKey` values, so they never end up aggregated by mistake.
 */
@Entity(
    tableName = "album_artist",
    primaryKeys = ["albumId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["albumId"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ArtistEntity::class,
            parentColumns = ["artistId"],
            childColumns = ["artistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["artistId"])],
)
data class AlbumArtistCrossRef(
    val albumId: AlbumId,
    val artistId: ArtistId,
    val position: Int,
)

/**
 * Which genres a track carries, in source order.
 *
 * Frozen by `docs/phase3a/03_ROOM_SCHEMA.md` §17. The relation follows the active
 * source, and genre never participates in identity.
 */
@Entity(
    tableName = "track_genre",
    primaryKeys = ["trackId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["trackId"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GenreEntity::class,
            parentColumns = ["genreId"],
            childColumns = ["genreId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["genreId"])],
)
data class TrackGenreCrossRef(
    val trackId: TrackId,
    val genreId: GenreId,
    val position: Int,
)
