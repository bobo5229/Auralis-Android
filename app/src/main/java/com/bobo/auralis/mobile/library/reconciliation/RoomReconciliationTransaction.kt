package com.bobo.auralis.mobile.library.reconciliation

import androidx.room.withTransaction
import com.bobo.auralis.mobile.library.db.AuralisDatabase
import com.bobo.auralis.mobile.library.db.entity.AlbumArtistCrossRef
import com.bobo.auralis.mobile.library.db.entity.AlbumEntity
import com.bobo.auralis.mobile.library.db.entity.ArtistEntity
import com.bobo.auralis.mobile.library.db.entity.GenreEntity
import com.bobo.auralis.mobile.library.db.entity.SourceMetadataEntity
import com.bobo.auralis.mobile.library.db.entity.TrackActiveSourceEntity
import com.bobo.auralis.mobile.library.db.entity.TrackArtistCrossRef
import com.bobo.auralis.mobile.library.db.entity.TrackEntity
import com.bobo.auralis.mobile.library.db.entity.TrackGenreCrossRef
import com.bobo.auralis.mobile.library.db.entity.TrackSourceEntity
import com.bobo.auralis.mobile.library.identity.IdentityTextNormalizerV1
import com.bobo.auralis.mobile.library.model.AlbumId
import com.bobo.auralis.mobile.library.model.ArtistId
import com.bobo.auralis.mobile.library.model.GenreId
import com.bobo.auralis.mobile.library.model.LibraryRootId
import com.bobo.auralis.mobile.library.model.ParseState
import com.bobo.auralis.mobile.library.model.PlayabilityState
import com.bobo.auralis.mobile.library.model.ResolvedObservation
import com.bobo.auralis.mobile.library.model.SourceAvailabilityState
import com.bobo.auralis.mobile.library.model.TrackId
import com.bobo.auralis.mobile.library.model.TrackSourceId

/**
 * Executes reconciliation writes within Room database transactions.
 *
 * Frozen by `docs/phase3a/05_RECONCILIATION_TX.md` §40, §41, §42, §46, §47.
 *
 * Boundary rule (§47): IO, TagLib parsing, SAF queries MUST happen before entering this layer.
 * This class accepts resolved observations and applies DB state mutations atomically.
 */
class RoomReconciliationTransaction(
    private val database: AuralisDatabase,
) {

    /**
     * Reconciles a single [ResolvedObservation] within an atomic transaction.
     *
     * @param resolved The observation with metadata and identity already extracted outside DB transaction.
     * @param rootId The persistent LibraryRootId for the source.
     * @param scanSessionId Monotonic ID of current scan session.
     * @param timestamp Epoch ms for created/updated timestamps.
     */
    suspend fun reconcileObservation(
        resolved: ResolvedObservation,
        rootId: LibraryRootId?,
        scanSessionId: Long,
        timestamp: Long = System.currentTimeMillis(),
    ): TrackId = database.withTransaction {
        reconcileObservationInternal(
            resolved = resolved,
            rootId = rootId,
            scanSessionId = scanSessionId,
            timestamp = timestamp,
            autoRefreshActiveSource = true,
        )
    }

    private suspend fun reconcileObservationInternal(
        resolved: ResolvedObservation,
        rootId: LibraryRootId?,
        scanSessionId: Long,
        timestamp: Long,
        autoRefreshActiveSource: Boolean,
    ): TrackId {
        val sourceDao = database.sourceDao()
        val trackDao = database.trackDao()

        val obs = resolved.observation
        val meta = obs.metadata

        // 1. Look for existing physical source (provider, documentId)
        val existingPhysicalSourceEntity = sourceDao.findByDocumentKey(
            provider = obs.documentKey.provider,
            documentId = obs.documentKey.documentId,
        )

        val existingPhysicalSnapshot = existingPhysicalSourceEntity?.let { source ->
            val existingMeta = sourceDao.findMetadata(source.sourceId)
            ExistingSourceSnapshot(
                sourceId = source.sourceId,
                trackId = source.trackId,
                provider = source.provider,
                documentId = source.documentId,
                albumKeyVersion = existingMeta?.albumKeyVersion,
                albumKeyHash = existingMeta?.albumKeyHash,
                trackKeyVersion = existingMeta?.trackKeyVersion ?: 1,
                trackKeyHash = existingMeta?.trackKeyHash ?: "",
            )
        }

        // 2. Look for semantic candidate tracks if needed
        val semanticMatchingTracks = if (existingPhysicalSnapshot == null && resolved.trackKeyStrength.isStrong()) {
            trackDao.findByKey(resolved.trackKey.version, resolved.trackKey.hash).map { it.trackId }
        } else {
            emptyList()
        }

        val reconcileObservation = ReconcileObservation(
            provider = obs.documentKey.provider,
            documentId = obs.documentKey.documentId,
            rootPriority = obs.root.priority,
            relativePath = obs.relativePath.toString(),
            albumKeyVersion = resolved.albumKey?.version,
            albumKeyHash = resolved.albumKey?.hash,
            trackKey = resolved.trackKey,
            trackKeyStrength = resolved.trackKeyStrength,
        )

        // 3. Make pure reconciliation decision
        val decision = ReconciliationDecisionEngine.decide(
            observation = reconcileObservation,
            existingPhysicalSource = existingPhysicalSnapshot,
            semanticMatchingTracks = semanticMatchingTracks,
        )

        val targetTrackId: TrackId
        val targetSourceId: TrackSourceId

        when (decision) {
            is ReconcileDecision.RetainPhysicalTrack -> {
                targetTrackId = decision.trackId
                targetSourceId = decision.sourceId

                val updatedSource = existingPhysicalSourceEntity!!.copy(
                    rootId = rootId ?: existingPhysicalSourceEntity.rootId,
                    uri = obs.uri.toString(),
                    relativePath = obs.relativePath.toString(),
                    fileName = obs.fileName,
                    format = obs.format,
                    mimeType = meta.mimeType ?: existingPhysicalSourceEntity.mimeType,
                    sizeBytes = obs.sizeBytes,
                    modifiedMs = obs.modifiedMs,
                    durationMs = meta.durationMs ?: existingPhysicalSourceEntity.durationMs,
                    bitrateKbps = meta.bitrateKbps ?: existingPhysicalSourceEntity.bitrateKbps,
                    sampleRateHz = meta.sampleRateHz ?: existingPhysicalSourceEntity.sampleRateHz,
                    availabilityState = SourceAvailabilityState.AVAILABLE,
                    parseState = ParseState.PARSED,
                    lastSeenScanId = scanSessionId,
                    lastSeenAt = timestamp,
                    failureStage = null,
                    failureCode = null,
                    failureMessage = null,
                )
                sourceDao.update(updatedSource)
            }

            is ReconcileDecision.BreakAlbumBoundary -> {
                targetSourceId = decision.sourceId
                // §30: Album boundary broken. Detach from old track and create a new TrackId.
                targetTrackId = TrackId.random()

                val newTrack = TrackEntity(
                    trackId = targetTrackId,
                    trackKeyVersion = resolved.trackKey.version,
                    trackKeyHash = resolved.trackKey.hash,
                    trackKeyStrength = resolved.trackKeyStrength,
                    albumId = null,
                    title = meta.title,
                    date = meta.date,
                    trackNumber = meta.trackNumber,
                    trackTotal = meta.trackTotal,
                    discNumber = meta.discNumber,
                    discTotal = meta.discTotal,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                )
                trackDao.insert(newTrack)

                val updatedSource = existingPhysicalSourceEntity!!.copy(
                    trackId = targetTrackId,
                    rootId = rootId ?: existingPhysicalSourceEntity.rootId,
                    uri = obs.uri.toString(),
                    relativePath = obs.relativePath.toString(),
                    fileName = obs.fileName,
                    format = obs.format,
                    mimeType = meta.mimeType ?: existingPhysicalSourceEntity.mimeType,
                    sizeBytes = obs.sizeBytes,
                    modifiedMs = obs.modifiedMs,
                    durationMs = meta.durationMs ?: existingPhysicalSourceEntity.durationMs,
                    bitrateKbps = meta.bitrateKbps ?: existingPhysicalSourceEntity.bitrateKbps,
                    sampleRateHz = meta.sampleRateHz ?: existingPhysicalSourceEntity.sampleRateHz,
                    availabilityState = SourceAvailabilityState.AVAILABLE,
                    parseState = ParseState.PARSED,
                    lastSeenScanId = scanSessionId,
                    lastSeenAt = timestamp,
                    failureStage = null,
                    failureCode = null,
                    failureMessage = null,
                )
                sourceDao.update(updatedSource)

                // The old track lost a source; refresh its active source
                refreshActiveSourceForTrack(decision.oldTrackId, timestamp)
            }

            is ReconcileDecision.AttachToExistingTrack -> {
                targetTrackId = decision.existingTrackId
                targetSourceId = TrackSourceId.random()

                val newSource = TrackSourceEntity(
                    sourceId = targetSourceId,
                    trackId = targetTrackId,
                    rootId = rootId,
                    provider = obs.documentKey.provider,
                    documentId = obs.documentKey.documentId,
                    uri = obs.uri.toString(),
                    relativePath = obs.relativePath.toString(),
                    fileName = obs.fileName,
                    format = obs.format,
                    mimeType = meta.mimeType ?: "audio/unknown",
                    sizeBytes = obs.sizeBytes,
                    modifiedMs = obs.modifiedMs,
                    durationMs = meta.durationMs,
                    bitrateKbps = meta.bitrateKbps,
                    sampleRateHz = meta.sampleRateHz,
                    availabilityState = SourceAvailabilityState.AVAILABLE,
                    parseState = ParseState.PARSED,
                    playabilityState = PlayabilityState.UNKNOWN,
                    lastSeenScanId = scanSessionId,
                    lastSeenAt = timestamp,
                    failureStage = null,
                    failureCode = null,
                    failureMessage = null,
                )
                sourceDao.insert(newSource)
            }

            is ReconcileDecision.CreateNewTrack,
            is ReconcileDecision.AmbiguousStrongMatchCreateNewTrack -> {
                targetTrackId = TrackId.random()
                targetSourceId = TrackSourceId.random()

                val newTrack = TrackEntity(
                    trackId = targetTrackId,
                    trackKeyVersion = resolved.trackKey.version,
                    trackKeyHash = resolved.trackKey.hash,
                    trackKeyStrength = resolved.trackKeyStrength,
                    albumId = null,
                    title = meta.title,
                    date = meta.date,
                    trackNumber = meta.trackNumber,
                    trackTotal = meta.trackTotal,
                    discNumber = meta.discNumber,
                    discTotal = meta.discTotal,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                )
                trackDao.insert(newTrack)

                val newSource = TrackSourceEntity(
                    sourceId = targetSourceId,
                    trackId = targetTrackId,
                    rootId = rootId,
                    provider = obs.documentKey.provider,
                    documentId = obs.documentKey.documentId,
                    uri = obs.uri.toString(),
                    relativePath = obs.relativePath.toString(),
                    fileName = obs.fileName,
                    format = obs.format,
                    mimeType = meta.mimeType ?: "audio/unknown",
                    sizeBytes = obs.sizeBytes,
                    modifiedMs = obs.modifiedMs,
                    durationMs = meta.durationMs,
                    bitrateKbps = meta.bitrateKbps,
                    sampleRateHz = meta.sampleRateHz,
                    availabilityState = SourceAvailabilityState.AVAILABLE,
                    parseState = ParseState.PARSED,
                    playabilityState = PlayabilityState.UNKNOWN,
                    lastSeenScanId = scanSessionId,
                    lastSeenAt = timestamp,
                    failureStage = null,
                    failureCode = null,
                    failureMessage = null,
                )
                sourceDao.insert(newSource)
            }
        }

        // 4. Update SourceMetadata snapshot (§13)
        val snapshot = SourceMetadataEntity(
            sourceId = targetSourceId,
            title = meta.title,
            albumTitle = meta.album,
            date = meta.date,
            trackNumber = meta.trackNumber,
            trackTotal = meta.trackTotal,
            discNumber = meta.discNumber,
            discTotal = meta.discTotal,
            artists = meta.artists,
            albumArtists = meta.albumArtists,
            genres = meta.genres,
            durationMs = meta.durationMs ?: 0L,
            bitrateKbps = meta.bitrateKbps ?: 0,
            sampleRateHz = meta.sampleRateHz ?: 0,
            mimeType = meta.mimeType ?: "audio/unknown",
            hasEmbeddedArtwork = meta.hasEmbeddedArtwork,
            trackKeyVersion = resolved.trackKey.version,
            trackKeyHash = resolved.trackKey.hash,
            trackKeyStrength = resolved.trackKeyStrength,
            albumKeyVersion = resolved.albumKey?.version,
            albumKeyHash = resolved.albumKey?.hash,
        )
        sourceDao.insertMetadata(snapshot)

        // 5. Select active source and project graph if winner changed (§40)
        if (autoRefreshActiveSource) {
            refreshActiveSourceForTrack(targetTrackId, timestamp)
        }

        return targetTrackId
    }

    /**
     * Batch-reconciles multiple observations in a single atomic Room transaction.
     *
     * Implements `docs/PHASE3B.md` §6 (batch DB write, 100–500 observations per batch).
     * Reconciles sources and collects all affected tracks, refreshing their active sources
     * and projecting graph once per affected track at the end of the batch.
     */
    suspend fun reconcileObservationsBatch(
        batch: List<BatchItem>,
        scanSessionId: Long,
        timestamp: Long = System.currentTimeMillis(),
    ) = database.withTransaction {
        val affectedTracks = mutableSetOf<TrackId>()
        for (item in batch) {
            val trackId = reconcileObservationInternal(
                resolved = item.resolved,
                rootId = item.rootId,
                scanSessionId = scanSessionId,
                timestamp = timestamp,
                autoRefreshActiveSource = false,
            )
            affectedTracks += trackId
        }

        for (trackId in affectedTracks) {
            refreshActiveSourceForTrack(trackId, timestamp)
        }
    }

    data class BatchItem(
        val resolved: ResolvedObservation,
        val rootId: LibraryRootId?,
    )

    /**
     * Runs missing reconciliation for sources not seen in the completed scan session (§37, §46).
     *
     * @param scanSessionId Completed scan session ID
     * @param rootIdToReachable Map indicating whether each root was reachable and completed cleanly.
     * @param timestamp Epoch ms
     */
    suspend fun reconcileMissingSources(
        scanSessionId: Long,
        rootIdToReachable: Map<LibraryRootId, Boolean>,
        timestamp: Long = System.currentTimeMillis(),
    ) = database.withTransaction {
        val sourceDao = database.sourceDao()
        val unseenSources = sourceDao.findNotSeenInScan(scanSessionId)

        val affectedTrackIds = mutableSetOf<TrackId>()

        for (source in unseenSources) {
            val isReachable = source.rootId?.let { rootIdToReachable[it] } ?: false
            val newState = ReconciliationDecisionEngine.decideMissingState(wasRootReachable = isReachable)

            if (source.availabilityState != newState) {
                sourceDao.update(source.copy(availabilityState = newState))
                affectedTrackIds += source.trackId
            }
        }

        for (trackId in affectedTrackIds) {
            refreshActiveSourceForTrack(trackId, timestamp)
        }
    }

    /**
     * Core of §40: evaluates candidates for a track, elects winner, and updates
     * Track, TrackActiveSource, Album, and artist/genre cross refs in one transaction.
     */
    suspend fun refreshActiveSourceForTrack(
        trackId: TrackId,
        timestamp: Long = System.currentTimeMillis(),
    ) {
        val sourceDao = database.sourceDao()
        val trackDao = database.trackDao()
        val graphDao = database.graphDao()
        val rootDao = database.libraryRootDao()

        val sources = sourceDao.findByTrack(trackId)
        val currentActive = sourceDao.findActiveSource(trackId)

        // Map rootId to root priority for candidate ranking
        val rootPriorities = rootDao.getAllByPriority().associate { it.rootId to it.priority }

        val candidateList = sources.map { s ->
            CandidateSource(
                sourceId = s.sourceId,
                rootPriority = s.rootId?.let { rootPriorities[it] } ?: Int.MAX_VALUE,
                relativePath = s.relativePath,
                availabilityState = s.availabilityState,
                parseState = s.parseState,
                playabilityState = s.playabilityState,
            )
        }

        val winnerCandidate = DuplicateSourceRanking.selectActiveSource(candidateList)

        if (winnerCandidate == null) {
            // No usable active source -> track is tombstone / inactive (§38)
            sourceDao.clearActiveSource(trackId)
            return
        }

        val winnerSourceId = winnerCandidate.sourceId
        if (currentActive?.sourceId == winnerSourceId) {
            // Winner unchanged (§40): do not rebuild logical metadata
            return
        }

        // Winner changed! Atomically apply §40 steps 1-5:
        // 1. update TrackActiveSource
        sourceDao.setActiveSource(TrackActiveSourceEntity(trackId = trackId, sourceId = winnerSourceId))

        val winnerMeta = sourceDao.findMetadata(winnerSourceId) ?: return

        // 2. resolve / create Album (§41)
        val albumId = if (winnerMeta.albumKeyVersion != null && winnerMeta.albumKeyHash != null && !winnerMeta.albumTitle.isNullOrBlank()) {
            resolveOrCreateAlbum(
                albumKeyVersion = winnerMeta.albumKeyVersion,
                albumKeyHash = winnerMeta.albumKeyHash,
                albumTitle = winnerMeta.albumTitle,
                date = winnerMeta.date,
                albumArtists = winnerMeta.albumArtists,
                timestamp = timestamp,
            )
        } else {
            null
        }

        // 3. copy winner SourceMetadata scalar values into Track
        val currentTrack = trackDao.findById(trackId)
        if (currentTrack != null) {
            val updatedTrack = currentTrack.copy(
                trackKeyVersion = winnerMeta.trackKeyVersion,
                trackKeyHash = winnerMeta.trackKeyHash,
                trackKeyStrength = winnerMeta.trackKeyStrength,
                albumId = albumId,
                title = winnerMeta.title,
                date = winnerMeta.date,
                trackNumber = winnerMeta.trackNumber,
                trackTotal = winnerMeta.trackTotal,
                discNumber = winnerMeta.discNumber,
                discTotal = winnerMeta.discTotal,
                updatedAt = timestamp,
            )
            trackDao.update(updatedTrack)
        }

        // 4. rebuild TrackArtist relation (§40 step 4, §42 stable IDs)
        graphDao.clearTrackArtists(trackId)
        val artistRefs = winnerMeta.artists.mapIndexedNotNull { index, artistName ->
            val artistId = resolveOrCreateArtist(artistName)
            artistId?.let { TrackArtistCrossRef(trackId = trackId, artistId = it, position = index) }
        }
        if (artistRefs.isNotEmpty()) {
            graphDao.insertTrackArtists(artistRefs)
        }

        // 5. rebuild TrackGenre relation (§40 step 5, §42 stable IDs)
        graphDao.clearTrackGenres(trackId)
        val genreRefs = winnerMeta.genres.mapIndexedNotNull { index, genreName ->
            val genreId = resolveOrCreateGenre(genreName)
            genreId?.let { TrackGenreCrossRef(trackId = trackId, genreId = it, position = index) }
        }
        if (genreRefs.isNotEmpty()) {
            graphDao.insertTrackGenres(genreRefs)
        }
    }

    private suspend fun resolveOrCreateAlbum(
        albumKeyVersion: Int,
        albumKeyHash: String,
        albumTitle: String,
        date: String?,
        albumArtists: List<String>,
        timestamp: Long,
    ): AlbumId {
        val graphDao = database.graphDao()
        val existing = graphDao.findAlbumByKey(albumKeyVersion, albumKeyHash)
        if (existing != null) {
            return existing.albumId
        }

        val newAlbumId = AlbumId.random()
        val album = AlbumEntity(
            albumId = newAlbumId,
            albumKeyVersion = albumKeyVersion,
            albumKeyHash = albumKeyHash,
            title = albumTitle,
            date = date,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        graphDao.insertAlbum(album)

        val albumArtistRefs = albumArtists.mapIndexedNotNull { index, artistName ->
            val artistId = resolveOrCreateArtist(artistName)
            artistId?.let { AlbumArtistCrossRef(albumId = newAlbumId, artistId = it, position = index) }
        }
        if (albumArtistRefs.isNotEmpty()) {
            graphDao.insertAlbumArtists(albumArtistRefs)
        }

        return newAlbumId
    }

    private suspend fun resolveOrCreateArtist(displayName: String): ArtistId? {
        val graphDao = database.graphDao()
        val normalizedKey = IdentityTextNormalizerV1.normalize(displayName) ?: return null
        val existing = graphDao.findArtistByKey(1, normalizedKey)
        if (existing != null) {
            return existing.artistId
        }

        val newArtistId = ArtistId.random()
        val artist = ArtistEntity(
            artistId = newArtistId,
            artistKeyVersion = 1,
            artistKey = normalizedKey,
            displayName = displayName,
        )
        graphDao.insertArtist(artist)
        return newArtistId
    }

    private suspend fun resolveOrCreateGenre(displayName: String): GenreId? {
        val graphDao = database.graphDao()
        val normalizedKey = IdentityTextNormalizerV1.normalize(displayName) ?: return null
        val existing = graphDao.findGenreByKey(1, normalizedKey)
        if (existing != null) {
            return existing.genreId
        }

        val newGenreId = GenreId.random()
        val genre = GenreEntity(
            genreId = newGenreId,
            genreKeyVersion = 1,
            genreKey = normalizedKey,
            displayName = displayName,
        )
        graphDao.insertGenre(genre)
        return newGenreId
    }

    private fun com.bobo.auralis.mobile.library.identity.TrackKeyStrength.isStrong(): Boolean =
        this == com.bobo.auralis.mobile.library.identity.TrackKeyStrength.STRONG
}
