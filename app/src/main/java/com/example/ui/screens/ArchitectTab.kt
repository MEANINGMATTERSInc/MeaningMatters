package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.Book
import com.example.data.model.Chapter
import com.example.data.model.ChapterStatus
import com.example.ui.components.EditorialCard
import com.example.ui.components.PipelineProgressCard
import com.example.ui.components.StatusPill
import com.example.ui.viewmodel.BookWorkspaceViewModel

@Composable
fun ArchitectTab(
    viewModel: BookWorkspaceViewModel,
    book: Book?,
    chapters: List<Chapter>,
    pipelineActive: Boolean,
    onOpenChapterInStudio: (String) -> Unit
) {
    var desiredChapterCount by remember { mutableFloatStateOf(8f) }
    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (book != null) {
            item {
                PipelineProgressCard(
                    currentStage = book.pipelineStage.toInt(),
                    lastCompleted = book.lastCompletedSection,
                    nextUnfinished = book.nextUnfinishedSection
                )
            }
        }

        item {
            EditorialCard(
                title = "Book Architect & Full-Book Pipeline",
                subtitle = "Cumulative blueprint with approved-chapter protection and session continuation"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Planned Chapters: ${desiredChapterCount.toInt()}",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "${chapters.count { it.blueprintApproved }}/${chapters.size} blueprints approved",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Slider(
                        value = desiredChapterCount,
                        onValueChange = { desiredChapterCount = it },
                        valueRange = 4f..16f,
                        steps = 11
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.generateCompleteBlueprint(desiredChapterCount.toInt()) },
                            modifier = Modifier.weight(1f).testTag("generate_blueprint_button")
                        ) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Generate Blueprint")
                        }
                        OutlinedButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.testTag("add_manual_chapter_button")
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add")
                        }
                    }

                    if (chapters.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { viewModel.continueFullBookPipelineNextChapter() },
                            enabled = !pipelineActive,
                            modifier = Modifier.fillMaxWidth().testTag("continue_pipeline_button")
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (pipelineActive) "Drafting Next Chapter…"
                                else "Stage 4: Draft Next Unfinished Chapter (Resume Pipeline)"
                            )
                        }
                    }
                }
            }
        }

        if (chapters.isEmpty()) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No chapters architected yet", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Generate a cumulative Stage 2 Book Blueprint or add chapters manually. Each chapter carries its own proposition, counterargument, evidence plan, and 7-point completion ledger.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            itemsIndexed(chapters, key = { _, ch -> ch.id }) { idx, chapter ->
                ChapterBlueprintCard(
                    index = idx,
                    totalCount = chapters.size,
                    chapter = chapter,
                    onMoveUp = { viewModel.moveChapter(chapter.id, -1) },
                    onMoveDown = { viewModel.moveChapter(chapter.id, +1) },
                    onDelete = { viewModel.deleteChapter(chapter.id) },
                    onOpenStudio = { onOpenChapterInStudio(chapter.id) },
                    onUpdateField = { updates -> viewModel.updateChapterFields(chapter.id, updates) }
                )
            }
        }
    }

    if (showAddDialog) {
        var title by remember { mutableStateOf("") }
        var partLabel by remember { mutableStateOf("Part I: Foundations") }
        var objective by remember { mutableStateOf("") }
        var proposition by remember { mutableStateOf("") }
        var targetWords by remember { mutableStateOf("3500") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Chapter") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = partLabel, onValueChange = { partLabel = it }, label = { Text("Part / Section") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Chapter Title *") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = proposition, onValueChange = { proposition = it }, label = { Text("Core Proposition") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = objective, onValueChange = { objective = it }, label = { Text("Chapter Objective") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = targetWords,
                        onValueChange = { targetWords = it.filter { c -> c.isDigit() } },
                        label = { Text("Target Word Count") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = title.isNotBlank(),
                    onClick = {
                        viewModel.addManualChapter(title, partLabel, objective, proposition, targetWords.toLongOrNull() ?: 3500L)
                        showAddDialog = false
                    }
                ) { Text("Add Chapter") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ChapterBlueprintCard(
    index: Int,
    totalCount: Int,
    chapter: Chapter,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onOpenStudio: () -> Unit,
    onUpdateField: (Map<String, Any?>) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val status = ChapterStatus.fromRaw(chapter.status)

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
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "CH ${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        if (chapter.partLabel.isNotBlank()) {
                            Text(
                                text = "• ${chapter.partLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = chapter.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (chapter.coreProposition.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = chapter.coreProposition,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row {
                    IconButton(onClick = onMoveUp, enabled = index > 0) {
                        Icon(Icons.Outlined.ArrowUpward, contentDescription = "Move chapter up", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onMoveDown, enabled = index < totalCount - 1) {
                        Icon(Icons.Outlined.ArrowDownward, contentDescription = "Move chapter down", modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(text = status.label)
                    StatusPill(
                        text = "${chapter.wordCount}/${chapter.targetWordCount}w",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StatusPill(
                        text = "${chapter.completionCriteriaMet}/7 Protocol",
                        containerColor = if (chapter.passesFullCompletionProtocol) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (chapter.passesFullCompletionProtocol) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = "Toggle blueprint details"
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(10.dp))
                BlueprintField("Objective", chapter.objective)
                BlueprintField("Argumentative Purpose", chapter.argumentativePurpose)
                BlueprintField("Required Premises", chapter.requiredPremises)
                BlueprintField("Required Evidence", chapter.requiredEvidence)
                BlueprintField("Formalisms", chapter.formalisms)
                BlueprintField("Strongest Counterargument", chapter.strongestCounterargument)
                BlueprintField("Expected Resolution", chapter.expectedResolution)
                BlueprintField("Transition to Next", chapter.transitionToNext)

                Spacer(Modifier.height(10.dp))
                Text("7-Point Full-Book Completion Protocol", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                CompletionCheckbox("1. Blueprint Approved", chapter.blueprintApproved) {
                    onUpdateField(mapOf("blueprintApproved" to it))
                }
                CompletionCheckbox("2. Full Prose Drafted", chapter.fullProseDrafted) {
                    onUpdateField(mapOf("fullProseDrafted" to it))
                }
                CompletionCheckbox("3. Required Arguments Developed", chapter.argumentsDeveloped) {
                    onUpdateField(mapOf("argumentsDeveloped" to it))
                }
                CompletionCheckbox("4. Evidence & Formal Claims Reviewed", chapter.evidenceReviewed) {
                    onUpdateField(mapOf("evidenceReviewed" to it))
                }
                CompletionCheckbox("5. RED_TEAM Adversarial Review Complete", chapter.redTeamCompleted) {
                    onUpdateField(mapOf("redTeamCompleted" to it))
                }
                CompletionCheckbox("6. Revisions Incorporated", chapter.revisionsIncorporated) {
                    onUpdateField(mapOf("revisionsIncorporated" to it))
                }
                CompletionCheckbox("7. Whole-Book Continuity Checked", chapter.continuityChecked) {
                    onUpdateField(mapOf("continuityChecked" to it))
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenStudio,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Write in Studio")
                }
                OutlinedButton(
                    onClick = {
                        onUpdateField(mapOf("blueprintApproved" to !chapter.blueprintApproved))
                    }
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (chapter.blueprintApproved) "Approved" else "Approve")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete chapter")
                }
            }
        }
    }
}

@Composable
private fun BlueprintField(label: String, value: String) {
    if (value.isBlank()) return
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CompletionCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}
