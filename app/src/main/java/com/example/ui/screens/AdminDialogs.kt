package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AccountStatus
import com.example.data.model.CicStream
import com.example.data.model.CollegeAccount
import com.example.data.model.GuideType
import com.example.data.model.OffCourseRecord
import com.example.data.model.UserRole
import com.example.ui.components.TimetableMatrixGrid

data class AdminEnrollPayload(
    val studentUsername: String,
    val studentName: String,
    val passwordOrNull: String?,
    val cicAdNo: String,
    val classDepartment: String,
    val stream: String,
    val phone: String,
    val academicYear: String,
    val semester: String,
    val subjectTitle: String,
    val exemptedSubject: String,
    val credit: Long,
    val guideAccount: CollegeAccount?,
    val guideUsernameFallback: String,
    val guideNameFallback: String,
    val timetableSlots: List<String>,
    val proposalAppNo: String,
    val referenceBooks: String
)

data class AccountFormPayload(
    val username: String,
    val displayName: String,
    val role: UserRole,
    val passwordOrNull: String?,
    val status: AccountStatus,
    val cicAdNo: String,
    val classDepartment: String,
    val stream: String,
    val assignedGuideUsername: String,
    val phone: String,
    val email: String,
    val guideType: String,
    val institution: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminEnrollStudentDialog(
    selectedYear: String,
    selectedSemester: String,
    existingStudents: List<CollegeAccount>,
    availableGuides: List<CollegeAccount>,
    onDismiss: () -> Unit,
    onConfirm: (AdminEnrollPayload) -> Unit
) {
    val defaultYear = selectedYear.takeIf { it.isNotBlank() && it != "ALL" } ?: "2026 - 2027"
    val defaultSem = selectedSemester.takeIf { it.isNotBlank() && it != "ALL" } ?: "Nov"

    var pickedExistingStudent by remember { mutableStateOf<CollegeAccount?>(null) }
    var studentUsername by remember { mutableStateOf("") }
    var studentName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var cicAdNo by remember { mutableStateOf("") }
    var classDept by remember { mutableStateOf("") }
    var stream by remember { mutableStateOf(CicStream.WAFY.label) }
    var phone by remember { mutableStateOf("") }

    var academicYear by remember { mutableStateOf(defaultYear) }
    var semester by remember { mutableStateOf(defaultSem) }
    var subjectTitle by remember { mutableStateOf("") }
    var exemptedSubject by remember { mutableStateOf("") }
    var creditText by remember { mutableStateOf("4") }
    var selectedGuide by remember { mutableStateOf<CollegeAccount?>(availableGuides.firstOrNull()) }
    var guideUsername by remember { mutableStateOf(availableGuides.firstOrNull()?.username.orEmpty()) }
    var guideName by remember { mutableStateOf(availableGuides.firstOrNull()?.displayName.orEmpty()) }
    var selectedSlots by remember { mutableStateOf(setOf("Monday:2", "Wednesday:5")) }
    var proposalAppNo by remember { mutableStateOf("") }
    var referenceBooks by remember { mutableStateOf("") }

    val canSubmit = studentUsername.length >= 3 &&
        studentName.isNotBlank() &&
        subjectTitle.isNotBlank() &&
        exemptedSubject.isNotBlank() &&
        guideName.isNotBlank() &&
        selectedSlots.isNotEmpty()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enroll Student in Off-Course",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Term: $academicYear • $semester Semester",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close dialog")
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (existingStudents.isNotEmpty()) {
                        Text(
                            text = "Quick Pick Existing Student (or enter new below):",
                            style = MaterialTheme.typography.labelLarge
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            existingStudents.take(12).forEach { stu ->
                                val isPicked = pickedExistingStudent?.username == stu.username
                                FilterChip(
                                    selected = isPicked,
                                    onClick = {
                                        pickedExistingStudent = stu
                                        studentUsername = stu.username
                                        studentName = stu.displayName
                                        cicAdNo = stu.cicAdNo
                                        classDept = stu.classDepartment
                                        if (stu.stream.isNotBlank()) stream = stu.stream
                                        phone = stu.phone
                                        val matchedGuide = availableGuides.firstOrNull {
                                            it.username.equals(stu.assignedGuideUsername, ignoreCase = true)
                                        }
                                        if (matchedGuide != null) {
                                            selectedGuide = matchedGuide
                                            guideUsername = matchedGuide.username
                                            guideName = matchedGuide.displayName
                                        }
                                    },
                                    label = { Text("${stu.displayName} (@${stu.username})") }
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = studentUsername,
                            onValueChange = {
                                pickedExistingStudent = null
                                studentUsername = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '-' }
                            },
                            label = { Text("Student Username *") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("enroll_student_username")
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(if (pickedExistingStudent == null) "Password (default kkhm123)" else "Keep Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = studentName,
                        onValueChange = { studentName = it },
                        label = { Text("Student Full Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("enroll_student_name")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = cicAdNo,
                            onValueChange = { cicAdNo = it },
                            label = { Text("CIC Ad No") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = classDept,
                            onValueChange = { classDept = it },
                            label = { Text("Class / Dept") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CicStream.entries.forEach { s ->
                            FilterChip(
                                selected = stream == s.label,
                                onClick = { stream = s.label },
                                label = { Text(s.label) }
                            )
                        }
                    }

                    HorizontalDivider()
                    Text("Off-Course & Exempted Subject Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = academicYear,
                            onValueChange = { academicYear = it },
                            label = { Text("Academic Year *") },
                            singleLine = true,
                            modifier = Modifier.weight(1.2f)
                        )
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("May", "Nov").forEach { sem ->
                                FilterChip(
                                    selected = semester.equals(sem, ignoreCase = true),
                                    onClick = { semester = sem },
                                    label = { Text(sem) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = subjectTitle,
                        onValueChange = { subjectTitle = it },
                        label = { Text("Off-Course Subject Title *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("enroll_subject_title")
                    )

                    OutlinedTextField(
                        value = exemptedSubject,
                        onValueChange = { exemptedSubject = it },
                        label = { Text("Exempted / Dropped Regular Subject *") },
                        placeholder = { Text("Which regular subject was dropped for Off-Course?") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("enroll_exempted_subject")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = creditText,
                            onValueChange = { creditText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Credit") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.6f)
                        )
                        OutlinedTextField(
                            value = proposalAppNo,
                            onValueChange = { proposalAppNo = it },
                            label = { Text("Proposal App No") },
                            singleLine = true,
                            modifier = Modifier.weight(1.4f)
                        )
                    }

                    HorizontalDivider()
                    Text("Assign Guide (College Teacher or External Mentor)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                    if (availableGuides.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            availableGuides.forEach { g ->
                                val tag = if (g.guideType == GuideType.EXTERNAL.name) "External" else "College"
                                FilterChip(
                                    selected = guideUsername.equals(g.username, ignoreCase = true),
                                    onClick = {
                                        selectedGuide = g
                                        guideUsername = g.username
                                        guideName = g.displayName
                                    },
                                    label = { Text("${g.displayName} [$tag]") }
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = guideUsername,
                            onValueChange = {
                                guideUsername = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '-' }
                                selectedGuide = availableGuides.firstOrNull { g -> g.username.equals(guideUsername, ignoreCase = true) }
                            },
                            label = { Text("Guide Username") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = guideName,
                            onValueChange = { guideName = it },
                            label = { Text("Guide Full Name *") },
                            singleLine = true,
                            modifier = Modifier.weight(1.2f)
                        )
                    }

                    TimetableMatrixGrid(
                        selectedSlots = selectedSlots,
                        editable = true,
                        onToggleSlot = { key ->
                            selectedSlots = if (key in selectedSlots) selectedSlots - key else selectedSlots + key
                        }
                    )

                    OutlinedTextField(
                        value = referenceBooks,
                        onValueChange = { referenceBooks = it },
                        label = { Text("Reference Books / Materials (Optional)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = {
                            onConfirm(
                                AdminEnrollPayload(
                                    studentUsername = studentUsername,
                                    studentName = studentName,
                                    passwordOrNull = password.ifBlank { null },
                                    cicAdNo = cicAdNo,
                                    classDepartment = classDept,
                                    stream = stream,
                                    phone = phone,
                                    academicYear = academicYear,
                                    semester = semester,
                                    subjectTitle = subjectTitle,
                                    exemptedSubject = exemptedSubject,
                                    credit = creditText.toLongOrNull() ?: 4L,
                                    guideAccount = selectedGuide,
                                    guideUsernameFallback = guideUsername,
                                    guideNameFallback = guideName,
                                    timetableSlots = selectedSlots.toList().sorted(),
                                    proposalAppNo = proposalAppNo,
                                    referenceBooks = referenceBooks
                                )
                            )
                        },
                        enabled = canSubmit,
                        modifier = Modifier.testTag("enroll_confirm_button")
                    ) {
                        Text("Enroll Off-Course")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateEditAccountDialog(
    initial: CollegeAccount,
    isNew: Boolean,
    availableGuides: List<CollegeAccount>,
    onDismiss: () -> Unit,
    onConfirm: (AccountFormPayload) -> Unit
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
    var email by remember { mutableStateOf(initial.email) }
    var guideType by remember {
        mutableStateOf(
            if (initial.guideType.isNotBlank()) GuideType.fromRaw(initial.guideType)
            else GuideType.COLLEGE_FACULTY
        )
    }
    var institution by remember {
        mutableStateOf(
            initial.institution.ifBlank {
                if (role == UserRole.GUIDE) "KKHM Islamic & Arts College" else ""
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Create User / Appoint Guide" else "Edit @${initial.username}") },
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

                if (!isNew && initial.username != "admin") {
                    Text("Account Status", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AccountStatus.entries.forEach { st ->
                            FilterChip(
                                selected = status == st,
                                onClick = { status = st },
                                label = { Text(st.label) }
                            )
                        }
                    }
                }

                if (role == UserRole.GUIDE) {
                    Text("Guide Category", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GuideType.entries.forEach { gt ->
                            FilterChip(
                                selected = guideType == gt,
                                onClick = {
                                    guideType = gt
                                    if (gt == GuideType.COLLEGE_FACULTY && institution.isBlank()) {
                                        institution = "KKHM Islamic & Arts College"
                                    }
                                },
                                label = { Text(gt.label) },
                                modifier = Modifier.testTag("dialog_guide_type_${gt.name.lowercase()}")
                            )
                        }
                    }
                    OutlinedTextField(
                        value = institution,
                        onValueChange = { institution = it },
                        label = {
                            Text(
                                if (guideType == GuideType.EXTERNAL) "External College / Institution / Expertise *"
                                else "College / Institution"
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("dialog_guide_institution")
                    )
                    OutlinedTextField(
                        value = classDept,
                        onValueChange = { classDept = it },
                        label = { Text("Department / Specialization") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
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
                                val tag = if (g.guideType == GuideType.EXTERNAL.name) "Ext" else "Clg"
                                FilterChip(
                                    selected = assignedGuide == g.username,
                                    onClick = { assignedGuide = g.username },
                                    label = { Text("@${g.username} ($tag)") }
                                )
                            }
                        }
                    }
                } else if (role != UserRole.GUIDE) {
                    OutlinedTextField(
                        value = classDept,
                        onValueChange = { classDept = it },
                        label = { Text("Department / Designation") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone / WhatsApp") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        AccountFormPayload(
                            username = username,
                            displayName = displayName,
                            role = role,
                            passwordOrNull = password.ifBlank { null },
                            status = status,
                            cicAdNo = if (role == UserRole.STUDENT) cicAdNo else "",
                            classDepartment = classDept,
                            stream = if (role == UserRole.STUDENT) stream else "",
                            assignedGuideUsername = if (role == UserRole.STUDENT) assignedGuide else "",
                            phone = phone,
                            email = email,
                            guideType = if (role == UserRole.GUIDE) guideType.name else "",
                            institution = if (role == UserRole.GUIDE || role == UserRole.PRINCIPAL) institution else ""
                        )
                    )
                },
                enabled = username.length >= 3 && displayName.isNotBlank() && (!isNew || password.length >= 4),
                modifier = Modifier.testTag("dialog_save_account_button")
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReassignGuideDialog(
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
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (guides.isNotEmpty()) {
                    Text("Quick Pick Active Guide (College & External):", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        guides.forEach { g ->
                            val tag = if (g.guideType == GuideType.EXTERNAL.name) "External" else "College"
                            FilterChip(
                                selected = guideUsername == g.username,
                                onClick = {
                                    guideUsername = g.username
                                    guideName = g.displayName
                                    if (g.email.isNotBlank()) guideEmail = g.email
                                },
                                label = { Text("${g.displayName} [$tag]") }
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
