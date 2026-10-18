package org.kealohalabs.receiptbeat.export

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.kealohalabs.receiptbeat.core.data.ReportRepository
import org.kealohalabs.receiptbeat.core.data.TaxYearSummary
import org.kealohalabs.receiptbeat.core.model.Money
import org.kealohalabs.receiptbeat.core.model.Receipt
import org.kealohalabs.receiptbeat.core.model.Report

private const val W = 612f // US Letter in points
private const val H = 792f
private const val M = 48f
private val TEAL = Color.rgb(0x0D, 0x4B, 0x54)
private val CORAL = Color.rgb(0xFE, 0x6B, 0x49)
private val PAPER = Color.rgb(0xFE, 0xF7, 0xEC)
private val INK = Color.rgb(0x11, 0x16, 0x18)
private val GREY = Color.rgb(0x5A, 0x63, 0x66)
val PdfDate: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

/** Small drawing helper over PdfDocument: pages, a brand header band, text and lines. */
class PdfWriter(private val regular: Typeface, private val bold: Typeface) {
    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private val canvas get() = page!!.canvas
    var y = M

    fun newPage() {
        page?.let(doc::finishPage)
        page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), doc.pages.size + 1).create())
        y = M
    }

    fun band(title: String, sub: String) {
        rect(0f, 0f, W, 92f, TEAL)
        rect(0f, 92f, W, 96f, CORAL)
        text(title, M, 48f, 22f, true, PAPER)
        text(sub, M, 72f, 11f, false, PAPER)
        y = 130f
    }

    fun text(
        s: String,
        x: Float,
        y: Float,
        size: Float = 10f,
        isBold: Boolean = false,
        color: Int = INK,
        right: Boolean = false,
        maxW: Float = 0f
    ) {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = if (isBold) bold else regular
            this.color = color
            textAlign = if (right) Paint.Align.RIGHT else Paint.Align.LEFT
        }
        val shown = if (maxW > 0) TextUtils.ellipsize(s, p, maxW, TextUtils.TruncateAt.END).toString() else s
        canvas.drawText(shown, x, y, p)
    }

    fun rect(left: Float, top: Float, right: Float, bottom: Float, color: Int) =
        canvas.drawRect(left, top, right, bottom, Paint().apply { this.color = color })

    fun line(x1: Float, y: Float, x2: Float, color: Int = GREY, width: Float = 0.5f) {
        canvas.drawLine(
            x1,
            y,
            x2,
            y,
            Paint().apply {
                this.color = color
                strokeWidth = width
            }
        )
    }

    /** Draws the bitmap as large as fits in the rest of the page, centered. */
    fun image(bmp: Bitmap) {
        val boxW = W - 2 * M
        val boxH = H - M - y
        val scale = minOf(boxW / bmp.width, boxH / bmp.height)
        val w = bmp.width * scale
        val h = bmp.height * scale
        val left = M + (boxW - w) / 2
        canvas.drawBitmap(bmp, null, RectF(left, y, left + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
    }

    fun finish(out: OutputStream) {
        page?.let(doc::finishPage)
        doc.writeTo(out)
        doc.close()
    }
}

/** Names shown in exports. */
data class Lookup(val categories: Map<String, String>, val payers: Map<String, String>, val projects: Map<String, String>)

private fun amount(cents: Long?, currency: String) = cents?.let { Money.format(it, currency) } ?: "No total"

/** Reimbursement report: cover, summary table, then one page per receipt image. */
object ReportPdf {
    fun write(
        out: OutputStream,
        pdf: PdfWriter,
        report: Report,
        receipts: List<Receipt>,
        names: Lookup,
        userName: String,
        homeCurrency: String,
        images: (Receipt) -> List<Bitmap>
    ) {
        val payer = report.payerId?.let { names.payers[it] } ?: "No payer"
        val dates = receipts.mapNotNull { it.purchaseDate }
        val range = if (dates.isEmpty()) "No dates" else "${dates.min().format(PdfDate)} to ${dates.max().format(PdfDate)}"
        val submitted = (report.submittedAt ?: report.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()

        pdf.newPage()
        pdf.band("Expense report", "${report.number}    ${report.title}")
        listOf(
            "Payer" to payer,
            "Report number" to report.number,
            "Dates" to range,
            "Receipts" to receipts.size.toString(),
            "Submitted by" to userName.ifBlank { "Not set" },
            "Submitted on" to submitted.format(PdfDate)
        ).forEach { (k, v) ->
            pdf.text(k, M, pdf.y, 10f, color = GREY)
            pdf.text(v, M + 130, pdf.y, 12f, maxW = W - 2 * M - 130)
            pdf.y += 26
        }
        pdf.y += 16
        pdf.rect(M, pdf.y - 24, M + 4, pdf.y + 26, CORAL)
        pdf.text("Total", M + 12, pdf.y - 8, 10f, color = GREY)
        pdf.text(Money.format(report.totalCents, homeCurrency), M + 12, pdf.y + 18, 28f, true)
        pdf.line(M, H - 120, M + 250, INK)
        pdf.text("Signature", M, H - 104, 9f, color = GREY)
        pdf.line(M + 300, H - 120, W - M, INK)
        pdf.text("Date", M + 300, H - 104, 9f, color = GREY)

        summaryTable(pdf, report, receipts, names, homeCurrency)

        receipts.forEach { r ->
            images(r).forEach { bmp ->
                pdf.newPage()
                pdf.text(r.merchantName ?: "Receipt", M, pdf.y, 14f, true, maxW = W - 2 * M - 120)
                pdf.text(amount(r.totalCents, r.currency), W - M, pdf.y, 14f, true, right = true)
                pdf.y += 18
                val info = listOfNotNull(
                    r.purchaseDate?.format(PdfDate),
                    r.categoryId?.let { names.categories[it] },
                    r.taxCents?.let { "Tax ${Money.format(it, r.currency)}" },
                    r.projectId?.let { names.projects[it] }
                ).joinToString("    ")
                pdf.text(info, M, pdf.y, 10f, color = GREY, maxW = W - 2 * M)
                r.notes?.takeIf { it.isNotBlank() }?.let {
                    pdf.y += 14
                    pdf.text(it.replace('\n', ' '), M, pdf.y, 10f, color = GREY, maxW = W - 2 * M)
                }
                pdf.y += 14
                pdf.image(bmp)
                bmp.recycle()
            }
        }
        pdf.finish(out)
    }

    private fun summaryTable(pdf: PdfWriter, report: Report, receipts: List<Receipt>, names: Lookup, home: String) {
        val amountX = W - M - 50
        fun header() {
            pdf.text("Date", M, pdf.y, 9f, true)
            pdf.text("Merchant", M + 80, pdf.y, 9f, true)
            pdf.text("Category", M + 260, pdf.y, 9f, true)
            pdf.text("Amount", amountX, pdf.y, 9f, true, right = true)
            pdf.text("Cur.", W - M - 40, pdf.y, 9f, true)
            pdf.line(M, pdf.y + 6, W - M, CORAL, 1.5f)
            pdf.y += 22
        }
        pdf.newPage()
        pdf.band("Summary", "${report.number}    ${receipts.size} receipts")
        header()
        receipts.forEach { r ->
            val foreign = r.currency != home && r.homeCurrencyTotalCents != null
            if (pdf.y > H - M - 40) {
                pdf.newPage()
                header()
            }
            pdf.text(r.purchaseDate?.toString() ?: "No date", M, pdf.y)
            pdf.text(r.merchantName ?: "Receipt", M + 80, pdf.y, maxW = 170f)
            pdf.text(r.categoryId?.let { names.categories[it] } ?: "No category", M + 260, pdf.y, maxW = 140f)
            pdf.text(amount(r.totalCents, r.currency), amountX, pdf.y, right = true)
            pdf.text(r.currency, W - M - 40, pdf.y)
            if (foreign) {
                pdf.y += 12
                val rate = r.fxRate?.let { " at rate %.4f".format(it) } ?: ""
                pdf.text("${Money.format(r.homeCurrencyTotalCents!!, home)} $home$rate", amountX, pdf.y, 8f, color = GREY, right = true)
            }
            pdf.y += 8
            pdf.line(M, pdf.y, W - M, Color.rgb(0xDD, 0xDD, 0xDD))
            pdf.y += 14
        }
        pdf.y += 6
        pdf.text("Total", M + 260, pdf.y, 11f, true)
        pdf.text(Money.format(receipts.sumOf { ReportRepository.homeCents(it) }, home), amountX, pdf.y, 11f, true, right = true)
        pdf.text(home, W - M - 40, pdf.y, 11f, true)
    }
}

/** One page summary for the accountant: totals by tax line. */
object TaxPdf {
    fun write(out: OutputStream, pdf: PdfWriter, t: TaxYearSummary, userName: String, home: String) {
        pdf.newPage()
        pdf.band(
            "Tax year ${t.year}",
            listOf(userName, "Made on ${LocalDate.now().format(PdfDate)}").filter {
                it.isNotBlank()
            }.joinToString("    ")
        )
        fun header() {
            pdf.text("Tax line", M, pdf.y, 9f, true)
            pdf.text("Receipts", M + 300, pdf.y, 9f, true, right = true)
            pdf.text("Spent", M + 400, pdf.y, 9f, true, right = true)
            pdf.text("Deductible", W - M, pdf.y, 9f, true, right = true)
            pdf.line(M, pdf.y + 6, W - M, CORAL, 1.5f)
            pdf.y += 22
        }
        header()
        t.lines.forEach { l ->
            if (pdf.y > H - M - 60) {
                pdf.newPage()
                header()
            }
            val label = l.line?.let { "${it.label} (${it.code})" } ?: "Not mapped to a tax line"
            pdf.text(label, M, pdf.y, maxW = 230f)
            pdf.text(l.count.toString(), M + 300, pdf.y, right = true)
            pdf.text(Money.format(l.totalCents, home), M + 400, pdf.y, right = true)
            pdf.text(Money.format(l.deductibleCents, home), W - M, pdf.y, right = true)
            l.line?.deductiblePercent?.takeIf { it < 100 }?.let {
                pdf.y += 12
                pdf.text("$it% deductible", M, pdf.y, 8f, color = GREY)
            }
            pdf.y += 8
            pdf.line(M, pdf.y, W - M, Color.rgb(0xDD, 0xDD, 0xDD))
            pdf.y += 14
        }
        pdf.y += 6
        pdf.text("Total", M, pdf.y, 11f, true)
        pdf.text(t.receipts.size.toString(), M + 300, pdf.y, 11f, true, right = true)
        pdf.text(Money.format(t.totalCents, home), M + 400, pdf.y, 11f, true, right = true)
        pdf.text(Money.format(t.deductibleCents, home), W - M, pdf.y, 11f, true, right = true)
        pdf.y += 30
        if (t.warnings.isNotEmpty()) {
            pdf.text("${t.warnings.size} items still need a look in the app.", M, pdf.y, 10f, color = GREY)
            pdf.y += 16
        }
        pdf.text("Every receipt is in the CSV and the receipts folder. This is not tax advice.", M, pdf.y, 10f, color = GREY)
        pdf.finish(out)
    }
}
