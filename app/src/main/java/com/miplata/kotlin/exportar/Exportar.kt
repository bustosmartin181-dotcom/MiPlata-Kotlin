package com.miplata.kotlin.exportar

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.miplata.kotlin.nucleo.*
import com.miplata.kotlin.ui.pesos
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/* Exportar a Excel (.xlsx) y PDF, con las mismas hojas y secciones que Mi plata. */
object Exportar {
    data class Datos(val hoy: String, val resumen: List<Triple<String, Any, Boolean>>, val dias: List<List<Any?>>,
                     val gastos: List<List<Any?>>, val importantes: List<List<Any?>>)

    private fun fecha(iso: String) = Fechas.diaYMes(iso) + "/" + iso.take(4)

    fun datos(S: Estado, hoy: String): Datos {
        val c = Nucleo.calcular(S, hoy); val o = Nucleo.objetivoDiario(S, hoy)
        val r = mutableListOf<Triple<String, Any, Boolean>>(
            Triple("Período", fecha(S.start) + " al " + fecha(S.end), false),
            Triple("Total de ingresos", S.ingresos.sumOf { it.a }, true), Triple("Total gastos importantes", c.total, true),
            Triple("Todavía por pagar", c.porPagar, true), Triple("Disponible del mes", c.disp, true), Triple("Base por día", c.base, true),
            Triple("Gastado en el período", S.spent.filter { it.cuentaEnSaldo }.sumOf { it.amt }, true), Triple("Saldo", c.saldo, true))
        if (o != null) { r += Triple("Objetivo por día", o.obj, true); r += Triple("Te sobra si lo cumplís", o.proyectado, true) }
        if (c.fondo.activo) { r += Triple("Ahorrado en el período", c.fondo.ahorroPeriodo, true); r += Triple("Fondo de ahorro total", c.fondo.totalConRend, true) }
        val dias = Nucleo.diasDelPeriodo(S, hoy, c).map { x ->
            val pasado = x.i <= c.hoyIdx || x.gasto != 0.0
            listOf(fecha(x.iso), x.dia, x.presupuesto, if (x.gasto != 0.0) x.gasto else null, if (pasado) x.sobra else null) }
        val gastos = S.spent.sortedWith(compareBy<Movimiento> { it.date }.thenBy { it.id }).map { e ->
            listOf(fecha(e.date), e.cat ?: "", e.note, e.amt, when { e.esMeta -> "Aporte a meta"; e.amt < 0 -> "Entrada"; e.dev == 1 -> "Me lo deben"
                e.dev == 2 -> "Me lo devolvieron"; else -> "Gasto" }) }
        val imp = S.items.filter { it.n.isNotBlank() || it.a != 0.0 }.map { i ->
            listOf(i.n, i.a, if (i.p) "Pagado" else "Pendiente", i.q?.takeIf { it.total > 1 }?.let { "${it.actual} de ${it.total}" } ?: "") }
        return Datos(hoy, r, dias, gastos, imp)
    }

    /* ---------- Excel ---------- */
    private fun esc(s: String) = s.replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private class Negrita(val t: String)
    private class Monto(val v: Double)
    private fun celda(v: Any?, r: Int, c: Int): String {
        val ref = "${'A' + c}${r + 1}"
        return when (v) {
            null, "" -> ""
            is Monto -> if (v.v.isFinite()) "<c r=\"$ref\" s=\"2\"><v>${Math.round(v.v * 100) / 100.0}</v></c>" else ""
            is Negrita -> "<c r=\"$ref\" t=\"inlineStr\" s=\"1\"><is><t xml:space=\"preserve\">${esc(v.t)}</t></is></c>"
            is Number -> "<c r=\"$ref\"><v>$v</v></c>"
            else -> "<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(v.toString())}</t></is></c>"
        }
    }
    private fun hoja(filas: List<List<Any?>>, anchos: List<Int>) =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><cols>" +
        anchos.mapIndexed { i, w -> "<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$w\" customWidth=\"1\"/>" }.joinToString("") + "</cols><sheetData>" +
        filas.mapIndexed { r, f -> "<row r=\"${r + 1}\">" + f.mapIndexed { c, v -> celda(v, r, c) }.joinToString("") + "</row>" }.joinToString("") +
        "</sheetData></worksheet>"
    private fun m(v: Any?) = (v as? Double)?.let { Monto(it) }

    fun xlsx(d: Datos, salida: OutputStream) {
        val hojas = listOf(
            Triple("Resumen", listOf(listOf<Any?>(Negrita("Mi plata"), Negrita("Resumen del período"))) + d.resumen.map { listOf(it.first, if (it.third) Monto(it.second as Double) else it.second) }, listOf(30, 26)),
            Triple("Días", listOf(listOf<Any?>(Negrita("Fecha"), Negrita("Día"), Negrita("Presupuesto"), Negrita("Gasto"), Negrita("Sobrante"))) +
                d.dias.map { listOf(it[0], it[1], m(it[2]), m(it[3]), m(it[4])) }, listOf(14, 12, 16, 16, 16)),
            Triple("Gastos anotados", listOf(listOf<Any?>(Negrita("Fecha"), Negrita("Categoría"), Negrita("Detalle"), Negrita("Monto"), Negrita("Tipo"))) +
                d.gastos.map { listOf(it[0], it[1], it[2], m(it[3]), it[4]) }, listOf(14, 16, 36, 16, 16)),
            Triple("Gastos importantes", listOf(listOf<Any?>(Negrita("Concepto"), Negrita("Monto"), Negrita("Estado"), Negrita("Cuotas"))) +
                d.importantes.map { listOf(it[0], m(it[1]), it[2], it[3]) }, listOf(34, 16, 14, 12)),
        )
        val ns = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"; val rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        val cab = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        val archivos = mutableListOf(
            "[Content_Types].xml" to cab + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
                hojas.indices.joinToString("") { "<Override PartName=\"/xl/worksheets/sheet${it + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" } + "</Types>",
            "_rels/.rels" to cab + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"$rel/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>",
            "xl/workbook.xml" to cab + "<workbook xmlns=\"$ns\" xmlns:r=\"$rel\"><sheets>" + hojas.mapIndexed { i, h -> "<sheet name=\"${esc(h.first)}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>" }.joinToString("") + "</sheets></workbook>",
            "xl/_rels/workbook.xml.rels" to cab + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                hojas.indices.joinToString("") { "<Relationship Id=\"rId${it + 1}\" Type=\"$rel/worksheet\" Target=\"worksheets/sheet${it + 1}.xml\"/>" } +
                "<Relationship Id=\"rId${hojas.size + 1}\" Type=\"$rel/styles\" Target=\"styles.xml\"/></Relationships>",
            "xl/styles.xml" to cab + "<styleSheet xmlns=\"$ns\"><numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"&quot;$&quot;#,##0;[Red]\\-&quot;$&quot;#,##0\"/></numFmts>" +
                "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
                "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>" +
                "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
                "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
                "<cellXfs count=\"3\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
                "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/></cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>",
        )
        hojas.forEachIndexed { i, h -> archivos += "xl/worksheets/sheet${i + 1}.xml" to hoja(h.second, h.third) }
        ZipOutputStream(salida).use { z -> archivos.forEach { (n, t) -> z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() } }
    }

    /* ---------- PDF (A4, letra de ancho fijo) ---------- */
    data class Linea(val t: String, val negrita: Boolean = false, val grande: Boolean = false)

    fun lineasPdf(d: Datos): List<Linea> {
        fun izq(t: String, n: Int) = t.take(n).padEnd(n)
        fun der(t: String, n: Int) = t.take(n).padStart(n)
        val L = mutableListOf(Linea("Mi plata - Resumen", grande = true), Linea(""), Linea("Creado el " + fecha(d.hoy)), Linea(""), Linea("RESUMEN", true))
        d.resumen.forEach { (t, v, plata) -> L += Linea(izq(t, 34) + der(if (plata) pesos(v as Double) else v.toString(), if (plata) 16 else 28)) }
        L += Linea(""); L += Linea("DIA POR DIA", true); L += Linea(izq("Dia", 15) + der("Presupuesto", 14) + der("Gasto", 14) + der("Sobrante", 14), true)
        d.dias.forEach { x -> L += Linea(izq((x[1] as String).take(3) + " " + x[0], 15) + der(pesos(x[2] as Double), 14) +
            der((x[3] as Double?)?.let(::pesos) ?: "", 14) + der((x[4] as Double?)?.let(::pesos) ?: "", 14)) }
        L += Linea(""); L += Linea("GASTOS ANOTADOS", true)
        if (d.gastos.isEmpty()) L += Linea("Todavia no hay gastos anotados.")
        d.gastos.forEach { x -> val tipo = x[4] as String; val cat = x[1] as String
            L += Linea(izq((x[0] as String).take(5), 7) + izq((if (tipo == "Gasto" && cat.isNotEmpty()) "$cat - " else "") + (x[2] as String).ifEmpty { tipo }, 46) + der(pesos(x[3] as Double), 14)) }
        L += Linea(""); L += Linea("GASTOS IMPORTANTES", true)
        if (d.importantes.isEmpty()) L += Linea("No cargaste gastos importantes.")
        d.importantes.forEach { x -> L += Linea(izq(x[0] as String, 34) + der(pesos(x[1] as Double), 14) + "  " + x[2] + ((x[3] as String).takeIf { it.isNotEmpty() }?.let { " (cuota $it)" } ?: "")) }
        return L
    }

    fun pdf(d: Datos, pie: String, salida: OutputStream) {
        val doc = PdfDocument()
        val normal = Paint().apply { typeface = Typeface.MONOSPACE; textSize = 9.5f; isAntiAlias = true }
        val negrita = Paint(normal).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
        val titulo = Paint().apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textSize = 16f; isAntiAlias = true }
        val chico = Paint(normal).apply { textSize = 8f }
        val lineas = lineasPdf(d); val porPagina = ((800 - 50) / 12.5).toInt()
        val paginas = lineas.chunked(porPagina).ifEmpty { listOf(listOf(Linea(""))) }
        paginas.forEachIndexed { k, pag ->
            val p = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, k + 1).create())
            var y = 842 - 800f + 0f
            pag.forEach { l -> y += 12.5f; p.canvas.drawText(l.t, 40f, y, if (l.grande) titulo else if (l.negrita) negrita else normal) }
            p.canvas.drawText("$pie  -  página ${k + 1} de ${paginas.size}", 40f, 842 - 28f, chico)
            doc.finishPage(p)
        }
        doc.writeTo(salida); doc.close()
    }

    /** Texto para compartir (WhatsApp, etc.). */
    fun resumenTexto(S: Estado, hoy: String): String {
        val c = Nucleo.calcular(S, hoy)
        val fechaLarga = java.text.SimpleDateFormat("EEEE d 'de' MMMM", java.util.Locale("es", "AR")).format(java.util.Date())
        val b = StringBuilder("💰 Mi plata · $fechaLarga\nPresupuesto de hoy: ${pesos(c.quedaHoy + c.gastoHoy)}\nGasté hoy: ${pesos(c.gastoHoy)}\nMe queda para hoy: ${pesos(c.quedaHoy)}")
        c.manana?.let { b.append("\nMañana tendré: ${pesos(it)}") }
        if (c.fondo.activo) b.append("\n🏦 Mi fondo de ahorro: ${pesos(c.fondo.totalConRend)}")
        b.append("\nDías hasta el próximo cobro: ${c.faltan}")
        S.metas.filter { it.t > 0 }.forEach { m -> b.append("\n🎯 ${m.n}: ${Math.round(Nucleo.juntadoEnMeta(S, m) / m.t * 100)}% juntado") }
        b.append("\n\n“${com.miplata.kotlin.ui.fraseDelDia(hoy)}”")
        return b.toString()
    }
}
