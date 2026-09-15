package com.bobo.auralis.mobile.library.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `docs/phase3a/02_SOURCE_MODEL.md` §22, §23, §24 and §45.
 *
 * These vocabularies are frozen: Step D stores them as strings in
 * `TrackSourceEntity`, so a silent rename here would corrupt state mapping in the
 * database. The exact value sets are therefore asserted rather than assumed.
 */
class SourceStatesTest {

    @Test
    fun `availability states are exactly the three from section 22`() {
        assertEquals(
            listOf("AVAILABLE", "MISSING", "UNREACHABLE"),
            SourceAvailabilityState.entries.map { it.name },
        )
    }

    @Test
    fun `parse states are exactly the three from section 23`() {
        assertEquals(
            listOf("NOT_PARSED", "PARSED", "FAILED"),
            ParseState.entries.map { it.name },
        )
    }

    @Test
    fun `playability states are exactly the three from section 24`() {
        assertEquals(
            listOf("UNKNOWN", "PLAYABLE", "UNPLAYABLE"),
            PlayabilityState.entries.map { it.name },
        )
    }

    @Test
    fun `unreachable is a distinct state and cannot stand in for missing`() {
        // §22: a temporarily unreachable directory must not look like a deletion.
        assertEquals(false, SourceAvailabilityState.UNREACHABLE == SourceAvailabilityState.MISSING)
        assertEquals(false, SourceAvailabilityState.UNREACHABLE == SourceAvailabilityState.AVAILABLE)
    }

    @Test
    fun `a failed parse is not the same as a never parsed source`() {
        assertEquals(false, ParseState.FAILED == ParseState.NOT_PARSED)
    }

    @Test
    fun `unconfirmed playability is a real state not an absence`() {
        // §24: a freshly scanned source is UNKNOWN, and that is enough to be an
        // active-source candidate; it is not "unknown == missing".
        assertTrue(PlayabilityState.entries.contains(PlayabilityState.UNKNOWN))
        assertEquals(false, PlayabilityState.UNKNOWN == PlayabilityState.UNPLAYABLE)
    }

    @Test
    fun `source failure keeps the three diagnostic fields with an optional message`() {
        val failure = SourceFailure(stage = "extraction", code = "PROVIDER_FAILED")
        assertEquals("extraction", failure.stage)
        assertEquals("PROVIDER_FAILED", failure.code)
        assertNull(failure.message)

        assertEquals("boom", failure.copy(message = "boom").message)
    }

    @Test
    fun `observed track source keeps exactly the fields from section 45`() {
        assertEquals(
            setOf(
                "documentkey",
                "root",
                "uri",
                "relativepath",
                "filename",
                "sizebytes",
                "modifiedms",
                "format",
                "metadata",
            ),
            instanceFieldNames(ObservedTrackSource::class.java),
        )
    }

    @Test
    fun `an observation carries no database identity or state`() {
        // §45: nothing here may be a Room entity field such as trackId, sourceId
        // or the three states; those only exist after reconciliation.
        val names = instanceFieldNames(ObservedTrackSource::class.java)

        assertEquals(false, names.any { it.contains("trackid") })
        assertEquals(false, names.any { it.contains("sourceid") })
        assertEquals(false, names.any { it.contains("state") })
    }

    @Test
    fun `resolved observation carries the identity outputs from section 45`() {
        assertEquals(
            setOf(
                "observation",
                "albumidentity",
                "albumkey",
                "trackidentity",
                "trackkey",
                "trackkeystrength",
            ),
            instanceFieldNames(ResolvedObservation::class.java),
        )
    }
}
