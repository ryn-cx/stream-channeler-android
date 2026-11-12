// TODO: Validate
package com.streamchanneler.app.screenautomation

// TODO: Validate
class ScreenshotButton(
    val buttonText: String,
    val keyCodesToPress: List<Int>? = null,
    val isPressedOnlyWhilePlaying: Boolean = false,
    val textRegion: ScreenRegion? = null,
) : PlayerButton
