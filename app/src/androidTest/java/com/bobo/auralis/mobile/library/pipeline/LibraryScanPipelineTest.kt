package com.bobo.auralis.mobile.library.pipeline

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bobo.auralis.mobile.library.db.AuralisDatabase
import com.bobo.auralis.mobile.library.db.entity.LibraryRootEntity
import com.bobo.auralis.mobile.library.db.entity.RootAvailabilityState
import com.bobo.auralis.mobile.library.metadata.Metadata
import com.bobo.auralis.mobile.library.metadata.MetadataExtractor
import com.bobo.auralis.mobile.library.metadata.MetadataResult
import com.bobo.auralis.mobile.library.metadata.MetadataTarget
import com.bobo.auralis.mobile.library.metadata.Properties
import com.bobo.auralis.mobile.library.model.LibraryRootId
import com.bobo.auralis.mobile.library.model.SourceAvailabilityState
import com.bobo.auralis.mobile.library.model.ParseState
import com.bobo.auralis.mobile.library.model.PlayabilityState
import com.bobo.auralis.mobile.library.saf.SafDocumentKey
import com.bobo.auralis.mobile.library.saf.SafFile
import com.bobo.auralis.mobile.library.saf.SafLocation
import com.bobo.auralis.mobile.library.saf.SafScanEvent
import java.io.FileNotFoundException
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android instrumentation tests for Step G/H LibraryScanPipeline.
 *
 * Verifies pipeline flow from discovery to metadata interpretation to Room projection.
 */
@RunWith(AndroidJUnit4::class)
class LibraryScanPipelineTest {

    private lateinit var db: AuralisDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AuralisDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun pipelineEmptyRootsReturnsCleanReport() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val pipeline = LibraryScanPipeline(context, db)

        val report = pipeline.executeScan(emptyList())
        assertEquals(0, report.totalFilesDiscovered)
        assertEquals(0, report.audioCandidatesDiscovered)
        assertEquals(0, report.parsedSuccessfully)
        assertEquals(0, report.rootsAttempted)
    }

    @Test
    fun fileOpenFailuresArePersistedAndDoNotStopTheRemainingScan() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val rootDao = db.libraryRootDao()
        val trackDao = db.trackDao()
        val sourceDao = db.sourceDao()
        val graphDao = db.graphDao()

        val rootId = LibraryRootId.random()
        val rootEntity = LibraryRootEntity(
            rootId = rootId,
            treeUri = "content://com.android.externalstorage.documents/tree/primary%3AMusic",
            displayPath = "/Music",
            priority = 0,
            availabilityState = RootAvailabilityState.AVAILABLE,
            lastSuccessfulScanAt = null,
            lastAttemptedScanAt = null,
            createdAt = 1000L,
        )
        rootDao.insert(rootEntity)

        // Mock extractor producing valid metadata
        val mockExtractor = object : MetadataExtractor {
            override suspend fun extract(target: MetadataTarget): MetadataResult {
                when (target.fileName) {
                    "removed.flac" -> throw FileNotFoundException("removed after discovery")
                    "denied.flac" -> throw SecurityException("permission revoked")
                }
                val metadata = Metadata(
                    id3v2 = mapOf(
                        "TIT2" to listOf("Pipeline Song"),
                        "TPE1" to listOf("Main Artist; Featured Artist"),
                        "TPE2" to listOf("Album Artist"),
                        "TALB" to listOf("Pipeline Album"),
                        "TCON" to listOf("Pop"),
                        "TDRC" to listOf("2024-05-01"),
                        "TRCK" to listOf("1/10"),
                        "TPOS" to listOf("1/1"),
                    ),
                    xiph = emptyMap(),
                    mp4 = emptyMap(),
                    cover = null,
                    properties = Properties(
                        mimeType = "audio/mpeg",
                        durationMs = 200_000L,
                        bitrateKbps = 320,
                        sampleRateHz = 44_100,
                    ),
                )
                return MetadataResult.Success(metadata)
            }
        }

        val pipeline = LibraryScanPipeline(
            context = context,
            database = db,
            extractor = mockExtractor,
            scan = {
                val root = SafLocation.restore(Uri.parse(rootEntity.treeUri))
                listOf("removed.flac", "denied.flac", "good.flac").map { name ->
                    SafScanEvent.Found(SafFile(
                        uri = Uri.parse("content://test/$name"),
                        path = root.path.file(name),
                        documentKey = SafDocumentKey("test", name),
                        mimeType = "audio/flac", size = 1024L, modifiedMs = 1000L,
                        parent = null, root = root,
                    ))
                }.asFlow()
            },
        )

        val report = pipeline.executeScan(listOf(rootEntity), scanSessionId = 1L)
        assertEquals(3, report.audioCandidatesDiscovered)
        assertEquals(2, report.parseFailed)
        assertEquals(1, report.parsedSuccessfully)
        assertEquals(1, report.rootsCompleted)
        assertEquals(3, trackDao.count())
        val removed = sourceDao.findByDocumentKey("test", "removed.flac")!!
        assertEquals("FileNotFoundException", removed.failureCode)
        assertEquals("removed after discovery", removed.failureMessage)
        val denied = sourceDao.findByDocumentKey("test", "denied.flac")!!
        assertEquals("SecurityException", denied.failureCode)
        val good = sourceDao.findByDocumentKey("test", "good.flac")!!
        assertEquals(good.sourceId, sourceDao.findActiveSource(good.trackId)?.sourceId)
        assertEquals("Pipeline Song", trackDao.findById(good.trackId)?.title)
        assertEquals(2, graphDao.trackArtists(good.trackId).size)
    }

    private suspend fun addRoot(priority: Int, directory: String = "Music"): LibraryRootEntity {
        val root = LibraryRootEntity(
            LibraryRootId.random(),
            "content://com.android.externalstorage.documents/tree/primary%3A$directory",
            "/$directory", priority, RootAvailabilityState.AVAILABLE, null, null, 1L,
        )
        db.libraryRootDao().insert(root)
        return root
    }

    private fun file(root: LibraryRootEntity, path: String, key: String = path): SafScanEvent.Found {
        val opened = SafLocation.restore(Uri.parse(root.treeUri))
        val filePath = path.split('/').fold(opened.path) { parent, name -> parent.file(name) }
        return SafScanEvent.Found(SafFile(
            Uri.parse("${root.treeUri}/document/$key"), filePath, SafDocumentKey("test", key),
            "audio/flac", 1024L, 1000L, null, opened,
        ))
    }

    private fun metadata(title: String = "Song"): MetadataResult = MetadataResult.Success(Metadata(
        id3v2 = mapOf(
            "TIT2" to listOf(title), "TPE1" to listOf("Artist A; Artist B"),
            "TPE2" to listOf("Album Artist"), "TALB" to listOf("Album"),
            "TDRC" to listOf("2024-01-01"), "TPOS" to listOf("1"), "TRCK" to listOf("1"),
        ),
        xiph = emptyMap(), mp4 = emptyMap(), cover = null,
        properties = Properties("audio/flac", 1000L, 320, 44100),
    ))

    private suspend fun scanEvents(
        roots: List<LibraryRootEntity>, events: List<SafScanEvent>, session: Long,
        extract: (MetadataTarget) -> MetadataResult = { metadata(it.fileName) },
    ): ScanSessionReport = LibraryScanPipeline(
        ApplicationProvider.getApplicationContext(), db,
        extractor = object : MetadataExtractor {
            override suspend fun extract(target: MetadataTarget) = extract(target)
        },
        scan = { events.asFlow() },
    ).executeScan(roots, session)

    @Test
    fun failedSubdirectoryIsUnreachableWhileCleanMissingSiblingIsMissing() = runBlocking {
        val root = addRoot(0)
        scanEvents(listOf(root), listOf(file(root, "Album/lost.flac"), file(root, "Album2/gone.flac")), 1L)
        val opened = SafLocation.restore(Uri.parse(root.treeUri))
        scanEvents(listOf(root), listOf(SafScanEvent.DirectoryUnavailable(
            opened, opened.path.file("Album"), SecurityException("denied"),
        )), 2L)
        assertEquals(SourceAvailabilityState.UNREACHABLE,
            db.sourceDao().findByDocumentKey("test", "Album/lost.flac")!!.availabilityState)
        assertEquals(SourceAvailabilityState.MISSING,
            db.sourceDao().findByDocumentKey("test", "Album2/gone.flac")!!.availabilityState)
    }

    @Test
    fun overlappingRootOwnershipIsStableInBothDiscoveryOrders() = runBlocking {
        val earlier = addRoot(0)
        val later = addRoot(1, "Music%2FAlbum")
        for ((index, roots) in listOf(listOf(later, earlier), listOf(earlier, later)).withIndex()) {
            val report = scanEvents(listOf(earlier, later), roots.map { file(it, "song.flac", "same-document") }, index + 1L)
            assertEquals(2, report.totalFilesDiscovered)
            assertEquals(2, report.audioCandidatesDiscovered)
            assertEquals(1, report.uniqueAudioCandidatesDiscovered)
            assertEquals(1, report.parsedSuccessfully)
            assertEquals(0, report.parseFailed)
            assertEquals(0, report.extractionFailures)
            // Actual work differs: two uncached aliases first, one cached earlier root next.
            assertEquals(if (index == 0) 2 else 0, report.cacheMisses)
            assertEquals(if (index == 0) 0 else 1, report.cacheHits)
            val source = db.sourceDao().findByDocumentKey("test", "same-document")!!
            assertEquals(earlier.rootId, source.rootId)
            assertEquals(file(earlier, "song.flac", "same-document").file.uri.toString(), source.uri)
            assertEquals(1, db.sourceDao().getAll().size)
        }
    }

    @Test
    fun laterFailedAliasDoesNotInvalidateAnAlreadyOpenedPhysicalFile() = runBlocking {
        val earlier = addRoot(0)
        val later = addRoot(1, "Music%2FAlbum")
        scanEvents(listOf(earlier, later), listOf(file(later, "song.flac", "same"), file(earlier, "song.flac", "same")), 1L) {
            if (it.uri.toString().startsWith(earlier.treeUri)) throw SecurityException("denied")
            metadata()
        }
        val source = db.sourceDao().findByDocumentKey("test", "same")!!
        assertEquals(later.rootId, source.rootId)
        assertEquals(ParseState.PARSED, source.parseState)
    }

    @Test
    fun failedActiveSourceElectsBackupEvenWhenBackupWasProcessedFirst() = runBlocking {
        val root = addRoot(0)
        scanEvents(listOf(root), listOf(file(root, "A.flac"), file(root, "B.flac")), 1L) { metadata() }
        val active = db.sourceDao().findByDocumentKey("test", "A.flac")!!
        // Force fresh extraction of A without changing the file signature.
        db.sourceDao().update(active.copy(parseState = ParseState.FAILED))
        scanEvents(listOf(root), listOf(file(root, "B.flac"), file(root, "A.flac")), 2L) {
            if (it.fileName == "A.flac") MetadataResult.NotAudio else metadata()
        }
        val backup = db.sourceDao().findByDocumentKey("test", "B.flac")!!
        assertEquals(backup.sourceId, db.sourceDao().findActiveSource(active.trackId)?.sourceId)
    }
}
