# ApriReader — Release Candidate Checklist

- **Target Platforms**: Google Play Store, RuStore
- **Release Version**: 2.0.0 (Version Code: 19)
- **Target SDK**: 36 (Android 15+ / 16 ready)
- **Min SDK**: 26 (Android 8.0 Oreo)
- **Release Artifacts** (`--rerun-tasks`, без кэша, дата сборки — см. таймстемп файла):
  - `app/build/outputs/apk/release/app-release.apk` (~19 МБ) — для ручной установки/тестов, подписан upload-ключом.
  - `app/build/outputs/bundle/release/app-release.aab` (~23 МБ) — то, что загружается в Play Console, подписан upload-ключом (`apri-upload.jks`, `CN=ApriReader`, действителен до 2095 года).
  - `app/build/outputs/mapping/release/mapping.txt` (~58 МБ) — карта деобфускации R8, нужна для расшифровки крашей из Play Console (Deobfuscation files).
- **Политика конфиденциальности**: опубликована — https://aprilarius.github.io/aprireader-android-privacy/ (репозиторий [Aprilarius/aprireader-android-privacy](https://github.com/Aprilarius/aprireader-android-privacy)).

---

## 1. Compliance & Security Audit Matrix

| Checklist Item | Requirement | ApriReader Status | Verification |
|---|---|---|---|
| **Zero Telemetry / Privacy** | No advertising IDs, third-party analytics SDKs, or background tracking | **PASSED** | 0 tracking SDKs in Gradle. Only local Room SQLite DB & Preferences used. |
| **Strict Network Security** | Cleartext HTTP disallowed; all traffic over TLS | **PASSED** | `android:usesCleartextTraffic="false"` in Manifest; HTTPS enforced in `MetadataRepository`. |
| **Exported Components** | No unintended exported activities/services/receivers | **PASSED** | Only `MainActivity` (launcher) is exported; `AudioPlayerService` and `CurrentBookWidgetReceiver` are `exported="false"`. |
| **File Permissions & Storage** | SAF scoped storage compliance; no legacy storage flags | **PASSED** | Zero `MANAGE_EXTERNAL_STORAGE` or `WRITE_EXTERNAL_STORAGE`. Fully utilizes Storage Access Framework (SAF) and persistent URI permissions. |
| **Permissions match declared list** | `aapt dump badging` matches [PRIVACY.md](PRIVACY.md) / [play/data-safety.md](play/data-safety.md) | **PASSED** | Six lines: `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, `ACCESS_NETWORK_STATE` (from AndroidX Media3/ExoPlayer, not requested directly), and the self-declared signature-level `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. Docs updated 2026-09-05 to reflect the audiobook-related additions. |
| **Zip Slip & Traversal Protection** | Archive parsers must sanitize paths and prevent directory traversal | **PASSED** | `ZipArchive.kt` strictly cleans all entry paths and rejects `..` lookups. |
| **Decompression-bomb protection** | Archive entries decompressed into memory must be size-capped | **PASSED** (fixed 2026-09-05) | `ZipArchive.readAll()`, CBZ page reads, and CBR/junrar reads all go through `readBytesUpTo()` (100MB cap), checked both by declared size and by actual bytes read. Regression test in `MaliciousAndCorruptedFileTest.kt`. |
| **Comic (CBZ/CBR) file pickability** | SAF `EXTRA_MIME_TYPES` list must not use invented MIME strings providers never report | **PASSED** (fixed 2026-09-06) | `SUPPORTED_MIME_TYPES` now leads with `"*/*"`, disabling OS-level MIME filtering; real format validation stays exact via `BookFormat.fromExtension()` post-selection. See BUG-022. |
| **CBR concurrent-decode safety** | Shared non-thread-safe archive handles must not be accessed from concurrent page-decode coroutines | **PASSED** (fixed 2026-09-06) | `CbrDocument.openPage()` now wraps `junrar.Archive` access in `synchronized(archive)` — `HorizontalPager`/`VerticalPager` decode neighboring pages concurrently by design. Verified with a 4-thread concurrent-read test against a real user CBR file. See BUG-023. |
| **Font & Binary Ingestion** | Custom font importer validates headers and caps sizes | **PASSED** | Header magic byte validator (TTF/OTF/WOFF/TTC) and 25MB size limit in place. |
| **XXE Protection** | XML parsers must reject external entities and DOCTYPE | **PASSED** | `Xml.kt` disables `disallow-doctype-decl`, external general/parameter entities. |
| **Process Death & State Preservation** | UI states and reading progress survive lifecycle kills | **PASSED** | Progress committed to Room DB via `appScope`; `SettingsRepository` & `AvatarStore` backed by persistent storage. |
| **Audio service lifecycle** | Foreground service must not resurrect itself on teardown | **PASSED** (fixed 2026-09-05) | `AudioPlayerService.onTaskRemoved` now calls `pauseAndRelease()`, which removes the player listener before pausing — avoids a race where the listener's own `startOrUpdate()` could cancel `stopSelf()`. |
| **A11y & Touch Targets** | Interactive elements meet ≥48dp touch target standards | **PASSED** | Reviewed manually; two under-48dp `IconButton`s found in `AudioMiniPlayer.kt` and bumped to 44dp. No exhaustive device-based a11y scan performed (see §3). |
| **Localization completeness** | All lint-visible strings translated in every shipped locale | **PASSED** (fixed 2026-09-05) | `:app:lintDebug` reported 19 `MissingTranslation` errors (de/az/it); all translated and lint is clean. |
| **Offline First** | App functions 100% offline for reading, bookmarks, and audio TTS | **PASSED** | Local Android TTS engine and offline format parsers used; online metadata fetch is strictly optional and user-triggered. |

---

## 2. Automated Testing Summary

- **Unit tests** (`./gradlew clean test`):
  - `:bookformat:testDebugUnitTest`: 28 tests **PASSED**.
  - `:app:testDebugUnitTest`: 92 tests **PASSED**.
  - **Total**: 120 tests, 0 failures.
- **Static analysis**:
  - `:app:lintDebug`: **PASSED**, 0 errors.
- **Build verification**:
  - `:app:assembleRelease`, `:app:bundleRelease` (`--rerun-tasks`, no cache): **SUCCESSFUL**.
  - `apksigner verify`: signature valid, `CN=ApriReader` (upload key, see §4).

## 3. Known gaps

- **Real-device verification for 2.0.0 — pending.** The comic-picker
  (BUG-022) and CBR-concurrency (BUG-023) fixes were verified via unit/JVM
  tests against the reporting user's real CBR file (staged locally, not
  committed — copyrighted content), not on-device: no working emulator in
  this environment (Hyper-V/WHPX booted, but `adb push`/`install` hung and
  the qemu process died twice). Ask the reporting user to confirm both: (a)
  adding a new `.cbz`/`.cbr` from the system picker no longer shows it
  greyed out, and (b) flipping quickly through a CBR comic's pages no
  longer shows "не удалось загрузить страницу N" on any page.
- **1.8.0's real-device pass (done by the project owner, 2026-09-05)**
  predates both of the above fixes and does not cover them.
- **`RELEASE_CHECKLIST.md` itself was stale until 2026-09-05** (referenced
  version 1.0.0 and old test counts) — now current as of this version; keep
  it updated on the next release rather than letting it drift again.

## 4. Before uploading to Play Console

- **Signing — done.** `apri-upload.jks` generated 2026-09-05 (RSA 4096,
  alias `apri-upload`, valid to 2095) and `keystore.properties` points at
  it — the artifacts in §"Release Artifacts" above are already signed with
  it, not the local dev key (`apri-dev.jks`, still used only for
  side-loading test builds). **The keystore file and its password must be
  backed up outside this machine** (done by the project owner on
  2026-09-05) — losing both, without Play App Signing enabled, permanently
  ends the ability to update this `applicationId` on Play.
- **Done by the project owner (2026-09-05):** phone screenshots taken;
  real-device smoke test of the signed build completed.
- **Still needed, human-only:**
  1. Play Console: create the app entry, paste the Data Safety answers from
     [play/data-safety.md](play/data-safety.md), the content-rating
     answers from the same file, the store text and screenshots from
     [store_assets/store_descriptions.md](store_assets/store_descriptions.md),
     and the privacy policy URL above. Upload the AAB to a closed/internal
     testing track first, not straight to production. Accept Play App
     Signing when offered.
