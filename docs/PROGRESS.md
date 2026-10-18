# Progress

Source: docs/SPEC.md, "Build phases and milestones". Check items off as they land.


## Phase 0. Repo and skeleton
- [x] Create the GitHub repo with README, LICENSE, CLAUDE.md, SECURITY.md, CONTRIBUTING.md, .gitignore
- [x] Set up Gradle modules, version catalog and convention plugins
- [x] Theme, colors, type and shared components in `core/designsystem`
- [x] Bottom bar with five empty tabs and the Capture button
- [x] Room database with SQLCipher and the core tables
- [x] GitHub Actions running lint, tests and a debug build

**Gate:** CI is green and the app opens on a real phone

## Phase 1. Capture and extraction
- [x] Document Scanner with single, batch and long modes
- [x] Import from Files and Photos, and the share target
- [x] Image cleanup, thumbnails, hashing
- [x] OCR, rules parser, Gemini Nano extraction, confidence scores
- [x] Inbox review card, Receipts list, Receipt detail with edit
- [x] Receipt test set and the extraction eval task

**Gate:** totals at 90% or better on the test set, and capture to card under 3 seconds

## Phase 2. Tracking
- [x] Categories with tax mapping presets
- [x] Payers and projects
- [x] Tracks, statuses and the stepper in Receipt detail
- [x] Home dashboard with the three money tiles
- [x] Search, filters, duplicate detection
- [x] Merchant memory for smart suggestions
- [x] Reminders

**Gate:** you can run a full month of your own receipts through it without a spreadsheet

## Phase 3. Email for everyone
- [x] Add email receipts wizard with a step by step guide for each provider
- [x] Streaming import of .mbox, .eml, .zip, .pst and .olm with progress, resume and Message-ID dedupe
- [x] Header skim, rules scorer and field pull from schema markup, PDF attachments and body text
- [x] Gemini Nano pass for borderline emails while the app is on screen, with a queue for later
- [x] Email to PDF snapshot with remote images blocked
- [x] Review stack with grouping, swipe, bulk keep and sender rules
- [x] IMAP live connect with app passwords
- [ ] Outlook through Microsoft sign in (needs your free Microsoft Entra app registration and client ID)
- [x] Remembered scan ranges, scheduled auto scans and scan log

**Gate:** a 12 month import from your own Gmail finds 90% of real receipts with few false hits, and the same works for one friend on Outlook or Yahoo

## Phase 4. Money, exports and backup
- [x] Reimburse view, report builder, PDF and CSV, mark paid (full and partial)
- [x] Taxes view, tax year ZIP, year lock
- [x] Backup and restore (save the ZIP to Google Drive or anywhere through the system picker)
- [ ] Automatic Google Drive app folder backup (needs a Google Cloud OAuth client for your signing key)
- [x] Full data export

**Gate:** you submit one real reimbursement report built in Receipt Beat

## Phase 5. Polish and release
- [x] Motion and haptics (springs, shimmer, swipe haptics, heartbeat logo). Shared element photo-to-card animation not done
- [x] Accessibility pass: money read as money, status icon plus color, swipe actions in the accessibility menu, 200% font checked on Home
- [x] Foldable and tablet layouts (rail plus list and detail side by side)
- [x] Glance widget, Quick Settings tile, app shortcuts
- [ ] Baseline Profiles and performance checks (needs a real phone)
- [x] Privacy policy (docs/PRIVACY.md) and store listing text (docs/STORE_LISTING.md). Screenshots not taken
- [ ] Play internal testing track, then the public Play Store listing

**Gate:** five outside testers use it for two weeks with no crashes and no lost receipts

## Notes

- 2026-10-17: Repo files laid out. Intro video reviewed, matches spec, no changes needed.
- Phase 0: modules are app, core/model, core/database, core/data, core/designsystem. Feature screens live as packages in app; extraction and mail become modules when their phase starts.
- Phase 0: shared module setup lives in the root build.gradle.kts instead of a build-logic convention plugin. Same effect, fewer files.
- Phase 0: Material 3 Expressive needs material3 1.4 (alpha). Using stable Material 3 with spring motion for now.
- Phase 0: gate checked on an emulator (receiptbeat_test, API 36) because the phone could not pair yet.
- Phase 1: receipt test set is 44 synthetic OCR text receipts (no real ones yet). Rules parser eval: total 100%, date 100%, merchant 95.5%. The parser was tuned on this set, so treat as a ceiling. Add real receipts to testdata/receipts.
- Phase 1: checked on emulator: import 3 rendered receipts, all totals, dates and merchants read correctly. Gemini Nano and the Document Scanner UI need a real phone to check.
- Phase 1: images are not converted to grayscale before OCR; ML Kit does its own. Coil was skipped; a small cached decoder reads the encrypted images.
- Phase 1: optional cloud AI uses HttpURLConnection instead of Ktor (one less library).
- Phase 2: Settings (categories with tax mapping and deductible %, payers, projects, AI, security, reminders, appearance, trash, about), onboarding with the intro video, daily reminder worker. Unpaid reminders use the receipt's last change date.
- Phase 3: file import checked on emulator with a 6 email mbox: 4 receipts found, newsletter and personal mail skipped, kept emails become Inbox receipts. IMAP tested against GreenMail in unit tests; a real Gmail or Yahoo account still needs a manual check.
- Phase 4: report TR-2026-001 built on the emulator: cover page, summary table, receipt pages, CSV. Partial payments fill the oldest receipts first. Backup ZIP is plain (not encrypted), the app says so.
- After phase 5: Trips screen (from Home). Add receipts dated in a trip in one tap, Trip filter and bulk Add to trip in Receipts, trip PDF and CSV (cover with spend by category, summary, a page per receipt). Checked on the emulator.
