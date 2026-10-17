package com.bobo.auralis.mobile.ui.demo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun DemoLibraryScreen(
    bottomPadding: Dp,
    onAlbumsClick: () -> Unit,
    onTracksClick: () -> Unit,
    onPlaylistsClick: () -> Unit,
    onGenresClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, bottom = bottomPadding),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        LibraryCategoryRow("专辑", Icons.Rounded.LibraryMusic, onAlbumsClick)
        LibraryCategoryDivider()
        LibraryCategoryRow("歌曲", Icons.Rounded.MusicNote, onTracksClick)
        LibraryCategoryDivider()
        LibraryCategoryRow("歌单", Icons.AutoMirrored.Rounded.QueueMusic, onPlaylistsClick)
        LibraryCategoryDivider()
        LibraryCategoryRow("流派", Icons.Rounded.Category, onGenresClick)
    }
}

@Composable
private fun LibraryCategoryDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 38.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
    )
}

@Composable
private fun LibraryCategoryRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}
