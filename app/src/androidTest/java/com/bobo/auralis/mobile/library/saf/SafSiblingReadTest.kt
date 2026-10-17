package com.bobo.auralis.mobile.library.saf

import android.net.Uri
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bobo.auralis.mobile.artwork.LibraryArtworkResolver
import com.bobo.auralis.mobile.library.browser.LibraryBrowserRepository
import com.bobo.auralis.mobile.library.db.AuralisDatabase
import com.bobo.auralis.mobile.lyrics.LyricsLoader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in, read-only smoke test using the device's existing library and SAF grants. */
@RunWith(AndroidJUnit4::class)
class SafSiblingReadTest {
    @Test fun existingLibrarySourcesRemainReadable(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("realLibrary") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val snapshot = LibraryBrowserRepository(AuralisDatabase.get(context)).snapshot.first()
        val tracks = snapshot.tracks.filter { it.isPlayable && it.audioSource?.treeUri != null }.take(12)
        assertTrue("Needs an existing scanned library", tracks.isNotEmpty())
        val siblings = SafSiblingFiles(context.contentResolver)
        val artwork = LibraryArtworkResolver(context)
        val lyrics = LyricsLoader(context)
        var covers = 0
        var texts = 0
        var sidecars = 0
        for (track in tracks) {
            val source = requireNotNull(track.audioSource)
            val tree = Uri.parse(requireNotNull(source.treeUri))
            val directory = siblings.directoryId(tree, source.relativePath)
            assertNotNull(directory)
            val children = requireNotNull(siblings.children(tree, requireNotNull(directory)))
            assertTrue(children.any { it.name == source.fileName && !it.isDirectory })
            sidecars += children.count { !it.isDirectory && it.name.endsWith(".lrc", ignoreCase = true) }
            if (artwork.resolve(requireNotNull(track.artworkSource)) != null) covers++
            if (lyrics.load(source) != null) texts++
        }
        Log.i("SafSiblingReadTest", "sources=${tracks.size}, covers=$covers, lyrics=$texts, directoryLrcEntries=$sidecars")
    }
}
