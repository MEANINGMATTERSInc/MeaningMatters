package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.core.unwrapFirestoreFlowError
import com.example.data.model.AIInteraction
import com.example.data.model.CodexCategory
import com.example.data.model.CodexOrigin
import com.example.data.model.EvidenceLevel
import com.example.data.model.ExportFormat
import com.example.data.model.ExportJob
import com.example.data.model.KnowledgeEntry
import com.example.data.model.ResearchClaim
import com.example.data.model.ResearchSource
import com.example.data.model.ReviewFinding
import com.example.data.model.ReviewReport
import com.example.data.model.ReviewType
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class KnowledgeAndGovernanceRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        if (expectedUserId != null) {
            check(uid == expectedUserId) { "Signed-in user mismatch; refusing cross-user access." }
        }
        return uid
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)
    private fun bookDoc(uid: String, bookId: String) = userDoc(uid).collection("books").document(bookId)

    // --- USER PROFILE & SETTINGS ---
    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid"
        emitAll(
            userDoc(uid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreFlowError(error) ?: throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(userId: String, displayName: String, email: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val ref = userDoc(uid)
            if (!ref.get().await().exists()) {
                val safeName = displayName.trim().ifBlank { "Author" }.take(120)
                ref.set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to safeName,
                        "email" to email.trim().take(200),
                        "defaultPenName" to safeName,
                        "editorFontSizeSp" to 17L,
                        "dyslexiaFriendlySpacing" to false,
                        "focusModeDefault" to false,
                        "autoSaveEnabled" to true,
                        "preferredModel" to "gemini-3.8-flash",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserProfile(userId: String, updates: Map<String, Any?>): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val allowed = setOf(
                "displayName", "email", "defaultPenName", "editorFontSizeSp",
                "dyslexiaFriendlySpacing", "focusModeDefault", "autoSaveEnabled", "preferredModel"
            )
            val payload = mutableMapOf<String, Any>()
            updates.forEach { (key, value) ->
                if (key in allowed && value != null) payload[key] = value
            }
            payload["updatedAt"] = FieldValue.serverTimestamp()
            userDoc(uid).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteAllUserData(userId: String, bookRepository: BookRepository): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val booksSnap = userDoc(uid).collection("books").get().await()
            for (doc in booksSnap.documents) {
                bookRepository.deleteBook(uid, doc.id).getOrThrow()
            }
            userDoc(uid).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- KNOWLEDGE CODEX ---
    fun observeCodex(userId: String, bookId: String): Flow<List<KnowledgeEntry>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/books/$bookId/codex"
        emitAll(
            bookDoc(uid, bookId).collection("codex")
                .snapshots()
                .map { snap ->
                    snap.toObjects(KnowledgeEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreFlowError(error) ?: throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addCodexEntry(userId: String, bookId: String, entry: KnowledgeEntry): Result<String> {
        var path = "users/$userId/books/$bookId/codex"
        return try {
            val uid = requireUserId(userId)
            val cleanTitle = entry.title.trim().take(200)
            require(cleanTitle.isNotEmpty()) { "Codex title cannot be empty." }
            val ref = bookDoc(uid, bookId).collection("codex").document()
            path = ref.path
            ref.set(
                mapOf(
                    "userId" to uid,
                    "bookId" to bookId,
                    "title" to cleanTitle,
                    "category" to CodexCategory.fromRaw(entry.category).name,
                    "summary" to entry.summary.trim().take(3000),
                    "details" to entry.details.trim().take(20000),
                    "epistemicStatus" to EvidenceLevel.fromRaw(entry.epistemicStatus).name,
                    "originType" to CodexOrigin.fromRaw(entry.originType).name,
                    "relatedChapterIds" to entry.relatedChapterIds.take(100),
                    "approved" to entry.approved,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateCodexEntry(userId: String, bookId: String, entryId: String, updates: Map<String, Any?>): Result<Unit> {
        val path = "users/$userId/books/$bookId/codex/$entryId"
        return try {
            val uid = requireUserId(userId)
            val allowed = setOf("title", "category", "summary", "details", "epistemicStatus", "originType", "relatedChapterIds", "approved")
            val payload = mutableMapOf<String, Any>()
            updates.forEach { (key, value) ->
                if (key in allowed && value != null) payload[key] = value
            }
            payload["updatedAt"] = FieldValue.serverTimestamp()
            bookDoc(uid, bookId).collection("codex").document(entryId).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteCodexEntry(userId: String, bookId: String, entryId: String): Result<Unit> {
        val path = "users/$userId/books/$bookId/codex/$entryId"
        return try {
            val uid = requireUserId(userId)
            bookDoc(uid, bookId).collection("codex").document(entryId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- RESEARCH SOURCES ---
    fun observeSources(userId: String, bookId: String): Flow<List<ResearchSource>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/books/$bookId/sources"
        emitAll(
            bookDoc(uid, bookId).collection("sources")
                .snapshots()
                .map { snap ->
                    snap.toObjects(ResearchSource::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreFlowError(error) ?: throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addSource(userId: String, bookId: String, source: ResearchSource): Result<String> {
        var path = "users/$userId/books/$bookId/sources"
        return try {
            val uid = requireUserId(userId)
            val cleanTitle = source.title.trim().take(300)
            require(cleanTitle.isNotEmpty()) { "Source title cannot be empty." }
            val ref = bookDoc(uid, bookId).collection("sources").document()
            path = ref.path
            ref.set(
                mapOf(
                    "userId" to uid,
                    "bookId" to bookId,
                    "title" to cleanTitle,
                    "author" to source.author.trim().take(250),
                    "publicationDate" to source.publicationDate.trim().take(80),
                    "publisher" to source.publisher.trim().take(200),
                    "url" to source.url.trim().take(1000),
                    "sourceType" to source.sourceType.trim().ifBlank { "Book" }.take(80),
                    "notes" to source.notes.trim().take(15000),
                    "evidenceLevel" to EvidenceLevel.fromRaw(source.evidenceLevel).name,
                    "chapterId" to source.chapterId.take(128),
                    "citationText" to source.citationText.trim().take(1500),
                    "verificationNote" to source.verificationNote.trim().take(1500),
                    "attachmentName" to source.attachmentName.trim().take(300),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateSource(userId: String, bookId: String, sourceId: String, updates: Map<String, Any?>): Result<Unit> {
        val path = "users/$userId/books/$bookId/sources/$sourceId"
        return try {
            val uid = requireUserId(userId)
            val allowed = setOf(
                "title", "author", "publicationDate", "publisher", "url", "sourceType",
                "notes", "evidenceLevel", "chapterId", "citationText", "verificationNote", "attachmentName"
            )
            val payload = mutableMapOf<String, Any>()
            updates.forEach { (key, value) ->
                if (key in allowed && value != null) payload[key] = value
            }
            payload["updatedAt"] = FieldValue.serverTimestamp()
            bookDoc(uid, bookId).collection("sources").document(sourceId).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteSource(userId: String, bookId: String, sourceId: String): Result<Unit> {
        val path = "users/$userId/books/$bookId/sources/$sourceId"
        return try {
            val uid = requireUserId(userId)
            bookDoc(uid, bookId).collection("sources").document(sourceId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- RESEARCH CLAIMS ---
    fun observeClaims(userId: String, bookId: String): Flow<List<ResearchClaim>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/books/$bookId/claims"
        emitAll(
            bookDoc(uid, bookId).collection("claims")
                .snapshots()
                .map { snap ->
                    snap.toObjects(ResearchClaim::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreFlowError(error) ?: throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addClaim(userId: String, bookId: String, claim: ResearchClaim): Result<String> {
        var path = "users/$userId/books/$bookId/claims"
        return try {
            val uid = requireUserId(userId)
            val cleanStmt = claim.statement.trim().take(3000)
            require(cleanStmt.isNotEmpty()) { "Claim statement cannot be empty." }
            val ref = bookDoc(uid, bookId).collection("claims").document()
            path = ref.path
            ref.set(
                mapOf(
                    "userId" to uid,
                    "bookId" to bookId,
                    "chapterId" to claim.chapterId.take(128),
                    "sourceId" to claim.sourceId.take(128),
                    "statement" to cleanStmt,
                    "evidenceLevel" to EvidenceLevel.fromRaw(claim.evidenceLevel).name,
                    "citationRef" to claim.citationRef.trim().take(500),
                    "verified" to claim.verified,
                    "notes" to claim.notes.trim().take(3000),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteClaim(userId: String, bookId: String, claimId: String): Result<Unit> {
        val path = "users/$userId/books/$bookId/claims/$claimId"
        return try {
            val uid = requireUserId(userId)
            bookDoc(uid, bookId).collection("claims").document(claimId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- REVIEW REPORTS ---
    fun observeReviews(userId: String, bookId: String): Flow<List<ReviewReport>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/books/$bookId/reviews"
        emitAll(
            bookDoc(uid, bookId).collection("reviews")
                .snapshots()
                .map { snap ->
                    snap.toObjects(ReviewReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreFlowError(error) ?: throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveReviewReport(userId: String, bookId: String, report: ReviewReport): Result<String> {
        var path = "users/$userId/books/$bookId/reviews"
        return try {
            val uid = requireUserId(userId)
            val ref = bookDoc(uid, bookId).collection("reviews").document()
            path = ref.path
            val findingMaps = report.findings.take(100).map { f: ReviewFinding ->
                mapOf(
                    "category" to f.category.take(120),
                    "severity" to f.severity.take(30),
                    "chapterRef" to f.chapterRef.take(160),
                    "description" to f.description.take(2000),
                    "recommendation" to f.recommendation.take(2000)
                )
            }
            ref.set(
                mapOf(
                    "userId" to uid,
                    "bookId" to bookId,
                    "chapterId" to report.chapterId.take(128),
                    "reviewType" to ReviewType.fromRaw(report.reviewType).name,
                    "summary" to report.summary.trim().take(10000),
                    "overallScore" to report.overallScore.coerceIn(0L, 100L),
                    "findings" to findingMaps,
                    "qualitiesFlagged" to report.qualitiesFlagged.take(20),
                    "unresolvedQuestions" to report.unresolvedQuestions.take(50),
                    "authorAcknowledged" to report.authorAcknowledged,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun acknowledgeReview(userId: String, bookId: String, reviewId: String): Result<Unit> {
        val path = "users/$userId/books/$bookId/reviews/$reviewId"
        return try {
            val uid = requireUserId(userId)
            bookDoc(uid, bookId).collection("reviews").document(reviewId)
                .update(mapOf("authorAcknowledged" to true)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- AI GOVERNANCE AUDIT LOG ---
    fun observeAiLogs(userId: String, bookId: String): Flow<List<AIInteraction>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/books/$bookId/ai_logs"
        emitAll(
            bookDoc(uid, bookId).collection("ai_logs")
                .snapshots()
                .map { snap ->
                    snap.toObjects(AIInteraction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreFlowError(error) ?: throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun logAiInteraction(userId: String, bookId: String, log: AIInteraction): Result<String> {
        var path = "users/$userId/books/$bookId/ai_logs"
        return try {
            val uid = requireUserId(userId)
            val ref = bookDoc(uid, bookId).collection("ai_logs").document()
            path = ref.path
            ref.set(
                mapOf(
                    "userId" to uid,
                    "bookId" to bookId,
                    "chapterId" to log.chapterId.take(128),
                    "agentRole" to log.agentRole.ifBlank { "Authoring Agent" }.take(100),
                    "actionType" to log.actionType.ifBlank { "ASSIST" }.take(100),
                    "modelName" to log.modelName.ifBlank { "gemini-3.8-flash" }.take(80),
                    "promptSummary" to log.promptSummary.take(2000),
                    "outputSummary" to log.outputSummary.take(4000),
                    "decision" to (log.decision.takeIf { it in setOf("PENDING", "ACCEPTED", "MODIFIED", "REJECTED") } ?: "PENDING"),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateAiDecision(userId: String, bookId: String, interactionId: String, decision: String): Result<Unit> {
        val path = "users/$userId/books/$bookId/ai_logs/$interactionId"
        return try {
            val uid = requireUserId(userId)
            val safe = decision.takeIf { it in setOf("PENDING", "ACCEPTED", "MODIFIED", "REJECTED") } ?: "ACCEPTED"
            bookDoc(uid, bookId).collection("ai_logs").document(interactionId)
                .update(mapOf("decision" to safe)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- EXPORT JOBS ---
    fun observeExports(userId: String, bookId: String): Flow<List<ExportJob>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/books/$bookId/exports"
        emitAll(
            bookDoc(uid, bookId).collection("exports")
                .snapshots()
                .map { snap ->
                    snap.toObjects(ExportJob::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreFlowError(error) ?: throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun logExportJob(userId: String, bookId: String, job: ExportJob): Result<String> {
        var path = "users/$userId/books/$bookId/exports"
        return try {
            val uid = requireUserId(userId)
            val ref = bookDoc(uid, bookId).collection("exports").document()
            path = ref.path
            ref.set(
                mapOf(
                    "userId" to uid,
                    "bookId" to bookId,
                    "format" to ExportFormat.fromRaw(job.format).name,
                    "fileName" to job.fileName.ifBlank { "manuscript.pdf" }.take(250),
                    "status" to (job.status.takeIf { it in setOf("COMPLETED", "FAILED") } ?: "COMPLETED"),
                    "validated" to job.validated,
                    "validationNotes" to job.validationNotes.take(2000),
                    "byteSize" to job.byteSize.coerceIn(0L, 500000000L),
                    "chapterCount" to job.chapterCount.coerceIn(0L, 500L),
                    "wordCount" to job.wordCount.coerceIn(0L, 5000000L),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }
}
