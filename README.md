<!-- TODO: Validate -->
# Stream Channeler for Android TV

The Stream Channeler app for Android TV, Google TV and Fire TV. It automatically plays your
[Stream Channeler](https://streamchanneler.com/) channels like a real TV channel by opening each video in the streaming
app that hosts it. Channels are created and edited on [Stream Channeler's website](https://streamchanneler.com/).

![The Stream Channeler guide](docs/guide.png)

## Features

- Plays the next video on the channel automatically
- Skips recaps, intros, credits and ads
- Picks your profile and enters its PIN
- Stops streaming apps from autoplaying their own next episode
- Plays Internet Archive videos in its own player

## Install

1. Download and install the APK:
   1. Install a downloader app from the TV's app store.
   2. Allow it to install apps: when it asks, or under **Settings > Apps > Security & restrictions > Unknown sources** (on Fire TV, **Settings > My Fire TV > Developer options > Install unknown apps**).
   3. In the downloader, enter the link to `stream-channeler-release.apk` from the latest [release](../../releases/latest), then install it when the download finishes.
2. Open the app and sign in with a code or your Stream Channeler email and password, or choose **Continue without signing in** to browse public channels only.
3. Open **Settings** (Menu button) and turn on each permission under **Permissions**. Selecting a button opens the device's settings screen for it:
   - **Detect when videos end (notification access)**: lets the app tell when a video in a streaming app ends.
   - **Come back to the guide (display over other apps)**: lets the app return to the guide, or play the next video, when one ends.
   - **Pick streaming profiles (accessibility)**: picks your profile and enters its PIN in streaming apps. It also presses skip buttons and cancels a streaming app's own next episode autoplay, which can involve taking screenshots of the streaming app to find them.

   If a button does nothing, or the screen it opens doesn't list the app, use the advanced install and setup for that permission. Everything else still works without it.

### Advanced install and setup

**The advanced install and setup is only required if your device can't change a setting from inside the Stream Channeler app. **

Before using adb, check whether Android is blocking the permission because the app was installed from outside an app store. On Android 13 and newer, notification access and accessibility can show as greyed out or say **Restricted setting**. To unblock them, open **Settings > Apps**, choose **Stream Channeler**, open the menu (three dots in the top right corner) and choose **Allow restricted settings**. The wording differs between devices; on Samsung it can read something like "allow without restrictions". Then go back to **Settings** in the app and select the permission's button again.

If the above does not work you will need a computer with [Android platform tools](https://developer.android.com/tools/releases/platform-tools)
installed. You only need to run the commands for the permissions that could not be toggled from inside of the app.

1. Enable adb on the TV and connect to it from the computer:
   1. Turn on developer options: open **Settings**, find the **About** screen (usually under **System** or **Device Preferences**), and select **Android TV OS build** or **Build number** seven times. On Fire TV, open **Settings > My Fire TV > About** and select the device name seven times instead.
   2. Open **Developer options** (usually under **System** or **Device Preferences**, or **My Fire TV** on Fire TV) and turn on **USB debugging** or **ADB debugging**. Turn on **Wireless debugging** too if your device has it.
   3. Find the TV's IP address under **Settings > Network** and connect from the computer with `adb connect <TV IP address>`. If wireless debugging shows a pairing code, run `adb pair <IP address>:<pairing port>` first. Accept the prompt that appears on the TV.
2. Run the command for each permission that is still off. Settings also lists the commands for the missing ones under **Permissions**.

   ```sh
   # Notification access: Used to determine when a video ends.
   adb shell cmd notification allow_listener com.streamchanneler.app/com.streamchanneler.app.playback.PlaybackWatcherService

   # Display over other apps: Used to allow the app to open when a video completes.
   adb shell appops set com.streamchanneler.app SYSTEM_ALERT_WINDOW allow

   # Accessibility: Used to pick profiles, press skip buttons and cancel next episode autoplay on streaming services.
   adb shell settings put secure enabled_accessibility_services com.streamchanneler.app/com.streamchanneler.app.screenautomation.ScreenAutomationService
   ```

   The last command replaces every other enabled accessibility service. If the TV already has one, use the command shown under **Permissions** in Settings, which keeps the others. Updating or force-stopping the app turns its accessibility service off, so run it again after each update.
3. **Only needed on Android 12 and older, including Fire OS.** HBO Max, Hulu, Netflix, Paramount+ and Peacock are picked with remote
   buttons. On Android 13 and newer the app presses them itself through accessibility, older versions of Android do not
   support this.
   1. Install [Shizuku](https://github.com/RikkaApps/Shizuku) on the TV.
   2. Start Shizuku's helper from the computer. The helper has the same control of the TV as `adb` from a computer. It
      stops when the TV restarts, so run this again after every restart:

      ```sh
      # Shizuku: Used to pick profiles on streaming services that need to be picked using simulated remote button presses instead of simulated touches on older Android devices.
      adb shell 'sh -c "$(dirname $(pm path moe.shizuku.privileged.api | cut -d: -f2))/lib/arm/libshizuku.so"'
      ```

   3. In the app, open **Settings** and select **Press remote buttons** under **Permissions**.
   4. Choose **Allow** in Shizuku's prompt.

## How to use

1. Open Stream Channeler from the TV's home screen.
2. Sign in with a code or your email and password, or continue without signing in to browse public channels.
3. Pick a video in the guide to start watching the channel.

## Streaming App Compatibility

✅ means Stream Channeler does it. ✔️ means the streaming service does not need it. ❌ means it is not supported yet.

| App | Recap Skipped | Intro Skipped | Credits Skipped | Ads Skipped | Next Episode Autoplay Cancelled | Video Completion Detection | Profile Selector | Pin Entry |
| --- | ------------- | ------------- | --------------- | ----------- | ------------------------------- | -------------------------- | ---------------- | --------- |
| Amazon Prime Video | ❌ | ✅ | ✔️ | ✅ | ✅ | ✅ | ✅ | ✔️ |
| Crunchyroll | ❌ | ✅ | ✅ | ✔️ | ✅ | ✅ | ✅ | ✅ |
| Disney+ | ✅ | ✅ | ❌ | ✔️ | ✅ | ✅ | ✅ | ✅ |
| HBO Max | ❌ | ✅ | ✔️ | ✔️ | ✔️ | ✅ | ✅ | ✅ |
| HIDIVE | ✔️ | ✅ | ✅ | ✔️ | ✔️ | ✅ | ✅ | ✔️ |
| Hulu | ✅ | ✅ | ✔️ | ✔️ | ✅ | ✅ | ✅ | ✔️ |
| Internet Archive<sup>[1](#streaming-app-note-1)</sup> | ✔️ | ✔️ | ✔️ | ✔️ | ✔️ | ✅ | ✔️ | ✔️ |
| Netflix | ✔️ | ❌ | ✔️ | ✔️ | ❌ | ✅ | ✅ | ✅ |
| NHK World | ✔️ | ✔️ | ✔️ | ✔️ | ✔️ | ✅ | ✔️ | ✔️ |
| Paramount+ | ✅ | ✅ | ✔️ | ✅ | ✅ | ✅ | ✅ | ✔️ |
| PBS | ✔️ | ✔️ | ✔️ | ✔️ | ✔️ | ✅ | ✔️ | ✔️ |
| Peacock | ✔️ | ✅ | ✔️ | ✔️ | ✅ | ✅ | ✅ | ✅ |
| Pluto TV | ❌ | ✔️ | ✔️ | ✔️ | ✅ | ✅ | ✔️ | ✔️ |
| Tubi | ✔️ | ✔️ | ✔️ | ✔️ | ❌ | ✅ | ✔️ | ✔️ |
| YouTube | ✔️ | ✔️ | ✔️ | ✔️ | ✔️ | ✅ | ✅ | ✔️ |

1. <a id="streaming-app-note-1"></a>Internet Archive videos play inside of the app because there is no official app.

Profile names, profile numbers and PINs are saved to your account, so the other Stream Channeler apps use them too.

## Stopping a video early

After a video is opened from the guide, the app watches it so it can come back to the guide, or play the next
video, when it ends. Any of these stops the watching, so nothing happens when you leave a video partway through:

- **Open Stream Channeler again**, from the home screen or recent apps.
- **Switch to another app**, including the home screen. Opening a system panel
  over the video, such as quick settings or voice search, counts as switching apps.
- **Press Back during the "Playing the next episode" countdown**.
- **Turn off notification access** for Stream Channeler, or sign out.

Leaving the video for another screen of the streaming app, such as the show's page, counts as the video ending, so the
next video plays after the countdown.

## Advanced Controls

| Remote | Controller | Action |
| --- | --- | --- |
| D-pad | D-pad or left stick | Move through the menus. |
| Select | A | Click the highlighted item. |
| Hold Select | Hold A | Change the highlighted episode's watch status |
| Rewind / Fast Forward | LB / RB | Page the timeline earlier or later |
| Channel Up / Down, Page Up / Down | LT / RT | Page through channels |
| Back | B | Move from the guide up to the toolbar, cancel **Up next**, or leave the app from the toolbar |
| Menu | Start | Change the highlighted episode's watch status, or open Settings when no episode is highlighted |
| Settings | | Settings |
| Guide | | Jump to the guide |
| | X | Search channels |
| | Y | Resort or check the highlighted channel |
| | Select / Back (View) | Refresh the guide |

## Build

```sh
./gradlew assembleDebug
```

The APK is in `build/outputs/apk/debug/`. `compile.ps1` builds the signed release APK, and needs the release signing key
in Windows Credential Manager.

## Contributing

Contributions must be made under the [Contributor License Agreement](CLA.md). The agreement assigns ownership of every contribution to Stream Channeler. Accept it by adding the sentence from the agreement's **How to accept** section to the description of your first pull request.

## License

Copyright (C) 2026 Stream Channeler

This project is licensed under the [GNU Affero General Public License v3.0](LICENSE).
