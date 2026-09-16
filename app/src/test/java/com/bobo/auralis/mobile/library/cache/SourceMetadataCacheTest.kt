package com.bobo.auralis.mobile.library.cache

import com.bobo.auralis.mobile.library.db.entity.SourceMetadataEntity
import com.bobo.auralis.mobile.library.db.entity.TrackSourceEntity
import com.bobo.auralis.mobile.library.identity.TrackKeyStrength
import com.bobo.auralis.mobile.library.model.ParseState
import com.bobo.auralis.mobile.library.model.PlayabilityState
import com.bobo.auralis.mobile.library.model.SourceAvailabilityState
import com.bobo.auralis.mobile.library.model.TrackId
import com.bobo.auralis.mobile.library.model.TrackSourceId
import com.bobo.auralis.mobile.library.saf.SafComponents
import com.bobo.auralis.mobile.library.saf.SafDocumentKey
import com.bobo.auralis.mobile.library.saf.SafPath
import com.bobo.auralis.mobile.library.saf.SafRoot
import com.bobo.auralis.mobile.library.scan.AudioFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceMetadataCacheTest {

    private val sampleSourceId = TrackSourceId.random()
    private val sampleTrackId = TrackId.random()

    private fun sampleSourceEntity(
        size: Long = 1000L,
        modifiedMs: Long = 2000L,
        parseState: ParseState = ParseState.PARSED,
    ): TrackSourceEntity = TrackSourceEntity(
        sourceId = sampleSourceId,
        trackId = sampleTrackId,
        provider = "com.android.externalstorage.documents",
        documentId = "doc_1",
        rootId = null,
        uri = "content://test/song.flac",
        relativePath = "song.flac",
        fileName = "song.flac",
        format = AudioFormat.FLAC,
        mimeType = "audio/flac",
        sizeBytes = size,
        modifiedMs = modifiedMs,
        durationMs = 180000L,
        bitrateKbps = 1000,
        sampleRateHz = 44100,
        availabilityState = SourceAvailabilityState.AVAILABLE,
        parseState = parseState,
        playabilityState = PlayabilityState.UNKNOWN,
        lastSeenScanId = 1L,
        lastSeenAt = 1000L,
        failureStage = null,
        failureCode = null,
        failureMessage = null,
    )

    private fun sampleMetadataEntity(): SourceMetadataEntity = SourceMetadataEntity(
        sourceId = sampleSourceId,
        title = "Track Title",
        albumTitle = "Album Title",
        date = "2024-01-01",
        trackNumber = 1,
        trackTotal = 10,
        discNumber = 1,
        discTotal = 1,
        artists = listOf("Artist 1"),
        albumArtists = listOf("Album Artist 1"),
        genres = listOf("Pop"),
        durationMs = 180000L,
        bitrateKbps = 1000,
        sampleRateHz = 44100,
        mimeType = "audio/flac",
        hasEmbeddedArtwork = true,
        trackKeyVersion = 1,
        trackKeyHash = "hash_track",
        trackKeyStrength = TrackKeyStrength.STRONG,
        albumKeyVersion = 1,
        albumKeyHash = "hash_album",
    )

    @Test
    fun `unchanged source produces Cache HIT`() {
        val source = sampleSourceEntity(size = 1000L, modifiedMs = 2000L)
        val meta = sampleMetadataEntity()

        val classification = SourceMetadataCache.classify(
            fileSize = 1000L,
            fileModifiedMs = 2000L,
            existingSource = source,
            cachedMetadata = meta,
        )
        assertTrue(classification is CacheClassification.Hit)
        val hit = classification as CacheClassification.Hit
        assertEquals(source, hit.source)
        assertEquals(meta, hit.metadata)
    }

    @Test
    fun `modified timestamp change produces Cache STALE`() {
        val source = sampleSourceEntity(size = 1000L, modifiedMs = 2000L)
        val meta = sampleMetadataEntity()

        val classification = SourceMetadataCache.classify(
            fileSize = 1000L,
            fileModifiedMs = 3000L,
            existingSource = source,
            cachedMetadata = meta,
        )
        assertTrue(classification is CacheClassification.Stale)
    }

    @Test
    fun `size change produces Cache STALE`() {
        val source = sampleSourceEntity(size = 1000L, modifiedMs = 2000L)
        val meta = sampleMetadataEntity()

        val classification = SourceMetadataCache.classify(
            fileSize = 1005L,
            fileModifiedMs = 2000L,
            existingSource = source,
            cachedMetadata = meta,
        )
        assertTrue(classification is CacheClassification.Stale)
    }

    @Test
    fun `unparsed or failed source produces Cache STALE even if stats match`() {
        val source = sampleSourceEntity(size = 1000L, modifiedMs = 2000L, parseState = ParseState.FAILED)
        val meta = sampleMetadataEntity()

        val classification = SourceMetadataCache.classify(
            fileSize = 1000L,
            fileModifiedMs = 2000L,
            existingSource = source,
            cachedMetadata = meta,
        )
        assertTrue(classification is CacheClassification.Stale)
    }

    @Test
    fun `null source or null metadata produces Cache MISS`() {
        assertEquals(
            CacheClassification.Miss,
            SourceMetadataCache.classify(1000L, 2000L, null, null),
        )
        assertEquals(
            CacheClassification.Miss,
            SourceMetadataCache.classify(1000L, 2000L, sampleSourceEntity(), null),
        )
    }
}
