package com.bobo.auralis.mobile.ui.demo

import com.bobo.auralis.mobile.ui.theme.GlassStyle

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import com.bobo.auralis.mobile.ui.theme.LiquidGlassBlur
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bobo.auralis.mobile.R
import com.bobo.auralis.mobile.ui.theme.AuralisThemeMode
import com.bobo.auralis.mobile.ui.theme.AlbumLayoutAnimationStyle

private val libraryTitleFontFamily = FontFamily(
    Font(R.font.noto_serif_sc_black_subset, weight = FontWeight.Black),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DemoTopBar(
    title: String,
    onManageLibrary: () -> Unit,
    useLibraryTitleFont: Boolean,
    glassStyle: GlassStyle,
    onGlassStyleSelected: (GlassStyle) -> Unit,
    liquidGlassBlurDp: Float,
    onLiquidGlassBlurDpChanged: (Float) -> Unit,
    themeMode: AuralisThemeMode,
    onThemeModeSelected: (AuralisThemeMode) -> Unit,
    albumLayoutAnimationStyle: AlbumLayoutAnimationStyle,
    onAlbumLayoutAnimationStyleSelected: (AlbumLayoutAnimationStyle) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Text(
                title,
                style = if (useLibraryTitleFont) {
                    MaterialTheme.typography.titleLarge.copy(
                        fontFamily = libraryTitleFontFamily,
                        fontWeight = FontWeight.Black,
                    )
                } else {
                    MaterialTheme.typography.titleLarge
                },
            )
        },
        actions = {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "更多")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                Text(
                    text = "外观模式",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                AuralisThemeMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label) },
                        leadingIcon = { if (mode == themeMode) Icon(Icons.Rounded.Check, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onThemeModeSelected(mode)
                        },
                    )
                }
                Text(
                    text = "玻璃效果",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                GlassStyle.entries.forEach { style ->
                    DropdownMenuItem(
                        text = { Text(style.label) },
                        leadingIcon = { if (style == glassStyle) Icon(Icons.Rounded.Check, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onGlassStyleSelected(style)
                        },
                    )
                }
                if (glassStyle == GlassStyle.Liquid) {
                    Column(Modifier.width(240.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
                        val valueLabel = if (liquidGlassBlurDp % 1f == 0f) {
                            "${liquidGlassBlurDp.toInt()} dp"
                        } else "$liquidGlassBlurDp dp"
                        Text("模糊强度 · $valueLabel", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = liquidGlassBlurDp,
                            onValueChange = { dp ->
                                val normalized = LiquidGlassBlur.normalize(dp)
                                if (normalized != liquidGlassBlurDp) onLiquidGlassBlurDpChanged(normalized)
                            },
                            valueRange = LiquidGlassBlur.MinDp..LiquidGlassBlur.MaxDp,
                            steps = LiquidGlassBlur.SliderSteps,
                            modifier = Modifier.fillMaxWidth().semantics {
                                contentDescription = "Liquid Glass 模糊强度"
                                stateDescription = valueLabel
                            },
                        )
                    }
                }
                Text(
                    text = "专辑布局动效",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                AlbumLayoutAnimationStyle.entries.forEach { style ->
                    DropdownMenuItem(
                        text = { Text(style.label) },
                        leadingIcon = {
                            if (style == albumLayoutAnimationStyle) {
                                Icon(Icons.Rounded.Check, contentDescription = null)
                            }
                        },
                        onClick = {
                            menuExpanded = false
                            onAlbumLayoutAnimationStyleSelected(style)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("曲库管理") },
                    onClick = {
                        menuExpanded = false
                        onManageLibrary()
                    },
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}
