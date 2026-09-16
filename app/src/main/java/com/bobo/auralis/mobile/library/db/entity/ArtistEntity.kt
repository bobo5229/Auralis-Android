package com.bobo.auralis.mobile.library.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bobo.auralis.mobile.library.model.ArtistId

/**
 * One artist.
 *
 * Frozen by `docs/phase3a/03_ROOM_SCHEMA.md` §19 and §6. Artist and Album Artist
 * share this table but keep separate relations ([TrackArtistCrossRef] and
 * [AlbumArtistCrossRef]), so neither can ever fall back to the other — which is
 * cheaper and clearer than two nearly identical tables.
 *
 * `artistKey` is the canonicalized name while `artistId` is a random UUID looked
 * up by that key (§42), so a future identity algorithm change stays migratable.
 *
 * v1 adds no `sortName`, alias, MusicBrainz id, external id or search-normalized
 * name.
 */
@Entity(
    tableName = "artist",
    indices = [Index(value = ["artistKeyVersion", "artistKey"], unique = true)],
)
data class ArtistEntity(
    @PrimaryKey val artistId: ArtistId,
    val artistKeyVersion: Int,
    val artistKey: String,
    val displayName: String,
)
