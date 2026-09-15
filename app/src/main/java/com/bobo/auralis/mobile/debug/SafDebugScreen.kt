package com.bobo.auralis.mobile.debug

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bobo.auralis.mobile.library.scan.CandidateAudio
import com.bobo.auralis.mobile.library.saf.SafLocation

/**
 * Temporary Technical Spike screen.
 *
 * Scope: SAF root selection/persistence, recursive scan status and candidate file inspection.
 * No Room, no playback, no tag/cover/lyrics parsing and no product visual design.
 */
@Composable
fun SafDebugScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val controller = remember { SafDebugController(context.applicationContext) }
    DisposableEffect(controller) { onDispose { controller.close() } }

    val treePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            controller.addRoot(uri)
        }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text("SAF Technical Spike", style = MaterialTheme.typography.headlineSmall) }
            item {
                Text(
                    "临时调试页：验证目录选择、持久授权与递归扫描，不代表正式 UI",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            item { StatusSection(controller) }

            item { Text("曲库根目录 (${controller.roots.size})", style = MaterialTheme.typography.titleMedium) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { treePicker.launch(null) },
                        enabled = !controller.isScanning,
                    ) {
                        Text("添加曲库目录")
                    }
                    OutlinedButton(
                        onClick = { controller.rescan() },
                        enabled = controller.roots.isNotEmpty() && !controller.isScanning,
                    ) {
                        Text("重新扫描")
                    }
                }
            }

            if (controller.roots.isEmpty()) {
                item {
                    Text(
                        "尚未选择目录。至少添加一个目录后才能扫描。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(controller.roots, key = { it.uri.toString() }) { root ->
                RootRow(
                    root = root,
                    error = controller.rootErrorFor(root),
                    enabled = !controller.isScanning,
                    onRemove = { controller.removeRoot(root) },
                )
            }

            controller.message?.let { message ->
                item {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (controller.errors.isNotEmpty()) {
                item {
                    Text(
                        "扫描异常 (${controller.errors.size})",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(
                    items = controller.errors,
                    key = { "${it.rootUri}|${it.pathLabel}|${it.message}" },
                ) { error ->
                    ErrorRow(error)
                }
            }

            item {
                Text(
                    "候选音频 (${controller.candidates.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (controller.candidates.isEmpty()) {
                item {
                    Text(
                        "暂无候选音频，点击“重新扫描”开始。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(
                items = controller.candidates,
                key = { "${it.documentKey.provider}|${it.documentKey.documentId}" },
            ) { candidate ->
                CandidateRow(candidate)
            }
        }
    }
}

@Composable
private fun StatusSection(controller: SafDebugController) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("状态: ${controller.status.label}", style = MaterialTheme.typography.titleMedium)
            Text("扫描文件总数: ${controller.totalFileCount}")
            Text("候选音频数量: ${controller.candidateCount}")
            Text("错误数量: ${controller.errorCount}")
            Text("扫描耗时: ${formatDuration(controller.elapsedMs)}")
        }
    }
}

@Composable
private fun RootRow(
    root: SafLocation.Opened,
    error: String?,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(root.path.toString(), style = MaterialTheme.typography.bodyLarge)
                Text(
                    root.uri.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (error != null) {
                    Text(
                        "不可访问: $error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            TextButton(onClick = onRemove, enabled = enabled) { Text("删除") }
        }
    }
}

@Composable
private fun ErrorRow(error: ScanErrorItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                if (error.isRootError) {
                    "根目录不可访问: ${error.rootLabel}"
                } else {
                    "子目录不可访问: ${error.pathLabel}"
                },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(error.message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CandidateRow(candidate: CandidateAudio) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(candidate.fileName, style = MaterialTheme.typography.bodyLarge)
        Text(
            "${candidate.format.label} · ${formatBytes(candidate.size)} · 来源: ${candidate.rootLabel}",
            style = MaterialTheme.typography.bodySmall,
        )
        HorizontalDivider()
    }
}

private fun formatBytes(bytes: Long): String =
    when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }

private fun formatDuration(ms: Long): String =
    if (ms < 1000) {
        "$ms ms"
    } else {
        "%.1f s".format(ms / 1000.0)
    }
