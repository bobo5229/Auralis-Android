package com.bobo.auralis.mobile.library.identity

/**
 * Versioned album identity hash.
 *
 * This is never a database primary key. `AlbumId` is an independent, stable
 * UUID, so a future `AlbumKeyV2` only requires recomputing hashes instead of
 * migrating the library and everything referencing it.
 */
data class AlbumKey(
    val version: Int,
    val hash: String,
)

/**
 * Album identity hash algorithm, version 1.
 *
 * Frozen by `docs/phase3a/01_IDENTITY.md` §5.1:
 *
 * ```text
 * AlbumKeyV1 = SHA-256(
 *     versionMarker,
 *     albumTitle,
 *     sortedDistinct(albumArtists),
 *     date
 * )
 * ```
 *
 * Encoding uses the shared length-prefixed encoder from §11, so the album title
 * date and artist set cannot run into each other.
 */
object AlbumKeyV1 {

    const val VERSION = 1

    private const val MARKER = "AURALIS_ALBUM_V1"

    fun derive(identity: AlbumIdentity): AlbumKey {
        val material = identity.toKeyMaterial()
        return AlbumKey(
            version = VERSION,
            hash = IdentityHasher()
                .marker(MARKER)
                .field("album.title").text(material.title)
                .field("album.artists").textList(material.artists)
                .field("album.date").text(material.date)
                .hash(),
        )
    }
}

/**
 * Writes canonical album identity material into a digest.
 *
 * A missing album is written as null title, empty artist list and null date, so
 * it can never hash like a present album.
 */
internal fun IdentityHasher.writeAlbumMaterial(album: AlbumIdentity?): IdentityHasher = apply {
    val material = album?.toKeyMaterial()
    field("album.title").text(material?.title)
    field("album.artists").textList(material?.artists ?: emptyList())
    field("album.date").text(material?.date)
}
