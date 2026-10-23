# QMG Studio  ·  Samsung custom boot animations

**Repo:** https://github.com/xBooM-SL/Qmg-studio  ·  **Download:** [Releases](https://github.com/xBooM-SL/Qmg-studio/releases)  ·  **Problems:** [Issues](https://github.com/xBooM-SL/Qmg-studio/issues)

Open-source tools to make Samsung `.qmg` (Qmage) boot / shutdown animations from any video or GIF.

* **Android app** (`app/`, "QMG Studio"): video/GIF → `.qmg`, live preview, GIF export, one-tap install (root).
* **CLI** (`tools/`): `ffmpeg` → `qmgenc` for Termux / Linux. Also decodes `.qmg` back to raw frames.
* **Format notes**: [`docs/FORMAT.md`](docs/FORMAT.md)

Nothing from Samsung is bundled. The encoder reads the delta table at run time from the
`libQmageDecoder.so` on your own device (exported symbol `Qmage_ori_deltaRGB_new`).

## App features
* **Live preview** pinned on top while you edit: fit / fill / stretch, fade, dithering and quality are shown by the real encoder on a phone mock-up
* Make **boot and shutdown at the same time** (or either one)
* Any Samsung screen size (multiple of 4), detected from your stock `bootsamsung.qmg` ("Match my phone")
* Video **and GIF** input, trim up to 60 s, speed, fit/fill/stretch, fade in/out
* Quality presets, dithering, and **target file size** (per-frame rate control)
* **Preview** decoded from the `.qmg` itself, optional check against the phone's own Samsung decoder
* **QMG → GIF** export, open any `.qmg` to preview
* Boot + shutdown animations, KernelSU/Magisk module (.zip) or direct root install, backup/undo

## Build / release the APK
Push to GitHub: the workflow in `.github/workflows/build.yml` builds the signed `QmgStudio-apk` artifact.
To publish a release: `git tag v1.5 && git push origin v1.5` (the APK is attached to the GitHub Release automatically).
Every build uses the same signing key and a higher versionCode, so new APKs install as **updates** (see `SECURITY.md`).

## CLI (Termux)
```
pkg install clang ffmpeg
cp /sdcard/qmg_backup/libQmageDecoder.so ~/
clang -O2 -o qmgenc tools/qmgenc_cli.c app/src/main/cpp/qmgcore.c -Iapp/src/main/cpp
sh tools/video2qmg.sh myvideo.mp4 bootsamsung.qmg 8        # 720x1600 by default
QMG_W=1080 QMG_H=2340 sh tools/video2qmg.sh clip.mp4 out.qmg 8
./qmgenc -d bootsamsung.qmg -l ~/libQmageDecoder.so -o frames.rgb565   # decode
python3 tools/selftest.py ~/libQmageDecoder.so                           # round-trip self test
```
The device plays animations at 12 fps. Keep clips short (3-6 s) or use the target-size option.

## Root
QMG Studio asks for root when it opens (needed for one-tap install, reading the stock animation on some phones, and reboot).
Without root it still converts videos and saves the `.qmg` / module `.zip`, which you can flash or copy yourself.

## Install
Use the app ("Install" tab), or put the files in a module under `system/media/`
(`bootsamsung.qmg`, `bootsamsungloop.qmg`, `shutdown.qmg`). A metamodule such as meta-overlayfs is required.
**Back up your stock files first.**

## Status
The encoder is verified bit-exact against Samsung's own decoder library (run in an emulator) for keyframes, delta frames
and many screen sizes (720x1600, 1080x2340, 1440x3120, 1812x2176 ...). Not tested on every device. Use at your own risk.

License: MIT
