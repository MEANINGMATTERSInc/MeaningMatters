package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.HistoryEdu
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.data.model.AIInteraction
import com.example.data.model.Chapter
import com.example.data.model.MuhasabahFramework
import com.example.data.model.ReviewFinding
import com.example.data.model.ReviewReport
import com.example.data.model.ReviewType
import com.example.ui.components.EditorialCard
import com.example.ui.components.GovernanceNoticeBanner
import com.example.ui.components.StatusPill
import com.example.ui.viewmodel.BookWorkspaceViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ReviewAndGovernanceTab(
    viewModel: BookWorkspaceViewModel,
    chapters: List<Chapter>,
    selectedChapterId: String?,
    reviews: List<ReviewReport>,
    aiLogs: List<AIInteraction>,
    onReviseChapterFromReview: (String) -> Unit
) {
    var activeSubTab by remember { mutableIntStateOf(0) } // 0 = Muhasabah & RED_TEAM & Consistency, 1 = AI Audit Trail
    var scopeChapterId by remember(selectedChapterId, chapters) {
        mutableStateOf(selectedChapterId ?: chapters.firstOrNull()?.id)
    }
    val targetChapter = remember(chapters, scopeChapterId) {
        chapters.firstOrNull { it.id == scopeChapterId }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = activeSubTab == 0,
                    onClick = { activeSubTab = 0 },
                    label = { Text("Editorial Reviews (${reviews.size})") },
                    modifier = Modifier.testTag("subtab_reviews")
                )
                FilterChip(
                    selected = activeSubTab == 1,
                    onClick = { activeSubTab = 1 },
                    label = { Text("AI Governance Log (${aiLogs.size})") },
                    modifier = Modifier.testTag("subtab_audit_log")
                )
            }
        }

        if (activeSubTab == 0) {
            item {
                GovernanceNoticeBanner(
                    text = "Muhasabah Ethical Guard: Reviews evaluate evidence, intent, harm, and patterns across ideas or institutions — never labeling real individuals as evil."
                )
            }

            // Target Chapter Selector & Review Runners
            item {
                EditorialCard(
                    title = "Muhasabah, RED_TEAM & Consistency Engine",
                    subtitle = "Reflective accountability, Stage 5 adversarial critique, and Stage 7 whole-book verification"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (chapters.isNotEmpty()) {
                            Text("Chapter Scope (for Chapter-Level Reviews)", style = MaterialTheme.typography.labelLarge)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                chapters.forEach { ch ->
                                    FilterChip(
                                        selected = ch.id == scopeChapterId,
                                        onClick = { scopeChapterId = ch.id },
                                        label = { Text("Ch ${ch.orderIndex + 1}: ${ch.title.take(18)}") }
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { viewModel.runReview(ReviewType.MUHASABAH, targetChapter) },
                                modifier = Modifier.weight(1f).testTag("run_muhasabah_button")
                            ) {
                                Icon(Icons.Outlined.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Muhasabah")
                            }
                            Button(
                                enabled = targetChapter != null,
                                onClick = { viewModel.runReview(ReviewType.RED_TEAM, targetChapter) },
                                modifier = Modifier.weight(1f).testTag("run_redteam_button")
                            ) {
                                Icon(Icons.Outlined.Gavel, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("RED_TEAM")
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                enabled = chapters.isNotEmpty(),
                                onClick = { viewModel.runReview(ReviewType.CONSISTENCY, null) },
                                modifier = Modifier.weight(1f).testTag("run_consistency_button")
                            ) {
                                Icon(Icons.Outlined.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Consistency")
                            }
                            OutlinedButton(
                                enabled = chapters.isNotEmpty(),
                                onClick = { viewModel.runReview(ReviewType.WHOLE_BOOK, null) },
                                modifier = Modifier.weight(1f).testTag("run_whole_book_button")
                            ) {
                                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Stage 7 Audit")
                            }
                        }

                        Text(
                            text = "Muhasabah Reflective Risk Lens: ${MuhasabahFramework.TEN_QUALITIES_OF_EVIL.joinToString(" · ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (reviews.isEmpty()) {
                item {
                    EditorialCard(
                        title = "No Reviews Recorded Yet",
                        subtitle = "Run Muhasabah, RED_TEAM, Consistency, or Stage 7 Whole-Book Verification above"
                    ) {
                        Text(
                            "Every review report is stored persistently with actionable findings, flagged qualities, and unresolved questions so your revisions stay auditable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(reviews, key = { it.id }) { report ->
                    ReviewReportCard(
                        report = report,
                        chapterTitle = chapters.firstOrNull { it.id == report.chapterId }?.title,
                        onAcknowledge = { viewModel.acknowledgeReview(report.id) },
                        onReviseInStudio = if (report.chapterId.isNotBlank()) {
                            { onReviseChapterFromReview(report.chapterId) }
                        } else null
                    )
                }
            }
        } else {
            item {
                GovernanceNoticeBanner(
                    text = "Author Governance Audit Trail: Every AI authoring, blueprint, and review action is logged with its agent role, model, and your approval decision."
                )
            }
            if (aiLogs.isEmpty()) {
                item {
                    EditorialCard(title = "No AI Actions Logged Yet", subtitle = "AI interactions appear here automatically") {
                        Text(
                            "Your book currently has zero AI interaction records.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(aiLogs, key = { it.id }) { log ->
                    AiAuditLogCard(log = log)
                }
            }
        }
    }
}

@Composable
private fun ReviewReportCard(
    report: ReviewReport,
    chapterTitle: String?,
    onAcknowledge: () -> Unit,
    onReviseInStudio: (() -> Unit)?
) {
    val type = ReviewType.fromRaw(report.reviewType)
    val dateFmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.US) }
    val whenStr = report.createdAt?.toDate()?.let { dateFmt.format(it) } ?: "Just now"

    EditorialCard(
        title = type.label,
        subtitle = "${chapterTitle ?: "Whole Book"}  •  $whenStr",
        trailing = {
            StatusPill(
                text = "Score ${report.overallScore}/100",
                containerColor = when {
                    report.overallScore >= 80 -> MaterialTheme.colorScheme.tertiaryContainer
                    report.overallScore >= 60 -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.errorContainer
                },
                contentColor = when {
                    report.overallScore >= 80 -> MaterialTheme.colorScheme.onTertiaryContainer
                    report.overallScore >= 60 -> MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.onErrorContainer
                }
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(report.summary, style = MaterialTheme.typography.bodyMedium)

            if (report.qualitiesFlagged.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    report.qualitiesFlagged.forEach { q ->
                        StatusPill(
                            text = "Lens: $q",
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (report.findings.isNotEmpty()) {
                Text("Actionable Findings (${report.findings.size})", style = MaterialTheme.typography.titleSmall)
                report.findings.forEach { f: ReviewFinding ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(f.category, style = MaterialTheme.typography.titleSmall)
                                StatusPill(
                                    text = f.severity,
                                    containerColor = if (f.severity == "HIGH") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = if (f.severity == "HIGH") MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            if (f.chapterRef.isNotBlank()) {
                                Text(f.chapterRef, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            }
                            Text(f.description, style = MaterialTheme.typography.bodySmall)
                            if (f.recommendation.isNotBlank()) {
                                Text(
                                    text = "Recommendation: ${f.recommendation}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            if (report.unresolvedQuestions.isNotEmpty()) {
                Text("Honest Unresolved Questions", style = MaterialTheme.typography.titleSmall)
                report.unresolvedQuestions.forEach { q ->
                    Text("• $q", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (!report.authorAcknowledged) {
                    OutlinedButton(onClick = onAcknowledge, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Acknowledge")
                    }
                } else {
                    StatusPill(
                        text = "Author Acknowledged",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                if (onReviseInStudio != null) {
                    Button(onClick = onReviseInStudio, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.HistoryEdu, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Revise Chapter")
                    }
                }
            }
        }
    }
}

@Composable
private fun AiAuditLogCard(log: AIInteraction) {
    val dateFmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.US) }
    val timeText = log.createdAt?.toDate()?.let { dateFmt.format(it) } ?: "Just now"

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(log.agentRole, style = MaterialTheme.typography.titleSmall)
                StatusPill(
                    text = log.decision,
                    containerColor = when (log.decision) {
                        "ACCEPTED", "MODIFIED" -> MaterialTheme.colorScheme.tertiaryContainer
                        "REJECTED" -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentColor = when (log.decision) {
                        "ACCEPTED", "MODIFIED" -> MaterialTheme.colorScheme.onTertiaryContainer
                        "REJECTED" -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            }
            Text(
                text = "${log.actionType}  •  ${log.modelName}  •  $timeText",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (log.promptSummary.isNotBlank()) {
                Text(log.promptSummary, style = MaterialTheme.typography.bodySmall)
            }
            if (log.outputSummary.isNotBlank()) {
                Text(
                    text = log.outputSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3
                )
            }
        }
    }
}
