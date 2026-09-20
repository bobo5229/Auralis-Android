package com.bobo.auralis.mobile

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.bobo.auralis.mobile.artwork.LibraryArtworkFetcher
import com.bobo.auralis.mobile.artwork.LibraryArtworkKeyer

class AuralisApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(LibraryArtworkKeyer())
                add(LibraryArtworkFetcher.Factory(context.applicationContext))
            }
            .build()
}
