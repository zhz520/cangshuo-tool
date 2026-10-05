package com.cangshuo.toolbox.feature.imagecompress.domain

import android.graphics.Bitmap
import android.net.Uri

/** Contract for local image compression and storage operations. */
interface ImageCompressRepository {
    /** Reads image metadata and generates a memory-efficient original thumbnail. */
    suspend fun readMetadata(uri: Uri): Result<Pair<ImageMetadata, Bitmap>>

    /** Performs purely local compression, resizing, and format conversion. */
    suspend fun compress(uri: Uri, params: CompressParams): Result<CompressResult>

    /** Streams an output to MediaStore (API 29+) or a user-created document. */
    suspend fun save(result: CompressResult, destination: Uri? = null): Result<Uri>

    /** Creates a separate, temporary file for other apps to read. */
    suspend fun prepareShare(result: CompressResult): Result<Uri>

    /** Releases this tool's private output asynchronously, without deleting shared exports. */
    fun discard(result: CompressResult)
}
