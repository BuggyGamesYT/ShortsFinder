# Shorts Finder

An Android app by **Buggy Games YT** that finds the best moments in a video to cut into YouTube Shorts.

It lists every upload on a channel (by default [@BuggyGamesYT](https://www.youtube.com/@BuggyGamesYT)). Pick a video and the app ranks its best moments, using what YouTube already shows publicly:

| Signal | Where it comes from | Chip |
|---|---|---|
| **Most replayed** | The "Most replayed" graph above the progress bar. Peaks are where viewers rewatch. | Replayed |
| **Reactions** | The video's captions (uploaded or auto). Looks for `[Laughter]`, `[Music]`, "no way", "wait", "bro", "oh my god", "lol", stretched words like "noooo", and lines full of `!` or CAPS (a stand-in for loud moments, since the app doesn't analyze audio). | Reaction |
| **Comments** | Top comments. Timestamps like `1:23` or `0:05` are counted; liked comments count a bit more. | Comments |

The signals are mixed with weights you can change in Settings. Moments closer than 10 seconds are merged. Each moment gets a suggested clip (starts a few seconds before the peak, 15–60 s long, never past the end of the video) and buttons to open YouTube at that time, copy the timestamp, or copy the clip range.

Short or brand-new videos often have no "Most replayed" graph, no captions, or no comments yet. The app says which signals were missing and ranks with whatever is there.

## Features

- Full upload list from the channel's **Videos** tab, plus the **Shorts** tab (optional, on by default). Follows YouTube's paging until the end.
- Thumbnails, titles, views, length, upload age, search, Videos/Shorts filter, pull to refresh.
- List cached on the device; it refreshes itself once a day or when you pull down.
- Channel handle editable in Settings (`@handle`, a channel URL, or a `UC…` channel id).
- No account, no API key. Uses the same public web endpoints youtube.com uses.
- Dark Material 3 design.

## Install

Download `ShortsFinder-<version>.apk` from the [Releases](../../releases) page (or from the artifact of a workflow run) and open it on your phone. Allow installs from your browser or file manager when Android asks.

### GrapheneOS

GrapheneOS lets you switch off network access per app. Shorts Finder needs it:

**Settings → Apps → Shorts Finder → Permissions → Network → Allow.**

If it's off, the app shows "Can't reach YouTube" and Retry won't help until it is allowed.

## Building

APKs are built by GitHub Actions (`.github/workflows/build.yml`):

- every push to `main` and every manual run ("Run workflow") builds a release APK and attaches it to the run as an artifact;
- pushing a tag like `v1.0.0` also creates a GitHub Release with `ShortsFinder-1.0.0.apk`.

The version name comes from the tag (or `baseVersionName` in `app/build.gradle.kts` plus the run number for untagged builds). The version code is the workflow run number, so each new build installs over the last one.

Tooling: Gradle 8.7 (wrapper included), Android Gradle Plugin 8.5.2, Kotlin 2.0.20 with the Compose compiler plugin, JDK 17, compileSdk/targetSdk 34, minSdk 26.

To build locally instead: install JDK 17 and the Android SDK, then run `./gradlew assembleRelease`.

## Signing

Android only installs an update over an existing copy when both are signed with the same key.

**Recommended: your own release key.** Make one once and keep it safe (losing it means users have to uninstall to update):

```sh
keytool -genkeypair -v -keystore shortsfinder.keystore -alias shortsfinder \
  -keyalg RSA -keysize 4096 -validity 10000 -storetype PKCS12
base64 -w0 shortsfinder.keystore > shortsfinder.keystore.b64
```

Then in the GitHub repo go to **Settings → Secrets and variables → Actions** and add:

| Secret | Value |
|---|---|
| `SHORTSFINDER_KEYSTORE_B64` | contents of `shortsfinder.keystore.b64` |
| `SHORTSFINDER_KEY_PASS` | the keystore password (also used as the key password) |
| `SHORTSFINDER_KEY_ALIAS` | optional, only if your alias isn't `shortsfinder` |

Never commit the keystore; `.gitignore` already excludes `*.keystore` and `*.jks`.

**Fallback: debug key.** Without those secrets the workflow still produces an installable APK, signed with the standard Android debug key. The workflow caches that key between runs so fallback builds can update each other, but GitHub deletes caches that go unused for 7 days. When that happens a new debug key is made and the next build will need an uninstall first. Switching from the fallback key to your release key also needs one uninstall. The release notes say which key signed each build.

## Project layout

```
app/src/main/java/com/buggygamesyt/shortsfinder/
  MainActivity.kt            screen switching
  data/Http.kt               OkHttp with retries
  data/Json.kt               JSON helpers, clock/count parsing
  data/Models.kt             data classes
  data/YouTubeClient.kt      channel, video, captions and comments loading
  data/MomentFinder.kt       scoring and clip windows
  data/Storage.kt            settings and the on-device video cache
  ui/AppViewModel.kt         app state
  ui/VideoListScreen.kt      upload list
  ui/MomentsScreen.kt        ranked moments
  ui/SettingsScreen.kt       settings
  ui/Format.kt               formatting, links, clipboard
  ui/theme/Theme.kt          dark theme
```

## Notes

YouTube changes its web page format from time to time. If the list or a signal stops loading, the parser in `data/YouTubeClient.kt` is the place to look.

## License

MIT. Copyright (c) 2026 Buggy Games YT. See [LICENSE](LICENSE).
