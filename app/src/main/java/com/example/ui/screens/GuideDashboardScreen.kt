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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AcademicTerm
import com.example.data.model.OffCourseRecord
import com.example.data.model.SignatureStatus
import com.example.data.model.UiState
import com.example.ui.components.AcademicTermFilterBar
import com.example.ui.components.StatusBadge
import com.example.ui.components.UtilizationGaugeRing
import com.example.ui.components.signatureStatusColors
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.VerifiedGreen

@Composable
fun GuideDashboardScreen(
    recordsState: UiState<List<OffCourseRecord>>,
    terms: List<AcademicTerm>,
    activeUsername: String,
    userEmail: String,
    selectedYear: String,
    selectedSemester: String,
    onSelectYear: (String) -> Unit,
    onSelectSemester: (String) -> Unit,
    onShowAllTerms: () -> Unit = {},
    onOpenRecord: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var onlyPending by remember { mutableStateOf(false) }

    when (recordsState) {
        is UiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Error -> Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(recordsState.message, color = MaterialTheme.colorScheme.error)
        }
        is UiState.Success -> {
            val all = recordsState.data
            val filtered = all.filter { rec ->
                (selectedYear == "ALL" || rec.academicYear == selectedYear) &&
                    (selectedSemester == "ALL" || rec.semester == selectedSemester) &&
                    (!onlyPending || rec.guideSignatureStatus != SignatureStatus.APPROVED.name ||
                        rec.completedWeeksCount > rec.verifiedWeeksCount) &&
                    (searchQuery.isBlank() ||
                        rec.studentName.contains(searchQuery, ignoreCase = true) ||
                        rec.cicAdNo.contains(searchQuery, ignoreCase = true) ||
                        rec.subjectTitle.contains(searchQuery, ignoreCase = true) ||
                        rec.exemptedSubject.contains(searchQuery, ignoreCase = true))
            }
            val totalAlloc = filtered.sumOf { it.totalAllocatedPeriods }
            val totalUtil = filtered.sumOf { it.totalUtilizedPeriods }
            val avgUtilPct = if (totalAlloc > 0) ((totalUtil.toDouble() / totalAlloc) * 100.0).toInt().coerceIn(0, 100) else 0
            val pendingVerifications = filtered.sumOf { (it.completedWeeksCount - it.verifiedWeeksCount).coerceAtLeast(0L) }

            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            UtilizationGaugeRing(
                                percent = avgUtilPct,
                                utilizedPeriods = totalUtil,
                                allocatedPeriods = totalAlloc,
                                ringSize = 96.dp
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("KKHM Guide Console", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text = "Assigned Students: ${filtered.size} • Pending Approvals: $pendingVerifications",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Inspect each student's per-period work and approve to unlock their next week.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text("Search student, CIC No, Off-Course or Exempted subject") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("guide_search_input")
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = onlyPending,
                            onClick = { onlyPending = !onlyPending },
                            label = { Text("Pending Weekly Approvals ($pendingVerifications)") }
                        )
                        AcademicTermFilterBar(
                            terms = terms,
                            records = all,
                            selectedYear = selectedYear,
                            selectedSemester = selectedSemester,
                            onSelectYear = onSelectYear,
                            onSelectSemester = onSelectSemester
                        )
                    }
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
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.SupervisorAccount,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (all.isEmpty()) "No Assigned Students Yet"
                                    else "No Assigned Students in $selectedYear ($selectedSemester)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = if (all.isEmpty()) {
                                        "Students assigned to '@$activeUsername' by KKHM Admin will appear here automatically."
                                    } else {
                                        "You have ${all.size} assigned student(s) in other Academic Years or Semesters."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (all.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = {
                                            onlyPending = false
                                            searchQuery = ""
                                            onShowAllTerms()
                                        },
                                        modifier = Modifier.testTag("guide_show_all_terms_button")
                                    ) {
                                        Text("Show All Years & Semesters")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { record ->
                        val (bg, fg) = signatureStatusColors(record.guideSignatureStatus)
                        val pendingWks = (record.completedWeeksCount - record.verifiedWeeksCount).coerceAtLeast(0L)
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
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(record.studentName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(
                                            "${record.cicAdNo} • ${record.subjectTitle}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (record.exemptedSubject.isNotBlank()) {
                                            Text(
                                                "Exempted Subject: ${record.exemptedSubject}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    StatusBadge(
                                        SignatureStatus.fromRaw(record.guideSignatureStatus).label,
                                        bg,
                                        fg
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StatusBadge(
                                        text = "${record.periodsPerWeek} Per./Wk • ${record.utilizationPercent}%",
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    if (pendingWks > 0) {
                                        StatusBadge(
                                            text = "$pendingWks Week(s) Pending Approval",
                                            containerColor = PendingAmber.copy(alpha = 0.2f),
                                            contentColor = PendingAmber
                                        )
                                    } else if (record.verifiedWeeksCount > 0) {
                                        StatusBadge(
                                            text = "${record.verifiedWeeksCount} Wks Approved",
                                            containerColor = VerifiedGreen.copy(alpha = 0.16f),
                                            contentColor = VerifiedGreen
                                        )
                                    }
                                }

                                Text(
                                    text = "${record.academicYear} (${record.semester}) • Logged: ${record.completedWeeksCount}/18 Wks (Approved: ${record.verifiedWeeksCount})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = { onOpenRecord(record.id) },
                                    modifier = Modifier.fillMaxWidth().testTag("guide_inspect_${record.id}")
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (pendingWks > 0) "Inspect & Approve ($pendingWks Pending)" else "Inspect Period Logs & Approve")
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
