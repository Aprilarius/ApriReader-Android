# ApriReader QA Master Plan

Comprehensive Quality Assurance, Security, Performance, and Release Readiness Master Plan.

| ID | Area / Phase | Scope & Description | Status |
|---|---|---|---|
| **P0-01** | **Architecture & Technology Inventory** | Full mapping of components, modules, dependencies, entry points | PASSED |
| **P1-01** | **Build Pipeline & Gradle Config** | Clean debug, release APK, and App Bundle (AAB) builds | PASSED |
| **P1-02** | **Static Analysis & Compiler Warnings** | Kotlin compiler warnings, ProGuard/R8 rules, deprecations | IN PROGRESS |
| **P2-01** | **Codebase Deep Audit: ViewModels** | Lifecycle state handling, error handling, cancellation, memory safety | IN PROGRESS |
| **P2-02** | **Codebase Deep Audit: Repositories & DAOs** | Room DB transactions, race conditions, IO dispatchers | IN PROGRESS |
| **P2-03** | **Codebase Deep Audit: UI & Navigation** | Navigation graph, back-handling, dialogs, sheets, bottom drawer | IN PROGRESS |
| **P3-01** | **Core Reader: Flow Reader (Continuous)** | Vertical continuous scroll, position save/restore, TOC jump, search | IN PROGRESS |
| **P3-02** | **Core Reader: Paged Reader (Horizontal/Vertical)** | Paginated layout, page turns, bookmarks, font scaling, margin adjustments | IN PROGRESS |
| **P3-03** | **Core Reader: RSVP Speed Reader** | RSVP engine, variable WPM, pause/resume, punctuation delay | IN PROGRESS |
| **P3-04** | **Core Reader: TTS Audio Read-Aloud** | System TTS engine integration, highlight current sentence, speed controls | IN PROGRESS |
| **P4-01** | **Format Engine: EPUB** | EPUB2 / EPUB3, NCX/NAV TOC, chapter extraction, internal images, cover | IN PROGRESS |
| **P4-02** | **Format Engine: FB2 / FB2.ZIP** | XML parsing, base64 images, footnotes, metadata, corrupted streams | IN PROGRESS |
| **P4-03** | **Format Engine: Plain Text (TXT)** | Large files, auto-encoding detection (UTF-8, Windows-1251, CP866, KOI8-R) | IN PROGRESS |
| **P4-04** | **Format Engine: PDF** | PdfBox-Android rendering, text extraction, page rendering, memory safety | IN PROGRESS |
| **P4-05** | **Format Engine: Comic Archives (CBZ/CBR)** | Zip & RAR extraction, image ordering, aspect ratios, corrupted archives | IN PROGRESS |
| **P5-01** | **State Management & Edge Cases** | Empty library, 1000+ books, missing files, permission denial, storage unplugged | IN PROGRESS |
| **P6-01** | **Lifecycle Torture Testing** | Process death, rotation, rapid background/foreground, memory pressure | IN PROGRESS |
| **P7-01** | **UI/UX & Design Themes** | 5 Design Styles (Liquid Glass, Neumorphism, Glassmorphism, Solid, Wood), Contrast | IN PROGRESS |
| **P8-01** | **Responsive & Multi-Window Layout** | Small phones, tablets, split-screen, landscape orientation, keyboard avoidance | IN PROGRESS |
| **P9-01** | **Accessibility (a11y) & TalkBack** | Content descriptions, semantic headings, minimum 48dp touch targets, font scaling | IN PROGRESS |
| **P10-01** | **Performance & Startup Time** | Cold start, main thread blocking, lazy grid rendering, smooth scrolling | IN PROGRESS |
| **P11-01** | **Memory Leak Audit** | Bitmap recycling, Context leak prevention, coroutine cleanup, TTS leaks | IN PROGRESS |
| **P12-01** | **Battery & Resource Efficiency** | Zero unnecessary background polling, wakelock sanity, efficient parsing | IN PROGRESS |
| **P13-01** | **Storage & Cache Management** | Cover cache size, temporary files cleanup, SAF tree URI retention | IN PROGRESS |
| **P14-01** | **Database Integrity & Migrations** | Room schemas, indexing, foreign keys, transaction rollbacks, DB upgrades | IN PROGRESS |
| **P15-01** | **Security & OWASP Mobile Audit** | Path traversal, zip slips, XML External Entity (XXE), cleartext traffic | IN PROGRESS |
| **P16-01** | **Malicious File & Input Fuzzing** | Zip bombs, malformed XML, broken HTML, zero-length files, recursive tags | IN PROGRESS |
| **P17-01** | **Privacy & Local-First Verification** | Zero telemetry, zero analytics tracking, local avatars, local stats | IN PROGRESS |
| **P18-01** | **Permissions Audit** | Strict minimal permissions (INTERNET for metadata/network covers only) | IN PROGRESS |
| **P19-01** | **Release Hardening & Signing** | Minification (R8), ProGuard rules, debug logs stripped, dual signing | IN PROGRESS |
| **P20-01** | **Crash & ANR Prevention** | Rapid click spam, double-tap, cancellation tokens, non-blocking IO | IN PROGRESS |
| **P21-01** | **Offline Operation** | Full standalone functionality without internet connection | IN PROGRESS |
| **P22-01** | **Interruption Handling** | Incoming calls, alarms, notification drawer pull, app switching | IN PROGRESS |
| **P23-01** | **Backup & Data Safety** | Auto-backup rules, exclusion of large temp cache and database locks | IN PROGRESS |
| **P24-01** | **Third-Party Dependencies** | Dependency vulnerability scan, unneeded libraries elimination | IN PROGRESS |
| **P25-01** | **Automated Test Suite Expansion** | End-to-end regression tests, fuzz tests, format tests | IN PROGRESS |
| **P26-01** | **Google Play & RuStore Readiness** | Target SDK 36, 64-bit binaries, Data Safety compliance, asset sizes | IN PROGRESS |
| **P27-01** | **Release Checklist** | All release validation criteria signed off | NOT TESTED |
| **P28-01** | **Bug Tracking & Resolution** | Every identified defect logged in BUGS.md and fixed | IN PROGRESS |
| **P29-01** | **Red Team Final Audit** | Independent antagonistic verification & Release Candidate sign-off | NOT TESTED |

