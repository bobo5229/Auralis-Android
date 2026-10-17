package com.bobo.auralis.mobile.library.management

import com.bobo.auralis.mobile.diagnostics.DiagnosticExportFormat
import com.bobo.auralis.mobile.library.db.entity.LibraryRootEntity
import com.bobo.auralis.mobile.library.model.LibraryRootId
import com.bobo.auralis.mobile.library.pipeline.ScanSessionReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ScanStatus(val label: String) {
    Idle("空闲"), Scanning("扫描中"), Completed("已完成"), Failed("失败"),
}

data class LibraryManagementState(
    val roots: List<LibraryRootEntity> = emptyList(),
    val initialized: Boolean = false,
    val busy: Boolean = false,
    val status: ScanStatus = ScanStatus.Idle,
    val report: ScanSessionReport? = null,
    val scanFailure: String? = null,
    val message: String? = null,
    val exporting: Boolean = false,
    val diagnosticMessage: String? = null,
)

internal interface LibraryManagementOperations {
    suspend fun initialize(): List<LibraryRootEntity>
    suspend fun roots(): List<LibraryRootEntity>
    suspend fun add(uri: String): String?
    suspend fun remove(rootId: LibraryRootId)
    suspend fun scan(roots: List<LibraryRootEntity>): ScanSessionReport
    suspend fun export(uri: String, format: DiagnosticExportFormat, state: LibraryManagementState)
}

/** Process-owned state. UI observers never own/cancel scans. Call actions on the main thread. */
internal class LibraryManagementStore(
    private val scope: CoroutineScope,
    private val operations: LibraryManagementOperations,
) {
    private val mutableState = MutableStateFlow(LibraryManagementState())
    val state = mutableState.asStateFlow()

    init { initialize() }

    fun initialize() = action(requireInitialized = false) {
        mutableState.value = mutableState.value.copy(roots = operations.initialize(), initialized = true)
    }

    fun addRoot(uri: String?) {
        if (uri == null) return
        action {
            val message = operations.add(uri)
            mutableState.value = mutableState.value.copy(roots = operations.roots(), message = message)
        }
    }

    fun removeRoot(rootId: LibraryRootId) = action {
        operations.remove(rootId)
        mutableState.value = mutableState.value.copy(roots = operations.roots())
    }

    fun rescan() {
        if (state.value.roots.isEmpty()) return
        action {
            mutableState.value = mutableState.value.copy(
                status = ScanStatus.Scanning, report = null, scanFailure = null,
            )
            try {
                val report = operations.scan(state.value.roots)
                mutableState.value = mutableState.value.copy(
                    report = report, status = ScanStatus.Completed,
                )
                mutableState.value = mutableState.value.copy(roots = operations.roots())
            } catch (cancelled: CancellationException) {
                mutableState.value = mutableState.value.copy(status = ScanStatus.Idle)
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    status = ScanStatus.Failed, scanFailure = error.describe(),
                )
                throw error
            }
        }
    }

    fun exportDiagnostics(uri: String?, format: DiagnosticExportFormat) {
        if (uri == null || state.value.exporting || state.value.busy || !state.value.initialized) return
        val snapshot = state.value
        mutableState.value = snapshot.copy(exporting = true, diagnosticMessage = null)
        scope.launch {
            try {
                operations.export(uri, format, snapshot)
                mutableState.value = mutableState.value.copy(diagnosticMessage = "诊断日志已导出")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(diagnosticMessage = "导出失败：${error.describe()}")
            } finally {
                mutableState.value = mutableState.value.copy(exporting = false)
            }
        }
    }

    private fun action(requireInitialized: Boolean = true, block: suspend () -> Unit) {
        val current = state.value
        if (current.busy || current.exporting || (requireInitialized && !current.initialized)) return
        // Claim synchronously, before launch: rapid taps and multiple screens cannot overlap mutations.
        mutableState.value = current.copy(busy = true, message = null)
        scope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(message = error.describe())
            } finally {
                mutableState.value = mutableState.value.copy(busy = false)
            }
        }
    }
}

private fun Throwable.describe(): String = "${javaClass.simpleName}: ${message ?: "操作失败，请重试"}"
