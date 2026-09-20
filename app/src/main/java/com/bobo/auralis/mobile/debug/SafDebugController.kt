package com.bobo.auralis.mobile.debug

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import com.bobo.auralis.mobile.diagnostics.buildDiagnosticReport
import com.bobo.auralis.mobile.library.pipeline.LibraryScanPipeline
import com.bobo.auralis.mobile.library.pipeline.ScanSessionReport
import com.bobo.auralis.mobile.library.model.AlbumId
import com.bobo.auralis.mobile.library.saf.LibraryRootRepository
import com.bobo.auralis.mobile.library.saf.SafLocation
import com.bobo.auralis.mobile.library.scan.CandidateAudio
import com.bobo.auralis.mobile.playback.PlaybackConnectionState
import com.bobo.auralis.mobile.playback.PlaybackControllerConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Temporary Technical Spike scan status. */
enum class ScanStatus(val label: String) {
    Idle("空闲"),
    Scanning("扫描中"),
    Completed("已完成"),
    Failed("失败"),
}

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
    private val rootRepository = LibraryRootRepository(appContext, database)
    private val pipeline = LibraryScanPipeline(appContext, database)
    private val playbackConnection = PlaybackControllerConnection(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var scanJob: Job? = null

    var roots by mutableStateOf<List<LibraryRootEntity>>(emptyList())
        private set

    var status by mutableStateOf(ScanStatus.Idle)
        private set

    var totalFileCount by mutableStateOf(0)
        private set

    var candidateCount by mutableStateOf(0)
        private set

    var errorCount by mutableStateOf(0)
        private set

    var cacheHits by mutableStateOf(0)
        private set

    var cacheMisses by mutableStateOf(0)
        private set

    var elapsedMs by mutableStateOf(0L)
        private set

    private var latestScanReport: ScanSessionReport? = null
    private var latestScanFailure: String? = null

    var isExportingDiagnostics by mutableStateOf(false)
        private set

    var diagnosticMessage by mutableStateOf<String?>(null)
        private set

    /** Transient action/scan error, shown at the top of the debug page. */
    var message by mutableStateOf<String?>(null)
        private set

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
            roots = rootRepository.initialize()
            refreshDatabaseTracks()
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

    /** Every unreachable directory reported by the latest scan. */
    val errors = mutableStateListOf<ScanErrorItem>()

    private val rootErrors = mutableStateMapOf<Uri, String>()

    val isScanning: Boolean
        get() = status == ScanStatus.Scanning

    /** Non-null when [root] itself was unreachable during the latest scan. */
    fun rootErrorFor(root: LibraryRootEntity): String? = rootErrors[Uri.parse(root.treeUri)]

    fun addRoot(uri: Uri?) {
        if (uri == null || isScanning) return

        val opened = SafLocation.fromPickerResult(appContext, uri)
        if (opened == null) {
            message = "无法取得该目录的持久读权限，请重新选择"
            return
        }

        scope.launch {
            val added = rootRepository.addRoot(opened)
            if (added == null) {
                message = "该目录已在曲库根目录列表中"
            } else {
                message = null
                roots = rootRepository.getRoots()
            }
        }
    }

    fun removeRoot(root: LibraryRootEntity) {
        if (isScanning) return

        scope.launch {
            if (rootRepository.removeRoot(root.rootId)) {
                roots = rootRepository.getRoots()
                rootErrors.remove(Uri.parse(root.treeUri))
                message = null
                refreshDatabaseTracks()
            }
        }
    }

    /** Starts a fresh scan of all configured roots through [LibraryScanPipeline]. */
    fun rescan() {
        if (isScanning || roots.isEmpty()) return
        scanJob = scope.launch { performScan() }
    }

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
            playbackConnection.play(item.track.trackId)
        }
    }

    fun stopPlayback() {
        playbackConnection.stop()
    }

    fun diagnosticFileName(format: DiagnosticExportFormat): String =
        DiagnosticReportFormatter.suggestedFileName(format, System.currentTimeMillis())

    fun exportDiagnostics(uri: Uri?, format: DiagnosticExportFormat) {
        if (uri == null || isExportingDiagnostics) return
        scope.launch {
            isExportingDiagnostics = true
            diagnosticMessage = null
            try {
                withContext(Dispatchers.IO) {
                    val report = buildDiagnosticReport(
                        generatedAt = System.currentTimeMillis(),
                        roots = database.libraryRootDao().getAllByPriority(),
                        sources = database.sourceDao().getAll(),
                        latestScan = latestScanReport,
                        latestScanFailure = latestScanFailure,
                    )
                    val content = DiagnosticReportFormatter.format(report, format)
                    val stream = appContext.contentResolver.openOutputStream(uri, "wt")
                        ?: error("目标文档无法打开")
                    stream.bufferedWriter(Charsets.UTF_8).use { writer -> writer.write(content) }
                }
                diagnosticMessage = "诊断日志已导出"
            } catch (e: Exception) {
                diagnosticMessage = "诊断日志导出失败: ${e.describe()}"
            } finally {
                isExportingDiagnostics = false
            }
        }
    }

    /** Cancels the scan scope when the debug screen leaves composition. */
    fun close() {
        playbackConnection.release()
        scope.cancel()
    }

    private suspend fun performScan() {
        val currentRoots = roots
        if (currentRoots.isEmpty()) return

        status = ScanStatus.Scanning
        totalFileCount = 0
        candidateCount = 0
        errorCount = 0
        elapsedMs = 0
        message = null
        candidates.clear()
        errors.clear()
        rootErrors.clear()
        diagnosticMessage = null
        latestScanReport = null
        latestScanFailure = null

        val startedAt = SystemClock.elapsedRealtime()

        try {
            val report = pipeline.executeScan(currentRoots)
            latestScanReport = report
            totalFileCount = report.totalFilesDiscovered
            candidateCount = report.audioCandidatesDiscovered
            errorCount = report.unavailableDirectories + report.parseFailed
            cacheHits = report.cacheHits
            cacheMisses = report.cacheMisses
            report.directoryFailures.forEach { failure ->
                val rootUri = Uri.parse(failure.rootUri)
                val rootLabel = currentRoots.firstOrNull { it.treeUri == failure.rootUri }?.displayPath
                    ?: failure.rootPath
                val item = ScanErrorItem(
                    rootUri = rootUri,
                    rootLabel = rootLabel,
                    pathLabel = failure.path,
                    isRootError = failure.path == failure.rootPath,
                    message = listOfNotNull(failure.code, failure.message).joinToString(": "),
                )
                errors += item
                if (item.isRootError) rootErrors[rootUri] = item.message
            }

            status = ScanStatus.Completed
            roots = rootRepository.getRoots()
            refreshDatabaseTracks()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            status = ScanStatus.Failed
            latestScanFailure = e.describe()
            message = "扫描失败: $latestScanFailure"
        } finally {
            elapsedMs = SystemClock.elapsedRealtime() - startedAt
        }
    }
}

private fun Throwable.describe(): String =
    "${this::class.java.simpleName}: ${message ?: "未知错误"}"
