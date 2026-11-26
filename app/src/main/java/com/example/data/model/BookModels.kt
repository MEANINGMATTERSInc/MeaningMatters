package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class EvidenceLevel(val label: String, val guidance: String) {
    VERIFIED("VERIFIED", "Independently verified against primary records or reproducible proofs"),
    SUPPORTED("SUPPORTED", "Substantially backed by credible cited research or documentation"),
    INTERPRETATION("INTERPRETATION", "Reasoned analytical reading of primary or secondary material"),
    TRADITION("TRADITION", "Grounded in historical, philosophical, or cultural transmission"),
    HYPOTHESIS("HYPOTHESIS", "Testable working proposition requiring further empirical validation"),
    SPECULATION("SPECULATION", "Exploratory conjecture clearly bounded from established fact"),
    UNVERIFIED("UNVERIFIED", "Not yet independently verified; must not be stated as settled fact");

    companion object {
        fun fromRaw(raw: String?): EvidenceLevel =
            entries.firstOrNull { it.name == raw } ?: UNVERIFIED
    }
}

enum class BookStatus(val label: String) {
    CONCEPT("Concept"),
    OUTLINING("Outlining"),
    DRAFTING("Drafting"),
    REVIEWING("Reviewing"),
    READY("Ready to Publish"),
    PUBLISHED("Published");

    companion object {
        fun fromRaw(raw: String?): BookStatus =
            entries.firstOrNull { it.name == raw } ?: CONCEPT
    }
}

enum class ChapterStatus(val label: String) {
    OUTLINE("Outline"),
    DRAFT("Draft"),
    REVIEWED("Reviewed"),
    APPROVED("Approved");

    companion object {
        fun fromRaw(raw: String?): ChapterStatus =
            entries.firstOrNull { it.name == raw } ?: OUTLINE
    }
}

enum class CodexCategory(val label: String) {
    CONCEPT("Concept"),
    PERSON("Person"),
    PLACE("Place"),
    EVENT("Event"),
    HISTORICAL_PERIOD("Historical Period"),
    SCIENTIFIC_TOPIC("Scientific Topic"),
    CULTURAL_TRADITION("Cultural Tradition"),
    TERMINOLOGY("Terminology"),
    QUOTATION("Quotation"),
    RESEARCH_FINDING("Research Finding");

    companion object {
        fun fromRaw(raw: String?): CodexCategory =
            entries.firstOrNull { it.name == raw } ?: CONCEPT
    }
}

enum class CodexOrigin(val label: String) {
    AUTHOR_APPROVED("Author-Approved Fact"),
    RESEARCH_FINDING("Research Finding"),
    INTERPRETATION("Interpretation"),
    AI_SUGGESTION("AI Suggestion (Unapproved)");

    companion object {
        fun fromRaw(raw: String?): CodexOrigin =
            entries.firstOrNull { it.name == raw } ?: AUTHOR_APPROVED
    }
}

enum class ReviewType(val label: String) {
    MUHASABAH("Muhasabah Review"),
    RED_TEAM("RED_TEAM Adversarial"),
    CONSISTENCY("Book Consistency"),
    WHOLE_BOOK("Whole-Book Verification");

    companion object {
        fun fromRaw(raw: String?): ReviewType =
            entries.firstOrNull { it.name == raw } ?: MUHASABAH
    }
}

enum class DriveSyncStatus(val label: String) {
    LOCAL_ONLY("Cloud Firestore Only"),
    EXPORTED_TO_DRIVE("Exported to Drive / Docs"),
    NEEDS_RESYNC("Changes Pending Re-Export");

    companion object {
        fun fromRaw(raw: String?): DriveSyncStatus =
            entries.firstOrNull { it.name == raw } ?: LOCAL_ONLY
    }
}

enum class ExportFormat(val extension: String, val mimeType: String, val label: String) {
    PDF("pdf", "application/pdf", "PDF Manuscript"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "DOCX (Word & Google Docs)"),
    EPUB("epub", "application/epub+zip", "EPUB 3 eBook"),
    MARKDOWN("md", "text/markdown", "Markdown Source"),
    JSON("json", "application/json", "Full Project Backup (JSON)");

    companion object {
        fun fromRaw(raw: String?): ExportFormat =
            entries.firstOrNull { it.name == raw } ?: PDF
    }
}

object MuhasabahFramework {
    val TEN_QUALITIES_OF_EVIL = listOf(
        "Deception",
        "Cruelty",
        "Manipulation",
        "Lack of empathy",
        "Betrayal",
        "Hypocrisy",
        "Greed",
        "Arrogance",
        "Exploitation",
        "Absence of accountability"
    )

    val PIPELINE_STAGES = listOf(
        1 to "Initialize Canonical Ledger",
        2 to "Establish Complete Blueprint",
        3 to "Research & Evidence Prep",
        4 to "Complete Manuscript Drafting",
        5 to "RED_TEAM Adversarial Review",
        6 to "Targeted Revision",
        7 to "Whole-Book Verification",
        8 to "Final Manuscript Assembly"
    )
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val defaultPenName: String = "",
    val editorFontSizeSp: Long = 17L,
    val dyslexiaFriendlySpacing: Boolean = false,
    val focusModeDefault: Boolean = false,
    val autoSaveEnabled: Boolean = true,
    val preferredModel: String = "gemini-3.8-flash",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Book(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val subtitle: String = "",
    val genre: String = "",
    val targetAudience: String = "",
    val status: String = BookStatus.CONCEPT.name,
    val archived: Boolean = false,
    val wordCount: Long = 0L,
    val targetWordCount: Long = 50000L,
    val chapterCount: Long = 0L,
    val completedChapters: Long = 0L,
    val purposeApproved: Boolean = false,
    val ledgerApproved: Boolean = false,
    val pipelineStage: Long = 1L,
    val lastCompletedSection: String = "",
    val nextUnfinishedSection: String = "Stage 1: Initialize Canonical Ledger",
    val driveSyncStatus: String = DriveSyncStatus.LOCAL_ONLY.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val progressFraction: Float
        get() = if (targetWordCount <= 0L) 0f else (wordCount.toFloat() / targetWordCount.toFloat()).coerceIn(0f, 1f)
}

@Keep
@IgnoreExtraProperties
data class BookPurpose(
    @DocumentId val id: String = "",
    val userId: String = "",
    val bookId: String = "",
    val title: String = "",
    val subtitle: String = "",
    val genre: String = "",
    val intendedAudience: String = "",
    val centralPurpose: String = "",
    val mainThesis: String = "",
    val readerTransformation: String = "",
    val authorValues: String = "",
    val intellectualBoundaries: String = "",
    val desiredStyle: String = "",
    val targetLengthWords: Long = 50000L,
    val generatedDocument: String = "",
    val approved: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AuthorVoice(
    @DocumentId val id: String = "",
    val userId: String = "",
    val bookId: String = "",
    val writingTone: String = "Thoughtful, lucid, intellectually honest",
    val sentenceComplexity: String = "Balanced rhythm with clear transitions",
    val vocabularyLevel: String = "Accessible scholarly precision",
    val narrativePerspective: String = "First-person plural (editorial we)",
    val preferredTerms: List<String> = emptyList(),
    val avoidedTerms: List<String> = emptyList(),
    val writingSamples: String = "",
    val styleNotes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CanonicalLedger(
    @DocumentId val id: String = "",
    val userId: String = "",
    val bookId: String = "",
    val coreThesis: String = "",
    val purpose: String = "",
    val intendedAudience: String = "",
    val foundationalAxioms: List<String> = emptyList(),
    val epistemicStandards: String = "Never fabricate sources, citations, quotations, or empirical claims. Explicitly classify every claim as VERIFIED, SUPPORTED, INTERPRETATION, TRADITION, HYPOTHESIS, SPECULATION, or UNVERIFIED.",
    val formalDefinitions: List<String> = emptyList(),
    val mathematicalSpecs: String = "",
    val terminologicalGlossary: List<String> = emptyList(),
    val resolvedPremises: List<String> = emptyList(),
    val openQuestions: List<String> = emptyList(),
    val approved: Boolean = false,
    val version: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BookSection(
    @DocumentId val id: String = "",
    val userId: String = "",
    val bookId: String = "",
    val title: String = "",
    val description: String = "",
    val orderIndex: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Chapter(
    @DocumentId val id: String = "",
    val userId: String = "",
    val bookId: String = "",
    val sectionId: String = "",
    val partLabel: String = "",
    val title: String = "",
    val orderIndex: Long = 0L,
    val objective: String = "",
    val coreProposition: String = "",
    val argumentativePurpose: String = "",
    val requiredPremises: String = "",
    val requiredEvidence: String = "",
    val formalisms: String = "",
    val strongestCounterargument: String = "",
    val expectedResolution: String = "",
    val transitionToNext: String = "",
    val targetWordCount: Long = 3500L,
    val content: String = "",
    val wordCount: Long = 0L,
    val status: String = ChapterStatus.OUTLINE.name,
    val blueprintApproved: Boolean = false,
    val fullProseDrafted: Boolean = false,
    val argumentsDeveloped: Boolean = false,
    val evidenceReviewed: Boolean = false,
    val redTeamCompleted: Boolean = false,
    val revisionsIncorporated: Boolean = false,
    val continuityChecked: Boolean = false,
    val versionNumber: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val completionCriteriaMet: Int
        get() = listOf(
            blueprintApproved,
            fullProseDrafted,
            argumentsDeveloped,
            evidenceReviewed,
            redTeamCompleted,
            revisionsIncorporated,
            continuityChecked
        ).count { it }

    val passesFullCompletionProtocol: Boolean
        get() = completionCriteriaMet == 7
}

@Keep
@IgnoreExtraProperties
data class ManuscriptVersion(
    @DocumentId val id: String = "",
    val userId: String = "",
    val bookId: String = "",
    val chapterId: String = "",
    val versionNumber: Long = 1L,
    val label: String = "",
    val content: String = "",
    val wordCount: Long = 0L,
    val authorApproved: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class KnowledgeEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val bookId: String = "",
    val title: String = "",
    val category: String = CodexCategory.CONCEPT.name,
    val summary: String = "",
    val details: String = "",
    val epistemicStatus: String = EvidenceLevel.INTERPRETATION.name,
    val originType: String = CodexOrigin.AUTHOR_APPROVED.name,
    val relatedChapterIds: List<String> = emptyList(),
    val approved: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ResearchSource(
    @DocumentId val id: String = "",
    val userId: String = "",
    val bookId: String = "",
    val title: String = "",
    val author: String = "",
    val publicationDate: String = "",
    val publisher: String = "",
    val url: String = "",
    val sourceType: String = "Book",
    val notes: String = "",
    val evidenceLevel: String = EvidenceLevel.UNVERIFIED.name,
    val chapterId: String = "",
    val citationText: String = "",
    val verificationNote: String = "Registered by author. Not independently verified via external web lookup.",
    val attachmentName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ResearchClaim(
    @DocumentId val id: String = "",
    val userId: String = "",
    val bookId: String = "",
    val chapterId: String = "",
    val sourceId: String = "",
    val statement: String = "",
    val evidenceLevel: String = EvidenceLevel.HYPOTHESIS.name,
    val citationRef: String = "",
    val verified: Boolean = false,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ReviewFinding(
    val category: String = "",
    val severity: String = "MEDIUM",
    val chapterRef: String = "",
    val description: String = "",
    val recommendation: String = ""
)

@Keep
@IgnoreExtraProperties
data class ReviewReport(
    @DocumentId val id: String = "",
    val userId: String = "",
    val bookId: String = "",
    val chapterId: String = "",
    val reviewType: String = ReviewType.MUHASABAH.name,
    val summary: String = "",
    val overallScore: Long = 0L,
    val findings: List<ReviewFinding> = emptyList(),
    val qualitiesFlagged: List<String> = emptyList(),
    val unresolvedQuestions: List<String> = emptyList(),
    val authorAcknowledged: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AIInteraction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val bookId: String = "",
    val chapterId: String = "",
    val agentRole: String = "",
    val actionType: String = "",
    val modelName: String = "",
    val promptSummary: String = "",
    val outputSummary: String = "",
    val decision: String = "PENDING",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ExportJob(
    @DocumentId val id: String = "",
    val userId: String = "",
    val bookId: String = "",
    val format: String = ExportFormat.PDF.name,
    val fileName: String = "",
    val status: String = "COMPLETED",
    val validated: Boolean = false,
    val validationNotes: String = "",
    val byteSize: Long = 0L,
    val chapterCount: Long = 0L,
    val wordCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

fun countWords(text: String): Long {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return 0L
    return trimmed.split(Regex("\\s+")).size.toLong()
}
