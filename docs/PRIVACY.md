# Receipt Beat privacy policy

Last updated: October 18, 2026

Receipt Beat is a free, open source app from Kealoha Labs. It keeps your receipts on your phone. We do not collect, see, sell or share any of your data.

## What stays on your phone

- Receipt photos, PDFs and the text read from them
- Amounts, dates, merchants, categories, payers, notes
- Emails you choose to keep, and the scores used to find them

All of it is stored on your phone, encrypted. The database uses SQLCipher and files use Google Tink, with keys held in the Android Keystore. Card numbers are never stored in full.

## What we collect

Nothing. Receipt Beat has no account, no analytics, no ads, no crash reporting and no telemetry. It never phones home.

## When the app uses the network

Only for things you turn on:

- **Your email provider**, if you connect an account. Access is read only. Receipt Beat never sends, deletes or moves mail. Your app password stays in the Android Keystore and can be removed in one tap.
- **An AI provider you choose** (Gemini, Claude or OpenAI), only if you paste your own key. Only the receipt text is sent, not the photo, unless you allow images. That provider's own privacy policy applies to those calls.
- **Where you save backups**, such as Google Drive, when you pick it in the system file picker.

Email files you import are read on your phone and never uploaded. Remote images in emails are never loaded, so tracking pixels never fire.

## On device AI

When your phone supports it, Receipt Beat uses Gemini Nano, which runs on your phone through Android's AICore. Nothing it reads leaves your phone.

## Your control

- Export everything as a ZIP at any time (Settings, Backup and restore).
- Delete any receipt. Deleted receipts stay in Trash for 30 days, then are gone.
- Uninstalling the app removes everything it stored.

## Children

Receipt Beat is not directed at children under 13.

## Contact

Questions or concerns: open an issue at github.com/olagon/receiptbeat, or for security problems use the private advisory form described in SECURITY.md.
