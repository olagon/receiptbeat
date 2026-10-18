package org.kealohalabs.receiptbeat.core.model

enum class Source { CAMERA, IMPORT, SHARE, EMAIL_FILE, EMAIL_LIVE }

enum class Track { REIMBURSABLE, TAX, PERSONAL }

enum class Status { INBOX, READY, SUBMITTED, REIMBURSED, PARTLY_REIMBURSED, REJECTED, CLAIMED, ARCHIVED }

enum class FileKind { ORIGINAL, DISPLAY, THUMB, PDF, EMAIL_SNAPSHOT }

enum class Confidence { HIGH, MEDIUM, LOW }

enum class ExtractionMethod { NANO, RULES, CLOUD, USER }

enum class MailAuth { APP_PASSWORD, MICROSOFT }

enum class MailFormat { MBOX, EML, EML_ZIP, PST, OLM }

enum class JobStatus { RUNNING, PAUSED, DONE, FAILED }

enum class CandidateState { NEW, KEPT, SKIPPED }

enum class SenderRuleKind { MUTE, ALWAYS_KEEP }

enum class ScanTrigger { MANUAL, SCHEDULED }

enum class ScanFrequency { OFF, EVERY_12H, DAILY, WEEKLY }

enum class ScanMode { FIND_ONLY, FIND_AND_EXTRACT, AUTO_KEEP_TRUSTED }

/** Where a receipt is on its track. The end states take it off the to do list. */
val Status.isDone: Boolean
    get() = this == Status.REIMBURSED || this == Status.CLAIMED || this == Status.ARCHIVED

/** Statuses a receipt moves through, in order, for its track. Used by the detail stepper. */
fun Track.steps(): List<Status> = when (this) {
    Track.REIMBURSABLE -> listOf(Status.INBOX, Status.READY, Status.SUBMITTED, Status.REIMBURSED)
    Track.TAX -> listOf(Status.INBOX, Status.READY, Status.CLAIMED)
    Track.PERSONAL -> listOf(Status.INBOX, Status.READY, Status.ARCHIVED)
}
