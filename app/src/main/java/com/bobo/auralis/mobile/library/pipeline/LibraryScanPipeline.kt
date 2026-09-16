package com.bobo.auralis.mobile.library.pipeline

import android.content.Context
import android.net.Uri
import android.os.SystemClock
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
import kotlinx.coroutines.flow.collect

/**
 * Summary report of an executed library scan session.
 */
data class ScanSessionReport(
    val scanId: Long,
    val startedAt: Long,
    val elapsedMs: Long,
    val totalFilesDiscovered: Int,
    val audioCandidatesDiscovered: Int,
    val parsedSuccessfully: Int,
    val parseFailed: Int,
    val rootsAttempted: Int,
    val rootsCompleted: Int,
    val unavailableDirectories: Int,
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
        var totalFiles = 0
        var audioCandidates = 0
        var parsedSuccess = 0
        var parseFailed = 0
        var unavailableDirs = 0

        val scanner = SafScanner.from(
            context = context,
            query = SafQuery(source = openedRoots, withHidden = false, multithread = true),
        )

        scanner.scan().collect { event ->
            when (event) {
                is SafScanEvent.Found -> {
                    totalFiles++
                    val file = event.file
                    val fileName = file.path.name.orEmpty()
                    val format = AudioFormat.fromFileName(fileName)

                    if (format != null) {
                        audioCandidates++
                        val rootEntity = rootByUri[file.root.uri.toString()]
                        val rootPriority = rootEntity?.priority ?: Int.MAX_VALUE
                        val rootId = rootEntity?.rootId

                        // Step 1: Extract raw metadata outside DB transaction (§47)
                        val extractResult = extractor.extract(MetadataTarget(file.uri, fileName))

                        if (extractResult is MetadataResult.Success && extractResult.metadata != null) {
                            // Step 2: Interpret according to Auralis metadata rules (§43)
                            val interpreted = extractResult.metadata.interpret()

                            // Step 3: Package into ObservedTrackSource and ResolvedObservation
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

                            // Step 4: Reconcile and project in Room transaction (§44, §40)
                            transaction.reconcileObservation(
                                resolved = resolved,
                                rootId = rootId,
                                scanSessionId = scanSessionId,
                                timestamp = startedAtSystem,
                            )
                            parsedSuccess++
                        } else {
                            // Parsing failed or not audio; record source state as FAILED
                            recordFailedSource(
                                file = file,
                                format = format,
                                rootId = rootId,
                                scanSessionId = scanSessionId,
                                extractResult = extractResult,
                                timestamp = startedAtSystem,
                            )
                            parseFailed++
                        }
                    }
                }

                is SafScanEvent.DirectoryUnavailable -> {
                    unavailableDirs++
                    if (event.path == event.root.path) {
                        failedRootUris += event.root.uri.toString()
                    }
                }
            }
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
        )

        // Update root status
        val completedCount = roots.count { !failedRootUris.contains(it.treeUri) }
        for (root in roots) {
            val isSuccess = !failedRootUris.contains(root.treeUri)
            rootDao.update(
                root.copy(
                    availabilityState = if (isSuccess) RootAvailabilityState.AVAILABLE else RootAvailabilityState.UNAVAILABLE,
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
            audioCandidatesDiscovered = audioCandidates,
            parsedSuccessfully = parsedSuccess,
            parseFailed = parseFailed,
            rootsAttempted = roots.size,
            rootsCompleted = completedCount,
            unavailableDirectories = unavailableDirs,
        )
    }

    private suspend fun recordFailedSource(
        file: com.bobo.auralis.mobile.library.saf.SafFile,
        format: AudioFormat,
        rootId: LibraryRootId?,
        scanSessionId: Long,
        extractResult: MetadataResult,
        timestamp: Long,
    ) {
        val sourceDao = database.sourceDao()
        val trackDao = database.trackDao()

        val existing = sourceDao.findByDocumentKey(file.documentKey.provider, file.documentKey.documentId)
        val failureCode = extractResult::class.java.simpleName

        if (existing != null) {
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
                    lastSeenScanId = scanSessionId,
                    lastSeenAt = timestamp,
                    failureStage = "extraction",
                    failureCode = failureCode,
                )
            )
        } else {
            // New unparsed source: gets its own TrackId (never merges with existing semantic tracks, §23)
            val newTrackId = TrackId.random()
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
                failureMessage = null,
            )
            sourceDao.insert(newSource)
        }
    }
}
