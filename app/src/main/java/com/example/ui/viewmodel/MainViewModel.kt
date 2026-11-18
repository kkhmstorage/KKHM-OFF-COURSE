package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.local.NotificationPreferences
import com.example.data.local.PreferencesManager
import com.example.data.model.AcademicTerm
import com.example.data.model.AccountStatus
import com.example.data.model.CollegeAccount
import com.example.data.model.DarkModePreference
import com.example.data.model.OffCourseRecord
import com.example.data.model.RecordStatus
import com.example.data.model.SignatureStatus
import com.example.data.model.TermStatus
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.WeekVerificationStatus
import com.example.data.model.WeeklyStudyReport
import com.example.data.remote.AppUpdateHelper
import com.example.data.remote.AppUpdateRepository
import com.example.data.remote.BulkImportRow
import com.example.data.remote.BulkImportUiState
import com.example.data.remote.CollegeAccountRepository
import com.example.data.remote.OffCourseRepository
import com.example.data.remote.RemoteAppRelease
import com.example.data.remote.SeededCredential
import com.example.data.remote.UpdateCheckUiState
import com.example.notifications.PeriodNotificationScheduler
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UserDefaultsDependencies(
    val repository: OffCourseRepository,
    val accountRepository: CollegeAccountRepository,
    val preferencesManager: PreferencesManager,
    val notificationScheduler: PeriodNotificationScheduler
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: OffCourseRepository,
    private val accountRepository: CollegeAccountRepository,
    private val preferencesManager: PreferencesManager,
    private val notificationScheduler: PeriodNotificationScheduler,
    private val currentUserId: String,
    private val currentUserEmail: String,
    private val currentUserName: String,
    private val updateRepository: AppUpdateRepository = AppUpdateRepository()
) : ViewModel() {

    private val _selectedRecordId = MutableStateFlow<String?>(null)
    val selectedRecordId: StateFlow<String?> = _selectedRecordId.asStateFlow()

    private val _portalLoginError = MutableStateFlow<String?>(null)
    val portalLoginError: StateFlow<String?> = _portalLoginError.asStateFlow()

    private val _seededCredentialsDialog = MutableStateFlow<List<SeededCredential>>(emptyList())
    val seededCredentialsDialog: StateFlow<List<SeededCredential>> = _seededCredentialsDialog.asStateFlow()

    private val _bulkImportState = MutableStateFlow(BulkImportUiState())
    val bulkImportState: StateFlow<BulkImportUiState> = _bulkImportState.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    private val _isActionInProgress = MutableStateFlow(false)
    val isActionInProgress: StateFlow<Boolean> = _isActionInProgress.asStateFlow()

    private val _remoteRelease = MutableStateFlow<RemoteAppRelease?>(null)
    private val _isCheckingUpdate = MutableStateFlow(false)
    private val _updateError = MutableStateFlow<String?>(null)

    val updateState: StateFlow<UpdateCheckUiState> = combine(
        _isCheckingUpdate,
        _remoteRelease,
        _updateError,
        preferencesManager.skippedUpdateVersionFlow
    ) { checking, release, error, skippedVersion ->
        when {
            checking -> UpdateCheckUiState.Checking
            release != null -> {
                val hasUpdate = AppUpdateHelper.isUpdateAvailable(
                    localVersionCode = BuildConfig.VERSION_CODE,
                    localBuildNumber = BuildConfig.CI_RUN_NUMBER,
                    localCommitSha = BuildConfig.BUILD_COMMIT_SHA,
                    remote = release
                )
                if (hasUpdate) {
                    UpdateCheckUiState.UpdateAvailable(
                        release = release,
                        currentVersionName = BuildConfig.VERSION_NAME,
                        currentVersionCode = BuildConfig.VERSION_CODE,
                        isDismissedBanner = skippedVersion >= release.versionCode
                    )
                } else {
                    UpdateCheckUiState.UpToDate(
                        currentVersionName = BuildConfig.VERSION_NAME,
                        currentVersionCode = BuildConfig.VERSION_CODE
                    )
                }
            }
            error != null -> UpdateCheckUiState.Error(error)
            else -> UpdateCheckUiState.Idle
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UpdateCheckUiState.Idle)

    val isBootstrappedAdminDevice: Boolean = accountRepository.isBootstrappedAdminDevice()

    val darkModePreference: StateFlow<DarkModePreference> = preferencesManager.darkModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DarkModePreference.SYSTEM)

    val notificationPreferences: StateFlow<NotificationPreferences> =
        preferencesManager.notificationPreferencesFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), NotificationPreferences())

    val loggedInUsername: StateFlow<String?> = preferencesManager.loggedInUsernameFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val activeAccountState: StateFlow<UiState<CollegeAccount?>> = loggedInUsername
        .flatMapLatest { username ->
            if (username.isNullOrBlank()) {
                flowOf(UiState.Success(null))
            } else {
                accountRepository.observeAccount(username)
                    .map<CollegeAccount?, UiState<CollegeAccount?>> { account ->
                        if (account == null || account.accountStatus == AccountStatus.SUSPENDED.name) {
                            UiState.Success(null)
                        } else {
                            accountRepository.syncActiveAccountSession(account)
                            UiState.Success(account)
                        }
                    }
                    .catch { error ->
                        val fsErr = error.rootFirestoreError()
                        if (fsErr == null && error is CancellationException) throw error
                        Log.w(TAG, "Error observing active college account", fsErr ?: error)
                        emit(UiState.Error((fsErr ?: error).message ?: "Failed to verify portal session"))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val assignedGuideAccount: StateFlow<CollegeAccount?> = activeAccountState
        .flatMapLatest { accState ->
            val guideUser = (accState as? UiState.Success)?.data?.assignedGuideUsername.orEmpty().trim()
            if (guideUser.isBlank()) flowOf<CollegeAccount?>(null)
            else flow<CollegeAccount?> {
                emit(accountRepository.getAccountByUsername(guideUser))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val activeRole: StateFlow<UserRole> = activeAccountState
        .map { state ->
            when (state) {
                is UiState.Success -> UserRole.fromRaw(state.data?.role)
                else -> UserRole.STUDENT
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UserRole.STUDENT)

    val userProfileState: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error ->
            val fsErr = error.rootFirestoreError()
            if (fsErr == null && error is CancellationException) throw error
            emit(UiState.Error((fsErr ?: error).message ?: "Failed to load profile"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val academicTermsState: StateFlow<UiState<List<AcademicTerm>>> = repository.observeAcademicTerms()
        .map<List<AcademicTerm>, UiState<List<AcademicTerm>>> { UiState.Success(it) }
        .catch { error ->
            val fsErr = error.rootFirestoreError()
            if (fsErr == null && error is CancellationException) throw error
            emit(UiState.Error((fsErr ?: error).message ?: "Failed to load academic terms"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val selectedAcademicYear: StateFlow<String> = combine(
        preferencesManager.selectedAcademicYearFlow,
        academicTermsState
    ) { saved, termsUi ->
        if (!saved.isNullOrBlank()) {
            saved
        } else {
            val terms = (termsUi as? UiState.Success)?.data.orEmpty()
            val active = terms.firstOrNull { it.status == TermStatus.ACTIVE.name }
            active?.academicYear ?: "2026 - 2027"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "2026 - 2027")

    val selectedSemester: StateFlow<String> = combine(
        preferencesManager.selectedSemesterFlow,
        academicTermsState
    ) { saved, termsUi ->
        if (!saved.isNullOrBlank()) {
            saved
        } else {
            val terms = (termsUi as? UiState.Success)?.data.orEmpty()
            val active = terms.firstOrNull { it.status == TermStatus.ACTIVE.name }
            active?.semester ?: "Nov"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "Nov")

    val collegeAccountsState: StateFlow<UiState<List<CollegeAccount>>> = activeRole
        .flatMapLatest { role ->
            if (role != UserRole.ADMIN) {
                flowOf(UiState.Success(emptyList()))
            } else {
                accountRepository.observeAllAccounts()
                    .map<List<CollegeAccount>, UiState<List<CollegeAccount>>> { UiState.Success(it) }
                    .catch { error ->
                        val fsErr = error.rootFirestoreError()
                        if (fsErr == null && error is CancellationException) throw error
                        emit(UiState.Error((fsErr ?: error).message ?: "Failed to load college accounts"))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val studentRecordsState: StateFlow<UiState<List<OffCourseRecord>>> = activeAccountState
        .flatMapLatest { accState ->
            val account = (accState as? UiState.Success)?.data
            if (account == null) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeStudentRecords(currentUserId, account.username)
                    .map<List<OffCourseRecord>, UiState<List<OffCourseRecord>>> { UiState.Success(it) }
                    .catch { error ->
                        val fsErr = error.rootFirestoreError()
                        if (fsErr == null && error is CancellationException) throw error
                        emit(UiState.Error((fsErr ?: error).message ?: "Failed to load student records"))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val guideRecordsState: StateFlow<UiState<List<OffCourseRecord>>> = activeAccountState
        .flatMapLatest { accState ->
            val account = (accState as? UiState.Success)?.data
            if (account == null || UserRole.fromRaw(account.role) != UserRole.GUIDE) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeGuideRecords(currentUserEmail, account.username)
                    .map<List<OffCourseRecord>, UiState<List<OffCourseRecord>>> { UiState.Success(it) }
                    .catch { error ->
                        val fsErr = error.rootFirestoreError()
                        if (fsErr == null && error is CancellationException) throw error
                        emit(UiState.Error((fsErr ?: error).message ?: "Failed to load mentor records"))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val principalRecordsState: StateFlow<UiState<List<OffCourseRecord>>> = activeRole
        .flatMapLatest { role ->
            if (role != UserRole.PRINCIPAL && role != UserRole.ADMIN) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observePrincipalRecords(currentUserEmail)
                    .map<List<OffCourseRecord>, UiState<List<OffCourseRecord>>> { UiState.Success(it) }
                    .catch { error ->
                        val fsErr = error.rootFirestoreError()
                        if (fsErr == null && error is CancellationException) throw error
                        emit(UiState.Error((fsErr ?: error).message ?: "Failed to load principal records"))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val adminRecordsState: StateFlow<UiState<List<OffCourseRecord>>> = activeRole
        .flatMapLatest { role ->
            if (role != UserRole.ADMIN) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeAllAdminRecords()
                    .map<List<OffCourseRecord>, UiState<List<OffCourseRecord>>> { UiState.Success(it) }
                    .catch { error ->
                        val fsErr = error.rootFirestoreError()
                        if (fsErr == null && error is CancellationException) throw error
                        emit(UiState.Error((fsErr ?: error).message ?: "Failed to load admin records"))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val selectedRecordState: StateFlow<UiState<OffCourseRecord?>> = _selectedRecordId
        .flatMapLatest { recordId ->
            if (recordId.isNullOrBlank()) flowOf(UiState.Success(null))
            else repository.observeRecord(recordId)
                .map<OffCourseRecord?, UiState<OffCourseRecord?>> { UiState.Success(it) }
                .catch { error ->
                    val fsErr = error.rootFirestoreError()
                    if (fsErr == null && error is CancellationException) throw error
                    emit(UiState.Error((fsErr ?: error).message ?: "Failed to load record"))
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Success(null))

    val weeklyReportsState: StateFlow<UiState<List<WeeklyStudyReport>>> =
        combine(_selectedRecordId, activeAccountState, selectedRecordState) { recordId, accState, recordState ->
            Triple(
                recordId,
                (accState as? UiState.Success)?.data,
                (recordState as? UiState.Success)?.data
            )
        }.flatMapLatest { (recordId, account, record) ->
            if (recordId.isNullOrBlank() || record == null || account == null) {
                flowOf(UiState.Success(emptyList()))
            } else {
                val role = UserRole.fromRaw(account.role)
                repository.observeWeeklyReports(
                    recordId = recordId,
                    role = role,
                    studentUid = record.studentUid,
                    guideUsername = if (role == UserRole.GUIDE) account.username else record.guideUsername,
                    studentUsername = if (role == UserRole.STUDENT) account.username else record.studentUsername
                )
                    .map<List<WeeklyStudyReport>, UiState<List<WeeklyStudyReport>>> { UiState.Success(it) }
                    .catch { error ->
                        val fsErr = error.rootFirestoreError()
                        if (fsErr == null && error is CancellationException) throw error
                        emit(UiState.Error((fsErr ?: error).message ?: "Failed to load weekly reports"))
                    }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    init {
        viewModelScope.launch {
            combine(studentRecordsState, notificationPreferences) { recState, prefs ->
                Pair((recState as? UiState.Success)?.data.orEmpty(), prefs)
            }.collect { (records, prefs) ->
                notificationScheduler.syncAlertsForRecords(
                    records = records.filter { it.recordStatus != RecordStatus.ARCHIVED.name },
                    enabled = prefs.enabled,
                    alertHour = prefs.alertHour,
                    alertMinute = prefs.alertMinute
                )
            }
        }
        checkForAppUpdate(silent = true)
    }

    fun checkForAppUpdate(silent: Boolean = false) {
        if (_isCheckingUpdate.value) return
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            _updateError.value = null
            val res = updateRepository.fetchLatestRelease()
            res.onSuccess { release ->
                _remoteRelease.value = release
                val hasUpdate = AppUpdateHelper.isUpdateAvailable(
                    localVersionCode = BuildConfig.VERSION_CODE,
                    localBuildNumber = BuildConfig.CI_RUN_NUMBER,
                    localCommitSha = BuildConfig.BUILD_COMMIT_SHA,
                    remote = release
                )
                if (!silent) {
                    if (hasUpdate) {
                        preferencesManager.setSkippedUpdateVersion(0)
                        _snackbarEvents.emit("New update v${release.versionName} is ready to download!")
                    } else {
                        _snackbarEvents.emit("You are already on the latest version (v${BuildConfig.VERSION_NAME}).")
                    }
                }
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "Unable to check GitHub Releases."
                if (!silent) {
                    _updateError.value = msg
                    _snackbarEvents.emit("Update check failed: $msg")
                }
            }
            _isCheckingUpdate.value = false
        }
    }

    fun dismissUpdateBanner(versionCode: Int) {
        viewModelScope.launch {
            preferencesManager.setSkippedUpdateVersion(versionCode)
        }
    }

    fun clearPortalLoginError() {
        _portalLoginError.value = null
    }

    fun dismissSeededCredentialsDialog() {
        _seededCredentialsDialog.value = emptyList()
    }

    fun loginWithCredentials(username: String, password: String) {
        viewModelScope.launch {
            _portalLoginError.value = null
            _isActionInProgress.value = true
            val res = accountRepository.authenticate(username, password)
            res.onSuccess { account ->
                preferencesManager.setLoggedInUsername(account.username)
                repository.seedDefaultAcademicTermsIfNeeded(UserRole.fromRaw(account.role))
                _snackbarEvents.emit("Welcome, ${account.displayName} (${UserRole.fromRaw(account.role).title})")
            }.onFailure { err ->
                _portalLoginError.value = err.localizedMessage ?: "Login failed"
            }
            _isActionInProgress.value = false
        }
    }

    fun bootstrapAdminSession() {
        viewModelScope.launch {
            _portalLoginError.value = null
            _isActionInProgress.value = true
            val res = accountRepository.ensureBootstrappedAdminAccount()
            res.onSuccess { account ->
                preferencesManager.setLoggedInUsername(account.username)
                repository.seedDefaultAcademicTermsIfNeeded(UserRole.ADMIN)
                _snackbarEvents.emit("Signed into KKHM College Admin Control Center")
            }.onFailure { err ->
                _portalLoginError.value = err.localizedMessage ?: "Admin bootstrap failed"
            }
            _isActionInProgress.value = false
        }
    }

    fun logoutPortalAccount() {
        viewModelScope.launch {
            _selectedRecordId.value = null
            _portalLoginError.value = null
            preferencesManager.setLoggedInUsername(null)
            _snackbarEvents.emit("Locked KKHM Portal. Enter username & password to continue.")
        }
    }

    fun saveCollegeAccount(
        username: String,
        displayName: String,
        role: UserRole,
        passwordOrNull: String?,
        status: AccountStatus,
        cicAdNo: String,
        classDepartment: String,
        stream: String,
        assignedGuideUsername: String,
        phone: String,
        onSaved: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isActionInProgress.value = true
            val res = accountRepository.saveCollegeAccount(
                rawUsername = username,
                displayName = displayName,
                role = role,
                rawPasswordOrNull = passwordOrNull,
                accountStatus = status,
                cicAdNo = cicAdNo,
                classDepartment = classDepartment,
                stream = stream,
                assignedGuideUsername = assignedGuideUsername,
                phone = phone
            )
            if (res.isSuccess) {
                _snackbarEvents.emit("Account '$username' saved (${role.title})")
                onSaved()
            } else {
                _snackbarEvents.emit(res.exceptionOrNull()?.localizedMessage ?: "Failed to save account")
            }
            _isActionInProgress.value = false
        }
    }

    fun resetAccountPassword(username: String, newPassword: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _isActionInProgress.value = true
            val res = accountRepository.resetPassword(username, newPassword)
            if (res.isSuccess) {
                _snackbarEvents.emit("Password updated for @$username")
                onDone()
            } else {
                _snackbarEvents.emit(res.exceptionOrNull()?.localizedMessage ?: "Password reset failed")
            }
            _isActionInProgress.value = false
        }
    }

    fun updateAccountStatus(username: String, status: AccountStatus) {
        viewModelScope.launch {
            val res = accountRepository.updateAccountStatus(username, status)
            if (res.isSuccess) _snackbarEvents.emit("@$username is now ${status.label}")
            else _snackbarEvents.emit(res.exceptionOrNull()?.localizedMessage ?: "Status update failed")
        }
    }

    fun deleteCollegeAccount(username: String) {
        viewModelScope.launch {
            val res = accountRepository.deleteAccount(username)
            if (res.isSuccess) _snackbarEvents.emit("Deleted @$username")
            else _snackbarEvents.emit(res.exceptionOrNull()?.localizedMessage ?: "Delete failed")
        }
    }

    fun seedSampleAccounts() {
        viewModelScope.launch {
            _isActionInProgress.value = true
            val res = accountRepository.seedSampleAccounts()
            res.onSuccess { list ->
                if (list.isEmpty()) {
                    _snackbarEvents.emit("Sample accounts (student1, student2, guide1, principal1) already exist.")
                } else {
                    _seededCredentialsDialog.value = list
                }
            }.onFailure { err ->
                _snackbarEvents.emit(err.localizedMessage ?: "Failed to create sample accounts")
            }
            _isActionInProgress.value = false
        }
    }

    fun startBulkUserImport(rows: List<BulkImportRow>) {
        val validRows = rows.filter { it.isValid }
        if (validRows.isEmpty()) {
            showMessage("No valid rows available to import.")
            return
        }
        viewModelScope.launch {
            _isActionInProgress.value = true
            _bulkImportState.value = BulkImportUiState(
                isImporting = true,
                processedCount = 0,
                totalCount = validRows.size
            )
            val result = accountRepository.bulkImportAccounts(validRows) { processed, total ->
                _bulkImportState.value = _bulkImportState.value.copy(
                    isImporting = true,
                    processedCount = processed,
                    totalCount = total
                )
            }
            _bulkImportState.value = result
            _isActionInProgress.value = false
            _snackbarEvents.emit(
                "Bulk import complete: ${result.createdCount} created, ${result.updatedCount} updated" +
                    if (result.failedRows.isNotEmpty()) ", ${result.failedRows.size} failed" else ""
            )
        }
    }

    fun resetBulkImportState() {
        _bulkImportState.value = BulkImportUiState()
    }

    fun adminReassignGuide(recordId: String, guideUsername: String, guideName: String, guideEmail: String) {
        viewModelScope.launch {
            _isActionInProgress.value = true
            val res = repository.adminReassignGuide(recordId, guideUsername, guideName, guideEmail)
            if (res.isSuccess) _snackbarEvents.emit("Assigned guide updated to $guideName")
            else _snackbarEvents.emit(res.exceptionOrNull()?.localizedMessage ?: "Guide reassignment failed")
            _isActionInProgress.value = false
        }
    }

    fun selectAcademicYear(year: String) {
        viewModelScope.launch {
            preferencesManager.setSelectedAcademicYear(year)
        }
    }

    fun selectSemester(semester: String) {
        viewModelScope.launch {
            preferencesManager.setSelectedSemester(semester)
        }
    }

    suspend fun lookupGuideAccount(username: String): CollegeAccount? =
        accountRepository.getAccountByUsername(username)

    fun selectRecord(recordId: String?) { _selectedRecordId.value = recordId }

    fun setDarkMode(preference: DarkModePreference) {
        viewModelScope.launch {
            preferencesManager.setDarkMode(preference)
            repository.updateDarkModePreference(preference)
        }
    }

    fun updateNotificationSettings(enabled: Boolean, hour: Int, minute: Int) {
        viewModelScope.launch {
            preferencesManager.setNotificationPreferences(enabled, hour, minute)
            _snackbarEvents.emit(
                if (enabled) "Period alerts scheduled at %02d:%02d".format(hour, minute)
                else "Period alerts paused"
            )
        }
    }

    fun sendTestNotification(): Boolean {
        val records = (studentRecordsState.value as? UiState.Success)?.data.orEmpty()
        val sent = notificationScheduler.sendTestNotification(records)
        viewModelScope.launch {
            _snackbarEvents.emit(
                if (sent) "Period reminder notification sent!"
                else "Please allow Notification permission to receive period alerts."
            )
        }
        return sent
    }

    fun saveOffCourseRecord(record: OffCourseRecord, onSaved: (String) -> Unit = {}) {
        viewModelScope.launch {
            _isActionInProgress.value = true
            val account = (activeAccountState.value as? UiState.Success)?.data
            val enriched = if (account != null) {
                record.copy(
                    studentUsername = record.studentUsername.ifBlank { account.username },
                    guideUsername = record.guideUsername.ifBlank { account.assignedGuideUsername }
                )
            } else {
                record
            }
            val result = repository.saveOffCourseRecord(enriched)
            result.onSuccess { id ->
                if (enriched.academicYear.isNotBlank() && selectedAcademicYear.value != "ALL" &&
                    selectedAcademicYear.value != enriched.academicYear
                ) {
                    preferencesManager.setSelectedAcademicYear(enriched.academicYear)
                }
                if (enriched.semester.isNotBlank() && selectedSemester.value != "ALL" &&
                    selectedSemester.value != enriched.semester
                ) {
                    preferencesManager.setSelectedSemester(enriched.semester)
                }
                _snackbarEvents.emit("Off-Course Record saved")
                onSaved(id)
            }.onFailure { err ->
                _snackbarEvents.emit(err.localizedMessage ?: "Failed to save record")
            }
            _isActionInProgress.value = false
        }
    }

    fun submitRecordForReview(recordId: String) {
        viewModelScope.launch {
            _isActionInProgress.value = true
            val res = repository.submitRecordForReview(recordId)
            if (res.isSuccess) _snackbarEvents.emit("Record submitted to Guide & Principal")
            else _snackbarEvents.emit(res.exceptionOrNull()?.localizedMessage ?: "Submission failed")
            _isActionInProgress.value = false
        }
    }

    fun saveWeeklyReport(record: OffCourseRecord, report: WeeklyStudyReport, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            _isActionInProgress.value = true
            val res = repository.saveWeeklyReport(record, report)
            if (res.isSuccess) {
                _snackbarEvents.emit("Week ${report.weekNumber} study report & periods logged")
                onSaved()
            } else {
                _snackbarEvents.emit(res.exceptionOrNull()?.localizedMessage ?: "Failed to save week report")
            }
            _isActionInProgress.value = false
        }
    }

    fun verifyWeeklyReport(
        record: OffCourseRecord,
        weekId: String,
        status: WeekVerificationStatus,
        signatureName: String,
        feedback: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isActionInProgress.value = true
            val res = repository.verifyWeeklyReport(
                record = record,
                weekId = weekId,
                status = status,
                authorizedSignature = signatureName,
                mentorFeedback = feedback,
                role = activeRole.value
            )
            if (res.isSuccess) {
                _snackbarEvents.emit("Week verification updated: ${status.label}")
                onDone()
            } else {
                _snackbarEvents.emit(res.exceptionOrNull()?.localizedMessage ?: "Verification failed")
            }
            _isActionInProgress.value = false
        }
    }

    fun submitGuideReview(recordId: String, status: SignatureStatus, signatureName: String, remarks: String) {
        viewModelScope.launch {
            _isActionInProgress.value = true
            val res = repository.submitGuideReview(recordId, status, signatureName, remarks)
            if (res.isSuccess) _snackbarEvents.emit("Guide signature saved (${status.label})")
            else _snackbarEvents.emit(res.exceptionOrNull()?.localizedMessage ?: "Guide review failed")
            _isActionInProgress.value = false
        }
    }

    fun submitPrincipalApproval(
        recordId: String,
        signatureStatus: SignatureStatus,
        signatureName: String,
        remarks: String,
        recordStatus: RecordStatus
    ) {
        viewModelScope.launch {
            _isActionInProgress.value = true
            val res = repository.submitPrincipalApproval(recordId, signatureStatus, signatureName, remarks, recordStatus)
            if (res.isSuccess) _snackbarEvents.emit("Principal authorization saved (${recordStatus.label})")
            else _snackbarEvents.emit(res.exceptionOrNull()?.localizedMessage ?: "Principal approval failed")
            _isActionInProgress.value = false
        }
    }

    fun createAcademicTerm(
        academicYear: String,
        semester: String,
        status: TermStatus,
        totalWeeks: Long,
        defaultPeriodsPerWeek: Long,
        notes: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isActionInProgress.value = true
            val res = repository.createAcademicTerm(academicYear, semester, status, totalWeeks, defaultPeriodsPerWeek, notes)
            if (res.isSuccess) {
                _snackbarEvents.emit("Academic term $academicYear ($semester) saved")
                onSuccess()
            } else {
                _snackbarEvents.emit(res.exceptionOrNull()?.localizedMessage ?: "Failed to add academic term")
            }
            _isActionInProgress.value = false
        }
    }

    fun updateAcademicTermStatus(termId: String, status: TermStatus) {
        viewModelScope.launch {
            val res = repository.updateAcademicTermStatus(termId, status)
            if (res.isSuccess) _snackbarEvents.emit("Term status updated to ${status.label}")
            else _snackbarEvents.emit(res.exceptionOrNull()?.localizedMessage ?: "Failed to update term status")
        }
    }

    fun deleteRecord(recordId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            val res = repository.deleteOffCourseRecord(recordId)
            if (res.isSuccess) {
                _snackbarEvents.emit("Off-Course Record deleted")
                onDeleted()
            } else {
                _snackbarEvents.emit(res.exceptionOrNull()?.localizedMessage ?: "Delete failed")
            }
        }
    }

    fun showMessage(msg: String) {
        viewModelScope.launch { _snackbarEvents.emit(msg) }
    }

    private fun Throwable.rootFirestoreError(): FirebaseFirestoreException? =
        generateSequence(this) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    companion object {
        private const val TAG = "MainViewModel"

        fun provideUserDefaults(context: Context): UserDefaultsDependencies {
            val appCtx = context.applicationContext
            return UserDefaultsDependencies(
                repository = OffCourseRepository(appCtx),
                accountRepository = CollegeAccountRepository(appCtx),
                preferencesManager = PreferencesManager(appCtx),
                notificationScheduler = PeriodNotificationScheduler(appCtx)
            )
        }
    }
}
