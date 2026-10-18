# Security

Receipt Beat holds card digits, addresses and medical spending, so we treat security reports seriously.

## Reporting a problem

Please do not open a public issue for a security problem. Use GitHub's private
[security advisory form](https://github.com/olagon/receiptbeat/security/advisories/new) instead.
We will reply within 7 days.

## What the app promises

- Everything is stored on the phone, encrypted (SQLCipher for the database, Tink for files, keys in the Android Keystore).
- No analytics, ads, crash reporting or telemetry of any kind.
- Email access is read only. Remote images in emails are never loaded.
- The only network calls go to your own email provider, your own backup location, and an AI provider only if you add your own key.
