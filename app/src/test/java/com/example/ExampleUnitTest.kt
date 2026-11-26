package com.example

import com.example.core.DiffLineType
import com.example.core.TextDiffEngine
import com.example.data.model.Book
import com.example.data.model.BookStatus
import com.example.data.model.Chapter
import com.example.data.model.ChapterStatus
import com.example.data.model.CodexCategory
import com.example.data.model.CodexOrigin
import com.example.data.model.DriveSyncStatus
import com.example.data.model.EvidenceLevel
import com.example.data.model.ExportFormat
import com.example.data.model.MuhasabahFramework
import com.example.data.model.ReviewType
import com.example.data.model.countWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun textDiffEngine_identicalTextProducesZeroChanges() {
        val text = "Chapter One\nPurpose precedes generation.\nHuman authority remains final."
        val summary = TextDiffEngine.computeDiff(text, text)
        assertEquals(0, summary.addedLines)
        assertEquals(0, summary.removedLines)
        assertEquals(3, summary.unchangedLines)
        assertEquals(0L, summary.wordDelta)
        assertTrue(summary.lines.all { it.type == DiffLineType.UNCHANGED })
    }

    @Test
    fun textDiffEngine_detectsAdditionsRemovalsAndWordDelta() {
        val oldText = "Line A\nOld premise here\nLine C"
        val newText = "Line A\nRevised stronger premise with citation\nLine C\nNew concluding thought"
        val summary = TextDiffEngine.computeDiff(oldText, newText)

        assertEquals(2, summary.addedLines)
        assertEquals(1, summary.removedLines)
        assertEquals(2, summary.unchangedLines)
        // Old: 7 words, New: 12 words => +5
        assertEquals(5L, summary.wordDelta)
        assertTrue(summary.lines.any { it.type == DiffLineType.REMOVED && it.text == "Old premise here" })
        assertTrue(summary.lines.any { it.type == DiffLineType.ADDED && it.text == "New concluding thought" })
    }

    @Test
    fun textDiffEngine_handlesEmptyStringsGracefully() {
        val addedOnly = TextDiffEngine.computeDiff("", "First line\nSecond line")
        assertEquals(2, addedOnly.addedLines)
        assertEquals(0, addedOnly.removedLines)
        assertEquals(4L, addedOnly.wordDelta)

        val removedOnly = TextDiffEngine.computeDiff("Only line", "")
        assertEquals(0, removedOnly.addedLines)
        assertEquals(1, removedOnly.removedLines)
        assertEquals(-2L, removedOnly.wordDelta)
    }

    @Test
    fun countWords_handlesWhitespaceAndBlankInput() {
        assertEquals(0L, countWords(""))
        assertEquals(0L, countWords("   \n\t  "))
        assertEquals(6L, countWords("Write with purpose. Research with integrity."))
    }

    @Test
    fun chapter_completionProtocolRequiresAllSevenGates() {
        val partial = Chapter(
            title = "Foundations",
            blueprintApproved = true,
            fullProseDrafted = true,
            argumentsDeveloped = true,
            evidenceReviewed = false,
            redTeamCompleted = false,
            revisionsIncorporated = false,
            continuityChecked = false
        )
        assertEquals(3, partial.completionCriteriaMet)
        assertFalse(partial.passesFullCompletionProtocol)

        val complete = partial.copy(
            evidenceReviewed = true,
            redTeamCompleted = true,
            revisionsIncorporated = true,
            continuityChecked = true
        )
        assertEquals(7, complete.completionCriteriaMet)
        assertTrue(complete.passesFullCompletionProtocol)
    }

    @Test
    fun book_progressFractionClampsSafely() {
        assertEquals(0f, Book(wordCount = 500L, targetWordCount = 0L).progressFraction, 0.0001f)
        assertEquals(0.5f, Book(wordCount = 25000L, targetWordCount = 50000L).progressFraction, 0.0001f)
        assertEquals(1f, Book(wordCount = 75000L, targetWordCount = 50000L).progressFraction, 0.0001f)
    }

    @Test
    fun enums_fallbackSafelyOnUnknownValues() {
        assertEquals(EvidenceLevel.UNVERIFIED, EvidenceLevel.fromRaw("UNKNOWN"))
        assertEquals(BookStatus.CONCEPT, BookStatus.fromRaw(null))
        assertEquals(ChapterStatus.OUTLINE, ChapterStatus.fromRaw("INVALID"))
        assertEquals(CodexCategory.CONCEPT, CodexCategory.fromRaw(""))
        assertEquals(CodexOrigin.AUTHOR_APPROVED, CodexOrigin.fromRaw("OTHER"))
        assertEquals(ReviewType.MUHASABAH, ReviewType.fromRaw("OTHER"))
        assertEquals(DriveSyncStatus.LOCAL_ONLY, DriveSyncStatus.fromRaw("OTHER"))
        assertEquals(ExportFormat.PDF, ExportFormat.fromRaw("OTHER"))
    }

    @Test
    fun muhasabahFramework_containsTenQualitiesAndEightPipelineStages() {
        assertEquals(10, MuhasabahFramework.TEN_QUALITIES_OF_EVIL.size)
        assertEquals(8, MuhasabahFramework.PIPELINE_STAGES.size)
        assertEquals(1, MuhasabahFramework.PIPELINE_STAGES.first().first)
        assertEquals(8, MuhasabahFramework.PIPELINE_STAGES.last().first)
    }
}
