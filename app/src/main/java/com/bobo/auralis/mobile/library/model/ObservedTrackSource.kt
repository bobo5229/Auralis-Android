package com.bobo.auralis.mobile.library.model

import android.net.Uri
import com.bobo.auralis.mobile.library.identity.AlbumIdentity
import com.bobo.auralis.mobile.library.identity.AlbumKey
import com.bobo.auralis.mobile.library.identity.DerivedIdentity
import com.bobo.auralis.mobile.library.identity.TrackIdentity
import com.bobo.auralis.mobile.library.identity.TrackKey
import com.bobo.auralis.mobile.library.identity.TrackKeyStrength
import com.bobo.auralis.mobile.library.metadata.AuralisMetadata
import com.bobo.auralis.mobile.library.saf.SafDocumentKey
import com.bobo.auralis.mobile.library.saf.SafPath
import com.bobo.auralis.mobile.library.scan.AudioFormat

/**
 * Which configured library root produced an observation.
 *
 * The library layer needs the root's identity and its addition-order priority
 * while reconciling (§27 orders duplicate candidates by root priority), without
 * a database lookup. Phase 3 keeps that in the observation rather than in a Room
 * entity.
 *
 * [priority] is the persistent `LibraryRootEntity.priority` from §20, not the
 * current list index: recomputing it per scan would change the ordering of every
 * remaining root as soon as one root is deleted.
 *
 * The tree URI is kept as a plain string so this value stays comparable and
 * hashable without reaching for `android.net.Uri`. The persistent `LibraryRootId`
 * UUID arrives with `LibraryRootEntity` in Step D.
 */
data class RootReference(
    val treeUri: String,
    val priority: Int,
)

/**
 * One physical audio source observed by a scan.
 *
 * Frozen by `docs/phase3a/02_SOURCE_MODEL.md` §45. The scan layer hands this to
 * reconciliation instead of a Room entity, so scanner, metadata engine, identity
 * calculation and persistence stay separate concerns.
 *
 * This is an observation, not a stored source: it carries no `sourceId`, no
 * `trackId` and no states, because those only exist once reconciliation has run
 * against the database.
 *
 * Note that [uri] is an `android.net.Uri` exactly as §45 specifies, so this
 * particular type cannot be built in a plain JVM unit test. The identity rules it
 * feeds stay pure Kotlin and are covered by JVM tests (§57); observation plumbing
 * is verified on device in Step K.
 */
data class ObservedTrackSource(
    val documentKey: SafDocumentKey,
    val root: RootReference,
    val uri: Uri,
    val relativePath: SafPath,
    val fileName: String,
    val sizeBytes: Long,
    val modifiedMs: Long,
    val format: AudioFormat,
    val metadata: AuralisMetadata,
)

/**
 * An observation plus the identity derived from it.
 *
 * Frozen by `docs/phase3a/02_SOURCE_MODEL.md` §45. Produced by the identity
 * layer, this is what reconciliation consumes: the physical facts about the file
 * together with the semantic keys used to decide whether it belongs to an
 * existing logical Track.
 */
data class ResolvedObservation(
    val observation: ObservedTrackSource,
    val albumIdentity: AlbumIdentity?,
    val albumKey: AlbumKey?,
    val trackIdentity: TrackIdentity,
    val trackKey: TrackKey,
    val trackKeyStrength: TrackKeyStrength,
) {
    companion object {
        /** Combines a scan observation with the identity already derived from it. */
        fun of(observation: ObservedTrackSource, identity: DerivedIdentity): ResolvedObservation =
            ResolvedObservation(
                observation = observation,
                albumIdentity = identity.albumIdentity,
                albumKey = identity.albumKey,
                trackIdentity = identity.trackIdentity,
                trackKey = identity.trackKey,
                trackKeyStrength = identity.trackKeyStrength,
            )
    }
}
