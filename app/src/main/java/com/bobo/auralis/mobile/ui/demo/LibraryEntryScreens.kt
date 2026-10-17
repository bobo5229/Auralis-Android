package com.bobo.auralis.mobile.ui.demo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun LibraryLoadingScreen() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun DemoPlaceholderScreen(label: String, bottomPadding: Dp) {
    Box(
        modifier = Modifier.fillMaxSize().padding(bottom = bottomPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
fun DemoWelcomeScreen(onManageLibrary: () -> Unit) {
    val primaryDecoration = MaterialTheme.colorScheme.primaryContainer
    val secondaryDecoration = MaterialTheme.colorScheme.secondaryContainer
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(primaryDecoration, size.width * 0.72f, Offset(size.width * 0.92f, size.height * 0.12f))
            drawCircle(secondaryDecoration, size.width * 0.5f, Offset(size.width * 0.04f, size.height * 0.78f))
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 42.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("AURALIS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(54.dp))
                Text("你的音乐，\n在这里成为收藏。", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(18.dp))
                Text(
                    "只读取你选择的本地目录。没有账号，没有云端，也不会修改音乐文件。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureLine("多目录曲库", "由你决定音乐来自哪里")
                FeatureLine("专辑优先", "保留发行版本和完整元数据")
                FeatureLine("纯本地播放", "离线也拥有完整体验")
                Spacer(Modifier.height(12.dp))
                Button(onClick = onManageLibrary, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("选择并扫描音乐目录")
                }
                Text(
                    "目录管理暂由技术诊断页承载，扫描结果会自动回到这里。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FeatureLine(title: String, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun DemoArtistsScreen(artists: List<Pair<String, Int>>, bottomPadding: Dp) {
    LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = bottomPadding)) {
        item {
            Text("艺术家", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(14.dp))
        }
        items(artists, key = { it.first }) { (artist, trackCount) ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(58.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(artist.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(artist, style = MaterialTheme.typography.titleMedium)
                    Text("$trackCount 首曲目", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun DemoPlaylistsScreen(bottomPadding: Dp) {
    Column(
        modifier = Modifier.fillMaxSize().padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("歌单", style = MaterialTheme.typography.headlineMedium)
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(66.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.padding(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("创建第一张歌单", style = MaterialTheme.typography.titleMedium)
                    Text("歌单数据模型将在后续阶段接入", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
