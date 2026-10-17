package com.bobo.auralis.mobile.library.saf

import org.junit.Assert.assertEquals
import org.junit.Test

class SafSiblingFilesTest {
    @Test fun nestedUnicodeDirectoryNamesArePreserved() {
        assertEquals(listOf("艺术家", "专辑 1"), siblingDirectorySegments("primary/Music", "primary/Music/艺术家/专辑 1/song.flac"))
    }
    @Test fun relativePathsAndRepeatedSeparatorsRetainExistingBehavior() {
        assertEquals(listOf("Artist", "Album"), siblingDirectorySegments("primary/Music", "Artist//Album/song.flac"))
    }
}
