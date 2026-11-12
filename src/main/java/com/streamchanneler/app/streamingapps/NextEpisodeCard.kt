// TODO: Validate
package com.streamchanneler.app.streamingapps

import com.streamchanneler.app.screenautomation.OnScreenElement

// TODO: Validate
/**
 * The card a streaming app shows before it autoplays the next episode, and the ways to cancel that autoplay.
 *
 * The cancel buttons are tried first. The cancel key codes are pressed when none of the buttons is showing.
 */
data class NextEpisodeCard(
    val shownElement: OnScreenElement,
    val autoplayCancelButtons: List<OnScreenElement> = emptyList(),
    val autoplayCancelKeyCodes: List<Int> = emptyList(),
)
