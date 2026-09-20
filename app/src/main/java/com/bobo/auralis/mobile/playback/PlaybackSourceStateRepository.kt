package com.bobo.auralis.mobile.playback

import androidx.media3.common.PlaybackException
import androidx.room.withTransaction
import com.bobo.auralis.mobile.library.db.AuralisDatabase
import com.bobo.auralis.mobile.library.model.PlayabilityState
import com.bobo.auralis.mobile.library.model.TrackId
import com.bobo.auralis.mobile.library.reconciliation.RoomReconciliationTransaction

/** Persists decoder evidence without letting the playback layer choose a duplicate source. */
class PlaybackSourceStateRepository(
    private val database: AuralisDatabase,
) {
    suspend fun markPlayable(mediaId: String) {
        val trackId = mediaId.toTrackId() ?: return
        database.withTransaction {
            val sourceDao = database.sourceDao()
            val active = sourceDao.findActiveSource(trackId) ?: return@withTransaction
            val source = sourceDao.findById(active.sourceId) ?: return@withTransaction
            if (source.playabilityState != PlayabilityState.PLAYABLE) {
                sourceDao.update(
                    source.copy(
                        playabilityState = PlayabilityState.PLAYABLE,
                        failureStage = source.failureStage.takeUnless { it == FAILURE_STAGE },
                        failureCode = source.failureCode.takeUnless { source.failureStage == FAILURE_STAGE },
                        failureMessage = source.failureMessage.takeUnless { source.failureStage == FAILURE_STAGE },
                    ),
                )
            }
        }
    }

    suspend fun markUnplayable(mediaId: String, error: PlaybackException) {
        val trackId = mediaId.toTrackId() ?: return
        database.withTransaction {
            val sourceDao = database.sourceDao()
            val active = sourceDao.findActiveSource(trackId) ?: return@withTransaction
            val source = sourceDao.findById(active.sourceId) ?: return@withTransaction
            sourceDao.update(
                source.copy(
                    playabilityState = PlayabilityState.UNPLAYABLE,
                    failureStage = FAILURE_STAGE,
                    failureCode = error.errorCodeName,
                    failureMessage = error.message,
                ),
            )
            RoomReconciliationTransaction(database).refreshActiveSourceForTrack(trackId)
        }
    }

    private companion object {
        const val FAILURE_STAGE = "playback"
    }
}
