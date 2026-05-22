# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [0.1.1] - 2026-05-17

### Changed
- ContentProvider permission `protectionLevel` lowered from `signature` to `normal` to support multi-repo ecosystem where community-maintained apps (TugaOBD, TugaGPS, TugaMedia, TugaSync) sign with their own keystores. Any Tuga-namespaced app can now read/write settings if it declares the corresponding `<uses-permission>`.
- Runtime `enforceCallerIsTugaSigned()` rewritten to a soft package-namespacing check: non-`com.miktuga.*` callers are logged as warnings but allowed. Strict `checkSignatures()` enforcement removed.

### Security note
- Multi-maintainer architecture means TugaSettings storage is no longer cryptographically isolated. For personal car use the trade-off is acceptable; for higher-security deployments restore `signature` protection + shared keystore among trusted maintainers.

## [0.1.0] - 2026-05-17

### Added
- Initial release. Sixth app in the Tuga ecosystem; centralised settings store for all Tuga utilities.
- `SettingsActivity` with inline segmented controls (no per-row dialogs):
  - Скорость: км/ч ⟷ mph
  - Температура: °C ⟷ °F
  - Расстояние: м ⟷ ft
- Path rows with inline `FolderPickerActivity` browsing `/storage/`:
  - USB mount path
  - Папка музыки
  - Папка отчётов
- Auto-update check switch (reserved for Phase 3 OTA).
- `SettingsProvider` ContentProvider at `content://com.miktuga.settings.provider/settings`, backed by `tugasettings_store` SharedPreferences.
- Two signature-protected custom permissions:
  - `com.miktuga.permission.READ_SETTINGS`
  - `com.miktuga.permission.WRITE_SETTINGS`
- Defense-in-depth `PackageManager.checkSignatures()` enforcement on `insert`/`update`/`delete` (skipped only for in-process calls).
- Typed schema lives in `tuga-design` (`com.miktuga.design.settings.TugaSetting` sealed class + `TugaSettingsClient` accessor).
- Russian launcher label "Tuga Settings"; landscape-locked.
- Signed with shared `_signing/tuga-release.jks` (v1+v2+v3); SHA-1 fingerprint matches every other Tuga app.

### Known limitations
- Consumer apps must declare `<uses-permission>` for both `READ_SETTINGS` and `WRITE_SETTINGS` in their manifests. Without those, `TugaSettingsClient.get` silently returns the declared default (SecurityException is swallowed by design).
- Install order matters: TugaSettings must be installed before any consumer (Android only grants signature-level permissions known to the system at install time).
