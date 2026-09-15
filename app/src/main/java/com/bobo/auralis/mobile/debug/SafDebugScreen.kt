package com.bobo.auralis.mobile.debug

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bobo.auralis.mobile.library.metadata.AuralisMetadata
import com.bobo.auralis.mobile.library.metadata.RawMetadataDisplay
import com.bobo.auralis.mobile.library.saf.SafLocation
import com.bobo.auralis.mobile.library.scan.CandidateAudio

/**
 * Technical Spike Phase 2B screen.
 *
 * Scope: SAF root selection/persistence, recursive scan, and raw TagLib metadata extraction display.
 * Direct display of raw extraction results and raw tag maps for verification on real device.
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
            item { Text("Auralis Phase 2B Spike", style = MaterialTheme.typography.headlineSmall) }
            item {
                Text(
                    "技术验证页：TagLib JNI 原生元数据提取与格式解码验证",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            item { StatusSection(controller) }

            // Metadata Detail Card
            if (controller.isExtracting) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text("正在通过 TagLib 提取原始元数据...")
                        }
                    }
                }
            }

            controller.inspectError?.let { err ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("元数据提取失败", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.height(4.dp))
                            TextButton(onClick = { controller.clearSelectedMetadata() }) { Text("关闭") }
                        }
                    }
                }
            }

            controller.selectedInterpreted?.let { interpreted ->
                item {
                    AuralisInterpretedCard(interpreted = interpreted)
                }
            }

            controller.selectedMetadata?.let { metadata ->
                item {
                    MetadataInspectionCard(
                        display = metadata,
                        onDismiss = { controller.clearSelectedMetadata() },
                    )
                }
            }

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
                    "候选音频 (${controller.candidates.size}) - 点击条目查看原始元数据",
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
                val isSelected = controller.selectedCandidate == candidate
                CandidateRow(
                    candidate = candidate,
                    isSelected = isSelected,
                    onInspect = { controller.inspect(candidate) },
                )
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
private fun AuralisInterpretedCard(
    interpreted: AuralisMetadata,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Auralis 语义解析结果 (Interpreted)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            HorizontalDivider()

            MetadataItem("Title", interpreted.title ?: "（未知 Title）")
            MetadataItem(
                "Artists (${interpreted.artists.size})",
                if (interpreted.artists.isEmpty()) "（无 / 未知 Artist）" else interpreted.artists.joinToString(" ; "),
            )
            MetadataItem(
                "Album Artists (${interpreted.albumArtists.size})",
                if (interpreted.albumArtists.isEmpty()) "（无 / 未知 Album Artist，未降级）" else interpreted.albumArtists.joinToString(" ; "),
            )
            MetadataItem("Album", interpreted.album ?: "（未知 Album）")
            MetadataItem(
                "Genres (${interpreted.genres.size})",
                if (interpreted.genres.isEmpty()) "（无）" else interpreted.genres.joinToString(" ; "),
            )
            MetadataItem("Date (YYYY-MM-DD)", interpreted.date ?: "（无 / 缺失）")
            MetadataItem(
                "Track",
                if (interpreted.trackNumber != null) {
                    if (interpreted.trackTotal != null) "${interpreted.trackNumber} / ${interpreted.trackTotal}" else "${interpreted.trackNumber}"
                } else {
                    "（无）"
                },
            )
            MetadataItem(
                "Disc",
                if (interpreted.discNumber != null) {
                    if (interpreted.discTotal != null) "${interpreted.discNumber} / ${interpreted.discTotal}" else "${interpreted.discNumber}"
                } else {
                    "（无）"
                },
            )
            MetadataItem(
                "Embedded Lyrics",
                if (interpreted.embeddedLyrics != null) "存在 (字符数: ${interpreted.embeddedLyrics.length})" else "无",
            )
            if (interpreted.embeddedLyrics != null) {
                Text(
                    "歌词前100字: ${interpreted.embeddedLyrics.take(100).replace('\n', ' ')}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MetadataItem(
                "Embedded Artwork",
                if (interpreted.hasEmbeddedArtwork) "存在 (${formatBytes(interpreted.artworkBytes.toLong())})" else "无",
            )
            MetadataItem("Codec / MIME", interpreted.mimeType)
            MetadataItem("Duration", "${interpreted.durationMs} ms (${formatDuration(interpreted.durationMs)})")
            MetadataItem("Bitrate", "${interpreted.bitrateKbps} kbps")
            MetadataItem("Sample Rate", "${interpreted.sampleRateHz} Hz")
        }
    }
}

@Composable
private fun MetadataInspectionCard(
    display: RawMetadataDisplay,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "原始元数据读取结果",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
            Text("文件: ${display.fileName}", style = MaterialTheme.typography.labelMedium)
            HorizontalDivider()

            MetadataItem("Title", display.title ?: "（无）")
            MetadataItem("Artist (Raw)", if (display.artistsRaw.isEmpty()) "（无）" else display.artistsRaw.joinToString(" | "))
            MetadataItem("Album Artist (Raw)", if (display.albumArtistsRaw.isEmpty()) "（无）" else display.albumArtistsRaw.joinToString(" | "))
            MetadataItem("Album", display.album ?: "（无）")
            MetadataItem("Genre (Raw)", if (display.genresRaw.isEmpty()) "（无）" else display.genresRaw.joinToString(" | "))
            MetadataItem("Genre (格式解码)", if (display.genresDecoded.isEmpty()) "（无）" else display.genresDecoded.joinToString(" | "))
            MetadataItem("Date", display.date ?: "（无）")
            MetadataItem("Track", "${display.trackRaw ?: "无"} (结构化: ${display.trackPosition ?: "无"})")
            MetadataItem("Disc", "${display.discRaw ?: "无"} (结构化: ${display.discPosition ?: "无"})")
            MetadataItem(
                "Lyrics",
                if (display.hasLyrics) "存在 (字符数: ${display.lyricsLength})" else "无",
            )
            if (display.hasLyrics && display.lyricsSnippet != null) {
                Text(
                    "歌词预览: ${display.lyricsSnippet}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MetadataItem(
                "Artwork",
                if (display.hasArtwork) "存在 (${formatBytes(display.artworkBytes.toLong())})" else "无",
            )
            MetadataItem("Duration", "${display.durationMs} ms (${formatDuration(display.durationMs)})")
            MetadataItem("Bitrate", "${display.bitrateKbps} kbps")
            MetadataItem("Sample Rate", "${display.sampleRateHz} Hz")
            MetadataItem("Codec / MIME", display.mimeType)

            HorizontalDivider()
            Text("原始 Tag Map (可展开核对)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            ExpandableTagMapSection("ID3v2 Map", display.id3v2Map)
            ExpandableTagMapSection("MP4 Map", display.mp4Map)
            ExpandableTagMapSection("Xiph Map", display.xiphMap)
        }
    }
}

@Composable
private fun MetadataItem(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ExpandableTagMapSection(title: String, map: Map<String, List<String>>) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                "$title (${map.size} 项) ${if (expanded) "▲ 收起" else "▼ 展开"}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            if (expanded) {
                Spacer(modifier = Modifier.height(4.dp))
                if (map.isEmpty()) {
                    Text("（无数据）", style = MaterialTheme.typography.bodySmall)
                } else {
                    map.toSortedMap().forEach { (k, v) ->
                        Text(
                            "$k -> [${v.joinToString(", ") { "\"$it\"" }}]",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
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
private fun CandidateRow(
    candidate: CandidateAudio,
    isSelected: Boolean,
    onInspect: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onInspect() },
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(candidate.fileName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${candidate.format.label} · ${formatBytes(candidate.size)} · 来源: ${candidate.rootLabel}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onInspect) {
                Text(if (isSelected) "已选中" else "查看标签")
            }
        }
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
