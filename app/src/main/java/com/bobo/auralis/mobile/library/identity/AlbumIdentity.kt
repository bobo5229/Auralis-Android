package com.bobo.auralis.mobile.library.identity

/**
 * Canonical album identity material, version 1.
 *
 * Frozen by `docs/phase3a/01_IDENTITY.md` §5.1:
 *
 * ```text
 * AlbumIdentityV1 { albumTitle; albumArtists; date }
 * ```
 *
 * Album identity is exactly these three components. The album title alone never
 * decides identity, and none of the following may be used to split or merge a
 * release behind the user's back: directory name, artwork, bitrate, codec, file
 * format, track count, disc total, file size or sample rate (§5.5). v1 adds no
 * edition, deluxe or remaster inference at all.
 *
 * Values are canonical when built through [from] — that is the only supported
 * construction path.
 */
data class AlbumIdentity(
    val albumTitle: String,
    val albumArtists: List<String>,
    val date: String?,
) {
    companion object {

        /**
         * Builds canonical album identity from interpreted metadata.
         *
         * Returns null when there is no album title (§5.4): a missing album must
         * not create a real `AlbumEntity("Unknown Album")`, because that would
         * aggregate every untagged file into one fake album. Callers store
         * `albumId = null` and let the UI present "未知 Album". A track without
         * an album can consequently never reach a STRONG TrackKey and will not
         * be merged across physical files.
         *
         * A missing album artist set enters the key as the empty set (§5.2). It
         * is never backfilled from Artist, and no `ArtistEntity("Unknown Album
         * Artist")` is created — "Unknown" is UI presentation only.
         *
         * A missing date enters the key as null (§5.3); null is a legitimate
         * identity component, so `Album / Artist / NULL` and
         * `Album / Artist / 2025-01-01` are two different albums.
         */
        fun from(
            album: String?,
            albumArtists: List<String>,
            date: String?,
        ): AlbumIdentity? {
            val title = IdentityTextNormalizerV1.normalize(album) ?: return null
            return AlbumIdentity(
                albumTitle = title,
                albumArtists = IdentityTextNormalizerV1.canonicalCollection(albumArtists),
                date = IdentityTextNormalizerV1.normalize(date),
            )
        }
    }
}

/**
 * Validated, canonical album material as it enters a hash.
 *
 * Key derivation re-canonicalizes instead of trusting the caller, so identity
 * stays stable even if an [AlbumIdentity] was built outside [AlbumIdentity.from].
 */
internal data class AlbumKeyMaterial(
    val title: String,
    val artists: List<String>,
    val date: String?,
)

internal fun AlbumIdentity.toKeyMaterial(): AlbumKeyMaterial {
    val title = requireNotNull(IdentityTextNormalizerV1.normalize(albumTitle)) {
        "AlbumIdentity.albumTitle must not be blank; construct it with AlbumIdentity.from()"
    }
    return AlbumKeyMaterial(
        title = title,
        artists = IdentityTextNormalizerV1.canonicalCollection(albumArtists),
        date = IdentityTextNormalizerV1.normalize(date),
    )
}
