package org.kealohalabs.receiptbeat.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.graphics.scale
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import org.kealohalabs.receiptbeat.core.data.FileStore
import org.kealohalabs.receiptbeat.core.model.FileKind
import org.kealohalabs.receiptbeat.core.model.ReceiptFile
import org.kealohalabs.receiptbeat.extraction.ImageHash

/**
 * Turns a captured photo or an imported file into the files a receipt keeps:
 * the original, a WebP display copy (about 1600 px), a 320 px thumbnail, and a perceptual hash.
 * PDFs keep the PDF as the source of truth and render each page for reading.
 */
@Singleton
class ImageProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: FileStore
) {
    data class Saved(val files: List<ReceiptFile>, val hash: Long?)

    fun mimeOf(uri: Uri): String = context.contentResolver.getType(uri)
        ?: when (uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic", "heif" -> "image/heic"
            "eml" -> "message/rfc822"
            else -> "image/jpeg"
        }

    /** Save images (one receipt, possibly several pages) and return the file rows. */
    fun saveImages(receiptId: String, uris: List<Uri>, stitch: Boolean): Saved {
        val bitmaps = uris.map { decode(it) }
        val pages = if (stitch && bitmaps.size > 1) listOf(stitch(bitmaps)) else bitmaps
        val out = mutableListOf<ReceiptFile>()
        uris.forEachIndexed { i, uri ->
            val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            val path = "$receiptId/original_$i"
            store.write(path, bytes)
            out +=
                ReceiptFile(
                    receiptId = receiptId,
                    kind = FileKind.ORIGINAL,
                    path = path,
                    mime = mimeOf(uri),
                    bytes = bytes.size.toLong(),
                    page = i
                )
        }
        out += savePages(receiptId, pages)
        return Saved(out, hash(pages.first()))
    }

    /** Save a PDF (from a file, a scanner result, or an email) and its rendered pages. */
    fun savePdf(receiptId: String, pdf: ByteArray): Saved {
        val path = "$receiptId/source.pdf"
        store.write(path, pdf)
        val pages = renderPdf(pdf)
        val out =
            listOf(
                ReceiptFile(receiptId = receiptId, kind = FileKind.PDF, path = path, mime = "application/pdf", bytes = pdf.size.toLong())
            ) +
                savePages(receiptId, pages)
        return Saved(out, pages.firstOrNull()?.let(::hash))
    }

    fun savePdf(receiptId: String, uri: Uri): Saved = savePdf(
        receiptId,
        context.contentResolver.openInputStream(uri)!!.use {
            it.readBytes()
        }
    )

    private fun savePages(receiptId: String, pages: List<Bitmap>): List<ReceiptFile> = buildList {
        pages.forEachIndexed { i, bmp ->
            val display = fit(bmp, DISPLAY_PX)
            val dPath = "$receiptId/display_$i.webp"
            add(
                ReceiptFile(
                    receiptId = receiptId,
                    kind = FileKind.DISPLAY,
                    path = dPath,
                    mime = "image/webp",
                    bytes = store.write(dPath, webp(display, 82)),
                    page = i
                )
            )
            if (i == 0) {
                val tPath = "$receiptId/thumb.webp"
                add(
                    ReceiptFile(
                        receiptId = receiptId,
                        kind = FileKind.THUMB,
                        path = tPath,
                        mime = "image/webp",
                        bytes = store.write(tPath, webp(fit(bmp, THUMB_PX), 75))
                    )
                )
            }
        }
    }

    fun decode(uri: Uri): Bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
        d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        // Keep memory sane for huge camera photos. 3000 px is plenty for OCR.
        val longSide = max(info.size.width, info.size.height)
        if (longSide > MAX_DECODE_PX) d.setTargetSampleSize(longSide / MAX_DECODE_PX + 1)
    }

    fun decodeStored(path: String): Bitmap = store.read(path).let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }

    fun renderPdf(pdf: ByteArray, maxPages: Int = 10): List<Bitmap> {
        val tmp = File.createTempFile("pdf", ".pdf", context.cacheDir)
        try {
            tmp.writeBytes(pdf)
            ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { r ->
                    return (0 until minOf(r.pageCount, maxPages)).map { i ->
                        r.openPage(i).use { page ->
                            val scale = DISPLAY_PX.toFloat() / max(page.width, page.height)
                            val bmp = Bitmap.createBitmap(
                                (page.width * scale).toInt(),
                                (page.height * scale).toInt(),
                                Bitmap.Config.ARGB_8888
                            )
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp
                        }
                    }
                }
            }
        } finally {
            tmp.delete()
        }
    }

    /** Long receipts: join pages top to bottom into one tall image. */
    fun stitch(pages: List<Bitmap>): Bitmap {
        val width = pages.maxOf { it.width }
        val scaled = pages.map { if (it.width == width) it else it.scale(width, it.height * width / it.width) }
        val out = Bitmap.createBitmap(width, scaled.sumOf { it.height }, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        var y = 0f
        scaled.forEach {
            c.drawBitmap(it, 0f, y, null)
            y += it.height
        }
        return out
    }

    private fun fit(bmp: Bitmap, px: Int): Bitmap {
        val longSide = max(bmp.width, bmp.height)
        if (longSide <= px) return bmp
        val s = px.toFloat() / longSide
        return bmp.scale((bmp.width * s).toInt().coerceAtLeast(1), (bmp.height * s).toInt().coerceAtLeast(1))
    }

    private fun webp(bmp: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, it) }.toByteArray()

    /** 9x8 grayscale difference hash. Close hashes mean the same picture. */
    fun hash(bmp: Bitmap): Long {
        val small = bmp.scale(9, 8)
        val gray = IntArray(72) { i ->
            val p = small.getPixel(i % 9, i / 9)
            (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
        }
        return ImageHash.dHash(gray)
    }

    companion object {
        const val DISPLAY_PX = 1600
        const val THUMB_PX = 320
        const val MAX_DECODE_PX = 3000
    }
}
