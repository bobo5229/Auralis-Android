package com.bobo.auralis.mobile.library.cache

import com.bobo.auralis.mobile.library.db.entity.SourceMetadataEntity
import com.bobo.auralis.mobile.library.db.entity.TrackSourceEntity
import com.bobo.auralis.mobile.library.identity.AlbumIdentity
import com.bobo.auralis.mobile.library.identity.AlbumKey
import com.bobo.auralis.mobile.library.identity.TrackIdentity
import com.bobo.auralis.mobile.library.identity.TrackKey
import com.bobo.auralis.mobile.library.metadata.AuralisMetadata
import com.bobo.auralis.mobile.library.model.ObservedTrackSource
import com.bobo.auralis.mobile.library.model.ParseState
import com.bobo.auralis.mobile.library.model.ResolvedObservation
import com.bobo.auralis.mobile.library.model.RootReference
import com.bobo.auralis.mobile.library.saf.SafFile
import com.bobo.auralis.mobile.library.scan.AudioFormat

/**
 * Result of checking a discovered file against persistent metadata extraction cache.
 *
 * Implements `docs/PHASE3B.md` §5 (Hit / Stale / Miss).
 */
sealed interface CacheClassification {
    /**
     * File has not changed since last successful extraction.
     * Can hydrate directly without opening the physical file or calling TagLib JNI.
     */
    data class Hit(
        val source: TrackSourceEntity,
        val metadata: SourceMetadataEntity,
    ) : CacheClassification

    /**
     * Physical file was previously seen, but size or modified timestamp changed.
     * Requires fresh TagLib extraction.
     */
    data class Stale(
        val existingSource: TrackSourceEntity,
    ) : CacheClassification

    /**
     * New file never seen before or previously unparsed.
     * Requires fresh TagLib extraction.
     */
    data object Miss : CacheClassification
}

/**
 * Manages metadata extraction caching based on [TrackSourceEntity] and [SourceMetadataEntity].
 *
 * Rules from `docs/PHASE3B.md` §5:
 * 1. Physical source continuity is matched by `(provider, documentId)`.
 * 2. Source change signal uses `(modifiedMs, sizeBytes)`.
 * 3. Cache HIT avoids all file IO and TagLib extraction.
 */
object SourceMetadataCache {

    /**
     * Classifies a discovered file against an existing source record and its metadata snapshot.
     */
    fun classify(
        fileSize: Long,
        fileModifiedMs: Long,
        existingSource: TrackSourceEntity?,
        cachedMetadata: SourceMetadataEntity?,
    ): CacheClassification {
        if (existingSource == null || cachedMetadata == null) {
            return CacheClassification.Miss
        }

        // Must have parsed successfully previously to be eligible for HIT
        if (existingSource.parseState != ParseState.PARSED) {
            return CacheClassification.Stale(existingSource)
        }

        // Change signal: modifiedMs and sizeBytes
        val isUnchanged = existingSource.modifiedMs == fileModifiedMs &&
            existingSource.sizeBytes == fileSize

        return if (isUnchanged) {
            CacheClassification.Hit(existingSource, cachedMetadata)
        } else {
            CacheClassification.Stale(existingSource)
        }
    }

    /**
     * Overload taking [SafFile].
     */
    fun classify(
        file: SafFile,
        existingSource: TrackSourceEntity?,
        cachedMetadata: SourceMetadataEntity?,
    ): CacheClassification = classify(
        fileSize = file.size,
        fileModifiedMs = file.modifiedMs,
        existingSource = existingSource,
        cachedMetadata = cachedMetadata,
    )

    /**
     * Hydrates a [ResolvedObservation] from a cache HIT without calling TagLib JNI or reading the file.
     */
    fun hydrate(
        hit: CacheClassification.Hit,
        file: SafFile,
        rootPriority: Int,
        format: AudioFormat,
    ): ResolvedObservation = hydrate(
        hit = hit,
        documentKey = file.documentKey,
        rootTreeUri = file.root.uri.toString(),
        rootPriority = rootPriority,
        uri = file.uri,
        relativePath = file.path,
        fileName = file.path.name.orEmpty(),
        sizeBytes = file.size,
        modifiedMs = file.modifiedMs,
        format = format,
    )

    /**
     * Core hydration taking explicit fields.
     */
    fun hydrate(
        hit: CacheClassification.Hit,
        documentKey: com.bobo.auralis.mobile.library.saf.SafDocumentKey,
        rootTreeUri: String,
        rootPriority: Int,
        uri: android.net.Uri?,
        relativePath: com.bobo.auralis.mobile.library.saf.SafPath,
        fileName: String,
        sizeBytes: Long,
        modifiedMs: Long,
        format: AudioFormat,
    ): ResolvedObservation {
        val meta = hit.metadata

        val auralisMetadata = AuralisMetadata(
            title = meta.title,
            artists = meta.artists,
            albumArtists = meta.albumArtists,
            album = meta.albumTitle,
            genres = meta.genres,
            date = meta.date,
            trackNumber = meta.trackNumber,
            trackTotal = meta.trackTotal,
            discNumber = meta.discNumber,
            discTotal = meta.discTotal,
            embeddedLyrics = null,
            hasEmbeddedArtwork = meta.hasEmbeddedArtwork,
            artworkBytes = 0,
            artworkData = null,
            durationMs = meta.durationMs,
            bitrateKbps = meta.bitrateKbps,
            sampleRateHz = meta.sampleRateHz,
            mimeType = meta.mimeType,
            rawMetadata = null,
        )

        val rootRef = RootReference(
            treeUri = rootTreeUri,
            priority = rootPriority,
        )

        val observation = ObservedTrackSource(
            documentKey = documentKey,
            root = rootRef,
            uri = uri ?: android.net.Uri.EMPTY,
            relativePath = relativePath,
            fileName = fileName,
            sizeBytes = sizeBytes,
            modifiedMs = modifiedMs,
            format = format,
            metadata = auralisMetadata,
        )

        val albumIdentity = if (!meta.albumTitle.isNullOrBlank()) {
            AlbumIdentity(
                albumTitle = meta.albumTitle,
                albumArtists = meta.albumArtists,
                date = meta.date,
            )
        } else {
            null
        }

        val albumKey = if (meta.albumKeyVersion != null && meta.albumKeyHash != null) {
            AlbumKey(version = meta.albumKeyVersion, hash = meta.albumKeyHash)
        } else {
            null
        }

        val trackIdentity = TrackIdentity(
            albumIdentity = albumIdentity,
            title = meta.title ?: fileName,
            trackArtists = meta.artists,
            discNumber = meta.discNumber,
            trackNumber = meta.trackNumber,
        )

        val trackKey = TrackKey(
            version = meta.trackKeyVersion,
            hash = meta.trackKeyHash,
        )

        return ResolvedObservation(
            observation = observation,
            albumIdentity = albumIdentity,
            albumKey = albumKey,
            trackIdentity = trackIdentity,
            trackKey = trackKey,
            trackKeyStrength = meta.trackKeyStrength,
        )
    }
}
