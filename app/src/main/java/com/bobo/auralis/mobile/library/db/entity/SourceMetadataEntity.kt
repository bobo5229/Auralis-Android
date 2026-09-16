package com.bobo.auralis.mobile.library.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bobo.auralis.mobile.library.identity.TrackKeyStrength
import com.bobo.auralis.mobile.library.model.TrackSourceId

/**
 * The metadata snapshot one physical source last parsed successfully.
 *
 * Frozen by `docs/phase3a/03_ROOM_SCHEMA.md` §13. Every source keeps its own copy.
 * Without it, promoting a backup duplicate after the active source disappears
 * would have to re-read the file, because the logical track only ever holds the
 * active source's values.
 *
 * `artists`, `albumArtists` and `genres` are stored as one JSON column each —
 * named `artistsSerialized` and friends exactly as §13 specifies, and encoded by
 * the converter §52 allows. That is acceptable here precisely because this table
 * is a per-source cache; the graph the library browses by is the normalized
 * relation tables, so no browsing query ever touches these columns.
 *
 * `rawMetadata` never enters the database: it exists for extraction diagnostics
 * only. Artwork bytes never enter it either — only the boolean fact that embedded
 * artwork exists, which is all §53 requires the Phase 3 schema to express.
 */
@Entity(
    tableName = "source_metadata",
    foreignKeys = [
        ForeignKey(
            entity = TrackSourceEntity::class,
            parentColumns = ["sourceId"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["trackKeyVersion", "trackKeyHash"]),
        Index(value = ["albumKeyVersion", "albumKeyHash"]),
    ],
)
data class SourceMetadataEntity(
    @PrimaryKey val sourceId: TrackSourceId,

    val title: String?,
    val albumTitle: String?,
    /** `YYYY-MM-DD`, never a database timestamp (§51). */
    val date: String?,

    val trackNumber: Int?,
    val trackTotal: Int?,
    val discNumber: Int?,
    val discTotal: Int?,

    @ColumnInfo(name = "artistsSerialized") val artists: List<String>,
    @ColumnInfo(name = "albumArtistsSerialized") val albumArtists: List<String>,
    @ColumnInfo(name = "genresSerialized") val genres: List<String>,

    val durationMs: Long,
    val bitrateKbps: Int,
    val sampleRateHz: Int,
    val mimeType: String,

    /** §53: the Phase 3 schema only needs to express that embedded artwork exists. */
    val hasEmbeddedArtwork: Boolean,

    val trackKeyVersion: Int,
    val trackKeyHash: String,
    val trackKeyStrength: TrackKeyStrength,

    /** Null when the source has no album title and therefore no album identity (§5.4). */
    val albumKeyVersion: Int?,
    val albumKeyHash: String?,
)
