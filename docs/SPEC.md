# Receipt Beat: Full Build Spec

Oct 17, 2026 · @Kealoha

## Overview and goals

Receipt Beat turns every receipt a person gets, on paper or by email from any provider, into a clean record that stays tracked until it is reimbursed or claimed on taxes.

Most receipt apps stop at "scan and store." The gap they leave is the follow through. People lose money because a receipt sits in a pile and never gets submitted, or because they can't find the deductible ones in April. Receipt Beat is built around that follow through. Every receipt has a clear state, and the app keeps nudging until it reaches the end of its life.

Receipt Beat is **open source** under Kealoha Labs, published on GitHub at github.com/olagon/receiptbeat, and it is built **for everyone**, whatever email they use. That shapes four choices across this spec. Everything runs on the phone, so nobody needs an API key or a cloud account. Email works with any provider, by importing an export file or connecting the account directly, with no Google approval needed in version 1. No secrets ever go in the repo. And there is **no tracking of any kind**. No analytics, no ads, no crash reporting, no telemetry. Receipt Beat never phones home.

### Who it is for

| User | What they need most |
| --- | --- |
| Employee who travels or buys for work | Fast capture, expense reports, know what the company still owes them |
| Freelancer or small business owner | Tax categories, mileage, year end totals for their accountant |
| Board member or volunteer | Separate reimbursement tracking per organization |
| Household | Medical (HSA/FSA), charitable gifts, warranty and return records |

### Goals for version 1

1. Capture a paper receipt in under 3 seconds, with auto crop and auto read.
2. Bring in receipts from any email provider, by importing an email export or connecting the account, then approve likely receipts in bulk.
3. Read merchant, date, total, tax and currency with at least 95% accuracy on clear receipts.
4. Track every receipt from **Inbox** to **Reimbursed** or **Claimed for taxes**, with nothing falling through.
5. Export a clean report (PDF plus CSV plus original images) in two taps.
6. Work fully offline for capture. Sync when there is signal.

### What it will not do in version 1

It will not file taxes, connect to bank accounts, or replace full accounting software. It exports cleanly to tools that do.

## Core user flows

Five flows cover almost everything a user does. Each one should feel like it takes seconds, not minutes.

### 1. Scan a paper receipt

1. Tap the big capture button on the home screen, or use the home screen widget, Quick Settings tile, or lock screen shortcut.
2. The camera opens with live edge detection. It snaps on its own when the receipt is steady and in focus.
3. The user can keep scanning more receipts in a row (batch mode) or long receipts in pages (stitch mode).
4. The receipt drops into the **Inbox** right away. Reading happens in the background.
5. A few seconds later the card fills in with merchant, date, total, tax and a suggested category.
6. The user confirms with one tap, or fixes any field. Low confidence fields glow so the eye goes right to them.

### 2. Add from a file or another app

1. Share a PDF, photo, screenshot or saved email to Receipt Beat from any app (Android share sheet).
2. Or tap **Import** and pick from Files, Photos or Google Drive.
3. Same reading pipeline as a scan. Lands in the Inbox.

### 3. Bring in receipts from email

1. The user taps **Add email receipts** and picks their provider (Gmail, Outlook, Yahoo, iCloud, Proton, other). The app shows the simplest way in for that provider, in plain words.
2. They either **import a file** (for example, a Gmail export from Google Takeout) or **connect the account** with an app password or Microsoft sign in.
3. They pick a date range (3, 6, 12, 24 months or a tax year). The scan runs on the phone with a progress card. Nothing leaves the phone.
4. Found receipts show in a **Review** stack, grouped by sender, with subject, date and amount visible.
5. The user swipes right to keep, left to skip, or selects many and taps **Keep all**, setting a category for a whole group at once. Skipped senders can be muted forever.
6. Connected accounts keep finding new receipts on a schedule and show a small badge on the Review tab, never noisy alerts. A single email can also be saved as PDF or .eml and shared straight to the app.

### 4. Get reimbursed

1. The user opens the **Reimburse** view and filters by payer (for example, "Resource Innovations" or "Board travel").
2. They select receipts and tap **Create report**.
3. Receipt Beat builds a PDF with a summary table and each receipt image, plus a CSV.
4. They send it by email, share it, or upload it to their company tool.
5. The receipts move to **Submitted**. When the money comes in, the user marks them **Reimbursed** (single, bulk, or by marking the whole report paid).
6. If a report sits unpaid past a set number of days, Receipt Beat reminds the user to follow up.

### 5. Tax time

1. The user opens **Taxes** and picks the tax year.
2. They see totals by tax category (for example, Schedule C lines, medical, charity, home office).
3. They tap **Export for my accountant**. Receipt Beat makes a ZIP with a PDF summary, a CSV, and every original image named by date and merchant.
4. Receipts move to **Claimed** for that year and lock so totals don't shift by accident.

## Feature list by release

Version 1 is everything needed to capture, track and export with confidence. Version 1.5 and 2 add the features that make people recommend the app to friends.

| Feature | Release | Why users want it |
| --- | --- | --- |
| Auto edge detect camera with auto capture | 1.0 | No fiddling. Point and done |
| Batch scan and long receipt stitching | 1.0 | Ten receipts after a trip in one go |
| Import from share sheet, Files, Photos, Drive | 1.0 | Digital receipts arrive in many places |
| On device OCR plus AI field extraction | 1.0 | Fast, private, works offline |
| Email from any provider: file import (mbox, EML, PST, OLM), live connect, review stack, remembered ranges, scheduled scans | 1.0 | The killer feature. Finds what people forgot |
| Categories (expense and tax) with smart suggestions | 1.0 | Learns from the user's own choices |
| Status tracking from Inbox to Reimbursed or Claimed | 1.0 | Nothing falls through |
| Payers and projects (who owes, what it was for) | 1.0 | Separates work, board, client and personal |
| Search across merchant, notes, amount, and full receipt text | 1.0 | "That coffee in Hilo last March" |
| Duplicate detection (same scan twice, or paper plus email copy) | 1.0 | Stops double claims |
| PDF, CSV and ZIP exports | 1.0 | Hand it to anyone |
| Encrypted local storage and app lock (fingerprint, face, PIN) | 1.0 | Receipts hold card digits and addresses |
| Cloud backup to the user's own Google Drive | 1.0 | Never lose a year of receipts |
| Home screen widget and Quick Settings tile | 1.0 | Capture without opening the app |
| Reminders for unsubmitted and unpaid receipts | 1.0 | The follow through that saves real money |
| Split a receipt across categories or payers | 1.5 | Hotel room is work, minibar is not |
| Multi currency with auto conversion on the receipt date | 1.5 | Travel abroad |
| Mileage log with GPS trip tracking and IRS rate | 1.5 | Big deduction most people skip |
| Per diem tracking | 1.5 | Common for travel policies |
| Optional Gmail API sync, after Google verification | 2.0 | Gmail with one tap sign in, no app password |
| Spending insights and charts by category and month | 1.5 | See where money goes |
| Return and warranty tracker with expiry alerts | 2.0 | Receipts are proof of purchase |
| Shared spaces (spouse, bookkeeper, assistant) | 2.0 | Hand off the busywork |
| Direct export to QuickBooks, Xero, Wave | 2.0 | Saves the accountant time |
| Ask your receipts (natural language questions) | 2.0 | "How much did I spend on board travel this year?" |
| Tablet and Chromebook layouts | 2.0 | Bigger screen for monthly review |

Foldables are supported from version 1. On a Galaxy Z Fold, the inner screen shows the list on the left and the receipt detail on the right.

## Capture and extraction

Every receipt, from camera, file or email, runs through one pipeline. It turns an image or PDF into structured fields with a confidence score on each one, all on the phone by default.

### Step 1. Capture

- **Camera.** Use the Google ML Kit Document Scanner API. It gives edge detection, auto capture, perspective fix, shadow removal and cropping for free, with a polished system UI. Wrap it so the user can stay in batch mode.
- **Custom camera fallback.** For devices without Google Play services, build a CameraX screen with OpenCV edge detection. This also keeps the app F-Droid friendly.
- **Long receipts.** Stitch mode captures in pages and joins them top to bottom into one tall image.
- **Files.** Accept JPG, PNG, HEIC, WebP and PDF. Render each PDF page to an image for OCR but keep the original PDF as the source of truth.

### Step 2. Clean up

- Convert to grayscale for OCR, keep a color copy for display.
- Save the display image as WebP at about 1600 px on the long side (roughly 150 to 300 KB).
- Make a thumbnail (about 320 px) for fast lists.
- Hash the image (perceptual hash) for duplicate checks later.

### Step 3. Read the text (OCR)

- Use ML Kit Text Recognition v2 on device. It is fast, free and works offline.
- Keep the full raw text and the position of every line. Raw text powers search. Positions let the UI highlight where a value came from when the user taps a field.

### Step 4. Pull out the fields

Three layers, tried in order. The first one that returns good results wins.

1. **On device AI (default).** Send the OCR text to Gemini Nano through the ML Kit GenAI Prompt API on phones that support it. Ask for strict JSON with the fields below. No network, no key, no cost. Android only allows it while the app is on screen, which is always true while someone is scanning.
2. **Rules parser (always on).** Regex and heuristics for dates, totals, tax lines, currency symbols and card last four. Runs on every device and acts as a cross check on the AI.
3. **Cloud AI (optional, off by default).** Users can paste their own API key in Settings (Gemini, Claude or OpenAI) for hard receipts like faded thermal paper or handwriting. The key is stored in the Android Keystore and never leaves the phone except in calls to that provider.

| Field | Notes |
| --- | --- |
| Merchant name | Normalized ("SQ \*KONA COFFEE" becomes "Kona Coffee") with a local merchant dictionary |
| Merchant address and phone | Optional |
| Date and time | Handle US and international formats; flag dates in the future |
| Subtotal, tax, tip, total | Stored as integer cents, never floating point |
| Currency | From symbol, code, or the merchant's country |
| Payment method | Card brand and last four only. Never store a full card number |
| Line items | Name, quantity, price. Optional, used for split receipts |
| Receipt or order number | Helps duplicate detection |
| Suggested category | From merchant history first, then AI guess |

### Step 5. Check the math and score confidence

- If subtotal plus tax plus tip does not equal total (within 1 cent), flag the total.
- Each field gets a confidence of high, medium or low. Low fields glow in the review card.
- If the AI and the rules parser disagree on the total, show both and let the user pick.
- Every time the user corrects a field, save the correction. Use it to improve merchant matching and category suggestions for that user.

### Step 6. Duplicate check

A receipt is a likely duplicate when the merchant, total and date match within one day, or the image hash is very close. The user sees "Looks like a duplicate of this one" with both side by side, and picks keep both, merge, or delete.

## Email: receipts from any provider

Receipt Beat finds receipts in anyone's email, whatever provider they use, and does all the reading on the phone. Version 1 never touches the Gmail API, so it needs no Google verification or paid security assessment, and it works the same for Gmail, Outlook, Yahoo, iCloud, Proton and the rest.

### Three ways in

| Way | Works with | Best for | Approval needed |
| --- | --- | --- | --- |
| Import an export file | Every provider | Catching up on past months or years in one go | None |
| Connect the account (live) | Gmail, Yahoo, AOL, iCloud, Fastmail and most others with an app password. Outlook.com and Microsoft 365 with Microsoft sign in | New receipts found on a schedule, hands free | None from Google. Outlook needs a free Microsoft app registration |
| Share one email | Every provider | A single receipt that just arrived | None |

Why live connect works without Google approval: Google still lets third party apps connect to Gmail over IMAP, and tells apps that don't use Google sign in to use an app password ([Google](https://workspaceupdates.googleblog.com/2023/09/winding-down-google-sync-and-less-secure-apps-support.html)). Google requires 2-Step Verification before a person can create one. Outlook.com is different. Microsoft turned off passwords for IMAP, so Outlook accounts connect with Microsoft sign in (OAuth) instead ([Microsoft](https://learn.microsoft.com/en-us/answers/questions/5649919/cannot-sign-in-programmatically-with-app-password)). Receipt Beat uses the same IMAP code for both, passing a Microsoft sign in token for Outlook.

### How to export your email

The app shows these steps for each provider, with screenshots. People only need the folders that hold receipts (Inbox, Purchases, Receipts) and the dates they care about, which keeps the file small.

| Provider | How to get your mail | File you get |
| --- | --- | --- |
| Gmail | Go to takeout.google.com, tap Deselect all, tick Mail, choose only the labels you want (for example, Inbox or Purchases), then Create export. Google emails a download link when it is ready. Or connect the account with an app password | .mbox inside a .zip |
| Outlook.com, Hotmail, Live | Easiest is to connect the account with Microsoft sign in. To export, Microsoft says you need desktop Outlook, then export the mailbox to a .pst file on Windows or an .olm file on Mac ([Microsoft](https://support.microsoft.com/en-US/Outlook/export-your-outlook-com-mailbox)) | .pst or .olm |
| Microsoft 365 work or school | Classic Outlook on Windows: File, Open and Export, Import/Export, Export to a file, Outlook Data File (.pst). Many workplaces block exports, so check with IT first | .pst |
| Yahoo and AOL | Yahoo has no export button. Connect the account with a Yahoo app password, which is how Yahoo itself says to download mail into other apps ([Yahoo](https://help.yahoo.com/kb/SLN28681.html)). AOL works the same way | Live connect |
| iCloud Mail | On a Mac, open Mail, pick the mailbox, then Mailbox, Export Mailbox, and zip the folder it makes. Or connect the account with an Apple app specific password | .mbox |
| Proton Mail | Run Proton's free Export Tool on a computer. It saves every email as an EML file plus JSON details ([Proton](https://proton.me/support/export-import-emails)). Zip the folder | .eml files in a .zip |
| Fastmail and most others | Connect with an app password | Live connect |
| Anything else | Install free Thunderbird on a computer, add the account, let it download, then save folders as mbox with the ImportExportTools NG add-on | .mbox |

To get a file onto the phone, download it on the phone directly, or put it in Google Drive or on a USB drive. Receipt Beat opens it through the normal Android file picker, zipped or not.

### How a mailbox gets processed (all on the phone)

1. **Open as a stream.** The file is read a little at a time, so even a huge Takeout file never has to fit in memory. Formats are .mbox, .eml, .zip of either, .pst and .olm.
2. **Skim the headers first.** For every message the app reads only the date, sender and subject. Anything outside the chosen dates, from a muted sender, or already seen (matched by a hash of its Message-ID) is skipped right away. Most of a mailbox drops out here, fast.
3. **Score with rules.** What is left gets a receipt score from 0 to 100% using the signals below. Clear yes and clear no are decided here with no AI at all.
4. **Ask the local AI about the borderline ones.** Emails in the middle band go to the on device model, which answers in strict JSON whether this is a real receipt or invoice, and if so, the merchant, date, total, tax and currency.
5. **Pull out the fields.** In order of trust, schema.org markup in the email, then a PDF attachment (its text first, OCR if it is a scan), then the email body turned into clean text. The math check from the capture pipeline runs here too.
6. **Make the receipt image.** If there is no PDF, the email is turned into a clean PDF snapshot on the phone, with remote images blocked so tracking pixels in emails never load.
7. **Send to Review.** Finds land in the Review stack grouped by sender. Nothing becomes a receipt until the user keeps it.

A progress card shows messages scanned and receipts found. Big imports run as a foreground job with a notification, so Android does not stop them, and they pick up where they left off if interrupted.

| Signal | Weight |
| --- | --- |
| Schema.org Order or Invoice markup in the email | Very strong. Many stores embed it |
| Sender is a known merchant or payment service | Strong. Ship an editable starter list (Amazon, Uber, airlines, Square, Stripe, PayPal, Apple, Google) |
| A money amount near words like total, paid, charged | Strong |
| PDF attachment named invoice or receipt | Strong |
| Marketing words (sale, % off, heavy unsubscribe links) with no amount | Negative |
| Sender the user muted before | Excluded |

### The local model

- **First choice is Gemini Nano.** It is built into many recent Android phones and shared by all apps through Android's AICore service, so Receipt Beat downloads nothing ([Google](https://developers.google.com/ml-kit/genai)). Google even names reading text from a receipt and sorting the items as a key use for its Prompt API ([Android Developers](https://developer.android.com/blog/posts/ml-kit-s-prompt-api-unlock-custom-on-device-gemini-nano-experiences)). It works best on the newest phones.
- **One important limit.** Google only lets these AI calls run while the app is the top app on screen ([Google](https://developers.google.com/ml-kit/genai)). So scheduled overnight scans use the rules only and park borderline emails. The AI finishes them the moment the user opens the app, and during a file import while the progress screen is open.
- **Phones without Gemini Nano.** An optional one time download of a small open model from Google's Gemma family, run on the phone with Google's on device LLM runtime. It is a few hundred megabytes, so the app asks first and downloads on Wi-Fi only (version 1.5).
- **Every phone.** The rules scorer and parser always run, so the app works with no AI at all. It simply sends more emails to Review for the user to decide.
- **Optional cloud AI.** Off by default, using the person's own key, text only, as in the capture section.
- **What the model never does.** It never sees emails that failed the header skim, and nothing it reads ever leaves the phone.

### Connected accounts: remembered searches and scheduled scans

- Each finished scan saves the date range it covered for that account and folder, so the app never searches the same stretch of mail twice. New scans only fill the gaps.
- **Extend back** adds older months (for example, all of 2024) and searches only the missing ones.
- The server does the first filter. IMAP search narrows by date and words, and for Gmail accounts the app uses Gmail's own search syntax over IMAP (X-GM-RAW), so only likely receipts are downloaded.
- Between scans, the app asks only for messages newer than the last one it saw in each folder.
- A scan that stops partway (no signal, phone dies) saves its place and picks up there next time.

| Auto scan setting | Options |
| --- | --- |
| How often | Off, every 12 hours, daily, weekly |
| Time of day | Pick an hour (for example, 2 AM). Android runs it close to that time, not to the exact minute |
| Conditions | Wi-Fi only, charging only (both on by default) |
| What it does | Find only, Find and extract, or Auto keep trusted senders |

**Find and extract** downloads and reads each find so cards are ready for one tap approval (the AI part finishes when the app opens). **Auto keep trusted senders** sends receipts from senders marked Always keep straight to the Inbox with their default category and payer. A **Scan log** shows each run, with emails checked, found, auto kept and any error. New finds show as a badge on the Review tab, with one quiet weekly summary notification at most.

### Review stack

- Results are grouped by sender with count and total (for example, "Uber, 23 receipts, $412.80").
- Each card shows merchant, date, amount, subject line and the score.
- Swipe right to keep, left to skip. Select many and **Keep all with category**.
- **Mute sender** and **Always keep from sender** rules make the next scan smarter.
- Paper and email copies of the same purchase get caught as duplicates.

### Later: Gmail API sync (version 2, only if people ask)

One tap Gmail sign in would remove the app password step, but every Gmail scope that can read mail is restricted. That means Google verification plus an outside security assessment (CASA) every year ([Unified.to](https://docs.unified.to/skills/gmail-oauth-app/SKILL.md)). Worth doing only once the app has a following.

## Categories, statuses, reimbursement and tax tracking

Every receipt answers two questions. **What was it for?** (its category) and **Where is it headed?** (its track and status). The app keeps those separate so one receipt can be a "Meals" expense that is reimbursable by one payer, then archived once paid.

&#91;embedded content: receipt lifecycle · 3 tracks, 3 end states\]

A receipt only leaves the app's to do list when it lands in one of the three green end states.

### Tracks and statuses

| Track | Statuses in order | Ends when |
| --- | --- | --- |
| Reimbursable | Inbox, Ready, Submitted, Reimbursed (or Partly reimbursed, or Rejected) | Money is received |
| Tax deductible | Inbox, Ready, Claimed | The tax year is exported and locked |
| Personal record | Inbox, Ready, Archived | User files it (warranty, returns, budget) |

A rejected receipt goes back to Ready with the reason noted. The user can resubmit it, move it to the tax track (if it is a business cost they paid themselves), or move it to personal.

### Categories

Users see simple, friendly categories. Behind the scenes each one maps to a tax line.

| Category | Default tax mapping (US) |
| --- | --- |
| Meals | Schedule C, meals (often 50% deductible) |
| Travel and airfare | Schedule C, travel |
| Lodging | Schedule C, travel |
| Car, fuel, parking, tolls | Schedule C, car and truck |
| Office supplies | Schedule C, supplies |
| Software and subscriptions | Schedule C, other expenses |
| Phone and internet | Schedule C, utilities |
| Equipment | Schedule C, depreciation or supplies |
| Professional services | Schedule C, legal and professional |
| Education and training | Schedule C, other expenses |
| Home office | Form 8829 |
| Medical | Schedule A medical, or HSA/FSA |
| Charitable gift | Schedule A gifts to charity |
| Gifts to clients | Schedule C, other (limits apply) |
| Other | Unmapped, user picks |

Users can rename, recolor, add, hide and merge categories. Tax mappings ship as editable presets (US sole proprietor first). Other countries come as community contributed preset files, which suits an open source project. The app shows a short note that it is not tax advice.

### Payers and projects

- A **payer** is whoever pays the user back, like an employer, a client or a nonprofit board. Each payer has a default category set, report style, submit deadline (for example, within 60 days) and a reminder rule.
- A **project or trip** is a free tag (for example, "Kauaʻi site visit, Oct 2026") for grouping receipts in a report.

### Smart suggestions

1. Same merchant as before? Use the category and payer the user picked last time.
2. Known merchant type (airline, hotel, gas station)? Use the matching category.
3. Otherwise ask the on device AI, and show the guess as a chip the user can accept with one tap.

### Money dashboard

The home screen leads with three numbers the user cares about.

- **Owed to you.** Total submitted and not yet paid, by payer.
- **Ready to submit.** Reimbursable receipts not yet sent in.
- **Deductible this year.** Running tax total for the current year.

### Reminders that save money

| Trigger | Nudge |
| --- | --- |
| Receipt in Inbox over 3 days | "4 receipts need a quick look" |
| Reimbursable and not submitted, near the payer deadline | "Submit your Board travel receipts by Friday" |
| Submitted over 30 days and unpaid | "Resource Innovations still owes you $286.40" |
| January each year | "Your 2026 tax export is ready" |

All reminder timings are editable, and all can be turned off.

### Keeping records

Keep receipts at least 7 years by default (tax records are generally kept 3 to 7 years). Claimed years lock so totals cannot change by accident. Unlocking needs a confirm step.

## Data model

All data lives in a local Room database (encrypted with SQLCipher) plus an app private folder for images and PDFs. Money is always stored as whole cents in a Long. IDs are UUID strings so records can sync and merge later without clashes.

```kotlin
// Core record
Receipt(
  id: String, createdAt: Instant, updatedAt: Instant,
  source: Source,               // CAMERA, IMPORT, SHARE, EMAIL_FILE, EMAIL_LIVE
  merchantName: String?, merchantRaw: String?, merchantAddress: String?,
  purchaseDate: LocalDate?, purchaseTime: LocalTime?,
  subtotalCents: Long?, taxCents: Long?, tipCents: Long?, totalCents: Long?,
  currency: String,             // ISO 4217, e.g. "USD"
  homeCurrencyTotalCents: Long?, fxRate: Double?,
  paymentBrand: String?, paymentLast4: String?,
  receiptNumber: String?,
  categoryId: String?, payerId: String?, projectId: String?,
  track: Track,                 // REIMBURSABLE, TAX, PERSONAL
  status: Status,               // INBOX, READY, SUBMITTED, REIMBURSED,
                                // PARTLY_REIMBURSED, REJECTED, CLAIMED, ARCHIVED
  reimbursedCents: Long?, taxYear: Int?, locked: Boolean,
  notes: String?, ocrText: String?, imageHash: String?,
  duplicateOfId: String?, deletedAt: Instant?   // soft delete, 30 day trash
)

ReceiptFile(id, receiptId, kind /* ORIGINAL, DISPLAY, THUMB, PDF, EMAIL_SNAPSHOT */, path, mime, bytes, page)
FieldConfidence(receiptId, field, value, confidence /* HIGH, MEDIUM, LOW */, method /* NANO, RULES, CLOUD, USER */, box /* x,y,w,h on image */)
LineItem(id, receiptId, name, qty, unitCents, totalCents, categoryId?, payerId?)   // splits use these
Split(id, receiptId, amountCents, categoryId, payerId?, track)

Category(id, name, icon, color, taxLineId?, sortOrder, hidden)
TaxLine(id, presetId, code, label, deductiblePercent)   // e.g. meals at 50%
Payer(id, name, defaultTrack, submitDeadlineDays?, reminderDays, reportStyle, email?)
Project(id, name, startDate?, endDate?, payerId?)

Report(id, payerId, title, createdAt, submittedAt?, paidAt?, totalCents, pdfPath?, csvPath?)
ReportItem(reportId, receiptId)

MailAccount(id, auth /* APP_PASSWORD, MICROSOFT */, email, imapHost, lastUidByFolder?, lastScanAt?, scanFrom: LocalDate)   // secret lives in Android Keystore, never in the database
ImportJob(id, fileName, format /* MBOX, EML, EML_ZIP, PST, OLM */, bytesTotal, bytesRead, messagesSeen, candidates, status, resumeOffset?, startedAt, finishedAt?)
EmailSource(receiptId, accountId?, importJobId?, messageIdHash, threadId?, sender, subject, receivedAt, score)
EmailCandidate(id, accountId?, importJobId?, messageIdHash, needsAi: Boolean, sender, subject, snippet, receivedAt, score, amountCents?, state /* NEW, KEPT, SKIPPED */)
SenderRule(id, accountId?, senderOrDomain, rule /* MUTE, ALWAYS_KEEP */, defaultCategoryId?, defaultPayerId?)

ScanRange(id, accountId, folder, fromDate: LocalDate, toDate: LocalDate, queryVersion: Int, completedAt: Instant)   // dates already searched
ScanRun(id, accountId, startedAt, finishedAt?, trigger /* MANUAL, SCHEDULED */, checked, found, autoKept, error?, resumeToken?)
AutoScanSettings(accountId, frequency /* OFF, EVERY_12H, DAILY, WEEKLY */, preferredHour, wifiOnly, chargingOnly, mode /* FIND_ONLY, FIND_AND_EXTRACT, AUTO_KEEP_TRUSTED */)

MerchantMemory(merchantKey, lastCategoryId, lastPayerId, timesUsed)   // powers suggestions
Correction(id, receiptId, field, oldValue, newValue, at)
MileageTrip(id, date, startPlace, endPlace, miles, rateCents, purpose, payerId?, track, status)   // version 1.5
```

Search uses a Room FTS4 table over merchant, notes, OCR text and email subject. Schema changes ship with Room auto migrations and a test for each one.

## Architecture and tech stack

Receipt Beat is a modern native Android app in Kotlin and Jetpack Compose, local first, with no backend. That keeps it private, cheap to run (free), and easy for anyone to fork and build.

&#91;embedded content: app architecture · on device layers and 3 network services\]

Screens only ever talk to repositories. Email accounts and Drive are reached only if the user connects them, by background jobs, and the dashed AI provider is off unless the user adds a key.

### Stack

| Layer | Choice |
| --- | --- |
| Language | Kotlin 2.x, Java 17 toolchain |
| UI | Jetpack Compose, Material 3 Expressive, Navigation Compose, adaptive layouts for foldables |
| Pattern | MVVM with one way data flow (screen state flows down, user events flow up) |
| Dependency injection | Hilt |
| Async | Kotlin Coroutines and Flow |
| Database | Room with SQLCipher encryption, FTS4 for search |
| Settings | Jetpack DataStore |
| Background work | WorkManager (OCR queue, email imports and scans, backups, reminders) |
| Camera and scan | ML Kit Document Scanner, CameraX fallback |
| OCR | ML Kit Text Recognition v2 (on device) |
| On device AI | Gemini Nano through ML Kit GenAI Prompt API where supported, plus an optional downloadable Gemma model for other phones (version 1.5) |
| Optional cloud AI | User supplied key for Gemini, Claude or OpenAI, called with Ktor |
| Email | Apache James Mime4j to read mbox and EML, java-libpst for PST, a zip and XML reader for OLM, Angus Mail (Jakarta Mail) for IMAP live connect, jsoup to turn HTML email into text |
| Sign in | MSAL for Outlook live connect. App passwords for other providers, kept in the Android Keystore. Google sign in only for Drive backup |
| Images | Coil 3 |
| PDF | Android PdfRenderer to read, PdfDocument to build reports |
| Widget | Jetpack Glance |
| Security | BiometricPrompt, Android Keystore |
| Backup | Google Drive app data folder (a private hidden folder only Receipt Beat can see) |
| Min and target SDK | minSdk 29 (Android 10), targetSdk 36 |

### Gradle modules

```
app/                  // entry point, navigation, Hilt setup
core/model/           // plain Kotlin data classes and enums
core/database/        // Room, DAOs, migrations, SQLCipher
core/data/            // repositories that screens talk to
core/designsystem/    // theme, colors, type, shared components
core/common/          // money, dates, currency, utils
extraction/           // OCR, Nano prompt, rules parser, scoring, dedupe
mail/                 // mbox, EML, PST, OLM import, IMAP and Outlook live connect, email to PDF
feature/home/         // dashboard
feature/capture/      // scanner, batch, import, share target
feature/inbox/        // review new receipts
feature/receipt/      // detail and edit
feature/email/        // import wizard, provider guides, review stack
feature/reimburse/    // payers, reports, paid tracking
feature/taxes/        // tax year view, export, lock
feature/settings/     // categories, rules, AI, backup, security
widget/               // Glance widget and Quick Settings tile
```

Use a Gradle version catalog (`libs.versions.toml`) and convention plugins so every module is set up the same way.

### Build flavors

- **play** is the main build with Google Play services (Document Scanner, Gemini Nano, Drive backup).
- **foss** (version 2) swaps in CameraX plus OpenCV and Tesseract, with no Google services, so the app can be listed on F-Droid. Email file import and IMAP live connect still work in this flavor.

### Rules Claude Code should follow

- Screens never touch Room or the network directly. They go through repositories in `core/data`.
- All heavy work (OCR, AI, email fetch) runs in WorkManager or on `Dispatchers.Default` and `Dispatchers.IO`, never the main thread.
- Every feature module has its own ViewModel tests and at least one Compose UI test.
- No secrets in source. Keys come from `local.properties` or the user at runtime.

## Design system and world class UI

The UI should feel calm, fast and confident, like a good bank app crossed with a great camera app. Money is the hero on every screen, and the next action is always one thumb tap away.

### Design principles

1. **Capture first.** The capture button is always within thumb reach, on every main screen.
2. **Show the money.** Amounts are big, bold and easy to scan. Everything else is quieter.
3. **Never block.** Reading happens in the background. The user can keep going while cards fill in.
4. **Undo over confirm.** Delete, archive, mark paid all act right away with an Undo snackbar. Pop up dialogs only for things that cannot be undone, like unlocking a tax year.
5. **One look tells the state.** Every receipt shows its status by color and icon, not just words.

### Visual language

| Token | Choice |
| --- | --- |
| Base | Material 3 Expressive, edge to edge, predictive back |
| Brand color | Deep ocean teal as primary, warm coral as accent for the capture button |
| Dynamic color | Off by default to keep the brand look, toggle in Settings |
| Themes | Light, dark and true black (for OLED), following the system by default |
| Type | Inter for UI text. Amounts use tabular numbers so columns line up |
| Shapes | Large rounded cards (24 dp corners), pill shaped status chips |
| Icons | Material Symbols Rounded, one weight across the app |
| Spacing | 4 dp grid, 16 dp screen margins, 48 dp minimum touch targets |

### Status colors

| Status | Color | Icon |
| --- | --- | --- |
| Inbox | Neutral gray | Tray |
| Ready | Blue | Check circle outline |
| Submitted | Amber | Paper plane |
| Reimbursed | Green | Check circle filled |
| Rejected | Red | Error |
| Claimed | Purple | Lock |
| Archived | Muted gray | Archive box |

### Motion and feel

- A shared element animation carries the photo from the camera into its card in the Inbox, so the user sees where it went.
- While a receipt is being read, its card shows a soft shimmer and the words "Reading receipt" with fields filling in one by one.
- Spring based motion for sheets and lists (Material 3 Expressive motion scheme).
- Light haptic tap on capture, on swipe actions, and when a receipt is marked reimbursed.
- A small, tasteful celebration when the Owed to you number hits zero.

### Gestures

- Swipe right on a card to move it forward (Ready, Submitted, Reimbursed).
- Swipe left to archive or skip.
- Long press to multi select, then a bottom bar shows bulk actions.
- Pull down on the home screen to check connected email accounts now.

### Accessibility (built in, not added later)

- Full TalkBack labels, including amounts read as money ("forty two dollars and fifty cents").
- Layouts hold up at 200% font size.
- WCAG AA contrast in every theme. Status never relies on color alone.
- Every swipe action also lives in a menu for people who can't swipe.

### Adaptive layouts

- **Phone:** bottom navigation with five tabs.
- **Foldable inner screen and tablets:** navigation rail on the left, list and detail side by side.
- **Folded half open (tabletop):** camera preview on top, capture controls on the bottom half.

### Prototype first

Before Claude Code writes Compose screens, build a clickable HTML prototype of the five main screens with fake data. Tune the look on the phone, then hand it to Claude Code as the visual reference in `design/prototype.html`.

## Screen by screen spec

The bottom bar has five spots. **Home**, **Receipts**, a raised **Capture** button in the center, **Review** (found emails), and **Money** (reimbursement and taxes). Settings opens from the profile icon on Home.

### Onboarding (first launch only)

1. Welcome screen with one line on what Receipt Beat does and a short looping animation of a receipt turning into a card.
2. "How will you use Receipt Beat?" Pick any of work reimbursement, business taxes, household records. This sets default tracks and categories.
3. Add a first payer (optional, skippable).
4. Offer **Add email receipts** with a plain words privacy card. Skippable.
5. Turn on app lock (optional).

No account to create. Camera and notification permissions are asked only when first needed.

### Home

- Top: greeting and profile icon (opens Settings).
- Three money tiles: Owed to you, Ready to submit, Deductible this year. Tap any tile to jump to the filtered list.
- "Needs your attention" card: Inbox count, Review count, any overdue reports.
- Recent receipts (last 5) as compact cards.
- Month spend by category as a simple bar (version 1.5).

### Capture

- Opens the scanner straight away. Bottom row shows mode chips (Single, Batch, Long) plus Import and Flash.
- Batch mode shows a thumbnail tray of scans with a count. Tap Done to send them all to the Inbox.
- After a single scan, a small sheet slides up with the card filling in. Quick buttons let the user set track and payer before leaving.

### Receipts

- Search bar on top. Searches merchant, amount, notes and full receipt text.
- Filter chips: status, track, category, payer, date range, source (camera or email).
- Sort by date, amount or merchant.
- Group headers by month with month totals.
- Each card: thumbnail, merchant, date, category icon, big amount, status chip. Low confidence or duplicate badges when needed.

### Receipt detail

- Top half: zoomable receipt image (or email snapshot). Swipe between pages.
- Bottom sheet: every field as an editable row. Low confidence rows are highlighted. Tapping a field highlights the matching spot on the image.
- Track and status shown as a stepper the user can tap to move forward or back.
- Actions: split, change payer, add note, share, view the original email, delete.
- History list at the bottom (created, edited, submitted on report, paid).

### Review (found emails)

- Top: Add email receipts button, account and import switcher, last scan time, Scan now button, and a strip showing which months are already searched with an Extend back button.
- Grouped by sender, each group with count and total. Expand to see each email.
- Card stack mode for one by one swiping, or list mode for bulk select.
- Per sender menu: Keep all, Mute sender, Always keep, Set default category and payer.

### Money

A segmented switch at the top flips between Reimburse and Taxes.

**Reimburse**

- One section per payer showing Ready, Submitted and Paid totals.
- **Create report** picks all Ready receipts for that payer (user can untick any). Preview the PDF, then share or email.
- Report list with status. **Mark report paid** marks every receipt on it reimbursed. Partial amounts allowed.

**Taxes**

- Year picker. Totals by tax line, with the deductible amount after limits (like meals at 50%).
- Unclaimed and missing info warnings (for example, receipts with no category).
- **Export for my accountant** and **Lock year**.

### Settings

- Categories and tax presets
- Payers and projects
- Mail accounts, auto scan schedule, scan log and sender rules
- AI (on device status, optional cloud key, extraction test screen)
- Backup and restore (Google Drive, or export everything as a ZIP)
- Security (app lock, hide amounts in recents screen)
- Reminders
- Appearance (theme, dynamic color, home currency)
- About (version, open source licenses, link to GitHub, Kealoha Labs)

### Widget and shortcuts

- Home screen widget in two sizes. Small is a capture button. Medium adds the Owed to you number and Inbox count.
- Quick Settings tile that opens the scanner.
- Long press app icon shortcuts: Scan, Import, Review emails.

## Exports, reports and expense submission

Every export is built on the phone and handed to the Android share sheet, so it can go to email, Drive, Slack or a company expense tool.

| Export | What is inside | Used for |
| --- | --- | --- |
| Reimbursement report (PDF) | Cover page with payer, date range, total and the user's name. Summary table. Then each receipt image, one per page, with its fields | Sending to an employer, client or board treasurer |
| Report data (CSV) | One row per receipt with date, merchant, category, amount, tax, currency, payer, project, notes, file name | Pasting into a company expense system |
| Tax year package (ZIP) | PDF summary by tax line, CSV of every receipt, and every original image named like `2026-03-14_Kona-Coffee_12.50.jpg` | Handing to an accountant |
| Full backup (ZIP) | The database as JSON plus all files | Moving phones or leaving the app. Users always own their data |

### Report details that matter

- Report numbers (TR-2026-014) so the payer and user can talk about the same report.
- A signature line and a "submitted on" date.
- Foreign currency receipts show both the original and the home currency amount with the rate used.
- Split receipts show only the share charged to that payer.
- An optional company report template per payer (logo, required fields like cost center or GL code). Templates are simple JSON files, easy for the community to share.

### Coming later

Direct export to QuickBooks, Xero and Wave (version 2), and a generic email to expense tool option for systems that take receipts by email.

## Security, privacy and Google verification

Receipts hold card digits, home addresses and medical spending. Receipt Beat treats them like bank data, and being open source means anyone can check that it does.

### On the phone

- Room database encrypted with SQLCipher. The key lives in the Android Keystore.
- Images and PDFs stored in app private storage, encrypted with Google Tink using a Keystore backed key (the older Jetpack Security crypto library is deprecated).
- Optional app lock with fingerprint, face or PIN, with a lock timeout setting.
- `FLAG_SECURE` option hides the app in the recent apps screen and blocks screenshots.
- Card numbers are never stored in full. The rules parser masks anything that looks like a full card number in the saved OCR text.
- Android Auto Backup is turned off for the database so data never ends up in an unencrypted system backup.

### Network

- The only servers Receipt Beat ever talks to are the person's own email provider (only if they connect an account), Google Drive (only if they turn on backup) and their chosen AI provider (only if they add a key). File imports never touch the network.
- Zero tracking, in every build. No analytics, no ads, no crash reporting SDK, no Firebase, no telemetry, no usage pings. Bugs are reported by people on GitHub issues. Settings has a Copy debug log button that copies a local log the user can read before choosing to share it.
- Live connect is read only. Only emails that pass the receipt score get fully downloaded. Emails are shown and saved with remote images blocked, so tracking pixels never fire. App passwords and sign in tokens live in the Android Keystore and can be removed in one tap.
- Cloud AI calls send only the OCR text by default, not the image, unless the user allows images.

### Google verification checklist (only if Gmail API sync is added in version 2)

- [ ] Public privacy policy page (host on olagon.github.io) that explains exactly what Gmail data is used for
- [ ] Home page and support email on the OAuth consent screen
- [ ] Demo video showing the Gmail flow, as Google requires
- [ ] Show the app follows Google's Limited Use rules (no ads, no selling data, no humans reading mail)
- [ ] Pass the yearly CASA security assessment for the restricted Gmail scope
- [ ] Play Console Data safety form declaring that no data is collected or shared

### Open source hygiene

- License Apache 2.0 (includes a patent grant). MIT is a fine simpler choice if preferred.
- `.gitignore` covers `local.properties`, keystores, `google-services.json` and any `.env` file.
- GitHub secret scanning and Dependabot turned on.
- A `SECURITY.md` explains how to report a security issue privately.

## Testing and quality

The most important test asset is a set of real receipts with the right answers written down. Extraction quality is measured against it on every pull request.

### Receipt test set

- Collect 200 receipts to start (paper scans, PDFs, and saved emails from several providers as `.eml` files). Strip personal details before committing.
- Store each one in `testdata/receipts/` with a matching JSON file of correct values.
- Mix in hard cases like faded thermal paper, crumpled, long grocery receipts, foreign currency, handwritten tips, Hawaiʻi general excise tax lines, and restaurant receipts with tip added later.
- An `extraction-eval` Gradle task runs the rules parser on the set and prints accuracy per field. Gemini Nano runs are checked on a real device.

### Targets

| Measure | Target |
| --- | --- |
| Total correct on clear receipts | 95% or better |
| Date correct | 95% or better |
| Merchant correct | 90% or better |
| Email scan finds real receipts (recall) | 90% or better |
| Email scan avoids non receipts (precision) | 85% or better |
| Time from capture to filled card | Under 3 seconds on a mid range phone |
| Cold start | Under 1 second to the Home screen |
| Crashes in the 2 week tester run | Zero, from tester reports (the app has no crash tracking) |

### Test types

- Unit tests for money math, currency, date parsing, rules parser, receipt scoring and duplicate checks.
- Room DAO and migration tests.
- ViewModel tests with fake repositories.
- Compose UI tests and screenshot tests (Roborazzi) for every main screen in light, dark, big font and foldable sizes.
- Email import tests against sample mbox, EML, PST and OLM files, plus IMAP tests against a local test mail server (GreenMail), never live mail. A 5 GB synthetic mbox checks that imports never run out of memory.
- Baseline Profiles and Macrobenchmark to keep startup and scrolling smooth.

### CI on GitHub Actions

Every pull request runs lint (ktlint and detekt), unit tests, screenshot tests and the extraction eval, then builds a debug APK as a downloadable artifact. Tagged releases build a signed release bundle using secrets stored in GitHub, never in the repo.

## Build phases and milestones

The build runs in six phases. Each one ends with a working app you can install on your phone, and Claude Code should not start the next phase until the gate for the current one passes.

&#91;embedded content: build roadmap · 6 phases, 6 gates\]

Email comes after tracking on purpose. The review stack only pays off once categories, payers and statuses exist to sort receipts into.

### Phase 0. Repo and skeleton

- [ ] Create the GitHub repo with README, LICENSE, CLAUDE.md, SECURITY.md, CONTRIBUTING.md, .gitignore
- [ ] Set up Gradle modules, version catalog and convention plugins
- [ ] Theme, colors, type and shared components in `core/designsystem`
- [ ] Bottom bar with five empty tabs and the Capture button
- [ ] Room database with SQLCipher and the core tables
- [ ] GitHub Actions running lint, tests and a debug build

* **Gate:** CI is green and the app opens on a real phone

### Phase 1. Capture and extraction

- [ ] Document Scanner with single, batch and long modes
- [ ] Import from Files and Photos, and the share target
- [ ] Image cleanup, thumbnails, hashing
- [ ] OCR, rules parser, Gemini Nano extraction, confidence scores
- [ ] Inbox review card, Receipts list, Receipt detail with edit
- [ ] Receipt test set and the extraction eval task

* **Gate:** totals at 90% or better on the test set, and capture to card under 3 seconds

### Phase 2. Tracking

- [ ] Categories with tax mapping presets
- [ ] Payers and projects
- [ ] Tracks, statuses and the stepper in Receipt detail
- [ ] Home dashboard with the three money tiles
- [ ] Search, filters, duplicate detection
- [ ] Merchant memory for smart suggestions
- [ ] Reminders

* **Gate:** you can run a full month of your own receipts through it without a spreadsheet

### Phase 3. Email for everyone

- [ ] Add email receipts wizard with a step by step guide for each provider
- [ ] Streaming import of .mbox, .eml, .zip, .pst and .olm with progress, resume and Message-ID dedupe
- [ ] Header skim, rules scorer and field pull from schema markup, PDF attachments and body text
- [ ] Gemini Nano pass for borderline emails while the app is on screen, with a queue for later
- [ ] Email to PDF snapshot with remote images blocked
- [ ] Review stack with grouping, swipe, bulk keep and sender rules
- [ ] IMAP live connect with app passwords, and Outlook through Microsoft sign in
- [ ] Remembered scan ranges, scheduled auto scans and scan log

* **Gate:** a 12 month import from your own Gmail finds 90% of real receipts with few false hits, and the same works for one friend on Outlook or Yahoo

### Phase 4. Money, exports and backup

- [ ] Reimburse view, report builder, PDF and CSV, mark paid (full and partial)
- [ ] Taxes view, tax year ZIP, year lock
- [ ] Google Drive app folder backup and restore
- [ ] Full data export

* **Gate:** you submit one real reimbursement report built in Receipt Beat

### Phase 5. Polish and release

- [ ] Motion, haptics, shared element capture animation
- [ ] Accessibility pass (TalkBack, 200% font, contrast)
- [ ] Foldable and tablet layouts
- [ ] Glance widget, Quick Settings tile, app shortcuts
- [ ] Baseline Profiles and performance checks
- [ ] Privacy policy page, Play Store listing, screenshots
- [ ] Play internal testing track, then the public Play Store listing

* **Gate:** five outside testers use it for two weeks with no crashes and no lost receipts

## Repo setup and Claude Code kickoff

When you sit down at your Mac, it is three steps. Make the repo, drop in two files (this spec as `docs/SPEC.md` and the `CLAUDE.md` below), then start Claude Code in the folder and paste the kickoff prompt.

### Before you start

- Android Studio (latest stable) with JDK 17
- GitHub CLI (`gh`) signed in as olagon
- Your phone with USB debugging on
- No Google Cloud setup is needed. For Outlook live connect only, a free Microsoft Entra app registration (package `org.kealohalabs.receiptbeat`, plus your debug signature hash from `./gradlew signingReport`)

### Step 1. Make the repo

```bash
cd ~/code
gh repo create olagon/receiptbeat --public --clone --license apache-2.0 --gitignore Android \
  --description "Open source Android receipt scanner and tracker. Scan, import email from any provider, track until reimbursed or claimed for taxes."
cd receiptbeat
mkdir -p docs design testdata/receipts
```

### Step 2. Add the two files

Export this doc as Markdown and save it as `docs/SPEC.md`. Save the block below as `CLAUDE.md` in the repo root. Then commit.

```bash
git add . && git commit -m "Add build spec and Claude Code guide" && git push
```

### CLAUDE.md

```markdown
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
```

### Step 3. Start Claude Code

```bash
claude
```

Then paste this kickoff prompt.

```
Read CLAUDE.md and docs/SPEC.md all the way through. Then:

1. Write docs/PROGRESS.md with every task from "Build phases and milestones" as an unchecked list.
2. Make a short plan for Phase 0 and show it to me before writing code.
3. After I say go, build Phase 0 on a branch named phase-0-skeleton. Commit as you go.
4. When the Phase 0 gate is met, install the debug build on my phone, push the branch,
   open a pull request, and tell me what to check.

Ask me if anything in the spec is unclear or seems wrong. Do not guess on email sign in,
signing keys, or package names.
```

### Each later session

Start Claude Code in the folder and say "Read CLAUDE.md and docs/PROGRESS.md, then continue with the next unchecked task." That keeps every session on track even weeks apart.
