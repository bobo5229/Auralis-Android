package com.bobo.auralis.mobile.library.model

data class LibraryAudioSource(
    val sourceId: TrackSourceId,
    val audioUri: String,
    val treeUri: String?,
    val relativePath: String,
    val fileName: String,
    val modifiedMs: Long,
    val sizeBytes: Long,
    val rootLastSuccessfulScanAt: Long?,
) {
    val cacheKey: String
        get() = "auralis-audio:$sourceId:$modifiedMs:$sizeBytes:${rootLastSuccessfulScanAt ?: 0L}"
}
