package com.bobo.auralis.mobile.library.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bobo.auralis.mobile.library.model.AlbumId

/**
 * One album release.
 *
 * Frozen by `docs/phase3a/03_ROOM_SCHEMA.md` §18. Identity is album title plus
 * album artist set plus date; album artists live in [AlbumArtistCrossRef], never
 * as a joined string column here.
 *
 * Deliberately absent as authoritative data: `coverPath`, `representativeTrackId`,
 * `trackCount` and `duration`. Those are derivable or belong to a later cache or
 * view, and album artwork caching must key off `albumId` — never off the album
 * title.
 */
@Entity(
    tableName = "album",
    indices = [Index(value = ["albumKeyVersion", "albumKeyHash"], unique = true)],
)
data class AlbumEntity(
    @PrimaryKey val albumId: AlbumId,
    val albumKeyVersion: Int,
    val albumKeyHash: String,
    val title: String,
    /** `YYYY-MM-DD`, or null when the release date is unknown (§5.3). */
    val date: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
