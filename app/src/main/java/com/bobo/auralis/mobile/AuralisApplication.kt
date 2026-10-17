package com.bobo.auralis.mobile

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.bobo.auralis.mobile.artwork.LibraryArtworkFetcher
import com.bobo.auralis.mobile.artwork.LibraryArtworkKeyer

class AuralisApplication : Application(), SingletonImageLoader.Factory {
    // Lives for this process only: no foreground service or automatic scan restart.
    private val libraryScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate,
    )
    internal val libraryManagement by lazy {
        com.bobo.auralis.mobile.library.management.LibraryManagementStore(
            libraryScope,
            com.bobo.auralis.mobile.library.management.AndroidLibraryManagementOperations(this),
        )
    }

    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(LibraryArtworkKeyer())
                add(LibraryArtworkFetcher.Factory(context.applicationContext))
            }
            .build()
}
