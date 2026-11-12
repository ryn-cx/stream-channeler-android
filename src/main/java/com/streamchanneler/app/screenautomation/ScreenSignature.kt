// TODO: Validate
package com.streamchanneler.app.screenautomation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import org.opencv.android.OpenCVLoader
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.ORB

// TODO: Validate
data class ScreenRegion(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    // TODO: Validate
    fun containsCenterOf(textBounds: Rect, screenPixels: ScreenPixels): Boolean {
        val textReferenceCenterX = textBounds.centerX() * REFERENCE_SCREEN_WIDTH / screenPixels.width
        val textReferenceCenterY = textBounds.centerY() * REFERENCE_SCREEN_HEIGHT / screenPixels.height
        return textReferenceCenterX in left..right && textReferenceCenterY in top..bottom
    }
}


// TODO: Validate
class ScreenPixels(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
) {
    // TODO: Validate
    fun rgbAt(referenceX: Int, referenceY: Int): Int {
        val screenX = (referenceX * width / REFERENCE_SCREEN_WIDTH).coerceIn(0, width - 1)
        val screenY = (referenceY * height / REFERENCE_SCREEN_HEIGHT).coerceIn(0, height - 1)
        return pixels[screenY * width + screenX] and 0xFFFFFF
    }
}


// TODO: Validate
sealed interface ScreenSignature {
    // TODO: Validate
    fun isShownInScreenshot(screenPixels: ScreenPixels): Boolean
}


// TODO: Validate
data class ReferenceImageScreenSignature(
    val referenceImageResourcePath: String,
    val referenceRegion: ScreenRegion,
) : ScreenSignature {
    // TODO: Validate
    private val referenceOrbFeatures: KeypointFeatures by lazy {
        val referenceGrayPixels = referenceGrayPixels()
        orbFeaturesOfGrayPixels(referenceGrayPixels).also { referenceGrayPixels.release() }
    }

    // TODO: Validate
    private fun referenceGrayPixels(): Mat {
        val referenceImage = ReferenceImageScreenSignature::class.java.getResourceAsStream(referenceImageResourcePath)!!
            .use { BitmapFactory.decodeStream(it) }
        val referenceArgbPixels = referenceImage.argbPixels()
        val referenceGrayPixels = grayscaleMatFromRgbPixels(referenceImage.width, referenceImage.height) { referenceX, referenceY ->
            referenceArgbPixels[referenceY * referenceImage.width + referenceX]
        }
        referenceImage.recycle()
        return referenceGrayPixels
    }

    // TODO: Validate
    private fun searchedGrayPixels(screenPixels: ScreenPixels): Mat {
        val searchLeft = (referenceRegion.left - SEARCH_MARGIN).coerceAtLeast(0)
        val searchTop = (referenceRegion.top - SEARCH_MARGIN).coerceAtLeast(0)
        val searchRight = (referenceRegion.right + SEARCH_MARGIN).coerceAtMost(REFERENCE_SCREEN_WIDTH - 1)
        val searchBottom = (referenceRegion.bottom + SEARCH_MARGIN).coerceAtMost(REFERENCE_SCREEN_HEIGHT - 1)
        return grayscaleMatFromRgbPixels(searchRight - searchLeft + 1, searchBottom - searchTop + 1) { searchedX, searchedY ->
            screenPixels.rgbAt(searchLeft + searchedX, searchTop + searchedY)
        }
    }

    // TODO: Validate
    override fun isShownInScreenshot(screenPixels: ScreenPixels): Boolean {
        val loadedReferenceOrbFeatures = referenceOrbFeatures
        val searchedGrayPixels = searchedGrayPixels(screenPixels)
        val screenOrbFeatures = orbFeaturesOfGrayPixels(searchedGrayPixels)
        searchedGrayPixels.release()
        return try {
            ransacInlierMatchCount(loadedReferenceOrbFeatures, screenOrbFeatures) >= MINIMUM_INLIER_MATCHES
        } finally {
            screenOrbFeatures.release()
        }
    }

    // TODO: Validate
    private companion object {
        // TODO: Validate
        const val SEARCH_MARGIN = 100
        // TODO: Validate
        const val MINIMUM_INLIER_MATCHES = 40
    }
}

// TODO: Validate
private class KeypointFeatures(val keypoints: MatOfKeyPoint, val descriptors: Mat) {
    // TODO: Validate
    fun release() {
        keypoints.release()
        descriptors.release()
    }
}

// TODO: Validate
internal fun Bitmap.argbPixels(): IntArray = IntArray(width * height).also { argbPixels ->
    getPixels(argbPixels, 0, width, 0, 0, width, height)
}

// TODO: Validate
private val isOpenCvLoaded: Boolean by lazy { OpenCVLoader.initLocal() }

// TODO: Validate
private inline fun grayscaleMatFromRgbPixels(width: Int, height: Int, rgbAt: (Int, Int) -> Int): Mat {
    check(isOpenCvLoaded)
    val grayBytes = ByteArray(width * height)
    for (pixelY in 0 until height) {
        for (pixelX in 0 until width) {
            val rgb = rgbAt(pixelX, pixelY)
            val gray = (299 * (rgb shr 16 and 0xFF) + 587 * (rgb shr 8 and 0xFF) + 114 * (rgb and 0xFF) + 500) / 1000
            grayBytes[pixelY * width + pixelX] = gray.toByte()
        }
    }
    return Mat(height, width, CvType.CV_8UC1).apply { put(0, 0, grayBytes) }
}

// TODO: Validate
private fun orbFeaturesOfGrayPixels(grayPixels: Mat): KeypointFeatures {
    val borderedGrayPixels = Mat()
    Core.copyMakeBorder(
        grayPixels,
        borderedGrayPixels,
        ORB_EDGE_BORDER,
        ORB_EDGE_BORDER,
        ORB_EDGE_BORDER,
        ORB_EDGE_BORDER,
        Core.BORDER_REPLICATE,
    )
    val keypoints = MatOfKeyPoint()
    val descriptors = Mat()
    ORB.create(ORB_MAXIMUM_KEYPOINTS).detectAndCompute(borderedGrayPixels, Mat(), keypoints, descriptors)
    borderedGrayPixels.release()
    return KeypointFeatures(keypoints, descriptors)
}

// TODO: Validate
private fun ransacInlierMatchCount(referenceFeatures: KeypointFeatures, screenFeatures: KeypointFeatures): Int {
    if (referenceFeatures.descriptors.empty() || screenFeatures.descriptors.rows() < 2) return 0
    val nearestTwoMatchesPerReferenceKeypoint = mutableListOf<MatOfDMatch>()
    BFMatcher.create(Core.NORM_HAMMING, false).knnMatch(
        referenceFeatures.descriptors,
        screenFeatures.descriptors,
        nearestTwoMatchesPerReferenceKeypoint,
        2,
    )
    val ratioTestMatches = nearestTwoMatchesPerReferenceKeypoint.mapNotNull { nearestTwoMatches ->
        val nearestTwoMatchArray = nearestTwoMatches.toArray()
        nearestTwoMatches.release()
        nearestTwoMatchArray.takeIf { it.size == 2 && it[0].distance < RATIO_TEST_MAXIMUM * it[1].distance }?.first()
    }
    if (ratioTestMatches.size < 4) return 0
    val referenceKeypoints = referenceFeatures.keypoints.toArray()
    val screenKeypoints = screenFeatures.keypoints.toArray()
    val referenceMatchedPoints = MatOfPoint2f(*ratioTestMatches.map { referenceKeypoints[it.queryIdx].pt }.toTypedArray())
    val screenMatchedPoints = MatOfPoint2f(*ratioTestMatches.map { screenKeypoints[it.trainIdx].pt }.toTypedArray())
    val ransacInlierMask = Mat()
    return try {
        Calib3d.findHomography(
            referenceMatchedPoints,
            screenMatchedPoints,
            Calib3d.RANSAC,
            RANSAC_REPROJECTION_THRESHOLD,
            ransacInlierMask,
        ).release()
        if (ransacInlierMask.empty()) 0 else Core.countNonZero(ransacInlierMask)
    } finally {
        referenceMatchedPoints.release()
        screenMatchedPoints.release()
        ransacInlierMask.release()
    }
}

// TODO: Validate
const val REFERENCE_SCREEN_WIDTH = 1920
// TODO: Validate
const val REFERENCE_SCREEN_HEIGHT = 1080
// TODO: Validate
private const val RATIO_TEST_MAXIMUM = 0.75f
// TODO: Validate
private const val ORB_EDGE_BORDER = 31
// TODO: Validate
private const val ORB_MAXIMUM_KEYPOINTS = 500
// TODO: Validate
private const val RANSAC_REPROJECTION_THRESHOLD = 5.0
