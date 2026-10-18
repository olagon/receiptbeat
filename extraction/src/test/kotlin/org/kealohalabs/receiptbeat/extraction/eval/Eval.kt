package org.kealohalabs.receiptbeat.extraction.eval

import java.io.File
import kotlin.system.exitProcess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.kealohalabs.receiptbeat.extraction.MerchantNormalizer
import org.kealohalabs.receiptbeat.extraction.RulesParser

/** Runs the rules parser on testdata/receipts and prints accuracy per field. Fails when total accuracy is under 90%. */
fun main(args: Array<String>) {
    val dir = File(args.firstOrNull() ?: "testdata/receipts")
    val files = dir.listFiles { f -> f.extension == "txt" }.orEmpty().sortedBy { it.name }
    require(files.isNotEmpty()) { "No receipts in ${dir.absolutePath}" }
    val fields = listOf("merchant", "date", "total", "tax", "currency")
    val hits = fields.associateWith { 0 }.toMutableMap()
    val misses = mutableListOf<String>()

    for (txt in files) {
        val want = Json.parseToJsonElement(File(dir, txt.nameWithoutExtension + ".json").readText()).jsonObject
        val got = RulesParser.parse(txt.readText())
        fun s(key: String) = want[key]?.jsonPrimitive?.takeIf { it.isString }?.content
        val pairs = mapOf(
            "merchant" to (MerchantNormalizer.key(s("merchant").orEmpty()) to got.merchant.value?.let { MerchantNormalizer.key(it) }),
            "date" to (s("date") to got.date.value?.toString()),
            "total" to (want["total_cents"]?.jsonPrimitive?.longOrNull to got.total.value),
            "tax" to (want["tax_cents"]?.jsonPrimitive?.longOrNull to got.tax.value),
            "currency" to (s("currency") to got.currency.value)
        )
        for ((field, p) in pairs) {
            val (expected, actual) = p
            if (expected == actual) hits[field] = hits.getValue(field) + 1 else misses += "${txt.name} $field: want $expected, got $actual"
        }
    }

    val n = files.size
    println("Extraction eval on $n receipts")
    fields.forEach { println("  %-9s %5.1f%%  (%d/%d)".format(it, 100.0 * hits.getValue(it) / n, hits.getValue(it), n)) }
    if (misses.isNotEmpty()) println("Misses:\n" + misses.joinToString("\n") { "  $it" })
    if (hits.getValue("total") * 10 < n * 9) {
        println("Total accuracy is under 90%.")
        exitProcess(1)
    }
}
