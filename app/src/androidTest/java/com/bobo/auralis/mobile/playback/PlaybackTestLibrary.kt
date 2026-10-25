package com.bobo.auralis.mobile.playback

import android.net.Uri
import com.bobo.auralis.mobile.library.db.entity.LibraryRootEntity
import com.bobo.auralis.mobile.library.identity.IdentityDerivation
import com.bobo.auralis.mobile.library.metadata.AuralisMetadata
import com.bobo.auralis.mobile.library.model.ObservedTrackSource
import com.bobo.auralis.mobile.library.model.ResolvedObservation
import com.bobo.auralis.mobile.library.model.RootReference
import com.bobo.auralis.mobile.library.saf.SafComponents
import com.bobo.auralis.mobile.library.saf.SafDocumentKey
import com.bobo.auralis.mobile.library.saf.SafPath
import com.bobo.auralis.mobile.library.saf.SafRoot
import com.bobo.auralis.mobile.library.scan.AudioFormat

internal fun playbackObservation(
    documentId: String,
    root: LibraryRootEntity? = null,
    album: String? = "Album",
): ResolvedObservation {
    val metadata = AuralisMetadata(
        title = "Song", artists = listOf("Artist A", "Artist B"),
        albumArtists = listOf("Album Artist"), album = album, genres = listOf("Rock", "Pop"),
        date = "2024-01-01", trackNumber = 3, trackTotal = 10, discNumber = 2, discTotal = 2,
        embeddedLyrics = null, hasEmbeddedArtwork = false, artworkBytes = 0,
        durationMs = 180_000, bitrateKbps = 320, sampleRateHz = 44_100, mimeType = "audio/flac",
    )
    val tree = Uri.parse(root?.treeUri ?: "content://test/tree/primary%3AMusic")
    val observation = ObservedTrackSource(
        documentKey = SafDocumentKey(tree.authority!!, documentId),
        root = RootReference(tree.toString(), root?.priority ?: 0),
        uri = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, documentId),
        relativePath = SafPath(SafRoot.Volume("primary"), SafComponents.parseUnix(documentId.substringAfter(':'))),
        fileName = "song.flac", sizeBytes = 100, modifiedMs = 1, format = AudioFormat.FLAC, metadata = metadata,
    )
    return ResolvedObservation.of(observation, IdentityDerivation.derive(metadata))
}
