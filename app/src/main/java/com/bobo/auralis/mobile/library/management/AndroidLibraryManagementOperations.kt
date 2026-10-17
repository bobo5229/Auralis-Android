package com.bobo.auralis.mobile.library.management

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.bobo.auralis.mobile.diagnostics.DiagnosticExportFormat
import com.bobo.auralis.mobile.diagnostics.DiagnosticReportFormatter
import com.bobo.auralis.mobile.diagnostics.buildDiagnosticReport
import com.bobo.auralis.mobile.library.db.AuralisDatabase
import com.bobo.auralis.mobile.library.db.entity.LibraryRootEntity
import com.bobo.auralis.mobile.library.model.LibraryRootId
import com.bobo.auralis.mobile.library.pipeline.LibraryScanPipeline
import com.bobo.auralis.mobile.library.saf.LibraryRootRepository
import com.bobo.auralis.mobile.library.saf.SafLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AndroidLibraryManagementOperations(context: Context) : LibraryManagementOperations {
    private val context = context.applicationContext
    private val database = AuralisDatabase.get(this.context)
    private val repository = LibraryRootRepository(this.context, database)
    private val pipeline = LibraryScanPipeline(this.context, database)

    override suspend fun initialize() = repository.initialize()
    override suspend fun roots() = repository.getRoots()
    override suspend fun add(uri: String): String? = withContext(Dispatchers.IO) {
        val opened = SafLocation.fromPickerResult(context, Uri.parse(uri))
            ?: return@withContext "无法取得目录的持久读权限，请重新选择"
        if (repository.addRoot(opened) == null) "该目录已在曲库中" else null
    }
    override suspend fun remove(rootId: LibraryRootId) { repository.removeRoot(rootId) }
    override suspend fun scan(roots: List<LibraryRootEntity>) = withContext(Dispatchers.IO) {
        pipeline.executeScan(roots)
    }
    override suspend fun export(uri: String, format: DiagnosticExportFormat, state: LibraryManagementState) =
        withContext(Dispatchers.IO) {
            val report = database.withTransaction {
                buildDiagnosticReport(
                    generatedAt = System.currentTimeMillis(),
                    roots = database.libraryRootDao().getAllByPriority(),
                    sources = database.sourceDao().getAll(),
                    latestScan = state.report,
                    latestScanFailure = state.scanFailure,
                )
            }
            val stream = context.contentResolver.openOutputStream(Uri.parse(uri), "wt")
                ?: error("目标文档无法打开")
            stream.bufferedWriter(Charsets.UTF_8).use { it.write(DiagnosticReportFormatter.format(report, format)) }
        }
}
