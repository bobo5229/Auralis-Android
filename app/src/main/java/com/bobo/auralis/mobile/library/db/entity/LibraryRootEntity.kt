package com.bobo.auralis.mobile.library.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bobo.auralis.mobile.library.model.LibraryRootId

/**
 * Whether a configured library root is currently reachable.
 *
 * Frozen by `docs/phase3a/03_ROOM_SCHEMA.md` §21. This is a two-value vocabulary
 * and deliberately not the same type as
 * [com.bobo.auralis.mobile.library.model.SourceAvailabilityState]:
 *
 * - `UNAVAILABLE` means the provider or its permission is temporarily gone. The
 *   root row stays, its sources stay, and no source may be declared missing.
 * - Removing a root is a different action entirely: the root configuration is
 *   removed, which is not representable as a state and is modelled as deleting
 *   the row.
 */
enum class RootAvailabilityState {
    AVAILABLE,
    UNAVAILABLE,
}

/**
 * A user-selected SAF library root.
 *
 * Frozen by `docs/phase3a/03_ROOM_SCHEMA.md` §20.
 *
 * [priority] is the root's addition order and the future duplicate-candidate
 * priority. It is a stored, monotonically increasing value rather than the
 * current list index, because recomputing it per scan would change the ordering
 * of every remaining root the moment one root is removed. Deleting the root with
 * priority 2 simply leaves 0, 1, 3 — no renumbering.
 *
 * This replaces the temporary `SafRootStore` SharedPreferences spike; the
 * migration off it is Step I.
 */
@Entity(
    tableName = "library_root",
    indices = [Index(value = ["treeUri"], unique = true)],
)
data class LibraryRootEntity(
    @PrimaryKey val rootId: LibraryRootId,
    val treeUri: String,
    val displayPath: String,
    /** Addition order. Smaller wins when ordering duplicate candidates (§27). */
    val priority: Int,
    val availabilityState: RootAvailabilityState,
    val lastSuccessfulScanAt: Long?,
    val lastAttemptedScanAt: Long?,
    val createdAt: Long,
)
