package com.example

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.data.model.Khata
import com.example.data.model.Person
import com.example.data.model.User
import com.example.data.repository.AuditLogRepository
import com.example.data.repository.KhataRepository
import com.example.data.repository.PartnerRepository
import com.example.data.repository.PersonRepository
import com.example.data.repository.TransactionRepository
import com.example.data.repository.UserRepository
import com.example.ui.auth.FirebaseConfigMissingScreen
import com.example.ui.auth.OnboardingScreen
import com.example.ui.auth.SignInScreen
import com.example.ui.history.AuditHistoryScreen
import com.example.ui.home.HomeScreen
import com.example.ui.khata.KhataDetailScreen
import com.example.ui.partner.PartnerManagementScreen
import com.example.ui.partner.PartnerRequestsScreen
import com.example.ui.person.PersonDetailScreen
import com.example.ui.profile.ProfileScreen
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

sealed interface Screen {
    data object Home : Screen
    data class KhataDetail(val khataId: String) : Screen
    data class PersonDetail(val khataId: String, val personId: String) : Screen
    data class AuditHistory(val khataId: String) : Screen
    data class PartnerManagement(val khataId: String) : Screen
    data object PartnerRequests : Screen
    data object Profile : Screen
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainContainer()
            }
        }
    }
}

@Composable
fun MainContainer() {
    val context = LocalContext.current
    var isFirebaseReady by remember {
        mutableStateOf(isFirebaseInitialized(context))
    }

    if (isFirebaseReady) {
        val databaseIdRes = context.resources.getIdentifier("firestore_database_id", "string", context.packageName)
        val databaseId = if (databaseIdRes != 0) {
            runCatching { context.getString(databaseIdRes) }.getOrNull()?.trim().orEmpty()
        } else ""

        val firestore = remember(databaseId) {
            try {
                if (databaseId.isNotBlank() && databaseId != "(default)") {
                    FirebaseFirestore.getInstance(databaseId)
                } else {
                    FirebaseFirestore.getInstance()
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "Named Firestore database '$databaseId' failed, falling back to default", e)
                FirebaseFirestore.getInstance()
            }
        }

        val userRepository = remember(firestore) { UserRepository(firestore) }
        val khataRepository = remember(firestore) { KhataRepository(firestore) }
        val personRepository = remember(firestore) { PersonRepository(firestore) }
        val transactionRepository = remember(firestore) { TransactionRepository(firestore) }
        val partnerRepository = remember(firestore) { PartnerRepository(firestore) }
        val auditLogRepository = remember(firestore) { AuditLogRepository(firestore) }

        AppRoot(
            userRepository = userRepository,
            khataRepository = khataRepository,
            personRepository = personRepository,
            transactionRepository = transactionRepository,
            partnerRepository = partnerRepository,
            auditLogRepository = auditLogRepository
        )
    } else {
        FirebaseConfigMissingScreen(
            onRetry = {
                if (tryInitializeFirebase(context)) {
                    isFirebaseReady = true
                }
            }
        )
    }
}

private fun isFirebaseInitialized(context: Context): Boolean {
    return FirebaseApp.getApps(context).isNotEmpty() || tryInitializeFirebase(context)
}

private fun tryInitializeFirebase(context: Context): Boolean {
    if (FirebaseApp.getApps(context).isNotEmpty()) return true
    return try {
        val app = FirebaseApp.initializeApp(context)
        app != null || FirebaseApp.getApps(context).isNotEmpty()
    } catch (e: Exception) {
        Log.w("MainActivity", "Firebase initialization check: ${e.message}")
        false
    }
}

@Composable
fun AppRoot(
    userRepository: UserRepository,
    khataRepository: KhataRepository,
    personRepository: PersonRepository,
    transactionRepository: TransactionRepository,
    partnerRepository: PartnerRepository,
    auditLogRepository: AuditLogRepository
) {
    var firebaseUser by remember {
        mutableStateOf(runCatching { Firebase.auth.currentUser }.getOrNull())
    }

    DisposableEffect(Unit) {
        val auth = runCatching { Firebase.auth }.getOrNull()
        if (auth != null) {
            val listener = FirebaseAuth.AuthStateListener { a ->
                firebaseUser = a.currentUser
            }
            auth.addAuthStateListener(listener)
            onDispose {
                auth.removeAuthStateListener(listener)
            }
        } else {
            onDispose {}
        }
    }

    val user = firebaseUser
    if (user == null) {
        SignInScreen(
            onAuthSuccess = {
                firebaseUser = runCatching { Firebase.auth.currentUser }.getOrNull()
            }
        )
    } else {
        AuthenticatedAppFlow(
            firebaseUser = user,
            userRepository = userRepository,
            khataRepository = khataRepository,
            personRepository = personRepository,
            transactionRepository = transactionRepository,
            partnerRepository = partnerRepository,
            auditLogRepository = auditLogRepository,
            onSignedOut = {
                firebaseUser = null
            }
        )
    }
}

@Composable
fun AuthenticatedAppFlow(
    firebaseUser: FirebaseUser,
    userRepository: UserRepository,
    khataRepository: KhataRepository,
    personRepository: PersonRepository,
    transactionRepository: TransactionRepository,
    partnerRepository: PartnerRepository,
    auditLogRepository: AuditLogRepository,
    onSignedOut: () -> Unit
) {
    val profileStateFlow = remember(firebaseUser.uid) {
        userRepository.observeProfileState(firebaseUser.uid)
    }
    val profileState by profileStateFlow.collectAsState(initial = com.example.data.repository.ProfileState.Loading)

    when (val state = profileState) {
        is com.example.data.repository.ProfileState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        is com.example.data.repository.ProfileState.NotFound -> {
            // FIRST-TIME GOOGLE SIGN-IN: Show onboarding to choose unique public User ID
            OnboardingScreen(
                firebaseUser = firebaseUser,
                userRepository = userRepository,
                onProfileCreated = {
                    // State automatically transitions to ProfileState.Exists
                }
            )
        }

        is com.example.data.repository.ProfileState.Exists -> {
            // RETURNING GOOGLE USER: Profile already exists, keep exact same User ID, load real Khatas
            MainUdhaarKhataApp(
                user = state.user,
                khataRepository = khataRepository,
                personRepository = personRepository,
                transactionRepository = transactionRepository,
                partnerRepository = partnerRepository,
                auditLogRepository = auditLogRepository,
                userRepository = userRepository,
                onSignedOut = onSignedOut
            )
        }

        is com.example.data.repository.ProfileState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun MainUdhaarKhataApp(
    user: User,
    khataRepository: KhataRepository,
    personRepository: PersonRepository,
    transactionRepository: TransactionRepository,
    partnerRepository: PartnerRepository,
    auditLogRepository: AuditLogRepository,
    userRepository: UserRepository,
    onSignedOut: () -> Unit
) {
    var screenStack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }
    val currentScreen = screenStack.lastOrNull() ?: Screen.Home

    fun navigateTo(screen: Screen) {
        screenStack = screenStack + screen
    }

    fun navigateBack() {
        if (screenStack.size > 1) {
            screenStack = screenStack.dropLast(1)
        }
    }

    // Real-time observation of Khatas
    val khatasFlow = remember(user.uid) {
        khataRepository.observeUserKhatas(user.uid)
    }
    val khatas by khatasFlow.collectAsState(initial = emptyList())

    // Real-time observation of Incoming Partner Requests
    val incomingRequestsFlow = remember(user.uid) {
        partnerRepository.observeIncomingRequests(user.uid)
    }
    val incomingRequests by incomingRequestsFlow.collectAsState(initial = emptyList())

    // Real-time observation of Outgoing Requests
    val outgoingRequestsFlow = remember(user.uid) {
        partnerRepository.observeOutgoingRequests(user.uid)
    }
    val outgoingRequests by outgoingRequestsFlow.collectAsState(initial = emptyList())

    when (val screen = currentScreen) {
        is Screen.Home -> {
            HomeScreen(
                user = user,
                khatas = khatas,
                isLoading = false,
                pendingRequestsCount = incomingRequests.size,
                khataRepository = khataRepository,
                onKhataClick = { khata ->
                    navigateTo(Screen.KhataDetail(khata.khataId))
                },
                onProfileClick = {
                    navigateTo(Screen.Profile)
                },
                onRequestsClick = {
                    navigateTo(Screen.PartnerRequests)
                }
            )
        }

        is Screen.KhataDetail -> {
            BackHandler { navigateBack() }

            val khata = khatas.find { it.khataId == screen.khataId }
                ?: Khata(khataId = screen.khataId, name = "Khata")

            val peopleFlow = remember(screen.khataId) {
                personRepository.observePeople(screen.khataId)
            }
            val people by peopleFlow.collectAsState(initial = emptyList())

            KhataDetailScreen(
                khata = khata,
                currentUser = user,
                people = people,
                isLoading = false,
                personRepository = personRepository,
                khataRepository = khataRepository,
                onBackClick = { navigateBack() },
                onPersonClick = { person ->
                    navigateTo(Screen.PersonDetail(screen.khataId, person.personId))
                },
                onHistoryClick = {
                    navigateTo(Screen.AuditHistory(screen.khataId))
                },
                onPartnerClick = {
                    navigateTo(Screen.PartnerManagement(screen.khataId))
                }
            )
        }

        is Screen.PersonDetail -> {
            BackHandler { navigateBack() }

            val khata = khatas.find { it.khataId == screen.khataId }
                ?: Khata(khataId = screen.khataId, name = "Khata")

            val personFlow = remember(screen.khataId, screen.personId) {
                personRepository.observePerson(screen.khataId, screen.personId)
            }
            val person by personFlow.collectAsState(initial = null)

            val transactionsFlow = remember(screen.khataId, screen.personId) {
                transactionRepository.observeTransactions(screen.khataId, screen.personId)
            }
            val transactions by transactionsFlow.collectAsState(initial = emptyList())

            val currentPerson = person ?: Person(personId = screen.personId, name = "Person")

            PersonDetailScreen(
                person = currentPerson,
                khataId = screen.khataId,
                currentUser = user,
                transactions = transactions,
                isLoading = false,
                personRepository = personRepository,
                transactionRepository = transactionRepository,
                onBackClick = { navigateBack() }
            )
        }

        is Screen.AuditHistory -> {
            BackHandler { navigateBack() }

            val khata = khatas.find { it.khataId == screen.khataId }
                ?: Khata(khataId = screen.khataId, name = "Khata")

            val logsFlow = remember(screen.khataId) {
                auditLogRepository.observeAuditLogs(screen.khataId)
            }
            val logs by logsFlow.collectAsState(initial = emptyList())

            AuditHistoryScreen(
                khataName = khata.name,
                logs = logs,
                isLoading = false,
                onBackClick = { navigateBack() }
            )
        }

        is Screen.PartnerManagement -> {
            BackHandler { navigateBack() }

            val khata = khatas.find { it.khataId == screen.khataId }
                ?: Khata(khataId = screen.khataId, name = "Khata")

            PartnerManagementScreen(
                khata = khata,
                currentUser = user,
                outgoingRequests = outgoingRequests,
                userRepository = userRepository,
                partnerRepository = partnerRepository,
                onBackClick = { navigateBack() }
            )
        }

        is Screen.PartnerRequests -> {
            BackHandler { navigateBack() }

            PartnerRequestsScreen(
                currentUser = user,
                incomingRequests = incomingRequests,
                partnerRepository = partnerRepository,
                onBackClick = { navigateBack() }
            )
        }

        is Screen.Profile -> {
            BackHandler { navigateBack() }

            ProfileScreen(
                user = user,
                userRepository = userRepository,
                onBackClick = { navigateBack() },
                onSignedOut = onSignedOut
            )
        }
    }
}
