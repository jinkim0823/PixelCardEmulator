# PixelCardEmulator

> An NFC Card Emulator that **actually works on Pixel phones, root not required.**

[![Android](https://img.shields.io/badge/Android-15%2B%20(API%2035%2B)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Root Not Required](https://img.shields.io/badge/Root-Not%20Required-success)](#setup)

**PixelCardEmulator** enables NFC-A (ISO 14443-A) card identity (UID) emulation on Google Pixel and select devices without root, unlocking custom UID simulation previously blocked by Android's randomized HCE UID policy.

---

## Features

- **No Root Required**: Needs only a one-time ADB permission (`WRITE_SECURE_SETTINGS`).
- **Dual Emulation Backends**:
  - **OEM Extension (Android 15+)**: Uses platform `NfcOemExtension.emulateNfcTechnologyATag`.
  - **Raw NCI Fallback**: Direct controller communication via vendor NCI commands with automatic parameter snapshot and restore.
- **UID Support**: 4-byte, 7-byte, and 10-byte NFC-A UIDs.
- **Card Management**: Read cards via NFC, enter hex UIDs manually, set default card, and mask UIDs for privacy.
- **Material 3 Expressive UI**: Dynamic Color, smooth transitions, swipe actions, and haptics.

---

## Requirements

- **Device**: Google Pixel running **Android 15+** (or compatible Samsung / OnePlus / Sony devices via Raw NCI).
- **Permission**: `android.permission.WRITE_SECURE_SETTINGS` (granted via ADB once).

---

## Setup

1. Install the APK.
2. Grant the required permission via ADB (or Shizuku / LADB):
   ```bash
   adb shell pm grant github.realtvop.pixelcardemulator android.permission.WRITE_SECURE_SETTINGS
   ```
3. Enable NFC, open the app, scan or add your card, and tap to activate.

---

## Limitations

- **UID Only**: Emulates only the ISO 14443-A UID (`NFCID1`). Does **not** emulate encrypted card data (e.g. MIFARE Classic sectors, DESFire crypto, or bank payment cards).
- **Intended Use**: Compatible with access readers, parking barriers, and gym turnstiles that verify identity solely by UID.

---

## Build

```bash
./gradlew assembleDebug
```

---

## License

Licensed under the [Apache License 2.0](LICENSE).
