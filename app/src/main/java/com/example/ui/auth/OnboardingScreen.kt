package com.example.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.repository.UserIdCheckState
import com.example.data.repository.UserRepository
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    firebaseUser: FirebaseUser,
    userRepository: UserRepository,
    onProfileCreated: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(firebaseUser.displayName ?: "") }
    var userIdHandle by remember { mutableStateOf("") }
    var checkState by remember { mutableStateOf<UserIdCheckState>(UserIdCheckState.Idle) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    // Real-time handle checking with debounce
    LaunchedEffect(userIdHandle) {
        val trimmed = userIdHandle.trim().lowercase().removePrefix("@")
        if (trimmed.isEmpty()) {
            checkState = UserIdCheckState.Idle
            return@LaunchedEffect
        }
        if (trimmed.length < 3) {
            checkState = UserIdCheckState.Invalid("Minimum 3 characters required")
            return@LaunchedEffect
        }

        debounceJob?.cancel()
        debounceJob = scope.launch {
            checkState = UserIdCheckState.Checking
            delay(400)
            checkState = userRepository.checkUserIdAvailability(trimmed)
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            errorMessage = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Badge,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Complete Your Profile",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )

                    Text(
                        text = "Choose your public User ID for Partner sharing",
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Your Name / Dukaan Name") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_name_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = userIdHandle,
                        onValueChange = {
                            userIdHandle = it.trim().lowercase().removePrefix("@")
                        },
                        label = { Text("Unique User ID (e.g. rahul_khata)") },
                        prefix = { Text("@", fontWeight = FontWeight.Bold) },
                        trailingIcon = {
                            when (checkState) {
                                is UserIdCheckState.Checking -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                                is UserIdCheckState.Available -> {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Available",
                                        tint = Color(0xFF2E7D32)
                                    )
                                }
                                is UserIdCheckState.Taken, is UserIdCheckState.Invalid -> {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Unavailable",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                                else -> {}
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_userid_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Helper feedback text
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        when (val state = checkState) {
                            is UserIdCheckState.Available -> {
                                Text(
                                    text = "✓ @${state.handle} is available!",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color(0xFF2E7D32),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                            is UserIdCheckState.Taken -> {
                                Text(
                                    text = "✗ @${state.handle} is already taken. Try another.",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                            is UserIdCheckState.Invalid -> {
                                Text(
                                    text = state.reason,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                            else -> {
                                Text(
                                    text = "Letters, numbers, underscores (3-30 chars)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    val canSubmit = name.trim().isNotBlank() &&
                            checkState is UserIdCheckState.Available &&
                            !isSaving

                    Button(
                        onClick = {
                            isSaving = true
                            scope.launch {
                                val result = userRepository.registerUserProfile(
                                    name = name.trim(),
                                    email = firebaseUser.email ?: "",
                                    chosenUserId = userIdHandle.trim().lowercase()
                                )
                                isSaving = false
                                result.onSuccess {
                                    onProfileCreated()
                                }.onFailure { error ->
                                    errorMessage = error.localizedMessage ?: "Failed to save profile"
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("onboarding_save_button"),
                        shape = RoundedCornerShape(25.dp),
                        enabled = canSubmit
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Save & Open Udhaar Khata", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
