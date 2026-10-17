package com.bobo.auralis.mobile.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobo.auralis.mobile.diagnostics.DiagnosticExportFormat
import com.bobo.auralis.mobile.diagnostics.DiagnosticReportFormatter
import com.bobo.auralis.mobile.library.db.entity.LibraryRootEntity
import com.bobo.auralis.mobile.library.db.entity.RootAvailabilityState
import com.bobo.auralis.mobile.library.management.LibraryManagementState
import com.bobo.auralis.mobile.library.management.LibraryManagementStore
import com.bobo.auralis.mobile.library.management.ScanStatus

@Composable
internal fun LibraryManagementRoute(store: LibraryManagementStore, onBack: () -> Unit) {
    val state by store.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        store.addRoot(it?.toString())
    }
    val textExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) {
        store.exportDiagnostics(it?.toString(), DiagnosticExportFormat.TEXT)
    }
    val jsonExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        store.exportDiagnostics(it?.toString(), DiagnosticExportFormat.JSON)
    }
    LibraryManagementScreen(
        state = state, onBack = onBack, onAdd = { picker.launch(null) },
        onRemove = { store.removeRoot(it.rootId) }, onScan = store::rescan,
        onRetry = store::initialize,
        onExport = { format ->
            val name = DiagnosticReportFormatter.suggestedFileName(format, System.currentTimeMillis())
            if (format == DiagnosticExportFormat.TEXT) textExporter.launch(name) else jsonExporter.launch(name)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryManagementScreen(
    state: LibraryManagementState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onRemove: (LibraryRootEntity) -> Unit,
    onScan: () -> Unit,
    onRetry: () -> Unit,
    onExport: (DiagnosticExportFormat) -> Unit,
) {
    var removing by remember { mutableStateOf<LibraryRootEntity?>(null) }
    val enabled = state.initialized && !state.busy && !state.exporting
    Scaffold(topBar = {
        TopAppBar(title = { Text("曲库管理") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
        })
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text("音乐目录", style = MaterialTheme.typography.titleLarge)
                Text("选择存放音乐的目录，再重新扫描以更新曲库。", style = MaterialTheme.typography.bodyMedium)
            }
            if (state.busy) item {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if (state.status == ScanStatus.Scanning) "正在扫描，可返回浏览曲库" else "正在更新目录…")
            }
            state.message?.let { message -> item {
                Text(message, color = MaterialTheme.colorScheme.error)
            } }
            if (!state.initialized && !state.busy) item {
                OutlinedButton(onClick = onRetry) { Text("重试加载") }
            }
            if (state.initialized && state.roots.isEmpty()) item {
                Text("尚未添加音乐目录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(state.roots, key = { it.rootId.toString() }) { root ->
                Column {
                    Text(root.displayPath, style = MaterialTheme.typography.titleMedium)
                    if (root.availabilityState != RootAvailabilityState.AVAILABLE) {
                        Text("目录暂不可访问，请检查权限后重新扫描", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { removing = root }, enabled = enabled) { Text("移除目录") }
                    HorizontalDivider()
                }
            }
            item {
                Button(onClick = onAdd, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("添加音乐目录") }
                OutlinedButton(onClick = onScan, enabled = enabled && state.roots.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text("重新扫描")
                }
            }
            state.report?.let { report -> item {
                Text("本次扫描完成", style = MaterialTheme.typography.titleMedium)
                Text("已解析 ${report.parsedSuccessfully} 个音频文件；解析失败 ${report.parseFailed} 个；不可访问目录 ${report.unavailableDirectories} 个。")
            } }
            item {
                Text("诊断日志", style = MaterialTheme.typography.titleLarge)
                Text("遇到目录访问、解析或播放问题时，可导出日志。", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { onExport(DiagnosticExportFormat.TEXT) }, enabled = enabled) { Text("导出文本日志") }
                OutlinedButton(onClick = { onExport(DiagnosticExportFormat.JSON) }, enabled = enabled) { Text("导出 JSON 日志") }
                if (state.exporting) Text("正在导出…")
                state.diagnosticMessage?.let { Text(it) }
            }
        }
    }
    removing?.let { root ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("移除此目录？") },
            text = { Text("${root.displayPath}\n不会删除原始音乐文件。") },
            confirmButton = { TextButton(onClick = { onRemove(root); removing = null }, enabled = enabled) { Text("移除") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("取消") } },
        )
    }
}
