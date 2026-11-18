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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Key
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
import androidx.compose.ui.unit.dp
import com.example.data.model.AcademicTerm
import com.example.data.model.AccountStatus
import com.example.data.model.CicStream
import com.example.data.model.CollegeAccount
import com.example.data.model.OffCourseRecord
import com.example.data.model.SignatureStatus
import com.example.data.model.UiState
import com.example.data.model.UserRole
import com.example.data.remote.BulkImportRow
import com.example.data.remote.BulkImportUiState
import com.example.data.remote.SeededCredential
import com.example.ui.components.AcademicTermFilterBar
import com.example.ui.components.StatusBadge
import com.example.ui.components.signatureStatusColors
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.VerifiedGreen

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdminDashboardScreen(
    accountsState: UiState<List<CollegeAccount>>,
    recordsState: UiState<List<OffCourseRecord>>,
    terms: List<AcademicTerm>,
    selectedYear: String,
    selectedSemester: String,
    seededCredentials: List<SeededCredential>,
    onDismissSeededCredentials: () -> Unit,
    onSelectYear: (String) -> Unit,
    onSelectSemester: (String) -> Unit,
    onShowAllTerms: () -> Unit = {},
    onSaveAccount: (String, String, UserRole, String?, AccountStatus, String, String, String, String, String) -> Unit,
    onResetPassword: (String, String) -> Unit,
    onToggleAccountStatus: (String, AccountStatus) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onSeedSampleAccounts: () -> Unit,
    onOpenRecord: (String) -> Unit,
    onReassignGuide: (String, String, String, String) -> Unit,
    onDeleteRecord: (String) -> Unit,
    bulkImportState: BulkImportUiState = BulkImportUiState(),
    onStartBulkImport: (List<BulkImportRow>) -> Unit = {},
    onResetBulkImport: () -> Unit = {},
    onShowMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableIntStateOf(0) }
    var userSearch by remember { mutableStateOf("") }
    var recordSearch by remember { mutableStateOf("") }
    var showCreateEditDialog by remember { mutableStateOf<CollegeAccount?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var resetPasswordFor by remember { mutableStateOf<CollegeAccount?>(null) }
    var reassignGuideFor by remember { mutableStateOf<OffCourseRecord?>(null) }
    var showBulkImportDialog by remember { mutableStateOf(false) }

    val accounts = (accountsState as? UiState.Success)?.data.orEmpty()
    val records = (recordsState as? UiState.Success)?.data.orEmpty()
    val guides = accounts.filter { it.role == UserRole.GUIDE.name && it.accountStatus == AccountStatus.ACTIVE.name }

    Column(modifier = modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            listOf("Users & Passwords (${accounts.size})", "All Records (${records.size})").forEachIndexed { idx, label ->
                SegmentedButton(
                    selected = selectedSection == idx,
                    onClick = { selectedSection = idx },
                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = 2),
                    modifier = Modifier.testTag(if (idx == 0) "admin_tab_users" else "admin_tab_records")
                ) {
                    Text(label, maxLines = 1)
                }
            }
        }

        if (selectedSection == 0) {
            AdminUsersTab(
                accountsState = accountsState,
                accounts = accounts,
                searchQuery = userSearch,
                onSearchChange = { userSearch = it },
                onCreateUser = {
                    isCreatingNew = true
                    showCreateEditDialog = CollegeAccount()
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
        } else {
            AdminRecordsTab(
                recordsState = recordsState,
                records = records,
                terms = terms,
                selectedYear = selectedYear,
                selectedSemester = selectedSemester,
                searchQuery = recordSearch,
                onSearchChange = { recordSearch = it },
                onSelectYear = onSelectYear,
                onSelectSemester = onSelectSemester,
                onShowAllTerms = onShowAllTerms,
                onOpenRecord = onOpenRecord,
                onReassignGuide = { reassignGuideFor = it },
                onDeleteRecord = onDeleteRecord
            )
        }
    }

    if (showCreateEditDialog != null) {
        CreateEditAccountDialog(
            initial = showCreateEditDialog!!,
            isNew = isCreatingNew,
            availableGuides = guides,
            onDismiss = { showCreateEditDialog = null },
            onConfirm = { u, name, role, pwd, status, cicNo, dept, stream, guideUser, phone ->
                onSaveAccount(u, name, role, pwd, status, cicNo, dept, stream, guideUser, phone)
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
            guides = guides,
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
private fun AdminUsersTab(
    accountsState: UiState<List<CollegeAccount>>,
    accounts: List<CollegeAccount>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onCreateUser: () -> Unit,
    onEditUser: (CollegeAccount) -> Unit,
    onResetPassword: (CollegeAccount) -> Unit,
    onToggleStatus: (String, AccountStatus) -> Unit,
    onDeleteUser: (String) -> Unit,
    onSeedSamples: () -> Unit,
    onBulkImport: () -> Unit
) {
    val filtered = accounts.filter { acc ->
        searchQuery.isBlank() ||
            acc.username.contains(searchQuery, ignoreCase = true) ||
            acc.displayName.contains(searchQuery, ignoreCase = true) ||
            acc.role.contains(searchQuery, ignoreCase = true) ||
            acc.cicAdNo.contains(searchQuery, ignoreCase = true)
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
                        Text("KKHM Admin Control Center", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "Create & manage student, guide, principal & admin credentials. Each user sees strictly their own isolated workspace.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onCreateUser,
                            modifier = Modifier.weight(1f).testTag("admin_create_user_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Create User")
                        }
                        FilledTonalButton(
                            onClick = onSeedSamples,
                            modifier = Modifier.weight(1f).testTag("admin_seed_users_button")
                        ) {
                            Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Sample Users")
                        }
                    }
                    OutlinedButton(
                        onClick = onBulkImport,
                        modifier = Modifier.fillMaxWidth().testTag("admin_bulk_import_button")
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Bulk Import Users (Download / Upload CSV Template)")
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text("Search username, name, role, or CIC No") },
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
                                Text(acc.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "@${acc.username} • ${role.title}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val meta = listOfNotNull(
                                    acc.cicAdNo.takeIf { it.isNotBlank() },
                                    acc.classDepartment.takeIf { it.isNotBlank() },
                                    acc.stream.takeIf { it.isNotBlank() },
                                    acc.assignedGuideUsername.takeIf { it.isNotBlank() }?.let { "Guide: @$it" }
                                ).joinToString(" • ")
                                if (meta.isNotBlank()) {
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

@Composable
private fun AdminRecordsTab(
    recordsState: UiState<List<OffCourseRecord>>,
    records: List<OffCourseRecord>,
    terms: List<AcademicTerm>,
    selectedYear: String,
    selectedSemester: String,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSelectYear: (String) -> Unit,
    onSelectSemester: (String) -> Unit,
    onShowAllTerms: () -> Unit,
    onOpenRecord: (String) -> Unit,
    onReassignGuide: (OffCourseRecord) -> Unit,
    onDeleteRecord: (String) -> Unit
) {
    val filtered = records.filter { rec ->
        (selectedYear == "ALL" || rec.academicYear == selectedYear) &&
            (selectedSemester == "ALL" || rec.semester == selectedSemester) &&
            (searchQuery.isBlank() ||
                rec.studentName.contains(searchQuery, ignoreCase = true) ||
                rec.studentUsername.contains(searchQuery, ignoreCase = true) ||
                rec.subjectTitle.contains(searchQuery, ignoreCase = true) ||
                rec.guideName.contains(searchQuery, ignoreCase = true))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text("Search student, username, guide, or subject") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            AcademicTermFilterBar(
                terms = terms,
                records = records,
                selectedYear = selectedYear,
                selectedSemester = selectedSemester,
                onSelectYear = onSelectYear,
                onSelectSemester = onSelectSemester
            )
        }
        if (recordsState is UiState.Loading) {
            item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else if (filtered.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (records.isEmpty()) "No Off-Course records created yet."
                            else "No records in $selectedYear ($selectedSemester) (${records.size} in other terms).",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (records.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    onSearchChange("")
                                    onShowAllTerms()
                                },
                                modifier = Modifier.testTag("admin_show_all_terms_button")
                            ) {
                                Text("Show All Years & Semesters")
                            }
                        }
                    }
                }
            }
        } else {
            items(filtered, key = { it.id }) { rec ->
                val (gBg, gFg) = signatureStatusColors(rec.guideSignatureStatus)
                val (pBg, pFg) = signatureStatusColors(rec.principalSignatureStatus)
                ElevatedCard(onClick = { onOpenRecord(rec.id) }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rec.subjectTitle, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${rec.studentName} (@${rec.studentUsername.ifBlank { "device" }}) • ${rec.cicAdNo}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Guide: ${rec.guideName} (@${rec.guideUsername.ifBlank { "-" }}) • ${rec.academicYear} (${rec.semester})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text("${rec.utilizationPercent}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusBadge("Guide: ${SignatureStatus.fromRaw(rec.guideSignatureStatus).label}", gBg, gFg)
                            StatusBadge("Principal: ${SignatureStatus.fromRaw(rec.principalSignatureStatus).label}", pBg, pFg)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            FilledTonalButton(onClick = { onOpenRecord(rec.id) }, modifier = Modifier.weight(1f)) {
                                Text("Open & Verify")
                            }
                            OutlinedButton(onClick = { onReassignGuide(rec) }) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Guide")
                            }
                            TextButton(onClick = { onDeleteRecord(rec.id) }) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateEditAccountDialog(
    initial: CollegeAccount,
    isNew: Boolean,
    availableGuides: List<CollegeAccount>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, UserRole, String?, AccountStatus, String, String, String, String, String) -> Unit
) {
    var username by remember { mutableStateOf(initial.username) }
    var displayName by remember { mutableStateOf(initial.displayName) }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(UserRole.fromRaw(initial.role)) }
    var status by remember { mutableStateOf(AccountStatus.fromRaw(initial.accountStatus)) }
    var cicAdNo by remember { mutableStateOf(initial.cicAdNo) }
    var classDept by remember { mutableStateOf(initial.classDepartment) }
    var stream by remember { mutableStateOf(initial.stream.ifBlank { CicStream.WAFY.label }) }
    var assignedGuide by remember { mutableStateOf(initial.assignedGuideUsername) }
    var phone by remember { mutableStateOf(initial.phone) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Create KKHM User Account" else "Edit @${initial.username}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '-' } },
                    enabled = isNew,
                    label = { Text("Username * (a-z, 0-9, _)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("dialog_account_username")
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Full Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("dialog_account_name")
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (isNew) "Password * (min 4 chars)" else "New Password (leave blank to keep)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("dialog_account_password")
                )
                Text("Role", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    UserRole.entries.forEach { r ->
                        FilterChip(
                            selected = role == r,
                            onClick = { role = r },
                            label = { Text(r.title) },
                            modifier = Modifier.testTag("dialog_role_${r.name.lowercase()}")
                        )
                    }
                }
                if (role == UserRole.STUDENT) {
                    OutlinedTextField(
                        value = cicAdNo,
                        onValueChange = { cicAdNo = it },
                        label = { Text("CIC Ad No") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = classDept,
                        onValueChange = { classDept = it },
                        label = { Text("Class / Department") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CicStream.entries.forEach { s ->
                            FilterChip(
                                selected = stream == s.label,
                                onClick = { stream = s.label },
                                label = { Text(s.label) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = assignedGuide,
                        onValueChange = { assignedGuide = it },
                        label = { Text("Assigned Guide Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (availableGuides.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            availableGuides.forEach { g ->
                                FilterChip(
                                    selected = assignedGuide == g.username,
                                    onClick = { assignedGuide = g.username },
                                    label = { Text("@${g.username}") }
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone / WhatsApp") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        username, displayName, role, password.ifBlank { null }, status,
                        cicAdNo, classDept, if (role == UserRole.STUDENT) stream else "",
                        assignedGuide, phone
                    )
                },
                enabled = username.length >= 3 && displayName.isNotBlank() && (!isNew || password.length >= 4),
                modifier = Modifier.testTag("dialog_save_account_button")
            ) { Text("Save User") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReassignGuideDialog(
    record: OffCourseRecord,
    guides: List<CollegeAccount>,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var guideUsername by remember { mutableStateOf(record.guideUsername) }
    var guideName by remember { mutableStateOf(record.guideName) }
    var guideEmail by remember { mutableStateOf(record.guideEmail) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign Guide for ${record.studentName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (guides.isNotEmpty()) {
                    Text("Quick Pick Active Guide:", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        guides.forEach { g ->
                            FilterChip(
                                selected = guideUsername == g.username,
                                onClick = {
                                    guideUsername = g.username
                                    guideName = g.displayName
                                },
                                label = { Text("${g.displayName} (@${g.username})") }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = guideUsername,
                    onValueChange = { guideUsername = it },
                    label = { Text("Guide Username *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = guideName,
                    onValueChange = { guideName = it },
                    label = { Text("Guide Full Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = guideEmail,
                    onValueChange = { guideEmail = it },
                    label = { Text("Guide Email (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(guideUsername, guideName, guideEmail) },
                enabled = guideName.isNotBlank()
            ) { Text("Assign Guide") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
