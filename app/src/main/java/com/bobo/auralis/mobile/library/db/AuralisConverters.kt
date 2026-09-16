package com.bobo.auralis.mobile.library.db

import androidx.room.TypeConverter
import com.bobo.auralis.mobile.library.identity.TrackKeyStrength
import com.bobo.auralis.mobile.library.model.ParseState
import com.bobo.auralis.mobile.library.model.PlayabilityState
import com.bobo.auralis.mobile.library.model.SourceAvailabilityState
import com.bobo.auralis.mobile.library.scan.AudioFormat
import java.util.UUID

/**
 * Room type converters.
 *
 * Frozen by `docs/phase3a/03_ROOM_SCHEMA.md` §52: converters exist only for real
 * value-type data — identifiers, enums and the per-source list snapshot. Anything
 * the user actually queries (artist list, genre list, album relationship) stays
 * normalized into its own table and must never be folded back into a column.
 *
 * Entity ids are `@JvmInline value class XxxId(UUID)`. Room's KSP processor
 * supports value classes as entity fields, including primary keys, and resolves
 * their storage through the underlying type — so the single `UUID ↔ String` pair
 * below covers every id column and no per-id converter is needed. SQLite affinity
 * for those columns is TEXT, as §2 requires.
 *
 * Only one direction pair is written per type; the state vocabularies are frozen
 * by §22, §23 and §24 and pinned by `SourceStatesTest`, because a silent rename
 * would corrupt the meaning of rows that are already stored.
 */
class AuralisConverters {

    @TypeConverter
    fun uuidToString(value: UUID?): String? = value?.toString()

    @TypeConverter
    fun stringToUuid(value: String?): UUID? = value?.let(UUID::fromString)

    @TypeConverter
    fun availabilityStateToString(value: SourceAvailabilityState?): String? = value?.name

    @TypeConverter
    fun stringToAvailabilityState(value: String?): SourceAvailabilityState? =
        value?.let { SourceAvailabilityState.valueOf(it) }

    @TypeConverter
    fun parseStateToString(value: ParseState?): String? = value?.name

    @TypeConverter
    fun stringToParseState(value: String?): ParseState? = value?.let { ParseState.valueOf(it) }

    @TypeConverter
    fun playabilityStateToString(value: PlayabilityState?): String? = value?.name

    @TypeConverter
    fun stringToPlayabilityState(value: String?): PlayabilityState? =
        value?.let { PlayabilityState.valueOf(it) }

    @TypeConverter
    fun trackKeyStrengthToString(value: TrackKeyStrength?): String? = value?.name

    @TypeConverter
    fun stringToTrackKeyStrength(value: String?): TrackKeyStrength? =
        value?.let { TrackKeyStrength.valueOf(it) }

    @TypeConverter
    fun audioFormatToString(value: AudioFormat?): String? = value?.name

    @TypeConverter
    fun stringToAudioFormat(value: String?): AudioFormat? = value?.let { AudioFormat.valueOf(it) }

    /**
     * A source snapshot keeps its own artist / album artist / genre lists in one
     * column. §13 explicitly allows this because that table is a per-source cache,
     * not the graph the library browses by.
     */
    @TypeConverter
    fun stringListToJson(value: List<String>?): String? = value?.let(StringListCodec::encode)

    @TypeConverter
    fun jsonToStringList(value: String?): List<String>? = value?.let(StringListCodec::decode)
}
