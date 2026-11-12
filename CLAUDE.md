<!-- TODO: Validate -->
# Project rules

## Only handle situations that actually happen

Only write code for the situations Stream Channeler really runs into. Do not add matches, branches, fallbacks or
options for a screen or state just because it showed up somewhere in a log or could exist in theory. First ask whether
the app can reach that situation in the flow being changed.

For example, the app opens an episode link, so the episode page is the only page the play button code ever sees. A
series page's "Continue" button must not be matched just because a log shows that a series page exists.

When unsure whether a situation can happen, ask instead of adding code for it.
