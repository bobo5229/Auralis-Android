package com.bobo.auralis.mobile.library.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bobo.auralis.mobile.library.identity.TrackKeyStrength
import com.bobo.auralis.mobile.library.model.AlbumId
import com.bobo.auralis.mobile.library.model.TrackId

/**
 * One logical track: the entry the user actually sees.
 *
 * Frozen by `docs/phase3a/03_ROOM_SCHEMA.md` §14. It holds the metadata snapshot
 * of the current active source; artist and genre relations live in their own
 * tables rather than as repeated TEXT columns.
 *
 * `trackId` is a stable UUID while `trackKeyHash` is a recomputable reconciliation
 * key, which is what lets a title edit keep the same `TrackId` (§31).
 *
 * A track without an active source keeps its row — that row is the tombstone.
 * There is deliberately no state column, because "active" is exactly "has a
 * [TrackActiveSourceEntity] row" (§38), and storing both would let them disagree.
 */
@Entity(
    tableName = "track",
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["albumId"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["trackKeyVersion", "trackKeyHash"]),
        Index(value = ["albumId"]),
    ],
)
data class TrackEntity(
    @PrimaryKey val trackId: TrackId,
    val trackKeyVersion: Int,
    val trackKeyHash: String,
    val trackKeyStrength: TrackKeyStrength,
    /** Null when the track has no album title at all (§5.4). */
    val albumId: AlbumId?,
    val title: String?,
    /** `YYYY-MM-DD` release date. Never a database timestamp (§51). */
    val date: String?,
    val trackNumber: Int?,
    val trackTotal: Int?,
    val discNumber: Int?,
    val discTotal: Int?,
    val createdAt: Long,
    val updatedAt: Long,
)
