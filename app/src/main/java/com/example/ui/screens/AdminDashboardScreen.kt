package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AcademicTerm
import com.example.data.model.AccountStatus
import com.example.data.model.CollegeAccount
import com.example.data.model.GuideType
import com.example.data.model.OffCourseRecord
import com.example.data.model.SignatureStatus
import com.example.data.model.UiState
import com.example.data.model.UserRole
import com.example.data.model.WeekVerificationStatus
import com.example.data.model.WeeklyStudyReport
import com.example.data.remote.BulkImportRow
import com.example.data.remote.BulkImportUiState
import com.example.data.remote.SeededCredential
import com.example.ui.components.AcademicTermFilterBar
import com.example.ui.components.PeriodLogsBreakdown
import com.example.ui.components.StatusBadge
import com.example.ui.components.signatureStatusColors
import com.example.ui.components.weekStatusColors
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.VerifiedGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    accountsState: UiState<List<CollegeAccount>>,
    recordsState: UiState<List<OffCourseRecord>>,
    consolidatedReports: Map<String, List<WeeklyStudyReport>>,
    isLoadingConsolidated: Boolean,
    terms: List<AcademicTerm>,
    selectedYear: String,
    selectedSemester: String,
    seededCredentials: List<SeededCredential>,
    onDismissSeededCredentials: () -> Unit,
    onSelectYear: (String) -> Unit,
    onSelectSemester: (String) -> Unit,
    onShowAllTerms: () -> Unit = {},
    onSaveAccount: (AccountFormPayload) -> Unit,
    onEnrollStudentOffCourse: (AdminEnrollPayload) -> Unit,
    onResetPassword: (String, String) -> Unit,
    onToggleAccountStatus: (String, AccountStatus) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onSeedSampleAccounts: () -> Unit,
    onOpenRecord: (String) -> Unit,
    onOpenStudentHistory: (String) -> Unit,
    onReassignGuide: (String, String, String, String) -> Unit,
    onDeleteRecord: (String) -> Unit,
    onLoadConsolidatedReports: (List<OffCourseRecord>) -> Unit,
    onPrintConsolidated: (Int?, List<OffCourseRecord>) -> Unit,
    onShareConsolidatedPdf: (Int?, List<OffCourseRecord>) -> Unit,
    bulkImportState: BulkImportUiState = BulkImportUiState(),
    onStartBulkImport: (List<BulkImportRow>) -> Unit = {},
    onResetBulkImport: () -> Unit = {},
    onShowMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableIntStateOf(0) }
    var userSearch by remember { mutableStateOf("") }
    var roleFilter by remember { mutableStateOf<UserRole?>(null) }
    var recordSearch by remember { mutableStateOf("") }
    var selectedWeekFilter by remember { mutableStateOf<Int?>(null) }

    var showCreateEditDialog by remember { mutableStateOf<CollegeAccount?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var showEnrollDialog by remember { mutableStateOf(false) }
    var resetPasswordFor by remember { mutableStateOf<CollegeAccount?>(null) }
    var reassignGuideFor by remember { mutableStateOf<OffCourseRecord?>(null) }
    var showBulkImportDialog by remember { mutableStateOf(false) }

    val accounts = (accountsState as? UiState.Success)?.data.orEmpty()
    val records = (recordsState as? UiState.Success)?.data.orEmpty()
    val activeGuides = remember(accounts) {
        accounts.filter { it.role == UserRole.GUIDE.name && it.accountStatus == AccountStatus.ACTIVE.name }
    }
    val activeStudents = remember(accounts) {
        accounts.filter { it.role == UserRole.STUDENT.name && it.accountStatus == AccountStatus.ACTIVE.name }
    }

    val termRecords = remember(records, selectedYear, selectedSemester) {
        records.filter { rec ->
            (selectedYear == "ALL" || rec.academicYear == selectedYear) &&
                (selectedSemester == "ALL" || rec.semester == selectedSemester)
        }
    }

    val semestersByStudent = remember(records) {
        records.groupBy { resolveStudentHistoryKey(it) }
            .mapValues { (_, list) -> list.map { "${it.academicYear} (${it.semester})" }.distinct().size }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Persistent Academic Year & Semester selector at top of Admin Control Center
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AcademicTermFilterBar(
                    terms = terms,
                    records = records,
                    selectedYear = selectedYear,
                    selectedSemester = selectedSemester,
                    onSelectYear = onSelectYear,
                    onSelectSemester = onSelectSemester
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val tabs = listOf(
                        "Off-Course (${termRecords.size})",
                        "Weekly & Print",
                        "Users (${accounts.size})"
                    )
                    tabs.forEachIndexed { idx, label ->
                        SegmentedButton(
                            selected = selectedSection == idx,
                            onClick = { selectedSection = idx },
                            shape = SegmentedButtonDefaults.itemShape(index = idx, count = tabs.size),
                            modifier = Modifier.testTag(
                                when (idx) {
                                    0 -> "admin_tab_offcourse"
                                    1 -> "admin_tab_weekly_print"
                                    else -> "admin_tab_users"
                                }
                            )
                        ) {
                            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        when (selectedSection) {
            0 -> AdminOffCourseTab(
                recordsState = recordsState,
                allRecordsCount = records.size,
                termRecords = termRecords,
                semestersByStudent = semestersByStudent,
                selectedYear = selectedYear,
                selectedSemester = selectedSemester,
                searchQuery = recordSearch,
                onSearchChange = { recordSearch = it },
                onEnrollStudent = { showEnrollDialog = true },
                onBulkImport = {
                    onResetBulkImport()
                    showBulkImportDialog = true
                },
                onShowAllTerms = onShowAllTerms,
                onOpenRecord = onOpenRecord,
                onOpenStudentHistory = onOpenStudentHistory,
                onReassignGuide = { reassignGuideFor = it },
                onDeleteRecord = onDeleteRecord
            )
            1 -> AdminWeeklyPrintTab(
                termRecords = termRecords,
                consolidatedReports = consolidatedReports,
                isLoading = isLoadingConsolidated,
                selectedYear = selectedYear,
                selectedSemester = selectedSemester,
                selectedWeekFilter = selectedWeekFilter,
                onSelectWeekFilter = { selectedWeekFilter = it },
                onLoadConsolidated = onLoadConsolidatedReports,
                onPrint = { onPrintConsolidated(selectedWeekFilter, termRecords) },
                onSharePdf = { onShareConsolidatedPdf(selectedWeekFilter, termRecords) },
                onOpenRecord = onOpenRecord
            )
            else -> AdminUsersTab(
                accountsState = accountsState,
                accounts = accounts,
                searchQuery = userSearch,
                roleFilter = roleFilter,
                onSearchChange = { userSearch = it },
                onRoleFilterChange = { roleFilter = it },
                onCreateUser = { defaultRole ->
                    isCreatingNew = true
                    showCreateEditDialog = CollegeAccount(
                        role = defaultRole.name,
                        guideType = if (defaultRole == UserRole.GUIDE) GuideType.COLLEGE_FACULTY.name else ""
                    )
                },
                onEditUser = { acc ->
                    isCreatingNew = false
                    showCreateEditDialog = acc
                },
                onResetPassword = { resetPasswordFor = it },
                onToggleStatus = onToggleAccountStatus,
                onDeleteUser = onDeleteAccount,
                onSeedSamples = onSeedSampleAccounts,
                onBulkImport = {
                    onResetBulkImport()
                    showBulkImportDialog = true
                }
            )
        }
    }

    if (showEnrollDialog) {
        AdminEnrollStudentDialog(
            selectedYear = selectedYear,
            selectedSemester = selectedSemester,
            existingStudents = activeStudents,
            availableGuides = activeGuides,
            onDismiss = { showEnrollDialog = false },
            onConfirm = { payload ->
                onEnrollStudentOffCourse(payload)
                showEnrollDialog = false
            }
        )
    }

    if (showCreateEditDialog != null) {
        CreateEditAccountDialog(
            initial = showCreateEditDialog!!,
            isNew = isCreatingNew,
            availableGuides = activeGuides,
            onDismiss = { showCreateEditDialog = null },
            onConfirm = { payload ->
                onSaveAccount(payload)
                showCreateEditDialog = null
            }
        )
    }

    if (resetPasswordFor != null) {
        val target = resetPasswordFor!!
        var newPwd by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { resetPasswordFor = null },
            title = { Text("Reset Password for @${target.username}") },
            text = {
                OutlinedTextField(
                    value = newPwd,
                    onValueChange = { newPwd = it },
                    label = { Text("New Password (min 4 chars)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("admin_reset_pwd_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onResetPassword(target.username, newPwd)
                        resetPasswordFor = null
                    },
                    enabled = newPwd.length >= 4,
                    modifier = Modifier.testTag("admin_confirm_reset_pwd")
                ) { Text("Update Password") }
            },
            dismissButton = {
                TextButton(onClick = { resetPasswordFor = null }) { Text("Cancel") }
            }
        )
    }

    if (reassignGuideFor != null) {
        ReassignGuideDialog(
            record = reassignGuideFor!!,
            guides = activeGuides,
            onDismiss = { reassignGuideFor = null },
            onSave = { gUser, gName, gEmail ->
                onReassignGuide(reassignGuideFor!!.id, gUser, gName, gEmail)
                reassignGuideFor = null
            }
        )
    }

    if (seededCredentials.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = onDismissSeededCredentials,
            title = { Text("Sample KKHM Accounts Created") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Share these login credentials with test users (passwords are hashed in Firestore):",
                        style = MaterialTheme.typography.bodySmall
                    )
                    seededCredentials.forEach { cred ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("${cred.role.title}: ${cred.displayName}", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    "Username: ${cred.username}   •   Password: ${cred.rawPassword}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = onDismissSeededCredentials) { Text("Done") }
            }
        )
    }

    if (showBulkImportDialog) {
        BulkUserImportDialog(
            existingUsernames = accounts.map { it.username }.toSet(),
            selectedYear = selectedYear,
            selectedSemester = selectedSemester,
            importState = bulkImportState,
            onStartImport = onStartBulkImport,
            onResetImportState = onResetBulkImport,
            onShowMessage = onShowMessage,
            onDismiss = {
                onResetBulkImport()
                showBulkImportDialog = false
            }
        )
    }
}

@Composable
private fun AdminOffCourseTab(
    recordsState: UiState<List<OffCourseRecord>>,
    allRecordsCount: Int,
    termRecords: List<OffCourseRecord>,
    semestersByStudent: Map<String, Int>,
    selectedYear: String,
    selectedSemester: String,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onEnrollStudent: () -> Unit,
    onBulkImport: () -> Unit,
    onShowAllTerms: () -> Unit,
    onOpenRecord: (String) -> Unit,
    onOpenStudentHistory: (String) -> Unit,
    onReassignGuide: (OffCourseRecord) -> Unit,
    onDeleteRecord: (String) -> Unit
) {
    val filtered = termRecords.filter { rec ->
        searchQuery.isBlank() ||
            rec.studentName.contains(searchQuery, ignoreCase = true) ||
            rec.studentUsername.contains(searchQuery, ignoreCase = true) ||
            rec.cicAdNo.contains(searchQuery, ignoreCase = true) ||
            rec.subjectTitle.contains(searchQuery, ignoreCase = true) ||
            rec.exemptedSubject.contains(searchQuery, ignoreCase = true) ||
            rec.guideName.contains(searchQuery, ignoreCase = true)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Off-Course Enrollments • ${if (selectedYear == "ALL") "All Years" else selectedYear} (${if (selectedSemester == "ALL") "All Sem" else selectedSemester})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Enroll individual students or bulk-upload students with their Off-Course subject, exempted regular subject, timetable & guide.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onEnrollStudent,
                            modifier = Modifier.weight(1f).testTag("admin_enroll_student_button")
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Enroll Student", maxLines = 1)
                        }
                        FilledTonalButton(
                            onClick = onBulkImport,
                            modifier = Modifier.weight(1f).testTag("admin_offcourse_bulk_button")
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Bulk CSV", maxLines = 1)
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text("Search student, CIC No, subject, exempted subject, guide") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("admin_record_search_input")
            )
        }

        if (recordsState is UiState.Loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (filtered.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (allRecordsCount == 0) {
                                "No Off-Course students enrolled yet. Tap 'Enroll Student' or 'Bulk CSV' above."
                            } else {
                                "No Off-Course students in $selectedYear ($selectedSemester). ($allRecordsCount in other terms)"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (allRecordsCount > 0) {
                            OutlinedButton(
                                onClick = {
                                    onSearchChange("")
                                    onShowAllTerms()
                                },
                                modifier = Modifier.testTag("admin_show_all_terms_button")
                            ) { Text("Show All Years & Semesters") }
                        }
                    }
                }
            }
        } else {
            items(filtered, key = { it.id }) { rec ->
                val studentKey = resolveStudentHistoryKey(rec)
                val semesterCount = semestersByStudent[studentKey] ?: 1
                val (gBg, gFg) = signatureStatusColors(rec.guideSignatureStatus)
                val (pBg, pFg) = signatureStatusColors(rec.principalSignatureStatus)
                val guideTypeShort = if (rec.guideType == GuideType.EXTERNAL.name) "External Guide" else "College Faculty"

                ElevatedCard(
                    onClick = { onOpenRecord(rec.id) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("admin_record_card_${rec.id}")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rec.studentName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "@${rec.studentUsername.ifBlank { "portal" }} • ${rec.cicAdNo} • ${rec.classDepartment}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Off-Course: ${rec.subjectTitle}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${rec.utilizationPercent}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${rec.verifiedWeeksCount}/${rec.completedWeeksCount} Wks OK",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VerifiedGreen
                                )
                            }
                        }

                        // Highlighted Exempted / Dropped Regular Subject box
                        Surface(
                            color = PendingAmber.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.AutoStories,
                                    contentDescription = null,
                                    tint = PendingAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Exempted Subject: ${rec.exemptedSubject.ifBlank { "Not specified" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Text(
                            text = "Guide: ${rec.guideName} ($guideTypeShort) • ${rec.periodsPerWeek} Per/wk • ${rec.academicYear} (${rec.semester})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            StatusBadge(
                                text = if (semesterCount > 1) "Past Semesters: $semesterCount Terms" else "1st Term",
                                containerColor = if (semesterCount > 1) VerifiedGreen.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = if (semesterCount > 1) VerifiedGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            StatusBadge("Guide: ${SignatureStatus.fromRaw(rec.guideSignatureStatus).label}", gBg, gFg)
                            StatusBadge("Prin: ${SignatureStatus.fromRaw(rec.principalSignatureStatus).label}", pBg, pFg)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            FilledTonalButton(
                                onClick = { onOpenStudentHistory(studentKey) },
                                modifier = Modifier.weight(1f).testTag("admin_history_btn_${rec.id}")
                            ) {
                                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("History ($semesterCount)", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { onOpenRecord(rec.id) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Record Book", maxLines = 1)
                            }
                            IconButton(onClick = { onReassignGuide(rec) }) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = "Reassign Guide")
                            }
                            IconButton(onClick = { onDeleteRecord(rec.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete record", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun AdminWeeklyPrintTab(
    termRecords: List<OffCourseRecord>,
    consolidatedReports: Map<String, List<WeeklyStudyReport>>,
    isLoading: Boolean,
    selectedYear: String,
    selectedSemester: String,
    selectedWeekFilter: Int?,
    onSelectWeekFilter: (Int?) -> Unit,
    onLoadConsolidated: (List<OffCourseRecord>) -> Unit,
    onPrint: () -> Unit,
    onSharePdf: () -> Unit,
    onOpenRecord: (String) -> Unit
) {
    LaunchedEffect(termRecords.map { it.id }) {
        onLoadConsolidated(termRecords)
    }

    val allMatchingReports = remember(termRecords, consolidatedReports, selectedWeekFilter) {
        termRecords.flatMap { rec ->
            consolidatedReports[rec.id].orEmpty()
                .filter { selectedWeekFilter == null || it.weekNumber.toInt() == selectedWeekFilter }
        }
    }
    val verifiedCount = allMatchingReports.count { it.verificationStatus == WeekVerificationStatus.VERIFIED.name }
    val pendingCount = allMatchingReports.count { it.verificationStatus != WeekVerificationStatus.VERIFIED.name }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Consolidated Weekly Activity Report",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${if (selectedYear == "ALL") "All Years" else selectedYear} • ${if (selectedSemester == "ALL") "All Semesters" else selectedSemester} • ${selectedWeekFilter?.let { "Week $it" } ?: "All Weeks"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { onLoadConsolidated(termRecords) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh weekly reports")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        SummaryMiniTile("Students", "${termRecords.size}", Modifier.weight(1f))
                        SummaryMiniTile("Approved Wks", "$verifiedCount", Modifier.weight(1f))
                        SummaryMiniTile("Pending Wks", "$pendingCount", Modifier.weight(1f))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = onPrint,
                            enabled = termRecords.isNotEmpty() && !isLoading,
                            modifier = Modifier.weight(1f).testTag("admin_print_weekly_button")
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Print Report")
                        }
                        FilledTonalButton(
                            onClick = onSharePdf,
                            enabled = termRecords.isNotEmpty() && !isLoading,
                            modifier = Modifier.weight(1f).testTag("admin_share_weekly_pdf_button")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share PDF")
                        }
                    }
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    FilterChip(
                        selected = selectedWeekFilter == null,
                        onClick = { onSelectWeekFilter(null) },
                        label = { Text("All Weeks") }
                    )
                }
                items((1..18).toList()) { wk ->
                    FilterChip(
                        selected = selectedWeekFilter == wk,
                        onClick = { onSelectWeekFilter(wk) },
                        label = { Text("W$wk") }
                    )
                }
            }
        }

        if (isLoading) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Loading students' weekly activity logs…", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else if (termRecords.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No Off-Course records found for the selected Academic Year & Semester.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            items(termRecords, key = { "print_${it.id}" }) { rec ->
                val reports = consolidatedReports[rec.id].orEmpty()
                    .filter { selectedWeekFilter == null || it.weekNumber.toInt() == selectedWeekFilter }
                    .sortedBy { it.weekNumber }

                ElevatedCard(
                    onClick = { onOpenRecord(rec.id) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rec.studentName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "${rec.subjectTitle} • Exempted: ${rec.exemptedSubject.ifBlank { "-" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Guide: ${rec.guideName} • ${rec.cicAdNo}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusBadge(
                                text = "${reports.size} wk(s)",
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        if (reports.isEmpty()) {
                            Text(
                                text = selectedWeekFilter?.let { "Week $it not logged yet." } ?: "No weekly reports submitted yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            reports.forEach { rep ->
                                val (wBg, wFg) = weekStatusColors(rep.verificationStatus)
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "Week ${rep.weekNumber} (${rep.dateFrom} → ${rep.dateTo}) • ${rep.utilizedPeriods}/${rep.allocatedPeriods} Per",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            StatusBadge(WeekVerificationStatus.fromRaw(rep.verificationStatus).label, wBg, wFg)
                                        }
                                        if (rep.description.isNotBlank()) {
                                            Text(rep.description, style = MaterialTheme.typography.bodySmall)
                                        }
                                        PeriodLogsBreakdown(periodLogs = rep.periodLogs)
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

@Composable
private fun SummaryMiniTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminUsersTab(
    accountsState: UiState<List<CollegeAccount>>,
    accounts: List<CollegeAccount>,
    searchQuery: String,
    roleFilter: UserRole?,
    onSearchChange: (String) -> Unit,
    onRoleFilterChange: (UserRole?) -> Unit,
    onCreateUser: (UserRole) -> Unit,
    onEditUser: (CollegeAccount) -> Unit,
    onResetPassword: (CollegeAccount) -> Unit,
    onToggleStatus: (String, AccountStatus) -> Unit,
    onDeleteUser: (String) -> Unit,
    onSeedSamples: () -> Unit,
    onBulkImport: () -> Unit
) {
    val filtered = accounts.filter { acc ->
        (roleFilter == null || acc.role == roleFilter.name) &&
            (searchQuery.isBlank() ||
                acc.username.contains(searchQuery, ignoreCase = true) ||
                acc.displayName.contains(searchQuery, ignoreCase = true) ||
                acc.role.contains(searchQuery, ignoreCase = true) ||
                acc.institution.contains(searchQuery, ignoreCase = true) ||
                acc.cicAdNo.contains(searchQuery, ignoreCase = true))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Users & Guides Management", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Appoint college teachers & external mentors as Guides, add students/principals, and edit all account details anytime.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onCreateUser(UserRole.GUIDE) },
                            modifier = Modifier.weight(1f).testTag("admin_appoint_guide_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Appoint Guide", maxLines = 1)
                        }
                        FilledTonalButton(
                            onClick = { onCreateUser(UserRole.STUDENT) },
                            modifier = Modifier.weight(1f).testTag("admin_create_user_button")
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add User", maxLines = 1)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onBulkImport,
                            modifier = Modifier.weight(1f).testTag("admin_bulk_import_button")
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Bulk CSV", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = onSeedSamples,
                            modifier = Modifier.weight(1f).testTag("admin_seed_users_button")
                        ) {
                            Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Sample Users", maxLines = 1)
                        }
                    }
                }
            }
        }

        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = roleFilter == null,
                    onClick = { onRoleFilterChange(null) },
                    label = { Text("All (${accounts.size})") }
                )
                UserRole.entries.forEach { role ->
                    val count = accounts.count { it.role == role.name }
                    FilterChip(
                        selected = roleFilter == role,
                        onClick = { onRoleFilterChange(role) },
                        label = { Text("${role.title} ($count)") }
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text("Search username, name, institution, or CIC No") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("admin_user_search_input")
            )
        }

        if (accountsState is UiState.Loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else {
            items(filtered, key = { it.username }) { acc ->
                val isActive = acc.accountStatus == AccountStatus.ACTIVE.name
                val role = UserRole.fromRaw(acc.role)
                ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(acc.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "@${acc.username} • ${role.title}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (role == UserRole.GUIDE) {
                                    val gt = GuideType.fromRaw(acc.guideType)
                                    Spacer(Modifier.height(4.dp))
                                    StatusBadge(
                                        text = gt.label,
                                        containerColor = if (gt == GuideType.EXTERNAL) PendingAmber.copy(alpha = 0.16f) else MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = if (gt == GuideType.EXTERNAL) PendingAmber else MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                val meta = listOfNotNull(
                                    acc.institution.takeIf { it.isNotBlank() },
                                    acc.cicAdNo.takeIf { it.isNotBlank() },
                                    acc.classDepartment.takeIf { it.isNotBlank() },
                                    acc.stream.takeIf { it.isNotBlank() },
                                    acc.assignedGuideUsername.takeIf { it.isNotBlank() }?.let { "Guide: @$it" },
                                    acc.phone.takeIf { it.isNotBlank() },
                                    acc.email.takeIf { it.isNotBlank() }
                                ).joinToString(" • ")
                                if (meta.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            StatusBadge(
                                text = AccountStatus.fromRaw(acc.accountStatus).label,
                                containerColor = if (isActive) VerifiedGreen.copy(alpha = 0.16f) else PendingAmber.copy(alpha = 0.16f),
                                contentColor = if (isActive) VerifiedGreen else PendingAmber
                            )
                        }
                        HorizontalDivider()
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { onEditUser(acc) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Edit", maxLines = 1)
                            }
                            OutlinedButton(onClick = { onResetPassword(acc) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Pwd", maxLines = 1)
                            }
                            if (acc.username != "admin") {
                                TextButton(
                                    onClick = {
                                        onToggleStatus(
                                            acc.username,
                                            if (isActive) AccountStatus.SUSPENDED else AccountStatus.ACTIVE
                                        )
                                    }
                                ) { Text(if (isActive) "Suspend" else "Activate") }
                                TextButton(onClick = { onDeleteUser(acc.username) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete @${acc.username}", tint = MaterialTheme.colorScheme.error)
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
