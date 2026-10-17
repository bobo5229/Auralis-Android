package com.bobo.auralis.mobile.debug

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bobo.auralis.mobile.library.db.AuralisDatabase
import com.bobo.auralis.mobile.library.db.entity.AlbumEntity
import com.bobo.auralis.mobile.library.db.entity.LibraryRootEntity
import com.bobo.auralis.mobile.library.metadata.AuralisMetadata
import com.bobo.auralis.mobile.library.metadata.MetadataExtractor
import com.bobo.auralis.mobile.library.metadata.MetadataResult
import com.bobo.auralis.mobile.library.metadata.MetadataTarget
import com.bobo.auralis.mobile.library.metadata.RawMetadataDisplay
import com.bobo.auralis.mobile.library.metadata.interpret
import com.bobo.auralis.mobile.library.metadata.toRawDisplay
import com.bobo.auralis.mobile.diagnostics.DiagnosticExportFormat
import com.bobo.auralis.mobile.diagnostics.DiagnosticReportFormatter
import com.bobo.auralis.mobile.library.model.AlbumId
import com.bobo.auralis.mobile.library.scan.CandidateAudio
import com.bobo.auralis.mobile.playback.PlaybackConnectionState
import com.bobo.auralis.mobile.playback.PlaybackControllerConnection
import com.bobo.auralis.mobile.playback.PlaybackQueueTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A directory that could not be queried during a scan, kept for the debug UI. */
data class ScanErrorItem(
    val rootUri: Uri,
    val rootLabel: String,
    val pathLabel: String,
    val isRootError: Boolean,
    val message: String,
)

/**
 * State holder for the Phase 3A Technical Spike & Debug Screen.
 *
 * Implements §62 inspection: displays TrackId, TrackKey, TrackKey strength,
 * SourceId, SafDocumentKey, root priority, active source, and source states.
 */
class SafDebugController(context: Context) {
    private val appContext = context.applicationContext
    private val database = AuralisDatabase.get(appContext)
    private val management = (appContext as com.bobo.auralis.mobile.AuralisApplication).libraryManagement
    private val playbackConnection = PlaybackControllerConnection(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var managementState by mutableStateOf(management.state.value)
    val roots get() = managementState.roots
    val status get() = managementState.status
    val totalFileCount get() = managementState.report?.totalFilesDiscovered ?: 0
    val candidateCount get() = managementState.report?.audioCandidatesDiscovered ?: 0
    val errorCount get() = managementState.report?.let { it.unavailableDirectories + it.parseFailed } ?: 0
    val cacheHits get() = managementState.report?.cacheHits ?: 0
    val cacheMisses get() = managementState.report?.cacheMisses ?: 0
    val elapsedMs get() = managementState.report?.elapsedMs ?: 0L
    val isExportingDiagnostics get() = managementState.exporting
    val diagnosticMessage get() = managementState.diagnosticMessage
    val message get() = managementState.message

    /** Candidate audio files discovered by the latest scan, in discovery order. */
    val candidates = mutableStateListOf<CandidateAudio>()

    /** Reconciled logical tracks and their physical sources from Room database (§62). */
    val inspectedTracks = mutableStateListOf<TrackInspectionItem>()

    /** Real AlbumEntity groups for the temporary Albums inspection view. */
    val inspectedAlbums = mutableStateListOf<AlbumInspectionItem>()

    var playback by mutableStateOf(PlaybackConnectionState())
        private set

    /** Selected audio candidate and its raw metadata extraction result. */
    var selectedCandidate by mutableStateOf<CandidateAudio?>(null)
        private set

    var selectedMetadata by mutableStateOf<RawMetadataDisplay?>(null)
        private set

    var selectedInterpreted by mutableStateOf<AuralisMetadata?>(null)
        private set

    var inspectError by mutableStateOf<String?>(null)
        private set

    var isExtracting by mutableStateOf(false)
        private set

    init {
        scope.launch {
            var firstEmission = true
            management.state.collect { next ->
                val previous = managementState
                managementState = next
                if (next.initialized && !next.busy &&
                    (firstEmission || !previous.initialized || previous.busy || previous.roots != next.roots || previous.report != next.report)) {
                    refreshDatabaseTracks()
                }
                firstEmission = false
            }
        }
        scope.launch {
            playbackConnection.state.collect { playback = it }
        }
    }

    fun inspect(candidate: CandidateAudio) {
        selectedCandidate = candidate
        selectedMetadata = null
        selectedInterpreted = null
        inspectError = null
        val uri = candidate.uri
        if (uri == null) {
            inspectError = "无法获取文件 URI"
            return
        }
        scope.launch {
            isExtracting = true
            try {
                val extractor = MetadataExtractor.from(appContext)
                when (val result = extractor.extract(MetadataTarget(uri, candidate.fileName))) {
                    is MetadataResult.Success -> {
                        val md = result.metadata
                        if (md != null) {
                            selectedMetadata = md.toRawDisplay(candidate.fileName)
                            selectedInterpreted = md.interpret()
                        } else {
                            inspectError = "读取成功但元数据为空"
                        }
                    }
                    is MetadataResult.NoMetadata -> {
                        inspectError = "文件不包含可识别的元数据"
                    }
                    is MetadataResult.NotAudio -> {
                        inspectError = "TagLib 未识别该文件为支持的音频格式"
                    }
                    is MetadataResult.ProviderFailed -> {
                        inspectError = "SAF Provider 打开文件失败 (openFileDescriptor 失败)"
                    }
                }
            } catch (e: Exception) {
                inspectError = "解析异常: ${e.javaClass.simpleName}: ${e.message}"
            } finally {
                isExtracting = false
            }
        }
    }

    fun clearSelectedMetadata() {
        selectedCandidate = null
        selectedMetadata = null
        selectedInterpreted = null
        inspectError = null
    }

    /** Project shared scan diagnostics for the inspection UI. */
    val errors: List<ScanErrorItem>
        get() = managementState.report?.directoryFailures.orEmpty().map { failure ->
            ScanErrorItem(
                Uri.parse(failure.rootUri),
                roots.firstOrNull { it.treeUri == failure.rootUri }?.displayPath ?: failure.rootPath,
                failure.path, failure.path == failure.rootPath,
                listOfNotNull(failure.code, failure.message).joinToString(": "),
            )
        }
    val isScanning get() = managementState.busy || managementState.exporting
    fun rootErrorFor(root: LibraryRootEntity): String? =
        errors.firstOrNull { it.isRootError && it.rootUri.toString() == root.treeUri }?.message
    fun addRoot(uri: Uri?) = management.addRoot(uri?.toString())
    fun removeRoot(root: LibraryRootEntity) = management.removeRoot(root.rootId)
    fun rescan() = management.rescan()

    /** Refreshes the database tracks list for debug inspection. */
    fun refreshDatabaseTracks() {
        scope.launch {
            withContext(Dispatchers.IO) {
                val trackDao = database.trackDao()
                val sourceDao = database.sourceDao()
                val graphDao = database.graphDao()
                val rootDao = database.libraryRootDao()

                val rootPriorities = rootDao.getAllByPriority().associate { it.rootId to it.priority }
                val tracks = trackDao.getAll()
                val albumDetails = mutableMapOf<AlbumId, Pair<AlbumEntity, List<String>>>()
                val items = tracks.map { track ->
                    val activeSource = sourceDao.findActiveSource(track.trackId)
                    val sources = sourceDao.findByTrack(track.trackId)
                    val album = track.albumId?.let { graphDao.findAlbumById(it) }
                    val albumArtists = album?.let { entity ->
                        albumDetails.getOrPut(entity.albumId) {
                            val names = graphDao.albumArtists(entity.albumId)
                                .mapNotNull { graphDao.findArtistById(it.artistId)?.displayName }
                            entity to names
                        }.second
                    }.orEmpty()
                    val artistRefs = graphDao.trackArtists(track.trackId)
                    val artists = artistRefs.mapNotNull { graphDao.findArtistById(it.artistId)?.displayName }
                    val genreRefs = graphDao.trackGenres(track.trackId)
                    val genres = genreRefs.mapNotNull { graphDao.findGenreById(it.genreId)?.displayName }

                    TrackInspectionItem(
                        track = track,
                        activeSourceId = activeSource?.sourceId,
                        sources = sources.map { s ->
                            SourceInspectionDetail(
                                source = s,
                                isActive = s.sourceId == activeSource?.sourceId,
                                rootPriority = s.rootId?.let { rootPriorities[it] },
                            )
                        },
                        albumTitle = album?.title,
                        albumDate = album?.date,
                        albumArtists = albumArtists,
                        artists = artists,
                        genres = genres,
                    )
                }

                withContext(Dispatchers.Main) {
                    inspectedTracks.clear()
                    inspectedTracks.addAll(items)
                    inspectedAlbums.clear()
                    inspectedAlbums.addAll(buildAlbumInspectionItems(items))
                }
            }
        }
    }

    fun playOrToggle(item: TrackInspectionItem) {
        val canToggleCurrent =
            playback.currentMediaId == item.track.trackId.toString() &&
                playback.error == null &&
                playback.playbackState in setOf(
                    androidx.media3.common.Player.STATE_BUFFERING,
                    androidx.media3.common.Player.STATE_READY,
                )
        if (canToggleCurrent) {
            playbackConnection.togglePlayPause()
        } else {
            playbackConnection.play(
                trackId = item.track.trackId,
                queueTracks = inspectedTracks
                    .filter { it.activeSourceId != null }
                    .map { inspected ->
                        PlaybackQueueTrack(
                            trackId = inspected.track.trackId,
                            albumKey = inspected.track.albumId?.toString(),
                            discNumber = inspected.track.discNumber,
                            trackNumber = inspected.track.trackNumber,
                        )
                    },
            )
        }
    }

    fun stopPlayback() {
        playbackConnection.stop()
    }

    fun diagnosticFileName(format: DiagnosticExportFormat): String =
        DiagnosticReportFormatter.suggestedFileName(format, System.currentTimeMillis())

    fun exportDiagnostics(uri: Uri?, format: DiagnosticExportFormat) =
        management.exportDiagnostics(uri?.toString(), format)

    /** Only inspection and playback observation stop; the shared scan continues. */
    fun close() {
        playbackConnection.release()
        scope.cancel()
    }
}
