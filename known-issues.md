## Known Issues

- App icon still shows default Android icon on device.
  - Tried: replace mipmap PNGs, add adaptive icon XMLs in `mipmap-anydpi-v26`,
    switch adaptive layers to PNG foreground + transparent background,
    clean/rebuild, uninstall/reinstall.
  - Next: inspect APK with `aapt`/`apkanalyzer` and verify launcher cache on device.

- Cloud storage files (e.g., Drive/Dropbox) can cause a crash on app restart.
  - Current workaround: open only local files via Storage Access Framework.
