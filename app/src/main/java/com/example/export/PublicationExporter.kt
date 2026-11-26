package com.example.export

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.model.Book
import com.example.data.model.BookPurpose
import com.example.data.model.CanonicalLedger
import com.example.data.model.Chapter
import com.example.data.model.ExportFormat
import com.example.data.model.KnowledgeEntry
import com.example.data.model.ResearchSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class ManuscriptBundle(
    val book: Book,
    val authorName: String,
    val purpose: BookPurpose?,
    val ledger: CanonicalLedger?,
    val chapters: List<Chapter>,
    val codex: List<KnowledgeEntry>,
    val sources: List<ResearchSource>
)

data class ExportResult(
    val file: File,
    val format: ExportFormat,
    val byteSize: Long,
    val validated: Boolean,
    val validationNotes: String,
    val chapterCount: Long,
    val wordCount: Long
)

class PublicationExporter(private val context: Context) {

    fun export(bundle: ManuscriptBundle, format: ExportFormat): ExportResult {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val slug = bundle.book.title
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "manuscript" }
            .take(40)
        val file = File(exportsDir, "${slug}_${System.currentTimeMillis()}.${format.extension}")

        val orderedChapters = bundle.chapters.sortedBy { it.orderIndex }
        val totalWords = orderedChapters.sumOf { it.wordCount }

        when (format) {
            ExportFormat.PDF -> writePdf(bundle, orderedChapters, file)
            ExportFormat.DOCX -> writeDocx(bundle, orderedChapters, file)
            ExportFormat.EPUB -> writeEpub(bundle, orderedChapters, file)
            ExportFormat.MARKDOWN -> file.writeText(buildMarkdown(bundle, orderedChapters), Charsets.UTF_8)
            ExportFormat.JSON -> file.writeText(buildJsonBackup(bundle, orderedChapters), Charsets.UTF_8)
        }

        val (isValid, notes) = validateExport(file, format)
        return ExportResult(
            file = file,
            format = format,
            byteSize = file.length(),
            validated = isValid,
            validationNotes = notes,
            chapterCount = orderedChapters.size.toLong(),
            wordCount = totalWords
        )
    }

    private fun sanitizeProse(text: String): String {
        // Strip internal drafting instructions enclosed in [[INTERNAL:...]] if present
        return text.replace(Regex("\\[\\[INTERNAL:.*?]]", RegexOption.DOT_MATCHES_ALL), "").trim()
    }

    private fun buildMarkdown(bundle: ManuscriptBundle, chapters: List<Chapter>): String {
        val year = SimpleDateFormat("yyyy", Locale.US).format(Date())
        return buildString {
            appendLine("# ${bundle.book.title}")
            if (bundle.book.subtitle.isNotBlank()) appendLine("## ${bundle.book.subtitle}")
            appendLine()
            appendLine("**Author:** ${bundle.authorName}")
            appendLine("**Genre:** ${bundle.book.genre.ifBlank { "Non-Fiction / Literary" }}")
            appendLine()
            appendLine("---")
            appendLine("### Copyright")
            appendLine("Copyright © $year ${bundle.authorName}. All rights reserved.")
            appendLine("Prepared with MeaningMatters Book Writer — Write with purpose. Research with integrity. Publish with accountability.")
            appendLine()
            appendLine("---")
            appendLine("## Table of Contents")
            chapters.forEachIndexed { idx, ch ->
                val part = if (ch.partLabel.isNotBlank()) "${ch.partLabel} — " else ""
                appendLine("${idx + 1}. $part${ch.title}")
            }
            if (bundle.ledger != null && bundle.ledger.terminologicalGlossary.isNotEmpty()) {
                appendLine("- Glossary")
            }
            if (bundle.sources.isNotEmpty()) {
                appendLine("- Bibliography")
            }
            appendLine()
            if (bundle.purpose != null && bundle.purpose.centralPurpose.isNotBlank()) {
                appendLine("---")
                appendLine("## Preface & Core Thesis")
                appendLine("**Central Purpose:** ${bundle.purpose.centralPurpose}")
                appendLine()
                appendLine("**Main Thesis:** ${bundle.purpose.mainThesis}")
                appendLine()
            }
            chapters.forEachIndexed { idx, ch ->
                appendLine("---")
                if (ch.partLabel.isNotBlank()) appendLine("*${ch.partLabel}*")
                appendLine("## Chapter ${idx + 1}: ${ch.title}")
                appendLine()
                val body = sanitizeProse(ch.content)
                if (body.isNotBlank()) {
                    appendLine(body)
                } else {
                    appendLine("_[Chapter draft not yet completed — Objective: ${ch.objective}]_")
                }
                appendLine()
            }
            val glossary = bundle.ledger?.terminologicalGlossary.orEmpty()
            if (glossary.isNotEmpty()) {
                appendLine("---")
                appendLine("## Glossary")
                glossary.forEach { appendLine("- $it") }
                appendLine()
            }
            if (bundle.sources.isNotEmpty()) {
                appendLine("---")
                appendLine("## Bibliography & Epistemic Provenance")
                bundle.sources.forEach { s ->
                    val cite = s.citationText.ifBlank {
                        "${s.author.ifBlank { "Unknown Author" }} (${s.publicationDate.ifBlank { "n.d." }}). *${s.title}*. ${s.publisher}"
                    }
                    appendLine("- $cite [${s.evidenceLevel}]")
                }
                appendLine()
            }
            val openQs = bundle.ledger?.openQuestions.orEmpty()
            if (openQs.isNotEmpty()) {
                appendLine("---")
                appendLine("## Appendix: Open Epistemic Questions")
                openQs.forEach { appendLine("- $it") }
            }
        }
    }

    private fun buildJsonBackup(bundle: ManuscriptBundle, chapters: List<Chapter>): String {
        val root = JSONObject()
        root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))
        root.put("author", bundle.authorName)
        root.put("book", JSONObject().apply {
            put("title", bundle.book.title)
            put("subtitle", bundle.book.subtitle)
            put("genre", bundle.book.genre)
            put("status", bundle.book.status)
            put("wordCount", bundle.book.wordCount)
            put("pipelineStage", bundle.book.pipelineStage)
        })
        if (bundle.purpose != null) {
            root.put("purpose", JSONObject().apply {
                put("centralPurpose", bundle.purpose.centralPurpose)
                put("mainThesis", bundle.purpose.mainThesis)
                put("authorValues", bundle.purpose.authorValues)
                put("approved", bundle.purpose.approved)
            })
        }
        if (bundle.ledger != null) {
            root.put("canonicalLedger", JSONObject().apply {
                put("coreThesis", bundle.ledger.coreThesis)
                put("foundationalAxioms", JSONArray(bundle.ledger.foundationalAxioms))
                put("formalDefinitions", JSONArray(bundle.ledger.formalDefinitions))
                put("terminologicalGlossary", JSONArray(bundle.ledger.terminologicalGlossary))
                put("openQuestions", JSONArray(bundle.ledger.openQuestions))
            })
        }
        root.put("chapters", JSONArray().apply {
            chapters.forEach { ch ->
                put(JSONObject().apply {
                    put("orderIndex", ch.orderIndex)
                    put("partLabel", ch.partLabel)
                    put("title", ch.title)
                    put("status", ch.status)
                    put("wordCount", ch.wordCount)
                    put("coreProposition", ch.coreProposition)
                    put("content", sanitizeProse(ch.content))
                })
            }
        })
        root.put("codex", JSONArray().apply {
            bundle.codex.forEach { e ->
                put(JSONObject().apply {
                    put("title", e.title)
                    put("category", e.category)
                    put("epistemicStatus", e.epistemicStatus)
                    put("originType", e.originType)
                    put("summary", e.summary)
                })
            }
        })
        root.put("sources", JSONArray().apply {
            bundle.sources.forEach { s ->
                put(JSONObject().apply {
                    put("title", s.title)
                    put("author", s.author)
                    put("publicationDate", s.publicationDate)
                    put("evidenceLevel", s.evidenceLevel)
                    put("citationText", s.citationText)
                })
            }
        })
        return root.toString(2)
    }

    private fun writePdf(bundle: ManuscriptBundle, chapters: List<Chapter>, outFile: File) {
        try {
            writeNativeAndroidPdf(bundle, chapters, outFile)
        } catch (_: Throwable) {
            writeStandardPdf14(bundle, chapters, outFile)
        }
    }

    private fun writeNativeAndroidPdf(bundle: ManuscriptBundle, chapters: List<Chapter>, outFile: File) {
        val pdf = PdfDocument()
        val pageWidth = 595 // A4 points
        val pageHeight = 842
        val marginX = 56f
        val topY = 72f
        val bottomY = 780f
        val contentWidth = pageWidth - (marginX * 2)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(23, 40, 60)
            textSize = 26f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(88, 96, 110)
            textSize = 14f
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(23, 40, 60)
            textSize = 16f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(31, 36, 45)
            textSize = 11f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(120, 125, 134)
            textSize = 9f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        var pageNum = 1

        fun drawFooter(canvas: android.graphics.Canvas, pageNumber: Int) {
            canvas.drawText(bundle.book.title.take(55), marginX, pageHeight - 32f, footerPaint)
            val pLabel = "Page $pageNumber"
            val w = footerPaint.measureText(pLabel)
            canvas.drawText(pLabel, pageWidth - marginX - w, pageHeight - 32f, footerPaint)
        }

        fun wrapLines(text: String, paint: Paint, maxWidth: Float): List<String> {
            val out = mutableListOf<String>()
            text.split("\n").forEach { rawParagraph ->
                val p = rawParagraph.trim()
                if (p.isEmpty()) {
                    out.add("")
                } else {
                    val words = p.split(Regex("\\s+"))
                    var current = StringBuilder()
                    for (word in words) {
                        val candidate = if (current.isEmpty()) word else "$current $word"
                        if (paint.measureText(candidate) <= maxWidth) {
                            current = StringBuilder(candidate)
                        } else {
                            if (current.isNotEmpty()) out.add(current.toString())
                            current = StringBuilder(word)
                        }
                    }
                    if (current.isNotEmpty()) out.add(current.toString())
                }
            }
            return out
        }

        // Page 1: Title & Copyright Page
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = page.canvas
        var y = 210f
        wrapLines(bundle.book.title, titlePaint, contentWidth).forEach { line ->
            canvas.drawText(line, marginX, y, titlePaint)
            y += 34f
        }
        if (bundle.book.subtitle.isNotBlank()) {
            y += 6f
            wrapLines(bundle.book.subtitle, subtitlePaint, contentWidth).forEach { line ->
                canvas.drawText(line, marginX, y, subtitlePaint)
                y += 22f
            }
        }
        y += 28f
        canvas.drawText("By ${bundle.authorName}", marginX, y, headingPaint)
        val year = SimpleDateFormat("yyyy", Locale.US).format(Date())
        canvas.drawText("Copyright © $year ${bundle.authorName}. All rights reserved.", marginX, 680f, bodyPaint)
        canvas.drawText("Published via MeaningMatters Book Writer — Epistemic Accountability Edition", marginX, 698f, footerPaint)
        drawFooter(canvas, pageNum)
        pdf.finishPage(page)
        pageNum++

        // Helper for flowing pages
        fun startFlowPage(): Pair<PdfDocument.Page, Float> {
            val p = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
            return p to topY
        }

        // Table of Contents + Preface
        val (tocPage, startY) = startFlowPage()
        page = tocPage
        canvas = page.canvas
        y = startY
        canvas.drawText("Table of Contents", marginX, y, headingPaint)
        y += 26f
        chapters.forEachIndexed { idx, ch ->
            if (y > bottomY) {
                drawFooter(canvas, pageNum)
                pdf.finishPage(page)
                pageNum++
                val next = startFlowPage()
                page = next.first
                canvas = page.canvas
                y = next.second
            }
            val label = "${idx + 1}. ${ch.title} (${ch.wordCount} words)"
            canvas.drawText(label.take(80), marginX, y, bodyPaint)
            y += 18f
        }
        if (bundle.purpose != null && bundle.purpose.mainThesis.isNotBlank()) {
            y += 18f
            canvas.drawText("Core Thesis & Purpose", marginX, y, headingPaint)
            y += 22f
            wrapLines("${bundle.purpose.centralPurpose}\n\nThesis: ${bundle.purpose.mainThesis}", bodyPaint, contentWidth)
                .forEach { line ->
                    if (y > bottomY) {
                        drawFooter(canvas, pageNum)
                        pdf.finishPage(page)
                        pageNum++
                        val next = startFlowPage()
                        page = next.first
                        canvas = page.canvas
                        y = next.second
                    }
                    if (line.isNotEmpty()) canvas.drawText(line, marginX, y, bodyPaint)
                    y += 16f
                }
        }
        drawFooter(canvas, pageNum)
        pdf.finishPage(page)
        pageNum++

        // Chapters
        chapters.forEachIndexed { idx, ch ->
            val (chPage, chY) = startFlowPage()
            page = chPage
            canvas = page.canvas
            y = chY
            if (ch.partLabel.isNotBlank()) {
                canvas.drawText(ch.partLabel.uppercase(Locale.US), marginX, y, footerPaint)
                y += 18f
            }
            wrapLines("Chapter ${idx + 1}: ${ch.title}", headingPaint, contentWidth).forEach { line ->
                canvas.drawText(line, marginX, y, headingPaint)
                y += 22f
            }
            y += 8f
            val body = sanitizeProse(ch.content).ifBlank { "[Draft pending — Objective: ${ch.objective}]" }
            wrapLines(body, bodyPaint, contentWidth).forEach { line ->
                if (y > bottomY) {
                    drawFooter(canvas, pageNum)
                    pdf.finishPage(page)
                    pageNum++
                    val next = startFlowPage()
                    page = next.first
                    canvas = page.canvas
                    y = next.second
                }
                if (line.isNotEmpty()) canvas.drawText(line, marginX, y, bodyPaint)
                y += 16f
            }
            drawFooter(canvas, pageNum)
            pdf.finishPage(page)
            pageNum++
        }

        // Glossary & Bibliography
        val glossary = bundle.ledger?.terminologicalGlossary.orEmpty()
        if (glossary.isNotEmpty() || bundle.sources.isNotEmpty()) {
            val (backPage, backY) = startFlowPage()
            page = backPage
            canvas = page.canvas
            y = backY
            if (glossary.isNotEmpty()) {
                canvas.drawText("Glossary", marginX, y, headingPaint)
                y += 22f
                glossary.forEach { term ->
                    wrapLines("• $term", bodyPaint, contentWidth).forEach { line ->
                        if (y > bottomY) {
                            drawFooter(canvas, pageNum)
                            pdf.finishPage(page)
                            pageNum++
                            val next = startFlowPage()
                            page = next.first
                            canvas = page.canvas
                            y = next.second
                        }
                        canvas.drawText(line, marginX, y, bodyPaint)
                        y += 16f
                    }
                }
                y += 16f
            }
            if (bundle.sources.isNotEmpty()) {
                canvas.drawText("Bibliography", marginX, y, headingPaint)
                y += 22f
                bundle.sources.forEach { s ->
                    val cite = s.citationText.ifBlank { "${s.author} (${s.publicationDate}). ${s.title}. ${s.publisher}" }
                    wrapLines("• $cite [${s.evidenceLevel}]", bodyPaint, contentWidth).forEach { line ->
                        if (y > bottomY) {
                            drawFooter(canvas, pageNum)
                            pdf.finishPage(page)
                            pageNum++
                            val next = startFlowPage()
                            page = next.first
                            canvas = page.canvas
                            y = next.second
                        }
                        canvas.drawText(line, marginX, y, bodyPaint)
                        y += 16f
                    }
                }
            }
            drawFooter(canvas, pageNum)
            pdf.finishPage(page)
        }

        FileOutputStream(outFile).use { fos -> pdf.writeTo(fos) }
        pdf.close()
    }

    /**
     * Standards-compliant pure-Kotlin PDF 1.4 writer used as a resilient fallback
     * when native Android PdfDocument is unavailable (e.g., headless JVM / Robolectric).
     */
    private fun writeStandardPdf14(bundle: ManuscriptBundle, chapters: List<Chapter>, outFile: File) {
        fun pdfEsc(text: String): String = text
            .replace("\\", "\\\\")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .map { ch -> if (ch.code in 32..126) ch else ' ' }
            .joinToString("")

        fun wrapAscii(text: String, maxChars: Int = 82): List<String> {
            val out = mutableListOf<String>()
            text.split("\n").forEach { raw ->
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) {
                    out.add("")
                } else {
                    var line = StringBuilder()
                    for (word in trimmed.split(Regex("\\s+"))) {
                        if (line.length + word.length + 1 <= maxChars) {
                            if (line.isNotEmpty()) line.append(' ')
                            line.append(word)
                        } else {
                            if (line.isNotEmpty()) out.add(line.toString())
                            line = StringBuilder(word)
                        }
                    }
                    if (line.isNotEmpty()) out.add(line.toString())
                }
            }
            return out
        }

        val allLines = mutableListOf<String>()
        allLines.add(bundle.book.title)
        if (bundle.book.subtitle.isNotBlank()) allLines.add(bundle.book.subtitle)
        allLines.add("By ${bundle.authorName}")
        allLines.add("")
        chapters.forEachIndexed { idx, ch ->
            allLines.add("Chapter ${idx + 1}: ${ch.title}")
            allLines.addAll(wrapAscii(sanitizeProse(ch.content).ifBlank { ch.objective }))
            allLines.add("")
        }
        val pages = allLines.chunked(46).ifEmpty { listOf(listOf(bundle.book.title)) }

        val objects = mutableListOf<String>()
        // Obj 1: Catalog, Obj 2: Pages, Obj 3: Font
        objects.add("<< /Type /Catalog /Pages 2 0 R >>")
        val pageObjIds = pages.indices.map { 4 + it * 2 }
        val kids = pageObjIds.joinToString(" ") { "$it 0 R" }
        objects.add("<< /Type /Pages /Kids [ $kids ] /Count ${pages.size} >>")
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Times-Roman >>")

        pages.forEachIndexed { idx, pageLines ->
            val contentObjId = 5 + idx * 2
            objects.add(
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] " +
                    "/Resources << /Font << /F1 3 0 R >> >> /Contents $contentObjId 0 R >>"
            )
            val stream = buildString {
                appendLine("BT")
                appendLine("/F1 11 Tf")
                appendLine("56 780 Td")
                appendLine("15 TL")
                pageLines.forEach { line ->
                    appendLine("(${pdfEsc(line)}) Tj T*")
                }
                appendLine("ET")
            }
            val streamBytes = stream.toByteArray(Charsets.US_ASCII)
            objects.add("<< /Length ${streamBytes.size} >>\nstream\n${stream}endstream")
        }

        val out = ByteArrayOutputStream()
        fun writeAscii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        writeAscii("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()
        objects.forEachIndexed { index, body ->
            offsets.add(out.size())
            writeAscii("${index + 1} 0 obj\n$body\nendobj\n")
        }
        val xrefOffset = out.size()
        writeAscii("xref\n0 ${objects.size + 1}\n")
        writeAscii("0000000000 65535 f \n")
        offsets.forEach { off ->
            writeAscii(String.format(Locale.US, "%010d 00000 n \n", off))
        }
        writeAscii("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        outFile.writeBytes(out.toByteArray())
    }

    private fun writeDocx(bundle: ManuscriptBundle, chapters: List<Chapter>, outFile: File) {
        val year = SimpleDateFormat("yyyy", Locale.US).format(Date())
        fun xmlEsc(s: String): String = s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

        fun paragraphXml(text: String, style: String? = null, pageBreakBefore: Boolean = false): String {
            val pPr = buildString {
                if (style != null || pageBreakBefore) {
                    append("<w:pPr>")
                    if (style != null) append("<w:pStyle w:val=\"$style\"/>")
                    if (pageBreakBefore) append("<w:pageBreakBefore/>")
                    append("</w:pPr>")
                }
            }
            return "<w:p>$pPr<w:r><w:t xml:space=\"preserve\">${xmlEsc(text)}</w:t></w:r></w:p>"
        }

        val docXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>""")
            append(paragraphXml(bundle.book.title, style = "Title"))
            if (bundle.book.subtitle.isNotBlank()) append(paragraphXml(bundle.book.subtitle, style = "Subtitle"))
            append(paragraphXml("By ${bundle.authorName}", style = "Heading2"))
            append(paragraphXml("Copyright © $year ${bundle.authorName}. All rights reserved."))
            append(paragraphXml("Table of Contents", style = "Heading1", pageBreakBefore = true))
            chapters.forEachIndexed { idx, ch ->
                append(paragraphXml("${idx + 1}. ${ch.title}"))
            }
            if (bundle.purpose != null && bundle.purpose.mainThesis.isNotBlank()) {
                append(paragraphXml("Preface & Core Thesis", style = "Heading1", pageBreakBefore = true))
                append(paragraphXml(bundle.purpose.centralPurpose))
                append(paragraphXml("Thesis: ${bundle.purpose.mainThesis}"))
            }
            chapters.forEachIndexed { idx, ch ->
                append(paragraphXml("Chapter ${idx + 1}: ${ch.title}", style = "Heading1", pageBreakBefore = true))
                if (ch.partLabel.isNotBlank()) append(paragraphXml(ch.partLabel, style = "Subtitle"))
                val body = sanitizeProse(ch.content).ifBlank { "[Draft pending — Objective: ${ch.objective}]" }
                body.split("\n").forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("## ")) {
                        append(paragraphXml(trimmed.removePrefix("## "), style = "Heading2"))
                    } else if (trimmed.isNotEmpty()) {
                        append(paragraphXml(trimmed))
                    }
                }
            }
            val glossary = bundle.ledger?.terminologicalGlossary.orEmpty()
            if (glossary.isNotEmpty()) {
                append(paragraphXml("Glossary", style = "Heading1", pageBreakBefore = true))
                glossary.forEach { append(paragraphXml("• $it")) }
            }
            if (bundle.sources.isNotEmpty()) {
                append(paragraphXml("Bibliography", style = "Heading1", pageBreakBefore = true))
                bundle.sources.forEach { s ->
                    val cite = s.citationText.ifBlank { "${s.author} (${s.publicationDate}). ${s.title}. ${s.publisher}" }
                    append(paragraphXml("• $cite [${s.evidenceLevel}]"))
                }
            }
            append("""<w:sectPr><w:pgSz w:w="12240" w:h="15840"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr>""")
            append("</w:body></w:document>")
        }

        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""

        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

        val docRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

        val stylesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:rPr><w:b/><w:sz w:val="48"/></w:rPr></w:style>
  <w:style w:type="paragraph" w:styleId="Subtitle"><w:name w:val="Subtitle"/><w:rPr><w:i/><w:sz w:val="28"/></w:rPr></w:style>
  <w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:rPr><w:b/><w:sz w:val="34"/></w:rPr></w:style>
  <w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:rPr><w:b/><w:sz w:val="26"/></w:rPr></w:style>
</w:styles>"""

        ZipOutputStream(FileOutputStream(outFile)).use { zos ->
            fun addEntry(name: String, content: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            addEntry("[Content_Types].xml", contentTypes)
            addEntry("_rels/.rels", rels)
            addEntry("word/_rels/document.xml.rels", docRels)
            addEntry("word/styles.xml", stylesXml)
            addEntry("word/document.xml", docXml)
        }
    }

    private fun writeEpub(bundle: ManuscriptBundle, chapters: List<Chapter>, outFile: File) {
        val bookUuid = UUID.randomUUID().toString()
        val year = SimpleDateFormat("yyyy", Locale.US).format(Date())
        val modifiedIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

        fun esc(s: String): String = s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

        fun xhtmlPage(title: String, bodyHtml: String): String = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><title>${esc(title)}</title><link rel="stylesheet" type="text/css" href="styles.css"/></head>
<body>$bodyHtml</body>
</html>"""

        val css = """body { font-family: Georgia, serif; line-height: 1.65; color: #1F242D; margin: 1.5em; }
h1, h2 { color: #17283C; } .meta { color: #58606E; font-style: italic; }"""

        val titleXhtml = xhtmlPage(
            bundle.book.title,
            "<h1>${esc(bundle.book.title)}</h1>" +
                (if (bundle.book.subtitle.isNotBlank()) "<p class=\"meta\">${esc(bundle.book.subtitle)}</p>" else "") +
                "<p><strong>${esc(bundle.authorName)}</strong></p>" +
                "<p class=\"meta\">Copyright © $year ${esc(bundle.authorName)}. All rights reserved.</p>"
        )

        val chapterFiles = mutableListOf<Triple<String, String, String>>() // id, filename, title
        chapterFiles.add(Triple("titlepage", "title.xhtml", "Title & Copyright"))

        val chapterContents = mutableMapOf<String, String>()
        chapterContents["title.xhtml"] = titleXhtml

        chapters.forEachIndexed { idx, ch ->
            val fname = "chapter_${idx + 1}.xhtml"
            val id = "ch_${idx + 1}"
            chapterFiles.add(Triple(id, fname, "Chapter ${idx + 1}: ${ch.title}"))
            val paragraphs = sanitizeProse(ch.content)
                .ifBlank { "[Draft pending — Objective: ${ch.objective}]" }
                .split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n") { line ->
                    if (line.startsWith("## ")) "<h2>${esc(line.removePrefix("## "))}</h2>"
                    else "<p>${esc(line)}</p>"
                }
            val partHtml = if (ch.partLabel.isNotBlank()) "<p class=\"meta\">${esc(ch.partLabel)}</p>" else ""
            chapterContents[fname] = xhtmlPage(ch.title, "$partHtml<h1>Chapter ${idx + 1}: ${esc(ch.title)}</h1>\n$paragraphs")
        }

        if (bundle.sources.isNotEmpty()) {
            val bibHtml = buildString {
                append("<h1>Bibliography</h1><ul>")
                bundle.sources.forEach { s ->
                    val cite = s.citationText.ifBlank { "${s.author} (${s.publicationDate}). ${s.title}. ${s.publisher}" }
                    append("<li>${esc(cite)} <em>[${esc(s.evidenceLevel)}]</em></li>")
                }
                append("</ul>")
            }
            chapterFiles.add(Triple("bibliography", "bibliography.xhtml", "Bibliography"))
            chapterContents["bibliography.xhtml"] = xhtmlPage("Bibliography", bibHtml)
        }

        val navItems = chapterFiles.joinToString("\n") { (_, fname, title) ->
            "<li><a href=\"$fname\">${esc(title)}</a></li>"
        }
        val navXhtml = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><title>Table of Contents</title><link rel="stylesheet" type="text/css" href="styles.css"/></head>
<body><nav epub:type="toc" id="toc"><h1>Table of Contents</h1><ol>$navItems</ol></nav></body>
</html>"""

        val manifestItems = chapterFiles.joinToString("\n") { (id, fname, _) ->
            """    <item id="$id" href="$fname" media-type="application/xhtml+xml"/>"""
        }
        val spineItems = chapterFiles.joinToString("\n") { (id, _, _) ->
            """    <itemref idref="$id"/>"""
        }

        val contentOpf = """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="pub-id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="pub-id">urn:uuid:$bookUuid</dc:identifier>
    <dc:title>${esc(bundle.book.title)}</dc:title>
    <dc:creator>${esc(bundle.authorName)}</dc:creator>
    <dc:language>en</dc:language>
    <meta property="dcterms:modified">$modifiedIso</meta>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="css" href="styles.css" media-type="text/css"/>
$manifestItems
  </manifest>
  <spine>
$spineItems
  </spine>
</package>"""

        val containerXml = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""

        ZipOutputStream(FileOutputStream(outFile)).use { zos ->
            // EPUB 3 requires uncompressed 'mimetype' as the very first ZIP entry
            val mimeBytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
            val mimeEntry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimeBytes.size.toLong()
                compressedSize = mimeBytes.size.toLong()
                val checksum = CRC32().apply { update(mimeBytes) }
                this.crc = checksum.value
            }
            zos.putNextEntry(mimeEntry)
            zos.write(mimeBytes)
            zos.closeEntry()

            fun addCompressed(path: String, text: String) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(text.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            addCompressed("META-INF/container.xml", containerXml)
            addCompressed("OEBPS/content.opf", contentOpf)
            addCompressed("OEBPS/nav.xhtml", navXhtml)
            addCompressed("OEBPS/styles.css", css)
            chapterContents.forEach { (fname, html) ->
                addCompressed("OEBPS/$fname", html)
            }
        }
    }

    fun validateExport(file: File, format: ExportFormat): Pair<Boolean, String> {
        if (!file.exists() || file.length() == 0L) {
            return false to "Exported file is missing or empty."
        }
        return try {
            when (format) {
                ExportFormat.PDF -> {
                    val header = ByteArray(5)
                    file.inputStream().use { it.read(header) }
                    val ok = String(header, Charsets.US_ASCII).startsWith("%PDF-")
                    ok to if (ok) "Validated PDF header (%PDF-) and ${file.length()} bytes." else "Invalid PDF header."
                }
                ExportFormat.DOCX -> {
                    ZipFile(file).use { zf ->
                        val hasDoc = zf.getEntry("word/document.xml") != null
                        val hasTypes = zf.getEntry("[Content_Types].xml") != null
                        val hasRels = zf.getEntry("_rels/.rels") != null
                        val ok = hasDoc && hasTypes && hasRels
                        ok to if (ok) "Validated OOXML archive (word/document.xml, [Content_Types].xml, _rels/.rels)." else "Missing required OOXML entries."
                    }
                }
                ExportFormat.EPUB -> {
                    ZipFile(file).use { zf ->
                        val hasMime = zf.getEntry("mimetype") != null
                        val hasContainer = zf.getEntry("META-INF/container.xml") != null
                        val hasOpf = zf.getEntry("OEBPS/content.opf") != null
                        val hasNav = zf.getEntry("OEBPS/nav.xhtml") != null
                        val ok = hasMime && hasContainer && hasOpf && hasNav
                        ok to if (ok) "Validated EPUB 3 package (mimetype, container.xml, content.opf, nav.xhtml)." else "Missing required EPUB 3 entries."
                    }
                }
                ExportFormat.MARKDOWN -> {
                    val text = file.readText()
                    val ok = text.startsWith("# ") && text.length > 20
                    ok to if (ok) "Validated UTF-8 Markdown manuscript (${text.length} chars)." else "Empty Markdown output."
                }
                ExportFormat.JSON -> {
                    val parsed = JSONObject(file.readText())
                    val ok = parsed.has("book") && parsed.has("chapters")
                    ok to if (ok) "Validated JSON backup schema (book + chapters)." else "Missing JSON keys."
                }
            }
        } catch (e: Exception) {
            false to "Validation error: ${e.localizedMessage ?: "unknown"}"
        }
    }
}
