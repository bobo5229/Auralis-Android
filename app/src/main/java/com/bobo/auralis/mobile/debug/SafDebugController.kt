package com.bobo.auralis.mobile.debug

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bobo.auralis.mobile.library.metadata.AuralisMetadata
import com.bobo.auralis.mobile.library.metadata.MetadataExtractor
import com.bobo.auralis.mobile.library.metadata.MetadataResult
import com.bobo.auralis.mobile.library.metadata.MetadataTarget
import com.bobo.auralis.mobile.library.metadata.RawMetadataDisplay
import com.bobo.auralis.mobile.library.metadata.interpret
import com.bobo.auralis.mobile.library.metadata.toRawDisplay
import com.bobo.auralis.mobile.library.scan.CandidateAudio
import com.bobo.auralis.mobile.library.scan.DocumentAcceptResult
import com.bobo.auralis.mobile.library.scan.ScanDocumentCollector
import com.bobo.auralis.mobile.library.saf.SafLocation
import com.bobo.auralis.mobile.library.saf.SafQuery
import com.bobo.auralis.mobile.library.saf.SafScanEvent
import com.bobo.auralis.mobile.library.saf.SafScanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

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
 * Temporary state holder for the Technical Spike debug screen.
 *
 * Deliberately not a ViewModel: the debug screen is disposable and a configuration change may
 * cancel an in-progress spike scan. Product architecture will introduce a proper scan state holder
 * together with Room.
 */
class SafDebugController(context: Context) {
    private val appContext = context.applicationContext
    private val rootStore = SafRootStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var scanJob: Job? = null

    var roots by mutableStateOf(rootStore.load())
        private set

    var status by mutableStateOf(ScanStatus.Idle)
        private set

    var totalFileCount by mutableStateOf(0)
        private set

    var candidateCount by mutableStateOf(0)
        private set

    var errorCount by mutableStateOf(0)
        private set

    var elapsedMs by mutableStateOf(0L)
        private set

    /** Transient action/scan error, shown at the top of the debug page. */
    var message by mutableStateOf<String?>(null)
        private set

    /** Candidate audio files discovered by the latest scan, in discovery order. */
    val candidates = mutableStateListOf<CandidateAudio>()

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
    fun rootErrorFor(root: SafLocation.Opened): String? = rootErrors[root.uri]

    fun addRoot(uri: Uri?) {
        if (uri == null || isScanning) return

        val opened = SafLocation.fromPickerResult(appContext, uri)
        if (opened == null) {
            message = "无法取得该目录的持久读权限，请重新选择"
            return
        }
        if (!rootStore.add(opened)) {
            message = "该目录已在曲库根目录列表中"
            return
        }

        message = null
        roots = rootStore.load()
    }

    fun removeRoot(root: SafLocation.Opened) {
        if (isScanning) return

        if (rootStore.remove(root)) {
            roots = rootStore.load()
            rootErrors.remove(root.uri)
            message = null
        }
    }

    /** Starts a fresh scan of all configured roots. */
    fun rescan() {
        if (isScanning || roots.isEmpty()) return
        scanJob = scope.launch { performScan() }
    }

    /** Cancels the scan scope when the debug screen leaves composition. */
    fun close() {
        scope.cancel()
    }

    private suspend fun performScan() {
        val source = roots
        if (source.isEmpty()) return

        status = ScanStatus.Scanning
        totalFileCount = 0
        candidateCount = 0
        errorCount = 0
        elapsedMs = 0
        message = null
        candidates.clear()
        errors.clear()
        rootErrors.clear()

        val collector = ScanDocumentCollector()
        val startedAt = SystemClock.elapsedRealtime()

        try {
            SafScanner.from(
                    appContext,
                    SafQuery(source = source, withHidden = false, multithread = true),
                )
                .scan()
                .collect { event ->
                    when (event) {
                        is SafScanEvent.Found -> {
                            val file = event.file
                            val result =
                                collector.accept(
                                    documentKey = file.documentKey,
                                    fileName = file.path.name.orEmpty(),
                                    size = file.size,
                                    rootLabel = file.root.path.toString(),
                                    uri = file.uri,
                                )
                            when (result) {
                                is DocumentAcceptResult.Duplicate -> Unit
                                is DocumentAcceptResult.NewDocument -> {
                                    totalFileCount = collector.documentCount
                                    result.candidate?.let { candidate ->
                                        candidates.add(candidate)
                                        candidateCount = collector.candidateCount
                                    }
                                }
                            }
                        }
                        is SafScanEvent.DirectoryUnavailable -> {
                            val isRootError = event.path == event.root.path
                            errors.add(
                                ScanErrorItem(
                                    rootUri = event.root.uri,
                                    rootLabel = event.root.path.toString(),
                                    pathLabel = event.path.toString(),
                                    isRootError = isRootError,
                                    message = event.cause.describe(),
                                )
                            )
                            errorCount = errors.size
                            if (isRootError) {
                                rootErrors[event.root.uri] = event.cause.describe()
                            }
                        }
                    }
                }
            status = ScanStatus.Completed
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
