# F-Droid submission checklist

The app-owned Fastlane metadata is already included under `fastlane/metadata/android/`:

- English and Simplified Chinese descriptions
- versionCode `1` changelog
- two Android Emulator screenshots

The repository also contains `.github/fdroid/metadata.yml.in`, which is used by
`.github/workflows/fdroid.yml` to build the current source revision with the
official F-Droid buildserver container. The workflow uploads the F-Droid-style
unsigned APK, source archive, and build logs as Actions artifacts. This is a
metadata/build validation artifact; it is not signed with the app release key
and is not the APK published by F-Droid.

The F-Droid build metadata belongs in the separate `fdroiddata` repository. Copy the following draft to `metadata/io.github.realtvop.pixelcardemulator.yml`:

```yaml
Categories:
  - Connectivity
License: Apache-2.0
AuthorName: RealTvop
SourceCode: https://github.com/realtvop/PixelCardEmulator
IssueTracker: https://github.com/realtvop/PixelCardEmulator/issues

AutoName: PixelCardEmulator

RepoType: git
Repo: https://github.com/realtvop/PixelCardEmulator.git

Builds:
  - versionName: '0.1'
    versionCode: 1
    commit: 426134715748135a2a0c51f9a89f21906304e094
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: '0.1'
CurrentVersionCode: 1
```

Before opening the F-Droid merge request:

1. Publish a source release tag matching `versionName` (for example, `v0.1`).
2. Use the full source commit hash in the `Builds` entry.
3. In a Linux/F-Droid build environment, run `fdroid readmeta`, `fdroid rewritemeta io.github.realtvop.pixelcardemulator`, `fdroid checkupdates --allow-dirty io.github.realtvop.pixelcardemulator`, `fdroid lint io.github.realtvop.pixelcardemulator`, and `fdroid build io.github.realtvop.pixelcardemulator`.
4. Open the New App merge request in `fdroiddata` after the source tag and build checks pass.

The current source uses local wireless ADB only for the opt-in permission setup and has no analytics or telemetry. F-Droid's build server still needs to review the Maven/JitPack dependency set and the app's privileged NFC permissions during the actual build review.
