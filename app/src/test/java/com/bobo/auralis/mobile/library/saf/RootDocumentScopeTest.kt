package com.bobo.auralis.mobile.library.saf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootDocumentScopeTest {
    @Test fun documentAncestryRespectsVolumesAndPathComponents() {
        assertTrue(treeContainsDocument("primary:", "primary:Music/Album/song.flac"))
        assertTrue(treeContainsDocument("primary:Music", "primary:Music/Album/song.flac"))
        assertFalse(treeContainsDocument("primary:Music", "primary:Music2/song.flac"))
        assertFalse(treeContainsDocument("primary:Music/Album", "primary:Music/song.flac"))
        assertFalse(treeContainsDocument("primary:Music", "other:Music/song.flac"))
        assertFalse(treeContainsDocument("opaque-root", "opaque-root/song"))
    }
}
