package com.example

import android.os.Bundle
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
import com.example.data.model.Khata
import com.example.data.model.Person
import com.example.data.model.User
import com.example.data.repository.AuditLogRepository
import com.example.data.repository.KhataRepository
import com.example.data.repository.PartnerRepository
import com.example.data.repository.PersonRepository
import com.example.data.repository.TransactionRepository
import com.example.data.repository.UserRepository
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

        val databaseId = getString(R.string.firestore_database_id)
        val firestore = FirebaseFirestore.getInstance(databaseId)

        val userRepository = UserRepository(firestore)
        val khataRepository = KhataRepository(firestore)
        val personRepository = PersonRepository(firestore)
        val transactionRepository = TransactionRepository(firestore)
        val partnerRepository = PartnerRepository(firestore)
        val auditLogRepository = AuditLogRepository(firestore)

        setContent {
            MyApplicationTheme {
                AppRoot(
                    userRepository = userRepository,
                    khataRepository = khataRepository,
                    personRepository = personRepository,
                    transactionRepository = transactionRepository,
                    partnerRepository = partnerRepository,
                    auditLogRepository = auditLogRepository
                )
            }
        }
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
    var firebaseUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            firebaseUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    val user = firebaseUser
    if (user == null) {
        SignInScreen(
            onAuthSuccess = {
                firebaseUser = Firebase.auth.currentUser
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
