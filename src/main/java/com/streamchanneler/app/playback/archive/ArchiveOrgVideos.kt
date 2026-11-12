// TODO: Validate
package com.streamchanneler.app.playback.archive

import com.streamchanneler.app.data.GuideVideo
import com.streamchanneler.app.data.optNullableString
import com.streamchanneler.app.data.toJsonObjectList
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

// TODO: Validate
data class ArchiveVideoLink(val identifier: String, val requestedFileName: String?)

// TODO: Validate
data class ArchiveItemFile(val name: String, val format: String?, val originalFileName: String?)

// TODO: Validate
object ArchiveOrgVideos {
    // TODO: Validate
    private const val ARCHIVE_HOST = "archive.org"
    // TODO: Validate
    private const val CONNECT_TIMEOUT = 15_000

    // TODO: Validate
    private const val READ_TIMEOUT = 30_000
    // TODO: Validate
    private val DIRECTLY_PLAYABLE_EXTENSIONS = setOf("mp4", "m4v", "webm")
    // TODO: Validate
    private val MP4_FORMATS_BEST_FIRST = listOf("h.264 HD", "h.264", "h.264 IA", "MPEG4", "512Kb MPEG4")

    // TODO: Validate
    fun archiveVideoLinkOfGuideVideo(video: GuideVideo): ArchiveVideoLink? = video.url?.let(::videoLinkFromUrl)

    // TODO: Validate
    fun videoLinkFromUrl(videoUrl: String): ArchiveVideoLink? {
        val videoUri = runCatching { URI(videoUrl) }.getOrNull() ?: return null
        val videoHost = videoUri.host?.lowercase() ?: return null
        if (videoHost != ARCHIVE_HOST && !videoHost.endsWith(".$ARCHIVE_HOST")) return null
        val videoPathSegments = videoUri.path.orEmpty().split("/").filter { it.isNotEmpty() }
        if (videoPathSegments.size < 2 || videoPathSegments[0] != "details") return null
        return ArchiveVideoLink(
            identifier = videoPathSegments[1],
            requestedFileName = videoPathSegments.drop(2).joinToString("/").ifEmpty { null },
        )
    }

    // TODO: Validate
    fun isDirectlyPlayable(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in DIRECTLY_PLAYABLE_EXTENSIONS

    // TODO: Validate
    fun playableFileName(videoLink: ArchiveVideoLink, itemFiles: List<ArchiveItemFile>): String? {
        val requestedFileName = videoLink.requestedFileName
        if (requestedFileName != null && isDirectlyPlayable(requestedFileName)) return requestedFileName
        val requestedUploadFileName = requestedFileName?.let { fileName ->
            itemFiles.firstOrNull { it.name == fileName }?.originalFileName ?: fileName
        }
        val sameUploadFiles = requestedUploadFileName?.let { uploadFileName ->
            itemFiles.filter { it.name == uploadFileName || it.originalFileName == uploadFileName }
        } ?: itemFiles
        val bestMp4File = MP4_FORMATS_BEST_FIRST.firstNotNullOfOrNull { mp4Format ->
            sameUploadFiles.firstOrNull { it.format == mp4Format && isDirectlyPlayable(it.name) }
        }
        return bestMp4File?.name
            ?: sameUploadFiles.firstOrNull { isDirectlyPlayable(it.name) }?.name
            ?: requestedFileName
    }

    // TODO: Validate
    fun downloadUrl(identifier: String, fileName: String): String =
        "https://$ARCHIVE_HOST/download/${encodePathSegment(identifier)}/" +
            fileName.split("/").joinToString("/") { encodePathSegment(it) }

    // TODO: Validate
    fun fetchItemFiles(identifier: String): List<ArchiveItemFile> {
        val metadataUrl = URL("https://$ARCHIVE_HOST/metadata/${encodePathSegment(identifier)}/files")
        val metadataConnection = metadataUrl.openConnection() as HttpURLConnection
        try {
            metadataConnection.connectTimeout = CONNECT_TIMEOUT
            metadataConnection.readTimeout = READ_TIMEOUT
            val metadataResponseCode = metadataConnection.responseCode
            if (metadataResponseCode !in 200..299) {
                throw IOException("archive.org answered $metadataResponseCode for $identifier")
            }
            val metadataResponseText = metadataConnection.inputStream.bufferedReader().use { it.readText() }
            val itemFilesJson = JSONObject(metadataResponseText).optJSONArray("result")?.toJsonObjectList().orEmpty()
            return itemFilesJson.map { itemFileJson ->
                ArchiveItemFile(
                    name = itemFileJson.getString("name"),
                    format = itemFileJson.optNullableString("format"),
                    originalFileName = itemFileJson.optNullableString("original"),
                )
            }
        } finally {
            metadataConnection.disconnect()
        }
    }

    // TODO: Validate
    private fun encodePathSegment(pathSegment: String): String =
        URLEncoder.encode(pathSegment, "UTF-8").replace("+", "%20")
}
