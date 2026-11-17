package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.NotificationPreferences
import com.example.data.model.AcademicTerm
import com.example.data.model.CollegeAccount
import com.example.data.model.DarkModePreference
import com.example.data.model.TermStatus
import com.example.data.model.UiState
import com.example.data.model.UserRole
import com.example.ui.components.StatusBadge
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.VerifiedGreen

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AcademicSettingsScreen(
    activeAccount: CollegeAccount?,
    userEmail: String,
    activeRole: UserRole,
    darkModePreference: DarkModePreference,
    notificationPrefs: NotificationPreferences,
    termsState: UiState<List<AcademicTerm>>,
    onSelectDarkMode: (DarkModePreference) -> Unit,
    onLogoutPortal: () -> Unit,
    onUpdateNotifications: (Boolean, Int, Int) -> Unit,
    onSendTestNotification: () -> Unit,
    onCreateTerm: (String, String, TermStatus, Long, Long, String) -> Unit,
    onUpdateTermStatus: (String, TermStatus) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddTermDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onSendTestNotification()
    }

    val terms = (termsState as? UiState.Success)?.data.orEmpty()
    val canManageTerms = activeRole == UserRole.GUIDE || activeRole == UserRole.PRINCIPAL || activeRole == UserRole.ADMIN

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Account & Isolated Portal Session Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = activeAccount?.displayName?.ifBlank { "KKHM User" } ?: "KKHM User",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "@${activeAccount?.username.orEmpty()} • Role: ${activeRole.title}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "KKHM Islamic & Arts College • Cloud Device: $userEmail",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onLogoutPortal,
                            modifier = Modifier.weight(1f).testTag("settings_switch_user_button")
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Switch User")
                        }
                        OutlinedButton(
                            onClick = onSignOut,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Google Out")
                        }
                    }
                }
            }
        }

        // Dark Mode Option
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Appearance & Dark Mode", style = MaterialTheme.typography.titleMedium)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        DarkModePreference.entries.forEachIndexed { idx, pref ->
                            SegmentedButton(
                                selected = darkModePreference == pref,
                                onClick = { onSelectDarkMode(pref) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = idx,
                                    count = DarkModePreference.entries.size
                                ),
                                modifier = Modifier.testTag("dark_mode_${pref.name.lowercase()}")
                            ) {
                                Text(pref.label)
                            }
                        }
                    }
                }
            }
        }

        // Day-of-Period Notification Alerts
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Day-of-Period Notifications", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Alerts students automatically on scheduled timetable days.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = notificationPrefs.enabled,
                            onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                onUpdateNotifications(enabled, notificationPrefs.alertHour, notificationPrefs.alertMinute)
                            }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(7, 8, 9, 18).forEach { hr ->
                            FilterChip(
                                selected = notificationPrefs.alertHour == hr,
                                onClick = { onUpdateNotifications(notificationPrefs.enabled, hr, 0) },
                                label = { Text("%02d:00".format(hr)) }
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onSendTestNotification()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Send Test Period Alert Now")
                    }
                }
            }
        }

        // Academic Years & Semesters Database Configuration
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Academic Years & Semesters", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Add upcoming years/semesters or archive past terms.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canManageTerms) {
                    Button(
                        onClick = { showAddTermDialog = true },
                        modifier = Modifier.testTag("add_academic_term_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Term")
                    }
                }
            }
        }

        if (terms.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (!canManageTerms)
                            "Default terms will appear once configured by KKHM Admin, Principal, or Guide."
                        else "No academic terms added yet. Tap 'Add Term' to configure past, current, or upcoming years & semesters.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(terms, key = { it.id }) { term ->
                val status = TermStatus.fromRaw(term.status)
                val (bg, fg) = when (status) {
                    TermStatus.ACTIVE -> VerifiedGreen.copy(alpha = 0.16f) to VerifiedGreen
                    TermStatus.UPCOMING -> PendingAmber.copy(alpha = 0.16f) to PendingAmber
                    TermStatus.ARCHIVED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "${term.academicYear} • ${term.semester}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${term.totalWeeks} Weeks • Default ${term.defaultPeriodsPerWeek} Periods/wk",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusBadge(status.label, bg, fg)
                        }
                        if (canManageTerms) {
                            HorizontalDivider()
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TermStatus.entries.forEach { target ->
                                    FilterChip(
                                        selected = status == target,
                                        onClick = { onUpdateTermStatus(term.id, target) },
                                        label = { Text(target.label) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showAddTermDialog) {
        AddAcademicTermDialog(
            onDismiss = { showAddTermDialog = false },
            onSave = { year, sem, status, weeks, periods, notes ->
                onCreateTerm(year, sem, status, weeks, periods, notes)
                showAddTermDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddAcademicTermDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, TermStatus, Long, Long, String) -> Unit
) {
    var year by remember { mutableStateOf("2027 - 2028") }
    var semester by remember { mutableStateOf("Nov") }
    var status by remember { mutableStateOf(TermStatus.UPCOMING) }
    var weeksText by remember { mutableStateOf("18") }
    var periodsText by remember { mutableStateOf("4") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Academic Year & Semester") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it },
                    label = { Text("Academic Year *") },
                    placeholder = { Text("2027 - 2028") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("term_year_input")
                )
                OutlinedTextField(
                    value = semester,
                    onValueChange = { semester = it },
                    label = { Text("Semester * (Nov / May / Custom)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("term_semester_input")
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TermStatus.entries.forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = { Text(st.label) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = weeksText,
                        onValueChange = { weeksText = it.filter(Char::isDigit).take(2) },
                        label = { Text("Weeks") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = periodsText,
                        onValueChange = { periodsText = it.filter(Char::isDigit).take(2) },
                        label = { Text("Periods/wk") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (year.isNotBlank() && semester.isNotBlank()) {
                        onSave(
                            year.trim(),
                            semester.trim(),
                            status,
                            (weeksText.toLongOrNull() ?: 18L).coerceIn(1L, 24L),
                            (periodsText.toLongOrNull() ?: 4L).coerceIn(1L, 35L),
                            notes.trim()
                        )
                    }
                },
                modifier = Modifier.testTag("save_academic_term_button")
            ) {
                Text("Save Term")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
