package com.bobo.auralis.mobile.library.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bobo.auralis.mobile.library.model.GenreId

/**
 * One genre.
 *
 * Frozen by `docs/phase3a/03_ROOM_SCHEMA.md` §19 and §7. Genre never takes part
 * in `TrackKey` or `AlbumKey` and never decides duplicates, so editing a genre
 * cannot turn a song into a different track.
 *
 * `displayName` keeps the canonicalized text; the original ordered list still
 * lives in the source snapshot so UI ordering is preserved.
 */
@Entity(
    tableName = "genre",
    indices = [Index(value = ["genreKeyVersion", "genreKey"], unique = true)],
)
data class GenreEntity(
    @PrimaryKey val genreId: GenreId,
    val genreKeyVersion: Int,
    val genreKey: String,
    val displayName: String,
)
