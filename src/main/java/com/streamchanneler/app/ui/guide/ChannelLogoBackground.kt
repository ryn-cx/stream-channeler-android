// TODO: Validate
package com.streamchanneler.app.ui.guide

import android.graphics.Bitmap

// TODO: Validate
fun channelLogoNeedsLightBackground(channelLogoBitmap: Bitmap): Boolean {
    val logoArgbPixels = IntArray(channelLogoBitmap.width * channelLogoBitmap.height)
    channelLogoBitmap.getPixels(logoArgbPixels, 0, channelLogoBitmap.width, 0, 0, channelLogoBitmap.width, channelLogoBitmap.height)
    return hasDarkEdgesOnTransparency(logoArgbPixels, channelLogoBitmap.width, channelLogoBitmap.height)
}

// TODO: Validate
fun hasDarkEdgesOnTransparency(argbPixels: IntArray, imageWidth: Int, imageHeight: Int): Boolean {
    // TODO: Validate
    fun isTransparent(pixelX: Int, pixelY: Int): Boolean =
        pixelX < 0 || pixelY < 0 || pixelX >= imageWidth || pixelY >= imageHeight ||
            (argbPixels[pixelY * imageWidth + pixelX] ushr 24) < 128

    var transparentPixelCount = 0
    var edgePixelCount = 0
    var darkEdgePixelCount = 0
    for (pixelY in 0 until imageHeight) {
        for (pixelX in 0 until imageWidth) {
            if (isTransparent(pixelX, pixelY)) {
                transparentPixelCount++
                continue
            }
            if (!isTransparent(pixelX - 1, pixelY) &&
                !isTransparent(pixelX + 1, pixelY) &&
                !isTransparent(pixelX, pixelY - 1) &&
                !isTransparent(pixelX, pixelY + 1)
            ) {
                continue
            }
            edgePixelCount++
            val edgePixel = argbPixels[pixelY * imageWidth + pixelX]
            val edgeLuminance = (
                0.2126 * ((edgePixel shr 16) and 0xFF) +
                    0.7152 * ((edgePixel shr 8) and 0xFF) +
                    0.0722 * (edgePixel and 0xFF)
                ) / 255
            if (edgeLuminance < 0.35) darkEdgePixelCount++
        }
    }
    return transparentPixelCount >= imageWidth * imageHeight * 0.1 && darkEdgePixelCount > edgePixelCount / 2.0
}
