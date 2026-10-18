# Receipt Beat

**Stay on top of every receipt.** Scan paper receipts, pull receipts out of any email, and track each one until you are paid back or it is claimed on your taxes.

https://github.com/olagon/receiptbeat/raw/main/docs/media/receipt_beat_intro.mp4

Free and open source, from Kealoha Labs. Android 10 and up.

## What it does

- **Snap it. Done.** Auto crop and auto read in under 3 seconds.
- **Works with any email.** Gmail, Outlook, Yahoo, iCloud, Proton and more. Import an export file or connect the account.
- **Reads it for you.** Merchant, date, tax and total, filled in.
- **Smart AI, right on your phone.** Gemini Nano where available, plus a rules parser that works on every phone.
- **Tracked until you are paid back.** Inbox, Ready, Submitted, Reimbursed. Or Claimed for taxes.
- **Tax time, sorted.** Totals by category, ready for your accountant.
- **Reports in two taps.** PDF, CSV and every photo.
- **Private by design.** No ads, no tracking, no account. It all stays on your phone.

## Build

You need Android Studio (or JDK 17 and the Android SDK).

```bash
./gradlew assembleDebug        # build
./gradlew installDebug         # install on a connected phone
./gradlew test                 # unit tests
./gradlew ktlintCheck detekt   # lint
```

The full spec is in [docs/SPEC.md](docs/SPEC.md). Progress is in [docs/PROGRESS.md](docs/PROGRESS.md).

## License

Apache 2.0. Intro music: "Life of Riley" by Kevin MacLeod (incompetech.com), licensed under CC BY 4.0.
