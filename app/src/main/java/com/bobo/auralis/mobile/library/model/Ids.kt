package com.bobo.auralis.mobile.library.model

import java.util.UUID

/**
 * Stable Auralis-generated entity identities.
 *
 * Frozen by `docs/phase3a/00_OVERVIEW.md` §2: every database entity uses an
 * Auralis-generated UUID as its own primary key, stored as TEXT.
 *
 * These are deliberately separate types from the identity keys (`TrackKey`,
 * `AlbumKey`, ...). A primary key must never be an identity key, because identity
 * algorithms can gain a V2: if `TrackKeyV1` were also the primary key, upgrading
 * the identity rules would force a migration of the whole library plus every
 * playlist and history reference. With a stable UUID only the key is recomputed.
 *
 * They are value classes so the type system keeps a track id from being passed
 * where a source id is expected, at no runtime cost.
 */
@JvmInline
value class TrackId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        /** A fresh identity for a newly created logical track. */
        fun random(): TrackId = TrackId(UUID.randomUUID())
    }
}

/** Identity of one physical audio source Auralis has discovered. */
@JvmInline
value class TrackSourceId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        fun random(): TrackSourceId = TrackSourceId(UUID.randomUUID())
    }
}

/** Identity of one album release. */
@JvmInline
value class AlbumId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        fun random(): AlbumId = AlbumId(UUID.randomUUID())
    }
}

/**
 * Identity of one artist.
 *
 * Artist and Album Artist share this type and one table, but their relations stay
 * independent (`TrackArtistCrossRef` vs `AlbumArtistCrossRef`), so no fallback can
 * ever happen between them. §42: the id is looked up by `ArtistKey` and created
 * randomly on first sight, so it is never a hash of the name and stays stable if
 * the identity algorithm changes later.
 */
@JvmInline
value class ArtistId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        fun random(): ArtistId = ArtistId(UUID.randomUUID())
    }
}

/** Identity of one genre. */
@JvmInline
value class GenreId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        fun random(): GenreId = GenreId(UUID.randomUUID())
    }
}

/** Identity of one user-selected SAF library root. */
@JvmInline
value class LibraryRootId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        fun random(): LibraryRootId = LibraryRootId(UUID.randomUUID())
    }
}
