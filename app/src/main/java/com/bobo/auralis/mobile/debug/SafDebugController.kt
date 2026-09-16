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
import com.bobo.auralis.mobile.library.db.entity.LibraryRootEntity
import com.bobo.auralis.mobile.library.metadata.AuralisMetadata
import com.bobo.auralis.mobile.library.metadata.MetadataExtractor
import com.bobo.auralis.mobile.library.metadata.MetadataResult
import com.bobo.auralis.mobile.library.metadata.MetadataTarget
import com.bobo.auralis.mobile.library.metadata.RawMetadataDisplay
import com.bobo.auralis.mobile.library.metadata.interpret
import com.bobo.auralis.mobile.library.metadata.toRawDisplay
import com.bobo.auralis.mobile.library.pipeline.LibraryScanPipeline
import com.bobo.auralis.mobile.library.saf.LibraryRootRepository
import com.bobo.auralis.mobile.library.saf.SafLocation
import com.bobo.auralis.mobile.library.scan.CandidateAudio
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

    /** Transient action/scan error, shown at the top of the debug page. */
    var message by mutableStateOf<String?>(null)
        private set

    /** Candidate audio files discovered by the latest scan, in discovery order. */
    val candidates = mutableStateListOf<CandidateAudio>()

    /** Reconciled logical tracks and their physical sources from Room database (§62). */
    val inspectedTracks = mutableStateListOf<TrackInspectionItem>()

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
                val items = tracks.map { track ->
                    val activeSource = sourceDao.findActiveSource(track.trackId)
                    val sources = sourceDao.findByTrack(track.trackId)
                    val album = track.albumId?.let { graphDao.findAlbumById(it) }
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
                        artists = artists,
                        genres = genres,
                    )
                }

                withContext(Dispatchers.Main) {
                    inspectedTracks.clear()
                    inspectedTracks.addAll(items)
                }
            }
        }
    }

    /** Cancels the scan scope when the debug screen leaves composition. */
    fun close() {
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

        val startedAt = SystemClock.elapsedRealtime()

        try {
            val report = pipeline.executeScan(currentRoots)
            totalFileCount = report.totalFilesDiscovered
            candidateCount = report.audioCandidatesDiscovered
            errorCount = report.unavailableDirectories
            cacheHits = report.cacheHits
            cacheMisses = report.cacheMisses

            status = ScanStatus.Completed
            roots = rootRepository.getRoots()
            refreshDatabaseTracks()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            status = ScanStatus.Failed
            message = "扫描失败: ${e.describe()}"
        } finally {
            elapsedMs = SystemClock.elapsedRealtime() - startedAt
        }
    }
}

private fun Throwable.describe(): String =
    "${this::class.java.simpleName}: ${message ?: "未知错误"}"
