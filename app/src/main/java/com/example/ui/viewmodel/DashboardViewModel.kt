package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.unwrapFirestoreFlowError
import com.example.data.model.Book
import com.example.data.model.UserProfile
import com.example.data.repository.BookRepository
import com.example.data.repository.KnowledgeAndGovernanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class DashboardViewModel(
    private val bookRepository: BookRepository,
    private val knowledgeRepository: KnowledgeAndGovernanceRepository,
    private val currentUserId: String,
    displayName: String,
    email: String
) : ViewModel() {

    val booksState: StateFlow<UiState<List<Book>>> = bookRepository.observeBooks(currentUserId)
        .map<List<Book>, UiState<List<Book>>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreFlowError(error) ?: throw error
            Log.w(TAG, "Error observing books", root)
            emit(UiState.Error(root.localizedMessage ?: "Failed to load books."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val userProfile: StateFlow<UserProfile?> = knowledgeRepository.observeUserProfile(currentUserId)
        .catch { error ->
            val root = unwrapFirestoreFlowError(error) ?: throw error
            Log.w(TAG, "Error observing profile", root)
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        viewModelScope.launch {
            knowledgeRepository.ensureUserProfile(currentUserId, displayName, email)
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun createBook(
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
        targetWordCount: Long,
        onCreated: (String) -> Unit
    ) {
        if (title.isBlank()) {
            _statusMessage.value = "Please enter a book title."
            return
        }
        viewModelScope.launch {
            _busy.value = true
            val result = bookRepository.createBook(
                userId = currentUserId,
                title = title,
                subtitle = subtitle,
                genre = genre,
                intendedAudience = intendedAudience,
                centralPurpose = centralPurpose,
                mainThesis = mainThesis,
                readerTransformation = readerTransformation,
                authorValues = authorValues,
                intellectualBoundaries = intellectualBoundaries,
                desiredStyle = desiredStyle,
                targetWordCount = targetWordCount
            )
            _busy.value = false
            result.onSuccess { bookId ->
                _statusMessage.value = "Book project created."
                onCreated(bookId)
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Failed to create book."
            }
        }
    }

    fun renameBook(bookId: String, newTitle: String, newSubtitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            val res = bookRepository.updateBookMetadata(
                currentUserId,
                bookId,
                mapOf("title" to newTitle.trim().take(200), "subtitle" to newSubtitle.trim().take(300))
            )
            _statusMessage.value = if (res.isSuccess) "Book renamed." else (res.exceptionOrNull()?.localizedMessage ?: "Rename failed.")
        }
    }

    fun toggleArchiveBook(bookId: String, currentlyArchived: Boolean) {
        viewModelScope.launch {
            val res = bookRepository.updateBookMetadata(
                currentUserId,
                bookId,
                mapOf("archived" to !currentlyArchived)
            )
            _statusMessage.value = if (res.isSuccess) {
                if (currentlyArchived) "Book restored from archive." else "Book archived."
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Archive toggle failed."
            }
        }
    }

    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            _busy.value = true
            val res = bookRepository.deleteBook(currentUserId, bookId)
            _busy.value = false
            _statusMessage.value = if (res.isSuccess) "Book permanently deleted." else (res.exceptionOrNull()?.localizedMessage ?: "Delete failed.")
        }
    }

    fun updateProfileSettings(
        displayName: String,
        penName: String,
        fontSizeSp: Long,
        dyslexiaSpacing: Boolean,
        focusMode: Boolean,
        autoSave: Boolean
    ) {
        viewModelScope.launch {
            val res = knowledgeRepository.updateUserProfile(
                currentUserId,
                mapOf(
                    "displayName" to displayName.trim().ifBlank { "Author" }.take(120),
                    "defaultPenName" to penName.trim().take(120),
                    "editorFontSizeSp" to fontSizeSp.coerceIn(12L, 32L),
                    "dyslexiaFriendlySpacing" to dyslexiaSpacing,
                    "focusModeDefault" to focusMode,
                    "autoSaveEnabled" to autoSave
                )
            )
            _statusMessage.value = if (res.isSuccess) "Author settings saved." else (res.exceptionOrNull()?.localizedMessage ?: "Settings update failed.")
        }
    }

    fun deleteAllCloudData(onComplete: () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val res = knowledgeRepository.deleteAllUserData(currentUserId, bookRepository)
            _busy.value = false
            if (res.isSuccess) {
                _statusMessage.value = "All cloud manuscripts and profile data deleted."
                onComplete()
            } else {
                _statusMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Account data deletion failed."
            }
        }
    }

    private companion object {
        const val TAG = "DashboardVM"
    }
}
