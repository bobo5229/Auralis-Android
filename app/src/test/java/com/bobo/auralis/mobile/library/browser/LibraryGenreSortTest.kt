package com.bobo.auralis.mobile.library.browser

import com.bobo.auralis.mobile.library.model.GenreId
import com.bobo.auralis.mobile.library.model.TrackId
import com.bobo.auralis.mobile.library.db.dao.TrackGenreNameRow
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryGenreSortTest {
    @Test fun projectsExactMembershipWithMultipleGenresAndKeepsSongOrder() {
        val rock = GenreId.random()
        val pop = GenreId.random()
        val orphan = GenreId.random()
        val first = song("First")
        val second = song("Second").copy(isPlayable = false)
        val untagged = song("Untagged")
        val rows = listOf(
            TrackGenreNameRow(rock, "Rock", second.trackId),
            TrackGenreNameRow(pop, "Pop", first.trackId),
            TrackGenreNameRow(rock, "Rock", first.trackId),
            TrackGenreNameRow(rock, "Rock", first.trackId),
            TrackGenreNameRow(orphan, "Rock", TrackId.random()),
        )
        val result = projectLibraryGenres(listOf(first, second, untagged), rows)
        assertEquals(listOf(pop, rock), result.map { it.genreId })
        assertEquals(listOf(first), result[0].tracks)
        assertEquals(listOf(first, second), result[1].tracks)
        assertEquals(emptyList<LibraryGenre>(), projectLibraryGenres(listOf(first), emptyList()))
    }

    private fun song(title: String) = LibraryTrack(
        TrackId.random(), null, title, emptyList(), null, null, null, null, true, null, null,
    )

    @Test fun sortsAlphabeticallyAndByPinyinWithoutChangingNames() {
        val names = listOf("摇滚", "Rock", "流行", "Jazz", "Hip-hop/Rap", "R&B/Soul")
        val genres = names.map { LibraryGenre(GenreId.random(), it) }
        assertEquals(
            listOf("Hip-hop/Rap", "Jazz", "流行", "R&B/Soul", "Rock", "摇滚"),
            sortLibraryGenres(genres).map { it.displayName },
        )
        assertEquals(genres.toSet(), sortLibraryGenres(genres).toSet())
    }

    @Test fun preservesDistinctIdsAndHasStableTieOrdering() {
        val genres = listOf("Rock", "rock", "Rock").map { LibraryGenre(GenreId.random(), it) }
        assertEquals(3, sortLibraryGenres(genres).size)
        assertEquals(sortLibraryGenres(genres), sortLibraryGenres(genres.reversed()))
        assertEquals(emptyList<LibraryGenre>(), sortLibraryGenres(emptyList()))
    }
}
