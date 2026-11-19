package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AcademicTerm
import com.example.data.model.GuideType
import com.example.data.model.OffCourseRecord
import com.example.data.model.SignatureStatus
import com.example.data.model.UiState
import com.example.ui.components.AcademicTermFilterBar
import com.example.ui.components.StatusBadge
import com.example.ui.components.StreamBreakdownSection
import com.example.ui.components.UtilizationGaugeRing
import com.example.ui.components.signatureStatusColors
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.VerifiedGreen

@Composable
fun PrincipalDashboardScreen(
    recordsState: UiState<List<OffCourseRecord>>,
    terms: List<AcademicTerm>,
    userEmail: String,
    selectedYear: String,
    selectedSemester: String,
    onSelectYear: (String) -> Unit,
    onSelectSemester: (String) -> Unit,
    onShowAllTerms: () -> Unit = {},
    onOpenRecord: (String) -> Unit,
    onOpenStudentHistory: (String) -> Unit,
    onOpenAcademicSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    when (recordsState) {
        is UiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Error -> Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(recordsState.message, color = MaterialTheme.colorScheme.error)
        }
        is UiState.Success -> {
            val all = recordsState.data
            val semestersByStudent = remember(all) {
                all.groupBy { resolveStudentHistoryKey(it) }
                    .mapValues { (_, list) -> list.map { "${it.academicYear} (${it.semester})" }.distinct().size }
            }
            val filtered = all.filter { rec ->
                (selectedYear == "ALL" || rec.academicYear == selectedYear) &&
                    (selectedSemester == "ALL" || rec.semester == selectedSemester) &&
                    (searchQuery.isBlank() ||
                        rec.studentName.contains(searchQuery, ignoreCase = true) ||
                        rec.cicAdNo.contains(searchQuery, ignoreCase = true) ||
                        rec.subjectTitle.contains(searchQuery, ignoreCase = true) ||
                        rec.exemptedSubject.contains(searchQuery, ignoreCase = true) ||
                        rec.guideName.contains(searchQuery, ignoreCase = true))
            }

            val totalAlloc = filtered.sumOf { it.totalAllocatedPeriods }
            val totalUtil = filtered.sumOf { it.totalUtilizedPeriods }
            val collegeUtilPct = if (totalAlloc > 0) {
                ((totalUtil.toDouble() / totalAlloc.toDouble()) * 100.0).toInt().coerceIn(0, 100)
            } else 0

            val pendingPrincipalCount = filtered.count {
                it.principalSignatureStatus == SignatureStatus.PENDING.name
            }
            val approvedCount = filtered.count {
                it.principalSignatureStatus == SignatureStatus.APPROVED.name
            }
            val totalWeeksLogged = filtered.sumOf { it.completedWeeksCount }
            val totalWeeksVerified = filtered.sumOf { it.verifiedWeeksCount }
            val lowUtilizationAlerts = filtered.filter {
                it.totalAllocatedPeriods > 0L && it.utilizationPercent < 70
            }

            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Executive Overview Hero
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                UtilizationGaugeRing(
                                    percent = collegeUtilPct,
                                    utilizedPeriods = totalUtil,
                                    allocatedPeriods = totalAlloc,
                                    ringSize = 104.dp
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("KKHM Principal Overview", style = MaterialTheme.typography.titleLarge)
                                    Text(
                                        text = "Off-Course Subjects, Exempted Subjects & Multi-Semester History",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "$approvedCount/${filtered.size} Approved • $pendingPrincipalCount Pending Sign-off",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ExecutiveStatTile("Students", filtered.size.toString(), Modifier.weight(1f))
                                ExecutiveStatTile("Weeks Verified", "$totalWeeksVerified/$totalWeeksLogged", Modifier.weight(1f))
                                ExecutiveStatTile("Pending Sign", pendingPrincipalCount.toString(), Modifier.weight(1f))
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FilledTonalButton(
                                    onClick = {
                                        val csv = buildExecutiveCsv(filtered)
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, "KKHM Off-Course Summary ($selectedYear $selectedSemester)")
                                            putExtra(Intent.EXTRA_TEXT, csv)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share Executive Report"))
                                    },
                                    modifier = Modifier.weight(1f).testTag("principal_export_summary_button")
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Share CSV")
                                }
                                OutlinedButton(
                                    onClick = onOpenAcademicSettings,
                                    modifier = Modifier.weight(1f).testTag("principal_manage_terms_button")
                                ) {
                                    Text("Manage Years/Sem")
                                }
                            }
                        }
                    }
                }

                // Historical Year & Semester Filter
                item {
                    AcademicTermFilterBar(
                        terms = terms,
                        records = all,
                        selectedYear = selectedYear,
                        selectedSemester = selectedSemester,
                        onSelectYear = onSelectYear,
                        onSelectSemester = onSelectSemester
                    )
                }

                // Low Utilization Alert Card
                if (lowUtilizationAlerts.isNotEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.WarningAmber,
                                        contentDescription = "Low utilization alert",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        "Low Period Utilization (< 70%)",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                lowUtilizationAlerts.take(3).forEach { rec ->
                                    Text(
                                        "• ${rec.studentName} (${rec.subjectTitle}): ${rec.utilizationPercent}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }

                // Stream Breakdown
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("CIC Stream Enrollment Breakdown", style = MaterialTheme.typography.titleMedium)
                            StreamBreakdownSection(records = filtered)
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text("Filter by student, exempted subject, guide, CIC No, or subject") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("principal_search_input")
                    )
                }

                if (filtered.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text("No Records Match Filter", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = if (all.isEmpty()) "Records across KKHM Islamic & Arts College will appear here."
                                    else "There are ${all.size} record(s) under other Academic Years or Semesters.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (all.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = {
                                            searchQuery = ""
                                            onShowAllTerms()
                                        },
                                        modifier = Modifier.testTag("principal_show_all_terms_button")
                                    ) {
                                        Text("Show All Years & Semesters")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { record ->
                        val (pBg, pFg) = signatureStatusColors(record.principalSignatureStatus)
                        val (gBg, gFg) = signatureStatusColors(record.guideSignatureStatus)
                        val studentKey = resolveStudentHistoryKey(record)
                        val totalSemesters = semestersByStudent[studentKey] ?: 1
                        val guideTypeShort = if (record.guideType == GuideType.EXTERNAL.name) "External" else "College"

                        ElevatedCard(
                            onClick = { onOpenRecord(record.id) },
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(record.studentName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(
                                            "${record.cicAdNo} • ${record.stream} • ${record.academicYear} (${record.semester})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        "${record.utilizationPercent}%",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Text(
                                    text = "Off-Course: ${record.subjectTitle}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Surface(
                                    color = PendingAmber.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Exempted Subject: ${record.exemptedSubject.ifBlank { "Not specified" }}",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                                Text(
                                    text = "Guide: ${record.guideName} ($guideTypeShort) • Logged: ${record.completedWeeksCount}/18 Wks",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StatusBadge(
                                        text = if (totalSemesters > 1) "Past Semesters: $totalSemesters Terms" else "1 Semester Taken",
                                        containerColor = if (totalSemesters > 1) VerifiedGreen.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = if (totalSemesters > 1) VerifiedGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    StatusBadge(
                                        "Guide: ${SignatureStatus.fromRaw(record.guideSignatureStatus).label}",
                                        gBg,
                                        gFg
                                    )
                                    StatusBadge(
                                        "Prin: ${SignatureStatus.fromRaw(record.principalSignatureStatus).label}",
                                        pBg,
                                        pFg
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onOpenStudentHistory(studentKey) },
                                        modifier = Modifier.weight(1f).testTag("principal_history_${record.id}")
                                    ) {
                                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("All Semesters ($totalSemesters)")
                                    }
                                    FilledTonalButton(
                                        onClick = { onOpenRecord(record.id) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Open Record Book")
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
}

@Composable
private fun ExecutiveStatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun buildExecutiveCsv(records: List<OffCourseRecord>): String {
    val header = "Student,CIC_Ad_No,Stream,Year,Semester,OffCourse_Subject,Exempted_Subject,Guide,Guide_Type,Periods_Utilized,Periods_Allocated,Utilization_Pct,Weeks_Verified,Guide_Status,Principal_Status"
    val rows = records.joinToString("\n") { r ->
        listOf(
            r.studentName,
            r.cicAdNo,
            r.stream,
            r.academicYear,
            r.semester,
            r.subjectTitle,
            r.exemptedSubject,
            r.guideName,
            r.guideType,
            r.totalUtilizedPeriods,
            r.totalAllocatedPeriods,
            "${r.utilizationPercent}%",
            "${r.verifiedWeeksCount}/${r.completedWeeksCount}",
            r.guideSignatureStatus,
            r.principalSignatureStatus
        ).joinToString(",") { "\"${it.toString().replace("\"", "'")}\"" }
    }
    return "$header\n$rows"
}
