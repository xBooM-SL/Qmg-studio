# Changelog

## Next
- Module .zip now saves instantly: it is streamed straight into Download/QmgStudio (no temp copy, no in-memory re-check, STORED entries instead of re-compressing the .qmg files).
- Every card now has a flowing wave animation. Tapping the "Your animations" card saves the module .zip at once.
- In-app update: every time the app opens it checks the latest GitHub Release. If a newer version exists, an "Update available" dialog shows with release notes, a download progress bar and an "Update now" button that opens the Android installer.
- About card: new "Check for updates" button.
- New permissions: INTERNET (check/download) and REQUEST_INSTALL_PACKAGES (install the update).

## 1.0
- Version numbers restart at 1.0 and now grow automatically: 1.0 → 1.1 … 1.9 → 2.0 (see `.github/workflows/build.yml`).
- The one-tap "Install now (root)" button is removed. The tab is now "Save".
- Save buttons write the file at once into Download/QmgStudio (module .zip, bootsamsung.qmg, shutdown.qmg).
- Every build is checked with apksigner before it is published; all releases use the same signing key.


## 1.7
- New home: https://github.com/xBooM-SL/Qmg-studio
- About card shows the real installed version and has buttons for GitHub, Releases and Issues.

## 1.6
- The app asks for root when it opens (KernelSU / Magisk pop-up) and shows a **Root ✓ / ✗** chip; tap it to ask again.
- Header chips are read from the phone the app runs on: model, Android version, animation size, decoder, root.
- Without root: clear message, one-tap install/uninstall/reboot are disabled, saving the module (.zip) and the .qmg files still works.
- A notice appears on non-Samsung phones (QMG animations only run on Samsung).
- With root, the stock `bootsamsung.qmg` is read on first launch to learn the exact animation size of the phone.

## 1.5
- Fix: "Preview error (-1)" after "Verify with system decoder" or GIF export. The preview player, GIF export,
  single-frame preview and the verifier now use independent decoder instances.
- Faster live player (no bitmap allocation per frame).
- Faster encoder: O(1) best-delta lookup + early abort when planning blocks (output stays bit-identical to the
  slower search for the same settings, verified against Samsung's decoder).
- `tools/selftest.py`: round-trip self test.
- Release workflow: builds a signed APK on every push, and publishes a GitHub Release when you push a tag like `v1.5`.

## 1.4
- Fixed signing key and auto-incrementing versionCode, so a new build installs as an *update* over the old one.
- Parallel frame extraction, encoder cache, "Glow" fit mode, live clip preview.

## 1.2 / 1.3
- Live preview with the real encoder, boot + shutdown at once, any screen size (multiple of 4), GIF in/out.
