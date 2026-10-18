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
- [ ] Document Scanner with single, batch and long modes
- [ ] Import from Files and Photos, and the share target
- [ ] Image cleanup, thumbnails, hashing
- [ ] OCR, rules parser, Gemini Nano extraction, confidence scores
- [ ] Inbox review card, Receipts list, Receipt detail with edit
- [ ] Receipt test set and the extraction eval task

**Gate:** totals at 90% or better on the test set, and capture to card under 3 seconds

## Phase 2. Tracking
- [ ] Categories with tax mapping presets
- [ ] Payers and projects
- [ ] Tracks, statuses and the stepper in Receipt detail
- [ ] Home dashboard with the three money tiles
- [ ] Search, filters, duplicate detection
- [ ] Merchant memory for smart suggestions
- [ ] Reminders

**Gate:** you can run a full month of your own receipts through it without a spreadsheet

## Phase 3. Email for everyone
- [ ] Add email receipts wizard with a step by step guide for each provider
- [ ] Streaming import of .mbox, .eml, .zip, .pst and .olm with progress, resume and Message-ID dedupe
- [ ] Header skim, rules scorer and field pull from schema markup, PDF attachments and body text
- [ ] Gemini Nano pass for borderline emails while the app is on screen, with a queue for later
- [ ] Email to PDF snapshot with remote images blocked
- [ ] Review stack with grouping, swipe, bulk keep and sender rules
- [ ] IMAP live connect with app passwords, and Outlook through Microsoft sign in
- [ ] Remembered scan ranges, scheduled auto scans and scan log

**Gate:** a 12 month import from your own Gmail finds 90% of real receipts with few false hits, and the same works for one friend on Outlook or Yahoo

## Phase 4. Money, exports and backup
- [ ] Reimburse view, report builder, PDF and CSV, mark paid (full and partial)
- [ ] Taxes view, tax year ZIP, year lock
- [ ] Google Drive app folder backup and restore
- [ ] Full data export

**Gate:** you submit one real reimbursement report built in Receipt Beat

## Phase 5. Polish and release
- [ ] Motion, haptics, shared element capture animation
- [ ] Accessibility pass (TalkBack, 200% font, contrast)
- [ ] Foldable and tablet layouts
- [ ] Glance widget, Quick Settings tile, app shortcuts
- [ ] Baseline Profiles and performance checks
- [ ] Privacy policy page, Play Store listing, screenshots
- [ ] Play internal testing track, then the public Play Store listing

**Gate:** five outside testers use it for two weeks with no crashes and no lost receipts

## Notes

- 2026-10-17: Repo files laid out. Intro video reviewed, matches spec, no changes needed.
- Phase 0: modules are app, core/model, core/database, core/data, core/designsystem. Feature screens live as packages in app; extraction and mail become modules when their phase starts.
- Phase 0: shared module setup lives in the root build.gradle.kts instead of a build-logic convention plugin. Same effect, fewer files.
- Phase 0: Material 3 Expressive needs material3 1.4 (alpha). Using stable Material 3 with spring motion for now.
- Phase 0: gate checked on an emulator (receiptbeat_test, API 36) because the phone could not pair yet.
