package com.example.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import com.example.data.model.Khata
import com.example.data.model.User
import com.example.data.repository.KhataRepository
import com.example.ui.common.FormatUtils
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    user: User,
    khatas: List<Khata>,
    isLoading: Boolean,
    pendingRequestsCount: Int,
    khataRepository: KhataRepository,
    onKhataClick: (Khata) -> Unit,
    onProfileClick: () -> Unit,
    onRequestsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newKhataName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val filteredKhatas = remember(khatas, searchQuery) {
        if (searchQuery.isBlank()) {
            khatas.filter { !it.archived }
        } else {
            khatas.filter { !it.archived && it.name.contains(searchQuery.trim(), ignoreCase = true) }
        }
    }

    // Overall Totals
    val overallLena = remember(khatas) { khatas.filter { !it.archived }.sumOf { it.totalLena } }
    val overallDena = remember(khatas) { khatas.filter { !it.archived }.sumOf { it.totalDena } }
    val overallNet = overallLena - overallDena

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "उधार खाता",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                        Text(
                            text = "@${user.userId}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onRequestsClick,
                        modifier = Modifier.testTag("home_partner_requests_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (pendingRequestsCount > 0) {
                                    Badge { Text("$pendingRequestsCount") }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Partner Requests"
                            )
                        }
                    }
                    IconButton(
                        onClick = onProfileClick,
                        modifier = Modifier.testTag("home_profile_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("home_create_khata_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("+ Naya Khata", fontWeight = FontWeight.Bold)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Overall Financial Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Total Summary (कुल हिसाब)",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Lena Box
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("LENA (You'll get)", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = FormatUtils.formatRupees(overallLena),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            )
                        }

                        // Dena Box
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = null,
                                    tint = Color(0xFFC62828),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("DENA (You'll pay)", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFC62828), fontWeight = FontWeight.Bold))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = FormatUtils.formatRupees(overallDena),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFC62828)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Net Balance",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        val netColor = when {
                            overallNet > 0 -> Color(0xFF2E7D32)
                            overallNet < 0 -> Color(0xFFC62828)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Text(
                            text = FormatUtils.getNetBalanceLabel(overallNet),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = netColor
                            )
                        )
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search Khata name...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("home_search_khata_input"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Khatas List
            if (isLoading && khatas.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredKhatas.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No Khata found matching \"$searchQuery\"" else "No Khatas yet",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap '+ Naya Khata' to start a new ledger",
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredKhatas, key = { it.khataId }) { khata ->
                        KhataItemCard(
                            khata = khata,
                            onClick = { onKhataClick(khata) }
                        )
                    }
                }
            }
        }
    }

    // Create Khata Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { if (!isCreating) showCreateDialog = false },
            title = { Text("Create New Khata", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Enter any name for your Khata (e.g. Ghar ka Khata, Business, Ramesh, Shop, Delhi Trip):",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newKhataName,
                        onValueChange = { newKhataName = it },
                        label = { Text("Khata Name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_khata_name_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newKhataName.trim()
                        if (name.isNotEmpty()) {
                            isCreating = true
                            scope.launch {
                                val result = khataRepository.createKhata(
                                    name = name,
                                    actorName = user.name
                                )
                                isCreating = false
                                result.onSuccess {
                                    showCreateDialog = false
                                    newKhataName = ""
                                }.onFailure { error ->
                                    snackbarHostState.showSnackbar(error.localizedMessage ?: "Failed to create Khata")
                                }
                            }
                        }
                    },
                    enabled = newKhataName.trim().isNotBlank() && !isCreating,
                    modifier = Modifier.testTag("create_khata_confirm_button")
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Create")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreateDialog = false },
                    enabled = !isCreating
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun KhataItemCard(
    khata: Khata,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("khata_item_${khata.khataId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = khata.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${khata.peopleCount} log",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Lena: ${FormatUtils.formatRupees(khata.totalLena)}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        text = "Dena: ${FormatUtils.formatRupees(khata.totalDena)}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFFC62828),
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                val netColor = when {
                    khata.netBalance > 0 -> Color(0xFF2E7D32)
                    khata.netBalance < 0 -> Color(0xFFC62828)
                    else -> MaterialTheme.colorScheme.onSurface
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Net Balance",
                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    Text(
                        text = FormatUtils.getNetBalanceLabel(khata.netBalance),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = netColor
                        )
                    )
                }
            }
        }
    }
}
