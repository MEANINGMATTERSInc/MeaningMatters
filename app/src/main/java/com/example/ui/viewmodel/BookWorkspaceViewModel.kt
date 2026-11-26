package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AuthoringAiService
import com.example.ai.CitationStyle
import com.example.ai.FormattedCitationResult
import com.example.ai.WritingAssistAction
import com.example.core.unwrapFirestoreFlowError
import com.example.data.model.AIInteraction
import com.example.data.model.AuthorVoice
import com.example.data.model.Book
import com.example.data.model.BookPurpose
import com.example.data.model.BookSection
import com.example.data.model.CanonicalLedger
import com.example.data.model.Chapter
import com.example.data.model.ChapterStatus
import com.example.data.model.DriveSyncStatus
import com.example.data.model.ExportFormat
import com.example.data.model.ExportJob
import com.example.data.model.KnowledgeEntry
import com.example.data.model.ManuscriptVersion
import com.example.data.model.ResearchClaim
import com.example.data.model.ResearchSource
import com.example.data.model.ReviewReport
import com.example.data.model.ReviewType
import com.example.data.model.countWords
import com.example.data.repository.BookRepository
import com.example.data.repository.KnowledgeAndGovernanceRepository
import com.example.export.ExportResult
import com.example.export.ManuscriptBundle
import com.example.export.PublicationExporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AiTaskState {
    data object Idle : AiTaskState
    data class Running(val label: String) : AiTaskState
    data class Streaming(val label: String, val accumulatedText: String) : AiTaskState
    data class SuggestionReady(
        val action: WritingAssistAction,
        val chapterId: String,
        val interactionId: String,
        val suggestedText: String
    ) : AiTaskState
    data class Error(val message: String) : AiTaskState
}

@OptIn(ExperimentalCoroutinesApi::class)
class BookWorkspaceViewModel(
    private val bookRepository: BookRepository,
    private val knowledgeRepository: KnowledgeAndGovernanceRepository,
    private val aiService: AuthoringAiService,
    private val exporter: PublicationExporter,
    private val currentUserId: String,
    val bookId: String,
    private val authorDisplayName: String
) : ViewModel() {

    val book: StateFlow<Book?> = bookRepository.observeBook(currentUserId, bookId)
        .catch { e -> handleStreamError("book", e); emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val purpose: StateFlow<BookPurpose?> = bookRepository.observeBookPurpose(currentUserId, bookId)
        .catch { e -> handleStreamError("purpose", e); emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val voice: StateFlow<AuthorVoice?> = bookRepository.observeAuthorVoice(currentUserId, bookId)
        .catch { e -> handleStreamError("voice", e); emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val ledger: StateFlow<CanonicalLedger?> = bookRepository.observeCanonicalLedger(currentUserId, bookId)
        .catch { e -> handleStreamError("ledger", e); emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val sections: StateFlow<List<BookSection>> = bookRepository.observeSections(currentUserId, bookId)
        .catch { e -> handleStreamError("sections", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val chapters: StateFlow<List<Chapter>> = bookRepository.observeChapters(currentUserId, bookId)
        .catch { e -> handleStreamError("chapters", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val codex: StateFlow<List<KnowledgeEntry>> = knowledgeRepository.observeCodex(currentUserId, bookId)
        .catch { e -> handleStreamError("codex", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val sources: StateFlow<List<ResearchSource>> = knowledgeRepository.observeSources(currentUserId, bookId)
        .catch { e -> handleStreamError("sources", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val claims: StateFlow<List<ResearchClaim>> = knowledgeRepository.observeClaims(currentUserId, bookId)
        .catch { e -> handleStreamError("claims", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val reviews: StateFlow<List<ReviewReport>> = knowledgeRepository.observeReviews(currentUserId, bookId)
        .catch { e -> handleStreamError("reviews", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val aiLogs: StateFlow<List<AIInteraction>> = knowledgeRepository.observeAiLogs(currentUserId, bookId)
        .catch { e -> handleStreamError("aiLogs", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val exports: StateFlow<List<ExportJob>> = knowledgeRepository.observeExports(currentUserId, bookId)
        .catch { e -> handleStreamError("exports", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _selectedChapterId = MutableStateFlow<String?>(null)
    val selectedChapterId: StateFlow<String?> = _selectedChapterId.asStateFlow()

    val chapterVersions: StateFlow<List<ManuscriptVersion>> = _selectedChapterId
        .flatMapLatest { chId ->
            if (chId.isNullOrBlank()) flowOf(emptyList())
            else bookRepository.observeVersions(currentUserId, bookId, chId)
                .catch { e -> handleStreamError("versions", e); emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _aiState = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val aiState: StateFlow<AiTaskState> = _aiState.asStateFlow()

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

    private val _saveIndicator = MutableStateFlow("Saved to Cloud")
    val saveIndicator: StateFlow<String> = _saveIndicator.asStateFlow()

    private val _citationResult = MutableStateFlow<FormattedCitationResult?>(null)
    val citationResult: StateFlow<FormattedCitationResult?> = _citationResult.asStateFlow()

    private val _formattedBibliography = MutableStateFlow<String?>(null)
    val formattedBibliography: StateFlow<String?> = _formattedBibliography.asStateFlow()

    private val _lastExportResult = MutableStateFlow<ExportResult?>(null)
    val lastExportResult: StateFlow<ExportResult?> = _lastExportResult.asStateFlow()

    private val _pipelineActive = MutableStateFlow(false)
    val pipelineActive: StateFlow<Boolean> = _pipelineActive.asStateFlow()

    private var activeAiJob: Job? = null

    private fun handleStreamError(label: String, error: Throwable) {
        val root = unwrapFirestoreFlowError(error) ?: throw error
        Log.w(TAG, "Stream error ($label)", root)
        _snackbar.value = "Sync warning ($label): ${root.localizedMessage ?: "connection error"}"
    }

    fun clearSnackbar() {
        _snackbar.value = null
    }

    fun clearAiError() {
        if (_aiState.value is AiTaskState.Error) _aiState.value = AiTaskState.Idle
    }

    fun selectChapter(chapterId: String) {
        _selectedChapterId.value = chapterId
    }

    // --- MODULE B: PURPOSE & CANONICAL LEDGER ---
    fun savePurpose(updated: BookPurpose) {
        viewModelScope.launch {
            _saveIndicator.value = "Saving…"
            val res = bookRepository.saveBookPurpose(currentUserId, bookId, updated)
            _saveIndicator.value = if (res.isSuccess) "Saved to Cloud" else "Save failed"
            _snackbar.value = if (res.isSuccess) {
                if (updated.approved) "Book Purpose Document approved & saved." else "Book Purpose saved."
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to save purpose."
            }
        }
    }

    fun generatePurposeDocument() {
        val current = purpose.value ?: return
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _aiState.value = AiTaskState.Running("Drafting Book Purpose Document…")
            try {
                val doc = aiService.generatePurposeDocument(current)
                bookRepository.saveBookPurpose(currentUserId, bookId, current.copy(generatedDocument = doc, approved = false))
                knowledgeRepository.logAiInteraction(
                    currentUserId, bookId,
                    AIInteraction(
                        agentRole = "Architectural Author",
                        actionType = "GENERATE_PURPOSE_DOC",
                        modelName = aiService.activeModelName,
                        promptSummary = "Generate structured Book Purpose Document for ${current.title}",
                        outputSummary = doc.take(600),
                        decision = "PENDING"
                    )
                )
                _aiState.value = AiTaskState.Idle
                _snackbar.value = "Purpose Document generated. Review and approve before outlining."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiState.value = AiTaskState.Error(e.localizedMessage ?: "Purpose generation failed.")
            }
        }
    }

    fun saveCanonicalLedger(updated: CanonicalLedger) {
        viewModelScope.launch {
            _saveIndicator.value = "Saving…"
            val res = bookRepository.saveCanonicalLedger(currentUserId, bookId, updated)
            if (res.isSuccess && updated.approved) {
                bookRepository.updateBookMetadata(
                    currentUserId, bookId,
                    mapOf(
                        "pipelineStage" to maxOf(book.value?.pipelineStage ?: 1L, 2L),
                        "lastCompletedSection" to "Stage 1: Canonical Ledger v${updated.version}",
                        "nextUnfinishedSection" to "Stage 2: Establish Complete Blueprint"
                    )
                )
            }
            _saveIndicator.value = if (res.isSuccess) "Saved to Cloud" else "Save failed"
            _snackbar.value = if (res.isSuccess) "Canonical Ledger saved." else (res.exceptionOrNull()?.localizedMessage ?: "Failed to save ledger.")
        }
    }

    fun initializeCanonicalLedgerWithAi() {
        val currentPurpose = purpose.value ?: return
        val currentLedger = ledger.value ?: CanonicalLedger(bookId = bookId)
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _aiState.value = AiTaskState.Running("Stage 1: Initializing Canonical Epistemic Ledger…")
            try {
                val generated = aiService.initializeCanonicalLedger(currentPurpose, voice.value)
                val nextLedger = currentLedger.copy(
                    coreThesis = generated.coreThesis,
                    purpose = currentPurpose.centralPurpose,
                    intendedAudience = currentPurpose.intendedAudience,
                    foundationalAxioms = generated.foundationalAxioms,
                    formalDefinitions = generated.formalDefinitions,
                    mathematicalSpecs = generated.mathematicalSpecs,
                    terminologicalGlossary = generated.terminologicalGlossary,
                    resolvedPremises = generated.resolvedPremises,
                    openQuestions = generated.openQuestions,
                    approved = false,
                    version = currentLedger.version + 1L
                )
                bookRepository.saveCanonicalLedger(currentUserId, bookId, nextLedger)
                knowledgeRepository.logAiInteraction(
                    currentUserId, bookId,
                    AIInteraction(
                        agentRole = "Architectural Author & Formal Systems Analyst",
                        actionType = "INIT_CANONICAL_LEDGER",
                        modelName = aiService.activeModelName,
                        promptSummary = "Initialize Canonical Ledger for ${currentPurpose.title}",
                        outputSummary = "${generated.foundationalAxioms.size} axioms, ${generated.formalDefinitions.size} definitions, ${generated.terminologicalGlossary.size} glossary terms.",
                        decision = "PENDING"
                    )
                )
                _aiState.value = AiTaskState.Idle
                _snackbar.value = "Canonical Ledger initialized. Review and approve to lock invariants."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiState.value = AiTaskState.Error(e.localizedMessage ?: "Canonical Ledger initialization failed.")
            }
        }
    }

    // --- MODULE G: AUTHOR VOICE ---
    fun saveAuthorVoice(updated: AuthorVoice) {
        viewModelScope.launch {
            val res = bookRepository.saveAuthorVoice(currentUserId, bookId, updated)
            _snackbar.value = if (res.isSuccess) "Author Voice Profile saved." else (res.exceptionOrNull()?.localizedMessage ?: "Voice save failed.")
        }
    }

    // --- MODULE C: BOOK ARCHITECT & FULL-BOOK PIPELINE ---
    fun generateCompleteBlueprint(desiredChapterCount: Int = 8) {
        val currentPurpose = purpose.value ?: return
        val existingChapters = chapters.value
        val approvedChapters = existingChapters.filter { it.status == ChapterStatus.APPROVED.name || it.blueprintApproved }
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _aiState.value = AiTaskState.Running("Stage 2: Building Complete Cumulative Book Blueprint…")
            try {
                val generated = aiService.generateCompleteBlueprint(
                    purpose = currentPurpose,
                    ledger = ledger.value,
                    existingApprovedChapters = approvedChapters,
                    desiredChapterCount = desiredChapterCount
                )
                // Preserve approved chapters; replace only unapproved outline stubs
                existingChapters
                    .filter { it.status == ChapterStatus.OUTLINE.name && !it.blueprintApproved && it.wordCount == 0L }
                    .forEach { bookRepository.deleteChapter(currentUserId, bookId, it.id) }

                val baseOrder = existingChapters.count {
                    !(it.status == ChapterStatus.OUTLINE.name && !it.blueprintApproved && it.wordCount == 0L)
                }.toLong()

                val distinctParts = generated.map { it.partLabel }.distinct()
                distinctParts.forEachIndexed { idx, partTitle ->
                    if (sections.value.none { it.title == partTitle }) {
                        bookRepository.createSection(currentUserId, bookId, partTitle, "Architectural division", idx.toLong())
                    }
                }

                generated.forEachIndexed { idx, bp ->
                    bookRepository.createChapter(
                        currentUserId,
                        bookId,
                        Chapter(
                            partLabel = bp.partLabel,
                            title = bp.title,
                            orderIndex = baseOrder + idx.toLong(),
                            objective = bp.objective,
                            coreProposition = bp.coreProposition,
                            argumentativePurpose = bp.argumentativePurpose,
                            requiredPremises = bp.requiredPremises,
                            requiredEvidence = bp.requiredEvidence,
                            formalisms = bp.formalisms,
                            strongestCounterargument = bp.strongestCounterargument,
                            expectedResolution = bp.expectedResolution,
                            transitionToNext = bp.transitionToNext,
                            targetWordCount = bp.targetWordCount,
                            status = ChapterStatus.OUTLINE.name
                        )
                    )
                }

                bookRepository.updateBookMetadata(
                    currentUserId, bookId,
                    mapOf(
                        "pipelineStage" to maxOf(book.value?.pipelineStage ?: 1L, 3L),
                        "lastCompletedSection" to "Stage 2: Complete Blueprint (${generated.size} chapters)",
                        "nextUnfinishedSection" to "Chapter 1: ${generated.firstOrNull()?.title ?: "Drafting"}"
                    )
                )

                knowledgeRepository.logAiInteraction(
                    currentUserId, bookId,
                    AIInteraction(
                        agentRole = "Architectural Author",
                        actionType = "GENERATE_BLUEPRINT",
                        modelName = aiService.activeModelName,
                        promptSummary = "Generate $desiredChapterCount-chapter cumulative blueprint",
                        outputSummary = "Created ${generated.size} chapters while preserving ${approvedChapters.size} approved chapters.",
                        decision = "ACCEPTED"
                    )
                )
                _aiState.value = AiTaskState.Idle
                _snackbar.value = "Blueprint generated (${generated.size} chapters). Approved chapters were preserved."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiState.value = AiTaskState.Error(e.localizedMessage ?: "Blueprint generation failed.")
            }
        }
    }

    fun addManualChapter(title: String, partLabel: String, objective: String, coreProposition: String, targetWords: Long) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val nextIdx = (chapters.value.maxOfOrNull { it.orderIndex } ?: -1L) + 1L
            val res = bookRepository.createChapter(
                currentUserId,
                bookId,
                Chapter(
                    title = title.trim(),
                    partLabel = partLabel.trim().ifBlank { "Part I" },
                    orderIndex = nextIdx,
                    objective = objective.trim(),
                    coreProposition = coreProposition.trim(),
                    targetWordCount = targetWords.coerceIn(500L, 100000L)
                )
            )
            res.onSuccess { newId ->
                _selectedChapterId.value = newId
                _snackbar.value = "Chapter added."
            }.onFailure {
                _snackbar.value = it.localizedMessage ?: "Failed to add chapter."
            }
        }
    }

    fun moveChapter(chapterId: String, direction: Int) {
        val list = chapters.value.sortedBy { it.orderIndex }.toMutableList()
        val idx = list.indexOfFirst { it.id == chapterId }
        val target = idx + direction
        if (idx < 0 || target !in list.indices) return
        val item = list.removeAt(idx)
        list.add(target, item)
        viewModelScope.launch {
            bookRepository.reorderChapters(currentUserId, bookId, list.map { it.id })
        }
    }

    fun deleteChapter(chapterId: String) {
        viewModelScope.launch {
            val res = bookRepository.deleteChapter(currentUserId, bookId, chapterId)
            if (_selectedChapterId.value == chapterId) _selectedChapterId.value = null
            _snackbar.value = if (res.isSuccess) "Chapter deleted." else (res.exceptionOrNull()?.localizedMessage ?: "Delete failed.")
        }
    }

    fun updateChapterFields(chapterId: String, updates: Map<String, Any?>, silent: Boolean = false) {
        viewModelScope.launch {
            _saveIndicator.value = "Saving…"
            val res = bookRepository.updateChapter(currentUserId, bookId, chapterId, updates)
            if (res.isSuccess && book.value?.driveSyncStatus == DriveSyncStatus.EXPORTED_TO_DRIVE.name) {
                bookRepository.updateBookMetadata(currentUserId, bookId, mapOf("driveSyncStatus" to DriveSyncStatus.NEEDS_RESYNC.name))
            }
            _saveIndicator.value = if (res.isSuccess) "Saved to Cloud" else "Save failed"
            if (!silent && res.isFailure) {
                _snackbar.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to save chapter."
            }
        }
    }

    /**
     * Full-Book Continuation Pipeline:
     * Drafts the next unfinished chapter (preserving checkpointing across sessions)
     * without ever restarting from Chapter 1 or overwriting approved prose.
     */
    fun continueFullBookPipelineNextChapter() {
        val nextChapter = chapters.value
            .sortedBy { it.orderIndex }
            .firstOrNull { !it.fullProseDrafted && it.status != ChapterStatus.APPROVED.name }

        if (nextChapter == null) {
            _snackbar.value = "All planned chapters already have full prose drafted! Proceed to Stage 5 RED_TEAM & Stage 7 Whole-Book Verification."
            return
        }

        _selectedChapterId.value = nextChapter.id
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _pipelineActive.value = true
            val label = "Pipeline Stage 4: Drafting '${nextChapter.title}'…"
            _aiState.value = AiTaskState.Running(label)
            try {
                var finalDraft = ""
                aiService.streamWritingAssistance(
                    action = WritingAssistAction.DRAFT_FULL,
                    chapter = nextChapter,
                    purpose = purpose.value,
                    ledger = ledger.value,
                    voice = voice.value,
                    codex = codex.value,
                    sources = sources.value,
                    customInstruction = "Draft the complete, cumulative chapter prose strictly obeying the Canonical Ledger and Chapter Blueprint."
                ).collect { accumulated ->
                    finalDraft = accumulated
                    _aiState.value = AiTaskState.Streaming(label, accumulated)
                }

                // Save safety snapshot before updating chapter prose
                if (nextChapter.content.isNotBlank()) {
                    bookRepository.saveChapterSnapshot(
                        currentUserId, bookId, nextChapter.id,
                        nextChapter.versionNumber,
                        "Pre-Pipeline Backup v${nextChapter.versionNumber}",
                        nextChapter.content,
                        nextChapter.status == ChapterStatus.APPROVED.name
                    )
                }

                val nextVer = nextChapter.versionNumber + 1L
                bookRepository.updateChapter(
                    currentUserId, bookId, nextChapter.id,
                    mapOf(
                        "content" to finalDraft,
                        "status" to ChapterStatus.DRAFT.name,
                        "fullProseDrafted" to true,
                        "argumentsDeveloped" to true,
                        "versionNumber" to nextVer
                    )
                )
                bookRepository.saveChapterSnapshot(
                    currentUserId, bookId, nextChapter.id,
                    nextVer,
                    "Pipeline Stage 4 Draft v$nextVer",
                    finalDraft,
                    false
                )

                val remaining = chapters.value
                    .sortedBy { it.orderIndex }
                    .firstOrNull { it.id != nextChapter.id && !it.fullProseDrafted && it.status != ChapterStatus.APPROVED.name }

                bookRepository.updateBookMetadata(
                    currentUserId, bookId,
                    mapOf(
                        "pipelineStage" to if (remaining == null) 5L else 4L,
                        "lastCompletedSection" to "Drafted: ${nextChapter.title}",
                        "nextUnfinishedSection" to (remaining?.let { "Draft: ${it.title}" } ?: "Stage 5: RED_TEAM Adversarial Review")
                    )
                )
                knowledgeRepository.logAiInteraction(
                    currentUserId, bookId,
                    AIInteraction(
                        chapterId = nextChapter.id,
                        agentRole = "Chapter Drafter (Full-Book Pipeline)",
                        actionType = "PIPELINE_DRAFT_CHAPTER",
                        modelName = aiService.activeModelName,
                        promptSummary = "Full-Book Pipeline draft for ${nextChapter.title}",
                        outputSummary = "Drafted ${countWords(finalDraft)} words.",
                        decision = "ACCEPTED"
                    )
                )
                _aiState.value = AiTaskState.Idle
                _pipelineActive.value = false
                _snackbar.value = "Drafted '${nextChapter.title}' (${countWords(finalDraft)} words) & saved checkpoint."
            } catch (e: CancellationException) {
                _pipelineActive.value = false
                throw e
            } catch (e: Exception) {
                _pipelineActive.value = false
                _aiState.value = AiTaskState.Error(e.localizedMessage ?: "Pipeline chapter draft failed.")
            }
        }
    }

    // --- MODULE F: AI WRITING STUDIO & VERSION HISTORY ---
    fun runWritingAssist(
        chapter: Chapter,
        action: WritingAssistAction,
        customInstruction: String = ""
    ) {
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            val label = "${action.agentRole}: ${action.label}…"
            _aiState.value = AiTaskState.Running(label)
            try {
                val latestReviewText = if (action == WritingAssistAction.REVISE_FROM_REVIEW) {
                    reviews.value.firstOrNull { it.chapterId == chapter.id || it.chapterId.isBlank() }?.let { r ->
                        buildString {
                            appendLine("Review Summary: ${r.summary}")
                            r.findings.forEach { f ->
                                appendLine("- [${f.severity}] ${f.category}: ${f.description} -> Fix: ${f.recommendation}")
                            }
                        }
                    }.orEmpty()
                } else ""

                var output = ""
                aiService.streamWritingAssistance(
                    action = action,
                    chapter = chapter,
                    purpose = purpose.value,
                    ledger = ledger.value,
                    voice = voice.value,
                    codex = codex.value,
                    sources = sources.value,
                    customInstruction = customInstruction,
                    latestReviewContext = latestReviewText
                ).collect { accumulated ->
                    output = accumulated
                    _aiState.value = AiTaskState.Streaming(label, accumulated)
                }

                val logId = knowledgeRepository.logAiInteraction(
                    currentUserId, bookId,
                    AIInteraction(
                        chapterId = chapter.id,
                        agentRole = action.agentRole,
                        actionType = action.name,
                        modelName = aiService.activeModelName,
                        promptSummary = customInstruction.ifBlank { action.label },
                        outputSummary = output.take(800),
                        decision = "PENDING"
                    )
                ).getOrDefault("")

                _aiState.value = AiTaskState.SuggestionReady(
                    action = action,
                    chapterId = chapter.id,
                    interactionId = logId,
                    suggestedText = output
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiState.value = AiTaskState.Error(e.localizedMessage ?: "AI writing assistance failed.")
            }
        }
    }

    fun resolveAiSuggestion(
        chapter: Chapter,
        decision: String, // ACCEPTED, MODIFIED, REJECTED
        finalProse: String?
    ) {
        val currentState = _aiState.value as? AiTaskState.SuggestionReady ?: return
        viewModelScope.launch {
            if (currentState.interactionId.isNotBlank()) {
                knowledgeRepository.updateAiDecision(currentUserId, bookId, currentState.interactionId, decision)
            }
            if ((decision == "ACCEPTED" || decision == "MODIFIED") && finalProse != null) {
                // Auto-checkpoint previous content before applying AI suggestion
                if (chapter.content.isNotBlank()) {
                    bookRepository.saveChapterSnapshot(
                        currentUserId, bookId, chapter.id,
                        chapter.versionNumber,
                        "Auto-Checkpoint before ${currentState.action.label}",
                        chapter.content,
                        chapter.status == ChapterStatus.APPROVED.name
                    )
                }
                val mergedContent = if (currentState.action == WritingAssistAction.CONTINUE_DRAFT && chapter.content.isNotBlank()) {
                    "${chapter.content.trimEnd()}\n\n$finalProse"
                } else {
                    finalProse
                }
                val nextVer = chapter.versionNumber + 1L
                val updates = mutableMapOf<String, Any?>(
                    "content" to mergedContent,
                    "status" to ChapterStatus.DRAFT.name,
                    "versionNumber" to nextVer
                )
                if (currentState.action == WritingAssistAction.DRAFT_FULL) {
                    updates["fullProseDrafted"] = true
                    updates["argumentsDeveloped"] = true
                }
                if (currentState.action == WritingAssistAction.REVISE_FROM_REVIEW) {
                    updates["revisionsIncorporated"] = true
                }
                bookRepository.updateChapter(currentUserId, bookId, chapter.id, updates)
                _snackbar.value = "Suggestion applied & prior draft saved to Version History."
            } else {
                _snackbar.value = "AI suggestion rejected. Author manuscript unchanged."
            }
            _aiState.value = AiTaskState.Idle
        }
    }

    fun saveCheckpoint(chapter: Chapter, label: String) {
        viewModelScope.launch {
            val nextVer = chapter.versionNumber + 1L
            val res = bookRepository.saveChapterSnapshot(
                userId = currentUserId,
                bookId = bookId,
                chapterId = chapter.id,
                versionNumber = nextVer,
                label = label.trim().ifBlank { "Checkpoint v$nextVer" },
                content = chapter.content,
                authorApproved = chapter.status == ChapterStatus.APPROVED.name
            )
            if (res.isSuccess) {
                bookRepository.updateChapter(currentUserId, bookId, chapter.id, mapOf("versionNumber" to nextVer))
                _snackbar.value = "Checkpoint '$label' saved (v$nextVer)."
            } else {
                _snackbar.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to save checkpoint."
            }
        }
    }

    fun restoreVersion(chapter: Chapter, version: ManuscriptVersion) {
        viewModelScope.launch {
            // Preserve current content as safety snapshot first
            if (chapter.content != version.content && chapter.content.isNotBlank()) {
                bookRepository.saveChapterSnapshot(
                    currentUserId, bookId, chapter.id,
                    chapter.versionNumber,
                    "Safety Backup before Restore to v${version.versionNumber}",
                    chapter.content,
                    chapter.status == ChapterStatus.APPROVED.name
                )
            }
            val nextVer = chapter.versionNumber + 1L
            val res = bookRepository.updateChapter(
                currentUserId, bookId, chapter.id,
                mapOf(
                    "content" to version.content,
                    "versionNumber" to nextVer,
                    "status" to ChapterStatus.DRAFT.name
                )
            )
            _snackbar.value = if (res.isSuccess) {
                "Restored '${version.label}'. Prior text backed up in Version History."
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Restore failed."
            }
        }
    }

    // --- MODULE D & E: CODEX, SOURCES, CLAIMS & AI CITATION FORMATTER ---
    fun addCodexEntry(entry: KnowledgeEntry) {
        viewModelScope.launch {
            val res = knowledgeRepository.addCodexEntry(currentUserId, bookId, entry)
            _snackbar.value = if (res.isSuccess) "Knowledge Codex entry saved." else (res.exceptionOrNull()?.localizedMessage ?: "Failed to save entry.")
        }
    }

    fun toggleApproveCodex(entry: KnowledgeEntry) {
        viewModelScope.launch {
            knowledgeRepository.updateCodexEntry(
                currentUserId, bookId, entry.id,
                mapOf("approved" to !entry.approved)
            )
        }
    }

    fun deleteCodexEntry(entryId: String) {
        viewModelScope.launch { knowledgeRepository.deleteCodexEntry(currentUserId, bookId, entryId) }
    }

    fun addResearchSource(source: ResearchSource) {
        viewModelScope.launch {
            val res = knowledgeRepository.addSource(currentUserId, bookId, source)
            _snackbar.value = if (res.isSuccess) "Research source registered." else (res.exceptionOrNull()?.localizedMessage ?: "Failed to register source.")
        }
    }

    fun deleteResearchSource(sourceId: String) {
        viewModelScope.launch { knowledgeRepository.deleteSource(currentUserId, bookId, sourceId) }
    }

    fun addResearchClaim(claim: ResearchClaim) {
        viewModelScope.launch {
            val res = knowledgeRepository.addClaim(currentUserId, bookId, claim)
            _snackbar.value = if (res.isSuccess) "Claim linked to evidence ledger." else (res.exceptionOrNull()?.localizedMessage ?: "Failed to save claim.")
        }
    }

    fun deleteResearchClaim(claimId: String) {
        viewModelScope.launch { knowledgeRepository.deleteClaim(currentUserId, bookId, claimId) }
    }

    fun formatSourceCitation(source: ResearchSource, style: CitationStyle, pageOrLocator: String) {
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _aiState.value = AiTaskState.Running("Formatting ${style.label} citation…")
            try {
                val formatted = aiService.formatCitation(source, style, pageOrLocator)
                _citationResult.value = formatted
                if (source.id.isNotBlank()) {
                    knowledgeRepository.updateSource(
                        currentUserId, bookId, source.id,
                        mapOf("citationText" to formatted.bibliographyEntry.take(1500))
                    )
                }
                _aiState.value = AiTaskState.Idle
                _snackbar.value = "${style.label} citation generated."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiState.value = AiTaskState.Error(e.localizedMessage ?: "Citation formatting failed.")
            }
        }
    }

    fun generateFullBibliography(style: CitationStyle) {
        val currentSources = sources.value
        if (currentSources.isEmpty()) {
            _snackbar.value = "Register at least one research source first."
            return
        }
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _aiState.value = AiTaskState.Running("Compiling ${style.label} Bibliography…")
            try {
                val bib = aiService.generateFormattedBibliography(currentSources, style)
                _formattedBibliography.value = bib
                _aiState.value = AiTaskState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiState.value = AiTaskState.Error(e.localizedMessage ?: "Bibliography compilation failed.")
            }
        }
    }

    // --- MODULE H & I: MUHASABAH, RED_TEAM, CONSISTENCY & WHOLE-BOOK VERIFICATION ---
    fun runReview(reviewType: ReviewType, targetChapter: Chapter?) {
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _aiState.value = AiTaskState.Running("Running ${reviewType.label}…")
            try {
                val result = when (reviewType) {
                    ReviewType.MUHASABAH -> aiService.runMuhasabahReview(
                        chapter = targetChapter,
                        allChapters = chapters.value,
                        purpose = purpose.value,
                        ledger = ledger.value,
                        sources = sources.value
                    )
                    ReviewType.RED_TEAM -> {
                        val ch = targetChapter ?: chapters.value.firstOrNull()
                            ?: throw IllegalStateException("Create at least one chapter before running RED_TEAM review.")
                        aiService.runRedTeamReview(ch, purpose.value, ledger.value, sources.value)
                    }
                    ReviewType.CONSISTENCY, ReviewType.WHOLE_BOOK -> {
                        if (chapters.value.isEmpty()) {
                            throw IllegalStateException("Add chapters before running whole-book analysis.")
                        }
                        aiService.runConsistencyOrWholeBookAudit(
                            reviewType = reviewType,
                            chapters = chapters.value,
                            purpose = purpose.value,
                            ledger = ledger.value,
                            codex = codex.value,
                            sources = sources.value
                        )
                    }
                }

                knowledgeRepository.saveReviewReport(
                    currentUserId, bookId,
                    ReviewReport(
                        chapterId = targetChapter?.id.orEmpty(),
                        reviewType = reviewType.name,
                        summary = result.summary,
                        overallScore = result.overallScore,
                        findings = result.findings,
                        qualitiesFlagged = result.qualitiesFlagged,
                        unresolvedQuestions = result.unresolvedQuestions,
                        authorAcknowledged = false
                    )
                )

                if (reviewType == ReviewType.RED_TEAM && targetChapter != null) {
                    bookRepository.updateChapter(
                        currentUserId, bookId, targetChapter.id,
                        mapOf("redTeamCompleted" to true, "status" to ChapterStatus.REVIEWED.name)
                    )
                } else if (reviewType == ReviewType.WHOLE_BOOK) {
                    chapters.value.forEach { ch ->
                        if (ch.fullProseDrafted) {
                            bookRepository.updateChapter(currentUserId, bookId, ch.id, mapOf("continuityChecked" to true))
                        }
                    }
                    bookRepository.updateBookMetadata(
                        currentUserId, bookId,
                        mapOf(
                            "pipelineStage" to 8L,
                            "lastCompletedSection" to "Stage 7: Whole-Book Verification (${result.overallScore}/100)",
                            "nextUnfinishedSection" to "Stage 8: Final Manuscript Assembly & Export"
                        )
                    )
                }

                knowledgeRepository.logAiInteraction(
                    currentUserId, bookId,
                    AIInteraction(
                        chapterId = targetChapter?.id.orEmpty(),
                        agentRole = reviewType.label,
                        actionType = reviewType.name,
                        modelName = aiService.activeModelName,
                        promptSummary = "${reviewType.label} on ${targetChapter?.title ?: "Whole Book"}",
                        outputSummary = "Score ${result.overallScore}/100, ${result.findings.size} findings.",
                        decision = "ACCEPTED"
                    )
                )
                _aiState.value = AiTaskState.Idle
                _snackbar.value = "${reviewType.label} completed (Integrity Score: ${result.overallScore}/100)."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiState.value = AiTaskState.Error(e.localizedMessage ?: "Review execution failed.")
            }
        }
    }

    fun acknowledgeReview(reviewId: String) {
        viewModelScope.launch {
            knowledgeRepository.acknowledgeReview(currentUserId, bookId, reviewId)
            _snackbar.value = "Review acknowledged by author."
        }
    }

    // --- MODULE J & K: PUBLICATION ASSEMBLY & EXPORT ---
    fun exportManuscript(format: ExportFormat, markDriveExported: Boolean = false, onReady: (ExportResult) -> Unit) {
        val currentBook = book.value ?: return
        viewModelScope.launch {
            _aiState.value = AiTaskState.Running("Stage 8: Assembling & validating ${format.label}…")
            try {
                val bundle = ManuscriptBundle(
                    book = currentBook,
                    authorName = authorDisplayName.ifBlank { "Author" },
                    purpose = purpose.value,
                    ledger = ledger.value,
                    chapters = chapters.value,
                    codex = codex.value,
                    sources = sources.value
                )
                val result = withContext(Dispatchers.IO) {
                    exporter.export(bundle, format)
                }
                _lastExportResult.value = result
                knowledgeRepository.logExportJob(
                    currentUserId, bookId,
                    ExportJob(
                        format = format.name,
                        fileName = result.file.name,
                        status = if (result.validated) "COMPLETED" else "FAILED",
                        validated = result.validated,
                        validationNotes = result.validationNotes,
                        byteSize = result.byteSize,
                        chapterCount = result.chapterCount,
                        wordCount = result.wordCount
                    )
                )
                if (markDriveExported) {
                    bookRepository.updateBookMetadata(
                        currentUserId, bookId,
                        mapOf("driveSyncStatus" to DriveSyncStatus.EXPORTED_TO_DRIVE.name)
                    )
                }
                _aiState.value = AiTaskState.Idle
                _snackbar.value = "${format.label} assembled & validated (${result.byteSize / 1024} KB)."
                onReady(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiState.value = AiTaskState.Error(e.localizedMessage ?: "Export failed.")
            }
        }
    }

    fun importChapterFromText(title: String, importedContent: String) {
        if (importedContent.isBlank()) return
        viewModelScope.launch {
            val nextIdx = (chapters.value.maxOfOrNull { it.orderIndex } ?: -1L) + 1L
            val res = bookRepository.createChapter(
                currentUserId, bookId,
                Chapter(
                    title = title.ifBlank { "Imported Chapter ${nextIdx + 1}" },
                    partLabel = "Imported Manuscript",
                    orderIndex = nextIdx,
                    content = importedContent.take(400000),
                    status = ChapterStatus.DRAFT.name,
                    fullProseDrafted = true
                )
            )
            res.onSuccess { id ->
                _selectedChapterId.value = id
                _snackbar.value = "Imported document into new chapter (${countWords(importedContent)} words)."
            }.onFailure {
                _snackbar.value = it.localizedMessage ?: "Import failed."
            }
        }
    }

    private companion object {
        const val TAG = "BookWorkspaceVM"
    }
}
