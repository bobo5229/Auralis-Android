package com.bobo.auralis.mobile.library.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.bobo.auralis.mobile.library.db.dao.GraphDao
import com.bobo.auralis.mobile.library.db.dao.LibraryRootDao
import com.bobo.auralis.mobile.library.db.dao.SourceDao
import com.bobo.auralis.mobile.library.db.dao.TrackDao
import com.bobo.auralis.mobile.library.db.entity.AlbumArtistCrossRef
import com.bobo.auralis.mobile.library.db.entity.AlbumEntity
import com.bobo.auralis.mobile.library.db.entity.ArtistEntity
import com.bobo.auralis.mobile.library.db.entity.GenreEntity
import com.bobo.auralis.mobile.library.db.entity.LibraryRootEntity
import com.bobo.auralis.mobile.library.db.entity.SourceMetadataEntity
import com.bobo.auralis.mobile.library.db.entity.TrackActiveSourceEntity
import com.bobo.auralis.mobile.library.db.entity.TrackArtistCrossRef
import com.bobo.auralis.mobile.library.db.entity.TrackEntity
import com.bobo.auralis.mobile.library.db.entity.TrackGenreCrossRef
import com.bobo.auralis.mobile.library.db.entity.TrackSourceEntity

/**
 * The Auralis library database.
 *
 * Frozen by `docs/phase3a/03_ROOM_SCHEMA.md` §48, with the delete strategy of §49
 * and the indexes of §50 declared on the entities themselves.
 *
 * The graph is:
 *
 * ```text
 * LibraryRoot 1 ─── N TrackSource
 * Track       1 ─── N TrackSource
 * Track       1 ─── 0..1 TrackActiveSource
 * Track       N ─── 0..1 Album
 * Track       N ─── M Artist
 * Album       N ─── M Artist
 * Track       N ─── M Genre
 * TrackSource 1 ─── 0..1 SourceMetadata
 * ```
 *
 * `version = 1` with `exportSchema = true` is the migration baseline: the generated
 * schema is committed under `app/schemas`, so any later version bump can be diffed
 * against it and shipped with an explicit `Migration` instead of relying on
 * destructive fallback. Never enable `fallbackToDestructiveMigration` here — the
 * library is rebuildable from the roots, but user playlists are not.
 *
 * Foreign keys are enforced: Room turns on SQLite foreign key support, so the
 * delete rules in §49 are real behaviour rather than documentation.
 */
@Database(
    entities = [
        LibraryRootEntity::class,
        TrackEntity::class,
        TrackSourceEntity::class,
        SourceMetadataEntity::class,
        TrackActiveSourceEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        GenreEntity::class,
        TrackArtistCrossRef::class,
        AlbumArtistCrossRef::class,
        TrackGenreCrossRef::class,
    ],
    version = AuralisDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(AuralisConverters::class)
abstract class AuralisDatabase : RoomDatabase() {

    abstract fun libraryRootDao(): LibraryRootDao

    abstract fun trackDao(): TrackDao

    abstract fun sourceDao(): SourceDao

    abstract fun graphDao(): GraphDao

    companion object {
        /** Schema version. Bump this with a matching `Migration`, never without one. */
        const val VERSION = 1

        const val NAME = "auralis.db"

        @Volatile
        private var instance: AuralisDatabase? = null

        /** Process-wide instance. */
        fun get(context: Context): AuralisDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        /** Builds a separate instance, for tests that need an isolated database. */
        fun build(context: Context, name: String = NAME): AuralisDatabase =
            Room.databaseBuilder(context.applicationContext, AuralisDatabase::class.java, name)
                .build()
    }
}
