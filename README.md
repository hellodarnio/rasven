# Rasven for Android

Rasven is a free-to-use Android softphone for people who already have an account on an IAX2-compatible organizational PBX. It does not provide a phone service or PBX accounts.

**Project status:** Rasven 1.0.0 is a release candidate, not an approved public release. No installable APK is published in this repository. [فارسی](README.fa.md)

## What the app includes

- IAX2 account setup and connection status
- Incoming and outgoing call flows
- Contacts, call history, and call settings
- English interface by default, with Persian available

These describe the current source scope; they are not a claim that every PBX or device has been certified.

## Requirements

- Android 8.0 (API 26) or newer
- An IAX2 account from your organization’s PBX administrator
- Network access to that PBX
- Microphone and notification permissions for calling; contacts access is optional

Ask your PBX administrator for the server address, port, extension or username, and password. Use the values they provide; the correct port and network configuration depend on your PBX.

## Build a debug APK

Install a compatible JDK and Android SDK, then open this repository in Android Studio or run the Gradle Wrapper from the project root.

Windows PowerShell:

    .\gradlew.bat :app:assembleDebug

Linux or macOS:

    ./gradlew :app:assembleDebug

The debug APK is written under app/build/outputs/apk/debug/. It is for development and evaluation, not a signed public release.

Release signing uses credentials stored outside this repository. Never commit a keystore, password, or signing properties file.

## Validation and known gaps

The 2026-10-23 handoff reported 332 passing JVM unit tests and a targeted Android device run of 8/8. These checks predate the latest signing configuration change. A separate full device run had 5 failures (190/195), so the full device suite was not green.

Live PBX interoperability for the current source snapshot, attended transfer, and extended background/Doze behavior remain unverified. The release signing configuration changed after the previously built APK; a new APK must be built and checked before distribution.

## Support Rasven with cryptocurrency

Rasven is free to use. Voluntary support details will be listed here when ready.

- Asset: to be added
- Network: to be added
- Public wallet address: to be added

The asset and exact network will be stated together for every wallet address.

## License and contributions

A license has not yet been added to this repository. The maintainer will select and add an open-source license before the public source release. Contribution guidance will be published at that time.

Maintained by Darnio Studio.