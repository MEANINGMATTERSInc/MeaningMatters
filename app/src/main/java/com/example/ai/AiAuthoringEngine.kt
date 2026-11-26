package com.example.ai

import android.util.Log
import com.example.data.model.AuthorVoice
import com.example.data.model.BookPurpose
import com.example.data.model.CanonicalLedger
import com.example.data.model.Chapter
import com.example.data.model.KnowledgeEntry
import com.example.data.model.MuhasabahFramework
import com.example.data.model.ResearchSource
import com.example.data.model.ReviewFinding
import com.example.data.model.ReviewReport
import com.example.data.model.ReviewType
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class GeneratedBlueprintChapter(
    val partLabel: String,
    val title: String,
    val objective: String,
    val coreProposition: String,
    val argumentativePurpose: String,
    val requiredPremises: String,
    val requiredEvidence: String,
    val formalisms: String,
    val strongestCounterargument: String,
    val expectedResolution: String,
    val transitionToNext: String,
    val targetWordCount: Long
)

data class GeneratedLedgerResult(
    val coreThesis: String,
    val foundationalAxioms: List<String>,
    val formalDefinitions: List<String>,
    val mathematicalSpecs: String,
    val terminologicalGlossary: List<String>,
    val resolvedPremises: List<String>,
    val openQuestions: List<String>
)

data class GeneratedReviewResult(
    val summary: String,
    val overallScore: Long,
    val findings: List<ReviewFinding>,
    val qualitiesFlagged: List<String>,
    val unresolvedQuestions: List<String>
)

enum class CitationStyle(val label: String, val edition: String) {
    APA("APA", "APA 7th Edition"),
    MLA("MLA", "MLA 9th Edition"),
    CHICAGO("Chicago", "Chicago 17th Edition (Notes & Bibliography)"),
    HARVARD("Harvard", "Harvard Referencing"),
    IEEE("IEEE", "IEEE Citation Style")
}

data class FormattedCitationResult(
    val style: CitationStyle,
    val inTextParenthetical: String,
    val inTextNarrative: String,
    val bibliographyEntry: String,
    val integrityNote: String
)

enum class WritingAssistAction(val label: String, val agentRole: String, val instruction: String) {
    DRAFT_FULL(
        "Draft Chapter",
        "Chapter Drafter",
        "Draft complete, publication-grade prose for this chapter following the approved Chapter Blueprint and Canonical Ledger. Develop all required arguments, engage the strongest counterargument, preserve terminological invariance, and never fabricate citations or quotes."
    ),
    CONTINUE_DRAFT(
        "Continue Prose",
        "Chapter Drafter",
        "Continue directly from the end of the existing prose without repeating prior paragraphs. Maintain exact authorial voice, terminological invariance, and argumentative momentum toward the chapter's expected resolution."
    ),
    REWRITE_CLARITY(
        "Rewrite for Clarity",
        "Manuscript Editor",
        "Rewrite the passage to sharpen logical clarity, cadence, and precision while strictly preserving the author's approved meaning, thesis, and claims."
    ),
    EXPAND_ARGUMENT(
        "Expand & Deepen",
        "Philosophical Argument Analyst",
        "Expand the passage with deeper analytical reasoning, concrete nuance, and explicit handling of counterarguments without introducing fabricated facts or citations."
    ),
    SUMMARIZE(
        "Summarize Tightly",
        "Manuscript Editor",
        "Condense the passage into a dense, crisp synthesis preserving every core proposition and epistemic qualification."
    ),
    ALIGN_VOICE(
        "Align Author Voice",
        "Manuscript Editor",
        "Adjust tone, sentence complexity, vocabulary, and perspective to match the declared Author Voice Profile without altering the author's substantive claims."
    ),
    GRAMMAR_POLISH(
        "Grammar & Editorial Proof",
        "Manuscript Editor",
        "Perform a disciplined copy-edit for grammar, syntax, coherence, and rhetorical restraint while leaving the author's thesis intact."
    ),
    REVISE_FROM_REVIEW(
        "Incorporate Review Findings",
        "Manuscript Editor",
        "Revise the chapter prose to resolve the flagged RED_TEAM and Muhasabah findings, preserve valid arguments, temper unsupported certainty, and explicitly mark unresolved questions."
    )
}

/**
 * Model-independent AI authoring service abstraction so additional providers
 * can be integrated without coupling ViewModels or UI to a single SDK.
 */
interface AuthoringAiService {
    val activeModelName: String

    suspend fun generatePurposeDocument(purpose: BookPurpose): String

    suspend fun initializeCanonicalLedger(
        purpose: BookPurpose,
        voice: AuthorVoice?
    ): GeneratedLedgerResult

    suspend fun generateCompleteBlueprint(
        purpose: BookPurpose,
        ledger: CanonicalLedger?,
        existingApprovedChapters: List<Chapter>,
        desiredChapterCount: Int
    ): List<GeneratedBlueprintChapter>

    fun streamWritingAssistance(
        action: WritingAssistAction,
        chapter: Chapter,
        purpose: BookPurpose?,
        ledger: CanonicalLedger?,
        voice: AuthorVoice?,
        codex: List<KnowledgeEntry>,
        sources: List<ResearchSource>,
        customInstruction: String,
        latestReviewContext: String = ""
    ): Flow<String>

    suspend fun runMuhasabahReview(
        chapter: Chapter?,
        allChapters: List<Chapter>,
        purpose: BookPurpose?,
        ledger: CanonicalLedger?,
        sources: List<ResearchSource>
    ): GeneratedReviewResult

    suspend fun runRedTeamReview(
        chapter: Chapter,
        purpose: BookPurpose?,
        ledger: CanonicalLedger?,
        sources: List<ResearchSource>
    ): GeneratedReviewResult

    suspend fun runConsistencyOrWholeBookAudit(
        reviewType: ReviewType,
        chapters: List<Chapter>,
        purpose: BookPurpose?,
        ledger: CanonicalLedger?,
        codex: List<KnowledgeEntry>,
        sources: List<ResearchSource>
    ): GeneratedReviewResult

    suspend fun formatCitation(
        source: ResearchSource,
        style: CitationStyle,
        pageOrLocator: String = ""
    ): FormattedCitationResult

    suspend fun generateFormattedBibliography(
        sources: List<ResearchSource>,
        style: CitationStyle
    ): String
}

class FirebaseGeminiAuthoringService(
    override val activeModelName: String = "gemini-3.8-flash"
) : AuthoringAiService {

    companion object {
        private const val TAG = "MeaningMattersAI"
        private const val TIMEOUT_MS = 60_000L

        private const val GOVERNING_PREAMBLE = """
You are an authoring engine module inside MeaningMatters Book Writer.
Core principle: "Write with purpose. Research with integrity. Publish with accountability."
MANDATORY EPISTEMIC RULES:
1. Human Authority: Assist the author; never unilaterally alter the author's approved thesis or values.
2. Evidence Integrity: NEVER fabricate sources, citations, page numbers, quotations, statistics, historical events, or scientific studies. If evidence is missing, explicitly mark it [UNVERIFIED — REQUIRES AUTHOR SOURCE].
3. Epistemic Taxonomy: Clearly distinguish VERIFIED, SUPPORTED, INTERPRETATION, TRADITION, HYPOTHESIS, SPECULATION, and UNVERIFIED.
4. No Harm / Ethical Restraint: Examine patterns in ideas, institutions, or arguments — NEVER label real living individuals as evil.
"""
    }

    private val textModel by lazy {
        Firebase.ai.generativeModel(modelName = activeModelName)
    }

    private val ledgerModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "coreThesis" to Schema.string("Refined authoritative thesis statement"),
                "foundationalAxioms" to Schema.array(Schema.string(), "5-8 foundational axioms governing the book"),
                "formalDefinitions" to Schema.array(Schema.string(), "6-10 formal definitions in Term: Definition format"),
                "mathematicalSpecs" to Schema.string("Formal or structural specifications/notation if applicable, or concise logical schema"),
                "terminologicalGlossary" to Schema.array(Schema.string(), "8-12 invariant glossary terms in Term — Definition format"),
                "resolvedPremises" to Schema.array(Schema.string(), "4-6 resolved foundational premises"),
                "openQuestions" to Schema.array(Schema.string(), "4-6 honest unresolved questions to track")
            )
        )
        Firebase.ai.generativeModel(
            modelName = activeModelName,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    private val blueprintModel by lazy {
        val chapterSchema = Schema.obj(
            properties = mapOf(
                "partLabel" to Schema.string("Part title e.g. Part I: Foundations"),
                "title" to Schema.string("Chapter title"),
                "objective" to Schema.string("Clear chapter objective"),
                "coreProposition" to Schema.string("Central proposition defended in this chapter"),
                "argumentativePurpose" to Schema.string("How this chapter advances the cumulative book thesis"),
                "requiredPremises" to Schema.string("Prior premises or axioms required"),
                "requiredEvidence" to Schema.string("Evidence or empirical/textual verification required (do not fabricate citations)"),
                "formalisms" to Schema.string("Formal notation, definitions, or conceptual models used"),
                "strongestCounterargument" to Schema.string("Steel-manned strongest objection"),
                "expectedResolution" to Schema.string("Rigorous resolution or honest scope boundary"),
                "transitionToNext" to Schema.string("Logical bridge to the next chapter"),
                "targetWordCount" to Schema.integer("Target word count for this chapter")
            )
        )
        val rootSchema = Schema.obj(
            properties = mapOf(
                "chapters" to Schema.array(chapterSchema, "Sequential chapters forming a cumulative book architecture")
            )
        )
        Firebase.ai.generativeModel(
            modelName = activeModelName,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = rootSchema
            }
        )
    }

    private val reviewModel by lazy {
        val findingSchema = Schema.obj(
            properties = mapOf(
                "category" to Schema.string("Issue category e.g. Unsupported Claim, Contradiction, Terminological Drift"),
                "severity" to Schema.enumeration(listOf("LOW", "MEDIUM", "HIGH"), "Severity level"),
                "chapterRef" to Schema.string("Chapter or section reference"),
                "description" to Schema.string("Specific, evidence-based description of the issue"),
                "recommendation" to Schema.string("Actionable, author-respecting editorial recommendation")
            )
        )
        val rootSchema = Schema.obj(
            properties = mapOf(
                "summary" to Schema.string("Concise, honest executive review summary"),
                "overallScore" to Schema.integer("Integer integrity/rigor score from 0 to 100"),
                "findings" to Schema.array(findingSchema, "Actionable review findings"),
                "qualitiesFlagged" to Schema.array(Schema.string(), "Any of the 10 Qualities of Evil patterns detected in arguments/framing (empty if none)"),
                "unresolvedQuestions" to Schema.array(Schema.string(), "Honest open questions that cannot yet be settled")
            )
        )
        Firebase.ai.generativeModel(
            modelName = activeModelName,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = rootSchema
            }
        )
    }

    private val citationModel by lazy {
        val schema = Schema.obj(
            properties = mapOf(
                "inTextParenthetical" to Schema.string("Formatted parenthetical in-text citation or footnote marker"),
                "inTextNarrative" to Schema.string("Formatted narrative in-text citation"),
                "bibliographyEntry" to Schema.string("Complete bibliography/reference list entry following the requested style"),
                "integrityNote" to Schema.string("Note flagging any missing bibliographic fields without fabricating them")
            )
        )
        Firebase.ai.generativeModel(
            modelName = activeModelName,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
    }

    override suspend fun generatePurposeDocument(purpose: BookPurpose): String {
        val prompt = buildString {
            appendLine(GOVERNING_PREAMBLE)
            appendLine("AGENT ROLE: Architectural Author — Book Purpose Engine.")
            appendLine("Generate a structured, publication-grade Book Purpose Document in clean Markdown.")
            appendLine("Include sections: 1. Purpose & Moral Horizon, 2. Core Thesis & Epistemic Scope, 3. Reader Transformation, 4. Authorial Values & Intellectual Boundaries, 5. Aesthetic & Stylistic Covenant, 6. Accountability Checklist.")
            appendLine("INPUTS:")
            appendLine("Title: ${purpose.title}")
            appendLine("Subtitle: ${purpose.subtitle}")
            appendLine("Genre: ${purpose.genre}")
            appendLine("Intended Audience: ${purpose.intendedAudience}")
            appendLine("Central Purpose: ${purpose.centralPurpose}")
            appendLine("Main Thesis: ${purpose.mainThesis}")
            appendLine("Reader Transformation: ${purpose.readerTransformation}")
            appendLine("Author's Values: ${purpose.authorValues}")
            appendLine("Intellectual Boundaries: ${purpose.intellectualBoundaries}")
            appendLine("Desired Style: ${purpose.desiredStyle}")
            appendLine("Target Length: ${purpose.targetLengthWords} words")
        }
        return executeUnaryText(prompt, "Book Purpose Document")
    }

    override suspend fun initializeCanonicalLedger(
        purpose: BookPurpose,
        voice: AuthorVoice?
    ): GeneratedLedgerResult {
        val prompt = buildString {
            appendLine(GOVERNING_PREAMBLE)
            appendLine("AGENT ROLE: Architectural Author & Formal Systems Analyst — Stage 1: Initialize Canonical Ledger.")
            appendLine("Construct the authoritative Canonical Ledger that will govern every chapter and AI agent.")
            appendLine("Title: ${purpose.title} — ${purpose.subtitle}")
            appendLine("Genre: ${purpose.genre} | Audience: ${purpose.intendedAudience}")
            appendLine("Central Purpose: ${purpose.centralPurpose}")
            appendLine("Main Thesis: ${purpose.mainThesis}")
            appendLine("Values: ${purpose.authorValues}")
            appendLine("Intellectual Boundaries: ${purpose.intellectualBoundaries}")
            if (voice != null) {
                appendLine("Preferred Terms: ${voice.preferredTerms.joinToString(", ")}")
                appendLine("Avoided Terms: ${voice.avoidedTerms.joinToString(", ")}")
            }
        }
        val jsonText = executeStructuredJson(ledgerModel, prompt, "Canonical Ledger")
        val obj = JSONObject(jsonText)
        return GeneratedLedgerResult(
            coreThesis = obj.optString("coreThesis", purpose.mainThesis),
            foundationalAxioms = obj.optStringList("foundationalAxioms"),
            formalDefinitions = obj.optStringList("formalDefinitions"),
            mathematicalSpecs = obj.optString("mathematicalSpecs", ""),
            terminologicalGlossary = obj.optStringList("terminologicalGlossary"),
            resolvedPremises = obj.optStringList("resolvedPremises"),
            openQuestions = obj.optStringList("openQuestions")
        )
    }

    override suspend fun generateCompleteBlueprint(
        purpose: BookPurpose,
        ledger: CanonicalLedger?,
        existingApprovedChapters: List<Chapter>,
        desiredChapterCount: Int
    ): List<GeneratedBlueprintChapter> {
        val count = desiredChapterCount.coerceIn(3, 18)
        val prompt = buildString {
            appendLine(GOVERNING_PREAMBLE)
            appendLine("AGENT ROLE: Architectural Author — Stage 2: Establish Complete Book Blueprint.")
            appendLine("Generate exactly $count cumulative chapters organized into logical Parts.")
            appendLine("Chapters must form ONE cumulative intellectual argument, not disconnected essays.")
            if (existingApprovedChapters.isNotEmpty()) {
                appendLine("IMPORTANT: The author has ALREADY APPROVED these chapters; build around and complement them without contradicting them:")
                existingApprovedChapters.forEach {
                    appendLine("- Approved Chapter: ${it.title} | Proposition: ${it.coreProposition}")
                }
            }
            appendLine("Book: ${purpose.title} (${purpose.subtitle})")
            appendLine("Purpose: ${purpose.centralPurpose}")
            appendLine("Main Thesis: ${purpose.mainThesis}")
            appendLine("Target Book Word Count: ${purpose.targetLengthWords}")
            if (ledger != null) {
                appendLine("Canonical Axioms: ${ledger.foundationalAxioms.joinToString(" | ")}")
                appendLine("Canonical Definitions: ${ledger.formalDefinitions.joinToString(" | ")}")
            }
        }
        val jsonText = executeStructuredJson(blueprintModel, prompt, "Book Blueprint")
        val obj = JSONObject(jsonText)
        val arr = obj.optJSONArray("chapters") ?: return emptyList()
        val results = mutableListOf<GeneratedBlueprintChapter>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            results.add(
                GeneratedBlueprintChapter(
                    partLabel = item.optString("partLabel", "Part I"),
                    title = item.optString("title", "Chapter ${i + 1}"),
                    objective = item.optString("objective", ""),
                    coreProposition = item.optString("coreProposition", ""),
                    argumentativePurpose = item.optString("argumentativePurpose", ""),
                    requiredPremises = item.optString("requiredPremises", ""),
                    requiredEvidence = item.optString("requiredEvidence", ""),
                    formalisms = item.optString("formalisms", ""),
                    strongestCounterargument = item.optString("strongestCounterargument", ""),
                    expectedResolution = item.optString("expectedResolution", ""),
                    transitionToNext = item.optString("transitionToNext", ""),
                    targetWordCount = item.optLong("targetWordCount", (purpose.targetLengthWords / count).coerceAtLeast(1500L))
                )
            )
        }
        return results
    }

    override fun streamWritingAssistance(
        action: WritingAssistAction,
        chapter: Chapter,
        purpose: BookPurpose?,
        ledger: CanonicalLedger?,
        voice: AuthorVoice?,
        codex: List<KnowledgeEntry>,
        sources: List<ResearchSource>,
        customInstruction: String,
        latestReviewContext: String
    ): Flow<String> = flow {
        val prompt = buildString {
            appendLine(GOVERNING_PREAMBLE)
            appendLine("AGENT ROLE: ${action.agentRole}")
            appendLine("TASK: ${action.instruction}")
            if (customInstruction.isNotBlank()) {
                appendLine("AUTHOR DIRECTIVE: $customInstruction")
            }
            if (purpose != null) {
                appendLine("\n--- BOOK PURPOSE ---")
                appendLine("Title: ${purpose.title} | Thesis: ${purpose.mainThesis}")
                appendLine("Purpose: ${purpose.centralPurpose}")
                appendLine("Boundaries: ${purpose.intellectualBoundaries}")
            }
            if (ledger != null) {
                appendLine("\n--- CANONICAL LEDGER (AUTHORITATIVE — DO NOT CONTRADICT) ---")
                appendLine("Core Thesis: ${ledger.coreThesis}")
                if (ledger.foundationalAxioms.isNotEmpty()) {
                    appendLine("Axioms: ${ledger.foundationalAxioms.joinToString("; ")}")
                }
                if (ledger.formalDefinitions.isNotEmpty()) {
                    appendLine("Definitions: ${ledger.formalDefinitions.take(15).joinToString("; ")}")
                }
                if (ledger.terminologicalGlossary.isNotEmpty()) {
                    appendLine("Glossary: ${ledger.terminologicalGlossary.take(15).joinToString("; ")}")
                }
            }
            if (voice != null) {
                appendLine("\n--- AUTHOR VOICE PROFILE (NEVER IMITATE LIVING AUTHORS; FOLLOW STYLE TRAITS) ---")
                appendLine("Tone: ${voice.writingTone}")
                appendLine("Sentence Complexity: ${voice.sentenceComplexity}")
                appendLine("Vocabulary: ${voice.vocabularyLevel}")
                appendLine("Perspective: ${voice.narrativePerspective}")
                if (voice.preferredTerms.isNotEmpty()) appendLine("Prefer terms: ${voice.preferredTerms.joinToString(", ")}")
                if (voice.avoidedTerms.isNotEmpty()) appendLine("Avoid terms: ${voice.avoidedTerms.joinToString(", ")}")
                if (voice.writingSamples.isNotBlank()) appendLine("Author sample excerpt:\n${voice.writingSamples.take(1200)}")
            }
            if (codex.isNotEmpty()) {
                appendLine("\n--- PERSISTENT KNOWLEDGE CODEX (RELEVANT ENTRIES) ---")
                codex.take(12).forEach {
                    appendLine("- ${it.title} [${it.category} / ${it.epistemicStatus} / ${it.originType}]: ${it.summary}")
                }
            }
            if (sources.isNotEmpty()) {
                appendLine("\n--- REGISTERED RESEARCH SOURCES (ONLY CITE FROM THIS LIST; NEVER INVENT OTHERS) ---")
                sources.take(12).forEach {
                    appendLine("- ${it.title} by ${it.author} (${it.publicationDate}) [${it.evidenceLevel}] | Citation: ${it.citationText.ifBlank { "${it.author}, ${it.title}" }}")
                }
            } else {
                appendLine("\nNo verified bibliography sources registered yet. Do NOT invent citations; tag any empirical claim needing a citation with [UNVERIFIED — SOURCE NEEDED].")
            }
            appendLine("\n--- CHAPTER BLUEPRINT ---")
            appendLine("Part: ${chapter.partLabel} | Chapter: ${chapter.title}")
            appendLine("Objective: ${chapter.objective}")
            appendLine("Core Proposition: ${chapter.coreProposition}")
            appendLine("Required Premises: ${chapter.requiredPremises}")
            appendLine("Required Evidence: ${chapter.requiredEvidence}")
            appendLine("Strongest Counterargument: ${chapter.strongestCounterargument}")
            appendLine("Expected Resolution: ${chapter.expectedResolution}")
            appendLine("Transition to Next: ${chapter.transitionToNext}")
            if (latestReviewContext.isNotBlank()) {
                appendLine("\n--- REVIEW FINDINGS TO ADDRESS ---")
                appendLine(latestReviewContext.take(4000))
            }
            if (chapter.content.isNotBlank()) {
                appendLine("\n--- CURRENT CHAPTER MANUSCRIPT ---")
                appendLine(chapter.content.take(18000))
            }
            appendLine("\nOutput clean publication-ready prose with clear section subheadings (using ##). Do not wrap in code fences or include meta-commentary.")
        }

        val accumulated = StringBuilder()
        try {
            withTimeout(TIMEOUT_MS) {
                textModel.generateContentStream(prompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("Gemini returned an empty response.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Writing stream timed out", e)
            throw IllegalStateException("AI writing request timed out after 60 seconds. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Writing stream failed", e)
            throw e
        }
    }

    override suspend fun runMuhasabahReview(
        chapter: Chapter?,
        allChapters: List<Chapter>,
        purpose: BookPurpose?,
        ledger: CanonicalLedger?,
        sources: List<ResearchSource>
    ): GeneratedReviewResult {
        val targetText = if (chapter != null) {
            "Chapter: ${chapter.title}\n${chapter.content.ifBlank { chapter.objective }.take(16000)}"
        } else {
            allChapters.joinToString("\n\n") { "### ${it.title}\n${it.content.take(3000)}" }.take(20000)
        }
        val prompt = buildString {
            appendLine(GOVERNING_PREAMBLE)
            appendLine("AGENT ROLE: Muhasabah Reviewer — Reflection, Accountability, and Harm Prevention.")
            appendLine("Review the manuscript for: unsupported claims, internal contradictions, repeated arguments, misleading certainty, cultural misrepresentation, ethical concerns, manipulative framing, unclear reasoning, and inconsistency with the book's declared purpose.")
            appendLine("Apply the 10 Qualities of Evil ONLY as a reflective risk-detection lens on arguments, institutions, fictional characters, or documented patterns: ${MuhasabahFramework.TEN_QUALITIES_OF_EVIL.joinToString(", ")}.")
            appendLine("STRICT ETHICAL GUARD: Never label real people as evil. Distinguish honest mistakes from persistent harmful patterns. Evaluate evidence, intent, harm, accountability, and willingness to change.")
            if (purpose != null) {
                appendLine("Declared Purpose: ${purpose.centralPurpose}")
                appendLine("Author Values: ${purpose.authorValues}")
                appendLine("Boundaries: ${purpose.intellectualBoundaries}")
            }
            if (ledger != null) {
                appendLine("Canonical Thesis: ${ledger.coreThesis}")
            }
            appendLine("Registered Sources Count: ${sources.size}")
            appendLine("\nMANUSCRIPT UNDER REVIEW:\n$targetText")
        }
        return parseReviewJson(executeStructuredJson(reviewModel, prompt, "Muhasabah Review"))
    }

    override suspend fun runRedTeamReview(
        chapter: Chapter,
        purpose: BookPurpose?,
        ledger: CanonicalLedger?,
        sources: List<ResearchSource>
    ): GeneratedReviewResult {
        val prompt = buildString {
            appendLine(GOVERNING_PREAMBLE)
            appendLine("AGENT ROLE: Adversarial Reviewer — Stage 5 RED_TEAM Chapter Review.")
            appendLine("Rigorously audit this chapter for: unstated assumptions, invalid inferences, unsupported claims, domain conflations, contradictions with the Canonical Ledger, weak or straw-manned counterarguments, mathematical/formal inconsistencies, rhetorical filler, and terminological drift.")
            if (purpose != null) appendLine("Book Thesis: ${purpose.mainThesis}")
            if (ledger != null) {
                appendLine("Canonical Axioms: ${ledger.foundationalAxioms.joinToString("; ")}")
                appendLine("Canonical Definitions: ${ledger.formalDefinitions.joinToString("; ")}")
                appendLine("Mathematical Specs: ${ledger.mathematicalSpecs}")
            }
            appendLine("Chapter Title: ${chapter.title}")
            appendLine("Core Proposition: ${chapter.coreProposition}")
            appendLine("Strongest Counterargument Planned: ${chapter.strongestCounterargument}")
            appendLine("Registered Sources: ${sources.joinToString("; ") { "${it.title} [${it.evidenceLevel}]" }}")
            appendLine("\nCHAPTER CONTENT:\n${chapter.content.ifBlank { chapter.objective }.take(18000)}")
        }
        return parseReviewJson(executeStructuredJson(reviewModel, prompt, "RED_TEAM Review"))
    }

    override suspend fun runConsistencyOrWholeBookAudit(
        reviewType: ReviewType,
        chapters: List<Chapter>,
        purpose: BookPurpose?,
        ledger: CanonicalLedger?,
        codex: List<KnowledgeEntry>,
        sources: List<ResearchSource>
    ): GeneratedReviewResult {
        val roleTitle = if (reviewType == ReviewType.WHOLE_BOOK) {
            "Continuity Auditor — Stage 7 Whole-Book Verification"
        } else {
            "Continuity Auditor — Book Consistency Engine"
        }
        val prompt = buildString {
            appendLine(GOVERNING_PREAMBLE)
            appendLine("AGENT ROLE: $roleTitle.")
            appendLine("Audit the entire book for: contradictory statements across chapters, inconsistent terminology, conflicting definitions, repeated content, chapter sequencing problems, missing arguments, unresolved research questions, and whether earlier premises truly support later conclusions.")
            if (purpose != null) appendLine("Declared Thesis: ${purpose.mainThesis}")
            if (ledger != null) {
                appendLine("Canonical Thesis: ${ledger.coreThesis}")
                appendLine("Axioms: ${ledger.foundationalAxioms.joinToString("; ")}")
                appendLine("Glossary: ${ledger.terminologicalGlossary.joinToString("; ")}")
                appendLine("Open Questions: ${ledger.openQuestions.joinToString("; ")}")
            }
            if (codex.isNotEmpty()) {
                appendLine("Codex Entries: ${codex.take(20).joinToString("; ") { "${it.title} (${it.epistemicStatus}): ${it.summary.take(120)}" }}")
            }
            appendLine("Registered Sources: ${sources.size}")
            appendLine("\nCHAPTERS (${chapters.size}):")
            chapters.forEachIndexed { idx, ch ->
                appendLine("Chapter ${idx + 1}: ${ch.title} [Status=${ch.status}, Words=${ch.wordCount}]")
                appendLine("  Proposition: ${ch.coreProposition}")
                appendLine("  Excerpt: ${ch.content.take(1600)}")
            }
        }
        return parseReviewJson(executeStructuredJson(reviewModel, prompt, roleTitle))
    }

    override suspend fun formatCitation(
        source: ResearchSource,
        style: CitationStyle,
        pageOrLocator: String
    ): FormattedCitationResult {
        val prompt = buildString {
            appendLine(GOVERNING_PREAMBLE)
            appendLine("AGENT ROLE: Researcher — Citation Formatting Engine.")
            appendLine("Format the following author-supplied source STRICTLY in ${style.edition}.")
            appendLine("CRITICAL INTEGRITY RULE: NEVER fabricate missing details (such as DOI, volume, issue, city, publisher, page numbers, or year). If a field is blank, omit it per ${style.label} rules or use standard placeholders like (n.d.) and mention the missing field in integrityNote.")
            appendLine("Source Type: ${source.sourceType}")
            appendLine("Title: ${source.title}")
            appendLine("Author(s): ${source.author.ifBlank { "[Not provided]" }}")
            appendLine("Publication Date: ${source.publicationDate.ifBlank { "[Not provided]" }}")
            appendLine("Publisher / Journal: ${source.publisher.ifBlank { "[Not provided]" }}")
            appendLine("URL / DOI: ${source.url.ifBlank { "[Not provided]" }}")
            if (pageOrLocator.isNotBlank()) appendLine("Page / Locator: $pageOrLocator")
            appendLine("Evidence Level: ${source.evidenceLevel}")
        }
        val raw = executeStructuredJson(citationModel, prompt, "${style.label} Citation")
        val obj = JSONObject(raw)
        return FormattedCitationResult(
            style = style,
            inTextParenthetical = obj.optString("inTextParenthetical", ""),
            inTextNarrative = obj.optString("inTextNarrative", ""),
            bibliographyEntry = obj.optString("bibliographyEntry", ""),
            integrityNote = obj.optString("integrityNote", "Formatted from author-supplied metadata only.")
        )
    }

    override suspend fun generateFormattedBibliography(
        sources: List<ResearchSource>,
        style: CitationStyle
    ): String {
        val prompt = buildString {
            appendLine(GOVERNING_PREAMBLE)
            appendLine("AGENT ROLE: Researcher — Complete Bibliography Formatter.")
            appendLine("Compile a clean, properly sorted bibliography in ${style.edition} using ONLY the sources listed below.")
            appendLine("NEVER add sources not listed below and NEVER fabricate missing bibliographic metadata. Preserve epistemic honesty.")
            sources.forEachIndexed { idx, s ->
                appendLine("${idx + 1}. Title: ${s.title} | Author: ${s.author} | Date: ${s.publicationDate} | Publisher: ${s.publisher} | Type: ${s.sourceType} | URL: ${s.url} | Evidence: ${s.evidenceLevel}")
            }
            appendLine("\nReturn only the clean formatted bibliography list (one entry per blank-line-separated paragraph) with a brief header for ${style.edition}.")
        }
        return executeUnaryText(prompt, "${style.label} Bibliography")
    }

    private suspend fun executeUnaryText(prompt: String, label: String): String {
        return try {
            val response = withTimeout(TIMEOUT_MS) {
                textModel.generateContent(prompt)
            }
            response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("$label generation returned empty output.")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "$label timed out", e)
            throw IllegalStateException("$label timed out after 60 seconds. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "$label failed", e)
            throw e
        }
    }

    private suspend fun executeStructuredJson(
        model: com.google.firebase.ai.GenerativeModel,
        prompt: String,
        label: String
    ): String {
        return try {
            val response = withTimeout(TIMEOUT_MS) {
                model.generateContent(prompt)
            }
            response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("$label returned empty JSON.")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "$label timed out", e)
            throw IllegalStateException("$label timed out after 60 seconds. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "$label failed", e)
            throw e
        }
    }

    private fun parseReviewJson(rawJson: String): GeneratedReviewResult {
        val obj = JSONObject(rawJson)
        val findingsArr = obj.optJSONArray("findings")
        val findings = mutableListOf<ReviewFinding>()
        if (findingsArr != null) {
            for (i in 0 until findingsArr.length()) {
                val f = findingsArr.optJSONObject(i) ?: continue
                findings.add(
                    ReviewFinding(
                        category = f.optString("category", "General"),
                        severity = f.optString("severity", "MEDIUM"),
                        chapterRef = f.optString("chapterRef", "Manuscript"),
                        description = f.optString("description", ""),
                        recommendation = f.optString("recommendation", "")
                    )
                )
            }
        }
        return GeneratedReviewResult(
            summary = obj.optString("summary", "Review completed."),
            overallScore = obj.optLong("overallScore", 75L).coerceIn(0L, 100L),
            findings = findings,
            qualitiesFlagged = obj.optStringList("qualitiesFlagged"),
            unresolvedQuestions = obj.optStringList("unresolvedQuestions")
        )
    }

    private fun JSONObject.optStringList(key: String): List<String> {
        val arr = optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { idx ->
            arr.optString(idx, "").trim().takeIf { it.isNotEmpty() }
        }
    }
}
