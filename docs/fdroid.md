# F-Droid Build

This repository has a dedicated Android build path intended for the main
F-Droid repository:

```bash
./gradlew -PfdroidBuild=true :androidApp:clean :androidApp:assembleRelease
```

The `fdroidBuild` Gradle property keeps the normal build intact while
replacing non-free or proprietary-service integrations with local no-op shims.
The F-Droid build does not require `google-services.json`.
Its release APK is intentionally unsigned so F-Droid can apply repository
signing after building it from the public source repository.

On Android, the F-Droid build adds each module's
`src/androidFdroidMain/kotlin` directory and excludes the corresponding
implementation from `src/androidMain`. Builds without `fdroidBuild=true`
continue to compile the original Android implementations.

## Disabled Or Replaced Components

- Google Services and Firebase Crashlytics Gradle plugins are neither resolved
  nor applied.
- Firebase Auth, Firestore, Storage, and Crashlytics use `:firebase-stubs`
  instead of the GitLive Firebase SDK.
- Firebase Messaging / `kmpnotifier` uses `:notifier-stubs`.
- Google Sign-In, Google Identity, Apple auth through Firebase, and GitHub auth through Firebase are disabled on Android.
- Google Play Core in-app updates use the no-op `AndroidAppUpdate` implementation.
- Mixpanel analytics uses a no-op Android backend.
- Health KMP / Health Connect sync uses `:health-stubs`, avoiding Play
  Services Fitness/Auth transitives.
- Health Connect permissions and activities are removed from the merged Android
  manifest.
- The private Krisp artifact is disabled; the existing `:krisp-stubs` module is used.
- The Cactus native module is not included; `:cactus-stubs` replaces it for the F-Droid build.
- The bundled Cactus language model is excluded from Android assets.
- Downloading a third-party watch app requires an explicit warning that its
  watch binary and optional phone-side JavaScript have not been reviewed by
  F-Droid.
- Remote firmware checks require an informed user action. Downloading firmware
  requires a second confirmation that the firmware runs on the watch and
  bypasses F-Droid review; firmware notifications cannot bypass that dialog.
- Remote language-pack installation identifies the payload as third-party and
  not reviewed by F-Droid.
- F-Droid does not register the broad `content:` / `file:` package handlers,
  and the runtime rejects externally launched watch-app, firmware, and
  language-pack sideloads as defense in depth. Remote downloads retain their
  F-Droid warnings; explicitly selected local files remain direct user actions.
- Weather and watch diagnostics default off. Crash reporting and app analytics
  controls are hidden because their F-Droid backends are unavailable.
  Enabling weather discloses that location coordinates are sent to
  `weather-api.repebble.com`; separately enabling legacy watch-app weather
  interception may also send coordinates to `api.openweathermap.org`.
- The Haversine artifact is replaced with `:haversine-stubs`. Index 01 discovery,
  pairing, transfer, and background synchronization are disabled and hidden.
- Index-only audio, alarm, boot, Beeper, inference, widget, reminder, and share
  declarations are removed from the merged Android manifest.

Cloud-backed account, locker, push-token, proprietary crash reporting and
analytics SDKs, local Cactus transcription, Haversine/Index hardware support,
Krisp processing, Google/Apple/GitHub auth, platform health sync, and Play Store
update flows are therefore unavailable in the F-Droid APK. Core Pebble
connection and the local database work offline. Optional weather, app-store,
firmware, language-pack, voice, and proxy features still use their documented
network services.

## Source Scanner Notes

F-Droid scans the prepared source checkout before running Gradle. The scanner
does not evaluate `fdroidBuild` conditions, so a dependency that only appears
in a normal-build branch can still be reported. In particular, the external
metadata patch must remove the three normal-build Haversine dependency
declarations and their version-catalog entry even though the F-Droid runtime
selects `:haversine-stubs`.

The F-Droid build path excludes `:cactus`, but the upstream source tree still
contains tracked prebuilt Cactus binaries and a bundled Cactus model for
non-F-Droid builds. F-Droid build metadata should remove these with `scandelete`
because they are not needed when `fdroidBuild=true`:

```text
cactus-native/src/main/jniLibs/arm64-v8a/libcactus_engine.so
cactus/src/commonMain/resources/ios/lib/ios-arm64-simulator/libcactus_engine.a
cactus/src/commonMain/resources/ios/lib/ios-arm64-simulator/libcurl.a
cactus/src/commonMain/resources/ios/lib/ios-arm64/libcactus_engine.a
cactus/src/commonMain/resources/ios/lib/ios-arm64/libcurl.a
models/needle-pebble-ft-cq4.zip
```

F-Droid documents `scandelete` for binaries or other unwanted files that are
present in the source repository but not needed for the build.

The Android asset path
`composeApp/src/androidMain/assets/models/needle-pebble-ft-cq4.zip` is a tracked
symlink to the model above. Removing only the target leaves a dangling symlink
that Android lint cannot read, so the metadata recipe must also remove the
symlink with `rm`.

After the six files above are removed, the upstream source scanner still
reports normal-build-only and platform-specific dependency and repository
declarations in `androidApp`, `composeApp`, `experimental`, `index-ai`,
`pebble`, `util`, and `libpebble3`. The preparation patch also removes the proprietary Haversine
coordinate from the version catalog and normal-build declarations from
`libindex`. The exact event count depends on the scanner version because a
single declaration can match more than one signature; use the scanner output
as the authoritative list. F-Droid build metadata should patch those
declarations, or their containing non-F-Droid blocks, out of the prepared
source tree. Prefer a targeted metadata patch over broad `scanignore` entries
so new findings in the same build scripts remain visible.

The six `scandelete` entries alone are therefore not sufficient for a main
F-Droid repository submission. Re-run `fdroid scanner` against the
metadata-prepared source tree whenever these build scripts change.

With `fdroidserver` 2.4.2 and scanner definitions refreshed on 2026-07-27, a
clean archive of the audited source tree produced 22 fatal events: the six files
listed above, 15 references to Firebase, Crashlytics, Google Identity, Google
auth, Play Update, or CrashKiOS in Gradle scripts, and the GitHub Packages
publishing repository in `libpebble3`. The metadata patch should also remove
the three proprietary Haversine references and their version-catalog entry,
even though that coordinate was not identified by this scanner definition.

## Build Metadata Template

This is a tested starting template, not a substitute for an actual
`fdroid build`. Fill the release-specific placeholders only after the final
changes are publicly available at a stable release commit. Copy
`docs/fdroid-scanner.patch` to
`metadata/coredevices.coreapp/fdroid-scanner.patch` in `fdroiddata`.

```yaml
Categories:
  - Connectivity
License: GPL-3.0-only
AuthorName: <maintainer or project>
AuthorEmail: <public maintainer contact>
SourceCode: <public HTTPS source URL>
IssueTracker: <public issue tracker URL>
AntiFeatures:
  NonFreeAdd:
    en-US: Optional catalog and firmware flows can promote or install non-free watch payloads.
  NonFreeNet:
    en-US: The optional catalog uses proprietary Algolia, and other optional services lack complete server-license evidence.

AutoName: Pebble
RepoType: git
Repo: <public unauthenticated HTTPS Git URL>

Builds:
  - versionName: '<real release version name>'
    versionCode: <positive monotonic version code>
    commit: <full 40-character release commit>
    patch:
      - fdroid-scanner.patch
    scandelete:
      - cactus-native/src/main/jniLibs/arm64-v8a/libcactus_engine.so
      - cactus/src/commonMain/resources/ios/lib/ios-arm64-simulator/libcactus_engine.a
      - cactus/src/commonMain/resources/ios/lib/ios-arm64-simulator/libcurl.a
      - cactus/src/commonMain/resources/ios/lib/ios-arm64/libcactus_engine.a
      - cactus/src/commonMain/resources/ios/lib/ios-arm64/libcurl.a
      - models/needle-pebble-ft-cq4.zip
    rm:
      - composeApp/src/androidMain/assets/models/needle-pebble-ft-cq4.zip
    gradle:
      - yes
    gradleprops:
      - fdroidBuild=true
    output: androidApp/build/outputs/apk/release/androidApp-release-unsigned.apk

AutoUpdateMode: None
UpdateCheckMode: None
CurrentVersion: <same version name>
CurrentVersionCode: <same version code>
```

The app remains useful as an offline Bluetooth companion, but optional features
contact fixed third-party services and can install payloads with independent
licenses. `NonFreeNet` is a likely disclosure because the app is tied to
services whose complete server licensing has not been established, including a
proprietary Algolia-backed catalog. `NonFreeAdd` is also a likely disclosure
because the catalog and firmware flows can promote or install non-free watch
payloads. Confirm both labels against the production services and payload
catalog with F-Droid reviewers before submission. Opt-in diagnostics do not
default to `Tracking`; their settings name the recipient and data class.

The release commit should also include
`fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`. The existing
short and full descriptions are ready for upstream metadata import. The
previous Fastlane icon, feature graphic, and screenshots were removed because
F-Droid imports them automatically and their provenance was not documented.
Add graphics back only after recording their authorship and redistribution
rights.

## Third-Party Notices

`composeApp/src/androidMain/assets/licenses/THIRD_PARTY_NOTICES.md` maps bundled
Inter fonts to the SIL Open Font License 1.1, the Kotlin Speex wrapper to Apache
License 2.0, and the native Xiph Speex codec to its BSD 3-Clause notice. The
notice and all three complete license texts are packaged in Android APK assets.
The root `THIRD_PARTY_NOTICES.md` points maintainers to that canonical packaged
copy. Update the assets when adding bundled material or native dependencies.

## Rebase Checklist

1. Keep `settings.gradle.kts` excluding `:cactus` when `fdroidBuild=true`.
2. Put any new Firebase, Google Play Services, Play Core, Mixpanel, Haversine, private artifact, notifier, Health Connect, or native prebuilt dependency behind `if (!fdroidBuild)`.
3. Keep the Android source exclusions in the relevant module paired with their
   F-Droid replacements:

   ```text
   composeApp/src/androidMain/kotlin/coredevices/coreapp/auth/*.android.kt
   experimental/src/androidMain/kotlin/coredevices/ring/database/firestore/dao/AggregateCount.android.kt
   util/src/androidMain/kotlin/AppUpdate.android.kt
   util/src/androidMain/kotlin/coredevices/analytics/Analytics.android.kt
   ```

4. If upstream code starts using more external APIs from Firebase, notifier,
   health, or Cactus, extend only the matching integration-specific stub module
   with the minimal API surface needed to compile.
5. Re-run the release build:

   ```bash
   ./gradlew -PfdroidBuild=true :androidApp:clean :androidApp:assembleRelease
   ```

6. Re-run the Android dependency scan:

   ```bash
   ./gradlew -PfdroidBuild=true :androidApp:dependencies --configuration releaseRuntimeClasspath
   ```

   The output should not contain Firebase, Crashlytics, Google Play Services, Play Core, Mixpanel, `kmpnotifier`, `health-kmp`, Haversine, the private Krisp artifact, or the real Cactus module.

7. Inspect the APK's native libraries:

   ```bash
   jar tf androidApp/build/outputs/apk/release/androidApp-release*.apk \
       | rg '(^|/)lib/.*\.so$|libcactus|haversine|ppcommon'
   ```

   Expected native libraries are unrelated project/runtime libraries such as
   SQLite, Speex, and AndroidX graphics. `libcactus_engine.so`,
   `libhaversinesatellitelibrary.so`, and `libppcommon.so` must not appear.
   Local compatibility shims deliberately retain some upstream package names,
   so class-name searches are not a reliable dependency check.

8. Verify that disabled Cactus assets are absent:

   ```bash
   jar tf androidApp/build/outputs/apk/release/androidApp-release*.apk \
       | rg 'assets/models/needle-pebble-ft-cq4\.zip'
   ```

   This command should produce no output.

9. Prepare the external F-Droid build metadata, then run `fdroid scanner` on
   that prepared checkout. The scan must not rely on Gradle condition
   evaluation.
