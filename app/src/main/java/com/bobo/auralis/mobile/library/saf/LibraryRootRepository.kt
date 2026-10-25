package com.bobo.auralis.mobile.library.saf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import com.bobo.auralis.mobile.library.db.AuralisDatabase
import com.bobo.auralis.mobile.library.db.entity.LibraryRootEntity
import com.bobo.auralis.mobile.library.db.entity.RootAvailabilityState
import com.bobo.auralis.mobile.library.model.LibraryRootId
import com.bobo.auralis.mobile.library.model.SourceAvailabilityState
import com.bobo.auralis.mobile.library.reconciliation.RoomReconciliationTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository and migrator for SAF library roots.
 *
 * Frozen by `docs/phase3a/07_ROOT_PERSISTENCE.md` §55:
 * - Original addition order (priority) is preserved
 * - Persisted SAF permissions are not re-requested
 * - Tree URIs are migrated verbatim
 * - Room `LibraryRootEntity` is the sole source of truth
 */
class LibraryRootRepository(
    private val context: Context,
    private val database: AuralisDatabase,
    private val canReadDocument: (Uri, String) -> Boolean = { uri, documentId ->
        context.applicationContext.contentResolver.canReadDocument(uri, documentId)
    },
) {
    private val appContext = context.applicationContext
    private val rootDao = database.libraryRootDao()

    /**
     * Initializes the repository, performing a one-time migration from [SafRootStore]
     * if the Room table is empty and legacy preferences exist.
     */
    suspend fun initialize(): List<LibraryRootEntity> = withContext(Dispatchers.IO) {
        val legacyStore = SafRootStore(appContext)
        val roots = database.withTransaction {
            if (rootDao.count() == 0) {
                migrateFromLegacySpikeStore(legacyStore)
            }
            rootDao.getAllByPriority()
        }
        // Clear only after Room commits. Also retire leftovers from older app versions
        // whose Room roots already exist, so removing the last root stays permanent.
        legacyStore.clearMigratedRoots()
        roots
    }

    /**
     * Retrieves all roots ordered by priority (addition order).
     */
    suspend fun getRoots(): List<LibraryRootEntity> = withContext(Dispatchers.IO) {
        rootDao.getAllByPriority()
    }

    /**
     * Adds a newly picked tree URI.
     *
     * @return null if URI is already registered or persisted permission failed.
     */
    suspend fun addRoot(opened: SafLocation.Opened): LibraryRootEntity? = withContext(Dispatchers.IO) {
        database.withTransaction {
            val treeUri = opened.uri.toString()
            if (rootDao.findByTreeUri(treeUri) != null) {
                return@withTransaction null
            }

            val maxPriority = rootDao.maxPriority() ?: -1
            val nextPriority = maxPriority + 1
            val rootId = LibraryRootId.random()
            val now = System.currentTimeMillis()

            val entity = LibraryRootEntity(
                rootId = rootId,
                treeUri = treeUri,
                displayPath = opened.path.toString(),
                priority = nextPriority,
                availabilityState = RootAvailabilityState.AVAILABLE,
                lastSuccessfulScanAt = null,
                lastAttemptedScanAt = null,
                createdAt = now,
            )
            rootDao.insert(entity)
            entity
        }
    }

    /**
     * Removes a root by its [LibraryRootId].
     *
     * Releases persisted URI permissions if no other root uses the same URI.
     * Retains source/track identities, rebinds verified overlapping trees, and invalidates lost access.
     */
    suspend fun removeRoot(rootId: LibraryRootId): Boolean = withContext(Dispatchers.IO) {
        val entity = rootDao.findById(rootId) ?: return@withContext false
        val sourceDao = database.sourceDao()
        val remainingRoots = rootDao.getAllByPriority().filterNot { it.rootId == rootId }
        // Provider queries stay outside the Room transaction. Only verified access can be rebound.
        val replacements = sourceDao.findByRoot(rootId).associate { source ->
            val replacement = remainingRoots.firstNotNullOfOrNull { root ->
                val tree = Uri.parse(root.treeUri)
                val treeId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
                if (tree.authority != source.provider || treeId == null ||
                    !treeContainsDocument(treeId, source.documentId)
                ) return@firstNotNullOfOrNull null
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, source.documentId)
                val accessible = canReadDocument(uri, source.documentId)
                if (accessible) root.rootId to uri.toString() else null
            }
            source.sourceId to replacement
        }

        val removed = database.withTransaction {
            if (rootDao.findById(rootId) == null) return@withTransaction false
            val sources = sourceDao.findByRoot(rootId)
            rootDao.delete(rootId)
            val retainedRootIds = rootDao.getAllByPriority().map { it.rootId }.toSet()
            for (source in sources) {
                val replacement = replacements[source.sourceId]?.takeIf { it.first in retainedRootIds }
                sourceDao.update(source.copy(
                    rootId = replacement?.first,
                    uri = replacement?.second ?: source.uri,
                    availabilityState = if (replacement != null || source.availabilityState == SourceAvailabilityState.MISSING) {
                        source.availabilityState
                    } else SourceAvailabilityState.UNREACHABLE,
                ))
            }
            val reconciliation = RoomReconciliationTransaction(database)
            for (trackId in sources.map { it.trackId }.distinct()) {
                reconciliation.refreshActiveSourceForTrack(trackId)
            }
            true
        }
        if (!removed) return@withContext false

        // Check if any other root still references this tree URI
        val remainingSameUri = rootDao.findByTreeUri(entity.treeUri)
        if (remainingSameUri == null) {
            releasePersistedAccess(Uri.parse(entity.treeUri))
        }
        true
    }

    private suspend fun migrateFromLegacySpikeStore(legacyStore: SafRootStore) {
        val legacyRoots = legacyStore.load()
        if (legacyRoots.isEmpty()) return

        val now = System.currentTimeMillis()
        legacyRoots.forEachIndexed { index, opened ->
            val treeUri = opened.uri.toString()
            if (rootDao.findByTreeUri(treeUri) == null) {
                val entity = LibraryRootEntity(
                    rootId = LibraryRootId.random(),
                    treeUri = treeUri,
                    displayPath = opened.path.toString(),
                    priority = index,
                    availabilityState = RootAvailabilityState.AVAILABLE,
                    lastSuccessfulScanAt = null,
                    lastAttemptedScanAt = null,
                    createdAt = now,
                )
                rootDao.insert(entity)
            }
        }
    }

    private fun releasePersistedAccess(uri: Uri) {
        val permission = appContext.contentResolver.persistedUriPermissions.firstOrNull { it.uri == uri }
            ?: return

        var flags = 0
        if (permission.isReadPermission) {
            flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        if (permission.isWritePermission) {
            flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        }
        if (flags == 0) return

        try {
            appContext.contentResolver.releasePersistableUriPermission(uri, flags)
        } catch (_: Exception) {
            // Permission already revoked or provider unavailable
        }
    }
}
