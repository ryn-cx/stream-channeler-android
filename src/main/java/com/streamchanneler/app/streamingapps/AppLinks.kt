// TODO: Validate
package com.streamchanneler.app.streamingapps

import android.net.Uri

// TODO: Validate
fun addWwwToUrl(videoUrl: String?): String? {
    val videoUri = Uri.parse(videoUrl ?: return null)
    val videoHost = videoUri.host ?: return videoUrl
    if (videoHost.startsWith("www.")) return videoUrl
    return videoUri.buildUpon().authority("www.$videoHost").build().toString()
}

// TODO: Validate
fun lastPathSegmentOfVideoUrl(videoUrl: String?): String? = videoUrl?.let { Uri.parse(it).lastPathSegment }
