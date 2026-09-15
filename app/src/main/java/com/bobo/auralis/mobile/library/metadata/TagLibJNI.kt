package com.bobo.auralis.mobile.library.metadata

import java.io.FileInputStream

internal object TagLibJNI {
    init {
        System.loadLibrary("tagJNI")
    }

    /**
     * Open a file and extract raw metadata.
     *
     * Note: This method is blocking and should be handled on Dispatchers.IO.
     */
    fun open(target: MetadataTarget, fis: FileInputStream): MetadataResult {
        val inputStream = NativeInputStream(target.fileName, fis)
        val tag = openNative(inputStream)
        inputStream.close()
        return tag
    }

    private external fun openNative(inputStream: NativeInputStream): MetadataResult
}
