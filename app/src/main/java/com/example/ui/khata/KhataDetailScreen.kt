package com.example.ui.khata

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.example.data.model.Khata
import com.example.data.model.Person
import com.example.data.model.User
import com.example.data.repository.KhataRepository
import com.example.data.repository.PersonRepository
import com.example.ui.common.FormatUtils
import kotlinx.coroutines.launch

enum class PeopleFilter {
    ALL,
    LENA,
    DENA,
    SETTLED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KhataDetailScreen(
    khata: Khata,
    currentUser: User,
    people: List<Person>,
    isLoading: Boolean,
    personRepository: PersonRepository,
    khataRepository: KhataRepository,
    onBackClick: () -> Unit,
    onPersonClick: (Person) -> Unit,
    onHistoryClick: () -> Unit,
    onPartnerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(PeopleFilter.ALL) }
    var showAddPersonDialog by remember { mutableStateOf(false) }
    var newPersonName by remember { mutableStateOf("") }
    var newPersonPhone by remember { mutableStateOf("") }
    var newPersonNote by remember { mutableStateOf("") }
    var isAddingPerson by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf(khata.name) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val filteredPeople = remember(people, searchQuery, selectedFilter) {
        people.filter { !it.archived }
            .filter { person ->
                if (searchQuery.isBlank()) true
                else person.name.contains(searchQuery.trim(), ignoreCase = true) ||
                        (person.phone?.contains(searchQuery.trim()) == true)
            }
            .filter { person ->
                when (selectedFilter) {
                    PeopleFilter.ALL -> true
                    PeopleFilter.LENA -> person.netBalance > 0
                    PeopleFilter.DENA -> person.netBalance < 0
                    PeopleFilter.SETTLED -> person.netBalance == 0L
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = khata.name,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${people.count { !it.archived }} log • ${khata.memberUids.size} partners",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onPartnerClick, modifier = Modifier.testTag("khata_share_partner_button")) {
                        Icon(Icons.Default.Share, contentDescription = "Partners")
                    }
                    IconButton(onClick = onHistoryClick, modifier = Modifier.testTag("khata_audit_history_button")) {
                        Icon(Icons.Default.History, contentDescription = "Audit History")
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename Khata") },
                            onClick = {
                                showMenu = false
                                renameValue = khata.name
                                showRenameDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Archive Khata") },
                            onClick = {
                                showMenu = false
                                scope.launch {
                                    khataRepository.archiveKhata(khata.khataId, true, currentUser.name)
                                    onBackClick()
                                }
                            }
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
                onClick = { showAddPersonDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("khata_add_person_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("+ Add Person", fontWeight = FontWeight.Bold)
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
            // Khata Summary Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Lena: ${FormatUtils.formatRupees(khata.totalLena)}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Dena: ${FormatUtils.formatRupees(khata.totalDena)}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color(0xFFC62828),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Net Balance",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                        val netColor = when {
                            khata.netBalance > 0 -> Color(0xFF2E7D32)
                            khata.netBalance < 0 -> Color(0xFFC62828)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Text(
                            text = FormatUtils.getNetBalanceLabel(khata.netBalance),
                            style = MaterialTheme.typography.titleLarge.copy(
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
                placeholder = { Text("Search person name or phone...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("khata_search_person_input"),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == PeopleFilter.ALL,
                    onClick = { selectedFilter = PeopleFilter.ALL },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = selectedFilter == PeopleFilter.LENA,
                    onClick = { selectedFilter = PeopleFilter.LENA },
                    label = { Text("Lena") }
                )
                FilterChip(
                    selected = selectedFilter == PeopleFilter.DENA,
                    onClick = { selectedFilter = PeopleFilter.DENA },
                    label = { Text("Dena") }
                )
                FilterChip(
                    selected = selectedFilter == PeopleFilter.SETTLED,
                    onClick = { selectedFilter = PeopleFilter.SETTLED },
                    label = { Text("Barabar") }
                )
            }

            // People List
            if (isLoading && people.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filteredPeople.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No person found" else "No people in this Khata yet",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Add people to start recording Lena & Dena",
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredPeople, key = { it.personId }) { person ->
                        PersonItemCard(
                            person = person,
                            onClick = { onPersonClick(person) }
                        )
                    }
                }
            }
        }
    }

    // Add Person Dialog
    if (showAddPersonDialog) {
        AlertDialog(
            onDismissRequest = { if (!isAddingPerson) showAddPersonDialog = false },
            title = { Text("Add Person to Khata", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newPersonName,
                        onValueChange = { newPersonName = it },
                        label = { Text("Name (Required)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_person_name_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPersonPhone,
                        onValueChange = { newPersonPhone = it },
                        label = { Text("Phone Number (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPersonNote,
                        onValueChange = { newPersonNote = it },
                        label = { Text("Note / Relation (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newPersonName.trim()
                        if (name.isNotEmpty()) {
                            isAddingPerson = true
                            scope.launch {
                                val result = personRepository.addPerson(
                                    khataId = khata.khataId,
                                    name = name,
                                    phone = newPersonPhone.trim().ifEmpty { null },
                                    note = newPersonNote.trim().ifEmpty { null },
                                    actorName = currentUser.name
                                )
                                isAddingPerson = false
                                result.onSuccess {
                                    showAddPersonDialog = false
                                    newPersonName = ""
                                    newPersonPhone = ""
                                    newPersonNote = ""
                                }.onFailure { err ->
                                    snackbarHostState.showSnackbar(err.localizedMessage ?: "Failed to add person")
                                }
                            }
                        }
                    },
                    enabled = newPersonName.trim().isNotBlank() && !isAddingPerson,
                    modifier = Modifier.testTag("add_person_confirm_button")
                ) {
                    if (isAddingPerson) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Add")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddPersonDialog = false },
                    enabled = !isAddingPerson
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Khata") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text("Khata Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameValue.trim().isNotBlank()) {
                            scope.launch {
                                khataRepository.updateKhataName(khata.khataId, renameValue.trim(), currentUser.name)
                                showRenameDialog = false
                            }
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun PersonItemCard(
    person: Person,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("person_item_${person.personId}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = person.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = person.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    if (!person.phone.isNullOrBlank()) {
                        Text(
                            text = person.phone,
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    } else if (!person.note.isNullOrBlank()) {
                        Text(
                            text = person.note,
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
            }

            val amountColor = when {
                person.netBalance > 0 -> Color(0xFF2E7D32)
                person.netBalance < 0 -> Color(0xFFC62828)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = FormatUtils.getNetBalanceLabel(person.netBalance),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = amountColor
                    )
                )
                Text(
                    text = "Tap for Hisaab",
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                )
            }
        }
    }
}
