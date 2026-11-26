package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Book
import com.example.data.model.BookPurpose
import com.example.data.model.CanonicalLedger
import com.example.data.model.Chapter
import com.example.data.model.EvidenceLevel
import com.example.data.model.ExportFormat
import com.example.data.model.KnowledgeEntry
import com.example.data.model.ResearchSource
import com.example.export.ManuscriptBundle
import com.example.export.PublicationExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private fun sampleBundle(): ManuscriptBundle = ManuscriptBundle(
        book = Book(
            id = "book_1",
            userId = "user_1",
            title = "Architecture of Meaning",
            subtitle = "An Epistemic Inquiry",
            genre = "Philosophy & Systems"
        ),
        authorName = "Sadman Sakib",
        purpose = BookPurpose(
            centralPurpose = "Clarify the discipline of grounded reasoning.",
            mainThesis = "Purpose and epistemic humility must precede technical generation.",
            authorValues = "Truthfulness, accountability, clarity."
        ),
        ledger = CanonicalLedger(
            coreThesis = "Purpose and epistemic humility must precede technical generation.",
            foundationalAxioms = listOf("Axiom 1: Capability is not authority."),
            terminologicalGlossary = listOf("Epistemic Ledger — Authoritative registry of definitions and premises.")
        ),
        chapters = listOf(
            Chapter(
                id = "ch_1",
                partLabel = "Part I",
                title = "Foundations of Purpose",
                orderIndex = 0L,
                objective = "Establish why meaning must precede synthesis.",
                content = "[[INTERNAL:do not leak this note]]\nMeaning anchors every enduring work.\n\n## Core Distinction\nEvidence differs from speculation.",
                wordCount = 11L
            )
        ),
        codex = listOf(
            KnowledgeEntry(
                id = "codex_1",
                title = "Epistemic Humility",
                summary = "Recognizing the boundary between verified fact and conjecture.",
                epistemicStatus = EvidenceLevel.INTERPRETATION.name
            )
        ),
        sources = listOf(
            ResearchSource(
                id = "src_1",
                title = "The Craft of Research",
                author = "Booth, W. C., Colomb, G. G., & Williams, J. M.",
                publicationDate = "2016",
                publisher = "University of Chicago Press",
                evidenceLevel = EvidenceLevel.SUPPORTED.name,
                citationText = "Booth, W. C., Colomb, G. G., & Williams, J. M. (2016). The Craft of Research. University of Chicago Press."
            )
        )
    )

    @Test
    fun appNameMatchesMeaningMatters() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("MeaningMatters", appName)
    }

    @Test
    fun publicationExporter_generatesAndValidatesAllFormats() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val exporter = PublicationExporter(context)
        val bundle = sampleBundle()

        // Markdown
        val mdResult = exporter.export(bundle, ExportFormat.MARKDOWN)
        assertTrue("Markdown validation failed: ${mdResult.validationNotes}", mdResult.validated)
        val mdText = mdResult.file.readText()
        assertTrue(mdText.contains("# Architecture of Meaning"))
        assertTrue(mdText.contains("Chapter 1: Foundations of Purpose"))
        assertFalse("Internal draft markers must be stripped", mdText.contains("do not leak this note"))

        // JSON backup
        val jsonResult = exporter.export(bundle, ExportFormat.JSON)
        assertTrue("JSON validation failed: ${jsonResult.validationNotes}", jsonResult.validated)

        // DOCX (OOXML)
        val docxResult = exporter.export(bundle, ExportFormat.DOCX)
        assertTrue("DOCX validation failed: ${docxResult.validationNotes}", docxResult.validated)

        // EPUB 3 (uncompressed first mimetype entry + container/opf/nav)
        val epubResult = exporter.export(bundle, ExportFormat.EPUB)
        assertTrue("EPUB validation failed: ${epubResult.validationNotes}", epubResult.validated)
        ZipFile(epubResult.file).use { zf ->
            val firstEntry = zf.entries().nextElement()
            assertEquals("mimetype", firstEntry.name)
            assertEquals(ZipEntry.STORED, firstEntry.method)
        }

        // PDF
        val pdfResult = exporter.export(bundle, ExportFormat.PDF)
        assertTrue("PDF validation failed: ${pdfResult.validationNotes}", pdfResult.validated)
    }
}
