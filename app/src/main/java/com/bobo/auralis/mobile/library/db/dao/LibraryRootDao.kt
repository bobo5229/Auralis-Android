package com.bobo.auralis.mobile.library.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bobo.auralis.mobile.library.db.entity.LibraryRootEntity
import com.bobo.auralis.mobile.library.model.LibraryRootId

/**
 * Data access for configured library roots.
 *
 * Pure persistence: whether a root should be added, removed or marked
 * unavailable is decided above this layer, not here.
 */
@Dao
interface LibraryRootDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(root: LibraryRootEntity)

    @Update
    suspend fun update(root: LibraryRootEntity)

    @Query("DELETE FROM library_root WHERE rootId = :rootId")
    suspend fun delete(rootId: LibraryRootId)

    /** Roots in addition order, which is also duplicate-candidate priority (§20). */
    @Query("SELECT * FROM library_root ORDER BY priority ASC")
    suspend fun getAllByPriority(): List<LibraryRootEntity>

    @Query("SELECT * FROM library_root WHERE rootId = :rootId")
    suspend fun findById(rootId: LibraryRootId): LibraryRootEntity?

    @Query("SELECT * FROM library_root WHERE treeUri = :treeUri")
    suspend fun findByTreeUri(treeUri: String): LibraryRootEntity?

    /**
     * Highest priority currently stored, so the next root can continue the
     * monotonic sequence without renumbering existing roots (§20).
     */
    @Query("SELECT MAX(priority) FROM library_root")
    suspend fun maxPriority(): Int?

    @Query("SELECT COUNT(*) FROM library_root")
    suspend fun count(): Int
}
