package com.bobo.auralis.mobile.library.management

import com.bobo.auralis.mobile.diagnostics.DiagnosticExportFormat
import com.bobo.auralis.mobile.library.db.entity.LibraryRootEntity
import com.bobo.auralis.mobile.library.db.entity.RootAvailabilityState
import com.bobo.auralis.mobile.library.model.LibraryRootId
import com.bobo.auralis.mobile.library.pipeline.ScanSessionReport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test

class LibraryManagementStoreTest {
    @Test fun scanSurvivesObserverExitAndRejectsConcurrentMutations() = runBlocking {
        val owner = CoroutineScope(coroutineContext + SupervisorJob())
        val ops = FakeOperations()
        val store = LibraryManagementStore(owner, ops)
        try {
            yield()
            val observer = launch { store.state.collect {} }
            store.rescan()
            store.rescan()
            store.addRoot("content://another")
            store.removeRoot(ops.root.rootId)
            yield()
            observer.cancelAndJoin()
            assertEquals(1, ops.scans)
            assertEquals(0, ops.mutations)
            assertEquals(ScanStatus.Scanning, store.state.value.status)
            ops.scanGate.complete(Unit)
            yield()
            assertEquals(ScanStatus.Completed, store.state.value.status)
            assertFalse(store.state.value.busy)
            assertNotNull(store.state.value.report)
        } finally { owner.cancel() }
    }

    @Test fun failedScanCanRetryAndExportIncludesFailure() = runBlocking {
        val owner = CoroutineScope(coroutineContext + SupervisorJob())
        val ops = FakeOperations().apply { failScan = true; scanGate.complete(Unit) }
        val store = LibraryManagementStore(owner, ops)
        try {
            yield()
            store.rescan(); yield()
            assertEquals(ScanStatus.Failed, store.state.value.status)
            assertFalse(store.state.value.busy)
            store.exportDiagnostics("content://output", DiagnosticExportFormat.JSON); yield()
            assertTrue(ops.exported!!.scanFailure!!.contains("bad scan"))
            ops.failScan = false
            store.rescan(); yield()
            assertEquals(ScanStatus.Completed, store.state.value.status)
            assertNull(store.state.value.scanFailure)
        } finally { owner.cancel() }
    }

    @Test fun cancelledOwnerDoesNotPublishCompletedScanAndNewOwnerStartsIdle() = runBlocking {
        val owner = CoroutineScope(coroutineContext + SupervisorJob())
        val ops = FakeOperations()
        val store = LibraryManagementStore(owner, ops)
        yield()
        store.rescan(); yield()
        owner.coroutineContext[Job]!!.cancelAndJoin()
        assertEquals(ScanStatus.Idle, store.state.value.status)
        assertFalse(store.state.value.busy)
        assertNull(store.state.value.report)
        val nextOwner = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val fresh = LibraryManagementStore(nextOwner, ops)
            yield()
            assertEquals(ScanStatus.Idle, fresh.state.value.status)
            assertEquals(1, ops.scans) // Initialization must never restart a scan.
        } finally { nextOwner.cancel() }
    }

    @Test fun initializationFailureAndPickerCancellationRemainRecoverable() = runBlocking {
        val owner = CoroutineScope(coroutineContext + SupervisorJob())
        val ops = FakeOperations().apply { failInitialize = true }
        val store = LibraryManagementStore(owner, ops)
        try {
            yield()
            assertFalse(store.state.value.initialized)
            assertFalse(store.state.value.busy)
            store.rescan(); store.addRoot(null)
            assertEquals(0, ops.scans)
            assertEquals(0, ops.mutations)
            ops.failInitialize = false
            store.initialize(); yield()
            assertTrue(store.state.value.initialized)
            store.addRoot("content://duplicate"); yield()
            assertEquals("duplicate", store.state.value.message)
            assertFalse(store.state.value.busy)
        } finally { owner.cancel() }
    }

    @Test fun completionWaitsForRootRefreshAndRefreshFailureCanRetry() = runBlocking {
        val owner = CoroutineScope(coroutineContext + SupervisorJob())
        val ops = FakeOperations().apply { scanGate.complete(Unit) }
        val store = LibraryManagementStore(owner, ops)
        try {
            yield()
            val gate = CompletableDeferred<Unit>()
            ops.rootsGate = gate
            store.rescan(); yield()
            assertEquals(ScanStatus.Scanning, store.state.value.status)
            assertNull(store.state.value.report)
            assertTrue(store.state.value.busy)
            ops.failRoots = true
            gate.complete(Unit); yield()
            assertEquals(ScanStatus.Failed, store.state.value.status)
            assertNull(store.state.value.report)
            assertFalse(store.state.value.busy)
            ops.failRoots = false
            store.rescan(); yield()
            assertEquals(ScanStatus.Completed, store.state.value.status)
            assertNotNull(store.state.value.report)
        } finally { owner.cancel() }
    }

    private class FakeOperations : LibraryManagementOperations {
        val root = LibraryRootEntity(LibraryRootId.random(), "content://music", "/Music", 0,
            RootAvailabilityState.AVAILABLE, null, null, 0)
        val scanGate = CompletableDeferred<Unit>()
        var rootsGate: CompletableDeferred<Unit>? = null
        var failRoots = false
        var failInitialize = false
        var failScan = false
        var scans = 0
        var mutations = 0
        var exported: LibraryManagementState? = null
        override suspend fun initialize(): List<LibraryRootEntity> {
            if (failInitialize) error("load failed")
            return roots()
        }
        override suspend fun roots(): List<LibraryRootEntity> {
            rootsGate?.await()
            if (failRoots) error("roots refresh failed")
            return listOf(root)
        }
        override suspend fun add(uri: String): String? { mutations++; return "duplicate" }
        override suspend fun remove(rootId: LibraryRootId) { mutations++ }
        override suspend fun scan(roots: List<LibraryRootEntity>): ScanSessionReport {
            scans++
            scanGate.await()
            if (failScan) error("bad scan")
            return ScanSessionReport(scanId = 1, startedAt = 0, elapsedMs = 10,
                totalFilesDiscovered = 1, audioCandidatesDiscovered = 1, parsedSuccessfully = 1,
                parseFailed = 0, rootsAttempted = 1, rootsCompleted = 1, unavailableDirectories = 0)
        }
        override suspend fun export(uri: String, format: DiagnosticExportFormat, state: LibraryManagementState) {
            exported = state
        }
    }
}
