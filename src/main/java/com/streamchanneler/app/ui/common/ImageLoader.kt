// TODO: Validate
package com.streamchanneler.app.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import com.streamchanneler.app.data.openHttpConnection
import java.util.concurrent.Executors

// TODO: Validate
class ImageLoader {
    // TODO: Validate
    private val mainHandler = Handler(Looper.getMainLooper())
    // TODO: Validate
    private val downloadExecutor = Executors.newFixedThreadPool(DOWNLOAD_THREADS)
    // TODO: Validate
    private val failedImageUrls = mutableSetOf<String>()
    // TODO: Validate
    private val imageUrlToWaitingCallbacks = mutableMapOf<String, MutableSet<(Bitmap) -> Unit>>()
    // TODO: Validate
    private val bitmapCache = object : LruCache<String, Bitmap>(CACHE_SIZE_KILOBYTES) {
        // TODO: Validate
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    // TODO: Validate
    fun cachedBitmap(imageUrl: String?): Bitmap? = imageUrl?.let { bitmapCache.get(it) }

    // TODO: Validate
    fun loadBitmap(imageUrl: String?, onBitmapLoaded: (Bitmap) -> Unit) {
        if (imageUrl == null || !imageUrl.startsWith("http")) return
        bitmapCache.get(imageUrl)?.let {
            onBitmapLoaded(it)
            return
        }
        if (imageUrl in failedImageUrls) return
        imageUrlToWaitingCallbacks[imageUrl]?.let {
            it.add(onBitmapLoaded)
            return
        }
        // The guide grid asks again with the same callback on every frame, and a set keeps only one copy of it.
        imageUrlToWaitingCallbacks[imageUrl] = mutableSetOf(onBitmapLoaded)
        downloadExecutor.execute {
            val downloadedBitmap = runCatching { downloadBitmap(imageUrl) }.getOrNull()
            mainHandler.post {
                val callbacksForUrl = imageUrlToWaitingCallbacks.remove(imageUrl).orEmpty()
                if (downloadedBitmap == null) {
                    failedImageUrls.add(imageUrl)
                    mainHandler.postDelayed({ failedImageUrls.remove(imageUrl) }, FAILED_DOWNLOAD_RETRY_DELAY)
                    return@post
                }
                bitmapCache.put(imageUrl, downloadedBitmap)
                for (callback in callbacksForUrl) callback(downloadedBitmap)
            }
        }
    }

    // TODO: Validate
    fun showImageInView(imageView: ImageView, imageUrl: String?) {
        imageView.tag = imageUrl
        imageView.setImageBitmap(cachedBitmap(imageUrl))
        imageView.visibility = if (imageUrl == null || imageUrl in failedImageUrls) ImageView.GONE else ImageView.VISIBLE
        loadBitmap(imageUrl) { loadedBitmap ->
            if (imageView.tag == imageUrl) imageView.setImageBitmap(loadedBitmap)
        }
    }

    // TODO: Validate
    private fun downloadBitmap(imageUrl: String): Bitmap? {
        val connection = openHttpConnection(imageUrl)
        try {
            if (connection.responseCode !in 200..299) return null
            val imageBytes = connection.inputStream.use { it.readBytes() }
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, boundsOptions)
            var sampleSize = 1
            while (boundsOptions.outWidth / (sampleSize * 2) >= MAX_DECODED_WIDTH) sampleSize *= 2
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOptions)
        } finally {
            connection.disconnect()
        }
    }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val DOWNLOAD_THREADS = 4
        // TODO: Validate
        const val CACHE_SIZE_KILOBYTES = 32 * 1024
        // TODO: Validate
        const val MAX_DECODED_WIDTH = 640
        // TODO: Validate
        const val FAILED_DOWNLOAD_RETRY_DELAY = 30_000L
    }
}
