<p align="center">
  <img src="docs/media/readme/banner-en.png" alt="Rasven for Android — an IAX2 softphone, shown with the real Rasven app screen" width="100%">
</p>
<p align="center"><sub>The banner uses the real Rasven home screen. Its connection status and extension are sample values.</sub></p>

<h1 align="center">Rasven for Android</h1>

<p align="center">
  <a href="README.fa.md">فارسی</a> · <a href="#download">Download</a> · <a href="#features">Features</a> · <a href="#get-started">Get started</a> · <a href="#architecture">Architecture</a> · <a href="#support-rasven">Support Rasven</a>
</p>

<p align="center"><strong>Your office line, on Android.</strong><br>A free softphone for an existing IAX2-compatible organizational PBX account.</p>

Rasven helps people use their organization's existing business phone account on an Android phone. It does not provide phone service, a PBX, or PBX accounts.

## Download

<p align="center"><a href="https://github.com/hellodarnio/rasven/releases/tag/v1.0.0"><strong>↓ Download Rasven v1.0.0 APK</strong></a></p>

The APK is attached to the <a href="https://github.com/hellodarnio/rasven/releases/tag/v1.0.0">GitHub Release</a>. Rasven v1 is also available through app markets.

> **Package verification:** The Bazaar v1.0.0 package checked on 2026-10-29 had the same SHA-256 as the APK attached to this release: <code>07918398b40f32dc3e076e4d74407c078c66155d0dd92922ab4286a2855d3965</code>. This confirms that checked package only; packages from other markets were not compared.

<h2 id="features">What you can do</h2>

<table>
  <tr>
    <td width="50%"><h3 align="center">📞 Office-line calls</h3><p align="center">Make and receive calls through your organization's IAX2 PBX account.</p></td>
    <td width="50%"><h3 align="center">🎛️ In-call controls</h3><p align="center">Use mute, speaker, hold, and keypad controls while calling.</p></td>
  </tr>
  <tr>
    <td width="50%"><h3 align="center">👥 Everyday phone tools</h3><p align="center">Keep contacts, call history, and call settings close at hand.</p></td>
    <td width="50%"><h3 align="center">🌐 English + فارسی</h3><p align="center">Use the app in English or Persian.</p></td>
  </tr>
</table>

These are the features represented in the current source. They do not certify compatibility with every PBX or Android device.

<h2 id="get-started">Get started in three steps</h2>

1. Ask your PBX administrator for the server address, port, extension or username, and password.
2. Install Rasven from an app market or the GitHub Release above.
3. Add your account and allow microphone and notification access for calling. Contacts access is optional.

## See the app

<p align="center">
  <img src="docs/media/readme/home-en.png" alt="Rasven home dialer with sample account status and number keypad" width="245">
</p>
<p align="center"><sub>This is the real home dialer from the project. The connected indicator and extension are sample state, not a live PBX connection.</sub></p>

## Architecture

Rasven is an Android client with its own IAX2 signaling and UDP transport code. Call audio passes through the app's G.711 media engine and Android audio APIs. The PBX is an existing server managed by the organization; it is outside this repository.

<p align="center">
  <img src="docs/media/readme/architecture-en.svg" alt="Rasven architecture: Android UI, built-in IAX2 client, audio media, and the organization's external PBX" width="100%">
</p>

The app needs an IAX2 account and network access to that PBX. Ask the PBX administrator for the server address, port, extension or username, and password.

## Requirements

- Android 8.0 (API 26) or newer
- An account from an IAX2-compatible organizational PBX
- Network access to that PBX
- Microphone and notification permissions for calling; contacts access is optional

## Build from source

Install a compatible JDK and Android SDK, then open the repository in Android Studio or use the Gradle Wrapper from the project root.

**Windows PowerShell**

```powershell
.\gradlew.bat :app:assembleDebug
```

**Linux or macOS**

```sh
./gradlew :app:assembleDebug
```

The debug APK is written to <code>app/build/outputs/apk/debug/</code>. It is for development and evaluation, not a signed public release. A release build also requires the approved production application ID and signing credentials; keep those values outside this repository and never commit a keystore or passwords.

## Validation status

The source handoff dated 2026-10-23 reported **332 passing JVM unit tests** and a targeted Android device run of **8/8**. These checks predate the latest signing configuration change. A separate full device run reported **190/195**, with five failures, so that suite was not green. The handoff records no fresh APK build after the signing change.

Live interoperability with a PBX, attended transfer, and extended background/Doze behavior remain unverified for this source snapshot. Historical test results do not verify the APK currently distributed through app markets.

## Support Rasven

Rasven is free to use. If it helps your organization, you can support its continued development with USDT. Support is optional.

<table>
  <tr><th>💛 USDT · BNB Smart Chain (BEP-20)</th><th>🔺 USDT · TRON (TRC-20)</th></tr>
  <tr>
    <td align="center"><img src="docs/media/readme/usdt-bep20.svg" alt="QR code for the USDT BNB Smart Chain BEP-20 wallet address" width="180"></td>
    <td align="center"><img src="docs/media/readme/usdt-trc20.svg" alt="QR code for the USDT TRON TRC-20 wallet address" width="180"></td>
  </tr>
  <tr>
    <td align="center"><code>0x2548E3A6dECac1FFfb0d02175d165B914CF04D40</code></td>
    <td align="center"><code>TDWVLgeWp21hkPV4zgRJzMTB28nzcyuGck</code></td>
  </tr>
</table>

Each QR code contains only the wallet address; it does not select a token or network. Before sending, confirm **USDT** and the exact network shown above in your wallet. A transfer on a different network may not be recoverable.

## Project status and license

The source repository is public. **No project license has been added yet**, so the code is visible but has not been released under an open-source license. A license must be selected before general reuse or public contributions can be invited.

Maintained by **Darnio Studio**.
