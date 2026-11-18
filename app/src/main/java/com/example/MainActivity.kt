package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.local.PreferencesManager
import com.example.data.model.DarkModePreference
import com.example.data.model.OffCourseRecord
import com.example.data.model.UiState
import com.example.data.model.UserRole
import com.example.data.remote.authStateFlow
import com.example.data.remote.signOutUser
import com.example.export.OffCoursePdfExporter
import com.example.ui.components.ThemeToggleIconButton
import com.example.ui.screens.AcademicSettingsScreen
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.GuideDashboardScreen
import com.example.ui.screens.OffCourseRecordFormScreen
import com.example.ui.screens.PortalLoginScreen
import com.example.ui.screens.PrincipalDashboardScreen
import com.example.ui.screens.RecordBookDetailScreen
import com.example.ui.screens.StudentHomeScreen
import com.example.ui.theme.CicOffCourseTheme
import com.example.ui.viewmodel.MainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

object Routes {
    const val WORKSPACE = "workspace"
    const val SETTINGS = "settings"
    const val RECORD_FORM = "record_form?recordId={recordId}"
    const val RECORD_DETAIL = "record_detail/{recordId}"

    fun recordForm(recordId: String? = null): String =
        if (recordId.isNullOrBlank()) "record_form" else "record_form?recordId=$recordId"

    fun recordDetail(recordId: String): String = "record_detail/$recordId"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val prefsManager = remember(context) { PreferencesManager(context.applicationContext) }
            val darkModePref by prefsManager.darkModeFlow.collectAsStateWithLifecycle(
                initialValue = DarkModePreference.SYSTEM
            )
            val scope = rememberCoroutineScope()

            CicOffCourseTheme(darkModePreference = darkModePref) {
                AppNavigation(
                    darkModePreference = darkModePref,
                    onCycleDarkMode = { next ->
                        scope.launch { prefsManager.setDarkMode(next) }
                    }
                )
            }
        }
    }
}

@Composable
fun AppNavigation(
    darkModePreference: DarkModePreference,
    onCycleDarkMode: (DarkModePreference) -> Unit,
    auth: FirebaseAuth = Firebase.auth
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(
            darkModePreference = darkModePreference,
            onCycleDarkMode = onCycleDarkMode,
            onAuthSuccess = {}
        )
    } else {
        val uid = user.uid
        val email = user.email.orEmpty().trim().lowercase()
        val displayName = user.displayName.orEmpty()
        key(uid) {
            AuthenticatedShell(
                currentUserId = uid,
                currentUserEmail = email,
                currentUserName = displayName
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedShell(
    currentUserId: String,
    currentUserEmail: String,
    currentUserName: String
) {
    val context = LocalContext.current
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val scope = rememberCoroutineScope()

    val viewModel: MainViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val deps = MainViewModel.provideUserDefaults(app)
                MainViewModel(
                    repository = deps.repository,
                    accountRepository = deps.accountRepository,
                    preferencesManager = deps.preferencesManager,
                    notificationScheduler = deps.notificationScheduler,
                    currentUserId = currentUserId,
                    currentUserEmail = currentUserEmail,
                    currentUserName = currentUserName
                )
            }
        }
    )

    val activeAccountState by viewModel.activeAccountState.collectAsStateWithLifecycle()
    val activeRole by viewModel.activeRole.collectAsStateWithLifecycle()
    val darkModePreference by viewModel.darkModePreference.collectAsStateWithLifecycle()
    val notificationPrefs by viewModel.notificationPreferences.collectAsStateWithLifecycle()
    val termsState by viewModel.academicTermsState.collectAsStateWithLifecycle()
    val collegeAccountsState by viewModel.collegeAccountsState.collectAsStateWithLifecycle()
    val studentRecordsState by viewModel.studentRecordsState.collectAsStateWithLifecycle()
    val guideRecordsState by viewModel.guideRecordsState.collectAsStateWithLifecycle()
    val principalRecordsState by viewModel.principalRecordsState.collectAsStateWithLifecycle()
    val adminRecordsState by viewModel.adminRecordsState.collectAsStateWithLifecycle()
    val selectedRecordState by viewModel.selectedRecordState.collectAsStateWithLifecycle()
    val weeklyReportsState by viewModel.weeklyReportsState.collectAsStateWithLifecycle()
    val selectedYear by viewModel.selectedAcademicYear.collectAsStateWithLifecycle()
    val selectedSemester by viewModel.selectedSemester.collectAsStateWithLifecycle()
    val isActionInProgress by viewModel.isActionInProgress.collectAsStateWithLifecycle()
    val portalLoginError by viewModel.portalLoginError.collectAsStateWithLifecycle()
    val seededCredentials by viewModel.seededCredentialsDialog.collectAsStateWithLifecycle()
    val bulkImportState by viewModel.bulkImportState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val activeAccount = (activeAccountState as? UiState.Success)?.data
    if (activeAccountState is UiState.Loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (activeAccount == null) {
        PortalLoginScreen(
            deviceEmail = currentUserEmail,
            isBootstrappedAdminDevice = viewModel.isBootstrappedAdminDevice,
            isLoading = isActionInProgress,
            errorMessage = portalLoginError ?: (activeAccountState as? UiState.Error)?.message,
            darkModePreference = darkModePreference,
            onCycleDarkMode = viewModel::setDarkMode,
            onLogin = { u, p -> viewModel.loginWithCredentials(u, p) },
            onAdminQuickEnter = { viewModel.bootstrapAdminSession() },
            onSwitchGoogleAccount = {
                viewModel.logoutPortalAccount()
                signOutUser(credentialManager, scope)
            }
        )
        return
    }

    val terms = (termsState as? UiState.Success)?.data.orEmpty()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showMainChrome = currentRoute == Routes.WORKSPACE || currentRoute == Routes.SETTINGS

    val exportRecordPdf: (OffCourseRecord) -> Unit = { record ->
        val reports = (weeklyReportsState as? UiState.Success)?.data
            ?.filter { it.recordId == record.id }
            .orEmpty()
        val res = OffCoursePdfExporter.exportAndShareRecordPdf(context, record, reports)
        if (res.isFailure) {
            viewModel.showMessage(res.exceptionOrNull()?.localizedMessage ?: "Failed to export PDF")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            if (showMainChrome) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = when {
                                    currentRoute == Routes.SETTINGS -> "KKHM Terms & Settings"
                                    activeRole == UserRole.ADMIN -> "KKHM Admin Control"
                                    activeRole == UserRole.GUIDE -> "KKHM Mentor Portal"
                                    activeRole == UserRole.PRINCIPAL -> "KKHM Principal Dashboard"
                                    else -> "KKHM Off-Course"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "@${activeAccount.username} • ${activeRole.title}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.logoutPortalAccount() },
                            modifier = Modifier.testTag("topbar_logout_portal_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Switch User / Lock Portal"
                            )
                        }
                        Spacer(Modifier.width(2.dp))
                        ThemeToggleIconButton(
                            preference = darkModePreference,
                            onCycle = { viewModel.setDarkMode(it) }
                        )
                    }
                )
            }
        },
        bottomBar = {
            if (showMainChrome) {
                NavigationBar {
                    val workspaceIcon = when (activeRole) {
                        UserRole.STUDENT -> Icons.Default.Dashboard
                        UserRole.GUIDE -> Icons.Default.SupervisorAccount
                        UserRole.PRINCIPAL -> Icons.Default.VerifiedUser
                        UserRole.ADMIN -> Icons.Default.AdminPanelSettings
                    }
                    NavigationBarItem(
                        selected = currentRoute == Routes.WORKSPACE,
                        onClick = {
                            navController.navigate(Routes.WORKSPACE) {
                                popUpTo(Routes.WORKSPACE) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(workspaceIcon, contentDescription = "Workspace") },
                        label = {
                            Text(
                                when (activeRole) {
                                    UserRole.STUDENT -> "My Records"
                                    UserRole.GUIDE -> "Mentees"
                                    UserRole.PRINCIPAL -> "Principal"
                                    UserRole.ADMIN -> "Admin"
                                }
                            )
                        },
                        modifier = Modifier.testTag("nav_workspace")
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.SETTINGS,
                        onClick = {
                            navController.navigate(Routes.SETTINGS) {
                                popUpTo(Routes.WORKSPACE)
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Terms & Settings") },
                        modifier = Modifier.testTag("nav_settings")
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == Routes.WORKSPACE && activeRole == UserRole.STUDENT) {
                ExtendedFloatingActionButton(
                    onClick = { navController.navigate(Routes.recordForm()) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("New Record") },
                    modifier = Modifier.testTag("fab_new_record")
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.WORKSPACE,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.WORKSPACE) {
                when (activeRole) {
                    UserRole.STUDENT -> StudentHomeScreen(
                        recordsState = studentRecordsState,
                        terms = terms,
                        selectedYear = selectedYear,
                        selectedSemester = selectedSemester,
                        onSelectYear = viewModel::selectAcademicYear,
                        onSelectSemester = viewModel::selectSemester,
                        onOpenRecord = { id ->
                            viewModel.selectRecord(id)
                            navController.navigate(Routes.recordDetail(id))
                        },
                        onCreateNewRecord = { navController.navigate(Routes.recordForm()) },
                        onExportPdf = exportRecordPdf
                    )
                    UserRole.GUIDE -> GuideDashboardScreen(
                        recordsState = guideRecordsState,
                        terms = terms,
                        activeUsername = activeAccount.username,
                        userEmail = currentUserEmail,
                        selectedYear = selectedYear,
                        selectedSemester = selectedSemester,
                        onSelectYear = viewModel::selectAcademicYear,
                        onSelectSemester = viewModel::selectSemester,
                        onOpenRecord = { id ->
                            viewModel.selectRecord(id)
                            navController.navigate(Routes.recordDetail(id))
                        }
                    )
                    UserRole.PRINCIPAL -> PrincipalDashboardScreen(
                        recordsState = principalRecordsState,
                        terms = terms,
                        userEmail = currentUserEmail,
                        selectedYear = selectedYear,
                        selectedSemester = selectedSemester,
                        onSelectYear = viewModel::selectAcademicYear,
                        onSelectSemester = viewModel::selectSemester,
                        onOpenRecord = { id ->
                            viewModel.selectRecord(id)
                            navController.navigate(Routes.recordDetail(id))
                        },
                        onOpenAcademicSettings = {
                            navController.navigate(Routes.SETTINGS)
                        }
                    )
                    UserRole.ADMIN -> AdminDashboardScreen(
                        accountsState = collegeAccountsState,
                        recordsState = adminRecordsState,
                        terms = terms,
                        selectedYear = selectedYear,
                        selectedSemester = selectedSemester,
                        seededCredentials = seededCredentials,
                        onDismissSeededCredentials = viewModel::dismissSeededCredentialsDialog,
                        onSelectYear = viewModel::selectAcademicYear,
                        onSelectSemester = viewModel::selectSemester,
                        onSaveAccount = { u, n, r, p, st, cic, dept, str, gUser, ph ->
                            viewModel.saveCollegeAccount(u, n, r, p, st, cic, dept, str, gUser, ph)
                        },
                        onResetPassword = { u, pwd -> viewModel.resetAccountPassword(u, pwd) },
                        onToggleAccountStatus = viewModel::updateAccountStatus,
                        onDeleteAccount = viewModel::deleteCollegeAccount,
                        onSeedSampleAccounts = viewModel::seedSampleAccounts,
                        onOpenRecord = { id ->
                            viewModel.selectRecord(id)
                            navController.navigate(Routes.recordDetail(id))
                        },
                        onReassignGuide = viewModel::adminReassignGuide,
                        onDeleteRecord = { id -> viewModel.deleteRecord(id) },
                        bulkImportState = bulkImportState,
                        onStartBulkImport = viewModel::startBulkUserImport,
                        onResetBulkImport = viewModel::resetBulkImportState,
                        onShowMessage = viewModel::showMessage
                    )
                }
            }

            composable(Routes.SETTINGS) {
                AcademicSettingsScreen(
                    activeAccount = activeAccount,
                    userEmail = currentUserEmail,
                    activeRole = activeRole,
                    darkModePreference = darkModePreference,
                    notificationPrefs = notificationPrefs,
                    termsState = termsState,
                    onSelectDarkMode = viewModel::setDarkMode,
                    onLogoutPortal = viewModel::logoutPortalAccount,
                    onUpdateNotifications = viewModel::updateNotificationSettings,
                    onSendTestNotification = { viewModel.sendTestNotification() },
                    onCreateTerm = { y, s, st, w, p, n ->
                        viewModel.createAcademicTerm(y, s, st, w, p, n)
                    },
                    onUpdateTermStatus = viewModel::updateAcademicTermStatus,
                    onSignOut = {
                        viewModel.logoutPortalAccount()
                        signOutUser(credentialManager, scope)
                    }
                )
            }

            composable(
                route = "record_form?recordId={recordId}",
                arguments = listOf(
                    navArgument("recordId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { entry ->
                val recordId = entry.arguments?.getString("recordId")
                val pool = ((studentRecordsState as? UiState.Success)?.data.orEmpty() +
                    (adminRecordsState as? UiState.Success)?.data.orEmpty())
                val existing = pool.firstOrNull { it.id == recordId }
                OffCourseRecordFormScreen(
                    existingRecord = existing,
                    activeAccount = activeAccount,
                    userEmail = currentUserEmail,
                    terms = terms,
                    isSaving = isActionInProgress,
                    onBack = { navController.popBackStack() },
                    onSave = { updated ->
                        viewModel.saveOffCourseRecord(updated) { savedId ->
                            viewModel.selectRecord(savedId)
                            navController.popBackStack()
                        }
                    }
                )
            }

            composable(
                route = Routes.RECORD_DETAIL,
                arguments = listOf(navArgument("recordId") { type = NavType.StringType })
            ) { entry ->
                val recordId = entry.arguments?.getString("recordId").orEmpty()
                LaunchedEffect(recordId) {
                    if (recordId.isNotBlank()) viewModel.selectRecord(recordId)
                }
                RecordBookDetailScreen(
                    recordState = selectedRecordState,
                    reportsState = weeklyReportsState,
                    activeRole = activeRole,
                    currentUserId = currentUserId,
                    currentUserName = activeAccount.displayName.ifBlank { currentUserName },
                    notificationPrefs = notificationPrefs,
                    onBack = { navController.popBackStack() },
                    onEditRecord = { id -> navController.navigate(Routes.recordForm(id)) },
                    onSubmitForReview = viewModel::submitRecordForReview,
                    onExportPdf = { rec, reps ->
                        val res = OffCoursePdfExporter.exportAndShareRecordPdf(context, rec, reps)
                        if (res.isFailure) {
                            viewModel.showMessage(res.exceptionOrNull()?.localizedMessage ?: "PDF export failed")
                        }
                    },
                    onSaveWeeklyReport = { rec, rep -> viewModel.saveWeeklyReport(rec, rep) },
                    onVerifyWeeklyReport = { rec, weekId, status, sig, fb ->
                        viewModel.verifyWeeklyReport(rec, weekId, status, sig, fb)
                    },
                    onSubmitGuideReview = viewModel::submitGuideReview,
                    onSubmitPrincipalApproval = viewModel::submitPrincipalApproval,
                    onUpdateNotifications = viewModel::updateNotificationSettings,
                    onSendTestNotification = { viewModel.sendTestNotification() }
                )
            }
        }
    }
}
