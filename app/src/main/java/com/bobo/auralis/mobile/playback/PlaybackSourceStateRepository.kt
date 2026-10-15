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
    internal suspend fun markPlayable(evidence: PlaybackSourceEvidence) {
        val trackId = evidence.trackId
        database.withTransaction {
            val sourceDao = database.sourceDao()
            val source = sourceDao.findById(evidence.sourceId) ?: return@withTransaction
            if (!evidence.matches(source.trackId, source.sourceId, source.modifiedMs, source.sizeBytes)) return@withTransaction
            if (source.playabilityState != PlayabilityState.PLAYABLE) {
                sourceDao.update(
                    source.copy(
                        playabilityState = PlayabilityState.PLAYABLE,
                        failureStage = source.failureStage.takeUnless { it == FAILURE_STAGE },
                        failureCode = source.failureCode.takeUnless { source.failureStage == FAILURE_STAGE },
                        failureMessage = source.failureMessage.takeUnless { source.failureStage == FAILURE_STAGE },
                    ),
                )
                RoomReconciliationTransaction(database).refreshActiveSourceForTrack(trackId)
            }
        }
    }

    internal suspend fun markUnplayable(evidence: PlaybackSourceEvidence, error: PlaybackException) {
        val trackId = evidence.trackId
        database.withTransaction {
            val sourceDao = database.sourceDao()
            val source = sourceDao.findById(evidence.sourceId) ?: return@withTransaction
            if (!evidence.matches(source.trackId, source.sourceId, source.modifiedMs, source.sizeBytes)) return@withTransaction
            sourceDao.update(
                source.copy(
                    playabilityState = if (isPermanentPlaybackFailure(error.errorCode)) PlayabilityState.UNPLAYABLE else PlayabilityState.UNKNOWN,
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
