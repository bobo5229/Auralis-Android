package com.bobo.auralis.mobile.ui.demo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bobo.auralis.mobile.library.browser.LibraryGenre

@Composable
fun DemoGenresScreen(
    genres: List<LibraryGenre>,
    bottomPadding: Dp,
    listState: LazyListState,
    onGenreClick: (LibraryGenre) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding),
    ) {
        item(key = "genres-title") {
            Text(
                text = "流派",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 18.dp, top = 8.dp, end = 18.dp, bottom = 12.dp),
            )
        }
        if (genres.isEmpty()) {
            item(key = "genres-empty") {
                Text("暂无流派", modifier = Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        itemsIndexed(genres, key = { _, genre -> genre.genreId.toString() }) { index, genre ->
            Text(
                genre.displayName,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().clickable { onGenreClick(genre) }
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            )
            if (index < genres.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f),
                    thickness = 0.5.dp,
                )
            }
        }
    }
}
