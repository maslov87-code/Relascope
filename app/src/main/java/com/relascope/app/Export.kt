package com.relascope.app

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Table0Builder {
    fun rows(s: Session): List<List<Any?>> {
        val r = mutableListOf<List<Any?>>()
        r.add(listOf("Номер площадки", "Число площадок (полная-1, половинная-0,5)",
            "Число деревьев на площадках, подсчитанное с помощью полнотомера по породам, шт.", null, null, null, null, null, null, null, null, null, "всего"))
        r.add(listOf(null, null) + Species.values().map { it.ru as Any }.flatMap { listOf(it, null) } + listOf(null))
        r.add(listOf(null, null) + Species.values().flatMap { listOf("деловых", "дровяных") } + listOf(null))
        s.plots.forEachIndexed { i, p ->
            val row = mutableListOf<Any?>(i + 1, if (p.weight == 1.0) 1 else 0.5)
            Species.values().forEach { sp -> Grade.values().forEach { g -> row.add(p.get(sp, g)) } }
            row.add(p.total()); r.add(row)
        }
        val tot = mutableListOf<Any?>("Всего", s.plots.sumOf { it.weight }.let { if (it % 1.0 == 0.0) it.toInt() else it })
        Species.values().forEach { sp -> Grade.values().forEach { g -> tot.add(s.plots.sumOf { it.get(sp, g) }) } }
        tot.add(s.plots.sumOf { it.total() }); r.add(tot)
        return r
    }
    fun merges() = listOf("C1:M1", "A1:A3", "B1:B3", "N1:N3", "C2:D2", "E2:F2", "G2:H2", "I2:J2", "K2:L2")
}

object CsvExporter {
    fun export(os: OutputStream, s: Session) {
        val sb = StringBuilder()
        Table0Builder.rows(s).forEach { row -> sb.appendLine(row.joinToString(";") { esc(it) }) }
        os.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) // UTF-8 BOM для Excel
        os.write(sb.toString().toByteArray(Charsets.UTF_8))
    }
    private fun esc(v: Any?) = when (v) {
        null -> ""
        is Double -> if (v % 1.0 == 0.0) v.toInt().toString() else v.toString().replace('.', ',')
        else -> { val t = v.toString(); if (t.contains(';') || t.contains('"')) "\"" + t.replace("\"", "\"\"") + "\"" else t }
    }
}

object XlsxExporter {
    fun export(os: OutputStream, s: Session) {
        val rows = Table0Builder.rows(s)
        val merges = Table0Builder.merges()
        val sheet = StringBuilder("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
        rows.forEachIndexed { ri, row ->
            sheet.append("<row r=\"${ri + 1}\">")
            row.forEachIndexed { ci, v ->
                val ref = colRef(ci) + (ri + 1)
                when (v) {
                    null -> {}
                    is Int, is Double -> sheet.append("<c r=\"$ref\"><v>$v</v></c>")
                    else -> sheet.append("<c r=\"$ref\" t=\"inlineStr\"><is><t>${xml(v.toString())}</t></is></c>")
                }
            }
            sheet.append("</row>")
        }
        sheet.append("</sheetData>")
        if (merges.isNotEmpty()) { sheet.append("<mergeCells count=\"${merges.size}\">"); merges.forEach { sheet.append("<mergeCell ref=\"$it\"/>") }; sheet.append("</mergeCells>") }
        sheet.append("</worksheet>")

        ZipOutputStream(os).use { z ->
            fun put(name: String, content: String) { z.putNextEntry(ZipEntry(name)); z.write(content.toByteArray(Charsets.UTF_8)); z.closeEntry() }
            put("[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>")
            put("_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>")
            put("xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Таксация\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>")
            put("xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>")
            put("xl/worksheets/sheet1.xml", sheet.toString())
        }
    }
    private fun colRef(i: Int): String { var n = i; var s = ""; while (n >= 0) { s = ('A' + n % 26) + s; n = n / 26 - 1 }; return s }
    private fun xml(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}