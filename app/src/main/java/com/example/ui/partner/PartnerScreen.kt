package com.example.ui.partner

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.model.Khata
import com.example.data.model.PartnerRequest
import com.example.data.model.User
import com.example.data.repository.PartnerRepository
import com.example.data.repository.UserIdLookupResult
import com.example.data.repository.UserRepository
import com.example.ui.common.FormatUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerManagementScreen(
    khata: Khata,
    currentUser: User,
    outgoingRequests: List<PartnerRequest>,
    userRepository: UserRepository,
    partnerRepository: PartnerRepository,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchHandle by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf<UserIdLookupResult?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val khataRequests = outgoingRequests.filter { it.khataId == khata.khataId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Partner Management", fontWeight = FontWeight.Bold)
                        Text(khata.name, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Invite Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Share Khata with Partner",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Search by unique User ID (both of you can view & record hisaab)",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchHandle,
                            onValueChange = {
                                searchHandle = it.trim().lowercase().removePrefix("@")
                                searchResult = null
                            },
                            placeholder = { Text("Enter User ID (e.g. amit_store)") },
                            prefix = { Text("@", fontWeight = FontWeight.Bold) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("partner_search_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (searchHandle.length >= 3) {
                                    isSearching = true
                                    scope.launch {
                                        val res = userRepository.searchUserByHandle(searchHandle)
                                        isSearching = false
                                        res.onSuccess { lookup ->
                                            searchResult = lookup
                                            if (lookup == null) {
                                                snackbarHostState.showSnackbar("No user found with @$searchHandle")
                                            }
                                        }.onFailure { err ->
                                            snackbarHostState.showSnackbar(err.localizedMessage ?: "Search failed")
                                        }
                                    }
                                }
                            },
                            enabled = searchHandle.length >= 3 && !isSearching,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("partner_search_button")
                        ) {
                            if (isSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        }
                    }

                    // Search Result Card
                    searchResult?.let { result ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(result.name, fontWeight = FontWeight.Bold)
                                    Text("@${result.userId}", style = MaterialTheme.typography.bodySmall)
                                }
                                Button(
                                    onClick = {
                                        isSending = true
                                        scope.launch {
                                            val res = partnerRepository.sendPartnerRequest(
                                                khataId = khata.khataId,
                                                khataName = khata.name,
                                                senderUserId = currentUser.userId,
                                                senderName = currentUser.name,
                                                targetHandle = result.userId
                                            )
                                            isSending = false
                                            res.onSuccess {
                                                snackbarHostState.showSnackbar("Invitation sent to @${result.userId}!")
                                                searchResult = null
                                                searchHandle = ""
                                            }.onFailure { err ->
                                                snackbarHostState.showSnackbar(err.localizedMessage ?: "Failed to send request")
                                            }
                                        }
                                    },
                                    enabled = !isSending,
                                    modifier = Modifier.testTag("send_partner_request_button")
                                ) {
                                    if (isSending) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Send Invite")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Partner Invitations (${khataRequests.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (khataRequests.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No partner requests for this Khata yet",
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(khataRequests, key = { it.requestId }) { req ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("@${req.receiverUserId}", fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "Status: ${req.status.uppercase()}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = when (req.status) {
                                                "accepted" -> Color(0xFF2E7D32)
                                                "rejected" -> Color(0xFFC62828)
                                                else -> Color(0xFFF57C00)
                                            },
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                                Text(
                                    text = FormatUtils.formatShortDateTime(req.createdAt),
                                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerRequestsScreen(
    currentUser: User,
    incomingRequests: List<PartnerRequest>,
    partnerRepository: PartnerRepository,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Partner Requests", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            if (incomingRequests.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No pending partner requests",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "When someone shares a Khata with your handle (@${currentUser.userId}), it will appear here",
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(incomingRequests, key = { it.requestId }) { req ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("incoming_request_${req.requestId}"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = req.khataName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Invited by ${req.senderName} (@${req.senderUserId})",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val res = partnerRepository.respondToPartnerRequest(
                                                    request = req,
                                                    accept = true,
                                                    receiverName = currentUser.name
                                                )
                                                res.onSuccess {
                                                    snackbarHostState.showSnackbar("Joined Khata \"${req.khataName}\"!")
                                                }.onFailure { err ->
                                                    snackbarHostState.showSnackbar(err.localizedMessage ?: "Failed to accept")
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Accept")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                partnerRepository.respondToPartnerRequest(
                                                    request = req,
                                                    accept = false,
                                                    receiverName = currentUser.name
                                                )
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Reject")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
