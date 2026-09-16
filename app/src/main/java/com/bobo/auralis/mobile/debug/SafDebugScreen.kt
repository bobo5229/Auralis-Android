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
import com.bobo.auralis.mobile.library.db.entity.LibraryRootEntity
import com.bobo.auralis.mobile.library.metadata.AuralisMetadata
import com.bobo.auralis.mobile.library.metadata.RawMetadataDisplay
import com.bobo.auralis.mobile.library.scan.CandidateAudio

/**
 * Technical Spike Phase 3A debug inspection screen.
 *
 * Implements `docs/phase3a/08_DEBUG_ACCEPTANCE.md` §62:
 * - Displays TrackId, TrackKey, TrackKey strength, SourceId, SafDocumentKey, root priority,
 *   active source, and source states (availability, parse, playability).
 * - Displays Room-backed library roots and scan pipeline stats.
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
            item { Text("Auralis Phase 3A Inspection", style = MaterialTheme.typography.headlineSmall) }
            item {
                Text(
                    "技术验证页：Room 数据库图谱、对账结果与真机 SAF 行为验收",
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
                    OutlinedButton(
                        onClick = { controller.refreshDatabaseTracks() },
                        enabled = !controller.isScanning,
                    ) {
                        Text("刷新曲库")
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
            items(controller.roots, key = { it.rootId.value.toString() }) { root ->
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

            // §62: Reconciled Database Tracks Inspection Section
            item {
                Text(
                    "数据库逻辑曲目 (${controller.inspectedTracks.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (controller.inspectedTracks.isEmpty()) {
                item {
                    Text(
                        "数据库暂无曲目，完成扫描后将在此展示 TrackId、TrackKey、Active Source 及完整图谱关系。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(
                items = controller.inspectedTracks,
                key = { it.track.trackId.value.toString() },
            ) { item ->
                TrackInspectionCard(item)
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
            Text("发现文件总数: ${controller.totalFileCount}")
            Text("发现音频数量: ${controller.candidateCount}")
            Text("曲库曲目数量: ${controller.inspectedTracks.size}")
            Text("元数据抽取缓存: HIT ${controller.cacheHits} / MISS ${controller.cacheMisses}")
            Text("错误数量: ${controller.errorCount}")
            Text("扫描耗时: ${formatDuration(controller.elapsedMs)}")
        }
    }
}

@Composable
private fun TrackInspectionCard(item: TrackInspectionItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (item.activeSourceId != null) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) // Tombstone visual indicator
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = item.track.title ?: "（无 Title）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (item.activeSourceId != null) "ACTIVE" else "TOMBSTONE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (item.activeSourceId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }

            MetadataItem("TrackId", item.track.trackId.value.toString())
            MetadataItem("TrackKey", "${item.track.trackKeyVersion}:${item.track.trackKeyHash.take(16)}... (${item.track.trackKeyStrength.name})")
            MetadataItem("Album", item.albumTitle ?: "（无 / null）")
            MetadataItem("Artists", if (item.artists.isEmpty()) "（无）" else item.artists.joinToString(" ; "))
            MetadataItem("Genres", if (item.genres.isEmpty()) "（无）" else item.genres.joinToString(" ; "))
            MetadataItem("Active SourceId", item.activeSourceId?.value?.toString() ?: "（无活跃源）")

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "物理源列表 (${item.sources.size}):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )

            item.sources.forEach { detail ->
                val s = detail.source
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (detail.isActive) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                s.fileName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                            )
                            if (detail.isActive) {
                                Text("★ ACTIVE WINNER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Text("SourceId: ${s.sourceId.value}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                        Text("DocumentKey: ${s.provider} | ${s.documentId}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                        Text(
                            "RootPriority: ${detail.rootPriority ?: "N/A"} · 状态: ${s.availabilityState.name} / ${s.parseState.name} / ${s.playabilityState.name}",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
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
                    "歌词前100字: ${display.lyricsSnippet.take(100).replace('\n', ' ')}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MetadataItem("Artwork", if (display.hasArtwork) "存在 (${formatBytes(display.artworkBytes.toLong())})" else "无")
            MetadataItem("Codec / MIME", display.mimeType)
            MetadataItem("Duration", "${display.durationMs} ms (${formatDuration(display.durationMs)})")
            MetadataItem("Bitrate", "${display.bitrateKbps} kbps")
            MetadataItem("Sample Rate", "${display.sampleRateHz} Hz")

            Spacer(modifier = Modifier.height(4.dp))
            Text("底层标签键值对映射 (原始提取):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

            ExpandableTagMapSection("ID3v2 标签映射", display.id3v2Map)
            ExpandableTagMapSection("Vorbis / FLAC / Xiph 注释映射", display.xiphMap)
            ExpandableTagMapSection("MP4 / iTunes Atoms 映射", display.mp4Map)
        }
    }
}

@Composable
private fun MetadataItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
    root: LibraryRootEntity,
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
                Text(root.displayPath, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Priority: ${root.priority} · 状态: ${root.availabilityState.name}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    root.treeUri,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = FontFamily.Monospace,
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
