package com.bobo.auralis.mobile.library.pipeline

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.room.withTransaction
import com.bobo.auralis.mobile.library.db.AuralisDatabase
import com.bobo.auralis.mobile.library.db.entity.LibraryRootEntity
import com.bobo.auralis.mobile.library.db.entity.RootAvailabilityState
import com.bobo.auralis.mobile.library.db.entity.TrackSourceEntity
import com.bobo.auralis.mobile.library.identity.IdentityDerivation
import com.bobo.auralis.mobile.library.metadata.MetadataExtractor
import com.bobo.auralis.mobile.library.metadata.MetadataResult
import com.bobo.auralis.mobile.library.metadata.MetadataTarget
import com.bobo.auralis.mobile.library.metadata.interpret
import com.bobo.auralis.mobile.library.model.LibraryRootId
import com.bobo.auralis.mobile.library.model.ObservedTrackSource
import com.bobo.auralis.mobile.library.model.ParseState
import com.bobo.auralis.mobile.library.model.PlayabilityState
import com.bobo.auralis.mobile.library.model.ResolvedObservation
import com.bobo.auralis.mobile.library.model.RootReference
import com.bobo.auralis.mobile.library.model.SourceAvailabilityState
import com.bobo.auralis.mobile.library.model.TrackId
import com.bobo.auralis.mobile.library.model.TrackSourceId
import com.bobo.auralis.mobile.library.reconciliation.RoomReconciliationTransaction
import com.bobo.auralis.mobile.library.saf.SafLocation
import com.bobo.auralis.mobile.library.saf.SafQuery
import com.bobo.auralis.mobile.library.saf.SafScanEvent
import com.bobo.auralis.mobile.library.saf.SafScanner
import com.bobo.auralis.mobile.library.scan.AudioFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

/**
 * Summary report of an executed library scan session.
 * Discovery counts include overlapping-root aliases; parse outcomes count unique documents.
 * cacheHits counts actual hydrations, cacheMisses actual extraction attempts (including failures).
 * Work counts can vary with discovery order even when the unique outcomes are identical.
 */
data class ScanSessionReport(
    val scanId: Long,
    val startedAt: Long,
    val elapsedMs: Long,
    val totalFilesDiscovered: Int,
    val audioCandidatesDiscovered: Int,
    val parsedSuccessfully: Int,
    val parseFailed: Int,
    val cacheHits: Int = 0,
    val cacheMisses: Int = 0,
    val rootsAttempted: Int,
    val rootsCompleted: Int,
    val unavailableDirectories: Int,
    val directoryFailures: List<DirectoryScanFailure> = emptyList(),
    val uniqueAudioCandidatesDiscovered: Int = 0,
    val extractionFailures: Int = 0,
)

/** A recoverable SAF directory query failure captured for diagnostic export. */
data class DirectoryScanFailure(
    val rootUri: String,
    val rootPath: String,
    val path: String,
    val code: String,
    val message: String?,
)

/**
 * End-to-end library scan and reconciliation pipeline.
 *
 * Implements `docs/phase3a/06_PIPELINE_INTEGRATION.md` §43, §44 and §46:
 *
 * ```text
 * SAF discovery
 *     ↓
 * Candidate audio
 *     ↓
 * Raw metadata extraction (Outside DB transaction)
 *     ↓
 * AuralisMetadata interpretation
 *     ↓
 * ObservedTrackSource
 *     ↓
 * Identity derivation
 *     ↓
 * Reconciliation & projection (Within Room transaction)
 *     ↓
 * Reconcile missing sources
 * ```
 */
class LibraryScanPipeline(
    private val context: Context,
    private val database: AuralisDatabase,
    private val extractor: MetadataExtractor = MetadataExtractor.from(context),
    private val transaction: RoomReconciliationTransaction = RoomReconciliationTransaction(database),
    private val scan: (SafQuery) -> Flow<SafScanEvent> = { SafScanner.from(context, it).scan() },
) {

    /**
     * Executes a full library scan across the given roots.
     *
     * @param roots List of configured roots to scan.
     * @param scanSessionId Monotonic ID of current scan session. Defaults to currentTimeMillis.
     */
    suspend fun executeScan(
        roots: List<LibraryRootEntity>,
        scanSessionId: Long = System.currentTimeMillis(),
    ): ScanSessionReport {
        val startedAtSystem = System.currentTimeMillis()
        val startedAtRealtime = SystemClock.elapsedRealtime()

        if (roots.isEmpty()) {
            return ScanSessionReport(
                scanId = scanSessionId,
                startedAt = startedAtSystem,
                elapsedMs = 0L,
                totalFilesDiscovered = 0,
                audioCandidatesDiscovered = 0,
                parsedSuccessfully = 0,
                parseFailed = 0,
                rootsAttempted = 0,
                rootsCompleted = 0,
                unavailableDirectories = 0,
            )
        }

        val rootDao = database.libraryRootDao()
        val sourceDao = database.sourceDao()

        // Track attempt timestamp on roots
        for (root in roots) {
            rootDao.update(root.copy(lastAttemptedScanAt = startedAtSystem))
        }

        // Map URI string to root entity
        val rootByUri = roots.associateBy { it.treeUri }
        val openedRoots = roots.map { root ->
            SafLocation.restore(Uri.parse(root.treeUri))
        }

        val failedRootUris = mutableSetOf<String>()
        val unavailableScopes = mutableMapOf<LibraryRootId, MutableList<String>>()
        val parsedRootPriorities = mutableMapOf<com.bobo.auralis.mobile.library.saf.SafDocumentKey, Int>()
        var totalFiles = 0
        val fileCounts = ScanFileCounts()
        var cacheHits = 0
        var cacheMisses = 0
        var unavailableDirs = 0
        val directoryFailures = mutableListOf<DirectoryScanFailure>()

        val query = SafQuery(source = openedRoots, withHidden = false, multithread = true)

        val batch = mutableListOf<RoomReconciliationTransaction.BatchItem>()
        val batchSize = 150

        scan(query).collect { event ->
            when (event) {
                is SafScanEvent.Found -> {
                    totalFiles++
                    val file = event.file
                    val fileName = file.path.name.orEmpty()
                    val format = AudioFormat.fromFileName(fileName)

                    if (format != null) {
                        fileCounts.discovered(file.documentKey)
                        val rootEntity = rootByUri[file.root.uri.toString()]
                        val rootPriority = rootEntity?.priority ?: Int.MAX_VALUE
                        val rootId = rootEntity?.rootId
                        // A lower-priority overlapping tree must never overwrite a successful
                        // observation from an earlier root, regardless of traversal order.
                        if (parsedRootPriorities[file.documentKey]?.let { it <= rootPriority } == true) {
                            return@collect
                        }

                        // Step 1: Check metadata extraction cache (PHASE 3B §5)
                        val existingSource = sourceDao.findByDocumentKey(
                            provider = file.documentKey.provider,
                            documentId = file.documentKey.documentId,
                        )
                        val cachedMeta = existingSource?.let { sourceDao.findMetadata(it.sourceId) }
                        val classification = com.bobo.auralis.mobile.library.cache.SourceMetadataCache.classify(
                            file = file,
                            existingSource = existingSource,
                            cachedMetadata = cachedMeta,
                        )

                        when (classification) {
                            is com.bobo.auralis.mobile.library.cache.CacheClassification.Hit -> {
                                cacheHits++
                                fileCounts.parsed(file.documentKey)
                                // Hydrate observation instantly without file IO or TagLib JNI
                                val hydrated = com.bobo.auralis.mobile.library.cache.SourceMetadataCache.hydrate(
                                    hit = classification,
                                    file = file,
                                    rootPriority = rootPriority,
                                    format = format,
                                )
                                batch.add(RoomReconciliationTransaction.BatchItem(hydrated, rootId))
                                parsedRootPriorities[file.documentKey] = rootPriority
                            }

                            is com.bobo.auralis.mobile.library.cache.CacheClassification.Stale,
                            is com.bobo.auralis.mobile.library.cache.CacheClassification.Miss -> {
                                cacheMisses++
                                // Step 2: Fresh TagLib JNI extraction outside DB transaction
                                val extraction = extractMetadataForScan {
                                    extractor.extract(MetadataTarget(file.uri, fileName))
                                }
                                val extractResult = extraction.result

                                if (extractResult is MetadataResult.Success && extractResult.metadata != null) {
                                    val interpreted = extractResult.metadata.interpret()
                                    val rootRef = RootReference(
                                        treeUri = file.root.uri.toString(),
                                        priority = rootPriority,
                                    )
                                    val observation = ObservedTrackSource(
                                        documentKey = file.documentKey,
                                        root = rootRef,
                                        uri = file.uri,
                                        relativePath = file.path,
                                        fileName = fileName,
                                        sizeBytes = file.size,
                                        modifiedMs = file.modifiedMs,
                                        format = format,
                                        metadata = interpreted,
                                    )
                                    val identity = IdentityDerivation.derive(interpreted)
                                    val resolved = ResolvedObservation.of(observation, identity)

                                    batch.add(RoomReconciliationTransaction.BatchItem(resolved, rootId))
                                    parsedRootPriorities[file.documentKey] = rootPriority
                                    fileCounts.parsed(file.documentKey)
                                } else {
                                    fileCounts.failed(file.documentKey)
                                    // Another authorized tree already opened this physical file.
                                    // A failed alias must not invalidate that usable observation.
                                    if (file.documentKey in parsedRootPriorities) {
                                        return@collect
                                    }
                                    // Flush current batch before handling error
                                    if (batch.isNotEmpty()) {
                                        transaction.reconcileObservationsBatch(
                                            batch = batch.toList(),
                                            scanSessionId = scanSessionId,
                                            timestamp = startedAtSystem,
                                        )
                                        batch.clear()
                                    }
                                    recordFailedSource(
                                        file = file,
                                        format = format,
                                        rootId = rootId,
                                        scanSessionId = scanSessionId,
                                        extraction = extraction,
                                        timestamp = startedAtSystem,
                                    )
                                }
                            }
                        }

                        // Flush batch if threshold reached (§6)
                        if (batch.size >= batchSize) {
                            transaction.reconcileObservationsBatch(
                                batch = batch.toList(),
                                scanSessionId = scanSessionId,
                                timestamp = startedAtSystem,
                            )
                            batch.clear()
                        }
                    }
                }

                is SafScanEvent.DirectoryUnavailable -> {
                    unavailableDirs++
                    rootByUri[event.root.uri.toString()]?.rootId?.let { rootId ->
                        unavailableScopes.getOrPut(rootId) { mutableListOf() }.add(event.path.toString())
                    }
                    directoryFailures += DirectoryScanFailure(
                        rootUri = event.root.uri.toString(),
                        rootPath = event.root.path.toString(),
                        path = event.path.toString(),
                        code = event.cause::class.java.simpleName,
                        message = event.cause.message,
                    )
                    if (event.path == event.root.path) {
                        failedRootUris += event.root.uri.toString()
                    }
                }
            }
        }

        // Flush any remaining observations in batch
        if (batch.isNotEmpty()) {
            transaction.reconcileObservationsBatch(
                batch = batch.toList(),
                scanSessionId = scanSessionId,
                timestamp = startedAtSystem,
            )
            batch.clear()
        }

        // Missing reconciliation (§37, §46):
        // Only roots that didn't experience root-level failures are declared reachable.
        val rootIdToReachable = roots.associate { root ->
            root.rootId to (!failedRootUris.contains(root.treeUri))
        }

        transaction.reconcileMissingSources(
            scanSessionId = scanSessionId,
            rootIdToReachable = rootIdToReachable,
            timestamp = startedAtSystem,
            unavailableScopes = unavailableScopes,
        )

        // Update root status
        val completedCount = roots.count { !failedRootUris.contains(it.treeUri) }
        for (root in roots) {
            val isSuccess = !failedRootUris.contains(root.treeUri)
            rootDao.update(
                root.copy(
                    availabilityState = if (isSuccess) RootAvailabilityState.AVAILABLE else RootAvailabilityState.UNAVAILABLE,
                    lastAttemptedScanAt = startedAtSystem,
                    lastSuccessfulScanAt = if (isSuccess) startedAtSystem else root.lastSuccessfulScanAt,
                )
            )
        }

        val elapsed = SystemClock.elapsedRealtime() - startedAtRealtime

        return ScanSessionReport(
            scanId = scanSessionId,
            startedAt = startedAtSystem,
            elapsedMs = elapsed,
            totalFilesDiscovered = totalFiles,
            audioCandidatesDiscovered = fileCounts.audioCandidatesDiscovered,
            parsedSuccessfully = fileCounts.parsedSuccessfully,
            parseFailed = fileCounts.parseFailed,
            cacheHits = cacheHits,
            cacheMisses = cacheMisses,
            rootsAttempted = roots.size,
            rootsCompleted = completedCount,
            unavailableDirectories = unavailableDirs,
            directoryFailures = directoryFailures.toList(),
            uniqueAudioCandidatesDiscovered = fileCounts.uniqueAudioCandidatesDiscovered,
            extractionFailures = fileCounts.extractionFailures,
        )
    }

    private suspend fun recordFailedSource(
        file: com.bobo.auralis.mobile.library.saf.SafFile,
        format: AudioFormat,
        rootId: LibraryRootId?,
        scanSessionId: Long,
        extraction: ScanMetadataExtraction,
        timestamp: Long,
    ) = database.withTransaction {
        val sourceDao = database.sourceDao()
        val trackDao = database.trackDao()

        val existing = sourceDao.findByDocumentKey(file.documentKey.provider, file.documentKey.documentId)
        val failureCode = extraction.failureCode
        val trackId: TrackId

        if (existing != null) {
            trackId = existing.trackId
            sourceDao.update(
                existing.copy(
                    rootId = rootId ?: existing.rootId,
                    uri = file.uri.toString(),
                    relativePath = file.path.toString(),
                    fileName = file.path.name.orEmpty(),
                    format = format,
                    sizeBytes = file.size,
                    modifiedMs = file.modifiedMs,
                    availabilityState = SourceAvailabilityState.AVAILABLE,
                    parseState = ParseState.FAILED,
                    playabilityState = if (existing.modifiedMs != file.modifiedMs || existing.sizeBytes != file.size) {
                        PlayabilityState.UNKNOWN
                    } else existing.playabilityState,
                    lastSeenScanId = scanSessionId,
                    lastSeenAt = timestamp,
                    failureStage = "extraction",
                    failureCode = failureCode,
                    failureMessage = extraction.failureMessage,
                )
            )
        } else {
            // New unparsed source: gets its own TrackId (never merges with existing semantic tracks, §23)
            val newTrackId = TrackId.random()
            trackId = newTrackId
            val newTrack = com.bobo.auralis.mobile.library.db.entity.TrackEntity(
                trackId = newTrackId,
                trackKeyVersion = 1,
                trackKeyHash = "",
                trackKeyStrength = com.bobo.auralis.mobile.library.identity.TrackKeyStrength.WEAK,
                albumId = null,
                title = file.path.name,
                date = null,
                trackNumber = null,
                trackTotal = null,
                discNumber = null,
                discTotal = null,
                createdAt = timestamp,
                updatedAt = timestamp,
            )
            trackDao.insert(newTrack)

            val newSource = TrackSourceEntity(
                sourceId = TrackSourceId.random(),
                trackId = newTrackId,
                rootId = rootId,
                provider = file.documentKey.provider,
                documentId = file.documentKey.documentId,
                uri = file.uri.toString(),
                relativePath = file.path.toString(),
                fileName = file.path.name.orEmpty(),
                format = format,
                mimeType = "audio/unknown",
                sizeBytes = file.size,
                modifiedMs = file.modifiedMs,
                durationMs = null,
                bitrateKbps = null,
                sampleRateHz = null,
                availabilityState = SourceAvailabilityState.AVAILABLE,
                parseState = ParseState.FAILED,
                playabilityState = PlayabilityState.UNKNOWN,
                lastSeenScanId = scanSessionId,
                lastSeenAt = timestamp,
                failureStage = "extraction",
                failureCode = failureCode,
                failureMessage = extraction.failureMessage,
            )
            sourceDao.insert(newSource)
        }
        transaction.refreshActiveSourceForTrack(trackId, timestamp)
    }
}
