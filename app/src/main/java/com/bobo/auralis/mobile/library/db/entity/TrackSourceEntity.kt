package com.bobo.auralis.mobile.library.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bobo.auralis.mobile.library.model.LibraryRootId
import com.bobo.auralis.mobile.library.model.ParseState
import com.bobo.auralis.mobile.library.model.PlayabilityState
import com.bobo.auralis.mobile.library.model.SourceAvailabilityState
import com.bobo.auralis.mobile.library.model.TrackId
import com.bobo.auralis.mobile.library.model.TrackSourceId
import com.bobo.auralis.mobile.library.scan.AudioFormat

/**
 * One physical audio source Auralis has seen — currently or at some point in the
 * past.
 *
 * Frozen by `docs/phase3a/03_ROOM_SCHEMA.md` §12.
 *
 * `UNIQUE(provider, documentId)` is the physical continuity key: the same
 * physical document reached through two overlapping tree roots is still one
 * source. It is what keeps a rename or move from creating a new track as long as
 * the provider keeps reporting the same document id.
 *
 * [rootId] is nullable and its foreign key is `SET NULL` rather than `CASCADE`.
 * That is the schema-level consequence of §21: when a user removes a root, its
 * sources must survive and become detached or missing — they must not be deleted
 * along with the root configuration, because a document covered by another
 * configured root is still present. §12 lists `rootId` without stating
 * nullability, so this is the reading §21 forces.
 *
 * Note that `uri` and `relativePath` are stored as text: they are current
 * locations, never identity, and the domain model keeps the typed
 * `android.net.Uri` / `SafPath` at the observation boundary.
 */
@Entity(
    tableName = "track_source",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["trackId"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LibraryRootEntity::class,
            parentColumns = ["rootId"],
            childColumns = ["rootId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["provider", "documentId"], unique = true),
        Index(value = ["trackId"]),
        Index(value = ["rootId"]),
        Index(value = ["lastSeenScanId"]),
    ],
)
data class TrackSourceEntity(
    @PrimaryKey val sourceId: TrackSourceId,
    val trackId: TrackId,

    val provider: String,
    val documentId: String,

    val rootId: LibraryRootId?,
    val uri: String,
    val relativePath: String,
    val fileName: String,

    val format: AudioFormat,
    val mimeType: String,
    val sizeBytes: Long,
    val modifiedMs: Long,

    /** Audio properties are unknown until the file parses successfully. */
    val durationMs: Long?,
    val bitrateKbps: Int?,
    val sampleRateHz: Int?,

    val availabilityState: SourceAvailabilityState,
    val parseState: ParseState,
    val playabilityState: PlayabilityState,

    /**
     * Scan session that last observed this source.
     *
     * §46 defines the scan session but not the concrete id type, so this stays a
     * monotonic session number for now; Step G owns `ScanSession`.
     */
    val lastSeenScanId: Long?,
    val lastSeenAt: Long?,

    val failureStage: String?,
    val failureCode: String?,
    val failureMessage: String?,
)
