# SAK 08 test build

This fork adds a per-card **Default profile / SAK 08 · experimental** choice and a saved request-status panel. It is based on upstream commit `185ee3cfa078b47e51efe13fdb03ace3018032cb`.

The app is named **PixelCard SAK Test** and uses package `io.github.jinkim0823.pixelcardemulator.saktest`. It installs alongside the original app, with separate cards, preferences and permission grants. Both apps still control the same phone NFC controller; turn the original app's emulation off before testing this build.

## Phone comparison

1. Install the test APK. Use the app's setup flow to grant `WRITE_SECURE_SETTINGS` with Shizuku or ADB. The original app's grant does not transfer.
2. Add/scan the already registered physical card. Scanning still copies **UID only**; it does not import card keys, memory, ATQA or SAK.
3. Save with **Default profile**, tap the card to activate, and note the status/backend. Test with the screen unlocked.
4. Deactivate. Edit the same card and choose **SAK 08 · experimental**. Save, activate and test again. Keep **Force raw** off initially so only the profile changes between attempts.
5. Use **Copy status** to share the last three request outcomes. It includes the model/SDK, backend, requested profile fields, UID length and results, but not the UID value or card keys.
6. Deactivate after the test. Report any OFF failure before trying another NFC emulation app.

Do not re-register the door lock for this comparison: the UID stays the same. Door-lock registration may replace the existing key list. A working physical card is the comparison baseline.

## Meaning of the status

- **OEM / API accepted (status 0)**: the system call returned success. No controller readback or RF measurement was performed.
- **RAW / NCI accepted; profile + UID readback matched**: for SAK08, the app read 85/30/31/32/33/58 after resume and compared them with the request. This is controller configuration evidence, not RF evidence.
- **RAW / UID readback matched**: upstream default behavior; other profile fields are not verified. OnePlus CPH2747 can use the upstream UID-only path for the Default profile, but never for SAK08.
- **OFF request completed**: the existing backend's deactivation returned. This is not a measurement of full restoration or RF output. Existing recovery markers remain in use on failures.
- **FAILED**: the request failed; existing rollback behavior runs. Copy status preserves the failure and subsequent rollback as separate entries.

The panel is saved request history, not a live NFC health indicator. Changing NFC settings or another app controlling NFC can invalidate the last result. There is no external RF reader attached to this build.

## Requested parameters

| Profile | Backend | SDD | Platform | SAK | RATS TB1 |
| --- | --- | --- | --- | --- | --- |
| Default | OEM | 04 | 00 | 00 | 00 |
| Default | Raw, Google | 04 | 00 | 00 | 00 |
| Default | Raw, other | 06 | 0C | 20 | 40 |
| SAK08 | OEM or Raw | 04 | 00 | 08 | 00 |

These are NCI fields, not a complete captured ATQA/ATS. A four-byte UID with 08 prefix may be randomized by the controller. SAK08 changes identity requests only; it does not implement MIFARE Classic authentication, CRYPTO1 or sector memory. Whether a specific phone emits the requested SAK or a particular reader accepts it must be tested.

## Build

Use JDK21 and an Android SDK containing platform37 and the build tools selected by AGP. Set `sdk.dir` in untracked `local.properties`, then run:

```zsh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. This is a debug-signed test build. The unit tests cover profile migration defaults, OEM/raw selection, the four-byte08 condition and bounded failure/rollback history. They do not exercise a physical NFC controller. APK assembly and lint also do not establish RF or door-lock compatibility.
