# ApriReader — Final Release Audit & Red Team Report

**Date**: 2026-09-06
**Audited Target**: ApriReader (Package: `com.aprireader.app`), Version 2.0.0 (Version Code 19)
**Verdict**: **RELEASE CANDIDATE**

---

## 1. Executive Summary

ApriReader is a privacy-first, local-first reader application for Android 8.0+ (Min SDK 26) through Android 15/16 (Target SDK 36). This pass re-audited the full codebase end to end ahead of the 2.0.0 release: manifest and permissions, signing, secrets, network security, SQL usage, WebView usage, telemetry, ProGuard rules, cross-module concurrency, storage-scan safety, and DB migration risk — on top of the format-parser and lifecycle work already covered by earlier audits (see [FINAL_RELEASE_AUDIT history / BUGS.md]).

**Two new defects were found and fixed** during this pass (BUG-022, BUG-023 in [BUGS.md](BUGS.md)) — both in the comic (CBZ/CBR) pipeline, both confirmed by the reporting user's real-world use and reproduced with targeted tests before being fixed. No other correctness or security defects were found in this pass.

The application remains completely free of third-party telemetry, trackers, and advertising libraries. All book parsing, caching, and text-to-speech rendering operate locally on device.

---

## 2. New Findings This Pass

### 2.1 BUG-022 — Comic files unselectable in the system file picker (HIGH)
`LibraryScreen.kt`'s `SUPPORTED_MIME_TYPES`, passed to the SAF picker via `EXTRA_MIME_TYPES`, listed invented MIME strings for CBZ/CBR (`application/vnd.comicbook+zip`, `application/vnd.comicbook-rar`) that real Android `DocumentsProvider`s never report for these extensions. The system picker greys out any file whose reported MIME type isn't in the list — making `.cbz`/`.cbr` files impossible to select. **Fixed** by prepending a `"*/*"` wildcard, which disables OS-level MIME filtering; the app's own `BookFormat.fromExtension()` check after selection remains the actual gate, now surfaced through a friendly "unsupported format" message instead of silent failure.

### 2.2 BUG-023 — CBR page corruption under concurrent decode (HIGH)
`CbrDocument.openPage()` read pages from a single shared `junrar.Archive` instance with no synchronization. The reader's pager decodes the current page and both neighbors concurrently (`beyondViewportPageCount = 1`, each page's `produceState` running on its own IO-dispatcher coroutine). junrar's `Archive` is not safe for concurrent `getInputStream()` calls; concurrent access corrupted its internal decompressor state, silently decoding some pages to 0 bytes. Confirmed with a 4-thread concurrent-read test against a real user CBR file before fixing (page 0 → 0 bytes without the fix), and reconfirmed passing after. **Fixed** by wrapping the read in `synchronized(archive) { ... }`, the same pattern already used for the system `PdfRenderer` in `BookSession.Pdf`. `CbzDocument` was unaffected — its `RandomAccessSource.readAt()` implementations were already `@Synchronized`.

---

## 3. Areas Re-Verified This Pass (No New Issues)

- **Manifest & exported components**: only `MainActivity` is exported (launcher + VIEW/SEND intent filters with explicit MIME lists); `AudioPlayerService` and `CurrentBookWidgetReceiver` are `exported="false"`.
- **Secrets**: no hardcoded API keys, tokens, or credentials anywhere in `:app` or `:bookformat`. `keystore.properties` / `*.jks` correctly gitignored and not tracked.
- **Network security**: `usesCleartextTraffic="false"`; `MetadataRepository` enforces HTTPS on every request and upgrades any `http://` cover URL; connect/read timeouts set; cover downloads capped at 15MB after fetch (see §4 for a minor hardening note).
- **SQL**: no `@RawQuery`/string-built SQL anywhere — all Room access is through typed DAOs.
- **WebView**: none in the app (`FlowReader.kt`'s only "WebView" mention is a comment explaining why native Compose text rendering was chosen instead).
- **Telemetry**: zero analytics/crash-reporting/ad SDKs in the dependency graph.
- **ProGuard/R8**: release build is minified and shrunk; rules keep only what junrar/PDFBox/Room genuinely need reflectively.
- **Folder scanning** (`LibraryScanner.scanTree`): BFS with a `visited` document-id set — safe against directory cycles/symlink loops in a SAF tree.
- **Room schema**: version is still 1, no entities changed this cycle — no migration needed for 2.0.0.
- **Cross-module concurrency**: besides the CBR bug above, no other shared-mutable-state document reader was found unsynchronized; `FileRandomAccessSource`/`DescriptorRandomAccessSource` (used by EPUB/CBZ/PDF byte access) are already `@Synchronized`, and PDF page rendering is already `synchronized(renderer)`.

## 4. Minor, Non-Blocking Observations

- `MetadataRepository.fetchImageBytes()` calls `body.bytes()` before checking the 15MB size cap, so a response without an honest `Content-Length` could still be fully buffered into memory before rejection. Low real-world risk (HTTPS-only, fixed trusted catalogs — FantLab/Open Library/Google Books/Gutendex/Wikipedia — not arbitrary attacker-controlled hosts, and this path is opt-in/user-triggered), left as-is rather than changed under release-freeze; worth a follow-up to cap via a length-limiting `Source` if hardened further.
- `QA_MASTER_PLAN.md` still lists most phases as "IN PROGRESS" from an earlier planning pass — it was not treated as a live checklist this session (no device available to close out phases like device-based a11y or lifecycle torture testing); [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) is the authoritative, current release gate.

---

## 5. Release Artifact Specifications

- **Release APK**: `app/build/outputs/apk/release/app-release.apk` — **19.3 MB**, `versionCode=19`, `versionName=2.0.0`.
- **Release AAB**: `app/build/outputs/bundle/release/app-release.aab` — **23.7 MB**.
- **Signing**: `apri-upload.jks`, `CN=ApriReader`, SHA-1 `74:fc:7b:81:49:45:12:78:3f:be:90:b9:dc:56:61:2d:9f:86:d0:f6` — unchanged from the previous release, confirming no signing regression.
- **Automated tests**: 120 / 120 **PASSED** (`:bookformat` 28, `:app` 92). `:app:lintDebug`: 0 errors.

---

## 6. Final Verdict

# ✅ RELEASE CANDIDATE — 2.0.0

Real-device confirmation of BUG-022/BUG-023 by the reporting user is the one open item before calling this fully closed (see [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) §3) — everything else in this report is verified by static analysis, unit/JVM tests against a real reproduction file, lint, and a clean signed release build.
