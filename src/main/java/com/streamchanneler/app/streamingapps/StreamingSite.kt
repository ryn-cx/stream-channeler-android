// TODO: Validate
package com.streamchanneler.app.streamingapps

import com.streamchanneler.app.data.GuideVideo

// TODO: Validate
data class StreamingSite(
    val pluginKey: String,
    val apps: List<StreamingApp>,
    val isVideoUnplayable: (video: GuideVideo) -> Boolean = { false },
)

// TODO: Validate
val ALL_STREAMING_SITES = listOf(
    netflixSite,
    youTubeSite,
    primeVideoSite,
    huluSite,
    disneyPlusSite,
    hboMaxSite,
    tubiSite,
    plutoTvSite,
    peacockSite,
    paramountPlusSite,
    crunchyrollSite,
    hidiveSite,
    pbsSite,
    nhkWorldSite,
)

// TODO: Validate
fun findStreamingSite(video: GuideVideo): StreamingSite? =
    ALL_STREAMING_SITES.firstOrNull { streamingSite -> streamingSite.pluginKey == video.pluginKey }
