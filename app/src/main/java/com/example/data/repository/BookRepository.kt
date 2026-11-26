package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.core.unwrapFirestoreFlowError
import com.example.data.model.AuthorVoice
import com.example.data.model.Book
import com.example.data.model.BookPurpose
import com.example.data.model.BookSection
import com.example.data.model.BookStatus
import com.example.data.model.CanonicalLedger
import com.example.data.model.Chapter
import com.example.data.model.ChapterStatus
import com.example.data.model.DriveSyncStatus
import com.example.data.model.ManuscriptVersion
import com.example.data.model.countWords
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

class BookRepository(
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

    private fun booksCol(uid: String) =
        db.collection("users").document(uid).collection("books")

    private fun bookDoc(uid: String, bookId: String) =
        booksCol(uid).document(bookId)

    // --- BOOKS ---
    fun observeBooks(userId: String): Flow<List<Book>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/books"
        emitAll(
            booksCol(uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Book::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    fun observeBook(userId: String, bookId: String): Flow<Book?> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/books/$bookId"
        emitAll(
            bookDoc(uid, bookId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(Book::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    suspend fun createBook(
        userId: String,
        title: String,
        subtitle: String,
        genre: String,
        intendedAudience: String,
        centralPurpose: String,
        mainThesis: String,
        readerTransformation: String,
        authorValues: String,
        intellectualBoundaries: String,
        desiredStyle: String,
        targetWordCount: Long
    ): Result<String> {
        var path = "users/$userId/books"
        return try {
            val uid = requireUserId(userId)
            val cleanTitle = title.trim().take(200)
            require(cleanTitle.isNotEmpty()) { "Book title cannot be empty." }
            val safeTarget = targetWordCount.coerceIn(1000L, 5000000L)

            val docRef = booksCol(uid).document()
            val bookId = docRef.id
            path = docRef.path

            val batch = db.batch()
            val bookPayload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "subtitle" to subtitle.trim().take(300),
                "genre" to genre.trim().take(100),
                "targetAudience" to intendedAudience.trim().take(300),
                "status" to BookStatus.CONCEPT.name,
                "archived" to false,
                "wordCount" to 0L,
                "targetWordCount" to safeTarget,
                "chapterCount" to 0L,
                "completedChapters" to 0L,
                "purposeApproved" to false,
                "ledgerApproved" to false,
                "pipelineStage" to 1L,
                "lastCompletedSection" to "",
                "nextUnfinishedSection" to "Stage 1: Initialize Canonical Ledger",
                "driveSyncStatus" to DriveSyncStatus.LOCAL_ONLY.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(docRef, bookPayload)

            val purposePayload = mapOf(
                "userId" to uid,
                "bookId" to bookId,
                "title" to cleanTitle,
                "subtitle" to subtitle.trim().take(300),
                "genre" to genre.trim().take(100),
                "intendedAudience" to intendedAudience.trim().take(1000),
                "centralPurpose" to centralPurpose.trim().take(3000),
                "mainThesis" to mainThesis.trim().take(3000),
                "readerTransformation" to readerTransformation.trim().take(2000),
                "authorValues" to authorValues.trim().take(2000),
                "intellectualBoundaries" to intellectualBoundaries.trim().take(2000),
                "desiredStyle" to desiredStyle.trim().take(1000),
                "targetLengthWords" to safeTarget,
                "generatedDocument" to "",
                "approved" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(docRef.collection("purpose").document("primary"), purposePayload)

            val voicePayload = mapOf(
                "userId" to uid,
                "bookId" to bookId,
                "writingTone" to (desiredStyle.trim().ifBlank { "Thoughtful, lucid, intellectually honest" }.take(200)),
                "sentenceComplexity" to "Balanced rhythm with clear transitions",
                "vocabularyLevel" to "Accessible scholarly precision",
                "narrativePerspective" to "First-person plural (editorial we)",
                "preferredTerms" to emptyList<String>(),
                "avoidedTerms" to emptyList<String>(),
                "writingSamples" to "",
                "styleNotes" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(docRef.collection("voice").document("primary"), voicePayload)

            val ledgerPayload = mapOf(
                "userId" to uid,
                "bookId" to bookId,
                "coreThesis" to mainThesis.trim().take(4000),
                "purpose" to centralPurpose.trim().take(3000),
                "intendedAudience" to intendedAudience.trim().take(1000),
                "foundationalAxioms" to listOf(
                    "Capability does not confer authority; the author retains final approval.",
                    "Never fabricate citations, quotations, or empirical findings."
                ),
                "epistemicStandards" to "Distinguish VERIFIED, SUPPORTED, INTERPRETATION, TRADITION, HYPOTHESIS, SPECULATION, and UNVERIFIED across all chapters.",
                "formalDefinitions" to emptyList<String>(),
                "mathematicalSpecs" to "",
                "terminologicalGlossary" to emptyList<String>(),
                "resolvedPremises" to emptyList<String>(),
                "openQuestions" to emptyList<String>(),
                "approved" to false,
                "version" to 1L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(docRef.collection("ledger").document("primary"), ledgerPayload)

            batch.commit().await()
            Result.success(bookId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateBookMetadata(
        userId: String,
        bookId: String,
        updates: Map<String, Any?>
    ): Result<Unit> {
        val path = "users/$userId/books/$bookId"
        return try {
            val uid = requireUserId(userId)
            require(bookId.isNotBlank()) { "Book ID must not be blank." }
            val allowed = setOf(
                "title", "subtitle", "genre", "targetAudience", "status", "archived",
                "wordCount", "targetWordCount", "chapterCount", "completedChapters",
                "purposeApproved", "ledgerApproved", "pipelineStage",
                "lastCompletedSection", "nextUnfinishedSection", "driveSyncStatus"
            )
            val payload = mutableMapOf<String, Any>()
            updates.forEach { (key, value) ->
                if (key in allowed && value != null) payload[key] = value
            }
            payload["updatedAt"] = FieldValue.serverTimestamp()
            bookDoc(uid, bookId).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteBook(userId: String, bookId: String): Result<Unit> {
        val path = "users/$userId/books/$bookId"
        return try {
            val uid = requireUserId(userId)
            val docRef = bookDoc(uid, bookId)
            val subCollections = listOf(
                "purpose", "voice", "ledger", "sections", "chapters",
                "versions", "codex", "sources", "claims", "reviews", "ai_logs", "exports"
            )
            for (sub in subCollections) {
                val snap = docRef.collection(sub).get().await()
                if (!snap.isEmpty) {
                    snap.documents.chunked(400).forEach { chunk ->
                        val batch = db.batch()
                        chunk.forEach { batch.delete(it.reference) }
                        batch.commit().await()
                    }
                }
            }
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- BOOK PURPOSE ---
    fun observeBookPurpose(userId: String, bookId: String): Flow<BookPurpose?> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/books/$bookId/purpose/primary"
        emitAll(
            bookDoc(uid, bookId).collection("purpose").document("primary")
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(BookPurpose::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    suspend fun saveBookPurpose(userId: String, bookId: String, purpose: BookPurpose): Result<Unit> {
        val path = "users/$userId/books/$bookId/purpose/primary"
        return try {
            val uid = requireUserId(userId)
            val ref = bookDoc(uid, bookId).collection("purpose").document("primary")
            val exists = ref.get().await().exists()
            val fields = mapOf<String, Any>(
                "title" to purpose.title.trim().ifBlank { "Untitled" }.take(200),
                "subtitle" to purpose.subtitle.trim().take(300),
                "genre" to purpose.genre.trim().take(100),
                "intendedAudience" to purpose.intendedAudience.trim().take(1000),
                "centralPurpose" to purpose.centralPurpose.trim().take(3000),
                "mainThesis" to purpose.mainThesis.trim().take(3000),
                "readerTransformation" to purpose.readerTransformation.trim().take(2000),
                "authorValues" to purpose.authorValues.trim().take(2000),
                "intellectualBoundaries" to purpose.intellectualBoundaries.trim().take(2000),
                "desiredStyle" to purpose.desiredStyle.trim().take(1000),
                "targetLengthWords" to purpose.targetLengthWords.coerceIn(0L, 5000000L),
                "generatedDocument" to purpose.generatedDocument.take(40000),
                "approved" to purpose.approved,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (exists) {
                ref.update(fields).await()
            } else {
                val createPayload = fields.toMutableMap().apply {
                    put("userId", uid)
                    put("bookId", bookId)
                    put("createdAt", FieldValue.serverTimestamp())
                }
                ref.set(createPayload).await()
            }
            bookDoc(uid, bookId).update(
                mapOf(
                    "title" to purpose.title.trim().ifBlank { "Untitled" }.take(200),
                    "subtitle" to purpose.subtitle.trim().take(300),
                    "genre" to purpose.genre.trim().take(100),
                    "targetAudience" to purpose.intendedAudience.trim().take(300),
                    "targetWordCount" to purpose.targetLengthWords.coerceIn(0L, 5000000L),
                    "purposeApproved" to purpose.approved,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- AUTHOR VOICE ---
    fun observeAuthorVoice(userId: String, bookId: String): Flow<AuthorVoice?> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/books/$bookId/voice/primary"
        emitAll(
            bookDoc(uid, bookId).collection("voice").document("primary")
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(AuthorVoice::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    suspend fun saveAuthorVoice(userId: String, bookId: String, voice: AuthorVoice): Result<Unit> {
        val path = "users/$userId/books/$bookId/voice/primary"
        return try {
            val uid = requireUserId(userId)
            val ref = bookDoc(uid, bookId).collection("voice").document("primary")
            val exists = ref.get().await().exists()
            val fields = mapOf<String, Any>(
                "writingTone" to voice.writingTone.trim().take(200),
                "sentenceComplexity" to voice.sentenceComplexity.trim().take(200),
                "vocabularyLevel" to voice.vocabularyLevel.trim().take(200),
                "narrativePerspective" to voice.narrativePerspective.trim().take(200),
                "preferredTerms" to voice.preferredTerms.map { it.trim() }.filter { it.isNotEmpty() }.take(100),
                "avoidedTerms" to voice.avoidedTerms.map { it.trim() }.filter { it.isNotEmpty() }.take(100),
                "writingSamples" to voice.writingSamples.take(15000),
                "styleNotes" to voice.styleNotes.take(4000),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (exists) {
                ref.update(fields).await()
            } else {
                val createPayload = fields.toMutableMap().apply {
                    put("userId", uid)
                    put("bookId", bookId)
                    put("createdAt", FieldValue.serverTimestamp())
                }
                ref.set(createPayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- CANONICAL LEDGER ---
    fun observeCanonicalLedger(userId: String, bookId: String): Flow<CanonicalLedger?> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/books/$bookId/ledger/primary"
        emitAll(
            bookDoc(uid, bookId).collection("ledger").document("primary")
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(CanonicalLedger::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    suspend fun saveCanonicalLedger(userId: String, bookId: String, ledger: CanonicalLedger): Result<Unit> {
        val path = "users/$userId/books/$bookId/ledger/primary"
        return try {
            val uid = requireUserId(userId)
            val ref = bookDoc(uid, bookId).collection("ledger").document("primary")
            val exists = ref.get().await().exists()
            val safeVersion = ledger.version.coerceIn(1L, 10000L)
            val fields = mapOf<String, Any>(
                "coreThesis" to ledger.coreThesis.trim().take(4000),
                "purpose" to ledger.purpose.trim().take(3000),
                "intendedAudience" to ledger.intendedAudience.trim().take(1000),
                "foundationalAxioms" to ledger.foundationalAxioms.map { it.trim() }.filter { it.isNotEmpty() }.take(100),
                "epistemicStandards" to ledger.epistemicStandards.trim().take(6000),
                "formalDefinitions" to ledger.formalDefinitions.map { it.trim() }.filter { it.isNotEmpty() }.take(150),
                "mathematicalSpecs" to ledger.mathematicalSpecs.trim().take(10000),
                "terminologicalGlossary" to ledger.terminologicalGlossary.map { it.trim() }.filter { it.isNotEmpty() }.take(200),
                "resolvedPremises" to ledger.resolvedPremises.map { it.trim() }.filter { it.isNotEmpty() }.take(150),
                "openQuestions" to ledger.openQuestions.map { it.trim() }.filter { it.isNotEmpty() }.take(150),
                "approved" to ledger.approved,
                "version" to safeVersion,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (exists) {
                ref.update(fields).await()
            } else {
                val createPayload = fields.toMutableMap().apply {
                    put("userId", uid)
                    put("bookId", bookId)
                    put("createdAt", FieldValue.serverTimestamp())
                }
                ref.set(createPayload).await()
            }
            bookDoc(uid, bookId).update(
                mapOf(
                    "ledgerApproved" to ledger.approved,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- SECTIONS ---
    fun observeSections(userId: String, bookId: String): Flow<List<BookSection>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/books/$bookId/sections"
        emitAll(
            bookDoc(uid, bookId).collection("sections")
                .snapshots()
                .map { snap ->
                    snap.toObjects(BookSection::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.orderIndex }
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

    suspend fun createSection(userId: String, bookId: String, title: String, description: String, orderIndex: Long): Result<String> {
        var path = "users/$userId/books/$bookId/sections"
        return try {
            val uid = requireUserId(userId)
            val cleanTitle = title.trim().take(200)
            require(cleanTitle.isNotEmpty()) { "Section title cannot be empty." }
            val ref = bookDoc(uid, bookId).collection("sections").document()
            path = ref.path
            ref.set(
                mapOf(
                    "userId" to uid,
                    "bookId" to bookId,
                    "title" to cleanTitle,
                    "description" to description.trim().take(2000),
                    "orderIndex" to orderIndex.coerceIn(0L, 500L),
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

    // --- CHAPTERS ---
    fun observeChapters(userId: String, bookId: String): Flow<List<Chapter>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/books/$bookId/chapters"
        emitAll(
            bookDoc(uid, bookId).collection("chapters")
                .snapshots()
                .map { snap ->
                    snap.toObjects(Chapter::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.orderIndex }
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

    suspend fun createChapter(userId: String, bookId: String, chapter: Chapter): Result<String> {
        var path = "users/$userId/books/$bookId/chapters"
        return try {
            val uid = requireUserId(userId)
            val cleanTitle = chapter.title.trim().take(250)
            require(cleanTitle.isNotEmpty()) { "Chapter title cannot be empty." }
            val ref = bookDoc(uid, bookId).collection("chapters").document()
            path = ref.path
            val words = countWords(chapter.content)
            val payload = mapOf(
                "userId" to uid,
                "bookId" to bookId,
                "sectionId" to chapter.sectionId.take(128),
                "partLabel" to chapter.partLabel.trim().take(160),
                "title" to cleanTitle,
                "orderIndex" to chapter.orderIndex.coerceIn(0L, 500L),
                "objective" to chapter.objective.trim().take(3000),
                "coreProposition" to chapter.coreProposition.trim().take(3000),
                "argumentativePurpose" to chapter.argumentativePurpose.trim().take(3000),
                "requiredPremises" to chapter.requiredPremises.trim().take(4000),
                "requiredEvidence" to chapter.requiredEvidence.trim().take(4000),
                "formalisms" to chapter.formalisms.trim().take(4000),
                "strongestCounterargument" to chapter.strongestCounterargument.trim().take(4000),
                "expectedResolution" to chapter.expectedResolution.trim().take(4000),
                "transitionToNext" to chapter.transitionToNext.trim().take(2000),
                "targetWordCount" to chapter.targetWordCount.coerceIn(0L, 500000L),
                "content" to chapter.content.take(400000),
                "wordCount" to words,
                "status" to ChapterStatus.fromRaw(chapter.status).name,
                "blueprintApproved" to chapter.blueprintApproved,
                "fullProseDrafted" to chapter.fullProseDrafted,
                "argumentsDeveloped" to chapter.argumentsDeveloped,
                "evidenceReviewed" to chapter.evidenceReviewed,
                "redTeamCompleted" to chapter.redTeamCompleted,
                "revisionsIncorporated" to chapter.revisionsIncorporated,
                "continuityChecked" to chapter.continuityChecked,
                "versionNumber" to chapter.versionNumber.coerceIn(1L, 10000L),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            ref.set(payload).await()
            recalculateBookStats(uid, bookId)
            Result.success(ref.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateChapter(
        userId: String,
        bookId: String,
        chapterId: String,
        updates: Map<String, Any?>
    ): Result<Unit> {
        val path = "users/$userId/books/$bookId/chapters/$chapterId"
        return try {
            val uid = requireUserId(userId)
            require(chapterId.isNotBlank()) { "Chapter ID must not be blank." }
            val allowed = setOf(
                "sectionId", "partLabel", "title", "orderIndex", "objective",
                "coreProposition", "argumentativePurpose", "requiredPremises",
                "requiredEvidence", "formalisms", "strongestCounterargument",
                "expectedResolution", "transitionToNext", "targetWordCount",
                "content", "wordCount", "status", "blueprintApproved",
                "fullProseDrafted", "argumentsDeveloped", "evidenceReviewed",
                "redTeamCompleted", "revisionsIncorporated", "continuityChecked",
                "versionNumber"
            )
            val payload = mutableMapOf<String, Any>()
            updates.forEach { (key, value) ->
                if (key in allowed && value != null) payload[key] = value
            }
            val contentVal = payload["content"] as? String
            if (contentVal != null) {
                val safeContent = contentVal.take(400000)
                payload["content"] = safeContent
                payload["wordCount"] = countWords(safeContent)
            }
            payload["updatedAt"] = FieldValue.serverTimestamp()
            bookDoc(uid, bookId).collection("chapters").document(chapterId).update(payload).await()
            recalculateBookStats(uid, bookId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun reorderChapters(userId: String, bookId: String, orderedChapterIds: List<String>): Result<Unit> {
        val path = "users/$userId/books/$bookId/chapters"
        return try {
            val uid = requireUserId(userId)
            val batch = db.batch()
            orderedChapterIds.forEachIndexed { idx, chId ->
                val ref = bookDoc(uid, bookId).collection("chapters").document(chId)
                batch.update(
                    ref,
                    mapOf(
                        "orderIndex" to idx.toLong(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteChapter(userId: String, bookId: String, chapterId: String): Result<Unit> {
        val path = "users/$userId/books/$bookId/chapters/$chapterId"
        return try {
            val uid = requireUserId(userId)
            bookDoc(uid, bookId).collection("chapters").document(chapterId).delete().await()
            recalculateBookStats(uid, bookId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- MANUSCRIPT VERSIONS ---
    fun observeVersions(userId: String, bookId: String, chapterId: String): Flow<List<ManuscriptVersion>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/books/$bookId/versions"
        emitAll(
            bookDoc(uid, bookId).collection("versions")
                .snapshots()
                .map { snap ->
                    snap.toObjects(ManuscriptVersion::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.chapterId == chapterId }
                        .sortedByDescending { it.versionNumber }
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

    suspend fun saveChapterSnapshot(
        userId: String,
        bookId: String,
        chapterId: String,
        versionNumber: Long,
        label: String,
        content: String,
        authorApproved: Boolean
    ): Result<String> {
        var path = "users/$userId/books/$bookId/versions"
        return try {
            val uid = requireUserId(userId)
            val ref = bookDoc(uid, bookId).collection("versions").document()
            path = ref.path
            val safeContent = content.take(400000)
            ref.set(
                mapOf(
                    "userId" to uid,
                    "bookId" to bookId,
                    "chapterId" to chapterId.take(128),
                    "versionNumber" to versionNumber.coerceIn(1L, 10000L),
                    "label" to label.trim().ifBlank { "Snapshot v$versionNumber" }.take(200),
                    "content" to safeContent,
                    "wordCount" to countWords(safeContent),
                    "authorApproved" to authorApproved,
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

    private suspend fun recalculateBookStats(uid: String, bookId: String) {
        val snap = bookDoc(uid, bookId).collection("chapters").get().await()
        val chapters = snap.toObjects(Chapter::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        val totalWords = chapters.sumOf { it.wordCount }
        val completed = chapters.count { it.status == ChapterStatus.APPROVED.name || it.passesFullCompletionProtocol }.toLong()
        val computedStatus = when {
            chapters.isEmpty() -> BookStatus.CONCEPT.name
            completed == chapters.size.toLong() && chapters.isNotEmpty() -> BookStatus.READY.name
            chapters.any { it.redTeamCompleted || it.status == ChapterStatus.REVIEWED.name } -> BookStatus.REVIEWING.name
            totalWords > 0L -> BookStatus.DRAFTING.name
            else -> BookStatus.OUTLINING.name
        }
        bookDoc(uid, bookId).update(
            mapOf(
                "wordCount" to totalWords.coerceIn(0L, 5000000L),
                "chapterCount" to chapters.size.toLong().coerceIn(0L, 500L),
                "completedChapters" to completed.coerceIn(0L, 500L),
                "status" to computedStatus,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }
}
