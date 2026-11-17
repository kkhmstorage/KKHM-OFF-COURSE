package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.NotificationPreferences
import com.example.data.model.OffCourseRecord
import com.example.data.model.RecordStatus
import com.example.data.model.SignatureStatus
import com.example.data.model.UiState
import com.example.data.model.UserRole
import com.example.data.model.WeekVerificationStatus
import com.example.data.model.WeeklyStudyReport
import com.example.ui.components.StatusBadge
import com.example.ui.components.TimetableMatrixGrid
import com.example.ui.components.UtilizationGaugeRing
import com.example.ui.components.WeeklyUtilizationBarChart
import com.example.ui.components.signatureStatusColors
import com.example.ui.components.weekStatusColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordBookDetailScreen(
    recordState: UiState<OffCourseRecord?>,
    reportsState: UiState<List<WeeklyStudyReport>>,
    activeRole: UserRole,
    currentUserId: String,
    currentUserName: String,
    notificationPrefs: NotificationPreferences,
    onBack: () -> Unit,
    onEditRecord: (String) -> Unit,
    onSubmitForReview: (String) -> Unit,
    onExportPdf: (OffCourseRecord, List<WeeklyStudyReport>) -> Unit,
    onSaveWeeklyReport: (OffCourseRecord, WeeklyStudyReport) -> Unit,
    onVerifyWeeklyReport: (OffCourseRecord, String, WeekVerificationStatus, String, String) -> Unit,
    onSubmitGuideReview: (String, SignatureStatus, String, String) -> Unit,
    onSubmitPrincipalApproval: (String, SignatureStatus, String, String, RecordStatus) -> Unit,
    onUpdateNotifications: (Boolean, Int, Int) -> Unit,
    onSendTestNotification: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var editingWeekNumber by remember { mutableStateOf<Int?>(null) }
    var verifyingReport by remember { mutableStateOf<WeeklyStudyReport?>(null) }
    var showGuideSignDialog by remember { mutableStateOf(false) }
    var showPrincipalSignDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onSendTestNotification()
        }
    }

    val record = (recordState as? UiState.Success)?.data
    val reports = (reportsState as? UiState.Success)?.data.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(record?.subjectTitle ?: "Off-Course Record") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (record != null) {
                        IconButton(
                            onClick = { onExportPdf(record, reports) },
                            modifier = Modifier.testTag("detail_export_pdf_button")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                        }
                        if ((record.studentUid == currentUserId && record.recordStatus != RecordStatus.ARCHIVED.name) || activeRole == UserRole.ADMIN) {
                            IconButton(
                                onClick = { onEditRecord(record.id) },
                                modifier = Modifier.testTag("detail_edit_record_button")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Record")
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (recordState is UiState.Loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (recordState is UiState.Error || record == null) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text((recordState as? UiState.Error)?.message ?: "Record not found.")
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Overview") },
                    modifier = Modifier.testTag("tab_overview")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Study Report (W1–18)") },
                    modifier = Modifier.testTag("tab_study_report")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Timetable & Alerts") },
                    modifier = Modifier.testTag("tab_timetable_alerts")
                )
            }

            when (selectedTab) {
                0 -> OverviewTab(
                    record = record,
                    reports = reports,
                    activeRole = activeRole,
                    currentUserId = currentUserId,
                    onSubmitForReview = { onSubmitForReview(record.id) },
                    onExportPdf = { onExportPdf(record, reports) },
                    onOpenGuideSign = { showGuideSignDialog = true },
                    onOpenPrincipalSign = { showPrincipalSignDialog = true }
                )
                1 -> StudyReportTab(
                    record = record,
                    reports = reports,
                    activeRole = activeRole,
                    currentUserId = currentUserId,
                    onEditWeek = { week -> editingWeekNumber = week },
                    onVerifyWeek = { report -> verifyingReport = report }
                )
                2 -> TimetableAlertsTab(
                    record = record,
                    notificationPrefs = notificationPrefs,
                    onToggleAlerts = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        onUpdateNotifications(enabled, notificationPrefs.alertHour, notificationPrefs.alertMinute)
                    },
                    onSelectAlertHour = { hour ->
                        onUpdateNotifications(notificationPrefs.enabled, hour, 0)
                    },
                    onSendTestAlert = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onSendTestNotification()
                        }
                    }
                )
            }
        }
    }

    if (record != null && editingWeekNumber != null) {
        val wk = editingWeekNumber!!
        val existing = reports.firstOrNull { it.weekNumber.toInt() == wk }
        WeeklyReportEditDialog(
            record = record,
            weekNumber = wk,
            existingReport = existing,
            onDismiss = { editingWeekNumber = null },
            onSave = { updated ->
                onSaveWeeklyReport(record, updated)
                editingWeekNumber = null
            }
        )
    }

    if (record != null && verifyingReport != null) {
        val target = verifyingReport!!
        WeeklyVerifyDialog(
            report = target,
            defaultSignatureName = currentUserName.ifBlank { record.guideName },
            onDismiss = { verifyingReport = null },
            onVerify = { status, sig, feedback ->
                onVerifyWeeklyReport(record, target.id, status, sig, feedback)
                verifyingReport = null
            }
        )
    }

    if (record != null && showGuideSignDialog) {
        AuthorizationSignDialog(
            title = "Guide (Mentor) Signature & Review",
            initialStatus = SignatureStatus.fromRaw(record.guideSignatureStatus),
            initialName = record.guideSignatureName.ifBlank { currentUserName.ifBlank { record.guideName } },
            initialRemarks = record.guideRemarks,
            showRecordStatus = false,
            initialRecordStatus = RecordStatus.fromRaw(record.recordStatus),
            onDismiss = { showGuideSignDialog = false },
            onConfirm = { sigStatus, sigName, remarks, _ ->
                onSubmitGuideReview(record.id, sigStatus, sigName, remarks)
                showGuideSignDialog = false
            }
        )
    }

    if (record != null && showPrincipalSignDialog) {
        AuthorizationSignDialog(
            title = "Principal / Dean Authorization",
            initialStatus = SignatureStatus.fromRaw(record.principalSignatureStatus),
            initialName = record.principalSignatureName.ifBlank { currentUserName.ifBlank { "Principal / Dean" } },
            initialRemarks = record.principalRemarks,
            showRecordStatus = true,
            initialRecordStatus = RecordStatus.fromRaw(record.recordStatus),
            onDismiss = { showPrincipalSignDialog = false },
            onConfirm = { sigStatus, sigName, remarks, recStatus ->
                onSubmitPrincipalApproval(record.id, sigStatus, sigName, remarks, recStatus)
                showPrincipalSignDialog = false
            }
        )
    }
}

@Composable
private fun OverviewTab(
    record: OffCourseRecord,
    reports: List<WeeklyStudyReport>,
    activeRole: UserRole,
    currentUserId: String,
    onSubmitForReview: () -> Unit,
    onExportPdf: () -> Unit,
    onOpenGuideSign: () -> Unit,
    onOpenPrincipalSign: () -> Unit
) {
    val (gBg, gFg) = signatureStatusColors(record.guideSignatureStatus)
    val (pBg, pFg) = signatureStatusColors(record.principalSignatureStatus)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Period Utilization Summary
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        UtilizationGaugeRing(
                            percent = record.utilizationPercent,
                            utilizedPeriods = record.totalUtilizedPeriods,
                            allocatedPeriods = record.totalAllocatedPeriods
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Subject Utilization", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Completed Weeks: ${record.completedWeeksCount}/18 • Verified: ${record.verifiedWeeksCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Avg Focus: ${if (record.averageFocusRating > 0) String.format("%.1f / 5.0", record.averageFocusRating) else "Not rated"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    WeeklyUtilizationBarChart(reports = reports)
                }
            }
        }

        // Student & Subject Card (PDF Page 1 & 2)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("KKHM / CIC Off-Course Details", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DetailLine("Student", record.studentName)
                    DetailLine("CIC / Clg Ad No", "${record.cicAdNo} / ${record.clgAdNo.ifBlank { "-" }}")
                    DetailLine("Class / Dept", record.classDepartment)
                    DetailLine("Year & Semester", "${record.academicYear} • ${record.semester}")
                    DetailLine("Stream", record.stream)
                    DetailLine("College", record.collegeAfflName)
                    DetailLine("Proposal App No", "${record.proposalAppNo.ifBlank { "-" }} (Credit: ${record.credit})")
                    DetailLine("Guide (Mentor)", "${record.guideName} (${record.guidePhone.ifBlank { "-" }})")
                    if (record.referenceBooks.isNotBlank()) {
                        DetailLine("References", record.referenceBooks)
                    }
                }
            }
        }

        // Signatures & Authorization Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Official Signatures & Status", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Guide & Signature", style = MaterialTheme.typography.labelLarge)
                            Text(
                                record.guideSignatureName.ifBlank { record.guideName },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusBadge(
                            SignatureStatus.fromRaw(record.guideSignatureStatus).label,
                            gBg,
                            gFg
                        )
                    }
                    if (record.guideRemarks.isNotBlank()) {
                        Text("Guide Remarks: ${record.guideRemarks}", style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Principal / Dean & Signature", style = MaterialTheme.typography.labelLarge)
                            Text(
                                record.principalSignatureName.ifBlank { "Awaiting Authorization" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusBadge(
                            SignatureStatus.fromRaw(record.principalSignatureStatus).label,
                            pBg,
                            pFg
                        )
                    }
                    if (record.principalRemarks.isNotBlank()) {
                        Text("Principal Remarks: ${record.principalRemarks}", style = MaterialTheme.typography.bodySmall)
                    }

                    // Role-based actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (activeRole == UserRole.GUIDE || activeRole == UserRole.ADMIN) {
                            Button(
                                onClick = onOpenGuideSign,
                                modifier = Modifier.weight(1f).testTag("guide_sign_record_button")
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Guide Sign Off")
                            }
                        }
                        if (activeRole == UserRole.PRINCIPAL || activeRole == UserRole.ADMIN) {
                            Button(
                                onClick = onOpenPrincipalSign,
                                modifier = Modifier.weight(1f).testTag("principal_sign_record_button")
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Principal Approve")
                            }
                        }
                        if (activeRole == UserRole.STUDENT && record.studentUid == currentUserId && record.recordStatus == RecordStatus.ACTIVE.name) {
                            FilledTonalButton(
                                onClick = onSubmitForReview,
                                modifier = Modifier.weight(1f).testTag("submit_record_review_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Submit Record")
                            }
                        }
                        OutlinedButton(onClick = onExportPdf) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export PDF")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyReportTab(
    record: OffCourseRecord,
    reports: List<WeeklyStudyReport>,
    activeRole: UserRole,
    currentUserId: String,
    onEditWeek: (Int) -> Unit,
    onVerifyWeek: (WeeklyStudyReport) -> Unit
) {
    val byWeek = reports.associateBy { it.weekNumber.toInt() }
    val weeks = (1..18).toList()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(weeks, key = { it }) { weekNum ->
            val rep = byWeek[weekNum]
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (rep != null) MaterialTheme.colorScheme.surfaceContainerLow
                    else MaterialTheme.colorScheme.surfaceContainerLowest
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Week $weekNum", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            if (rep != null && (rep.dateFrom.isNotBlank() || rep.dateTo.isNotBlank())) {
                                Text(
                                    "${rep.dateFrom} → ${rep.dateTo}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (rep != null) {
                            val (bg, fg) = weekStatusColors(rep.verificationStatus)
                            StatusBadge(
                                text = WeekVerificationStatus.fromRaw(rep.verificationStatus).label,
                                containerColor = bg,
                                contentColor = fg
                            )
                        } else {
                            Text(
                                "Not logged yet",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (rep != null) {
                        Text(rep.description, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Periods: ${rep.utilizedPeriods}/${rep.allocatedPeriods} (${rep.utilizationPercent}%) • Focus: ${rep.focusRating}/5",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (rep.authorizedSignature.isNotBlank()) {
                            Text(
                                text = "Authorized by: ${rep.authorizedSignature}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (rep.mentorFeedback.isNotBlank()) {
                            Text(
                                text = "Feedback: ${rep.mentorFeedback}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (record.studentUid == currentUserId && record.recordStatus != RecordStatus.ARCHIVED.name) {
                            FilledTonalButton(
                                onClick = { onEditWeek(weekNum) },
                                modifier = Modifier.testTag("log_week_${weekNum}_button")
                            ) {
                                Text(if (rep == null) "Log Week $weekNum" else "Edit Week $weekNum")
                            }
                        }
                        if (rep != null && (activeRole == UserRole.GUIDE || activeRole == UserRole.PRINCIPAL || activeRole == UserRole.ADMIN)) {
                            Button(
                                onClick = { onVerifyWeek(rep) },
                                modifier = Modifier.testTag("verify_week_${weekNum}_button")
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Verify & Sign")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimetableAlertsTab(
    record: OffCourseRecord,
    notificationPrefs: NotificationPreferences,
    onToggleAlerts: (Boolean) -> Unit,
    onSelectAlertHour: (Int) -> Unit,
    onSendTestAlert: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TimetableMatrixGrid(
                selectedSlots = record.timetableSlots.toSet(),
                editable = false
            )
        }
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Day-of-Period Notifications", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Receive alerts on days when this subject has Off-Course periods.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = notificationPrefs.enabled,
                            onCheckedChange = onToggleAlerts,
                            modifier = Modifier.testTag("detail_alerts_switch")
                        )
                    }
                    Text("Preferred Alert Time", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(7, 8, 9, 18).forEach { hr ->
                            FilterChip(
                                selected = notificationPrefs.alertHour == hr,
                                onClick = { onSelectAlertHour(hr) },
                                label = { Text("%02d:00".format(hr)) }
                            )
                        }
                    }
                    Button(
                        onClick = onSendTestAlert,
                        modifier = Modifier.fillMaxWidth().testTag("send_test_alert_button")
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Send Test Period Alert Now")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.58f)
        )
    }
}

@Composable
private fun AuthorizationSignDialog(
    title: String,
    initialStatus: SignatureStatus,
    initialName: String,
    initialRemarks: String,
    showRecordStatus: Boolean,
    initialRecordStatus: RecordStatus,
    onDismiss: () -> Unit,
    onConfirm: (SignatureStatus, String, String, RecordStatus) -> Unit
) {
    var status by remember { mutableStateOf(SignatureStatus.APPROVED) }
    var name by remember { mutableStateOf(initialName) }
    var remarks by remember { mutableStateOf(initialRemarks) }
    var recStatus by remember { mutableStateOf(initialRecordStatus) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SignatureStatus.entries.forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = { Text(st.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Authorized Name / Stamp *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Remarks") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (showRecordStatus) {
                    Text("Record Lifecycle Status", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(RecordStatus.ACTIVE, RecordStatus.COMPLETED, RecordStatus.ARCHIVED).forEach { rs ->
                            FilterChip(
                                selected = recStatus == rs,
                                onClick = { recStatus = rs },
                                label = { Text(rs.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(status, name.trim().ifBlank { "Authorized" }, remarks.trim(), recStatus)
                },
                modifier = Modifier.testTag("confirm_auth_sign_button")
            ) {
                Text("Save Signature")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
