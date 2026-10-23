<div align="center">

# QMG Studio

**Make custom Samsung boot & shutdown animations from any video or GIF — right on your phone.**

[![Latest release](https://img.shields.io/github/v/release/xBooM-SL/Qmg-studio?label=latest&color=3ddc97)](https://github.com/xBooM-SL/Qmg-studio/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/xBooM-SL/Qmg-studio/total?color=4fc3f7)](https://github.com/xBooM-SL/Qmg-studio/releases)
[![License: MIT](https://img.shields.io/badge/license-MIT-a78bfa)](LICENSE)
![Platform](https://img.shields.io/badge/Android-Samsung-3ddc97)

[**⬇️ Download**](https://github.com/xBooM-SL/Qmg-studio/releases/latest) ·
[Report a problem](https://github.com/xBooM-SL/Qmg-studio/issues) ·
[Changelog](CHANGELOG.md) ·
[Format notes](docs/FORMAT.md)

</div>

---

QMG Studio converts videos and GIFs into Samsung's `.qmg` (Qmage) animation format, the one used for the
**power-on** and **power-off** animations. You get a live preview made by the real encoder, so what you see is
exactly what your phone will play.

> Nothing from Samsung is bundled. The encoder reads its delta table at run time from the `libQmageDecoder.so`
> that is already on **your own device**.

## ✨ Features

| | |
|---|---|
| 🎬 **Video + GIF input** | Trim up to 60 s, change speed, fit / fill / stretch / glow, fade in & out |
| 👀 **Live preview** | Pinned on top while you edit, shown on a phone mock-up, decoded from the real `.qmg` |
| 🔁 **Boot + shutdown together** | Make both animations in one go, or just one of them |
| 📐 **Any Samsung screen size** | Multiple of 4. "Match my phone" reads the size from your stock `bootsamsung.qmg` |
| 🎚️ **Quality control** | Quality presets, dithering and a **target file size** (per-frame rate control) |
| ✅ **Verify** | Optional check against your phone's own Samsung decoder |
| 🖼️ **QMG → GIF** | Open any `.qmg` to preview it, or export it as a GIF |
| 💾 **Instant save** | Module `.zip`, `bootsamsung.qmg` and `shutdown.qmg` are written straight to storage with a save pop-up |
| 🔄 **Auto update** | The app checks GitHub Releases itself and updates in one tap (see below) |
| 🛟 **Backup & undo** | Backs up your stock animations and can uninstall the module again |

## 📲 Install

1. Open the [**latest release**](https://github.com/xBooM-SL/Qmg-studio/releases/latest) and download `QmgStudio-vX.Y.apk`.
2. Open the APK and allow *Install unknown apps* for your browser/file manager when Android asks.
3. Open **QMG Studio**.

Only download APKs from this repository. See [`SECURITY.md`](SECURITY.md).

## 🔄 Auto update

You only need to install the app once.

* Every time the app opens it checks the **latest GitHub Release** of this repository.
* If a newer version exists, an **Update available** dialog shows the release notes, a download progress bar and an
  **Update now** button. Tapping it opens Android's installer and the new version installs over the old one,
  keeping the same app.
* You can also check by hand: **Tools → About → Check for updates**.
* Needs two permissions: `INTERNET` (check & download) and `REQUEST_INSTALL_PACKAGES` (hand the APK to the installer).
  The first time, Android asks you to allow installs from QMG Studio.

Updates work because every build is signed with the same key and gets a higher `versionCode`.

## 🚀 How to use

1. **Create** — pick a video or GIF, choose fit/fade/quality, set boot and/or shutdown clips.
2. **Preview** — watch the result exactly as the phone will play it.
3. **Save** — tap *Your animations* (or the individual buttons). Files land in
   `Download/QmgStudio` / `/storage/emulated/0/QmgStudio`:
   * **Module `.zip`** — flash it with KernelSU / Magisk (a metamodule such as *meta-overlayfs* is required).
   * **`bootsamsung.qmg`, `shutdown.qmg`** — copy them yourself to `system/media/`.
4. **Tools** — backup your stock animations, match your phone's size, uninstall the module, reboot, about/update.

> ⚠️ **Always back up your stock files first** (Tools → Backup). Use at your own risk.

## 🔐 Root

Root is **optional**. QMG Studio asks for it when it opens and shows a **Root ✓ / ✗** chip (tap it to ask again).

| | With root | Without root |
|---|---|---|
| Convert videos / GIFs, preview | ✅ | ✅ |
| Save `.qmg` and module `.zip` | ✅ | ✅ |
| Read the stock animation ("Match my phone"), backup | ✅ | partly |
| Uninstall module / reboot from the app | ✅ | ❌ |

QMG animations only run on **Samsung** phones; a notice appears on other brands.

## 🏗️ Build & release

Push to `main` and the workflow in [`.github/workflows/build.yml`](.github/workflows/build.yml) does everything:

1. bumps the version (`1.0 → 1.1 … 1.9 → 2.0`) and commits `version.txt`,
2. builds the signed release APK,
3. verifies it with `apksigner` (the build fails if the APK would not install),
4. publishes a **GitHub Release** with the APK attached.

That Release is what the in-app updater downloads, so **publishing = pushing to `main`**.
Markdown-only and `version.txt` changes do not trigger a build.

Local build: `gradle assembleRelease` (JDK 17, Android SDK 34, NDK 26.1).

## ✅ Status

The encoder is verified bit-exact against Samsung's own decoder library (run in an emulator) for keyframes,
delta frames and many screen sizes (720×1600, 1080×2340, 1440×3120, 1812×2176 …).
Not tested on every device.

## 📄 License

[MIT](LICENSE)

## Credits

Made by Claude

Maintained by xBooM-SL

Made in Sri Lanka
