package org.kealohalabs.receiptbeat.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigDecimal
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.kealohalabs.receiptbeat.core.data.CatalogRepository
import org.kealohalabs.receiptbeat.core.data.FileStore
import org.kealohalabs.receiptbeat.core.data.ReceiptRepository
import org.kealohalabs.receiptbeat.core.data.ReportRepository
import org.kealohalabs.receiptbeat.core.data.SettingsRepository
import org.kealohalabs.receiptbeat.core.designsystem.R
import org.kealohalabs.receiptbeat.core.model.FileKind
import org.kealohalabs.receiptbeat.core.model.Receipt
import org.kealohalabs.receiptbeat.core.model.ReceiptFile

object Csv {
    val header = listOf("Date", "Merchant", "Category", "Amount", "Tax", "Currency", "Payer", "Project", "Notes", "File")

    /** RFC 4180 quoting. Text that a spreadsheet would run as a formula gets a leading quote mark. */
    fun cell(value: String): String {
        val safe = if (value.firstOrNull() in setOf('=', '+', '-', '@') && value.toBigDecimalOrNull() == null) "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    fun row(cells: List<String>) = cells.joinToString(",") { cell(it) }

    fun receipts(list: List<Receipt>, names: Lookup, fileNames: Map<String, String>): String = buildString {
        append(row(header)).append("\r\n")
        list.forEach { r ->
            append(
                row(
                    listOf(
                        r.purchaseDate?.toString().orEmpty(),
                        r.merchantName.orEmpty(),
                        r.categoryId?.let { names.categories[it] }.orEmpty(),
                        decimal(r.totalCents),
                        decimal(r.taxCents),
                        r.currency,
                        r.payerId?.let { names.payers[it] }.orEmpty(),
                        r.projectId?.let { names.projects[it] }.orEmpty(),
                        r.notes.orEmpty(),
                        fileNames[r.id].orEmpty()
                    )
                )
            ).append("\r\n")
        }
    }

    private fun decimal(cents: Long?) = cents?.let { BigDecimal.valueOf(it, 2).toPlainString() }.orEmpty()
}

object ExportNames {
    /** Like 2026-03-14_Kona-Coffee_12.50.jpg. Later pages get _p2, _p3. */
    fun fileName(r: Receipt, ext: String, page: Int = 0): String {
        val merchant = (r.merchantName ?: "").replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').take(40).ifBlank { "Receipt" }
        return listOfNotNull(
            r.purchaseDate?.toString() ?: "no-date",
            merchant,
            r.totalCents?.let { BigDecimal.valueOf(it, 2).toPlainString() },
            "p${page + 1}".takeIf { page > 0 }
        ).joinToString("_") + ".$ext"
    }

    fun extOf(mime: String) = when (mime) {
        "image/jpeg" -> "jpg"
        "application/pdf" -> "pdf"
        "message/rfc822" -> "eml"
        else -> mime.substringAfter('/').substringBefore('+').ifBlank { "bin" }
    }

    /** The best copy to hand over: the original, else the PDF, else the cleaned image. */
    fun bestFiles(files: List<ReceiptFile>): List<ReceiptFile> =
        listOf(FileKind.ORIGINAL, FileKind.PDF, FileKind.EMAIL_SNAPSHOT, FileKind.DISPLAY)
            .firstNotNullOfOrNull { k -> files.filter { it.kind == k }.sortedBy { it.page }.ifEmpty { null } }
            .orEmpty()

    /** Names for every file, made unique within one export. */
    fun names(r: Receipt, files: List<ReceiptFile>, used: MutableSet<String>): List<Pair<ReceiptFile, String>> = files.mapIndexed { i, f ->
        val ext = extOf(f.mime)
        val base = fileName(r, ext, i).removeSuffix(".$ext")
        var name = "$base.$ext"
        var n = 2
        while (!used.add(name)) name = "${base}_${n++}.$ext"
        f to name
    }
}

/** Builds report and tax year files on the phone, in cache/exports, ready for the share sheet. */
@Singleton
class Exporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reports: ReportRepository,
    private val receipts: ReceiptRepository,
    private val catalog: CatalogRepository,
    private val settings: SettingsRepository,
    private val store: FileStore
) {
    private fun dir() = File(context.cacheDir, "exports").apply { mkdirs() }

    private fun writer(): PdfWriter {
        val regular = runCatching { ResourcesCompat.getFont(context, R.font.inter_regular) }.getOrNull() ?: Typeface.SANS_SERIF
        val bold =
            runCatching { ResourcesCompat.getFont(context, R.font.inter_medium) }.getOrNull()?.let { Typeface.create(it, Typeface.BOLD) }
                ?: Typeface.DEFAULT_BOLD
        return PdfWriter(regular, bold)
    }

    private suspend fun lookup() = Lookup(
        catalog.categories().associate { it.id to it.name },
        catalog.payers().associate { it.id to it.name },
        catalog.projects().associate { it.id to it.name }
    )

    /** The report PDF and CSV. */
    suspend fun report(reportId: String): List<File> = withContext(Dispatchers.IO) {
        val report = reports.get(reportId) ?: return@withContext emptyList()
        val list = reports.reportReceipts(reportId).sortedWith(compareBy({ it.purchaseDate }, { it.createdAt }))
        val names = lookup()
        val s = settings.current()
        val used = mutableSetOf<String>()
        val all = list.associate { it.id to receipts.files(it.id) }
        val fileNames = list.associate { r ->
            r.id to
                ExportNames.names(r, ExportNames.bestFiles(all[r.id].orEmpty()), used).joinToString(" ") { it.second }
        }
        val pdf = File(dir(), "${report.number}.pdf")
        pdf.outputStream().use { out ->
            ReportPdf.write(out, writer(), report, list, names, s.userName, s.homeCurrency) { r -> images(all[r.id].orEmpty()) }
        }
        val csv = File(dir(), "${report.number}.csv").apply { writeText(Csv.receipts(list, names, fileNames)) }
        listOf(pdf, csv)
    }

    /** Decoded one receipt at a time, so a long report never holds every image in memory. */
    private fun images(files: List<ReceiptFile>): List<Bitmap> = files
        .filter { it.kind == FileKind.DISPLAY }
        .sortedBy { it.page }
        .mapNotNull { f -> runCatching { store.input(f.path).use { BitmapFactory.decodeStream(it) } }.getOrNull() }

    /** ZIP for the accountant: PDF summary, CSV of every receipt, and every original file. */
    suspend fun taxYear(year: Int): File = withContext(Dispatchers.IO) {
        val t = reports.taxYear(year).first()
        val names = lookup()
        val s = settings.current()
        val list = t.receipts.sortedWith(compareBy({ it.purchaseDate }, { it.createdAt }))
        val used = mutableSetOf<String>()
        val files = list.associate { r -> r.id to ExportNames.names(r, ExportNames.bestFiles(receipts.files(r.id)), used) }
        val zip = File(dir(), "Receipt-Beat-taxes-$year.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { z ->
            z.putNextEntry(ZipEntry("Tax-summary-$year.pdf"))
            z.write(ByteArrayOutputStream().also { TaxPdf.write(it, writer(), t, s.userName, s.homeCurrency) }.toByteArray())
            z.putNextEntry(ZipEntry("Receipts-$year.csv"))
            z.write(Csv.receipts(list, names, files.mapValues { (_, v) -> v.joinToString(" ") { it.second } }).toByteArray())
            files.values.flatten().forEach { (f, name) ->
                runCatching {
                    store.input(f.path).use { input ->
                        z.putNextEntry(ZipEntry("receipts/$name"))
                        input.copyTo(z)
                    }
                }
            }
        }
        zip
    }
}
