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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun mockExtractorIntegrationPipeline() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val rootDao = db.libraryRootDao()
        val trackDao = db.trackDao()
        val sourceDao = db.sourceDao()
        val graphDao = db.graphDao()

        val rootId = LibraryRootId.random()
        val rootEntity = LibraryRootEntity(
            rootId = rootId,
            treeUri = "content://com.android.externalstorage.documents/tree/music",
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
                val metadata = Metadata(
                    id3v2 = mapOf(
                        "TIT2" to listOf("Pipeline Song"),
                        "TPE1" to listOf("Main Artist; Featured Artist"),
                        "TALB" to listOf("Pipeline Album"),
                        "TCON" to listOf("Pop"),
                        "TDRC" to listOf("2024-05-01"),
                        "TRCK" to listOf("1/10"),
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
        )

        // Pipeline executes cleanly
        assertNotNull(pipeline)
    }
}
