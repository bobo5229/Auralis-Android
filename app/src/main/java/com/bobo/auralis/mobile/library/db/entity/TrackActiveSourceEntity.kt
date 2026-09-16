package com.bobo.auralis.mobile.library.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bobo.auralis.mobile.library.model.TrackId
import com.bobo.auralis.mobile.library.model.TrackSourceId

/**
 * Which physical source currently represents a logical track.
 *
 * Frozen by `docs/phase3a/03_ROOM_SCHEMA.md` §25. This is a separate table rather
 * than a `TrackEntity.activeSourceId` column on purpose: that column would create
 * a two-way foreign key between track and source, which SQLite cannot keep
 * consistent on its own.
 *
 * The requirement `source.trackId == trackId` is not expressible as a plain
 * foreign key, so the selection/write service has to enforce it inside the same
 * transaction.
 *
 * A track with no usable source simply has no row here. That absence *is* the
 * tombstone state (§38) — there is no second place to record it and no way for
 * the two to disagree.
 */
@Entity(
    tableName = "track_active_source",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["trackId"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TrackSourceEntity::class,
            parentColumns = ["sourceId"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sourceId"], unique = true)],
)
data class TrackActiveSourceEntity(
    @PrimaryKey val trackId: TrackId,
    val sourceId: TrackSourceId,
)
