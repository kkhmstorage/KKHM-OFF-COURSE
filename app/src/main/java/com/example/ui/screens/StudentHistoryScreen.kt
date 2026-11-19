package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.GuideType
import com.example.data.model.OffCourseRecord
import com.example.data.model.SignatureStatus
import com.example.data.model.WeekVerificationStatus
import com.example.data.model.WeeklyStudyReport
import com.example.ui.components.PeriodLogsBreakdown
import com.example.ui.components.StatusBadge
import com.example.ui.components.signatureStatusColors
import com.example.ui.components.weekStatusColors
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.VerifiedGreen

fun resolveStudentHistoryKey(record: OffCourseRecord): String {
    return record.studentUsername.trim().lowercase().ifBlank {
        record.cicAdNo.trim().lowercase().ifBlank { record.studentUid }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentHistoryScreen(
    studentKey: String,
    allRecords: List<OffCourseRecord>,
    reportsByRecordId: Map<String, List<WeeklyStudyReport>>,
    isLoadingReports: Boolean,
    onLoadReports: (List<OffCourseRecord>) -> Unit,
    onOpenRecord: (String) -> Unit,
    onBack: () -> Unit
) {
    val studentRecords = remember(studentKey, allRecords) {
        allRecords.filter { resolveStudentHistoryKey(it).equals(studentKey, ignoreCase = true) }
            .sortedWith(
                compareByDescending<OffCourseRecord> { it.academicYear }
                    .thenByDescending { it.semester }
            )
    }

    LaunchedEffect(studentKey, studentRecords.map { it.id }) {
        if (studentRecords.isNotEmpty()) {
            onLoadReports(studentRecords)
        }
    }

    val expandedMap = remember(studentKey) { mutableStateMapOf<String, Boolean>() }
    val primary = studentRecords.firstOrNull()
    val distinctTerms = studentRecords.map { "${it.academicYear} (${it.semester})" }.distinct()
    val hasPriorSemesters = distinctTerms.size > 1

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(primary?.studentName ?: "Student Off-Course History") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("history_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (studentRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No Off-Course records found for this student.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Student Profile & Multi-Semester Summary Banner
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = primary?.studentName.orEmpty(),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = listOfNotNull(
                                        primary?.studentUsername?.takeIf { it.isNotBlank() }?.let { "@$it" },
                                        primary?.cicAdNo?.takeIf { it.isNotBlank() },
                                        primary?.classDepartment?.takeIf { it.isNotBlank() },
                                        primary?.stream?.takeIf { it.isNotBlank() }
                                    ).joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = null,
                                    tint = if (hasPriorSemesters) VerifiedGreen else MaterialTheme.colorScheme.primary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (hasPriorSemesters) {
                                            "Taken Off-Course across ${distinctTerms.size} Semesters (${studentRecords.size} total courses)"
                                        } else {
                                            "1 Semester Off-Course History (${distinctTerms.firstOrNull().orEmpty()})"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (hasPriorSemesters) {
                                            "Previous semesters recorded: ${distinctTerms.joinToString(", ")}"
                                        } else {
                                            "No earlier Off-Course semesters recorded before this term."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isLoadingReports) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Loading semester weekly activity logs…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Semester-by-Semester Cards
            items(studentRecords, key = { it.id }) { rec ->
                val reports = reportsByRecordId[rec.id].orEmpty().sortedBy { it.weekNumber }
                val isExpanded = expandedMap[rec.id] ?: (studentRecords.firstOrNull()?.id == rec.id)
                val (gBg, gFg) = signatureStatusColors(rec.guideSignatureStatus)
                val (pBg, pFg) = signatureStatusColors(rec.principalSignatureStatus)
                val guideTypeLabel = if (rec.guideType.isNotBlank()) {
                    GuideType.fromRaw(rec.guideType).label
                } else "College Teacher"

                ElevatedCard(
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().testTag("history_semester_card_${rec.id}")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                StatusBadge(
                                    text = "${rec.academicYear} • ${rec.semester} Semester",
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = rec.subjectTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "${rec.utilizationPercent}%",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Highlighted Exempted / Dropped Subject Box
                        Surface(
                            color = PendingAmber.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.AutoStories,
                                    contentDescription = null,
                                    tint = PendingAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = "Exempted / Dropped Subject",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = rec.exemptedSubject.ifBlank { "Not specified" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Guide: ${rec.guideName} ($guideTypeLabel) • ${rec.periodsPerWeek} Periods/wk • Logged: ${rec.completedWeeksCount}/18 Wks (Verified: ${rec.verifiedWeeksCount})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusBadge("Guide: ${SignatureStatus.fromRaw(rec.guideSignatureStatus).label}", gBg, gFg)
                            StatusBadge("Principal: ${SignatureStatus.fromRaw(rec.principalSignatureStatus).label}", pBg, pFg)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { expandedMap[rec.id] = !isExpanded },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (isExpanded) "Hide Weeks (${reports.size})" else "Show Weeks (${reports.size})")
                            }
                            FilledTonalButton(
                                onClick = { onOpenRecord(rec.id) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Open Record Book")
                            }
                        }

                        if (isExpanded) {
                            HorizontalDivider()
                            if (reports.isEmpty()) {
                                Text(
                                    text = "No weekly activity logs recorded in this semester yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    reports.forEach { rep ->
                                        val (wBg, wFg) = weekStatusColors(rep.verificationStatus)
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Week ${rep.weekNumber} (${rep.dateFrom} → ${rep.dateTo})",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    StatusBadge(
                                                        WeekVerificationStatus.fromRaw(rep.verificationStatus).label,
                                                        wBg,
                                                        wFg
                                                    )
                                                }
                                                Text(
                                                    text = "Periods: ${rep.utilizedPeriods}/${rep.allocatedPeriods} (${rep.utilizationPercent}%) • Focus: ${rep.focusRating}/5",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                if (rep.description.isNotBlank()) {
                                                    Text(
                                                        text = rep.description,
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                                PeriodLogsBreakdown(periodLogs = rep.periodLogs)
                                                if (rep.authorizedSignature.isNotBlank()) {
                                                    Text(
                                                        text = "Approved by: ${rep.authorizedSignature}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = VerifiedGreen
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
