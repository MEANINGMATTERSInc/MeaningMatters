package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AuthorVoice
import com.example.data.model.BookPurpose
import com.example.data.model.CanonicalLedger
import com.example.ui.components.EditorialCard
import com.example.ui.components.GovernanceNoticeBanner
import com.example.ui.components.StatusPill
import com.example.ui.viewmodel.BookWorkspaceViewModel

@Composable
fun PurposeAndLedgerTab(
    viewModel: BookWorkspaceViewModel,
    purpose: BookPurpose?,
    ledger: CanonicalLedger?,
    voice: AuthorVoice?
) {
    var selectedSection by remember { mutableIntStateOf(0) } // 0 = Purpose, 1 = Canonical Ledger, 2 = Author Voice

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedSection == 0,
                    onClick = { selectedSection = 0 },
                    label = { Text("Book Purpose") },
                    modifier = Modifier.testTag("subtab_purpose")
                )
                FilterChip(
                    selected = selectedSection == 1,
                    onClick = { selectedSection = 1 },
                    label = { Text("Canonical Ledger") },
                    modifier = Modifier.testTag("subtab_ledger")
                )
                FilterChip(
                    selected = selectedSection == 2,
                    onClick = { selectedSection = 2 },
                    label = { Text("Author Voice") },
                    modifier = Modifier.testTag("subtab_voice")
                )
            }
        }

        when (selectedSection) {
            0 -> item { PurposeEditorSection(purpose = purpose, viewModel = viewModel) }
            1 -> item { CanonicalLedgerSection(ledger = ledger, viewModel = viewModel) }
            2 -> item { AuthorVoiceSection(voice = voice, viewModel = viewModel) }
        }
    }
}

@Composable
private fun PurposeEditorSection(
    purpose: BookPurpose?,
    viewModel: BookWorkspaceViewModel
) {
    val p = purpose ?: BookPurpose(bookId = viewModel.bookId)
    var title by remember(p.updatedAt, p.title) { mutableStateOf(p.title) }
    var subtitle by remember(p.updatedAt, p.subtitle) { mutableStateOf(p.subtitle) }
    var genre by remember(p.updatedAt, p.genre) { mutableStateOf(p.genre) }
    var audience by remember(p.updatedAt, p.intendedAudience) { mutableStateOf(p.intendedAudience) }
    var centralPurpose by remember(p.updatedAt, p.centralPurpose) { mutableStateOf(p.centralPurpose) }
    var mainThesis by remember(p.updatedAt, p.mainThesis) { mutableStateOf(p.mainThesis) }
    var transformation by remember(p.updatedAt, p.readerTransformation) { mutableStateOf(p.readerTransformation) }
    var values by remember(p.updatedAt, p.authorValues) { mutableStateOf(p.authorValues) }
    var boundaries by remember(p.updatedAt, p.intellectualBoundaries) { mutableStateOf(p.intellectualBoundaries) }
    var style by remember(p.updatedAt, p.desiredStyle) { mutableStateOf(p.desiredStyle) }
    var generatedDoc by remember(p.updatedAt, p.generatedDocument) { mutableStateOf(p.generatedDocument) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GovernanceNoticeBanner(
            text = "Meaning before generation: Approve your Book Purpose Document so every AI agent respects your declared thesis and boundaries."
        )

        EditorialCard(
            title = "Book Purpose Engine",
            subtitle = "Core foundation for MeaningMatters authoring governance",
            trailing = {
                StatusPill(
                    text = if (p.approved) "APPROVED" else "DRAFT",
                    containerColor = if (p.approved) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (p.approved) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Book Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = subtitle, onValueChange = { subtitle = it }, label = { Text("Subtitle") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = genre, onValueChange = { genre = it }, label = { Text("Genre") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = audience, onValueChange = { audience = it }, label = { Text("Audience") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = centralPurpose, onValueChange = { centralPurpose = it }, label = { Text("Central Purpose") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = mainThesis, onValueChange = { mainThesis = it }, label = { Text("Main Thesis") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = transformation, onValueChange = { transformation = it }, label = { Text("Reader Transformation") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = values, onValueChange = { values = it }, label = { Text("Author's Values") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = boundaries, onValueChange = { boundaries = it }, label = { Text("Intellectual Boundaries") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = style, onValueChange = { style = it }, label = { Text("Desired Style") }, modifier = Modifier.fillMaxWidth())

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.savePurpose(
                                p.copy(
                                    title = title, subtitle = subtitle, genre = genre,
                                    intendedAudience = audience, centralPurpose = centralPurpose,
                                    mainThesis = mainThesis, readerTransformation = transformation,
                                    authorValues = values, intellectualBoundaries = boundaries,
                                    desiredStyle = style, generatedDocument = generatedDoc
                                )
                            )
                        },
                        modifier = Modifier.weight(1f).testTag("save_purpose_button")
                    ) {
                        Icon(Icons.Outlined.Save, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Save Draft")
                    }
                    Button(
                        onClick = {
                            viewModel.savePurpose(
                                p.copy(
                                    title = title, subtitle = subtitle, genre = genre,
                                    intendedAudience = audience, centralPurpose = centralPurpose,
                                    mainThesis = mainThesis, readerTransformation = transformation,
                                    authorValues = values, intellectualBoundaries = boundaries,
                                    desiredStyle = style, generatedDocument = generatedDoc,
                                    approved = true
                                )
                            )
                        },
                        modifier = Modifier.weight(1f).testTag("approve_purpose_button")
                    ) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Approve")
                    }
                }
            }
        }

        EditorialCard(
            title = "Structured Book Purpose Document",
            subtitle = "AI-synthesized charter editable and ratified by the author"
        ) {
            Button(
                onClick = { viewModel.generatePurposeDocument() },
                modifier = Modifier.fillMaxWidth().testTag("generate_purpose_doc_button")
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Generate Purpose Document with Gemini")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = generatedDoc,
                onValueChange = { generatedDoc = it },
                label = { Text("Book Purpose Charter (Editable)") },
                minLines = 6,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CanonicalLedgerSection(
    ledger: CanonicalLedger?,
    viewModel: BookWorkspaceViewModel
) {
    val l = ledger ?: CanonicalLedger(bookId = viewModel.bookId)
    var coreThesis by remember(l.updatedAt, l.coreThesis) { mutableStateOf(l.coreThesis) }
    var axiomsText by remember(l.updatedAt, l.foundationalAxioms) { mutableStateOf(l.foundationalAxioms.joinToString("\n")) }
    var epistemicStandards by remember(l.updatedAt, l.epistemicStandards) { mutableStateOf(l.epistemicStandards) }
    var definitionsText by remember(l.updatedAt, l.formalDefinitions) { mutableStateOf(l.formalDefinitions.joinToString("\n")) }
    var mathSpecs by remember(l.updatedAt, l.mathematicalSpecs) { mutableStateOf(l.mathematicalSpecs) }
    var glossaryText by remember(l.updatedAt, l.terminologicalGlossary) { mutableStateOf(l.terminologicalGlossary.joinToString("\n")) }
    var premisesText by remember(l.updatedAt, l.resolvedPremises) { mutableStateOf(l.resolvedPremises.joinToString("\n")) }
    var questionsText by remember(l.updatedAt, l.openQuestions) { mutableStateOf(l.openQuestions.joinToString("\n")) }

    fun splitLines(raw: String): List<String> = raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GovernanceNoticeBanner(
            text = "Stage 1 — Canonical Ledger: Every AI agent (Architect, Drafter, RED_TEAM, Continuity Auditor) obeys this ledger as the single authoritative reference."
        )

        EditorialCard(
            title = "Canonical Epistemic Ledger (v${l.version})",
            subtitle = "Foundational axioms, invariant terms, formalisms, and open questions",
            trailing = {
                StatusPill(
                    text = if (l.approved) "LOCKED" else "REVIEW",
                    containerColor = if (l.approved) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (l.approved) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { viewModel.initializeCanonicalLedgerWithAi() },
                    modifier = Modifier.fillMaxWidth().testTag("init_canonical_ledger_button")
                ) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Initialize Canonical Ledger with Gemini")
                }

                OutlinedTextField(value = coreThesis, onValueChange = { coreThesis = it }, label = { Text("Core Thesis") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = axiomsText, onValueChange = { axiomsText = it }, label = { Text("Foundational Axioms (one per line)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = definitionsText, onValueChange = { definitionsText = it }, label = { Text("Formal Definitions (one per line)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = mathSpecs, onValueChange = { mathSpecs = it }, label = { Text("Mathematical / Formal Specifications") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = glossaryText, onValueChange = { glossaryText = it }, label = { Text("Terminological Glossary (Term — Definition)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = premisesText, onValueChange = { premisesText = it }, label = { Text("Resolved Premises (one per line)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = questionsText, onValueChange = { questionsText = it }, label = { Text("Open Unresolved Questions (one per line)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = epistemicStandards, onValueChange = { epistemicStandards = it }, label = { Text("Epistemic Classification Standards") }, minLines = 2, modifier = Modifier.fillMaxWidth())

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            viewModel.saveCanonicalLedger(
                                l.copy(
                                    coreThesis = coreThesis,
                                    foundationalAxioms = splitLines(axiomsText),
                                    epistemicStandards = epistemicStandards,
                                    formalDefinitions = splitLines(definitionsText),
                                    mathematicalSpecs = mathSpecs,
                                    terminologicalGlossary = splitLines(glossaryText),
                                    resolvedPremises = splitLines(premisesText),
                                    openQuestions = splitLines(questionsText)
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Save Draft") }
                    Button(
                        onClick = {
                            viewModel.saveCanonicalLedger(
                                l.copy(
                                    coreThesis = coreThesis,
                                    foundationalAxioms = splitLines(axiomsText),
                                    epistemicStandards = epistemicStandards,
                                    formalDefinitions = splitLines(definitionsText),
                                    mathematicalSpecs = mathSpecs,
                                    terminologicalGlossary = splitLines(glossaryText),
                                    resolvedPremises = splitLines(premisesText),
                                    openQuestions = splitLines(questionsText),
                                    approved = true
                                )
                            )
                        },
                        modifier = Modifier.weight(1f).testTag("approve_ledger_button")
                    ) { Text("Approve & Lock") }
                }
            }
        }
    }
}

@Composable
private fun AuthorVoiceSection(
    voice: AuthorVoice?,
    viewModel: BookWorkspaceViewModel
) {
    val v = voice ?: AuthorVoice(bookId = viewModel.bookId)
    var tone by remember(v.updatedAt, v.writingTone) { mutableStateOf(v.writingTone) }
    var complexity by remember(v.updatedAt, v.sentenceComplexity) { mutableStateOf(v.sentenceComplexity) }
    var vocabulary by remember(v.updatedAt, v.vocabularyLevel) { mutableStateOf(v.vocabularyLevel) }
    var perspective by remember(v.updatedAt, v.narrativePerspective) { mutableStateOf(v.narrativePerspective) }
    var preferred by remember(v.updatedAt, v.preferredTerms) { mutableStateOf(v.preferredTerms.joinToString(", ")) }
    var avoided by remember(v.updatedAt, v.avoidedTerms) { mutableStateOf(v.avoidedTerms.joinToString(", ")) }
    var samples by remember(v.updatedAt, v.writingSamples) { mutableStateOf(v.writingSamples) }
    var notes by remember(v.updatedAt, v.styleNotes) { mutableStateOf(v.styleNotes) }

    EditorialCard(
        title = "Author Voice Engine",
        subtitle = "High-level stylistic characteristics preserved across all chapters (never imitates living authors)"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = tone, onValueChange = { tone = it }, label = { Text("Writing Tone") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = complexity, onValueChange = { complexity = it }, label = { Text("Sentence Complexity") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = vocabulary, onValueChange = { vocabulary = it }, label = { Text("Vocabulary Register") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = perspective, onValueChange = { perspective = it }, label = { Text("Narrative Perspective") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = preferred, onValueChange = { preferred = it }, label = { Text("Preferred Terminology (comma-separated)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = avoided, onValueChange = { avoided = it }, label = { Text("Words & Phrases to Avoid (comma-separated)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = samples, onValueChange = { samples = it }, label = { Text("Author Writing Samples (Reference Cadence)") }, minLines = 4, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Editorial Style Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())

            Button(
                onClick = {
                    viewModel.saveAuthorVoice(
                        v.copy(
                            writingTone = tone,
                            sentenceComplexity = complexity,
                            vocabularyLevel = vocabulary,
                            narrativePerspective = perspective,
                            preferredTerms = preferred.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                            avoidedTerms = avoided.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                            writingSamples = samples,
                            styleNotes = notes
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().testTag("save_voice_button")
            ) {
                Icon(Icons.Outlined.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save Author Voice Profile")
            }
        }
    }
}
