package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.WritingAssistAction
import com.example.core.DiffLineType
import com.example.core.TextDiffEngine
import com.example.data.model.Chapter
import com.example.data.model.ChapterStatus
import com.example.data.model.ManuscriptVersion
import com.example.data.model.countWords
import com.example.ui.components.EditorialCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.EditorialSerifFamily
import com.example.ui.theme.EpistemicMonoFamily
import com.example.ui.viewmodel.AiTaskState
import com.example.ui.viewmodel.BookWorkspaceViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun WritingStudioTab(
    viewModel: BookWorkspaceViewModel,
    chapters: List<Chapter>,
    selectedChapterId: String?,
    versions: List<ManuscriptVersion>,
    aiState: AiTaskState,
    editorFontSizeSp: Int,
    autoSaveEnabled: Boolean,
    focusMode: Boolean,
    onToggleFocusMode: () -> Unit
) {
    val activeChapter = remember(chapters, selectedChapterId) {
        chapters.firstOrNull { it.id == selectedChapterId } ?: chapters.firstOrNull()
    }

    LaunchedEffect(activeChapter?.id) {
        if (activeChapter != null && selectedChapterId != activeChapter.id) {
            viewModel.selectChapter(activeChapter.id)
        }
    }

    if (activeChapter == null) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            EditorialCard(
                title = "AI Writing Studio",
                subtitle = "No chapters created yet"
            ) {
                Text(
                    "Open the Architect tab to generate a complete cumulative Book Blueprint or add your first chapter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    var draftContent by remember(activeChapter.id) { mutableStateOf(activeChapter.content) }
    var lastSavedContent by remember(activeChapter.id) { mutableStateOf(activeChapter.content) }
    var customDirective by remember { mutableStateOf("") }
    var showVersionHistoryDialog by remember { mutableStateOf(false) }
    var showSaveCheckpointDialog by remember { mutableStateOf(false) }

    // Sync external updates (e.g., AI accepted suggestion or version restore) cleanly
    LaunchedEffect(activeChapter.content) {
        if (activeChapter.content != lastSavedContent) {
            draftContent = activeChapter.content
            lastSavedContent = activeChapter.content
        }
    }

    // Debounced cloud autosave
    LaunchedEffect(draftContent, autoSaveEnabled) {
        if (autoSaveEnabled && draftContent != lastSavedContent) {
            delay(1800L)
            lastSavedContent = draftContent
            viewModel.updateChapterFields(
                activeChapter.id,
                mapOf(
                    "content" to draftContent,
                    "status" to if (activeChapter.status == ChapterStatus.OUTLINE.name && draftContent.isNotBlank()) ChapterStatus.DRAFT.name else activeChapter.status
                ),
                silent = true
            )
        }
    }

    val liveWords = remember(draftContent) { countWords(draftContent) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Chapter Switcher Strip (hidden in Focus Mode)
        if (!focusMode) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(chapters, key = { it.id }) { ch ->
                        FilterChip(
                            selected = ch.id == activeChapter.id,
                            onClick = { viewModel.selectChapter(ch.id) },
                            label = {
                                Text("Ch ${ch.orderIndex + 1}: ${ch.title.take(22)}")
                            }
                        )
                    }
                }
            }
        }

        // Studio Action Header Bar
        item {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeChapter.title,
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = "$liveWords / ${activeChapter.targetWordCount} words  •  v${activeChapter.versionNumber}  •  ${versions.size} checkpoints",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row {
                            IconButton(
                                onClick = { showSaveCheckpointDialog = true },
                                modifier = Modifier.testTag("save_checkpoint_button")
                            ) {
                                Icon(Icons.Outlined.BookmarkAdd, contentDescription = "Save checkpoint version")
                            }
                            IconButton(
                                onClick = { showVersionHistoryDialog = true },
                                modifier = Modifier.testTag("open_version_history_button")
                            ) {
                                Icon(Icons.Outlined.History, contentDescription = "Version history and diffs")
                            }
                            IconButton(
                                onClick = onToggleFocusMode,
                                modifier = Modifier.testTag("toggle_focus_mode_button")
                            ) {
                                Icon(
                                    if (focusMode) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                                    contentDescription = "Toggle distraction-free focus mode"
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Chapter State Selector & Quick Formatting
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChapterStatus.entries.forEach { st ->
                            val selected = activeChapter.status == st.name
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    viewModel.updateChapterFields(
                                        activeChapter.id,
                                        mapOf(
                                            "status" to st.name,
                                            "fullProseDrafted" to (liveWords >= 100L || activeChapter.fullProseDrafted)
                                        )
                                    )
                                },
                                label = { Text(st.label) }
                            )
                        }
                        AssistChip(
                            onClick = {
                                val prefix = if (draftContent.isEmpty() || draftContent.endsWith("\n\n")) "" else "\n\n"
                                draftContent += "${prefix}## New Section Heading\n\n"
                            },
                            label = { Text("+ Heading") },
                            leadingIcon = { Icon(Icons.Outlined.Title, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        AssistChip(
                            onClick = {
                                lastSavedContent = draftContent
                                viewModel.updateChapterFields(activeChapter.id, mapOf("content" to draftContent))
                            },
                            label = { Text("Save Now") },
                            leadingIcon = { Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
            }
        }

        // Streaming / Pending AI Suggestion Human-Approval Panel
        if (aiState is AiTaskState.Streaming) {
            item {
                EditorialCard(
                    title = "Live AI Draft Stream",
                    subtitle = "Generating grounded in your Canonical Ledger & Author Voice…"
                ) {
                    Text(
                        text = aiState.accumulatedText,
                        style = TextStyle(
                            fontFamily = EditorialSerifFamily,
                            fontSize = editorFontSizeSp.sp,
                            lineHeight = (editorFontSizeSp + 9).sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        } else if (aiState is AiTaskState.SuggestionReady && aiState.chapterId == activeChapter.id) {
            item {
                var editableSuggestion by remember(aiState.interactionId) { mutableStateOf(aiState.suggestedText) }
                EditorialCard(
                    title = "Author Approval Required — ${aiState.action.label}",
                    subtitle = "Human authority: review, edit, accept, or reject. Prior draft is automatically checkpointed."
                ) {
                    OutlinedTextField(
                        value = editableSuggestion,
                        onValueChange = { editableSuggestion = it },
                        label = { Text("Proposed Prose (Editable before applying)") },
                        minLines = 6,
                        maxLines = 14,
                        textStyle = TextStyle(
                            fontFamily = EditorialSerifFamily,
                            fontSize = 15.sp,
                            lineHeight = 23.sp
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val decision = if (editableSuggestion == aiState.suggestedText) "ACCEPTED" else "MODIFIED"
                                viewModel.resolveAiSuggestion(activeChapter, decision, editableSuggestion)
                            },
                            modifier = Modifier.weight(1f).testTag("accept_ai_suggestion_button")
                        ) {
                            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (editableSuggestion == aiState.suggestedText) "Accept" else "Apply Edited")
                        }
                        OutlinedButton(
                            onClick = { viewModel.resolveAiSuggestion(activeChapter, "REJECTED", null) },
                            modifier = Modifier.testTag("reject_ai_suggestion_button")
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reject")
                        }
                    }
                }
            }
        }

        // Distraction-Free Editorial Manuscript Canvas
        item {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (!focusMode && activeChapter.coreProposition.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Proposition: ${activeChapter.coreProposition}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    OutlinedTextField(
                        value = draftContent,
                        onValueChange = { draftContent = it },
                        placeholder = {
                            Text(
                                "Write your chapter prose here, or invoke an AI authoring agent below grounded in your Canonical Ledger…",
                                style = TextStyle(fontFamily = EditorialSerifFamily, fontSize = editorFontSizeSp.sp)
                            )
                        },
                        textStyle = TextStyle(
                            fontFamily = EditorialSerifFamily,
                            fontSize = editorFontSizeSp.sp,
                            lineHeight = (editorFontSizeSp + 10).sp
                        ),
                        minLines = if (focusMode) 22 else 14,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manuscript_editor_input")
                    )
                }
            }
        }

        // AI Authoring Agents Bar (hidden in Focus Mode)
        if (!focusMode) {
            item {
                EditorialCard(
                    title = "Governed AI Writing Agents",
                    subtitle = "All actions obey your Canonical Ledger, Author Voice, and Evidence Integrity rules"
                ) {
                    OutlinedTextField(
                        value = customDirective,
                        onValueChange = { customDirective = it },
                        label = { Text("Optional author directive (e.g., focus on Section 2 counterargument)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(WritingAssistAction.entries) { action ->
                            AssistChip(
                                onClick = {
                                    // Flush latest editor content into chapter snapshot before invoking AI
                                    val liveChapter = activeChapter.copy(content = draftContent, wordCount = liveWords)
                                    viewModel.runWritingAssist(liveChapter, action, customDirective)
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text(action.label) },
                                modifier = Modifier.testTag("ai_action_${action.name}")
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSaveCheckpointDialog) {
        var label by remember { mutableStateOf("Checkpoint v${activeChapter.versionNumber + 1}") }
        AlertDialog(
            onDismissRequest = { showSaveCheckpointDialog = false },
            title = { Text("Save Chapter Checkpoint") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Create an immutable snapshot of '${activeChapter.title}' ($liveWords words) in Cloud Firestore so you can compare diffs or restore anytime.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Checkpoint Label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("checkpoint_label_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveCheckpoint(activeChapter.copy(content = draftContent), label)
                        showSaveCheckpointDialog = false
                    },
                    modifier = Modifier.testTag("confirm_save_checkpoint_button")
                ) { Text("Save Checkpoint") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveCheckpointDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showVersionHistoryDialog) {
        VersionHistoryAndDiffDialog(
            chapter = activeChapter.copy(content = draftContent, wordCount = liveWords),
            versions = versions,
            onDismiss = { showVersionHistoryDialog = false },
            onRestore = { ver ->
                viewModel.restoreVersion(activeChapter.copy(content = draftContent), ver)
                showVersionHistoryDialog = false
            }
        )
    }
}

@Composable
private fun VersionHistoryAndDiffDialog(
    chapter: Chapter,
    versions: List<ManuscriptVersion>,
    onDismiss: () -> Unit,
    onRestore: (ManuscriptVersion) -> Unit
) {
    var selectedVersion by remember(versions) { mutableStateOf(versions.firstOrNull()) }
    var compareTargetVersion by remember { mutableStateOf<ManuscriptVersion?>(null) } // null = compare against Current Draft
    val dateFmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.US) }

    val diffSummary = remember(selectedVersion, compareTargetVersion, chapter.content) {
        val base = selectedVersion
        if (base == null) null
        else {
            val targetText = compareTargetVersion?.content ?: chapter.content
            TextDiffEngine.computeDiff(base.content, targetText)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CompareArrows, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Text("Version History & Diffs", style = MaterialTheme.typography.headlineSmall)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (versions.isEmpty()) {
                    Text(
                        "No checkpoints saved for this chapter yet. Tap the bookmark icon in the Writing Studio toolbar to save your first named checkpoint.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text("Saved Checkpoints (${versions.size})", style = MaterialTheme.typography.labelLarge)
                    versions.forEach { ver ->
                        val isSelected = selectedVersion?.id == ver.id
                        val timeText = ver.createdAt?.toDate()?.let { dateFmt.format(it) } ?: "Just now"
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "v${ver.versionNumber}: ${ver.label}",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = "${ver.wordCount} words  •  $timeText",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(onClick = { selectedVersion = ver }) {
                                            Text(if (isSelected) "Viewing" else "Diff")
                                        }
                                        TextButton(
                                            onClick = { onRestore(ver) },
                                            modifier = Modifier.testTag("restore_version_${ver.versionNumber}")
                                        ) {
                                            Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Restore")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (selectedVersion != null && diffSummary != null) {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Diff: v${selectedVersion!!.versionNumber} → ${compareTargetVersion?.let { "v${it.versionNumber}" } ?: "Current Draft"}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            val sign = if (diffSummary.wordDelta >= 0) "+" else ""
                            StatusPill(
                                text = "+${diffSummary.addedLines} / -${diffSummary.removedLines} lines ($sign${diffSummary.wordDelta}w)"
                            )
                        }

                        if (versions.size > 1) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = compareTargetVersion == null,
                                    onClick = { compareTargetVersion = null },
                                    label = { Text("vs Current Draft") }
                                )
                                versions.filter { it.id != selectedVersion?.id }.take(5).forEach { other ->
                                    FilterChip(
                                        selected = compareTargetVersion?.id == other.id,
                                        onClick = { compareTargetVersion = other },
                                        label = { Text("vs v${other.versionNumber}") }
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                if (diffSummary.addedLines == 0 && diffSummary.removedLines == 0) {
                                    Text(
                                        "Identical — no differences between these two versions.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    diffSummary.lines.take(80).forEach { line ->
                                        val bgColor = when (line.type) {
                                            DiffLineType.ADDED -> Color(0xFF2D6A4F).copy(alpha = 0.16f)
                                            DiffLineType.REMOVED -> Color(0xFFB03A2E).copy(alpha = 0.16f)
                                            DiffLineType.UNCHANGED -> Color.Transparent
                                        }
                                        val prefix = when (line.type) {
                                            DiffLineType.ADDED -> "+ "
                                            DiffLineType.REMOVED -> "- "
                                            DiffLineType.UNCHANGED -> "  "
                                        }
                                        val textColor = when (line.type) {
                                            DiffLineType.ADDED -> Color(0xFF1B5E20)
                                            DiffLineType.REMOVED -> Color(0xFF8E1B1B)
                                            DiffLineType.UNCHANGED -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        Text(
                                            text = "$prefix${line.text}",
                                            style = TextStyle(
                                                fontFamily = EpistemicMonoFamily,
                                                fontSize = 11.sp,
                                                lineHeight = 16.sp,
                                                color = textColor
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(bgColor)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (diffSummary.lines.size > 80) {
                                        Text(
                                            "… ${diffSummary.lines.size - 80} more lines in full manuscript diff",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
