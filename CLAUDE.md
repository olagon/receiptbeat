# Receipt Beat

Open source Android app (Kealoha Labs, Apache 2.0) for scanning, importing and
pulling receipts from any email provider, then tracking each one until it is reimbursed or
claimed for taxes. The full spec is docs/SPEC.md. It is the source of truth.

## How to work
- Build phase by phase as listed in docs/SPEC.md under "Build phases and milestones".
- Do not start a new phase until the current gate passes. Tell me when a gate is ready to check.
- Track progress in docs/PROGRESS.md (phase, task, done, notes). Update it as you go.
- Make a branch per phase (phase-0-skeleton, phase-1-capture, ...). Commit small, with clear messages.
- Open a pull request with `gh pr create` at the end of each phase so CI runs.
- Ask before adding any library not named in the spec.

## Commands
- Build: ./gradlew assembleDebug
- Install on my phone: ./gradlew installDebug
- Tests: ./gradlew test
- Lint: ./gradlew ktlintCheck detekt
- Screenshots: ./gradlew verifyRoborazziDebug
- Extraction accuracy: ./gradlew :extraction:extractionEval

## Code rules
- Kotlin, Jetpack Compose, Material 3 Expressive, Hilt, Room plus SQLCipher, WorkManager.
- Screens talk only to repositories in core/data. Never call Room or the network from UI code.
- Money is Long cents. Never use Float or Double for money.
- Heavy work off the main thread. OCR, AI and mail fetch run in WorkManager.
- Every feature module gets ViewModel tests and at least one Compose UI test.
- Never commit secrets. The Microsoft client ID goes in local.properties. API keys come from the user at runtime.
- Never store full card numbers. Mask them in saved OCR text.
- Email access is read only. Never send, delete, move or change mail. No Gmail API in version 1.
- No tracking of any kind. Never add analytics, ads, crash reporting, Firebase or telemetry libraries.
- Never scan the same message twice. Dedupe by Message-ID hash, and for connected accounts check ScanRange first.
- Read email imports as a stream. Never load a whole mailbox file into memory.
- Block remote images when rendering any email.
- Gemini Nano only runs while the app is on screen. Background jobs use the rules scorer and queue borderline emails for the AI.

## UI copy
- Plain, simple, friendly English. Short sentences.
- No em dashes or en dashes in any user facing text.
- Use % for percentages.

## Design reference
- docs/SPEC.md, section "Design system and world class UI"
- design/prototype.html once it exists
- docs/media/receipt_beat_intro.mp4 is the brand reference (logo, colors, motion, tone)

## Brand tokens (sampled from the intro video)
- Background deep teal #093A40, surface teal #0D4B54
- Coral accent #FE6B49 (capture button, heartbeat line, totals)
- Paper #FEF7EC (receipt cards, light text on teal)
- Logo: rounded square teal tile, cream receipt with a coral heartbeat line across it
- The video uses Poppins for headings. Spec says Inter for UI. Use Poppins for display, Inter for body and amounts.
- Intro music: an original ukulele and marimba track (the same song as the hana local tour), CC0 1.0. No credit needed.
