package com.bobo.auralis.mobile.ui.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bobo.auralis.mobile.ui.theme.AlbumLayoutMode

@Composable
internal fun AlbumsLayoutMenu(
    selectedMode: AlbumLayoutMode,
    onModeSelected: (AlbumLayoutMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var menuContentPresent by remember { mutableStateOf(false) }
    var pendingMode by remember { mutableStateOf<AlbumLayoutMode?>(null) }
    LaunchedEffect(expanded, menuContentPresent, pendingMode) {
        if (!expanded && !menuContentPresent) {
            val mode = pendingMode ?: return@LaunchedEffect
            pendingMode = null
            onModeSelected(mode)
        }
    }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.ViewModule, contentDescription = "切换专辑布局")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // Material keeps the popup composed through its exit animation.
            // Start the grid transition only after that popup has been removed.
            DisposableEffect(Unit) {
                menuContentPresent = true
                onDispose { menuContentPresent = false }
            }
            Text(
                text = "封面网格",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            AlbumLayoutMode.entries.forEach { mode ->
                if (mode == AlbumLayoutMode.List) {
                    Text(
                        text = "其他布局",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    leadingIcon = {
                        if (mode == selectedMode) {
                            Icon(Icons.Rounded.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        pendingMode = mode.takeIf { it != selectedMode }
                        expanded = false
                    },
                )
            }
        }
    }
}
