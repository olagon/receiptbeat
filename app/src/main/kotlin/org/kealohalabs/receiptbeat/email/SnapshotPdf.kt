package org.kealohalabs.receiptbeat.email

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.ByteArrayOutputStream

/**
 * A clean PDF of an email, made on the phone from its text. Remote images are never loaded,
 * so tracking pixels never fire.
 */
object SnapshotPdf {
    private const val W = 612
    private const val H = 792
    private const val M = 48

    fun make(from: String, subject: String, date: String, body: String): ByteArray {
        val doc = PdfDocument()
        val head = TextPaint().apply {
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            color = Color.BLACK
            isAntiAlias = true
        }
        val text = TextPaint().apply {
            textSize = 10.5f
            color = Color.rgb(30, 30, 30)
            isAntiAlias = true
        }
        val width = W - 2 * M
        val header = StaticLayout.Builder.obtain(
            "$subject\nFrom: $from\nDate: $date",
            0,
            subject.length + from.length + date.length + 14,
            head,
            width
        ).build()
        val layout = StaticLayout.Builder.obtain(body, 0, body.length, text, width).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        var line = 0
        var pageNo = 1
        while (line < layout.lineCount || pageNo == 1) {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            val c = page.canvas
            var top = M.toFloat()
            if (pageNo == 1) {
                c.save()
                c.translate(M.toFloat(), top)
                header.draw(c)
                c.restore()
                top += header.height + 16
            }
            val startY = layout.getLineTop(line)
            var end = line
            while (end < layout.lineCount && layout.getLineBottom(end) - startY < H - M - top) end++
            c.save()
            c.translate(M.toFloat(), top - startY)
            c.clipRect(0f, startY.toFloat(), width.toFloat(), (if (end > 0) layout.getLineBottom(end - 1) else startY).toFloat())
            layout.draw(c)
            c.restore()
            doc.finishPage(page)
            if (end == line) break
            line = end
            pageNo++
            if (pageNo > 20) break
        }
        return ByteArrayOutputStream().also { doc.writeTo(it) }.toByteArray().also { doc.close() }
    }
}
